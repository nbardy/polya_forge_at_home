(ns kernel.fixture
  (:require [babashka.fs :as fs] [cheshire.core :as json]
            [clojure.string :as str]
            [kernel.codex-app-server :as app-server]
            [kernel.launcher :as launcher])
  (:import [java.lang ProcessHandle]))

(def fixture-problems (fs/file launcher/repo "test" "fixtures" "problems"))
(def fixture-goal
  "test/fixtures/problems/harness-fixture/goals/controller.edn")

(def delayed-writer-script "sleep 0.6; printf late > \"$1\"")
(defn delayed-writer-command [pid-file marker]
  ["/bin/sh" "-c"
   (str "/bin/sh -c \"$1\" child \"$2\" & child=$!; "
        "printf '%s' \"$child\" > \"$3\"; wait \"$child\"")
   "parent" delayed-writer-script (str marker) (str pid-file)])
(defn process-handle [pid-file]
  (when (fs/regular-file? pid-file)
    (.orElse (ProcessHandle/of (parse-long (str/trim (slurp pid-file)))) nil)))
(defn alive? [pid-file]
  (boolean (when-let [^ProcessHandle handle (process-handle pid-file)]
             (.isAlive handle))))
(defn stop-leftover! [pid-file]
  (when-let [^ProcessHandle handle (process-handle pid-file)]
    (when (.isAlive handle) (.destroyForcibly handle))))
(defn wait-until [predicate timeout-ms]
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (cond (predicate) true
            (< (System/currentTimeMillis) deadline)
            (do (Thread/sleep 10) (recur))
            :else false))))
(defn timeout-case [root name invoke!]
  (let [pid-file (fs/file root (str name ".pid"))
        marker (fs/file root (str name ".late"))]
    (try
      (let [exit (invoke! (delayed-writer-command pid-file marker))]
        (launcher/guard! (= 124 exit) "Timeout fixture returned the wrong exit"
                         {:case name :exit exit})
        (launcher/guard! (fs/regular-file? pid-file)
                         "Timeout fixture did not start its descendant" {:case name})
        (launcher/guard! (not (alive? pid-file))
                         "Timeout returned before its descendant exited" {:case name})
        (Thread/sleep 800)
        (launcher/guard! (not (fs/exists? marker))
                         "Timed-out descendant wrote after its controller returned"
                         {:case name})
        {:exit exit :descendant-reaped true :late-write false})
      (finally (stop-leftover! pid-file)))))

(defn smoke-process-lifecycle []
  (let [root (fs/create-temp-dir {:prefix "polya-forge-processes-"})
        prompt (fs/file root "prompt.md")]
    (try
      (spit prompt "fixture")
      (let [engine-timeout
            (timeout-case root "engine"
                          #(launcher/process! % {} 150))
            model-timeout
            (timeout-case
             root "model"
             #(launcher/model-process!
               % root prompt (fs/file root "model.events")
               (fs/file root "model.stderr") 150))
            broker (launcher/start-model-broker! root root)
            stopped? (atom false)
            pid-file (fs/file root "broker.pid")
            marker (fs/file root "broker.late")]
        (try
          (let [worker
                (future
                  (launcher/model-process!
                   (delayed-writer-command pid-file marker)
                   root prompt (fs/file root "broker.events")
                   (fs/file root "broker.stderr") 5000
                   (:processes broker) (:stopping? broker)))]
            (swap! (:workers broker) conj worker)
            (launcher/guard!
             (wait-until #(and (fs/regular-file? pid-file)
                               (seq @(:processes broker))) 2000)
             "Broker fixture did not register its active model process")
            (launcher/stop-model-broker! broker)
            (reset! stopped? true)
            (launcher/guard! (and (realized? worker)
                                  (empty? @(:processes broker))
                                  (not (alive? pid-file))
                                  (not (fs/exists? (:root broker))))
                             "Broker shutdown returned before quiescence")
            (Thread/sleep 800)
            (launcher/guard! (not (fs/exists? marker))
                             "Broker-owned descendant wrote after shutdown")
            {:status "PASS" :engine-timeout engine-timeout
             :model-timeout model-timeout :broker-quiescent true})
          (finally
            (stop-leftover! pid-file)
            (when-not @stopped?
              (try (launcher/stop-model-broker! broker)
                   (catch Exception _ nil))))))
      (finally (fs/delete-tree root)))))

(defn broker-call-dir! [run name]
  (let [dir (fs/create-dirs (fs/file run "calls" name))]
    (spit (fs/file dir "prompt.md") "Attack the exact endpoint.")
    (spit (fs/file dir "AGENTS.md") "Write only inside this attempt.")
    dir))

(defn smoke-model-broker-routing []
  (let [run (fs/create-temp-dir {:prefix "polya-forge-broker-routing-"})
        engine (fs/file launcher/repo "engine")
        processes (atom #{}) stopping? (atom false)
        app-calls (atom []) exec-calls (atom [])]
    (try
      (let [fresh (broker-call-dir! run "001-build")
            repair (broker-call-dir! run "002-repair")
            verify (broker-call-dir! run "003-verify")]
        (with-redefs
         [app-server/invoke!
          (fn [options]
            (swap! app-calls conj options)
            ((:event! options)
             {:type "thread.started" :thread_id "durable-builder"})
            {:conversation-id "durable-builder"
             :text (json/generate-string
                    {:claim "fixture" :claim_status "conjecture"
                     :derivation "fixture" :assumptions []
                     :failed_steps [] :verification_needed []
                     :next_actions [] :artifact_paths []})})
          launcher/model-process!
          (fn [& args] (swap! exec-calls conj args) 0)]
          (launcher/guard!
           (= 0 (:exit (launcher/broker-request!
                        engine run processes stopping?
                        {:op :invoke :dir (str fresh) :role :build
                         :timeout-ms 1000})))
           "Fresh builder did not use the model broker")
          (launcher/guard!
           (= 0 (:exit (launcher/broker-request!
                        engine run processes stopping?
                        {:op :invoke :dir (str repair) :role :build
                         :timeout-ms 1000
                         :conversation-id "durable-builder"})))
           "Repair builder did not use the model broker")
          (launcher/guard!
           (= 0 (:exit (launcher/broker-request!
                        engine run processes stopping?
                        {:op :invoke :dir (str verify) :role :verify
                         :timeout-ms 1000})))
           "Verifier did not use an independent exec")
          (launcher/guard!
           (and (= 2 (count @app-calls)) (= 1 (count @exec-calls))
                (nil? (:conversation-id (first @app-calls)))
                (= "durable-builder" (:conversation-id (second @app-calls)))
                (= (str (.getCanonicalFile (java.io.File. (str repair))))
                   (:cwd (second @app-calls)))
                (str/includes? (slurp (fs/file repair "events.jsonl"))
                               "durable-builder")
                (= "fixture"
                   (:claim (json/parse-string
                            (slurp (fs/file repair "pending-result.json")) true))))
           "Broker did not isolate durable builders from fresh verifiers")
          (try
            (launcher/broker-request!
             engine run processes stopping?
             {:op :invoke :dir (str verify) :role :verify :timeout-ms 1000
              :conversation-id "durable-builder"})
            (launcher/fail! "Verifier conversation reuse was accepted")
            (catch Exception error
              (launcher/guard!
               (= "Only a builder may continue a prior conversation"
                  (.getMessage error))
               "Verifier reuse failed for the wrong reason"))))
        {:status "PASS" :durable-builder-calls 2
         :independent-verifier-calls 1})
      (finally (fs/delete-tree run)))))

(defn write-reflection-run! [runs parent id mutation suffix]
  (let [run (fs/file runs id)
        call (fs/file run "calls" "001-reflect")
        source (fs/file call "candidate" "engine")
        result {:assessment "Fixture mutation." :mutation mutation}]
    (launcher/copy-tree! (launcher/engine-dir parent) source)
    (let [changed (fs/file source (:changed_file mutation))]
      (spit changed (str (slurp changed) suffix)))
    (launcher/atomic! (fs/file run "run.edn")
                      {:version (:version parent)
                       :version-sha256 (:sha256 parent)
                       :launcher-sha256 (launcher/launcher-sha)})
    (launcher/atomic! (fs/file call "request.edn")
                      {:call 1 :task "REFLECT" :role :reflect})
    (spit (fs/file call "result.json") (json/generate-string result))
    (launcher/atomic! (fs/file run "close.edn")
                      {:stop :fixture :reflection {:call 1 :result result}})
    run))

(defn tournament-result [engine run-id admitted?]
  {:benchmark-id launcher/trusted-benchmark-id :blinded? true
   :run-id run-id :engine engine
   :goal-sha256 (apply str (repeat 64 "a"))
   :budget {:fanout 1 :invocations 5 :wall-minutes 1}
   :whole-endpoint? admitted? :independently-admitted? admitted?
   :evidence {:close-sha256 (apply str (repeat 64 "c"))
              :packet-sha256s
              (if admitted? {"packet" (apply str (repeat 64 "d"))} {})
              :certificate-sha256s
              (if admitted? {"packet" (apply str (repeat 64 "e"))} {})}})

(defn smoke-evolution []
  (let [root (fs/create-temp-dir {:prefix "polya-forge-launcher-"})
        versions (fs/file root "versions")
        current (fs/file root "CURRENT.edn")
        receipts (fs/file root "activations")
        runs (fs/file root "runs")
        campaigns (fs/file root "campaigns")
        mutation {:changed_file "prompts/reflect.md"
                  :hypothesis "A harmless newline exercises activation."
                  :evidence_refs ["fixture"]
                  :expected_benefit "Exercise the version boundary."
                  :regression_risk "None beyond fixture scope."
                  :benchmark_test "Win a matched blinded endpoint benchmark."}]
    (try
      (with-redefs [launcher/forge-dir root
                    launcher/versions-dir versions
                    launcher/current-file current
                    launcher/receipts-dir receipts
                    launcher/runs-dir runs
                    launcher/campaigns-dir campaigns
                    launcher/problems-dir fixture-problems]
        (let [parent (launcher/install! (fs/file launcher/repo "engine"))
              _ (launcher/atomic! current parent)
              _ (write-reflection-run! runs parent "EVOLUTION" mutation "\n")
              gate-calls (atom 0)
              challenger
              (with-redefs [launcher/compatibility-gate!
                            (fn [_ pin] (swap! gate-calls inc) pin)]
                (launcher/candidate! "EVOLUTION" parent))
              activation (launcher/probation challenger)
              open (tournament-result parent "CHAMPION-OPEN" false)
              challenger-open (tournament-result challenger "CHALLENGER-OPEN" false)
              challenger-win (tournament-result challenger "CHALLENGER-WIN" true)]
          (launcher/guard! (= 1 @gate-calls)
                           "Candidate activation skipped the compatibility gate")
          (launcher/guard! (and (= parent (launcher/current!))
                                (= :probation (:status activation))
                                (= challenger (:to activation)))
                           "Compatibility displaced the champion before a benchmark")
          (let [tie (launcher/selection activation [open] [challenger-open])]
            (launcher/guard!
             (= [:rollback :tie-no-admitted-endpoint 0 0]
                [(:decision tie) (:reason tie)
                 (:champion-score tie) (:challenger-score tie)])
             "An all-zero endpoint tie rewarded partial work"))
          (try
            (launcher/selection
             activation [open]
             [(assoc challenger-open :budget
                     {:fanout 2 :invocations 5 :wall-minutes 1})])
            (launcher/fail! "Mismatched benchmark budgets were accepted")
            (catch Exception error
              (launcher/guard!
               (= "Champion and challenger benchmark budgets differ"
                  (.getMessage error))
               "Mismatched benchmark failed for the wrong reason")))
          (try
            (launcher/selection activation [open]
                                [(assoc challenger-open :packets 99)])
            (launcher/fail! "Partial-work metrics entered harness selection")
            (catch Exception error
              (launcher/guard!
               (= "Invalid independently gated benchmark result"
                  (.getMessage error))
               "Partial metrics failed for the wrong reason")))
          (write-reflection-run! runs challenger "DESCENDANT" mutation "\n\n")
          (launcher/candidate! "DESCENDANT" challenger)
          (launcher/guard!
           (= :unconfirmed-parent (:reason (launcher/decision "DESCENDANT")))
           "An unconfirmed challenger produced a mutation descendant")
          (let [selected (launcher/select-challenger!
                          activation [open] [challenger-win])]
            (launcher/guard! (and (= :confirm (:decision selected))
                                  (= challenger (launcher/current!))
                                  (seq (:challenger-evidence
                                        (last (launcher/receipts)))))
                             "An independently admitted endpoint win was not promoted"))
          ;; Simulate SIGKILL after CURRENT moves but before the confirm receipt.
          ;; The next launcher read must recover the last confirmed champion.
          (write-reflection-run! runs challenger "CRASH-WINDOW" mutation "\n\n")
          (let [crash-candidate
                (with-redefs [launcher/compatibility-gate! (fn [_ pin] pin)]
                  (launcher/candidate! "CRASH-WINDOW" challenger))]
            (launcher/atomic! current crash-candidate)
            (launcher/guard!
             (= challenger (launcher/current!))
             "CURRENT crash window left a probationary challenger active")
            (launcher/guard!
             (= :incomplete-confirmation-recovery
                (:reason (last (launcher/receipts))))
             "CURRENT crash recovery lacked a durable rollback receipt"))
          (write-reflection-run! runs challenger "EVOLUTION-2" mutation "\n\n")
          (let [next-challenger
                (with-redefs [launcher/compatibility-gate! (fn [_ pin] pin)]
                  (launcher/candidate! "EVOLUTION-2" challenger))
                next-activation (launcher/probation next-challenger)
                left (tournament-result challenger "CHAMPION-TIE" false)
                right (tournament-result next-challenger "CHALLENGER-TIE" false)
                selected (launcher/select-challenger!
                          next-activation [left] [right])]
            (launcher/guard! (and (= :rollback (:decision selected))
                                  (= :tie-no-admitted-endpoint (:reason selected))
                                  (= challenger (launcher/current!)))
                             "A zero/zero tie failed to retain the champion"))

          ;; Closed ordinary research runs settle their open mutation without
          ;; operator input. Tournament failure rolls back only the mutation.
          (write-reflection-run! runs challenger "AUTO-SETTLE" mutation "\n\n\n")
          (let [automatic
                (with-redefs [launcher/compatibility-gate! (fn [_ pin] pin)]
                  (launcher/candidate! "AUTO-SETTLE" challenger))
                automatic-activation (launcher/probation automatic)
                settled
                (with-redefs
                 [launcher/benchmark!
                  (fn [_]
                    (launcher/select-challenger!
                     automatic-activation
                     [(tournament-result challenger "AUTO-CHAMPION" false)]
                     [(tournament-result automatic "AUTO-CHALLENGER" true)]))]
                  (launcher/settle-open-challenger!))]
            (launcher/guard!
             (and (= :selected (:status settled))
                  (= automatic (launcher/current!)))
             "Automatic benchmark settlement did not promote its admitted win")
            (write-reflection-run! runs automatic "AUTO-FAIL" mutation "\n\n\n\n")
            (let [failed-candidate
                  (with-redefs [launcher/compatibility-gate! (fn [_ pin] pin)]
                    (launcher/candidate! "AUTO-FAIL" automatic))
                  failed
                  (with-redefs [launcher/benchmark!
                                (fn [_]
                                  (launcher/fail! "Synthetic tournament failure"))]
                    (launcher/settle-open-challenger!))]
              (launcher/guard!
               (and (= :failed (:status failed))
                    (= automatic (launcher/current!))
                    (nil? (launcher/probation failed-candidate)))
               "Tournament failure discarded or stalled the research champion")))

          ;; Launcher-owned authority still rejects a changed run manifest.
          (let [goal (launcher/goal-record fixture-goal)
                id "AUTHORITY"
                input (fs/file runs id "input")
                assignment {:run-id id :engine challenger
                            :problem-id (:problem-id goal)
                            :goal-path (:path goal) :goal-sha256 (:sha256 goal)
                            :launcher-sha256 (launcher/launcher-sha)}]
            (launcher/assign-run! id assignment)
            (fs/create-dirs input)
            (fs/copy (fs/file launcher/repo fixture-goal)
                     (fs/file input "goal.edn"))
            (launcher/atomic! (fs/file runs id "run.edn")
                              {:run-id id :problem-id (:problem-id goal)
                               :goal-sha256 (:sha256 goal)
                               :version (:version challenger)
                               :version-sha256 (:sha256 challenger)
                               :launcher-sha256 (launcher/launcher-sha)})
            (launcher/run-pin id)
            (let [file (fs/file runs id "run.edn")
                  manifest (launcher/read-edn file)]
              (launcher/atomic! file (assoc manifest :problem-id "tampered"))
              (try
                (launcher/run-pin id)
                (launcher/fail! "Tampered run authority was accepted")
                (catch Exception error
                  (launcher/guard!
                   (= "Run disagrees with launcher-owned authority"
                      (.getMessage error))
                   "Authority tamper produced the wrong rejection")))
              (launcher/atomic! file manifest)))

          ;; The live selector derives admission from the trusted packet and
          ;; fixed exhaustive gate; no CLI score or model verdict can supply it.
          (with-redefs [launcher/problems-dir
                        (fs/file launcher/repo "problems")]
            (let [path "problems/blinded-endpoint-benchmark/goals/find-token.edn"
                  goal (launcher/trusted-benchmark-goal! path)
                  id "TRUSTED-BENCHMARK"
                  run (fs/file runs id)
                  input (fs/file run "input")
                  packet-file (fs/file run "packets" "W001-gate.edn")
                  certificate-path
                  "calls/002-w001-gate-build/artifacts/endpoint.edn"
                  certificate-file (fs/file run certificate-path)
                  solution
                  (first (for [n (range 10000)
                               :let [token (format "PF-%04d" n)]
                               :when (= launcher/trusted-benchmark-digest
                                        (launcher/text-sha token))]
                           token))
                  assignment {:run-id id :engine challenger
                              :problem-id (:problem-id goal)
                              :goal-path (:path goal)
                              :goal-sha256 (:sha256 goal)
                              :launcher-sha256 (launcher/launcher-sha)}
                  certificate {:format-version 1
                               :benchmark-id launcher/trusted-benchmark-id
                               :candidate-token solution}]
              (launcher/assign-run! id assignment)
              (fs/create-dirs input)
              (fs/copy launcher/trusted-benchmark-goal
                       (fs/file input "goal.edn"))
              (launcher/atomic! (fs/file run "run.edn")
                                {:run-id id :problem-id (:problem-id goal)
                                 :goal-sha256 (:sha256 goal)
                                 :version (:version challenger)
                                 :version-sha256 (:sha256 challenger)
                                 :launcher-sha256 (launcher/launcher-sha)})
              (launcher/atomic! certificate-file certificate)
              (let [packet {:id "gate"
                            :build {:claim "Submitted the structured endpoint certificate."}
                            :verify {:verdict "PASS"
                                     :endpoint_disposition "CANDIDATE"
                                     :failure nil}
                            :artifacts
                            [{:name "artifacts/endpoint.edn"
                              :path certificate-path
                              :sha256 (launcher/sha
                                       (fs/read-all-bytes certificate-file))}]
                            :calls {:build 2 :verify 3}}]
                (launcher/atomic! packet-file packet)
                (launcher/atomic! (fs/file run "close.edn")
                                  {:stop :endpoint-candidate
                                   :endpoint {:status :candidate
                                              :admission :pending
                                              :packet-ids ["gate"]}})
                (let [admitted (launcher/benchmark-result id)]
                  (launcher/guard!
                   (and (:independently-admitted? admitted)
                        (= 1 (count (get-in admitted
                                           [:evidence :packet-sha256s])))
                        (= 1 (count (get-in admitted
                                           [:evidence :certificate-sha256s]))))
                   "Trusted exhaustive benchmark gate rejected its certificate"))
                ;; The correct token in explicitly negative prose must score
                ;; zero when the structured certificate submits a wrong token.
                (launcher/atomic! certificate-file
                                  (assoc certificate :candidate-token "PF-0000"))
                (launcher/atomic!
                 packet-file
                 (-> packet
                     (assoc-in [:build :claim]
                               (str solution " is not the submitted answer."))
                     (assoc-in [:artifacts 0 :sha256]
                               (launcher/sha
                                (fs/read-all-bytes certificate-file)))))
                (launcher/guard!
                 (not (:independently-admitted? (launcher/benchmark-result id)))
                 "A correct token in negative prose bypassed the certificate gate"))))

          ;; A process kill cannot redefine an answer-bearing memory file as
          ;; the next tournament's clean baseline.
          (let [memory-file (fs/file root "memory"
                                     launcher/trusted-benchmark-id "INDEX.edn")
                activation {:event :activate :status :probation
                            :run-id "BASELINE-ACTIVATION"
                            :from challenger
                            :to {:version "v9999"
                                 :sha256 (apply str (repeat 64 "9"))}}
                goals [{:sha256 (apply str (repeat 64 "8"))}]
                clean {:format-version 1 :runs [] :marker :clean}
                _ (launcher/atomic! memory-file clean)
                first-state (launcher/benchmark-state! activation goals memory-file)
                _ (launcher/atomic! memory-file
                                    (assoc clean :leaked-answer "PF-secret"))
                recovered (launcher/benchmark-state! activation goals memory-file)]
            (launcher/restore-benchmark-memory!
             memory-file (get-in recovered [:state :baseline]))
            (launcher/guard!
             (and (= clean (launcher/read-edn memory-file))
                  (= (get-in first-state [:state :baseline :sha256])
                     (get-in recovered [:state :baseline :sha256])))
             "Benchmark retry adopted post-kill memory as its baseline"))

          ;; A campaign stops at a candidate and never spends another round.
          (let [goal (launcher/goal-record fixture-goal)
                campaign
                (with-redefs
                 [launcher/execute!
                  (fn
                    ([_] (launcher/fail! "Unexpected campaign resume"))
                    ([_ _] (launcher/fail! "Unexpected launch arity"))
                    ([_ assigned _]
                     (launcher/atomic!
                      (fs/file runs assigned "close.edn")
                      {:stop :endpoint-candidate
                       :endpoint {:status :candidate :admission :pending
                                  :packet-ids ["endpoint-packet"]}})
                     assigned))
                  launcher/run-pin (fn [_] challenger)
                  launcher/current! (fn [] challenger)]
                  (launcher/start-campaign! 5 [fixture-goal]))]
            (launcher/guard!
             (and (= :candidate (:status campaign))
                  (= 1 (:completed campaign))
                  (= {:admission :pending :packet-ids ["endpoint-packet"]}
                     (select-keys (:endpoint campaign)
                                  [:admission :packet-ids])))
             "Campaign continued after an endpoint candidate")
            (try
              (launcher/resume-campaign! (:id campaign))
              (launcher/fail! "Endpoint-candidate campaign resumed")
              (catch Exception error
                (launcher/guard!
                 (= "Campaign is paused on an endpoint candidate"
                    (.getMessage error))
                 "Candidate campaign resume failed for the wrong reason"))))

          ;; A claimed endpoint stop with malformed metadata fails closed. It
          ;; must not silently become permission to start another round.
          (let [attempts (atom 0)
                rejected?
                (try
                  (with-redefs
                   [launcher/execute!
                    (fn
                      ([_] (launcher/fail! "Unexpected malformed resume"))
                      ([_ _] (launcher/fail! "Unexpected launch arity"))
                      ([_ assigned _]
                       (swap! attempts inc)
                       (launcher/atomic! (fs/file runs assigned "close.edn")
                                         {:stop :endpoint-candidate})
                       assigned))
                    launcher/run-pin (fn [_] challenger)
                    launcher/current! (fn [] challenger)]
                    (launcher/start-campaign! 5 [fixture-goal]))
                  false
                  (catch Exception error
                    (launcher/guard!
                     (= "Malformed endpoint-candidate close record"
                        (.getMessage error))
                     "Malformed endpoint close failed for the wrong reason")
                    true))
                manifest
                (launcher/read-edn
                 (last (sort-by fs/file-name (fs/glob campaigns "*.edn"))))]
            (launcher/guard!
             (and rejected? (= 1 @attempts) (= :interrupted (:status manifest)))
             "Malformed endpoint close advanced the campaign"))

          ;; An ordinary recovered failure still consumes no campaign round.
          (let [n (atom 0)
                campaign
                (with-redefs
                 [launcher/execute!
                  (fn
                    ([_] (launcher/fail! "Unexpected resume"))
                    ([_ _] (launcher/fail! "Unexpected launch arity"))
                    ([_ assigned _]
                     (if (= 1 (swap! n inc))
                       (throw (ex-info "Fixture rollback"
                                       {:rolled-back-to challenger}))
                       (do
                         (launcher/atomic! (fs/file runs assigned "close.edn")
                                           {:stop :planner-stopped})
                         assigned))))
                  launcher/run-pin
                  (fn [id] {:version id :sha256 (apply str (repeat 64 "0"))})
                  launcher/current! (fn [] challenger)]
                  (launcher/start-campaign! 2 [fixture-goal]))]
            (launcher/guard! (and (= :complete (:status campaign))
                                  (= 2 (:completed campaign))
                                  (= 1 (count (:failed-runs campaign)))
                                  (= 3 @n))
                             "Campaign rollback recovery changed"))
          {:status "PASS"
           :selection [:compatibility :probation :confirm :rollback]
           :zero-tie-retains-champion true
           :current-crash-recovered true
           :automatic-tournament-settlement true
           :descendant-chaining-blocked true
           :trusted-gate-derived-score true
           :durable-benchmark-baseline true
           :malformed-endpoint-failed-closed true
           :candidate-campaign-paused true}))
      (finally (fs/delete-tree root)))))

(defn smoke-publication []
  (let [root (fs/create-temp-dir {:prefix "polya-forge-publication-"})
        public (fs/file root "public-runs")
        receipts (fs/file root "activations")
        id "6f8a3ad4-87ce-4d26-9c03-112233445566"
        problem "harness-fixture"
        source (fs/file root "exports" (str id "-research"))]
    (try
      (with-redefs [launcher/forge-dir root
                    launcher/public-runs-dir public
                    launcher/receipts-dir receipts]
        (launcher/atomic!
         (fs/file root "RUNS.edn")
         {:format-version 1
          :runs {id {:run-id id :problem-id problem}}})
        (doseq [[rel value]
                [["run.edn" {:format-version 1 :run-id id :problem-id problem}]
                 ["close.edn" {:stop :fixture}]
                 ["input/goal.edn" {:format-version 1 :problem problem}]
                 ["input/problem.edn" {:format-version 1 :id problem}]
                 ["packets/W001-fixture.edn" {:id "fixture" :verdict "PASS"}]]]
          (launcher/atomic! (fs/file source rel) value))
        (spit
         (fs/file source "bundle.json")
         (json/generate-string
          {"format_version" 2 "kind" "research" "run_id" id
           "problem_id" problem
           "engine_hash" (apply str (repeat 64 "1"))
           "content_hash" (launcher/bundle-content-hash source)}
          {:pretty true}))
        (let [first-result (launcher/publish! id)
              second-result (launcher/publish! id)
              destination (fs/file public problem id)]
          (launcher/guard!
           (and (= "PUBLISHED" (:status first-result))
                (= "ALREADY_PUBLISHED" (:status second-result))
                (fs/regular-file? (fs/file destination "bundle.json"))
                (not (fs/exists? (fs/file destination "process.edn")))
                (= [:publish] (mapv :event (launcher/receipts))))
           "Public research publication invariant failed")
          {:status "PASS" :path (fs/unixify (fs/relativize public destination))
           :idempotent true :raw-process-public false}))
      (finally (fs/delete-tree root)))))
