(ns forge.fixture
  (:require [babashka.fs :as fs] [clojure.string :as str]
            [forge.core :as core]))

(def problem-id "harness-fixture")
(def fixture-problems-root
  (core/child core/repo-root "test" "fixtures" "problems"))
(def goal-file
  (core/child fixture-problems-root problem-id "goals" "controller.edn"))
(def objective "Exercise one deterministic controller lifecycle and durable resume.")
(def endpoint "Controller regression only; no mathematical endpoint.")
(def first-line "Build and independently audit one synthetic packet.")
(def blinded-benchmark-id "blinded-endpoint-benchmark")
(def blinded-benchmark-digest
  "a132c0c9c4ec6aae5bacf2ddb9d57a32cf6730aedfd27d44a0b5c28697cde1a6")

(defn contains-schema-key? [value target]
  (cond
    (map? value)
    (or (contains? value target)
        (boolean (some #(contains-schema-key? % target) (vals value))))

    (sequential? value)
    (boolean (some #(contains-schema-key? % target) value))

    :else false))

(defn smoke-schema-compatibility []
  (doseq [role [:plan :build :verify :remember :reflect]
          :let [schema (core/read-json
                        (core/role-file core/engine-root "schemas" role
                                        ".schema.json"))]]
    (core/guard
     (not (contains-schema-key? schema :uniqueItems))
     "Model output schema uses unsupported uniqueItems"
     {:role role}))
  {:status "PASS" :unsupported-keywords-absent ["uniqueItems"]})

(defn smoke-artifact-text []
  (let [root (fs/create-temp-dir {:prefix "polya-forge-text-"})
        ruby (core/child root "audit.rb")
        partition (core/child root "rejections-part-01")
        binary (core/child root "compiled-output")]
    (try
      (spit ruby "puts 'auditable'\n")
      (spit partition "1\t2\t3\n")
      (fs/write-bytes binary (byte-array [0 1 2 3]))
      (core/guard (and (core/text-file? ruby)
                       (core/text-file? partition)
                       (not (core/text-file? binary)))
                  "Artifact text detection regressed")
      {:status "PASS" :content-based true}
      (finally (fs/delete-tree root)))))

(defn smoke-blinded-endpoint-benchmark []
  (let [pack (core/child fixture-problems-root blinded-benchmark-id)
        matches (vec (for [n (range 10000)
                           :let [token (format "PF-%04d" n)]
                           :when (= blinded-benchmark-digest
                                    (core/sha256-text token))]
                       token))
        plaintext (first matches)]
    (core/guard (= 1 (count matches))
                "Blinded benchmark does not have one exact endpoint"
                {:matches (count matches)})
    (core/guard (not-any? #(str/includes? (slurp (str %)) plaintext)
                          (core/files pack))
                "Blinded benchmark embeds its plaintext solution")
    {:status "PASS" :domain-size 10000 :unique-preimages 1
     :plaintext-embedded false}))

(defn brief []
  {:id "DIRECT" :parent_packet_ids [] :objective objective
   :endpoint_edge endpoint :first_open_line first-line
   :inputs ["Deterministic fixture"] :exclusions ["Mathematical claims"]
   :cheapest_falsifier "Reject missing evidence."
   :completion_criteria "One independently audited packet."
   :kill_criteria "Any controller invariant fails."})

(defn smoke-parent-scope []
  (let [cross-run-id (apply str (repeat 64 "a"))
        invalid (assoc (brief) :parent_packet_ids [cross-run-id])]
    (try
      (core/validate-plan (core/read-edn goal-file) {:briefs [invalid]} 1 1 [])
      (core/fail "Cross-run evidence was accepted as a current-run parent")
      (catch Exception error
        (core/guard (= "Brief has invalid parent packets" (.getMessage error))
                    "Unexpected parent-scope failure" {:message (.getMessage error)})))
    {:status "PASS" :cross-run-parents-rejected true}))

(def build
  {:claim "The fixture produced one artifact." :claim_status "computational-evidence"
   :work "Ran the deterministic branch." :first_open_line first-line
   :evidence ["A text artifact exists."] :failed_steps [] :assumption_checks []
   :verification_remaining ["Independent fixture audit"] :next_actions ["Verify."]})
(def verify
  {:verdict "PASS" :endpoint_disposition "OPEN"
   :claim_status "computational-evidence"
   :audit "A separate verifier observed the artifact."
   :evidence ["Builder and verifier are separate calls."]
   :first_open_or_invalid_line first-line :failure nil})

(def repair-verify
  {:verdict "REPAIR" :endpoint_disposition "OPEN"
   :claim_status "unresolved"
   :audit "The complete attempt has one exact repairable defect."
   :evidence ["The first invalid line was isolated independently."]
   :first_open_or_invalid_line first-line
   :failure {:scope "Synthetic endpoint defect."
             :surviving_core "The direct construction remains usable."
             :smallest_repair "Correct the marked endpoint step and resubmit the complete result."
             :reopening_test "The independent verifier must pass the corrected complete result."}})

(def candidate-verify
  (assoc verify
         :endpoint_disposition "CANDIDATE"
         :audit "The independent verifier reconstructed the complete finite endpoint."
         :first_open_or_invalid_line
         "No mathematical line remains open; external admission is pending."))

(def fixture-builder-thread "fixture-builder-thread")
(defn write-builder-thread! [dir]
  (core/write-text (core/child dir "events.jsonl")
                   (str "{\"thread_id\":\"" fixture-builder-thread "\"}\n")))

(defn repair-model []
  (fn [role task prompt dir]
    (case role
      :plan
      (cond
        (= task "W001-PLAN") {:briefs [(brief)]}
        (= task "W002-PLAN")
        (let [parent (second (re-find #"\"id\"\s*:\s*\"([0-9a-f]{64})\"" prompt))]
          {:briefs [(assoc (brief) :id "DIRECT_REPAIR"
                           :parent_packet_ids [parent]
                           :inputs ["The verifier's exact smallest repair"])]})
        :else {:briefs []})
      :build
      (do (when (str/includes? task "DIRECT_REPAIR")
            (core/guard (= fixture-builder-thread
                           (:conversation-id
                            (core/read-edn (core/child dir "request.edn"))))
                        "Same-run repair did not retain its builder conversation"))
          (write-builder-thread! dir)
          (core/write-text (core/child dir "artifacts" "proof.md") "fixture")
          (assoc build :work (if (str/includes? task "DIRECT_REPAIR")
                               "Corrected the exact defect and returned the complete result."
                               "Returned a complete result with one defect.")))
      :verify (if (str/includes? task "DIRECT_REPAIR") verify repair-verify)
      :remember {:changes []}
      :reflect {:assessment "No harness mutation is justified." :mutation nil})))

(defn terminal-repair-model []
  (fn [role task prompt dir]
    (case role
      :plan
      (cond
        (= task "W001-PLAN") {:briefs [(brief)]}
        (= task "W002-PLAN")
        (let [parent (second (re-find #"\"id\"\s*:\s*\"([0-9a-f]{64})\"" prompt))]
          {:briefs [(assoc (brief) :id "DIRECT_REPAIR"
                           :parent_packet_ids [parent]
                           :inputs ["The verifier's exact smallest repair"])]})
        :else {:briefs []})
      :build
      (do (when (str/includes? task "DIRECT_REPAIR")
            (core/guard (= fixture-builder-thread
                           (:conversation-id
                            (core/read-edn (core/child dir "request.edn"))))
                        "Terminal repair lost its same-run builder conversation"))
          (write-builder-thread! dir)
          (core/write-text (core/child dir "artifacts" "proof.md") "fixture")
          (assoc build :work "Returned a complete result with one repairable defect."))
      :verify repair-verify
      :remember {:changes []}
      :reflect {:assessment "A terminal repair must survive the run boundary."
                :mutation nil})))

(defn carried-repair-model [repair interrupt?]
  (let [failed? (atom false)
        required [(:packet-id repair) (:smallest-repair repair)
                  (:reopening-test repair)]]
    (fn [role task prompt dir]
      (case role
        :plan
        (if (= task "W001-PLAN")
          (do
            (doseq [value required]
              (core/guard (str/includes? prompt value)
                          "First plan did not receive the exact terminal repair"
                          {:missing value}))
            {:briefs [(assoc (brief) :id "CARRIED_REPAIR"
                             :inputs [(str "Implement frozen repair packet "
                                           (:packet-id repair))])]})
          {:briefs []})
        :build
        (do
          (doseq [value required]
            (core/guard (str/includes? prompt value)
                        "Builder did not receive the exact terminal repair"
                        {:missing value}))
          (when (and interrupt? (compare-and-set! failed? false true))
            (core/fail "Synthetic carried-repair interruption"))
          (core/guard (= (:conversation-id repair)
                         (:conversation-id
                          (core/read-edn (core/child dir "request.edn"))))
                      "Cross-run repair did not resume its exact builder conversation")
          (write-builder-thread! dir)
          (core/write-text (core/child dir "artifacts" "proof.md") "repaired fixture")
          (assoc build :work "Implemented the carried repair against the complete endpoint."))
        :verify verify
        :remember {:changes []}
        :reflect {:assessment "The carried repair lifecycle passed." :mutation nil}))))

(defn artifact-failure-model []
  (fn [role task _ dir]
    (case role
      :plan {:briefs (if (= task "W001-PLAN") [(brief)] [])}
      :build (do (core/write-bytes (core/child dir "artifacts" "cache.pyc")
                                   (byte-array [0 1 2 3]))
                 build)
      :verify verify
      :remember {:changes []}
      :reflect {:assessment "The branch failure was preserved." :mutation nil})))

(defn endpoint-candidate-model []
  (fn [role task _ dir]
    (case role
      :plan (do
              (core/guard (= "W001-PLAN" task)
                          "Planner ran after an endpoint candidate")
              {:briefs [(brief)]})
      :build (do (core/write-text (core/child dir "artifacts" "proof.md")
                                  "complete finite fixture")
                 (assoc build
                        :claim "The complete finite fixture endpoint holds."
                        :verification_remaining []))
      :verify candidate-verify
      :remember {:changes []}
      :reflect {:assessment "An endpoint candidate pauses for admission; no mutation."
                :mutation nil})))

(defn fake-model
  ([interrupt?] (fake-model interrupt? false))
  ([interrupt? empty?]
   (let [failed? (atom false)]
     (fn [role task prompt dir]
       (case role
         :plan {:briefs (if (and (not empty?) (= task "W001-PLAN")) [(brief)] [])}
         :build
         (do (when (and interrupt? (compare-and-set! failed? false true))
               (core/fail "Synthetic interruption"))
             (core/write-text (core/child dir "artifacts" "proof.md") "fixture")
             build)
         :verify
         (do (core/guard (str/includes? prompt "proof.md")
                         "Verifier did not receive the artifact")
             verify)
         :remember
         (if empty?
           {:changes []}
           (let [id (second (re-find #"\"id\"\s*:\s*\"([0-9a-f]{64})\"" prompt))]
             {:changes [{:kind "preserve-salvage" :packet_ids [id]
                         :future_use "Avoid repeating the fixture."}]}))
         :reflect {:assessment "No mutation is justified." :mutation nil})))))

(defn isolated [f]
  (let [root (fs/create-temp-dir {:prefix "polya-forge-engine-"})]
    (try
      (with-redefs [core/forge-root root
                    core/runs-root (core/child root "runs")
                    core/problems-root fixture-problems-root]
        (f root))
      (finally (fs/delete-tree root)))))

(defn isolated-problem-copy [f]
  (let [root (fs/create-temp-dir {:prefix "polya-forge-cross-run-"})
        problems (core/child root "problems")]
    (try
      (core/copy-tree fixture-problems-root problems)
      (with-redefs [core/forge-root root
                    core/runs-root (core/child root "runs")
                    core/problems-root problems]
        (f root (core/child problems problem-id "goals" "controller.edn")))
      (finally (fs/delete-tree root)))))

(defn roles [ctx]
  (mapv :role (map core/call-record (core/call-dirs ctx))))

(defn smoke-round []
  (isolated
   (fn [root]
     (let [result (core/start-run goal-file {:fake (fake-model false)})
           run (core/find-run (:run-id result))
           ctx (core/context run {})
           packets (core/completed-packets ctx)
           memory (core/read-edn (core/child root "memory" problem-id
                                             "INDEX.edn"))]
       (core/guard (= :planner-stopped (get-in result [:close :stop]))
                   "Round did not close")
       (core/guard (= [:plan :build :verify :plan :remember :reflect] (roles ctx))
                   "Round role sequence changed")
       (core/guard (and (= 1 (count packets))
                        (not= (get-in packets [0 :calls :build])
                              (get-in packets [0 :calls :verify])))
                   "Packet or independent verification invariant failed")
       (core/guard (= 1 (count (:runs memory))) "Cross-run memory was not published")
       (let [empty (core/start-run goal-file {:fake (fake-model false true)})
             empty-ctx (core/context (core/find-run (:run-id empty)) {})]
         (core/guard (= [:plan :remember :reflect] (roles empty-ctx))
                     "Zero-plan round skipped terminal review"))
       {:status "PASS" :packets 1 :calls 6 :zero-plan-review true}))))

(defn smoke-repair-loop []
  (isolated
   (fn [_]
     (let [result (core/start-run goal-file {:fake (repair-model)})
           ctx (core/context (core/find-run (:run-id result)) {})
           packets (core/completed-packets ctx)
           [first-packet repaired] packets]
       (core/guard (= :planner-stopped (get-in result [:close :stop]))
                   "Repair loop did not close normally")
       (core/guard (= ["REPAIR" "PASS"]
                      (mapv #(get-in % [:verify :verdict]) packets))
                   "Verifier repair was not followed by a passing descendant")
       (core/guard (= [(:id first-packet)]
                      (get-in repaired [:brief :parent_packet_ids]))
                   "Repair descendant did not cite the exact failed packet")
       (core/guard
        (and (= fixture-builder-thread
                (get-in first-packet [:conversations :build]))
             (= fixture-builder-thread
                (get-in repaired [:conversations :build])))
        "Repair lineage did not preserve the durable builder thread")
       {:status "PASS" :attempts 2 :repair-parent (:id first-packet)
        :builder-thread fixture-builder-thread}))))

(defn smoke-endpoint-candidate-pause []
  (isolated
   (fn [_]
     (try
       (core/validate-verifier
        (assoc repair-verify :endpoint_disposition "CANDIDATE"))
       (core/fail "A failing verifier result became an endpoint candidate")
       (catch Exception error
         (core/guard
          (= "Endpoint candidate is not a complete independently passing claim"
             (.getMessage error))
          "Endpoint-candidate validation failed for the wrong reason")))
     (let [result (core/start-run goal-file {:fake (endpoint-candidate-model)})
           ctx (core/context (core/find-run (:run-id result)) {})
           packet (first (core/completed-packets ctx))
           close (:close result)]
       (core/guard (= :endpoint-candidate (:stop close))
                   "Research did not pause on the exact endpoint candidate")
       (core/guard (= {:status :candidate
                       :admission :pending
                       :packet-ids [(:id packet)]
                       :mechanisms #{:deterministic-regression}}
                      (:endpoint close))
                   "Endpoint candidate was mislabeled or treated as admitted")
       (core/guard (= [:plan :build :verify :remember :reflect] (roles ctx))
                   "Endpoint candidate either triggered another wave or skipped terminal review")
       {:status "PASS" :stop :endpoint-candidate
        :admission :pending :packets 1}))))

(defn smoke-cross-run-terminal-repair []
  (isolated-problem-copy
   (fn [root goal]
     (let [first-result (core/start-run goal {:fake (terminal-repair-model)})
           first-run (core/find-run (:run-id first-result))
           first-ctx (core/context first-run {})
           first-packets (core/completed-packets first-ctx)
           memory-file (core/child root "memory" problem-id "INDEX.edn")
           first-memory (core/read-edn memory-file)
           repair (-> first-memory :runs last :pending-repairs first)
           second-repair
           (assoc repair
                  :packet-id (apply str (repeat 64 "b"))
                  :smallest-repair "Execute the second independent terminal repair."
                  :reopening-test "Independently reopen the second repaired result.")
           failure (:failure repair-verify)]
       (core/guard (= :planner-stopped (get-in first-result [:close :stop]))
                   "Terminal-repair fixture did not close at its bounded zero plan")
       (core/guard (= ["REPAIR" "REPAIR"]
                      (mapv #(get-in % [:verify :verdict]) first-packets))
                   "Terminal-repair fixture did not preserve the repair chain")
       (core/guard (= 1 (count (get-in first-memory [:runs 0 :pending-repairs])))
                   "Open terminal repair was not published exactly once")
       (core/guard (and (= (:id (last first-packets)) (:packet-id repair))
                        (= (get-in first-ctx [:manifest :goal-sha256])
                           (:goal-sha256 repair))
                        (= (:smallest_repair failure) (:smallest-repair repair))
                        (= (:reopening_test failure) (:reopening-test repair))
                        (= fixture-builder-thread (:conversation-id repair))
                        (= (:build (last first-packets))
                           (get-in repair [:complete-candidate :build])))
                   "Published terminal repair lost exact verifier evidence")

       (try
         (core/validate-plan (core/read-edn goal) {:briefs [(brief)]}
                             1 1 [] [repair])
         (core/fail "Planner was allowed to ignore the carried terminal repair")
         (catch Exception error
           (core/guard
            (= "Planner abandoned a terminal verifier repair from the preceding run"
               (.getMessage error))
            "Unexpected carried-repair validation failure"
            {:message (.getMessage error)})))

       (let [empty-memory-root (core/child root "memory-without-model")]
         (with-redefs [core/forge-root empty-memory-root]
           (core/publish-local-memory first-ctx nil)
           (let [once (core/read-edn
                       (core/child empty-memory-root "memory" problem-id "INDEX.edn"))]
             (core/publish-local-memory first-ctx nil)
             (let [twice (core/read-edn
                          (core/child empty-memory-root "memory" problem-id "INDEX.edn"))]
               (core/guard (and (= once twice) (= 1 (count (:runs twice))))
                           "Mechanical repair publication was not idempotent")
               (core/guard (and (= [] (get-in twice [:runs 0 :changes]))
                                (= repair
                                   (get-in twice [:runs 0 :pending-repairs 0])))
                           "Absent MEMORY lost the mechanically published repair")))))

       ;; One first-wave branch may consume one handoff; other handoffs stay pending.
       (core/write-edn
        memory-file
        (update-in first-memory [:runs 0 :pending-repairs] conj second-repair))
       (let [fake (carried-repair-model repair true)
             interrupted
             (try
               (core/start-run goal {:fake fake})
               (core/fail "Carried-repair fixture did not interrupt")
               (catch Exception error
                 (core/guard (= "Synthetic carried-repair interruption"
                                (.getMessage error))
                             "Unexpected carried-repair fixture failure")
                 (ex-data error)))
             run-id (:run-id interrupted)
             run (core/find-run run-id)
             frozen-input (core/tree-hashes (core/child run "input"))]
         ;; Resume must use the run's frozen handoff even if canonical memory moves on.
         (core/write-edn memory-file
                         {:format-version 1 :problem-id problem-id :runs []})
         (let [ctx-before (core/context run {})]
           (core/guard (= [repair second-repair]
                          (core/pending-cross-run-repairs ctx-before))
                       "Resume read mutable canonical memory instead of frozen input"))
         (let [result (core/resume-run run-id {:fake fake})
               ctx (core/context run {})
               packets (core/completed-packets ctx)
               carried (first packets)
               remaining (-> (core/read-edn memory-file)
                             :runs last :pending-repairs)]
           (core/guard (= frozen-input (core/tree-hashes (core/child run "input")))
                       "Resume changed the frozen terminal-repair handoff")
           (core/guard (= :planner-stopped (get-in result [:close :stop]))
                       "Carried repair did not complete its resumed lifecycle")
           (core/guard (and (= 1 (count packets))
                            (= "PASS" (get-in carried [:verify :verdict]))
                            (empty? (get-in carried [:brief :parent_packet_ids]))
                            (some #(str/includes? % (:packet-id repair))
                                  (get-in carried [:brief :inputs])))
                       "Carried repair became a cross-run parent or was not executed")
           (core/guard (= {:plan 2 :build 2 :verify 1 :remember 1 :reflect 1}
                          (frequencies (roles ctx)))
                       "Carried-repair resume role counts changed")
           (core/guard (= [second-repair] remaining)
                       "An unconsumed terminal repair was dropped at the next boundary")))
       {:status "PASS"
        :terminal-packet (:packet-id repair)
        :absent-memory-published true
        :frozen-resume true
        :unconsumed-repair-preserved true}))))

(defn smoke-branch-failure-closes []
  (isolated
   (fn [_]
     (let [result (core/start-run goal-file {:fake (artifact-failure-model)})
           ctx (core/context (core/find-run (:run-id result)) {})
           failed (filter #(fs/regular-file? (core/child % "error.edn"))
                          (core/call-dirs ctx))]
       (core/guard (= :branch-failure (get-in result [:close :stop]))
                   "Recoverable branch failure crashed instead of closing")
       (core/guard (= [:plan :build :remember :reflect] (roles ctx))
                   "Branch failure skipped terminal memory or reflection")
       (core/guard (= 1 (count failed)) "Branch failure evidence was not preserved")
       {:status "PASS" :terminal-review true :preserved-errors 1}))))

(defn smoke-resume []
  (isolated
   (fn [_]
     (let [fake (fake-model true)
           interrupted
           (try
             (core/start-run goal-file {:fake fake})
             (core/fail "Fixture did not interrupt")
             (catch Exception error
               (core/guard (= "Synthetic interruption" (.getMessage error))
                           "Unexpected fixture failure")
               (ex-data error)))
           run-id (:run-id interrupted)
           run (core/find-run run-id)
           manifest (slurp (core/child run "run.edn"))
           input (core/tree-hashes (core/child run "input"))
           result (core/resume-run run-id {:fake fake})
           ctx (core/context run {})
           failed (filter #(fs/regular-file? (core/child % "error.edn"))
                          (core/call-dirs ctx))]
       (core/guard (= manifest (slurp (core/child run "run.edn")))
                   "Resume changed the manifest")
       (core/guard (= input (core/tree-hashes (core/child run "input")))
                   "Resume changed frozen input")
       (core/guard (= 1 (count failed)) "Failed evidence was not preserved")
       (core/guard (= {:plan 2 :build 2 :verify 1 :remember 1 :reflect 1}
                      (frequencies (roles ctx)))
                   "Resume role counts changed")
       (core/guard (= :planner-stopped (get-in result [:close :stop]))
                   "Resumed run did not close")
       {:status "PASS" :preserved-errors 1 :calls 7}))))
