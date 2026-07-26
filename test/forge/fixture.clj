(ns forge.fixture
  (:require [babashka.fs :as fs] [clojure.string :as str]
            [forge.core :as core]))

(def goal-file
  (core/child core/problems-root "poincare-conjecture" "goals"
              "controller-fixture.edn"))
(def objective "Exercise one deterministic controller lifecycle and durable resume.")
(def endpoint "Controller regression only; no mathematical endpoint.")
(def first-line "Build and independently audit one synthetic packet.")

(defn brief []
  {:id "DIRECT" :parent_packet_ids [] :objective objective
   :endpoint_edge endpoint :first_open_line first-line
   :inputs ["Deterministic fixture"] :exclusions ["Mathematical claims"]
   :cheapest_falsifier "Reject missing evidence."
   :completion_criteria "One independently audited packet."
   :kill_criteria "Any controller invariant fails."})

(def build
  {:claim "The fixture produced one artifact." :claim_status "computational-evidence"
   :work "Ran the deterministic branch." :first_open_line first-line
   :evidence ["A text artifact exists."] :failed_steps [] :assumption_checks []
   :verification_remaining ["Independent fixture audit"] :next_actions ["Verify."]})
(def verify
  {:verdict "PASS" :claim_status "computational-evidence"
   :audit "A separate verifier observed the artifact."
   :evidence ["Builder and verifier are separate calls."]
   :first_open_or_invalid_line first-line :failure nil})

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
                    core/runs-root (core/child root "runs")]
        (f root))
      (finally (fs/delete-tree root)))))

(defn roles [ctx]
  (mapv :role (map core/call-record (core/call-dirs ctx))))

(defn smoke-round []
  (isolated
   (fn [root]
     (let [result (core/start-run "poincare-conjecture" goal-file
                                  {:fake (fake-model false)})
           run (core/find-run (:run-id result))
           ctx (core/context run {})
           packets (core/completed-packets ctx)
           memory (core/read-edn (core/child root "memory" "poincare-conjecture"
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
       (let [empty (core/start-run "poincare-conjecture" goal-file
                                   {:fake (fake-model false true)})
             empty-ctx (core/context (core/find-run (:run-id empty)) {})]
         (core/guard (= [:plan :remember :reflect] (roles empty-ctx))
                     "Zero-plan round skipped terminal review"))
       {:status "PASS" :packets 1 :calls 6 :zero-plan-review true}))))

(defn smoke-resume []
  (isolated
   (fn [_]
     (let [fake (fake-model true)
           interrupted
           (try
             (core/start-run "poincare-conjecture" goal-file {:fake fake})
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
