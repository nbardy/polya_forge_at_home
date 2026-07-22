#!/usr/bin/env bb

(require '[babashka.process :as process]
         '[cheshire.core :as json]
         '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.string :as str])

(import '[java.io File RandomAccessFile]
        '[java.nio.charset StandardCharsets]
        '[java.nio.file Files LinkOption StandardCopyOption]
        '[java.security MessageDigest]
        '[java.time ZonedDateTime]
        '[java.time.format DateTimeFormatter]
        '[java.util.concurrent Callable Executors TimeUnit])

(def engine-root (-> *file* io/file .getCanonicalFile .getParentFile))
(def repo-root (.getParentFile engine-root))
(def problems-root (io/file repo-root "problems"))
(def local-root (io/file repo-root ".forge"))
(def runs-root (io/file local-root "runs"))
(def exports-root (io/file local-root "exports"))
(def active-version-file (io/file engine-root "ACTIVE_VERSION"))
(def timestamp-format (DateTimeFormatter/ofPattern "yyyyMMdd'T'HHmmssZ"))
(def human-time-format (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss XXX"))
(def bundle-format-version 1)
(def max-bundle-files 256)
(def max-bundle-file-bytes (* 5 1024 1024))

(def default-options
  {:model "gpt-5.6-sol"
   :effort "high"
   :workers 3
   :brief-limit 3
   :max-invocations 9
   :timeout-minutes 120
   :codex "codex"
   :fixture false})

(defn fail!
  ([message] (throw (ex-info message {})))
  ([message data] (throw (ex-info message data))))

(defn now [] (ZonedDateTime/now))
(defn timestamp [] (.format (now) timestamp-format))
(defn human-time [] (.format (now) human-time-format))
(defn canonical-file [x] (.getCanonicalFile (io/file x)))
(defn canonical-path [x] (.getPath (canonical-file x)))
(defn child [parent & parts] (apply io/file parent parts))
(defn ensure-dir! [x] (doto (io/file x) .mkdirs))
(defn require-file! [x]
  (let [f (io/file x)]
    (when-not (.isFile f) (fail! "Required file is missing" {:path (str f)}))
    f))
(defn require-dir! [x]
  (let [f (io/file x)]
    (when-not (.isDirectory f) (fail! "Required directory is missing" {:path (str f)}))
    f))
(defn read-edn [x] (edn/read-string (slurp (require-file! x))))
(defn read-json [x] (json/parse-string (slurp (require-file! x)) true))

(defn write-text! [x value]
  (let [file (io/file x)
        parent (.getParentFile file)
        temp (io/file parent (str "." (.getName file) ".tmp-" (System/nanoTime)))]
    (ensure-dir! parent)
    (spit temp (str value))
    (Files/move (.toPath temp) (.toPath file)
                (into-array java.nio.file.CopyOption
                            [StandardCopyOption/REPLACE_EXISTING
                             StandardCopyOption/ATOMIC_MOVE]))
    file))

(defn write-json! [x value]
  (write-text! x (str (json/generate-string value {:pretty true}) "\n")))
(defn write-edn! [x value] (write-text! x (str (pr-str value) "\n")))

(defn append-jsonl! [x value]
  (locking (.intern (str "polya-jsonl:" (canonical-path x)))
    (let [file (io/file x)]
      (ensure-dir! (.getParentFile file))
      (spit file (str (json/generate-string value) "\n") :append true))))

(defn sha256-bytes [^bytes bytes]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") bytes)]
    (apply str (map #(format "%02x" (bit-and % 0xff)) digest))))
(defn sha256-file [x] (sha256-bytes (Files/readAllBytes (.toPath (require-file! x)))))

(defn relative-path [root file]
  (-> (.relativize (.toPath (canonical-file root)) (.toPath (canonical-file file)))
      str
      (str/replace File/separator "/")))

(defn inside? [root file]
  (.startsWith (.toPath (canonical-file file)) (.toPath (canonical-file root))))

(defn safe-relative! [root path]
  (when (or (str/blank? path)
            (.isAbsolute (io/file path))
            (some #{".."} (str/split path #"[/\\]+")))
    (fail! "Unsafe relative path" {:path path}))
  (let [file (canonical-file (child root path))]
    (when-not (inside? root file)
      (fail! "Path escapes its root" {:root (canonical-path root) :path path}))
    (when (Files/isSymbolicLink (.toPath (io/file root path)))
      (fail! "Symlinks are forbidden" {:path path}))
    file))

(defn safe-slug [value]
  (let [slug (-> (str value) str/lower-case
                 (str/replace #"[^a-z0-9]+" "-")
                 (str/replace #"(^-+|-+$)" ""))]
    (if (str/blank? slug) "item" slug)))

(defn file-tree [root]
  (for [^File f (file-seq (io/file root)) :when (.isFile f)] f))

(defn tree-hashes [root]
  (into (sorted-map)
        (for [f (file-tree root)] [(relative-path root f) (sha256-file f)])))

(defn hashes-content-hash [hashes]
  (sha256-bytes
   (.getBytes (str/join "\n" (map (fn [[path hash]] (str path " " hash)) hashes))
              StandardCharsets/UTF_8)))

(defn active-version [] (str/trim (slurp (require-file! active-version-file))))
(defn version-root [] (child engine-root "versions" (active-version)))
(defn version-config [] (read-edn (child (version-root) "version.edn")))

(defn engine-content-hash []
  (hashes-content-hash
   (into (sorted-map)
         (concat
          [["ACTIVE_VERSION" (sha256-file active-version-file)]
           ["forge.clj" (sha256-file (child engine-root "forge.clj"))]]
          (for [[path hash] (tree-hashes (version-root))]
            [(str "versions/" (active-version) "/" path) hash])))))

(defn problem-dirs []
  (sort-by #(.getName ^File %)
           (for [^File f (or (seq (.listFiles (require-dir! problems-root))) [])
                 :when (and (.isDirectory f) (.isFile (child f "problem.edn")))] f)))

(defn find-problem [problem-id]
  (let [dir (child problems-root problem-id)]
    (when-not (and (re-matches #"[a-z0-9]+(?:-[a-z0-9]+)*" problem-id)
                   (.isFile (child dir "problem.edn")))
      (fail! "Unknown problem" {:problem problem-id}))
    {:dir (canonical-file dir) :manifest (read-edn (child dir "problem.edn"))}))

(def required-problem-keys
  #{:format-version :id :title :status :target :instructions :memory :catalog
    :retired-routes :goals :official-source :admission :budgets})
(def allowed-statuses #{:open :solved :benchmark :inactive})

(defn validate-problem! [dir manifest]
  (let [missing (seq (remove #(contains? manifest %) required-problem-keys))]
    (when missing (fail! "Problem manifest is missing fields" {:dir (str dir) :missing missing})))
  (when-not (= 1 (:format-version manifest)) (fail! "Unsupported problem format" {:id (:id manifest)}))
  (when-not (= (.getName (io/file dir)) (:id manifest))
    (fail! "Problem ID must equal its directory name" {:id (:id manifest)}))
  (when-not (allowed-statuses (:status manifest))
    (fail! "Invalid problem status" {:id (:id manifest) :status (:status manifest)}))
  (doseq [key [:target :instructions :memory :catalog :retired-routes]]
    (require-file! (safe-relative! dir (get manifest key))))
  (require-dir! (safe-relative! dir (:goals manifest)))
  (when-not (re-matches #"https://.+" (:official-source manifest))
    (fail! "official-source must be HTTPS" {:id (:id manifest)}))
  (let [budgets (:budgets manifest)]
    (doseq [key [:max-briefs :max-workers :max-invocations :max-wall-minutes]]
      (when-not (pos-int? (get budgets key))
        (fail! "Invalid positive problem budget" {:id (:id manifest) :budget key}))))
  {:id (:id manifest) :title (:title manifest) :status (:status manifest)})

(defn validate-version! []
  (let [root (version-root)
        config (read-edn (child root "version.edn"))]
    (when-not (= (active-version) (:version config))
      (fail! "ACTIVE_VERSION disagrees with version.edn"))
    (when-not (= #{:manage :execute :verify :remember :review}
                 (set (keys (:capabilities config))))
      (fail! "Engine version lacks required capabilities"))
    (doseq [[role {:keys [prompt schema]}] (:capabilities config)]
      (let [prompt-file (require-file! (safe-relative! root prompt))
            schema-file (require-file! (safe-relative! root schema))]
        (when (str/blank? (slurp prompt-file)) (fail! "Blank prompt" {:role role}))
        (let [parsed (read-json schema-file)]
          (when-not (= "object" (:type parsed)) (fail! "Schema root must be object" {:role role})))))
    {:version (:version config)
     :files (count (file-tree root))
     :version-content-hash (hashes-content-hash (tree-hashes root))
     :content-hash (engine-content-hash)}))

(defn validate-repository! []
  (doseq [schema ["problem.schema.json" "contribution.schema.json" "result-card.schema.json"]]
    (read-json (child repo-root "schemas" schema)))
  (let [version (validate-version!)
        problems (mapv (fn [dir] (validate-problem! dir (read-edn (child dir "problem.edn"))))
                       (problem-dirs))]
    (when-not (= 7 (count problems))
      (fail! "Expected seven initial Millennium problem packs" {:count (count problems)}))
    {:status "VALID" :engine version :problems problems}))

(def required-goal-headings
  ["**Status:**" "**Problem:**" "**Exact objective:**" "**Main claim or deliverable:**"
   "**First open line:**" "**Completion criteria:**" "**Kill criteria:**"
   "**Verification budget:**"])

(defn validate-goal! [problem goal-path]
  (let [goals-dir (safe-relative! (:dir problem) (get-in problem [:manifest :goals]))
        goal (canonical-file goal-path)]
    (require-file! goal)
    (when-not (inside? goals-dir goal)
      (fail! "Goal must live inside the selected problem's goals directory"
             {:goal (canonical-path goal) :goals (canonical-path goals-dir)}))
    (let [text (slurp goal)]
      (doseq [heading required-goal-headings]
        (when-not (str/includes? text heading)
          (fail! "Goal is missing a required field" {:field heading})))
      {:file goal :text text :hash (sha256-file goal)})))

(def goal-budget-patterns
  {:brief-limit #"(?m)^- \*\*Maximum briefs:\*\*\s*(\d+)\s*$"
   :workers #"(?m)^- \*\*Maximum active workers:\*\*\s*(\d+)\s*$"
   :max-invocations #"(?m)^- \*\*Maximum model invocations:\*\*\s*(\d+)\s*$"
   :timeout-minutes #"(?m)^- \*\*Maximum wall time:\*\*\s*(\d+)\s+minutes\s*$"})

(defn apply-goal-limits [opts goal]
  (reduce-kv
   (fn [result key pattern]
     (if-let [[_ value] (re-find pattern (:text goal))]
       (update result key min (Long/parseLong value))
       result))
   opts goal-budget-patterns))

(def value-flags
  {"--goal" :goal "--model" :model "--effort" :effort "--workers" :workers
   "--brief-limit" :brief-limit "--max-invocations" :max-invocations
   "--timeout-minutes" :timeout-minutes "--codex" :codex})
(def integer-flags #{:workers :brief-limit :max-invocations :timeout-minutes})

(defn parse-options [args]
  (loop [xs args opts default-options]
    (if (empty? xs)
      opts
      (let [flag (first xs) value (second xs) key (value-flags flag)]
        (when-not key (fail! "Unknown option" {:option flag}))
        (when-not value (fail! "Option requires a value" {:option flag}))
        (recur (nnext xs)
               (assoc opts key (if (integer-flags key)
                                 (try (Long/parseLong value)
                                      (catch Exception _ (fail! "Expected integer" {:option flag})))
                                 value)))))))

(defn bounded-options! [problem opts]
  (let [limits (get-in problem [:manifest :budgets])]
    (doseq [[key max-key] [[:workers :max-workers] [:brief-limit :max-briefs]
                           [:max-invocations :max-invocations]
                           [:timeout-minutes :max-wall-minutes]]]
      (when-not (<= 1 (get opts key) (get limits max-key))
        (fail! "Requested option exceeds problem budget" {:option key :requested (get opts key)
                                                           :maximum (get limits max-key)}))))
  opts)

(defn template [text replacements]
  (reduce-kv (fn [s key value] (str/replace s (str "{{" (name key) "}}") (str value)))
             text replacements))

(defn prompt-file [role]
  (safe-relative! (version-root) (get-in (version-config) [:capabilities role :prompt])))
(defn schema-file [role]
  (safe-relative! (version-root) (get-in (version-config) [:capabilities role :schema])))

(def snapshot-files
  {:target :target :instructions :instructions :memory :memory :catalog :catalog
   :retired :retired-routes})

(defn copy-file! [source destination]
  (ensure-dir! (.getParentFile (io/file destination)))
  (Files/copy (.toPath (require-file! source)) (.toPath (io/file destination))
              (into-array java.nio.file.CopyOption [StandardCopyOption/REPLACE_EXISTING]))
  destination)

(defn create-snapshot! [problem goal run-path]
  (let [snapshot (ensure-dir! (child run-path "snapshot"))]
    (copy-file! (child (:dir problem) "problem.edn") (child snapshot "problem.edn"))
    (copy-file! (:file goal) (child snapshot "goal.md"))
    (copy-file! (child repo-root "AGENTS.md") (child snapshot "repository-instructions.md"))
    (doseq [[output key] snapshot-files]
      (copy-file! (safe-relative! (:dir problem) (get-in problem [:manifest key]))
                  (child snapshot (str (name output) ".md"))))
    snapshot))

(defn snapshot-text [ctx name] (slurp (child (:snapshot ctx) (str name ".md"))))

(defn assert-controls! [ctx]
  (when-not (= (:engine_hash @(:state ctx)) (engine-content-hash))
    (fail! "Engine changed during or after this run; explicit migration is required"))
  (when-not (= (:snapshot_hashes @(:state ctx)) (tree-hashes (:snapshot ctx)))
    (fail! "Immutable run snapshot changed"))
  true)

(defn manager-snapshot [ctx]
  (str "# Repository rules\n\n" (snapshot-text ctx "repository-instructions")
       "\n# Target\n\n" (snapshot-text ctx "target")
       "\n# Problem instructions\n\n" (snapshot-text ctx "instructions")
       "\n# Key learnings\n\n" (snapshot-text ctx "memory")
       "\n# Results catalog\n\n" (snapshot-text ctx "catalog")
       "\n# Retired routes\n\n" (snapshot-text ctx "retired")
       "\n# Goal\n\n" (snapshot-text ctx "goal")))

(defn update-state! [ctx additions]
  (locking (:state ctx)
    (let [next (merge @(:state ctx) additions {:updated_at (human-time)})]
      (reset! (:state ctx) next)
      (write-edn! (child (:run-path ctx) "RUN.edn") next)
      (write-json! (child (:run-path ctx) "RUN.json") next)
      next)))

(defn event! [ctx value]
  (append-jsonl! (child (:run-path ctx) "events.jsonl")
                 (assoc value :recorded_at (human-time))))

(defn acquire-invocation! [ctx]
  (let [n (swap! (:invocations ctx) inc)]
    (when (> n (:max-invocations ctx))
      (fail! "Model invocation budget exhausted" {:limit (:max-invocations ctx)}))
    (update-state! ctx {:invocations n})
    n))

(defn destroy-process! [^Process p]
  (try (doseq [h (iterator-seq (.iterator (.descendants (.toHandle p))))]
         (.destroyForcibly h))
       (catch Exception _ nil))
  (try (.destroyForcibly p) (catch Exception _ nil)))

(defn fixture-result [role]
  (case role
    :manage
    {:manager_review "Deterministic no-model fixture manager."
     :endpoint "Exercise the complete controller pipeline without a mathematical claim."
     :first_open_line "Controller fixture branch."
     :briefs [{:id "FIXTURE" :objective "Exercise execution and verification plumbing."
               :endpoint_edge "No mathematical endpoint; controller fixture only."
               :first_open_line "Write and verify one synthetic structured result."
               :inputs ["Deterministic fixture contract"] :exclusions ["Mathematical claims"]
               :cheapest_falsifier "Reject any non-fixture claim or missing artifact."
               :completion_criteria "Executor and verifier produce schema-compatible results."
               :kill_criteria "Any controller invariant fails."
               :claim_state "question" :needs_verification true}]}
    :execute
    {:disposition "ADVANCE" :summary "Synthetic executor path completed."
     :claim_status "computational-evidence"
     :first_open_line "No mathematical line was attempted."
     :artifacts [] :evidence ["Controller produced this deterministic fixture result."]
     :failed_steps [] :assumption_checks ["Fixture mode made no model call."]
     :next_actions ["Run the independent fixture verifier."]}
    :verify
    {:verdict "PASS" :summary "Synthetic verifier path completed."
     :claim_maturity "computational-evidence"
     :first_invalid_or_open_line "No mathematical statement was audited."
     :evidence ["Builder and verifier artifacts are distinct."]
     :failure_type "none" :failure_scope "none"
     :surviving_core "Controller plumbing only."
     :smallest_repair "none" :reopening_test "Repeat deterministic fixture."}
    :remember
    {:run_summary "Deterministic controller fixture completed."
     :helpful_prior_work [] :always_on_candidates [] :demotions []
     :searchable_additions [] :quarantines [] :withholding_rules []}
    :review
    {:mathematical_progress "None; fixture made no mathematical claim."
     :process_quality "All five capabilities produced structured artifacts."
     :polya_receipt {:advanced_first_open_line false :removed_hypothesis false
                     :killed_route_class false :side_theorem false
                     :explanation "Controller fixture only."}
     :engine_assessment "The deterministic path completed."
     :engine_changes []}))

(defn invoke! [ctx role task-id prompt]
  (assert-controls! ctx)
  (let [attempt (ensure-dir! (child (:run-path ctx) "attempts" task-id (name role)))
        result-file (child attempt "result.json")]
    (cond
      (.isFile result-file)
      (read-json result-file)

      (:fixture ctx)
      (let [invocation (acquire-invocation! ctx)
            result (fixture-result role)]
        (write-text! (child attempt "prompt.md") prompt)
        (event! ctx {:event_type "attempt.started" :role (name role)
                     :task_id task-id :invocation invocation :fixture true})
        (write-json! result-file result)
        (event! ctx {:event_type "attempt.completed" :role (name role)
                     :task_id task-id :duration_ms 0 :fixture true})
        result)

      :else
      (let [invocation (acquire-invocation! ctx)
            prompt-path (child attempt "prompt.md")
            events-path (child attempt "events.jsonl")
            stderr-path (child attempt "stderr.log")
            last-path (child attempt "last-message.json")
            command [(:codex ctx) "exec" "--json" "--skip-git-repo-check"
                     "-C" (canonical-path attempt) "--sandbox" "workspace-write"
                     "-m" (:model ctx) "-c" (str "model_reasoning_effort=\"" (:effort ctx) "\"")
                     "-c" "approval_policy=\"never\""
                     "--output-schema" (canonical-path (schema-file role))
                     "--output-last-message" (canonical-path last-path) "-"]
            builder (ProcessBuilder. ^java.util.List command)]
        (write-text! prompt-path prompt)
        (.directory builder attempt)
        (.redirectOutput builder (java.lang.ProcessBuilder$Redirect/to events-path))
        (.redirectError builder (java.lang.ProcessBuilder$Redirect/to stderr-path))
        (event! ctx {:event_type "attempt.started" :role (name role)
                     :task_id task-id :invocation invocation})
        (let [started (System/nanoTime)
              p (.start builder)]
          (try
            (with-open [stdin (.getOutputStream p)]
              (.write stdin (.getBytes prompt StandardCharsets/UTF_8))
              (.flush stdin))
            (when-not (.waitFor p (:timeout-minutes ctx) TimeUnit/MINUTES)
              (destroy-process! p)
              (fail! "Model attempt timed out" {:role role :task task-id}))
            (when-not (zero? (.exitValue p))
              (fail! "Model attempt failed" {:role role :task task-id
                                              :exit (.exitValue p)
                                              :stderr (when (.isFile stderr-path) (slurp stderr-path))}))
            (let [result (read-json last-path)
                  duration (long (/ (- (System/nanoTime) started) 1000000))]
              (write-json! result-file result)
              (assert-controls! ctx)
              (event! ctx {:event_type "attempt.completed" :role (name role)
                           :task_id task-id :duration_ms duration})
              result)
            (finally (when (.isAlive p) (destroy-process! p)))))))))

(defn brief-markdown [brief]
  (str "# Brief " (:id brief) "\n\n"
       "- **Objective:** " (:objective brief) "\n"
       "- **Endpoint edge:** " (:endpoint_edge brief) "\n"
       "- **First open line:** " (:first_open_line brief) "\n"
       "- **Claim state:** " (:claim_state brief) "\n"
       "- **Cheapest falsifier:** " (:cheapest_falsifier brief) "\n"
       "- **Completion:** " (:completion_criteria brief) "\n"
       "- **Kill:** " (:kill_criteria brief) "\n\n"
       "## Inputs\n\n" (str/join "\n" (map #(str "- " %) (:inputs brief)))
       "\n\n## Exclusions\n\n" (str/join "\n" (map #(str "- " %) (:exclusions brief))) "\n"))

(defn validate-briefs! [ctx briefs]
  (when (> (count briefs) (:brief-limit ctx)) (fail! "Manager exceeded brief limit"))
  (when-not (= (count briefs) (count (distinct (map :id briefs))))
    (fail! "Manager emitted duplicate brief IDs"))
  (doseq [brief briefs]
    (when-not (:needs_verification brief)
      (fail! "Every mathematical brief must request independent verification" {:brief (:id brief)})))
  briefs)

(defn manage! [ctx]
  (let [result-file (child (:run-path ctx) "manager" "result.json")]
    (if (.isFile result-file)
      (read-json result-file)
      (let [prompt (template (slurp (prompt-file :manage))
                             {:BRIEF_LIMIT (:brief-limit ctx) :SNAPSHOT (manager-snapshot ctx)})
            result (invoke! ctx :manage "MANAGER" prompt)
            briefs (validate-briefs! ctx (:briefs result))]
        (ensure-dir! (child (:run-path ctx) "manager"))
        (write-json! result-file result)
        (doseq [brief briefs]
          (write-json! (child (:run-path ctx) "briefs" (str (:id brief) ".json")) brief)
          (write-text! (child (:run-path ctx) "briefs" (str (:id brief) ".md"))
                       (brief-markdown brief)))
        result))))

(defn execute-verify-branch! [ctx brief]
  (let [id (:id brief)
        brief-json (json/generate-string brief {:pretty true})
        execute-prompt (template (slurp (prompt-file :execute))
                                 {:TARGET (snapshot-text ctx "target")
                                  :GOAL (snapshot-text ctx "goal")
                                  :BRIEF brief-json})
        builder (invoke! ctx :execute id execute-prompt)
        verify-prompt (template (slurp (prompt-file :verify))
                                {:TARGET (snapshot-text ctx "target")
                                 :GOAL (snapshot-text ctx "goal")
                                 :BRIEF brief-json
                                 :BUILDER_RESULT (json/generate-string builder {:pretty true})})
        verifier (invoke! ctx :verify id verify-prompt)]
    {:id id :builder builder :verifier verifier}))

(defn execute-all! [ctx briefs]
  (let [pool (Executors/newFixedThreadPool (:workers ctx))
        tasks (mapv (fn [brief]
                      (.submit pool ^Callable (reify Callable
                                                (call [_] (execute-verify-branch! ctx brief)))))
                    briefs)]
    (try
      (mapv #(.get %) tasks)
      (finally
        (.shutdown pool)
        (.awaitTermination pool 10 TimeUnit/SECONDS)))))

(defn run-summary [ctx branches]
  (json/generate-string
   {:problem_id (:problem-id ctx)
    :run_id (:run-id ctx)
    :goal (snapshot-text ctx "goal")
    :branches branches}
   {:pretty true}))

(defn remember! [ctx branches]
  (let [prompt (template (slurp (prompt-file :remember)) {:RUN_SUMMARY (run-summary ctx branches)})]
    (invoke! ctx :remember "MEMORY" prompt)))

(defn review! [ctx branches memory]
  (let [summary (json/generate-string {:branches branches :memory memory} {:pretty true})
        prompt (template (slurp (prompt-file :review)) {:RUN_SUMMARY summary})]
    (invoke! ctx :review "REVIEW" prompt)))

(defn publish-run! [ctx branches memory review]
  (ensure-dir! (child (:run-path ctx) "memory"))
  (ensure-dir! (child (:run-path ctx) "review"))
  (write-json! (child (:run-path ctx) "memory" "memory_delta.json") memory)
  (write-json! (child (:run-path ctx) "review" "review.json") review)
  (write-json! (child (:run-path ctx) "review" "polya_receipt.json") (:polya_receipt review))
  (write-json! (child (:run-path ctx) "review" "engine_changes.json") (:engine_changes review))
  (let [manifest
        {:run_id (:run-id ctx)
         :created_at (human-time)
         :files (into (sorted-map)
                      (for [f (file-tree (:run-path ctx))
                            :let [rel (relative-path (:run-path ctx) f)]
                            :when (not (#{"RUN.edn" "RUN.json" "events.jsonl"} rel))]
                        [rel (sha256-file f)]))}]
    (write-edn! (child (:run-path ctx) "published" "manifest.edn") manifest)
    manifest))

(defn make-context [run-path state]
  (let [opts (merge default-options (:options state))]
    (merge opts
           {:run-id (:run_id state)
            :run-path (canonical-file run-path)
            :problem-id (:problem_id state)
            :snapshot (child run-path "snapshot")
            :state (atom state)
            :invocations (atom (long (or (:invocations state) 0)))})))

(defn pending-invocations [ctx briefs]
  (+
   (reduce +
           (for [brief briefs
                 role [:execute :verify]]
             (if (.isFile (child (:run-path ctx) "attempts" (:id brief)
                                 (name role) "result.json")) 0 1)))
   (if (.isFile (child (:run-path ctx) "attempts" "MEMORY" "remember" "result.json")) 0 1)
   (if (.isFile (child (:run-path ctx) "attempts" "REVIEW" "review" "result.json")) 0 1)))

(defn continue-run! [ctx]
  (try
    (assert-controls! ctx)
    (update-state! ctx {:state "MANAGE"})
    (let [manager (manage! ctx)
          briefs (:briefs manager)
          needed (+ @(:invocations ctx) (pending-invocations ctx briefs))]
      (when (> needed (:max-invocations ctx))
        (fail! "Goal cannot fund builders, verifiers, memory, and review"
               {:needed needed :limit (:max-invocations ctx)}))
      (update-state! ctx {:state "EXECUTE_VERIFY" :brief_count (count briefs)})
      (let [branches (execute-all! ctx briefs)]
        (update-state! ctx {:state "REMEMBER"})
        (let [memory (remember! ctx branches)]
          (update-state! ctx {:state "REVIEW"})
          (let [review (review! ctx branches memory)
                manifest (publish-run! ctx branches memory review)]
            (update-state! ctx {:state "CLOSED" :ended_at (human-time)
                                :published_files (count (:files manifest))})
            {:run_id (:run-id ctx) :state "CLOSED" :run_path (canonical-path (:run-path ctx))}))))
    (catch Exception error
      (event! ctx {:event_type "round.failed" :error (.getMessage error) :data (ex-data error)})
      (update-state! ctx {:state "FAILED" :error (.getMessage error)})
      (throw error))))

(defn with-run-lock [f]
  (ensure-dir! local-root)
  (let [raf (RandomAccessFile. (child local-root "run.lock") "rw")]
    (with-open [raf raf channel (.getChannel raf)]
      (let [lock (try (.tryLock channel) (catch Exception _ nil))]
        (when-not lock (fail! "Another mutating Forge command holds the local lock"))
        (f)))))

(defn start-run! [problem-id opts]
  (let [problem (find-problem problem-id)
        _ (validate-problem! (:dir problem) (:manifest problem))
        _ (when-not (:goal opts) (fail! "run requires --goal PATH"))
        goal (validate-goal! problem (:goal opts))
        opts (bounded-options! problem (apply-goal-limits opts goal))
        run-id (str (timestamp) "_" (format "%06d" (mod (System/nanoTime) 1000000))
                    "_" problem-id "_" (safe-slug (.getName ^File (:file goal))))
        run-path (ensure-dir! (child runs-root run-id))
        snapshot (create-snapshot! problem goal run-path)
        state {:format_version 1 :run_id run-id :problem_id problem-id
               :engine_version (active-version) :engine_hash (engine-content-hash)
               :goal_hash (:hash goal) :snapshot_hashes (tree-hashes snapshot)
               :state "CREATED" :started_at (human-time) :invocations 0
               :options (select-keys opts [:model :effort :workers :brief-limit
                                           :max-invocations :timeout-minutes :codex :fixture])}]
    (write-edn! (child run-path "RUN.edn") state)
    (write-json! (child run-path "RUN.json") state)
    (continue-run! (make-context run-path state))))

(defn run-directories []
  (sort-by #(.getName ^File %) #(compare %2 %1)
           (for [^File f (or (seq (.listFiles (ensure-dir! runs-root))) [])
                 :when (and (.isDirectory f) (.isFile (child f "RUN.edn")))] f)))

(defn find-run [run-id]
  (let [run (safe-relative! runs-root run-id)]
    (require-dir! run)
    run))

(defn resume-run! [run-id]
  (let [run (find-run run-id)
        state (read-edn (child run "RUN.edn"))]
    (when (= "CLOSED" (:state state)) (fail! "Run is already closed" {:run run-id}))
    (continue-run! (make-context run state))))

(def secret-patterns
  [#"(?i)(api[_-]?key|secret|token|password)\s*[:=]\s*[^\s]{8,}"
   #"AKIA[0-9A-Z]{16}"
   #"gh[pousr]_[A-Za-z0-9_]{20,}"])

(defn sanitize-text [text]
  (-> text
      (str/replace (canonical-path repo-root) "${REPOSITORY}")
      (str/replace (System/getProperty "user.home") "${HOME}")))

(defn assert-no-secrets! [text path]
  (doseq [pattern secret-patterns]
    (when (re-find pattern text) (fail! "Potential secret in export" {:path path}))))

(defn export-text! [source destination]
  (let [text (sanitize-text (slurp (require-file! source)))]
    (assert-no-secrets! text (str source))
    (write-text! destination text)))

(defn export-run! [run-id]
  (let [run (find-run run-id)
        state (read-edn (child run "RUN.edn"))]
    (when-not (= "CLOSED" (:state state))
      (fail! "Only closed runs may be exported" {:run run-id :state (:state state)}))
    (let [bundle-id (str run-id "-" (subs (:goal_hash state) 0 12))
          destination (child (ensure-dir! exports-root) bundle-id)]
      (when (.exists destination) (fail! "Export already exists" {:path (str destination)}))
      (ensure-dir! destination)
      (export-text! (child run "snapshot" "goal.md") (child destination "goal.md"))
      (export-text! (child run "snapshot" "target.md") (child destination "target.md"))
      (doseq [brief (file-tree (child run "briefs"))]
        (export-text! brief (child destination "briefs" (.getName ^File brief))))
      (doseq [^File task-dir (or (seq (.listFiles (child run "attempts"))) [])
              :when (.isDirectory task-dir)
              [role output] [["execute" "executor.json"] ["verify" "verifier.json"]]
              :let [source (child task-dir role "result.json")]
              :when (.isFile source)]
        (export-text! source (child destination "results" (.getName task-dir) output)))
      (doseq [[source output]
              [[(child run "memory" "memory_delta.json") (child destination "memory" "memory_delta.json")]
               [(child run "review" "review.json") (child destination "review" "review.json")]
               [(child run "review" "polya_receipt.json") (child destination "review" "polya_receipt.json")]
               [(child run "published" "manifest.edn") (child destination "published" "manifest.edn")]]]
        (export-text! source output))
      (let [files (into (sorted-map)
                        (for [f (file-tree destination)]
                          [(relative-path destination f) (sha256-file f)]))
            manifest {:format_version bundle-format-version :bundle_id bundle-id
                      :run_id run-id :problem_id (:problem_id state)
                      :engine_version (:engine_version state) :goal_hash (:goal_hash state)
                      :created_at (human-time) :files files}]
        (write-json! (child destination "bundle.json") manifest)
        {:status "EXPORTED" :bundle_id bundle-id :path (canonical-path destination)
         :files (count files)}))))

(defn json-key-path [value]
  (if (keyword? value)
    (if-let [prefix (namespace value)] (str prefix "/" (name value)) (name value))
    (str value)))

(defn assert-no-symlinks! [root]
  (loop [pending [(io/file root)]]
    (when-let [file (first pending)]
      (when (Files/isSymbolicLink (.toPath file))
        (fail! "Bundle contains a symlink" {:file (str file)}))
      (recur (into (vec (rest pending))
                   (if (.isDirectory ^File file)
                     (or (seq (.listFiles ^File file)) [])
                     [])))))
  true)

(defn inspect-bundle! [path]
  (let [root (canonical-file path)
        _ (require-dir! root)
        _ (assert-no-symlinks! root)
        all-files (vec (file-tree root))
        _ (when (> (count all-files) max-bundle-files)
            (fail! "Bundle contains too many files" {:count (count all-files)}))
        _ (doseq [f all-files]
            (when (> (.length ^File f) max-bundle-file-bytes)
              (fail! "Bundle file exceeds size limit" {:file (str f)})))
        manifest (read-json (child root "bundle.json"))]
    (when-not (= bundle-format-version (:format_version manifest))
      (fail! "Unsupported bundle format"))
    (doseq [[raw-path expected] (:files manifest)]
      (let [path (json-key-path raw-path)
            file (safe-relative! root path)]
        (require-file! file)
        (when-not (= expected (sha256-file file))
          (fail! "Bundle hash mismatch" {:path path}))))
    (let [declared (set (map json-key-path (keys (:files manifest))))
          actual (set (remove #{"bundle.json"} (map #(relative-path root %) all-files)))]
      (when-not (= declared actual)
        (fail! "Bundle file set differs from manifest"
               {:missing (vec (sort (remove actual declared)))
                :unexpected (vec (sort (remove declared actual)))})))
    (doseq [f all-files :when (not= "bundle.json" (.getName ^File f))]
      (assert-no-secrets! (slurp f) (relative-path root f)))
    {:status "BUNDLE_VALID" :bundle_id (:bundle_id manifest)
     :problem_id (:problem_id manifest) :files (count (:files manifest))}))

(defn command-exists? [command]
  (try (zero? (:exit (process/shell {:continue true :out :string :err :string}
                                    command "--version")))
       (catch Exception _ false)))

(defn doctor []
  (let [checks {:babashka (command-exists? "bb")
                :codex (command-exists? "codex")
                :git (command-exists? "git")}
        okay (every? true? (vals checks))]
    {:status (if okay "READY" "MISSING_DEPENDENCY") :checks checks}))

(defn list-problems []
  (mapv (fn [dir]
          (let [m (read-edn (child dir "problem.edn"))]
            {:id (:id m) :title (:title m) :status (name (:status m))}))
        (problem-dirs)))

(defn dry-run [problem-id opts]
  (let [problem (find-problem problem-id)
        _ (validate-problem! (:dir problem) (:manifest problem))
        _ (when-not (:goal opts) (fail! "dry-run requires --goal PATH"))
        goal (validate-goal! problem (:goal opts))
        opts (bounded-options! problem (apply-goal-limits opts goal))]
    {:status "DRY_RUN_OK" :problem problem-id :problem_status (name (get-in problem [:manifest :status]))
     :goal (canonical-path (:file goal)) :goal_hash (:hash goal)
     :engine_version (active-version)
     :plan ["manage" "parallel execute + independent verify" "remember" "review" "export"]
     :budgets (select-keys opts [:workers :brief-limit :max-invocations :timeout-minutes])}))

(defn recursive-delete! [root]
  (when (.exists (io/file root))
    (doseq [f (reverse (file-seq (io/file root)))] (Files/deleteIfExists (.toPath f)))))

(defn self-test! []
  (let [validation (validate-repository!)
        open-count (count (filter #(= :open (:status %)) (:problems validation)))
        solved-count (count (filter #(= :solved (:status %)) (:problems validation)))
        temp (.toFile (Files/createTempDirectory "polya-bundle-test" (make-array java.nio.file.attribute.FileAttribute 0)))]
    (try
      (write-text! (child temp "goal.md") "safe fixture\n")
      (let [files {"goal.md" (sha256-file (child temp "goal.md"))}
            manifest {:format_version 1 :bundle_id "fixture" :run_id "fixture-run"
                      :problem_id "poincare-conjecture" :engine_version "v0001"
                      :goal_hash (apply str (repeat 64 "0")) :created_at (human-time) :files files}]
        (write-json! (child temp "bundle.json") manifest)
        (inspect-bundle! temp)
        (write-text! (child temp "goal.md") "tampered\n")
        (let [tamper-caught (try (inspect-bundle! temp) false (catch Exception _ true))]
          (when-not tamper-caught (fail! "Tamper fixture was not rejected"))))
      (when-not (= [6 1] [open-count solved-count])
        (fail! "Problem status fixture failed" {:open open-count :solved solved-count}))
      {:status "SELF_TEST_PASS" :problems 7 :open 6 :solved 1
       :engine_hash (get-in validation [:engine :content-hash])}
      (finally (recursive-delete! temp)))))

(defn fixture! []
  (let [goal (child problems-root "poincare-conjecture" "goals" "example-source-audit.md")
        first-pass (with-run-lock
                     #(start-run! "poincare-conjecture"
                                  (assoc default-options :goal (canonical-path goal) :fixture true)))
        run-path (find-run (:run_id first-pass))
        interrupted (assoc (read-edn (child run-path "RUN.edn"))
                           :state "FAILED" :error "Synthetic post-artifact interruption")
        _ (write-edn! (child run-path "RUN.edn") interrupted)
        _ (write-json! (child run-path "RUN.json") interrupted)
        result (with-run-lock #(resume-run! (:run_id first-pass)))
        exported (with-run-lock #(export-run! (:run_id result)))
        inspected (inspect-bundle! (:path exported))]
    {:status "FIXTURE_PASS" :resumed_after_interruption true
     :run result :export exported :inspect inspected}))

(defn print-result [value] (println (json/generate-string value {:pretty true})))

(defn usage []
  (str "Pólya Forge at Home\n\n"
       "bb doctor\n"
       "bb problems\n"
       "bb validate [problem-id]\n"
       "bb test\n"
       "bb fixture\n"
       "bb dry-run <problem-id> --goal PATH [options]\n"
       "bb run <problem-id> --goal PATH [options]\n"
       "bb runs\n"
       "bb resume <run-id>\n"
       "bb export <run-id>\n"
       "bb inspect <bundle-path>\n"))

(defn -main [args]
  (let [command (or (first args) "help") rest-args (vec (rest args))]
    (case command
      "doctor" (print-result (doctor))
      "problems" (print-result {:problems (list-problems)})
      "validate" (if-let [id (first rest-args)]
                     (let [p (find-problem id)] (print-result (validate-problem! (:dir p) (:manifest p))))
                     (print-result (validate-repository!)))
      "test" (print-result (self-test!))
      "fixture" (print-result (fixture!))
      "dry-run" (let [id (first rest-args)]
                    (when-not id (fail! "dry-run requires a problem ID"))
                    (print-result (dry-run id (parse-options (subvec rest-args 1)))))
      "run" (let [id (first rest-args)]
                (when-not id (fail! "run requires a problem ID"))
                (print-result (with-run-lock #(start-run! id (parse-options (subvec rest-args 1))))))
      "runs" (print-result
              {:runs (mapv (fn [run]
                             (select-keys (read-json (child run "RUN.json"))
                                          [:run_id :problem_id :state :invocations :started_at :updated_at]))
                           (run-directories))})
      "resume" (let [id (first rest-args)]
                   (when-not id (fail! "resume requires a run ID"))
                   (print-result (with-run-lock #(resume-run! id))))
      "export" (let [id (first rest-args)]
                   (when-not id (fail! "export requires a run ID"))
                   (print-result (with-run-lock #(export-run! id))))
      "inspect" (let [path (first rest-args)]
                    (when-not path (fail! "inspect requires a bundle path"))
                    (print-result (inspect-bundle! path)))
      "help" (println (usage))
      (fail! "Unknown command" {:command command}))))

(try
  (-main (vec *command-line-args*))
  (catch Exception error
    (binding [*out* *err*]
      (println (json/generate-string {:status "ERROR" :message (.getMessage error)
                                      :data (ex-data error)} {:pretty true})))
    (System/exit 1)))
