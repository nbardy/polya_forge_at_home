(ns forge.core
  "One bounded evidence loop is evolvable because no fixed topology is optimal.
   Exact endpoint briefs and independent verification protect selection quality;
   frozen packets make parallel work resumable; terminal memory prevents repeats;
   reflection changes only the next version, never its own evidence."
  (:require [babashka.fs :as fs] [babashka.process :as process]
            [cheshire.core :as json] [clojure.edn :as edn] [clojure.string :as str])
  (:import [java.io BufferedReader FileReader]
           [java.nio.charset StandardCharsets]
           [java.security MessageDigest] [java.time ZonedDateTime]
           [java.time.format DateTimeFormatter] [java.util.concurrent TimeUnit]))
(def engine-root (fs/file (-> *file* fs/canonicalize fs/parent fs/parent)))
(def repo-root
  (fs/file (or (System/getenv "POLYA_FORGE_REPO_ROOT")
               (fs/parent engine-root))))
(def problems-root (fs/file repo-root "problems"))
(def forge-root (fs/file repo-root ".forge"))
(def runs-root (fs/file forge-root "runs"))
(defn fail ([message] (fail message {})) ([message data] (throw (ex-info message data))))
(defn guard ([ok message] (guard ok message {})) ([ok message data] (when-not ok (fail message data))))
(defn child [root & parts] (apply fs/file root parts))
(defn canonical [x] (fs/file (fs/canonicalize x)))
(defn path [x] (str (canonical x)))
(defn ensure-dir [x] (fs/file (fs/create-dirs x)))
(defn require-file [x] (guard (fs/regular-file? x) "Required file is missing" {:path (str x)}) (fs/file x))
(defn read-edn [x] (edn/read-string (slurp (require-file x))))
(defn read-json [x] (json/parse-string (slurp (require-file x)) true))
(defn atomic-move [source target] (fs/move source target {:replace-existing true :atomic-move true}))
(defn write-text [x value]
  (let [file (fs/file x) _ (ensure-dir (fs/parent file))
        temp (child (fs/parent file) (str "." (fs/file-name file) "." (System/nanoTime)))]
    (spit temp (str value)) (atomic-move temp file) file))
(defn write-edn [x value] (write-text x (str (pr-str value) "\n")))
(defn write-json [x value] (write-text x (json/generate-string value {:pretty true})))
(defn inside? [root file] (fs/starts-with? (canonical file) (canonical root)))
(defn safe-relative [root value]
  (guard (not (or (str/blank? value) (fs/absolute? value)
                  (some #{".."} (str/split value #"[/\\]+"))))
         "Unsafe relative path" {:path value})
  (let [file (canonical (child root value))]
    (guard (inside? root file) "Path escapes root" {:path value})
    (guard (not (fs/sym-link? (child root value))) "Symlinks are forbidden" {:path value})
    file))
(defn files [root] (filter fs/regular-file? (fs/glob root "**" {:hidden true})))
(defn text-file? [file]
  (boolean (re-find #"\.(md|txt|edn|jsonl?|clj[cs]?|bb|py|lean|csv|tsv|tex|bib|ya?ml|toml|log)$" (str/lower-case (str (fs/file-name file))))))
(defn relative [root file] (fs/unixify (fs/relativize (canonical root) (canonical file))))
(defn sha256-bytes [^bytes value]
  (apply str (map #(format "%02x" (bit-and % 0xff))
                  (.digest (MessageDigest/getInstance "SHA-256") value))))
(defn sha256-file [x] (sha256-bytes (fs/read-all-bytes (require-file x))))
(defn sha256-text [x] (sha256-bytes (.getBytes (str x) StandardCharsets/UTF_8)))
(defn tree-hashes [root] (into (sorted-map) (for [f (files root)] [(relative root f) (sha256-file f)])))
(defn content-hash [hashes] (sha256-text (str/join "\n" (map (fn [[p h]] (str p " " h)) hashes))))
(defn engine-hash [] (content-hash (tree-hashes engine-root)))
(defn now [] (str (ZonedDateTime/now)))
(defn run-stamp [] (.format (ZonedDateTime/now) (DateTimeFormatter/ofPattern "yyyyMMdd'T'HHmmssZ")))
(defn slug [x]
  (let [s (-> (str x) str/lower-case (str/replace #"[^a-z0-9]+" "-")
              (str/replace #"(^-+|-+$)" ""))]
    (if (str/blank? s) "item" s)))
(def config (read-edn (child engine-root "config.edn")))
(defn role-file [root folder role suffix] (child root folder (str (name role) suffix)))
(defn run-prompt [_ role] (role-file engine-root "prompts" role ".md"))
(defn run-schema [_ role] (role-file engine-root "schemas" role ".schema.json"))
(def pack-files {"target.md" "target.md" "memory/KEY_LEARNINGS.md" "memory.md"
                 "results/RESULTS_CATALOG.md" "catalog.md" "results/RETIRED_ROUTES.md" "retired.md"})
(defn problem-dirs []
  (sort-by fs/file-name
           (filter #(fs/regular-file? (child % "problem.edn")) (fs/list-dir problems-root))))
(defn validate-problem [dir]
  (let [m (read-edn (child dir "problem.edn"))]
    (guard (and (= 1 (:format-version m)) (= (str (fs/file-name dir)) (:id m)))
           "Invalid problem identity" {:problem (:id m)})
    (doseq [p (conj (vec (keys pack-files)) "AGENTS.md")] (require-file (safe-relative dir p)))
    (guard (fs/directory? (child dir "goals")) "Problem goals directory is missing" {:problem (:id m)})
    {:dir (canonical dir) :manifest m}))
(defn find-problem [id]
  (guard (re-matches #"[a-z0-9]+(?:-[a-z0-9]+)*" id) "Invalid problem ID" {:problem id})
  (validate-problem (child problems-root id)))
(defn validate-goal [problem goal-path]
  (let [file (canonical goal-path) root (canonical (child (:dir problem) "goals"))
        goal (read-edn file) budget (:budget goal) caps (:caps config)]
    (guard (inside? root file) "Goal must live in its problem pack" {:goal (path file)})
    (guard (and (= 1 (:format-version goal)) (= :active (:status goal))
                (= (get-in problem [:manifest :id]) (:problem goal)))
           "Goal is not active for this problem" {:goal (:id goal)})
    (doseq [k [:objective :deliverable :endpoint-edge :first-open-line :completion :kill]]
      (guard (not (str/blank? (get goal k))) "Goal field is blank" {:field k}))
    (doseq [k [:inputs :exclusions]]
      (guard (and (vector? (get goal k)) (seq (get goal k))) "Goal list is empty" {:field k}))
    (guard (= (set (keys caps)) (set (keys budget))) "Goal budget fields differ from the contract")
    (doseq [k [:fanout :invocations :wall-minutes]]
      (guard (and (pos-int? (get budget k)) (<= (get budget k) (get caps k)))
             "Goal budget exceeds engine cap" {:budget k :value (get budget k)}))
    (guard (>= (:invocations budget) 5) "Goal cannot fund plan, build, verify, memory, and reflection")
    {:file file :data goal}))
(defn validate-engine []
  (doseq [role [:plan :build :verify :remember :reflect]]
    (guard (not (str/blank? (slurp (require-file (role-file engine-root "prompts" role ".md")))))
           "Blank prompt" {:role role})
    (read-json (role-file engine-root "schemas" role ".schema.json")))
  true)
(defn validate-repository []
  (validate-engine)
  {:status "VALID" :engine_hash (engine-hash)
   :problems (mapv #(select-keys (:manifest (validate-problem %)) [:id :title :status])
                   (problem-dirs))})
(defn copy-file [source target]
  (ensure-dir (fs/parent target))
  (fs/copy (require-file source) target {:replace-existing true})
  target)
(defn copy-tree [source target]
  (when (fs/exists? target) (fs/delete-tree target))
  (doseq [file (files source)]
    (copy-file file (child target (relative source file))))
  target)
(defn local-memory-file [problem-id]
  (child forge-root "memory" problem-id "INDEX.edn"))
(defn local-memory [problem-id]
  (let [file (local-memory-file problem-id)]
    (if (fs/regular-file? file)
      (read-edn file)
      {:format-version 1 :problem-id problem-id :runs []})))
(defn create-input [problem goal run]
  (let [root (ensure-dir (child run "input"))
        problem-id (get-in problem [:manifest :id])]
    (copy-file (child (:dir problem) "problem.edn") (child root "problem.edn"))
    (copy-file (:file goal) (child root "goal.edn"))
    (write-text (child root "rules.md") (str (slurp (child repo-root "AGENTS.md")) "\n\n"
                                             (slurp (child (:dir problem) "AGENTS.md"))))
    (doseq [[source target] pack-files] (copy-file (child (:dir problem) source)
                                                   (child root target)))
    (write-edn (child root "local-memory.edn") (local-memory problem-id))
    root))
(defn snapshot-text [ctx name] (slurp (child (:input ctx) (str name ".md"))))
(defn snapshot [ctx]
  (str "# Rules\n\n" (snapshot-text ctx "rules") "\n# Target\n\n" (snapshot-text ctx "target")
       "\n# Memory\n\n" (snapshot-text ctx "memory") "\n# Catalog\n\n" (snapshot-text ctx "catalog")
       "\n# Retired routes\n\n" (snapshot-text ctx "retired")
       "\n# Local cross-run memory\n\n" (pr-str (read-edn (child (:input ctx) "local-memory.edn")))
       "\n# Goal\n\n" (pr-str (:goal ctx))))
(defn memory-context [ctx]
  (str (snapshot-text ctx "memory") "\n\n"
       (pr-str (read-edn (child (:input ctx) "local-memory.edn")))))
(defn render [text values] (reduce-kv #(str/replace %1 (str "{{" (name %2) "}}") (str %3))
                                      text values))
(defn pretty [value] (json/generate-string value {:pretty true}))
(defn prompt [ctx role values] (render (slurp (run-prompt ctx role)) values))
(defn call-dirs [ctx]
  (sort-by fs/file-name (filter fs/directory?
                                (fs/list-dir (ensure-dir (child (:run ctx) "calls"))))))
(defn request-of [dir] (let [f (child dir "request.edn")] (when (fs/regular-file? f) (read-edn f))))
(declare validate-artifacts validate-verifier validate-plan assert-run thread-id)
(defn mark-error [dir error]
  (write-edn (child dir "error.edn") {:failed_at (now) :message (.getMessage error)
                                      :data (ex-data error)}))
(defn checked-call [dir request value validate]
  (when (= :verify (:role request)) (validate-verifier value))
  (let [call {:data value :dir dir :request request
              :path (child dir "result.json")
              :artifacts (if (= :build (:role request)) (validate-artifacts dir) [])}]
    (when validate (validate call))
    call))
(defn complete-call
  ([ctx task] (complete-call ctx task nil))
  ([ctx task validate]
   (some (fn [dir]
           (let [request (request-of dir) result (child dir "result.json")]
             (when (and (= task (:task request)) (fs/regular-file? result)
                        (not (fs/regular-file? (child dir "error.edn"))))
               (try (checked-call dir request (read-json result) validate)
                    (catch Exception error (mark-error dir error) nil)))))
         (reverse (call-dirs ctx)))))
(defn remaining-calls [ctx] (- (get-in ctx [:budget :invocations]) @(:calls ctx)))
(defn remaining-ms [ctx] (max 0 (- (long (:deadline (:manifest ctx)))
                                   (System/currentTimeMillis))))
(def terminal-roles #{:remember :reflect})
(defn terminal-reserve-ms [ctx]
  (* 60000
     (min (long (get config :terminal-reserve-minutes 30))
          (max 1 (quot (long (get-in ctx [:budget :wall-minutes])) 4)))))
(defn call-ms [ctx role]
  (if (terminal-roles role)
    (remaining-ms ctx)
    (max 0 (- (remaining-ms ctx) (terminal-reserve-ms ctx)))))
(defn reserve-call [ctx role task metadata]
  (locking (:calls ctx)
    (guard (pos? (call-ms ctx role)) "Run wall-clock budget exhausted" {:stop :wall-time})
    (let [n (inc @(:calls ctx))
          limit (- (get-in ctx [:budget :invocations])
                   (if (#{:plan :build :verify} role) 2 0))]
      (guard (<= n limit) "Run invocation budget exhausted" {:stop :invocation-budget})
      (reset! (:calls ctx) n)
      (let [dir (ensure-dir (child (:run ctx) "calls" (format "%03d-%s" n (slug task))))]
        (copy-file (child (:input ctx) "rules.md") (child dir "AGENTS.md"))
        (write-edn (child dir "request.edn")
                   (merge {:call n :task task :role role :started_at (now)} metadata))
        dir))))
(defn validate-artifacts [dir]
  (let [root (child dir "artifacts") artifacts (vec (files root))]
    (guard (<= (count artifacts) 16) "Builder produced too many artifacts")
    (doseq [file (fs/glob root "**" {:hidden true})]
      (guard (not (fs/sym-link? file)) "Builder artifacts cannot contain symlinks"))
    (mapv #(do (guard (and (text-file? %) (<= (fs/size %) (* 5 1024 1024)))
                       "Builder artifacts must be bounded auditable text" {:path (str %)})
               %)
          artifacts)))
(defn invoke
  ([ctx role task prompt] (invoke ctx role task prompt {}))
  ([ctx role task prompt {:keys [request validate prepare]}]
   (let [check #(when validate (validate %))]
     (assert-run ctx)
     (or (complete-call ctx task check)
         (let [dir (reserve-call ctx role task request)
               _ (when prepare (prepare dir))
               prompt (if (fn? prompt) (prompt dir) prompt)
               _ (write-text (child dir "prompt.md") prompt)
               result (child dir "result.json") pending (child dir "pending-result.json")
               events (child dir "events.jsonl")]
           (try
             (if-let [fake (:fake ctx)]
               (do (write-text events "") (write-json pending (fake role task prompt dir)))
               (let [command [(get config :codex) "exec" "--json" "--ephemeral"
                              "--ignore-user-config" "--skip-git-repo-check"
                              "-C" (path dir) "--sandbox" "workspace-write"
                              "-m" (:model config)
                              "-c" (str "model_reasoning_effort=\"" (:effort config) "\"")
                              "-c" "approval_policy=\"never\""
                              "--output-schema" (path (run-schema ctx role))
                              "--output-last-message" (path pending) "-"]
                     handle (process/process command {:dir dir :in prompt :out events
                                                      :err (child dir "stderr.log")})]
                 (try
                   (guard (.waitFor ^Process (:proc handle) (call-ms ctx role)
                                    TimeUnit/MILLISECONDS)
                          "Run wall-clock budget exhausted" {:stop :wall-time :task task})
                   (guard (zero? (:exit @handle)) "Model call failed"
                          {:task task :exit (:exit @handle)})
                   (finally (try (process/destroy-tree handle) (catch Exception _ nil))))))
             (when-let [id (thread-id dir)]
               (write-edn (child dir "thread.edn") {:conversation-id id}))
             (let [call (checked-call dir (request-of dir) (read-json pending) check)]
               (assert-run ctx)
               (atomic-move pending result)
               call)
             (catch Exception error (mark-error dir error) (throw error))))))))
(defn thread-id [dir]
  (let [events (child dir "events.jsonl")]
    (when (fs/regular-file? events)
      (with-open [reader (BufferedReader. (FileReader. (str events)))]
        (some #(try
                 (let [event (json/parse-string % true)]
                   (or (:thread_id event) (:thread-id event)))
                 (catch Exception _ nil))
              (take 32 (line-seq reader)))))))
(defn packet-id [packet] (sha256-text (pr-str (dissoc packet :id))))
(defn packet-index [packets]
  (mapv (fn [packet]
          {:id (:id packet) :objective (get-in packet [:brief :objective])
           :verdict (get-in packet [:verify :verdict])
           :claim (get-in packet [:build :claim]) :claim_status (get-in packet [:verify :claim_status])
           :audit (get-in packet [:verify :audit])
           :first_open_line (get-in packet [:verify :first_open_or_invalid_line])})
        packets))
(defn load-packet [root file]
  (let [packet (read-edn file)
        artifacts (:artifacts packet)
        artifact-files (mapv #(safe-relative root (:path %)) artifacts)]
    (guard (= (:id packet) (packet-id packet)) "Frozen packet changed")
    (guard (= (str (fs/file-name file))
              (format "W%03d-%s.edn" (:wave packet) (get-in packet [:brief :id])))
           "Packet filename disagrees with its content")
    (guard (not= (get-in packet [:calls :build]) (get-in packet [:calls :verify]))
           "Builder cannot verify its own result")
    (doseq [[descriptor file] (map vector artifacts artifact-files)]
      (guard (= (:sha256 descriptor) (sha256-file file))
             "Frozen packet artifact changed" {:path (:path descriptor)}))
    (validate-verifier (:verify packet))
    (assoc packet :artifact-files artifact-files)))
(defn completed-packets [ctx]
  (let [root (child (:run ctx) "packets")
        packets (mapv #(load-packet (:run ctx) %)
                      (sort-by fs/file-name
                               (filter #(re-matches #"W\d{3}-.+\.edn" (str (fs/file-name %)))
                                       (if (fs/directory? root) (fs/list-dir root) []))))]
    (guard (= (count packets)
              (count (distinct (map #(vector (:wave %) (get-in % [:brief :id])) packets))))
           "Duplicate packet for one wave brief")
    (loop [groups (sort-by key (group-by :wave packets)) prior #{} wave 1]
      (if-let [[number current] (first groups)]
        (do (guard (= wave number) "Packet waves are not contiguous")
            (doseq [packet current
                    :let [parents (set (get-in packet [:brief :parent_packet_ids]))]]
              (guard (if (= wave 1) (empty? parents)
                         (and (seq parents) (every? prior parents)))
                     "Packet has missing or forward parents"))
            (recur (rest groups) (into prior (map :id current)) (inc wave)))
        packets))))
(defn validate-plan [goal plan wave limit prior]
  (let [briefs (:briefs plan) prior-ids (set (map :id prior))]
    (guard (and (vector? briefs) (<= (count briefs) limit))
           "Planner exceeded its brief limit")
    (guard (= (count briefs) (count (distinct (map :id briefs))))
           "Planner emitted duplicate brief IDs")
    (doseq [brief briefs]
      ;; Fan-out may change strategy, never the frozen endpoint objective.
      (guard (= (:objective goal) (:objective brief))
             "Brief changed the frozen objective" {:brief (:id brief)})
      (guard (= (:endpoint-edge goal) (:endpoint_edge brief))
             "Brief changed the frozen endpoint edge" {:brief (:id brief)})
      (guard (= (:first-open-line goal) (:first_open_line brief))
             "Brief changed the frozen first open line" {:brief (:id brief)})
      (let [parents (set (:parent_packet_ids brief))]
        (guard (not (if (= wave 1) (seq parents)
                        (or (empty? parents) (not-every? prior-ids parents))))
               "Brief has invalid parent packets" {:brief (:id brief)})))
    plan))
(defn plan-wave [ctx wave limit prior]
  (let [task (format "W%03d-PLAN" wave)
        text (prompt ctx :plan {:BRIEF_LIMIT limit :SNAPSHOT (snapshot ctx)
                                :PACKET_INDEX (pretty (packet-index prior))})]
    (invoke ctx :plan task text
            {:request {:brief-limit limit}
             :validate #(validate-plan (:goal ctx) (:data %) wave limit prior)})))
(defn validate-verifier [value]
  (guard (= (= "PASS" (:verdict value)) (nil? (:failure value)))
         "Verifier verdict and failure record disagree")
  value)
(defn packet-view [ctx packet]
  (assoc (select-keys packet [:id :brief :build :verify])
         :artifacts (mapv #(update % :path (fn [rel] (path (child (:run ctx) rel))))
                          (:artifacts packet))))
(defn freeze-packet [ctx wave brief plan build verify]
  (let [artifacts (mapv (fn [file]
                          {:name (relative (:dir build) file)
                           :path (relative (:run ctx) file)
                           :sha256 (sha256-file file)})
                        (:artifacts build))
        packet {:wave wave :brief brief :build (:data build) :verify (:data verify)
                :artifacts artifacts
                :calls {:plan (get-in plan [:request :call])
                        :build (get-in build [:request :call])
                        :verify (get-in verify [:request :call])}}
        packet (assoc packet :id (packet-id packet))
        file (child (:run ctx) "packets" (format "W%03d-%s.edn" wave (:id brief)))]
    (guard (not (fs/exists? file)) "Packet already exists" {:brief (:id brief)})
    (write-edn file packet)
    (load-packet (:run ctx) file)))
(defn run-branch [ctx wave brief plan prior]
  (let [prefix (format "W%03d-%s" wave (:id brief))
        parents (mapv (into {} (map (juxt :id identity) prior))
                      (:parent_packet_ids brief))
        frozen {:brief brief :parents (mapv #(packet-view ctx %) parents)}
        build (invoke ctx :build (str prefix "-BUILD")
                      (prompt ctx :build
                              {:TARGET (snapshot-text ctx "target") :GOAL (pr-str (:goal ctx))
                               :MEMORY (memory-context ctx)
                               :BRIEF (pretty frozen)}))
        packet (assoc frozen :build (:data build)
                      :build_artifacts
                      (mapv (fn [file] {:path (path file) :sha256 (sha256-file file)})
                            (:artifacts build)))
        verify (invoke ctx :verify (str prefix "-VERIFY")
                       (prompt ctx :verify
                               {:TARGET (snapshot-text ctx "target") :GOAL (pr-str (:goal ctx))
                                :MEMORY (memory-context ctx)
                                :PACKET (pretty packet)}))]
    (freeze-packet ctx wave brief plan build verify)))
(defn execute-wave [ctx wave briefs plan prior]
  (let [jobs (mapv #(future
                      (try (run-branch ctx wave % plan prior)
                           (catch Exception error error)))
                   briefs)
        results (mapv deref jobs)
        errors (filter #(instance? Exception %) results)]
    (when (seq errors)
      (throw (or (some #(when (:stop (ex-data %)) %) errors) (first errors))))
    results))
(defn call-record [dir]
  (let [request (request-of dir) error (child dir "error.edn")
        conversation (thread-id dir)]
    (cond-> (assoc request :outcome (cond (fs/regular-file? error) :failed
                                          (fs/regular-file? (child dir "result.json")) :completed
                                          :else :interrupted))
      conversation (assoc :conversation-id conversation)
      (fs/regular-file? error) (assoc :error (read-edn error)))))
(defn validate-memory [packets value]
  (let [passed (set (for [p packets :when (= "PASS" (get-in p [:verify :verdict]))]
                      (:id p)))]
    (doseq [change (:changes value)]
      (guard (#{"advance-frontier" "remove-assumption" "retire-route"
                "preserve-salvage"}
              (:kind change))
             "Unknown memory change kind" {:kind (:kind change)})
      (guard (and (seq (:packet_ids change)) (every? passed (:packet_ids change)))
             "Memory change lacks PASS packet support"))
    value))
(defn evolution-history []
  (let [root (child forge-root "activations")]
    {:key-learnings (slurp (require-file (child engine-root "memory" "KEY_LEARNINGS.md")))
     :recent-decisions
     (mapv read-edn
           (take-last 8
                      (sort-by fs/file-name
                               (if (fs/directory? root)
                                 (filter fs/regular-file? (fs/list-dir root))
                                 []))))}))
(defn candidate-root [dir] (child dir "candidate" "engine"))
(defn prepare-candidate [dir] (copy-tree engine-root (candidate-root dir)))
(defn publish-local-memory [ctx memory]
  (when memory
    (let [problem-id (:problem-id (:manifest ctx))
          file (local-memory-file problem-id)
          before (local-memory problem-id)
          run-id (:run-id (:manifest ctx))
          record {:run-id run-id :at (now)
                  :evidence-root (str "runs/" run-id)
                  :version (get-in ctx [:manifest :version])
                  :changes (get-in memory [:data :changes])}
          runs (:runs before)
          after (if (some #(= run-id (:run-id %)) runs)
                  before
                  (assoc before :runs (vec (take-last 32 (conj (vec runs) record)))))]
      (write-edn file after)
      file)))
(defn write-close [ctx stop memory reflection]
  (let [file (child (:run ctx) "close.edn")
        result #(when % {:call (get-in % [:request :call]) :result (:data %)})
        value (cond-> {:stop stop :ended_at (now)}
                memory (assoc :memory (result memory))
                reflection (assoc :reflection (result reflection)))]
    (if (fs/regular-file? file)
      (read-edn file)
      (do (publish-local-memory ctx memory) (write-edn file value) value))))
(defn close-run [ctx packets stop]
  (let [close-file (child (:run ctx) "close.edn")]
    (if (fs/regular-file? close-file)
      (read-edn close-file)
      (let [memory0 (complete-call ctx "MEMORY"
                                   #(validate-memory packets (:data %)))
            reflection0 (complete-call ctx "REFLECT")
            needed (cond reflection0 0 memory0 1 :else 2)]
        (if (and (pos? needed)
                 (or (< (remaining-calls ctx) needed) (zero? (remaining-ms ctx))))
          (write-close ctx stop memory0 reflection0)
          (let [memory (or memory0
                           (invoke ctx :remember "MEMORY"
                                   (prompt ctx :remember
                                           {:GOAL (pr-str (:goal ctx))
                                            :MEMORY (memory-context ctx)
                                            :PACKETS (pretty (mapv #(packet-view ctx %) packets))})
                                   {:validate #(validate-memory packets (:data %))}))
                record {:run (:manifest ctx) :stop stop :packets (packet-index packets)
                        :calls (mapv call-record (call-dirs ctx))}
                reflection (or reflection0
                               (invoke ctx :reflect "REFLECT"
                                       (fn [dir]
                                         (prompt
                                          ctx :reflect
                                          {:RUN_RECORD (pretty record)
                                           :GOAL (pr-str (:goal ctx))
                                           :MEMORY
                                           (str (memory-context ctx)
                                                "\n\n# Proposed changes\n"
                                                (pretty (:data memory)))
                                           :EVOLUTION_HISTORY
                                           (pretty (evolution-history))
                                           :CANDIDATE_DIR
                                           (path (candidate-root dir))}))
                                       {:prepare prepare-candidate}))]
            (write-close ctx stop memory reflection)))))))
(defn assert-run [ctx]
  (guard (= (or (:version-sha256 (:manifest ctx))
                (:engine_hash (:manifest ctx)))
            (engine-hash))
         "Pinned harness version changed")
  (guard (= (:input_hashes (:manifest ctx)) (tree-hashes (:input ctx)))
         "Frozen run input changed")
  true)
(defn context [run overrides]
  (let [manifest (read-edn (child run "run.edn"))
        input (child run "input")
        goal (read-edn (child input "goal.edn"))
        base {:run (canonical run) :input input :manifest manifest
              :goal goal :budget (:budget goal)}]
    (merge base {:calls (atom (count (keep request-of (call-dirs base))))} overrides)))
(defn run-state [ctx]
  (let [packets (completed-packets ctx) by-wave (group-by :wave packets)]
    (loop [wave 1 prior []]
      (let [task (format "W%03d-PLAN" wave)
            plan (complete-call
                  ctx task
                  #(validate-plan (:goal ctx) (:data %) wave
                                  (get-in % [:request :brief-limit]) prior))
            current (vec (get by-wave wave []))]
        (if-not plan
          (do (guard (not-any? #(>= (:wave %) wave) packets)
                     "Packets exist without a valid plan")
              {:wave wave :prior prior :packets packets})
          (let [briefs (get-in plan [:data :briefs])
                expected (into {} (map (juxt :id identity) briefs))
                actual (into {} (map #(vector (get-in % [:brief :id]) %) current))]
            (guard (and (every? expected (keys actual))
                        (every? #(= (get-in % [:calls :plan])
                                    (get-in plan [:request :call])) current)
                        (every? (fn [[id packet]] (= (expected id) (:brief packet))) actual))
                   "Packets disagree with their wave plan")
            (if (and (seq briefs) (= (set (keys expected)) (set (keys actual))))
              (recur (inc wave) (into prior current))
              (do (guard (not-any? #(> (:wave %) wave) packets)
                         "A later packet precedes an incomplete wave")
                  {:wave wave :prior prior :packets packets :plan plan
                   :missing (filterv #(not (actual (:id %))) briefs)}))))))))
(defn continue-run [ctx]
  (assert-run ctx)
  (if (fs/regular-file? (child (:run ctx) "close.edn"))
    (read-edn (child (:run ctx) "close.edn"))
    (letfn [(finish [packets stop] (close-run ctx packets stop))]
      (try
        (loop []
          (let [{:keys [wave prior packets plan missing]} (run-state ctx)]
            (cond
              (zero? (call-ms ctx :plan)) (finish packets :wall-time)
              (and (nil? plan) (<= (remaining-calls ctx) 2))
              (finish packets :invocation-budget)
              :else
              (let [limit (min (get-in ctx [:budget :fanout])
                               (max 0 (quot (- (remaining-calls ctx) 3) 2)))
                    plan (or plan (plan-wave ctx wave limit prior))
                    briefs (get-in plan [:data :briefs])]
                (if (empty? briefs)
                  (finish packets (if (seq packets) :planner-stopped :zero-initial-plan))
                  (do (execute-wave ctx wave (or missing briefs) plan prior)
                      (recur)))))))
        (catch Exception error
          (if-let [stop (:stop (ex-data error))]
            (finish (completed-packets ctx) stop)
            (throw error)))))))
(defn run-result [id run overrides] {:run-id id :run-path (path run)
                                     :close (continue-run (context run overrides))})
(defn start-run [problem-id goal-path overrides]
  (validate-engine)
  (let [problem (find-problem problem-id) goal (validate-goal problem goal-path)
        id (or (System/getenv "POLYA_FORGE_RUN_ID")
               (str (run-stamp) "_" (format "%06d" (mod (System/nanoTime) 1000000))
                    "_" problem-id "_" (slug (get-in goal [:data :id]))))
        _ (guard (re-matches #"[A-Za-z0-9][A-Za-z0-9_.+-]*" id)
                 "Launcher supplied an invalid run ID")
        run (ensure-dir (child runs-root id)) input (create-input problem goal run)
        manifest {:format-version 1 :run-id id :problem-id problem-id
                  :started-at (now)
                  :deadline (+ (System/currentTimeMillis)
                               (* 60000 (get-in goal [:data :budget :wall-minutes])))
                  :version (or (System/getenv "POLYA_FORGE_VERSION") "source")
                  :version-sha256
                  (or (System/getenv "POLYA_FORGE_VERSION_SHA256")
                      (engine-hash))
                  :launcher-sha256
                  (System/getenv "POLYA_FORGE_LAUNCHER_SHA256")
                  :engine_hash (engine-hash)
                  :input_hashes (tree-hashes input)}]
    (guard (= (:version-sha256 manifest) (engine-hash))
           "Launcher supplied the wrong harness version hash")
    (write-edn (child run "run.edn") manifest)
    (try
      (run-result id run overrides)
      (catch Exception error
        (throw (ex-info (.getMessage error)
                        (assoc (or (ex-data error) {}) :run-id id :run-path (path run))
                        error))))))
(defn find-run [id]
  (let [run (safe-relative (ensure-dir runs-root) id)]
    (guard (fs/regular-file? (child run "run.edn")) "Unknown or legacy run" {:run id})
    run))
(defn resume-run [id overrides] (let [run (find-run id)] (run-result id run overrides)))
