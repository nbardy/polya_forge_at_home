(ns forge.bundle
  (:require [babashka.fs :as fs] [cheshire.core :as json] [forge.core :as core]))

(def exports-root (core/child core/forge-root "exports"))

(defn research-paths [run]
  (let [packets (filter #(re-matches #"packets/W\d{3}-.+\.edn" %)
                        (map #(core/relative run %) (core/files (core/child run "packets"))))]
    (set (concat (map #(core/relative run %) (core/files (core/child run "input")))
                 packets
                 (mapcat #(map :path (:artifacts (core/read-edn (core/child run %))))
                         packets)))))

(defn write-bundle [run destination kind paths generated metadata]
  (core/guard (not (fs/exists? destination)) "Export already exists" {:path (str destination)})
  (let [temp (core/child (fs/parent destination)
                         (str "." (fs/file-name destination) "." (System/nanoTime)))]
    (core/ensure-dir temp)
    (doseq [rel paths]
      (let [source (core/safe-relative run rel) target (core/safe-relative temp rel)]
        (core/guard (core/text-file? source) "Bundles contain text only" {:path rel})
        (core/copy-file source target)))
    (doseq [[rel value] generated] (core/write-edn (core/child temp rel) value))
    (let [hashes (core/tree-hashes temp)]
      (core/write-json (core/child temp "bundle.json")
                       (merge metadata {"format_version" 2 "kind" (name kind)
                                        "content_hash" (core/content-hash hashes)}))
      (fs/move temp destination {:atomic-move true})
      {:kind kind :path (core/path destination) :files (count hashes)})))

(defn export-run [run-id]
  (let [run (core/find-run run-id) ctx (core/context run {})
        manifest (core/read-edn (core/child run "run.edn"))
        close (core/read-edn (core/child run "close.edn"))
        public (select-keys manifest [:format-version :run-id :problem-id :started-at
                                      :deadline :engine_hash])
        metadata {"run_id" run-id "problem_id" (:problem-id manifest)
                  "engine_hash" (:engine_hash manifest)}]
    (core/guard (and (:memory close) (:reflection close))
                "Only runs with terminal memory and reflection may be exported")
    (core/assert-run ctx)
    (core/run-state ctx)
    (core/ensure-dir exports-root)
    {:status "EXPORTED"
     :research
     (write-bundle run (core/child exports-root (str run-id "-research")) :research
                   (research-paths run)
                   {"run.edn" public "close.edn" (dissoc close :reflection)} metadata)
     :harness
     (write-bundle run (core/child exports-root (str run-id "-harness")) :harness #{}
                   {"run.edn" (assoc public :budget (:budget (core/read-edn
                                                             (core/child run "input" "goal.edn"))))
                    "close.edn" (select-keys close [:stop :ended_at :reflection])
                    "process.edn" (mapv core/call-record (core/call-dirs ctx))}
                   metadata)}))

(defn inspect-bundle [value]
  (let [root (core/canonical value) all (vec (take 4097 (core/files root)))
        bundle (core/require-file (core/child root "bundle.json"))
        _ (core/guard (<= (fs/size bundle) (* 1024 1024)) "Bundle manifest is too large")
        manifest (json/parse-string (slurp bundle) false)
        expected (get manifest "content_hash")]
    (core/guard (and (fs/directory? root) (= 2 (get manifest "format_version"))
                     (#{"research" "harness"} (get manifest "kind"))
                     (re-matches #"[a-f0-9]{64}" expected))
                "Bundle manifest is invalid")
    (core/guard (<= (count all) 4096) "Bundle contains too many files")
    (doseq [file (fs/glob root "**" {:hidden true})]
      (core/guard (not (fs/sym-link? file)) "Bundle contains a symlink")
      (when (fs/regular-file? file)
        (core/guard (and (core/text-file? file) (<= (fs/size file) (* 64 1024 1024)))
                    "Bundle file is not bounded auditable text")))
    (core/guard (= expected
                   (core/content-hash (dissoc (core/tree-hashes root) "bundle.json")))
                "Bundle hash mismatch")
    (if (= "research" (get manifest "kind"))
      (do (doseq [rel ["run.edn" "close.edn" "input/goal.edn" "input/problem.edn"]]
            (core/require-file (core/child root rel)))
          (core/guard (seq (core/completed-packets {:run root}))
                      "Research bundle contains no packets"))
      (doseq [rel ["run.edn" "close.edn" "process.edn"]]
        (core/require-file (core/child root rel))))
    {:status "BUNDLE_VALID" :format 2 :kind (get manifest "kind")
     :files (dec (count all))}))
