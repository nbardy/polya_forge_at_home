(ns forge.fixture
  (:require [clojure.string :as str]
            [forge.bundle :as bundle]
            [forge.core :as core]))

(defn brief [id parents line]
  {:id id :parent_packet_ids parents
   :objective (str "Exercise " line ".")
   :endpoint_edge "Controller regression only."
   :first_open_line line
   :inputs ["Deterministic fixture contract"]
   :exclusions ["Mathematical claims"]
   :cheapest_falsifier "Reject missing or unlinked artifacts."
   :completion_criteria "Build and verifier results freeze into one packet."
   :kill_criteria "Any controller invariant fails."})

(defn build-result []
  {:claim "The deterministic controller path produced a fixture artifact."
   :claim_status "computational-evidence"
   :work "Constructed one schema-shaped, non-mathematical fixture result."
   :first_open_line "No mathematical line was attempted."
   :evidence ["The fake model returned this deterministic result."]
   :failed_steps [] :assumption_checks []
   :verification_remaining ["Independent fixture audit"]
   :next_actions ["Run the verifier."]})

(defn fake-model []
  (let [failed-build? (atom false)
        failed-reflect? (atom false)]
    (fn [role task prompt dir]
      (case role
        :plan
        (cond
          (= task "W001-PLAN")
          {:briefs [(brief "BASE" [] "Create the first audited fixture packet.")
                    (brief "SURVIVOR" [] "Preserve a sibling across interruption.")]}

          (= task "W002-PLAN")
          (let [parent (first (re-seq #"[0-9a-f]{64}" prompt))]
            {:briefs [(brief "CHILD" [parent]
                             "Create a successor that cites the first packet.")]})

          :else {:briefs []})

        :build
        (do
          (when (and (str/includes? task "W001-BASE")
                     (compare-and-set! failed-build? false true))
            (core/fail "Synthetic mid-wave interruption"))
          (when (and (str/includes? task "W002")
                     (not (str/includes? prompt "proof.md")))
            (core/fail "Successor did not receive parent artifacts"))
          (core/write-text (core/child dir "artifacts" "proof.md")
                           (str "fixture evidence for " task))
          (build-result))

        :verify
        (do
          (when-not (str/includes? prompt "proof.md")
            (core/fail "Verifier did not receive builder artifacts"))
          {:verdict "PASS" :claim_status "computational-evidence"
           :audit "The build is a deterministic fixture and makes no mathematical claim."
           :evidence ["The build and verifier are separate calls."]
           :first_open_or_invalid_line "No mathematical line was audited."
           :failure nil})

        :remember
        (let [_ (when-not (str/includes? prompt "proof.md")
                  (core/fail "Memory did not receive verified artifacts"))
              ids (mapv second
                        (re-seq #"\"id\"\s*:\s*\"([0-9a-f]{64})\"" prompt))]
          {:changes [{:kind "add-asset"
                      :packet_ids ids
                      :future_use "Regression-test the lean controller."}]})

        :reflect
        (do
          (when (compare-and-set! failed-reflect? false true)
            (core/fail "Synthetic terminal interruption"))
          {:proposals []})))))

(defn fails? [f]
  (try (f) false (catch Exception _ true)))

(defn run-fixture []
  (let [goal (core/child core/problems-root "poincare-conjecture" "goals"
                         "controller-fixture.edn")
        fake (fake-model)
        interrupted
        (try
          (core/with-lock #(core/start-run "poincare-conjecture" goal {:fake fake}))
          (core/fail "Fixture did not interrupt")
          (catch Exception error
            (when-not (= "Synthetic mid-wave interruption" (.getMessage error))
              (throw error))
            (ex-data error)))
        run-id (:run-id interrupted)
        run (core/find-run run-id)
        partial (core/completed-packets {:run run})
        _ (core/guard (= ["SURVIVOR"] (mapv #(get-in % [:brief :id]) partial))
                      "Successful sibling was not frozen before resume")
        failed-build (first (filter #(.exists (core/child % "error.edn"))
                                    (core/call-dirs (core/context run {}))))
        _ (core/write-json (core/child failed-build "result.json") (build-result))
        terminal-interruption
        (try
          (core/with-lock #(core/resume-run run-id {:fake fake}))
          (core/fail "Fixture did not interrupt during reflection")
          (catch Exception error
            (when-not (= "Synthetic terminal interruption" (.getMessage error))
              (throw error))
            (ex-data error)))
        resumed (core/with-lock #(core/resume-run run-id {:fake fake}))
        ctx (core/context run {})
        roles (mapv :role (map core/call-record (core/call-dirs ctx)))
        counts (frequencies roles)
        _ (core/guard (= {:plan 3 :build 4 :verify 3 :remember 1 :reflect 2} counts)
                      "Fixture role counts changed" {:actual counts})
        _ (core/guard (= 1 (count (filter #{:remember} roles)))
                      "Terminal resume reran memory")
        packets (core/completed-packets ctx)
        _ (core/guard (= 3 (count packets))
                      "Fixture did not freeze three packets")
        _ (core/guard (contains? (set (map :id (take 2 packets)))
                                 (first (get-in packets [2 :brief :parent_packet_ids])))
                      "Successor packet lost its parent")
        _ (core/guard (every? #(not= (get-in % [:calls :build])
                                     (get-in % [:calls :verify])) packets)
                      "A builder verified itself")
        packet-file (core/child run "packets" "W001-SURVIVOR.edn")
        packet-text (slurp packet-file)
        packet (core/read-edn packet-file)
        _ (core/write-edn packet-file
                          (assoc packet :id (apply str (repeat 64 "0"))))
        _ (core/guard (fails? #(core/completed-packets ctx))
                      "Packet tampering was accepted")
        _ (core/write-text packet-file packet-text)
        _ (core/completed-packets ctx)
        exported (bundle/export-run run-id)
        research-check (bundle/inspect-bundle (get-in exported [:research :path]))
        harness-check (bundle/inspect-bundle (get-in exported [:harness :path]))
        exported-packet (core/child (get-in exported [:research :path])
                                    "packets" "W001-SURVIVOR.edn")
        exported-text (slurp exported-packet)
        _ (core/write-text exported-packet (str exported-text "\n"))
        _ (core/guard (fails? #(bundle/inspect-bundle
                                (get-in exported [:research :path])))
                      "Bundle tampering was accepted")
        _ (core/write-text exported-packet exported-text)]
    {:status "FIXTURE_PASS" :run resumed :roles roles
     :exports exported :inspection [research-check harness-check]}))

(defn edge-fixtures [goal]
  (let [empty-run (core/with-lock
                    #(core/start-run
                      "poincare-conjecture" goal
                      {:fake (fn [role _ _ _]
                               (if (= role :plan) {:briefs []}
                                   (core/fail "Empty plan invoked a later role")))}))
        empty-id (:run-id empty-run)
        _ (core/guard (= :zero-initial-plan (get-in empty-run [:close :stop]))
                      "Empty initial plan did not close honestly")
        _ (core/guard (fails? #(bundle/export-run empty-id))
                      "Zero-work run was exportable")
        deadline-data
        (try
          (core/with-lock
            #(core/start-run
              "poincare-conjecture" goal
              {:fake (fn [& _] (core/fail "Synthetic plan interruption"))}))
          (core/fail "Deadline fixture did not interrupt")
          (catch Exception error (ex-data error)))
        deadline-id (:run-id deadline-data)
        deadline-run (core/find-run deadline-id)
        manifest (assoc (core/read-edn (core/child deadline-run "run.edn"))
                        :deadline 0)
        deadline-result
        (core/with-lock
          #(core/resume-run deadline-id
                            {:manifest manifest
                             :fake (fn [& _] (core/fail "Expired run invoked a model"))}))
        _ (core/guard (= :wall-time (get-in deadline-result [:close :stop]))
                      "Expired run did not close")
        too-wide {:briefs [(brief "A" [] "A") (brief "B" [] "B")]}
        _ (core/guard (fails? #(core/validate-plan too-wide 1 1 []))
                      "Affordable fan-out was not enforced")]
    {:empty empty-id :deadline deadline-id}))

(defn test-all []
  (let [validation (core/validate-repository)
        problem (core/find-problem "poincare-conjecture")
        inactive (core/child (:dir problem) "goals" "example-source-audit.edn")
        rejected? (try (core/validate-goal problem inactive) false
                       (catch Exception _ true))
        goal (core/child (:dir problem) "goals" "controller-fixture.edn")]
    (when-not rejected? (core/fail "Inactive goal passed preflight"))
    {:status "TEST_PASS" :validation validation :fixture (run-fixture)
     :edges (edge-fixtures goal)}))
