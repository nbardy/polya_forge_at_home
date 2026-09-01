(ns kernel.codex-app-server-fixture
  (:require [babashka.fs :as fs]
            [kernel.codex-app-server :as app]))

(def schema
  {:type "object"
   :additionalProperties false
   :required ["answer"]
   :properties {:answer {:type "string"}}})

(defn response-script [thread-id turn-id answer]
  [{:id 0 :result {:userAgent "fixture"}}
   {:method "thread/started" :params {:thread {:id thread-id}}}
   {:id 1 :result {:thread {:id thread-id}}}
   {:id 2 :result {:turn {:id turn-id :status "inProgress" :items []}}}
   {:method "item/completed"
    :params {:threadId thread-id :turnId turn-id :completedAtMs 1
             :item {:id "message-1" :type "agentMessage"
                    :phase "final_answer" :text answer}}}
   {:method "turn/completed"
    :params {:threadId thread-id
             :turn {:id turn-id :status "completed" :items []}}}])

(defn fake-opener [script sent closed?]
  (fn [_]
    (let [remaining (atom (vec script))]
      {:send! #(swap! sent conj %)
       :receive! (fn []
                   (let [message (first @remaining)]
                     (swap! remaining #(vec (rest %)))
                     message))
       :close! #(reset! closed? true)})))

(defn invoke-case [dir role thread-id turn-id conversation-id]
  (let [sent (atom []) events (atom []) closed? (atom false)
        answer (str "{\"answer\":\"" (name role) "\"}")
        result
        (app/invoke!
         {:role role :cwd dir :model "fixture-model" :effort "high"
          :prompt "Attack the exact endpoint." :schema schema
          :conversation-id conversation-id :timeout-ms 1000
          :developer-instructions "Write only in this attempt directory."
          :event! #(swap! events conj %)
          :open-transport!
          (fake-opener (response-script thread-id turn-id answer) sent closed?)})]
    {:sent @sent :events @events :closed? @closed? :result result}))

(defn check! [ok message data]
  (when-not ok (throw (ex-info message data))))

(defn smoke-persistent-lead []
  (let [root (fs/create-temp-dir {:prefix "polya-forge-app-server-"})]
    (try
      (check! (= ["fixture-codex" "app-server" "-c"
                  "project_doc_max_bytes=0" "--stdio"]
                 (app/command "fixture-codex"))
              "App-server command permits ambient project instructions" {})
      (let [dir-a (str (fs/create-dirs (fs/file root "attempt-a")))
            dir-b (str (fs/create-dirs (fs/file root "attempt-b")))
            fresh-builder (invoke-case dir-a :build "builder-1" "turn-1" nil)
            repair-builder (invoke-case dir-b :build "builder-1" "turn-2" "builder-1")
            verifier-a (invoke-case dir-a :verify "verifier-1" "turn-3" nil)
            verifier-b (invoke-case dir-b :verify "verifier-2" "turn-4" nil)
            builder-start (nth (:sent fresh-builder) 2)
            builder-init (first (:sent fresh-builder))
            repair-start (nth (:sent repair-builder) 2)
            verify-start-a (nth (:sent verifier-a) 2)
            verify-start-b (nth (:sent verifier-b) 2)
            repair-turn (nth (:sent repair-builder) 3)
            canonical-b (str (.getCanonicalFile (java.io.File. dir-b)))]
        (check! (= ["initialize" "initialized" "thread/start" "turn/start"]
                   (mapv :method (:sent fresh-builder)))
                "Fresh builder RPC order changed" {:sent (:sent fresh-builder)})
        (check! (true? (get-in builder-init
                               [:params :capabilities :experimentalApi]))
                "Runtime workspace roots were used without protocol opt-in"
                {:request builder-init})
        (check! (false? (get-in builder-start [:params :ephemeral]))
                "Fresh builder conversation was not durable" {:request builder-start})
        (check! (= "workspace-write" (get-in builder-start [:params :sandbox]))
                "Thread start used an invalid legacy sandbox enum"
                {:request builder-start})
        (check! (and (= "thread/resume" (:method repair-start))
                     (= "builder-1" (get-in repair-start [:params :threadId]))
                     (= "builder-1" (get-in repair-builder [:result :conversation-id]))
                     (true? (get-in repair-builder [:result :continued])))
                "Repair did not resume the exact builder conversation"
                {:request repair-start :result (:result repair-builder)})
        (check! (and (= canonical-b (get-in repair-turn [:params :cwd]))
                     (= [canonical-b]
                        (get-in repair-turn [:params :sandboxPolicy :writableRoots]))
                     (= [canonical-b]
                        (get-in repair-turn [:params :runtimeWorkspaceRoots]))
                     (= schema (get-in repair-turn [:params :outputSchema])))
                "Repair turn escaped its immutable attempt directory"
                {:request repair-turn})
        (check! (and (= "thread/start" (:method verify-start-a))
                     (= "thread/start" (:method verify-start-b))
                     (true? (get-in verify-start-a [:params :ephemeral]))
                     (true? (get-in verify-start-b [:params :ephemeral]))
                     (not= (get-in verifier-a [:result :conversation-id])
                           (get-in verifier-b [:result :conversation-id])))
                "Independent verifiers shared a conversation"
                {:first verify-start-a :second verify-start-b})
        (check! (every? :closed? [fresh-builder repair-builder verifier-a verifier-b])
                "Fake app-server transport was not closed" {})
        (check! (some #(and (= "thread.started" (:type %))
                            (= "builder-1" (:thread_id %))
                            (true? (:continued %)))
                      (:events repair-builder))
                "Continuation marker was not preserved in the event log"
                {:events (:events repair-builder)})
        (let [sent (atom []) closed? (atom false)]
          (try
            (app/invoke!
             {:role :verify :cwd dir-a :model "fixture-model" :effort "high"
              :prompt "Verify." :schema schema :conversation-id "builder-1"
              :timeout-ms 1000
              :open-transport! (fake-opener [] sent closed?)})
            (check! false "Verifier continuation was accepted" {})
            (catch Exception error
              (check! (= "Only a builder may continue a prior conversation"
                         (.getMessage error))
                      "Verifier continuation failed for the wrong reason"
                      {:message (.getMessage error)})))
          (check! @closed? "Rejected verifier transport was not closed" {}))
        {:status "PASS"
         :builder-thread "builder-1"
         :repair-resumed true
         :fresh-verifiers ["verifier-1" "verifier-2"]
         :attempt-write-root canonical-b})
      (finally (fs/delete-tree root)))))
