(ns kernel.launcher
  "The engine may rewrite itself; this small outside selector never trusts its claims.
   Prelaunch authority fixes its goal, engine, and write scope. Exact pins make
   runs resumable; only closed, tested descendants may affect a later run."
  (:require [babashka.fs :as fs] [cheshire.core :as json] [clojure.edn :as edn]
            [clojure.java.io :as io] [clojure.set :as set] [clojure.string :as str]
            [kernel.codex-app-server :as app-server])
  (:import [java.io PushbackReader RandomAccessFile]
           [java.lang Process ProcessHandle]
           [java.net StandardProtocolFamily UnixDomainSocketAddress]
           [java.nio.channels Channels ServerSocketChannel]
           [java.nio.charset StandardCharsets]
           [java.security MessageDigest] [java.time ZonedDateTime]
           [java.util UUID] [java.util.concurrent TimeUnit TimeoutException]))

(def repo (-> *file* fs/canonicalize fs/parent fs/parent))
(def forge-dir (fs/file repo ".forge"))
(def versions-dir (fs/file forge-dir "versions"))
(def current-file (fs/file forge-dir "CURRENT.edn"))
(def receipts-dir (fs/file forge-dir "activations"))
(def runs-dir (fs/file forge-dir "runs"))
(def campaigns-dir (fs/file forge-dir "campaigns"))
(def problems-dir (fs/file repo "problems"))
(def public-runs-dir (fs/file repo "runs"))
(def trusted-benchmark-id "blinded-endpoint-benchmark")
(def trusted-benchmark-dir
  (fs/file repo "problems" trusted-benchmark-id))
(def trusted-benchmark-goal
  (fs/file trusted-benchmark-dir "goals" "find-token.edn"))
(def trusted-benchmark-digest
  "a132c0c9c4ec6aae5bacf2ddb9d57a32cf6730aedfd27d44a0b5c28697cde1a6")
(def launcher-files
  ["forge.clj" "kernel/launcher.clj" "kernel/codex_app_server.clj"
   "bb.edn" ".codex/config.toml"])
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
(defn run-ledger []
  (let [file (fs/file forge-dir "RUNS.edn")]
    (if (fs/regular-file? file) (read-edn file) {:format-version 1 :runs {}})))
(defn run-assignment [id] (get-in (run-ledger) [:runs id]))
(defn assign-run! [id record]
  (let [ledger (run-ledger) prior (get-in ledger [:runs id])]
    (guard! (= 1 (:format-version ledger)) "Invalid run ledger")
    (if prior
      (guard! (= prior record) "Run assignment changed" {:run-id id})
      (atomic! (fs/file forge-dir "RUNS.edn") (assoc-in ledger [:runs id] record)))
    record))
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
  (text-sha
   (str (tree-sha (fs/file repo "test")) "\n"
        (tree-sha trusted-benchmark-dir) "\n"
        (str/join "\n"
                  (for [path launcher-files :let [file (fs/file repo path)]]
                    (str path " " (sha (fs/read-all-bytes file))))))))
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
(declare compatibility-gate! probation)
(defn reconcile-current! [pin]
  (if-let [activation (probation pin)]
    (let [champion (valid-pin! (:from activation))]
      ;; A confirmed selection writes CURRENT and then its receipt. If the
      ;; launcher dies between those writes, the next launch must not treat the
      ;; still-probationary pointer as a champion.
      (atomic! current-file champion)
      (receipt! {:event :rollback :version pin :to champion
                 :reason :incomplete-confirmation-recovery
                 :activation-run (:run-id activation)
                 :message "Recovered CURRENT pointing at an open probation"})
      champion)
    pin))
(defn current! []
  (if (fs/regular-file? current-file)
    (reconcile-current! (valid-pin! (read-edn current-file)))
    (let [source (fs/file repo "engine")
          future {:version (next-version) :sha256 (tree-sha source)}
          _ (compatibility-gate! source future)
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
  (let [events (receipts)
        indexed (map-indexed vector events)
        activation (last (filter (fn [[_ event]]
                                   (and (= :activate (:event event))
                                        (= pin (:to event))))
                                 indexed))]
    (when activation
      (let [[index event] activation
            decided? (some (fn [later]
                             (and (#{:confirm :rollback} (:event later))
                                  (= pin (:version later))))
                           (drop (inc index) events))]
        (when-not decided? event)))))
(defn open-probations [parent]
  (->> (receipts)
       (filter #(and (= :activate (:event %)) (= parent (:from %))))
       (keep #(probation (:to %)))
       (distinct)
       vec))
(defn run-pin [id]
  (guard! (re-matches #"[A-Za-z0-9][A-Za-z0-9_.+-]*" (str id))
          "Invalid run ID" {:run-id id})
  (let [assigned (run-assignment id)
        _ (guard! assigned "Run lacks launcher-owned authority" {:run-id id})
        manifest (read-edn (fs/file runs-dir id "run.edn"))
        pin {:version (:version manifest) :sha256 (:version-sha256 manifest)}
        frozen-goal (fs/file runs-dir id "input" "goal.edn")]
    (guard! (and (= (:engine assigned) pin)
                 (= (:launcher-sha256 assigned) (launcher-sha))
                 (= (select-keys assigned
                                 [:run-id :problem-id :goal-sha256 :launcher-sha256])
                    (select-keys manifest
                                 [:run-id :problem-id :goal-sha256 :launcher-sha256]))
                 (fs/regular-file? frozen-goal)
                 (= (:goal-sha256 assigned) (sha (fs/read-all-bytes frozen-goal))))
            "Run disagrees with launcher-owned authority" {:run-id id})
    (valid-pin! pin)))

(def inherited-env ["HOME" "PATH" "TMPDIR" "USER" "SHELL" "TERM" "LANG" "LC_ALL"])
(def process-stop-timeout-ms 5000)
(defn process-tree-handles [^Process child]
  (let [root (.toHandle child)
        descendants
        (if (.isAlive root)
          (with-open [stream (.descendants root)]
            (vec (iterator-seq (.iterator stream))))
          [])]
    ;; Retain every handle before killing anything, then stop parents first so
    ;; they cannot spawn beyond the snapshot. Reparenting cannot lose retained handles.
    (into [root] descendants)))
(defn terminate-tree! [^Process child]
  (let [handles (process-tree-handles child)
        deadline (+ (System/currentTimeMillis) process-stop-timeout-ms)]
    (doseq [^ProcessHandle handle handles]
      (when (.isAlive handle) (.destroyForcibly handle)))
    (doseq [^ProcessHandle handle handles
            :let [remaining (- deadline (System/currentTimeMillis))]
            :when (and (.isAlive handle) (pos? remaining))]
      (try
        (.get (.onExit handle) remaining TimeUnit/MILLISECONDS)
        (catch TimeoutException _ nil)))
    (let [alive (filterv #(.isAlive ^ProcessHandle %) handles)]
      (guard! (empty? alive) "Process tree did not terminate"
              {:pids (mapv #(.pid ^ProcessHandle %) alive)}))
    true))
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
        (do (terminate-tree! child) 124)))))
(defn command-timeout [args]
  (if (#{"run" "resume"} (first args)) (* 13 60 60 1000) (* 5 60 1000)))
(defn toml-quote [value]
  (str "\"" (-> (str value) (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))
(defn sandbox-config [writes]
  (str "permissions.polya-forge-engine.filesystem={"
       "\":minimal\"=\"read\",\":tmpdir\"=\"write\",\":slash_tmp\"=\"write\","
       "\":workspace_roots\"={\".\"=\"read\"}"
       (apply str (map #(str "," (toml-quote %) "=\"write\"") writes)) "}"))
(defn sandbox-command
  ([writes command] (sandbox-command writes nil command))
  ([writes socket command]
   (concat ["codex" "sandbox" "-P" "polya-forge-engine"
            "-c" (sandbox-config writes) "-C" (str repo)]
           (when socket ["--allow-unix-socket" (str socket)])
           ["--"]
           command)))
(def model-roles #{:plan :build :verify :remember :reflect})
(defn model-config [engine]
  (let [config (read-edn (fs/file engine "config.edn"))
        model (:model config)
        effort (:effort config)]
    (guard! (and (string? model) (not (str/blank? model))
                 (string? effort) (not (str/blank? effort)))
            "Pinned engine has invalid model configuration")
    config))
(defn model-command [engine role dir pending]
  (let [config (model-config engine)
        model (:model config)
        effort (:effort config)
        schema (fs/file engine "schemas" (str (name role) ".schema.json"))]
    (guard! (and (model-roles role) (string? model) (not (str/blank? model))
                 (string? effort) (not (str/blank? effort))
                 (fs/regular-file? schema) (not (fs/sym-link? schema)))
            "Pinned engine has invalid model configuration")
    [(get config :codex "codex") "exec" "--json" "--ephemeral"
     "--ignore-user-config" "--ignore-rules" "--skip-git-repo-check"
     "-C" (str dir) "--sandbox" "workspace-write" "-m" model
     "-c" (str "model_reasoning_effort=" (toml-quote effort))
     "-c" "approval_policy=\"never\"" "--output-schema" (str schema)
     "--output-last-message" (str pending) "-"]))
(defn model-process!
  ([command dir prompt events stderr timeout-ms]
   (model-process! command dir prompt events stderr timeout-ms nil nil))
  ([command dir prompt events stderr timeout-ms processes stopping?]
   (let [builder (doto (ProcessBuilder. ^java.util.List (mapv str command))
                   (.directory (.toFile (fs/path dir)))
                   (.redirectInput (.toFile (fs/path prompt)))
                   (.redirectOutput (.toFile (fs/path events)))
                   (.redirectError (.toFile (fs/path stderr))))
         process-env (.environment builder)
         _ (.clear process-env)]
     (doseq [key inherited-env
             :let [value (System/getenv key)]
             :when value]
       (.put process-env key value))
     (.put process-env "PYTHONDONTWRITEBYTECODE" "1")
     (let [child (.start builder)]
       (when processes (swap! processes conj child))
       (try
         ;; Registration precedes this check. Either shutdown observes the child
         ;; in the registry, or this branch observes shutdown and reaps it.
         (when (and stopping? @stopping?) (terminate-tree! child))
         (if (.waitFor child timeout-ms TimeUnit/MILLISECONDS)
           (.exitValue child)
           (do (terminate-tree! child) 124))
         (finally
           (when processes (swap! processes disj child))))))))
(defn inherited-model-env []
  (into {"PYTHONDONTWRITEBYTECODE" "1"}
        (keep (fn [key]
                (when-let [value (System/getenv key)] [key value])))
        inherited-env))
(defn append-json-line! [file value]
  (spit file (str (json/generate-string value) "\n") :append true))
(defn broker-request! [engine run processes stopping? request]
  (let [request-keys (set (keys request))
        required #{:op :dir :role :timeout-ms}
        allowed (conj required :conversation-id)]
    (guard! (and (set/subset? required request-keys)
                 (set/subset? request-keys allowed))
            "Malformed model broker request"))
  (let [{:keys [op dir role timeout-ms conversation-id]} request
        calls-root (fs/canonicalize (fs/file run "calls"))
        dir (fs/canonicalize (fs/file (str dir)))
        _ (guard! (and (not @stopping?)
                       (not (fs/regular-file? (fs/file run "close.edn")))
                       (= :invoke op) (model-roles role)
                       (int? timeout-ms) (pos? timeout-ms)
                       (<= timeout-ms (* 13 60 60 1000))
                       (fs/directory? dir) (not (fs/sym-link? dir))
                       (fs/starts-with? dir calls-root))
                  "Model broker request exceeds its assigned run")
        _ (guard! (or (nil? conversation-id)
                      (and (= :build role)
                           (string? conversation-id)
                           (not (str/blank? conversation-id))))
                  "Only a builder may continue a prior conversation")
        prompt (fs/file dir "prompt.md")
        agents (fs/file dir "AGENTS.md")
        events (fs/file dir "events.jsonl")
        stderr (fs/file dir "stderr.log")
        pending (fs/file dir "pending-result.json")]
    (doseq [file [prompt agents]]
      (guard! (and (fs/regular-file? file) (not (fs/sym-link? file))
                   (<= (fs/size file) (* 8 1024 1024)))
              "Model broker input is missing or unsafe" {:path (str file)}))
    (doseq [file [events stderr pending]]
      (guard! (not (fs/sym-link? file))
              "Model broker output is a symlink" {:path (str file)}))
    (if (= :build role)
      (let [config (model-config engine)
            schema-file (fs/file engine "schemas" "build.schema.json")
            _ (guard! (and (fs/regular-file? schema-file)
                           (not (fs/sym-link? schema-file)))
                      "Pinned engine has no safe build schema")
            _ (spit events "")
            result
            (app-server/invoke!
             {:command (app-server/command (get config :codex "codex"))
              :role role :cwd (str dir)
              :model (:model config) :effort (:effort config)
              :prompt (slurp prompt)
              :schema (json/parse-string (slurp schema-file) true)
              :conversation-id conversation-id
              :developer-instructions (slurp agents)
              :timeout-ms timeout-ms :stderr (str stderr)
              :env (inherited-model-env)
              :register! #(swap! processes conj %)
              :unregister! #(swap! processes disj %)
              :stopping? stopping? :terminate! terminate-tree!
              :event! #(append-json-line! events %)})]
        (spit pending (:text result))
        {:exit 0 :conversation-id (:conversation-id result)})
      {:exit (model-process! (model-command engine role dir pending)
                             dir prompt events stderr timeout-ms
                             processes stopping?)})))
(defn broker-connection! [engine run processes stopping? channel]
  (with-open [channel channel
              reader (PushbackReader. (io/reader (Channels/newInputStream channel)))
              writer (io/writer (Channels/newOutputStream channel))]
    (let [reply (try
                  (broker-request! engine run processes stopping? (edn/read reader))
                  (catch Exception error
                    {:error (.getMessage error) :data (ex-data error)}))]
      (binding [*out* writer] (prn reply) (flush)))))
(defn start-model-broker! [engine run]
  (let [root (fs/create-temp-dir {:prefix "polya-forge-broker-"})
        socket (fs/file root "model.sock")
        server (ServerSocketChannel/open StandardProtocolFamily/UNIX)
        workers (atom [])
        processes (atom #{})
        stopping? (atom false)]
    (.bind server (UnixDomainSocketAddress/of (str socket)))
    {:root root :socket socket :server server :workers workers
     :processes processes :stopping? stopping?
     :acceptor
     (future
       (try
         (while (.isOpen server)
           (let [channel (.accept server)]
             (swap! workers conj
                    (future (broker-connection! engine run processes stopping? channel)))))
         (catch Exception error
           (when (.isOpen server) (throw error)))))}))
(defn stop-model-broker!
  [{:keys [root server acceptor workers processes stopping?]}]
  (reset! stopping? true)
  (try (.close ^ServerSocketChannel server) (catch Exception _ nil))
  (deref acceptor process-stop-timeout-ms nil)
  (let [errors (atom [])
        reap! (fn []
                (doseq [^Process child (vec @processes)]
                  (when (.isAlive child)
                    (try (terminate-tree! child)
                         (catch Exception error (swap! errors conj error))))))]
    (reap!)
    (doseq [worker @workers]
      (try (deref worker process-stop-timeout-ms nil)
           (catch Exception error (swap! errors conj error))))
    ;; A worker accepted just before server closure may have registered after
    ;; the first snapshot. The stopping flag makes it self-reap; this second pass
    ;; closes the remaining race before quiescence is asserted.
    (reap!)
    (let [live (filterv #(.isAlive ^Process %) @processes)
          unfinished (filterv #(not (realized? %)) @workers)]
      (guard! (and (realized? acceptor) (empty? live) (empty? unfinished)
                   (empty? @errors))
              "Model broker did not quiesce"
              {:live-pids (mapv #(.pid ^Process %) live)
               :unfinished-workers (count unfinished)
               :errors (mapv #(.getMessage ^Exception %) @errors)})))
  (when (fs/exists? root) (fs/delete-tree root))
  true)
(defn with-model-broker! [engine run f]
  (if-not run
    (f nil)
    (let [broker (start-model-broker! engine run)]
      (try (f (:socket broker))
           (finally (stop-model-broker! broker))))))
(def ^:dynamic *model-run* nil)
(defn engine-command!
  ([engine pin args] (engine-command! engine pin args [] {}))
  ([engine pin args writes extra-env]
   (with-model-broker!
     engine *model-run*
     (fn [socket]
       (process!
        (sandbox-command writes socket
                         (concat ["bb" "--classpath" (str engine)
                                  (str (fs/file engine "forge.clj"))] args))
        (merge {"POLYA_FORGE_REPO_ROOT" (str repo)
                "POLYA_FORGE_VERSION" (:version pin)
                "POLYA_FORGE_VERSION_SHA256" (:sha256 pin)
                "POLYA_FORGE_LAUNCHER_SHA256" (launcher-sha)}
               (when socket {"POLYA_FORGE_MODEL_BROKER" (str socket)})
               extra-env)
        (command-timeout args))))))
(defn run-ids []
  (set (for [dir (if (fs/directory? runs-dir) (fs/list-dir runs-dir) [])
             :when (fs/regular-file? (fs/file dir "run.edn"))] (str (fs/file-name dir)))))
(defn terminal? [run] (fs/regular-file? (fs/file run "close.edn")))
(defn goal-record [value]
  (guard! (and (string? value) (not (str/blank? value))
               (not (fs/absolute? value))
               (not-any? #{".."} (str/split value #"[/\\]+")))
          "Goal path must be repository-relative" {:goal value})
  (let [file (fs/file repo value)
        _ (guard! (and (fs/regular-file? file) (not (fs/sym-link? file))
                       (<= (fs/size file) (* 256 1024)))
                  "Goal is not a bounded regular file" {:goal value})
        bytes (fs/read-all-bytes file)
        _ (guard! (<= (alength bytes) (* 256 1024))
                  "Goal grew while being read" {:goal value})
        goal (edn/read-string (String. bytes StandardCharsets/UTF_8))
        problem (:problem goal)
        root (fs/file problems-dir (str problem) "goals")]
    (guard! (and (= 1 (:format-version goal)) (= :active (:status goal))
                 (string? problem)
                 (re-matches #"[a-z0-9]+(?:-[a-z0-9]+)*" problem)
                 (fs/starts-with? (fs/canonicalize file) (fs/canonicalize root)))
            "Goal is not active inside its declared problem pack" {:goal value})
    {:path value :problem-id problem :sha256 (sha bytes)}))
(defn run-id [_problem] (str (UUID/randomUUID)))
(defn invoke!
  ([pin args] (invoke! pin args nil nil))
  ([pin args assigned-id] (invoke! pin args assigned-id nil))
  ([pin args assigned-id resolved-goal]
   (let [command (first args)
         _ (when (= "run" command)
             (guard! (= 2 (count args)) "Run requires one goal path"))
         _ (when (= "resume" command)
             (guard! (= 2 (count args)) "Resume requires one run ID"))
         resume-id (when (= "resume" command) (second args))
         _ (when resume-id
             (guard! (re-matches #"[A-Za-z0-9][A-Za-z0-9_.+-]*" resume-id)
                     "Invalid assigned run ID" {:run-id resume-id}))
         assigned (when resume-id (run-assignment resume-id))
         _ (when resume-id
             (guard! assigned "Run lacks launcher-owned authority" {:run-id resume-id}))
         goal (when (= "run" command)
                (or resolved-goal (goal-record (second args))))
         _ (when resolved-goal
             (guard! (= (second args) (:path resolved-goal))
                     "Resolved goal path differs from run command"))
         problem (or (:problem-id goal) (:problem-id assigned))
         id (cond (= "run" command) (or assigned-id (run-id problem))
                  resume-id resume-id)
         _ (when id
             (guard! (re-matches #"[A-Za-z0-9][A-Za-z0-9_.+-]*" id)
                     "Invalid assigned run ID" {:run-id id}))
         run (when id (fs/file runs-dir id))
         _ (when (= "run" command)
             (assign-run! id {:run-id id :engine pin :problem-id problem
                              :goal-path (:path goal) :goal-sha256 (:sha256 goal)
                              :launcher-sha256 (launcher-sha)}))
         _ (when (= "run" command) (fs/create-dirs run))
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
         exit (binding [*model-run* run]
                (engine-command! (engine-dir pin) pin args writes
                                 (if (= "run" command)
                                   {"POLYA_FORGE_RUN_ID" id
                                    "POLYA_FORGE_GOAL_SHA256" (:sha256 goal)}
                                   {})))]
     (when-not (zero? exit)
       (fail! "Engine process failed"
              {:exit exit :version (:version pin)
               :new-runs (set/difference (run-ids) before)}))
     (when (= "run" command)
       (let [created (set/difference (run-ids) before)]
         (guard! (= #{id} created) "Engine did not create its assigned run"
                 {:assigned id :runs created})))
     id)))

(defn compatibility-gate! [source pin]
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
                      (str (fs/file repo "test" "runner.clj"))
                      "--compatibility-gate"])
                    env (* 5 60 1000)))
            "Engine compatibility gate failed")
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
        (if (and (= :activate (:event prior))
                 (probation (:to prior)))
          (valid-pin! (:to prior))
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
                (if-let [parent-probation (probation parent)]
                  (do
                    (receipt! {:event :reject :run-id id :version parent
                               :reason :unconfirmed-parent
                               :parent-activation-run (:run-id parent-probation)
                               :message "A probationary challenger cannot produce a descendant"
                               :candidate-sha256 digest
                               :result-sha256 result-sha256
                               :request-sha256 request-sha256
                               :close-sha256 close-sha256})
                    parent)
                  (if (not= parent (current!))
                  (do
                    (receipt! {:event :stale-candidate :run-id id
                               :version parent :candidate-sha256 digest
                               :result-sha256 result-sha256
                               :request-sha256 request-sha256
                               :close-sha256 close-sha256})
                    parent)
                    (if-let [open (first (open-probations parent))]
                      (do
                        (receipt! {:event :reject :run-id id :version parent
                                   :reason :challenger-already-probationary
                                   :open-challenger (:to open)
                                   :message "Select or roll back the open challenger first"
                                   :candidate-sha256 digest
                                   :result-sha256 result-sha256
                                   :request-sha256 request-sha256
                                   :close-sha256 close-sha256})
                        parent)
                      (do
                        (guard! (= #{(:changed_file mutation)} changed)
                                "Reflection must change exactly its declared engine file"
                                {:declared (:changed_file mutation) :actual changed})
                        (let [future {:version (next-version) :sha256 digest}
                              _ (compatibility-gate! source future)
                              installed (install! source)]
                          (guard! (= digest (:sha256 installed))
                                  "Installed candidate hash changed")
                          (receipt! {:event :activate :status :probation
                                     :run-id id :from parent :to installed
                                     :selection-rule
                                     :matched-blinded-admitted-whole-endpoints
                                     :mutation mutation :candidate-sha256 digest
                                     :result-sha256 result-sha256
                                     :request-sha256 request-sha256
                                     :close-sha256 close-sha256})
                          ;; CURRENT remains the champion until a matched blinded
                          ;; tournament produces an independently admitted win.
                          installed))))))))
          (catch Exception error
            (receipt! {:event :reject :run-id id :version parent
                       :message (.getMessage error) :data (ex-data error)
                       :close-sha256
                       (sha (fs/read-all-bytes (fs/file run "close.edn")))})
            parent))))))

(def benchmark-result-keys
  #{:benchmark-id :blinded? :run-id :engine :goal-sha256 :budget
    :whole-endpoint? :independently-admitted? :evidence})
(def benchmark-evidence-keys
  #{:close-sha256 :packet-sha256s :certificate-sha256s})
(defn trusted-benchmark-goal! [path]
  (let [goal (goal-record path)
        file (fs/canonicalize (fs/file repo path))
        manifest (read-edn (fs/file trusted-benchmark-dir "problem.edn"))]
    (guard! (and (= file (fs/canonicalize trusted-benchmark-goal))
                 (= trusted-benchmark-id (:problem-id goal))
                 (= trusted-benchmark-id (:id manifest))
                 (= :benchmark (:status manifest)))
            "Harness selection requires a fixed blinded benchmark goal"
            {:goal path})
    goal))
(defn bounded-edn! [file limit message]
  (guard! (and (fs/regular-file? file) (not (fs/sym-link? file))
               (<= (fs/size file) limit))
          message {:path (str file)})
  (read-edn file))
(defn endpoint-packet-records! [run close]
  (let [ids (get-in close [:endpoint :packet-ids])
        root (fs/file run "packets")
        _ (guard! (and (fs/directory? root) (not (fs/sym-link? root)))
                  "Benchmark packet index is missing or linked")
        files (if (fs/directory? root)
                (vec (fs/glob root "*.edn"))
                [])
        records (mapv (fn [file]
                        {:file file
                         :packet (bounded-edn! file (* 4 1024 1024)
                                               "Unsafe benchmark packet")})
                      files)
        by-id (group-by #(get-in % [:packet :id]) records)]
    (guard! (and (= :endpoint-candidate (:stop close))
                 (= :candidate (get-in close [:endpoint :status]))
                 (= :pending (get-in close [:endpoint :admission]))
                 (vector? ids) (seq ids) (= (count ids) (count (distinct ids))))
            "Benchmark run has no pending whole-endpoint candidate")
    (mapv (fn [id]
            (guard! (and (string? id) (= 1 (count (get by-id id))))
                    "Benchmark endpoint pointer is invalid" {:packet-id id})
            (first (get by-id id)))
          ids)))
(defn safe-run-file! [run relative]
  (guard! (and (string? relative) (not (str/blank? relative))
               (not (fs/absolute? relative))
               (not-any? #{".."} (str/split relative #"[/\\]+")))
          "Unsafe benchmark artifact path" {:path relative})
  (let [file (fs/file run relative)]
    (guard! (and (fs/starts-with? (fs/canonicalize file)
                                  (fs/canonicalize run))
                 (fs/regular-file? file) (not (fs/sym-link? file))
                 (<= (fs/size file) (* 64 1024)))
            "Unsafe benchmark certificate" {:path relative})
    file))
(defn endpoint-certificate [run packet]
  (let [matches (filterv #(= "artifacts/endpoint.edn" (:name %))
                         (:artifacts packet))]
    (when (= 1 (count matches))
      (let [entry (first matches)]
        (when (= #{:name :path :sha256} (set (keys entry)))
          (let [file (safe-run-file! run (:path entry))
                digest (sha (fs/read-all-bytes file))
                value (bounded-edn! file (* 64 1024)
                                    "Malformed benchmark certificate")]
            (when (and (= digest (:sha256 entry))
                       (= #{:format-version :benchmark-id :candidate-token}
                          (set (keys value)))
                       (= 1 (:format-version value))
                       (= trusted-benchmark-id (:benchmark-id value))
                       (string? (:candidate-token value)))
              {:value value :file file :sha256 digest})))))))
(defn trusted-benchmark-audit [run close]
  (let [matches (vec (for [n (range 10000)
                           :let [token (format "PF-%04d" n)]
                           :when (= trusted-benchmark-digest (text-sha token))]
                       token))
        solution (first matches)
        embedded? (some (fn [file]
                          (and (fs/regular-file? file)
                               (str/includes? (slurp (str file)) solution)))
                        (fs/glob trusted-benchmark-dir "**" {:hidden true}))
        records (endpoint-packet-records! run close)
        audited (mapv #(assoc % :certificate
                              (endpoint-certificate run (:packet %)))
                      records)]
    (guard! (= 1 (count matches))
            "Trusted benchmark predicate does not have one endpoint")
    (guard! (not embedded?) "Trusted benchmark pack embeds its plaintext endpoint")
    {:admitted?
     (boolean
      (some
       (fn [{:keys [packet certificate]}]
         (let [verify (:verify packet)
               calls (:calls packet)]
           (and (= solution (get-in certificate [:value :candidate-token]))
                (= "PASS" (:verdict verify))
                (= "CANDIDATE" (:endpoint_disposition verify))
                (nil? (:failure verify))
                (pos-int? (:build calls)) (pos-int? (:verify calls))
                (< (:build calls) (:verify calls)))))
       audited))
     :packet-sha256s
     (into (sorted-map)
           (map (fn [{:keys [file packet]}]
                  [(:id packet) (sha (fs/read-all-bytes file))]) audited))
     :certificate-sha256s
     (into (sorted-map)
           (keep (fn [{:keys [packet certificate]}]
                   (when certificate [(:id packet) (:sha256 certificate)]))
                 audited))}))
(defn benchmark-result [id]
  (let [assigned (run-assignment id)
        _ (guard! assigned "Benchmark run lacks launcher-owned authority"
                  {:run-id id})
        pin (run-pin id)
        run (fs/file runs-dir id)
        close (bounded-edn! (fs/file run "close.edn") (* 2 1024 1024)
                            "Benchmark run is not terminal")
        goal-file (fs/file run "input" "goal.edn")
        goal (bounded-edn! goal-file (* 256 1024) "Missing benchmark goal")
        trusted (trusted-benchmark-goal! (:goal-path assigned))
        whole? (and (= :endpoint-candidate (:stop close))
                    (= :candidate (get-in close [:endpoint :status]))
                    (= :pending (get-in close [:endpoint :admission])))
        audit (if whole?
                (trusted-benchmark-audit run close)
                {:admitted? false :packet-sha256s {}
                 :certificate-sha256s {}})]
    (guard! (= (:sha256 trusted) (:goal-sha256 assigned))
            "Benchmark goal differs from the fixed trusted goal")
    {:benchmark-id trusted-benchmark-id
     :blinded? true
     :run-id id :engine pin
     :goal-sha256 (:goal-sha256 assigned)
     :budget (:budget goal)
     :whole-endpoint? (boolean whole?)
     :independently-admitted? (boolean (:admitted? audit))
     :evidence {:close-sha256
                (sha (fs/read-all-bytes (fs/file run "close.edn")))
                :packet-sha256s (:packet-sha256s audit)
                :certificate-sha256s (:certificate-sha256s audit)}}))
(defn validate-benchmark-result! [value]
  (let [evidence (:evidence value)
        digest-map? #(and (map? %)
                          (every? string? (keys %))
                          (every? (fn [digest]
                                    (boolean (re-matches #"[0-9a-f]{64}" digest)))
                                  (vals %)))]
    (guard! (and (map? value) (= benchmark-result-keys (set (keys value)))
               (string? (:benchmark-id value))
               (true? (:blinded? value))
               (string? (:run-id value))
               (re-matches #"[0-9a-f]{64}" (:goal-sha256 value))
               (map? (:budget value))
               (boolean? (:whole-endpoint? value))
               (boolean? (:independently-admitted? value))
               (or (not (:independently-admitted? value))
                   (:whole-endpoint? value))
               (map? evidence)
               (= benchmark-evidence-keys (set (keys evidence)))
               (re-matches #"[0-9a-f]{64}" (:close-sha256 evidence))
               (digest-map? (:packet-sha256s evidence))
               (digest-map? (:certificate-sha256s evidence))
               (or (not (:independently-admitted? value))
                   (and (seq (:packet-sha256s evidence))
                        (seq (:certificate-sha256s evidence)))))
            "Invalid independently gated benchmark result" {:result value}))
  value)
(defn selection [activation champion-results challenger-results]
  (guard! (and (= :activate (:event activation))
               (= :probation (:status activation)))
          "Selection requires one probationary activation")
  (let [champion-results (mapv validate-benchmark-result! champion-results)
        challenger-results (mapv validate-benchmark-result! challenger-results)
        champion (into {} (map (juxt :benchmark-id identity) champion-results))
        challenger (into {} (map (juxt :benchmark-id identity) challenger-results))
        ids (set (keys champion))]
    (guard! (and (seq ids)
                 (= (count champion-results) (count champion))
                 (= (count challenger-results) (count challenger))
                 (= ids (set (keys challenger))))
            "Champion and challenger benchmark sets are not matched")
    (doseq [id ids
            :let [left (champion id) right (challenger id)]]
      (guard! (= (select-keys left [:benchmark-id :blinded? :goal-sha256 :budget])
                 (select-keys right [:benchmark-id :blinded? :goal-sha256 :budget]))
              "Champion and challenger benchmark budgets differ" {:benchmark id})
      (guard! (and (= (:from activation) (:engine left))
                   (= (:to activation) (:engine right)))
              "Benchmark result engine differs from the activation"
              {:benchmark id}))
    (let [champion-score (count (filter :independently-admitted?
                                        champion-results))
          challenger-score (count (filter :independently-admitted?
                                          challenger-results))
          promote? (> challenger-score champion-score)]
      {:decision (if promote? :confirm :rollback)
       :reason (cond promote? :more-admitted-whole-endpoints
                     (= 0 champion-score challenger-score)
                     :tie-no-admitted-endpoint
                     (= champion-score challenger-score) :tie-retain-champion
                     :else :challenger-lost)
       :champion-score champion-score
       :challenger-score challenger-score
       :benchmarks (vec (sort ids))
       :champion-runs (mapv :run-id champion-results)
       :challenger-runs (mapv :run-id challenger-results)
       :champion-evidence
       (mapv #(select-keys % [:run-id :goal-sha256 :evidence]) champion-results)
       :challenger-evidence
       (mapv #(select-keys % [:run-id :goal-sha256 :evidence]) challenger-results)})))
(defn select-challenger! [activation champion-results challenger-results]
  (let [candidate (valid-pin! (:to activation))
        champion (valid-pin! (:from activation))
        _ (guard! (= activation (probation candidate))
                  "Challenger is no longer probationary")
        _ (guard! (= champion (current!))
                  "Champion changed before benchmark selection")
        result (selection activation champion-results challenger-results)]
    (if (= :confirm (:decision result))
      (do
        (atomic! current-file candidate)
        (receipt! (merge {:event :confirm :version candidate :from champion
                          :selection-rule
                          :matched-blinded-admitted-whole-endpoints}
                         result))
        (assoc result :champion candidate))
      (do
        (atomic! current-file champion)
        (receipt! (merge {:event :rollback :version candidate :to champion
                          :selection-rule
                          :matched-blinded-admitted-whole-endpoints}
                         result))
        (assoc result :champion champion)))))
(def benchmark-baseline-keys #{:present? :value :sha256})
(defn baseline-sha [present? value]
  (text-sha (if present? (pr-str value) "ABSENT")))
(defn benchmark-memory-snapshot [file]
  (if (fs/regular-file? file)
    (let [value (bounded-edn! file (* 4 1024 1024)
                              "Unsafe benchmark memory baseline")]
      {:present? true :value value :sha256 (baseline-sha true value)})
    {:present? false :value nil :sha256 (baseline-sha false nil)}))
(defn validate-benchmark-baseline! [baseline]
  (guard! (and (map? baseline)
               (= benchmark-baseline-keys (set (keys baseline)))
               (boolean? (:present? baseline))
               (= (:sha256 baseline)
                  (baseline-sha (:present? baseline) (:value baseline)))
               (or (:present? baseline) (nil? (:value baseline))))
          "Benchmark memory baseline is invalid")
  baseline)
(defn restore-benchmark-memory! [file baseline]
  (let [baseline (validate-benchmark-baseline! baseline)]
    (if (:present? baseline)
      (atomic! file (:value baseline))
      (when (fs/exists? file) (fs/delete file)))))
(defn benchmark-state-file [activation]
  (fs/file forge-dir "benchmarks"
           (str (:version (:to activation)) ".edn")))
(defn benchmark-state! [activation goals memory-file]
  (let [file (benchmark-state-file activation)
        identity {:format-version 1 :status :running
                  :activation-run (:run-id activation)
                  :champion (:from activation) :challenger (:to activation)
                  :goal-sha256s (mapv :sha256 goals)}]
    (if (fs/regular-file? file)
      (let [state (bounded-edn! file (* 8 1024 1024)
                                "Unsafe benchmark tournament state")]
        (guard! (= identity
                   (select-keys state [:format-version :status :activation-run
                                       :champion :challenger :goal-sha256s]))
                "Benchmark tournament state differs from its activation")
        (validate-benchmark-baseline! (:baseline state))
        {:file file :state state})
      (let [state (assoc identity :started-at (str (ZonedDateTime/now))
                                  :baseline
                                  (benchmark-memory-snapshot memory-file))]
        ;; This is the recovery authority for a kill between either benchmark
        ;; run and the in-process finally block. It must precede all model work.
        (atomic! file state)
        {:file file :state state}))))
(defn run-pinned-benchmark! [pin goal]
  (let [id (run-id (:problem-id goal))]
    (invoke! pin ["run" (:path goal)] id goal)
    (guard! (= pin (run-pin id)) "Benchmark run used the wrong engine")
    (guard! (terminal? (fs/file runs-dir id))
            "Benchmark engine exited without closing its run" {:run-id id})
    id))
(defn benchmark! [paths]
  (guard! (seq paths) "Benchmark requires at least one blinded goal")
  (let [champion (current!)
        activations (open-probations champion)
        _ (guard! (= 1 (count activations))
                  "Benchmark requires exactly one open challenger"
                  {:open-challengers (mapv :to activations)})
        activation (first activations)
        challenger (valid-pin! (:to activation))
        goals (mapv trusted-benchmark-goal! paths)
        _ (guard! (= (count goals) (count (distinct (map :sha256 goals))))
                  "Benchmark goals must be distinct")
        memory-file (fs/file forge-dir "memory" trusted-benchmark-id "INDEX.edn")
        tournament (benchmark-state! activation goals memory-file)
        state-file (:file tournament)
        state (:state tournament)
        baseline (:baseline state)]
    (restore-benchmark-memory! memory-file baseline)
    (receipt! {:event :probation :version challenger :from champion
               :selection-rule :matched-blinded-admitted-whole-endpoints
               :benchmarks (mapv :sha256 goals)
               :tournament (fs/unixify (fs/relativize forge-dir state-file))
               :baseline-sha256 (:sha256 baseline)})
    (try
      (let [champion-results
            (mapv (fn [goal]
                    (restore-benchmark-memory! memory-file baseline)
                    (benchmark-result (run-pinned-benchmark! champion goal)))
                  goals)
            challenger-results
            (mapv (fn [goal]
                    (restore-benchmark-memory! memory-file baseline)
                    (benchmark-result (run-pinned-benchmark! challenger goal)))
                  goals)]
        (restore-benchmark-memory! memory-file baseline)
        (let [selected (select-challenger! activation champion-results
                                           challenger-results)]
          (atomic! state-file
                   (assoc state :status :complete
                                :ended-at (str (ZonedDateTime/now))
                                :selection selected
                                :champion-results champion-results
                                :challenger-results challenger-results))
          selected))
      (catch Exception error
        (restore-benchmark-memory! memory-file baseline)
        (when (probation challenger)
          (atomic! current-file champion)
          (receipt! {:event :rollback :version challenger :to champion
                     :reason :benchmark-failure
                     :selection-rule
                     :matched-blinded-admitted-whole-endpoints
                     :message (.getMessage error) :data (ex-data error)}))
        (atomic! state-file
                 (assoc state :status :failed
                              :ended-at (str (ZonedDateTime/now))
                              :error {:message (.getMessage error)
                                      :data (ex-data error)}))
        (throw error)))))
(defn settle-open-challenger! []
  (let [champion (current!)
        activations (open-probations champion)]
    (if (empty? activations)
      {:status :none :champion champion}
      (let [activation (first activations)
            challenger (:to activation)]
        (try
          (guard! (= 1 (count activations))
                  "Multiple challengers are simultaneously probationary")
          (valid-pin! challenger)
          (assoc (benchmark!
                  [(fs/unixify (fs/relativize repo trusted-benchmark-goal))])
                 :status :selected)
          (catch Exception error
            ;; A tournament is subordinate to the research run that produced
            ;; it. Even setup failures must settle probation and let the closed
            ;; research result stand.
            (when (probation challenger)
              (atomic! current-file champion)
              (receipt! {:event :rollback :version challenger :to champion
                         :reason :automatic-benchmark-failure
                         :selection-rule
                         :matched-blinded-admitted-whole-endpoints
                         :message (.getMessage error) :data (ex-data error)}))
            {:status :failed :champion champion :challenger challenger
             :error {:message (.getMessage error) :data (ex-data error)}}))))))
(defn adopt-source! []
  (let [parent (current!) source (fs/file repo "engine")
        digest (tree-sha source)]
    (guard! (empty? (open-probations parent))
            "Select or roll back the open challenger before adopting source")
    (if (= digest (:sha256 parent))
      parent
      (let [future {:version (next-version) :sha256 digest}
            _ (compatibility-gate! source future)
            installed (install! source)]
        (guard! (= digest (:sha256 installed))
                "Installed source engine changed after testing")
        (receipt! {:event :adopt :from parent :to installed})
        (atomic! current-file installed)
        installed))))
(defn rollback! [pin error]
  (when-let [event (probation pin)]
    (when (= pin (current!))
      (atomic! current-file (:from event))
      (valid-pin! (:from event)))
    (receipt! {:event :rollback :version pin :to (:from event)
               :reason :probationary-run-failure
               :message (.getMessage error) :data (ex-data error)})
    (:from event)))
(defn execute!
  ([args] (execute! args nil nil))
  ([args assigned-id] (execute! args assigned-id nil))
  ([args assigned-id resolved-goal]
   (let [assigned (when assigned-id (run-assignment assigned-id))
         pin (cond
               (= "resume" (first args)) (run-pin (second args))
               assigned (valid-pin! (:engine assigned))
               :else (current!))]
     (try
       (let [id (invoke! pin args assigned-id resolved-goal)]
         (when id
           (guard! (= pin (run-pin id)) "Run did not pin its executing engine")
           (guard! (terminal? (fs/file runs-dir id))
                   "Engine exited without closing its run" {:run-id id})
           (let [close (bounded-edn! (fs/file runs-dir id "close.edn")
                                     (* 2 1024 1024)
                                     "Run has no safe close record")]
             (candidate! id pin)
             ;; Benchmark invocations use invoke! directly, so this cannot
             ;; recurse. A mathematical endpoint pauses immediately and is not
             ;; delayed by harness selection.
             (when-not (= :endpoint-candidate (:stop close))
               (settle-open-challenger!))))
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
(defn campaign-assignment! [state id]
  (when-let [assigned (run-assignment id)]
    (guard!
     (= {:run-id id :problem-id (:problem state) :goal-path (:goal state)
         :goal-sha256 (:goal-sha256 state) :launcher-sha256 (launcher-sha)}
        (select-keys assigned
                     [:run-id :problem-id :goal-path :goal-sha256 :launcher-sha256]))
     "Pending run differs from its campaign" {:run-id id})
    (valid-pin! (:engine assigned))
    assigned))
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
                                   (run-id (:problem state)))]
                  (atomic! file ready)
                  ready))
              pending (:pending-run state)
              started? (fs/regular-file? (fs/file runs-dir pending "run.edn"))
              assigned (campaign-assignment! state pending)
              _ (when started?
                  (guard! assigned "Pending run lacks launcher-owned authority"
                          {:run-id pending}))
              assigned-pin (:engine assigned)
              event (when assigned-pin (probation assigned-pin))
              rolled-pin (when (and assigned-pin
                                    (or (rolled-back? assigned-pin)
                                        (and event (= (:from event) (current!)))))
                           assigned-pin)
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
                  (let [goal
                        (when-not started?
                          (let [goal (goal-record (:goal state))]
                            (guard! (= [(:problem state) (:goal-sha256 state)]
                                       [(:problem-id goal) (:sha256 goal)])
                                    "Campaign goal changed after creation")
                            goal))
                        id (if started?
                             (execute! ["resume" pending])
                             (execute! ["run" (:goal state)] pending goal))
                        close (bounded-edn! (fs/file runs-dir id "close.edn")
                                            (* 2 1024 1024)
                                            "Campaign run has no safe close record")
                        completed
                        (-> state
                            (update :completed inc)
                            (update :runs conj
                                    {:round (inc (:completed state)) :run-id id
                                     :engine (run-pin id) :next-engine (current!)})
                            (dissoc :pending-run))]
                    (if (= :endpoint-candidate (:stop close))
                      (do
                        (guard! (and (= :candidate
                                        (get-in close [:endpoint :status]))
                                     (= :pending
                                        (get-in close [:endpoint :admission]))
                                     (vector? (get-in close
                                                      [:endpoint :packet-ids]))
                                     (seq (get-in close
                                                  [:endpoint :packet-ids])))
                                "Malformed endpoint-candidate close record"
                                {:run-id id})
                        (assoc completed :status :candidate
                               :ended-at (str (ZonedDateTime/now))
                               :endpoint (assoc (:endpoint close) :run-id id)))
                      completed))
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
          (if (= :candidate (:status next))
            (do (atomic! file next) next)
            (recur next)))))))
(defn start-campaign! [rounds args]
  (guard! (and rounds (<= 1 rounds 100)) "Campaign rounds must be 1..100")
  (guard! (= 1 (count args)) "Campaign requires one goal path")
  (let [path (first args) goal (goal-record path)]
    (continue-campaign!
     {:format-version 2
      :id (str "campaign-" (System/currentTimeMillis) "-" (System/nanoTime))
      :started-at (str (ZonedDateTime/now))
      :goal path :problem (:problem-id goal) :goal-sha256 (:sha256 goal)
      :rounds rounds :completed 0 :runs []})))
(defn resume-campaign! [id]
  (let [state (read-edn (campaign-file id))]
    (guard! (and (= id (:id state)) (= 2 (:format-version state))
                 (string? (:goal state))
                 (string? (:problem state))
                 (string? (:goal-sha256 state))
                 (re-matches #"[0-9a-f]{64}" (:goal-sha256 state))
                 (int? (:rounds state)) (<= 1 (:rounds state) 100)
                 (nat-int? (:completed state))
                 (<= (:completed state) (:rounds state))
                 (vector? (:runs state)))
            "Unsupported or malformed campaign manifest")
    (guard! (not (#{:complete :candidate} (:status state)))
            (if (= :candidate (:status state))
              "Campaign is paused on an endpoint candidate"
              "Campaign is already complete"))
    (continue-campaign! state)))
(defn bundle-content-hash [root]
  (let [files (dissoc (tree-map root) "bundle.json")]
    (text-sha
     (str/join "\n" (map (fn [[path digest]] (str path " " digest)) files)))))
(defn validate-public-research! [root id problem]
  (guard! (and (fs/directory? root) (not (fs/sym-link? root)))
          "Missing research export" {:path (str root)})
  (let [entries (vec (take 4098 (fs/glob root "**" {:hidden true})))
        regular (filterv fs/regular-file? entries)
        bundle-file (fs/file root "bundle.json")
        _ (guard! (and (fs/regular-file? bundle-file)
                       (not (fs/sym-link? bundle-file))
                       (<= (fs/size bundle-file) (* 1024 1024)))
                  "Research export has no bounded manifest")
        manifest (json/parse-string (slurp bundle-file) false)]
    (guard! (<= (count regular) 4097) "Research export contains too many files")
    (doseq [entry entries]
      (guard! (not (fs/sym-link? entry)) "Research export contains a symlink")
      (guard! (or (fs/directory? entry) (fs/regular-file? entry))
              "Research export contains an unsupported file")
      (when (fs/regular-file? entry)
        (guard! (and (text-file? entry) (<= (fs/size entry) (* 64 1024 1024)))
                "Research export contains non-auditable content"
                {:path (str entry)})))
    (guard! (and (= 2 (get manifest "format_version"))
                 (= "research" (get manifest "kind"))
                 (= id (get manifest "run_id"))
                 (= problem (get manifest "problem_id"))
                 (re-matches #"[0-9a-f]{64}" (str (get manifest "content_hash")))
                 (= (get manifest "content_hash") (bundle-content-hash root)))
            "Research export manifest is invalid")
    (doseq [rel ["run.edn" "close.edn" "input/goal.edn" "input/problem.edn"]]
      (guard! (fs/regular-file? (fs/file root rel))
              "Research export is incomplete" {:path rel}))
    (guard! (some #(re-matches #"packets/W\d{3}-.+\.edn"
                               (fs/unixify (fs/relativize root %)))
                  regular)
            "Research export contains no verified packet")
    manifest))
(defn publish! [id]
  (guard! (re-matches #"[A-Za-z0-9][A-Za-z0-9_.+-]*" (str id))
          "Publish requires one valid run ID")
  (let [assigned (run-assignment id)
        _ (guard! assigned "Run lacks launcher-owned authority" {:run-id id})
        problem (:problem-id assigned)
        source (fs/file forge-dir "exports" (str id "-research"))
        _ (when-not (fs/directory? source) (execute! ["export" id]))
        manifest (validate-public-research! source id problem)
        destination (fs/file public-runs-dir problem id)]
    (if (fs/exists? destination)
      (do
        (validate-public-research! destination id problem)
        (guard! (= (tree-map source) (tree-map destination))
                "Published run differs from its research export")
        {:status "ALREADY_PUBLISHED" :run-id id :problem-id problem
         :path (str (fs/canonicalize destination))
         :content-hash (get manifest "content_hash")})
      (let [parent (fs/file public-runs-dir problem)
            stage (fs/file parent (str "." id "." (System/nanoTime)))]
        (fs/create-dirs parent)
        (copy-tree! source stage)
        (validate-public-research! stage id problem)
        (fs/move stage destination {:atomic-move true})
        (receipt! {:event :publish :run-id id :problem-id problem
                   :content-hash (get manifest "content_hash")
                   :path (fs/unixify (fs/relativize repo destination))})
        {:status "PUBLISHED" :run-id id :problem-id problem
         :path (str (fs/canonicalize destination))
         :content-hash (get manifest "content_hash")}))))
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
         "benchmark"
         (do (guard! (<= 2 (count args))
                     "Benchmark requires at least one blinded goal")
             (prn (benchmark! (vec (cons rounds rest)))))
         "campaign-resume"
         (do (guard! (= 2 (count args)) "Campaign resume requires one ID")
             (prn (resume-campaign! rounds)))
         "adopt"
         (do (guard! (= 1 (count args)) "Adopt takes no arguments")
             (prn (adopt-source!)))
         "publish"
         (do (guard! (= 2 (count args)) "Publish requires one run ID")
             (prn (publish! rounds)))
         (execute! args)))))
