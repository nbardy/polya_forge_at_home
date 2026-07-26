(ns kernel.fixture
  (:require [babashka.fs :as fs] [cheshire.core :as json]
            [kernel.launcher :as launcher]))

(defn smoke-evolution []
  (let [root (fs/create-temp-dir {:prefix "polya-forge-launcher-"})
        versions (fs/file root "versions")
        current (fs/file root "CURRENT.edn")
        receipts (fs/file root "activations")
        runs (fs/file root "runs")
        campaigns (fs/file root "campaigns")]
    (try
      (with-redefs [launcher/forge-dir root
                    launcher/versions-dir versions
                    launcher/current-file current
                    launcher/receipts-dir receipts
                    launcher/runs-dir runs
                    launcher/campaigns-dir campaigns]
        (let [parent (launcher/install! (fs/file launcher/repo "engine"))
              _ (launcher/atomic! current parent)
              id "EVOLUTION"
              run (fs/file runs id)
              call (fs/file run "calls" "001-reflect")
              source (fs/file call "candidate" "engine")
              _ (launcher/copy-tree! (launcher/engine-dir parent) source)
              changed (fs/file source "prompts" "reflect.md")
              _ (spit changed (str (slurp changed) "\n"))
              mutation {:changed_file "prompts/reflect.md"
                        :hypothesis "A harmless newline exercises activation."
                        :evidence_refs ["fixture"]
                        :expected_benefit "Exercise the version boundary."
                        :regression_risk "None beyond fixture scope."
                        :benchmark_test "The fixed smoke suite still passes."}
              result {:assessment "Fixture mutation." :mutation mutation}]
          (launcher/atomic! (fs/file run "run.edn")
                            {:version (:version parent)
                             :version-sha256 (:sha256 parent)
                             :launcher-sha256 (launcher/launcher-sha)})
          (launcher/atomic! (fs/file call "request.edn")
                            {:call 1 :task "REFLECT" :role :reflect})
          (spit (fs/file call "result.json") (json/generate-string result))
          (launcher/atomic! (fs/file run "close.edn")
                            {:stop :fixture
                             :reflection {:call 1 :result result}})
          (with-redefs [launcher/engine-command! (fn [& _] 0)
                        launcher/process! (fn [& _] 0)]
            (launcher/candidate! id parent))
          (let [active (launcher/current!)]
            (launcher/guard! (= "v0002" (:version active))
                             "Candidate was not activated")
            (launcher/guard! (= [:activate] (mapv :event (launcher/receipts)))
                             "Activation receipt was unreadable")
            (try
              (with-redefs
               [launcher/engine-command!
                (fn [_ pin _ _ env]
                  (launcher/atomic!
                   (fs/file runs (get env "POLYA_FORGE_RUN_ID") "run.edn")
                   {:version (:version pin) :version-sha256 (:sha256 pin)
                    :launcher-sha256 (launcher/launcher-sha)})
                  0)]
                (launcher/execute! ["run" "fixture" "goal.edn"]))
              (launcher/fail! "Probationary failure was accepted")
              (catch Exception error
                (launcher/guard! (= "Engine exited without closing its run"
                                    (.getMessage error))
                                 "Unexpected rollback failure"
                                 {:message (.getMessage error)
                                  :data (ex-data error)})))
            (launcher/guard! (= parent (launcher/current!))
                             "Probation failure did not restore the parent")
            (let [reject-id "REJECT"
                  reject-run (fs/file runs reject-id)
                  reject-call (fs/file reject-run "calls" "001-reflect")
                  reject-source (fs/file reject-call "candidate" "engine")
                  reject-result
                  {:assessment "Fixture rejection."
                   :mutation (assoc mutation :changed_file "wrong.md")}]
              (launcher/copy-tree! (launcher/engine-dir parent) reject-source)
              (spit (fs/file reject-source "prompts" "reflect.md")
                    (str (slurp (fs/file reject-source "prompts" "reflect.md")) "\n"))
              (launcher/atomic! (fs/file reject-run "run.edn") {})
              (launcher/atomic! (fs/file reject-call "request.edn")
                                {:call 1 :task "REFLECT" :role :reflect})
              (spit (fs/file reject-call "result.json")
                    (json/generate-string reject-result))
              (launcher/atomic! (fs/file reject-run "close.edn")
                                {:stop :fixture
                                 :reflection {:call 1 :result reject-result}})
              (launcher/candidate! reject-id parent))
            (let [events (mapv :event (launcher/receipts))]
              (launcher/guard! (= [:activate :rollback :reject] events)
                               "Activation receipts changed" {:events events})
              (let [n (atom 0)
                    campaign
                    (with-redefs [launcher/execute!
                                  (fn
                                    ([_] (launcher/fail! "Unexpected resume"))
                                    ([_ assigned]
                                     (let [saved (launcher/read-edn
                                                  (first (fs/glob campaigns "*.edn")))
                                           attempt (swap! n inc)]
                                       (launcher/guard!
                                        (= assigned (:pending-run saved))
                                        "Campaign launched before persisting its run")
                                       (when (= 1 attempt)
                                         (throw
                                          (ex-info "Fixture probation rollback"
                                                   {:rolled-back-to parent})))
                                       assigned)))
                                  launcher/run-pin
                                  (fn [id] {:version id :sha256 (apply str (repeat 64 "0"))})
                                  launcher/current!
                                  (fn [] {:version "next"
                                          :sha256 (apply str (repeat 64 "1"))})]
                      (launcher/start-campaign! 2 ["problem" "goal.edn"]))]
                (launcher/guard! (and (= :complete (:status campaign))
                                      (= 2 (:completed campaign))
                                      (= 1 (count (:failed-runs campaign)))
                                      (= 3 @n))
                                 "Campaign record did not close")
                {:status "PASS" :events events :campaign-rounds 2
                 :recovered-rollbacks 1})))))
      (finally (fs/delete-tree root)))))
