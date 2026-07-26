(ns kernel.launcher
  "The engine may rewrite itself; this small outside selector never trusts its claims.
   It snapshots exact trees, resumes exact pins, and activates only closed-run
   descendants which declare one evidence-backed change and pass fixed checks."
  (:require [babashka.fs :as fs] [cheshire.core :as json] [clojure.edn :as edn]
            [clojure.set :as set] [clojure.string :as str])
  (:import [java.io RandomAccessFile] [java.nio.charset StandardCharsets]
           [java.security MessageDigest] [java.time ZonedDateTime]
           [java.util.concurrent TimeUnit]))

(def repo (-> *file* fs/canonicalize fs/parent fs/parent))
(def forge-dir (fs/file repo ".forge"))
(def versions-dir (fs/file forge-dir "versions"))
(def current-file (fs/file forge-dir "CURRENT.edn"))
(def receipts-dir (fs/file forge-dir "activations"))
(def runs-dir (fs/file forge-dir "runs"))
(def campaigns-dir (fs/file forge-dir "campaigns"))
(def launcher-files
  ["forge.clj" "kernel/launcher.clj" "bb.edn" ".codex/config.toml"
   "test/runner.clj" "test/forge/fixture.clj" "test/kernel/fixture.clj"
   "problems/poincare-conjecture/goals/controller-fixture.edn"])
(def mutation-keys
  #{:changed_file :hypothesis :evidence_refs :expected_benefit
    :regression_risk :benchmark_test})

(defn fail! [message & [data]] (throw (ex-info message (or data {}))))
(defn guard! [ok message & [data]] (when-not ok (fail! message data)))
(defn read-edn [path] (edn/read-string (slurp (str path))))
(defn atomic! [path value]
  (fs/create-dirs (fs/parent path))
  (let [tmp (fs/file (fs/parent path) (str "." (fs/file-name path) "." (System/nanoTime)))]
    (spit tmp (str (pr-str value) "\n"))
    (fs/move tmp path {:replace-existing true :atomic-move true})))
(defn receipt! [event]
  (let [at (str (ZonedDateTime/now))
        path (fs/file receipts-dir (str (System/currentTimeMillis) "-" (System/nanoTime) ".edn"))]
    (fs/create-dirs receipts-dir) (atomic! path (assoc event :at at))))
(defn sha [bytes]
  (apply str (map #(format "%02x" (bit-and % 255))
                  (.digest (MessageDigest/getInstance "SHA-256") bytes))))
(defn text-sha [text] (sha (.getBytes (str text) StandardCharsets/UTF_8)))
(defn tree-map [root]
  (guard! (fs/directory? root) "Missing engine tree" {:path (str root)})
  (into (sorted-map)
        (for [file (fs/glob root "**" {:hidden true})
              :let [_ (guard! (not (fs/sym-link? file)) "Symlink in engine tree"
                              {:path (str file)})]
              :when (fs/regular-file? file)]
          [(fs/unixify (fs/relativize root file)) (sha (fs/read-all-bytes file))])))
(defn text-file? [file]
  (boolean
   (re-find #"\.(md|txt|edn|jsonl?|clj[cs]?|bb|py|lean|csv|tsv|tex|bib|ya?ml|toml)$"
            (str/lower-case (str (fs/file-name file))))))
(defn candidate-tree! [root]
  (guard! (and (fs/directory? root) (not (fs/sym-link? root)))
          "Missing or linked reflection candidate" {:path (str root)})
  (let [entries (vec (fs/glob root "**" {:hidden true}))
        regular (filterv fs/regular-file? entries)]
    (guard! (<= (count regular) 32) "Candidate engine contains too many files")
    (doseq [file entries]
      (guard! (not (fs/sym-link? file)) "Symlink in candidate" {:path (str file)})
      (guard! (or (fs/directory? file) (fs/regular-file? file))
              "Unsupported candidate file type" {:path (str file)})
      (when (fs/regular-file? file)
        (guard! (and (text-file? file) (<= (fs/size file) (* 2 1024 1024)))
                "Candidate engine must contain bounded text only"
                {:path (str file)})))
    root))
(defn tree-sha [root]
  (text-sha (str/join "\n" (map (fn [[path digest]] (str path " " digest))
                                 (tree-map root)))))
(defn launcher-sha []
  (text-sha (str/join "\n"
                      (for [path launcher-files :let [file (fs/file repo path)]]
                        (str path " " (sha (fs/read-all-bytes file)))))))
(defn copy-tree! [source target]
  (doseq [file (fs/glob source "**" {:hidden true})]
    (guard! (not (fs/sym-link? file)) "Symlink in candidate" {:path (str file)})
    (let [out (fs/file target (fs/relativize source file))]
      (if (fs/directory? file) (fs/create-dirs out)
          (do (fs/create-dirs (fs/parent out)) (fs/copy file out)))))
  target)

(defn version-dir [version] (fs/file versions-dir version))
(defn engine-dir [pin] (fs/file (version-dir (:version pin)) "engine"))
(defn valid-pin! [pin]
  (guard! (and (= #{:version :sha256} (set (keys pin)))
               (re-matches #"v\d{4}" (:version pin)))
          "Invalid engine pointer" {:pointer pin})
  (guard! (= (:sha256 pin) (tree-sha (engine-dir pin)))
          "Pinned engine hash mismatch" {:version (:version pin)})
  pin)
(defn next-version []
  (let [ids (for [dir (if (fs/directory? versions-dir) (fs/list-dir versions-dir) [])
                  :let [id (str (fs/file-name dir))] :when (re-matches #"v\d{4}" id)]
              (parse-long (subs id 1)))]
    (format "v%04d" (inc (reduce max 0 ids)))))
(defn install! [source]
  (let [id (next-version) target (version-dir id)
        stage (fs/file versions-dir (str "." id "." (System/nanoTime)))]
    (fs/create-dirs versions-dir) (copy-tree! source (fs/file stage "engine"))
    (let [pin {:version id :sha256 (tree-sha (fs/file stage "engine"))}]
      (fs/move stage target {:atomic-move true}) pin)))
(declare test-engine!)
(defn current! []
  (if (fs/regular-file? current-file)
    (valid-pin! (read-edn current-file))
    (let [source (fs/file repo "engine")
          future {:version (next-version) :sha256 (tree-sha source)}
          _ (test-engine! source future)
          pin (install! source)]
      (guard! (= (:sha256 future) (:sha256 pin))
              "Installed bootstrap engine changed after testing")
      (receipt! {:event :bootstrap :to pin})
      (atomic! current-file pin)
      pin)))
(defn receipts []
  (mapv read-edn
        (sort-by fs/file-name
                 (if (fs/directory? receipts-dir)
                   (vec (fs/glob receipts-dir "*.edn"))
                   []))))
(defn probation [pin]
  (let [event (last (filter #(or (= pin (:to %)) (= pin (:version %))) (receipts)))]
    (when (= :activate (:event event)) event)))
(defn run-pin [id]
  (guard! (re-matches #"[A-Za-z0-9][A-Za-z0-9_.+-]*" (str id))
          "Invalid run ID" {:run-id id})
  (let [manifest (read-edn (fs/file runs-dir id "run.edn"))
        pin {:version (:version manifest) :sha256 (:version-sha256 manifest)}]
    (guard! (= (:launcher-sha256 manifest) (launcher-sha))
            "Launcher changed since run began" {:run-id id})
    (valid-pin! pin)))

(def inherited-env ["HOME" "PATH" "TMPDIR" "USER" "SHELL" "TERM" "LANG" "LC_ALL"])
(defn process! [command env timeout-ms]
  (let [builder (doto (ProcessBuilder. ^java.util.List (mapv str command))
                  (.directory (.toFile repo)) (.inheritIO))
        process-env (.environment builder)
        _ (.clear process-env)]
    (doseq [key inherited-env
            :let [value (System/getenv key)]
            :when value]
      (.put process-env key value))
    (doseq [[key value] env] (.put process-env key value))
    (let [child (.start builder)]
      (if (.waitFor child timeout-ms TimeUnit/MILLISECONDS)
        (.exitValue child)
        (do (.destroyForcibly child) 124)))))
(defn command-timeout [args]
  (if (#{"run" "resume"} (first args)) (* 13 60 60 1000) (* 5 60 1000)))
(defn toml-quote [value]
  (str "\"" (-> (str value) (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))
(defn sandbox-config [writes]
  (str "permissions.polya-forge-engine.filesystem={"
       "\":minimal\"=\"read\",\":tmpdir\"=\"write\",\":slash_tmp\"=\"write\","
       "\":workspace_roots\"={\".\"=\"read\"}"
       (apply str (map #(str "," (toml-quote %) "=\"write\"") writes)) "}"))
(defn sandbox-command [writes command]
  (concat ["codex" "sandbox" "-P" "polya-forge-engine"
           "-c" (sandbox-config writes) "-C" (str repo) "--"]
          command))
(defn engine-command!
  ([engine pin args] (engine-command! engine pin args [] {}))
  ([engine pin args writes extra-env]
   (process!
    (sandbox-command writes
                     (concat ["bb" "--classpath" (str engine)
                              (str (fs/file engine "forge.clj"))] args))
    (merge {"POLYA_FORGE_REPO_ROOT" (str repo)
            "POLYA_FORGE_VERSION" (:version pin)
            "POLYA_FORGE_VERSION_SHA256" (:sha256 pin)
            "POLYA_FORGE_LAUNCHER_SHA256" (launcher-sha)}
           extra-env)
    (command-timeout args))))
(defn run-ids []
  (set (for [dir (if (fs/directory? runs-dir) (fs/list-dir runs-dir) [])
             :when (fs/regular-file? (fs/file dir "run.edn"))] (str (fs/file-name dir)))))
(defn terminal? [run] (fs/regular-file? (fs/file run "close.edn")))
(defn run-id [args]
  (str (System/currentTimeMillis) "-" (System/nanoTime) "-"
       (str/replace (str (second args)) #"[^a-zA-Z0-9_-]+" "-")))
(defn invoke!
  ([pin args] (invoke! pin args nil))
  ([pin args assigned-id]
   (let [command (first args)
         id (cond (= "run" command) (or assigned-id (run-id args))
                  (= "resume" command) (second args))
         _ (when id
             (guard! (re-matches #"[A-Za-z0-9][A-Za-z0-9_.+-]*" id)
                     "Invalid assigned run ID" {:run-id id}))
         run (when id (fs/file runs-dir id))
         _ (when (= "run" command) (fs/create-dirs run))
         problem (cond (= "run" command) (second args)
                       (= "resume" command) (:problem-id (read-edn (fs/file run "run.edn"))))
         _ (when problem
             (guard! (re-matches #"[a-z0-9]+(?:-[a-z0-9]+)*" problem)
                     "Invalid problem ID" {:problem problem}))
         memory (when problem (fs/file forge-dir "memory" problem))
         _ (when memory (fs/create-dirs memory))
         writes (cond-> []
                  run (conj run)
                  memory (conj memory)
                  (= "export" command) (conj (fs/file forge-dir "exports")))
         before (run-ids)
         exit (engine-command! (engine-dir pin) pin args writes
                               (if (= "run" command)
                                 {"POLYA_FORGE_RUN_ID" id} {}))]
     (when-not (zero? exit)
       (fail! "Engine process failed"
              {:exit exit :version (:version pin)
               :new-runs (set/difference (run-ids) before)}))
     (when (= "run" command)
       (let [created (set/difference (run-ids) before)]
         (guard! (= #{id} created) "Engine did not create its assigned run"
                 {:assigned id :runs created})))
     id)))

(defn test-engine! [source pin]
  (let [classpath (str source (System/getProperty "path.separator")
                       (fs/file repo "test"))
        env {"POLYA_FORGE_REPO_ROOT" (str repo)
             "POLYA_FORGE_VERSION" (:version pin)
             "POLYA_FORGE_VERSION_SHA256" (:sha256 pin)
             "POLYA_FORGE_LAUNCHER_SHA256" (launcher-sha)}]
    (guard! (zero? (engine-command! source pin ["check"])) "Engine check failed")
    (guard! (zero? (process!
                    (sandbox-command
                     []
                     ["bb" "--classpath" classpath
                      (str (fs/file repo "test" "runner.clj")) "--engine-gate"])
                    env (* 5 60 1000)))
            "Engine tests failed")
    pin))
(defn decision [id]
  (last (filter #(and (= id (:run-id %))
                      (#{:activate :reject :stale-candidate} (:event %)))
                (receipts))))
(defn rolled-back? [pin]
  (boolean
   (some #(and (= :rollback (:event %)) (= pin (:version %))) (receipts))))
(defn reflection-candidate [run]
  (let [close-file (fs/file run "close.edn")
        _ (guard! (and (not (fs/sym-link? close-file))
                       (<= (fs/size close-file) (* 2 1024 1024)))
                  "Unsafe close record")
        close (read-edn close-file)]
    (when-let [reflection (:reflection close)]
      (guard! (= #{:call :result} (set (keys reflection)))
              "Malformed reflection pointer")
      (let [call (:call reflection)
            call-root (fs/file run "calls")
            _ (guard! (and (fs/directory? call-root)
                           (not (fs/sym-link? call-root)))
                      "Missing or linked call index")
            entries (if (fs/directory? call-root) (fs/list-dir call-root) [])
            _ (doseq [entry entries]
                (guard! (not (fs/sym-link? entry))
                        "Symlink in call index" {:path (str entry)}))
            matches
            (vec
             (for [dir entries
                   :when (fs/directory? dir)
                   :let [request-file (fs/file dir "request.edn")]
                   :when (fs/regular-file? request-file)
                   :let [_ (guard! (and (not (fs/sym-link? request-file))
                                       (<= (fs/size request-file) (* 64 1024)))
                                  "Unsafe reflection request")]
                   :let [request (read-edn request-file)]
                   :when (= call (:call request))]
               {:dir dir :request request :request-file request-file}))]
        (guard! (and (pos-int? call) (= 1 (count matches)))
                "Reflection call pointer is invalid" {:call call})
        (let [{:keys [dir request request-file]} (first matches)
              result-file (fs/file dir "result.json")
              _ (guard! (and (fs/regular-file? result-file)
                             (not (fs/sym-link? result-file))
                             (<= (fs/size result-file) (* 2 1024 1024)))
                        "Reflection call has no bounded durable result")
              result (json/parse-string (slurp result-file) true)]
          (guard! (and (= :reflect (:role request)) (= "REFLECT" (:task request)))
                  "Reflection pointer names a non-reflection call")
          (guard! (= result (:result reflection))
                  "Close record differs from durable reflection result")
          {:source (fs/file dir "candidate" "engine")
           :result result
           :result-sha256 (sha (fs/read-all-bytes result-file))
           :request-sha256 (sha (fs/read-all-bytes request-file))
           :close-sha256 (sha (fs/read-all-bytes close-file))})))))
(defn validate-reflection! [result]
  (guard! (and (map? result) (= #{:assessment :mutation} (set (keys result)))
               (string? (:assessment result)) (not (str/blank? (:assessment result))))
          "Malformed reflection result")
  (when-let [mutation (:mutation result)]
    (guard! (and (map? mutation) (= mutation-keys (set (keys mutation))))
            "Malformed mutation declaration")
    (doseq [key [:changed_file :hypothesis :expected_benefit
                 :regression_risk :benchmark_test]]
      (guard! (and (string? (get mutation key))
                   (not (str/blank? (get mutation key))))
              "Blank mutation field" {:field key}))
    (let [path (:changed_file mutation)
          refs (:evidence_refs mutation)]
      (guard! (and (not (fs/absolute? path)) (not (str/includes? path "\\"))
                   (every? #(not (#{"." ".." ""} %))
                           (str/split path #"/" -1)))
              "Unsafe changed_file" {:path path})
      (guard! (and (vector? refs) (seq refs) (= (count refs) (count (distinct refs)))
                   (every? #(and (string? %) (not (str/blank? %))) refs))
              "Invalid mutation evidence references")))
  (:mutation result))
(defn candidate! [id parent]
  (let [run (fs/file runs-dir id)]
    (when (terminal? run)
      (if-let [prior (decision id)]
        (do
          (when (and (= :activate (:event prior)) (= parent (current!))
                     (not (rolled-back? (:to prior))))
            (atomic! current-file (valid-pin! (:to prior))))
          parent)
        (try
          (when-let [{:keys [source result result-sha256
                             request-sha256 close-sha256]}
                     (reflection-candidate run)]
            (candidate-tree! source)
            (let [mutation (validate-reflection! result)
                  old (tree-map (engine-dir parent))
                  new (tree-map source)
                  paths (set/union (set (keys old)) (set (keys new)))
                  changed (set (filter #(not= (get old %) (get new %)) paths))
                  digest (tree-sha source)]
              (if (nil? mutation)
                (do
                  (guard! (empty? changed)
                          "Reflection changed the engine without declaring a mutation"
                          {:changed changed})
                  parent)
                (if (not= parent (current!))
                  (do
                    (receipt! {:event :stale-candidate :run-id id
                               :version parent :candidate-sha256 digest
                               :result-sha256 result-sha256
                               :request-sha256 request-sha256
                               :close-sha256 close-sha256})
                    parent)
                  (do
                    (guard! (= #{(:changed_file mutation)} changed)
                            "Reflection must change exactly its declared engine file"
                            {:declared (:changed_file mutation) :actual changed})
                    (let [future {:version (next-version) :sha256 digest}
                          _ (test-engine! source future)
                          installed (install! source)]
                      (guard! (= digest (:sha256 installed))
                              "Installed candidate hash changed")
                      (receipt! {:event :activate :run-id id :from parent :to installed
                                 :mutation mutation :candidate-sha256 digest
                                 :result-sha256 result-sha256
                                 :request-sha256 request-sha256
                                 :close-sha256 close-sha256})
                      (atomic! current-file installed)
                      installed))))))
          (catch Exception error
            (receipt! {:event :reject :run-id id :version parent
                       :message (.getMessage error) :data (ex-data error)
                       :close-sha256
                       (sha (fs/read-all-bytes (fs/file run "close.edn")))})
            parent))))))
(defn adopt-source! []
  (let [parent (current!) source (fs/file repo "engine")
        digest (tree-sha source)]
    (if (= digest (:sha256 parent))
      parent
      (let [future {:version (next-version) :sha256 digest}
            _ (test-engine! source future)
            installed (install! source)]
        (guard! (= digest (:sha256 installed))
                "Installed source engine changed after testing")
        (receipt! {:event :adopt :from parent :to installed})
        (atomic! current-file installed)
        installed))))
(defn rollback! [pin error]
  (when (= pin (current!))
    (when-let [event (probation pin)]
      (atomic! current-file (:from event))
      (receipt! {:event :rollback :version pin :to (:from event)
                 :message (.getMessage error) :data (ex-data error)})
      (:from event))))
(defn execute!
  ([args] (execute! args nil))
  ([args assigned-id]
   (let [pin (if (= "resume" (first args)) (run-pin (second args)) (current!))]
     (try
       (let [id (invoke! pin args assigned-id)]
         (when id
           (guard! (= pin (run-pin id)) "Run did not pin its executing engine")
           (guard! (terminal? (fs/file runs-dir id))
                   "Engine exited without closing its run" {:run-id id})
           (when (and (= pin (current!)) (probation pin))
             (receipt! {:event :confirm :version pin :run-id id}))
           (candidate! id pin))
         id)
       (catch Exception error
         (if-let [restored (rollback! pin error)]
           (throw (ex-info (.getMessage error)
                           (assoc (or (ex-data error) {})
                                  :rolled-back-to restored)
                           error))
           (throw error)))))))
(defn campaign-file [id]
  (guard! (re-matches #"campaign-\d+-\d+" id) "Invalid campaign ID")
  (fs/file campaigns-dir (str id ".edn")))
(defn continue-campaign! [state]
  (let [file (campaign-file (:id state))]
    (loop [state (dissoc (assoc state :status :running) :error)]
      (atomic! file state)
      (if (= (:completed state) (:rounds state))
        (let [done (assoc state :status :complete :ended-at (str (ZonedDateTime/now)))]
          (atomic! file done) done)
        (let [state
              (if (:pending-run state)
                state
                (let [ready (assoc state :pending-run
                                   (run-id (into ["run"] (:args state))))]
                  (atomic! file ready)
                  ready))
              pending (:pending-run state)
              started? (fs/regular-file? (fs/file runs-dir pending "run.edn"))
              rolled-pin (when started?
                           (let [pin (run-pin pending)
                                 event (probation pin)]
                             (when (or (rolled-back? pin)
                                       (and event (= (:from event) (current!))))
                               pin)))
              next
              (if rolled-pin
                (let [_ (when-not (rolled-back? rolled-pin)
                          (receipt! {:event :rollback :version rolled-pin
                                     :to (current!) :message "Recovered interrupted rollback"}))
                      recovered
                      (-> state
                          (update :failed-runs (fnil conj [])
                                  {:round (inc (:completed state))
                                   :run-id pending :engine rolled-pin
                                   :reason :prior-rollback})
                          (dissoc :pending-run))]
                  (atomic! file recovered)
                  recovered)
                (try
                  (let [id (if started?
                             (execute! ["resume" pending])
                             (execute! (into ["run"] (:args state)) pending))]
                    (-> state
                        (update :completed inc)
                        (update :runs conj
                                {:round (inc (:completed state)) :run-id id
                                 :engine (run-pin id) :next-engine (current!)})
                        (dissoc :pending-run)))
                  (catch Exception error
                    (let [data (ex-data error)
                          failure {:round (inc (:completed state))
                                   :run-id pending
                                   :error {:message (.getMessage error) :data data}}]
                      (if-let [restored (:rolled-back-to data)]
                        (let [recovered
                              (-> state
                                  (update :failed-runs (fnil conj [])
                                          (assoc failure :rolled-back-to restored))
                                  (dissoc :pending-run))]
                          (atomic! file recovered)
                          recovered)
                        (let [stopped (assoc state :status :interrupted
                                            :error (:error failure))]
                          (atomic! file stopped)
                          (throw error)))))))]
          (recur next))))))
(defn start-campaign! [rounds args]
  (guard! (and rounds (<= 1 rounds 100)) "Campaign rounds must be 1..100")
  (guard! (= 2 (count args)) "Campaign requires a problem ID and goal path")
  (continue-campaign!
   {:format-version 1
    :id (str "campaign-" (System/currentTimeMillis) "-" (System/nanoTime))
    :started-at (str (ZonedDateTime/now))
    :rounds rounds :completed 0 :args (vec args) :runs []}))
(defn resume-campaign! [id]
  (let [state (read-edn (campaign-file id))]
    (guard! (not= :complete (:status state)) "Campaign is already complete")
    (continue-campaign! state)))
(defn with-lock [f]
  (doseq [dir [forge-dir runs-dir campaigns-dir (fs/file forge-dir "memory")
               (fs/file forge-dir "exports")]]
    (fs/create-dirs dir))
  (with-open [raf (RandomAccessFile. (str (fs/file forge-dir "launcher.lock")) "rw")
              channel (.getChannel raf)]
    (guard! (.tryLock channel) "Another Forge launcher is active") (f)))
(defn main [args]
  (with-lock
    #(let [[command rounds & rest] args]
       (case command
         "campaign" (prn (start-campaign! (parse-long rounds) rest))
         "campaign-resume" (prn (resume-campaign! rounds))
         "adopt" (prn (adopt-source!))
         (execute! args)))))
