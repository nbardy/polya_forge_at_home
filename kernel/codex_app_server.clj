(ns kernel.codex-app-server
  "Small JSONL client for one Codex app-server turn.

   Builder threads are durable so a rejected descendant can resume the exact
   conversation. Every other role is forced onto a fresh ephemeral thread.
   The caller supplies one canonical attempt directory; both cwd and the only
   writable root are pinned to that directory for the turn."
  (:require [cheshire.core :as json] [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io BufferedReader BufferedWriter InputStreamReader OutputStreamWriter]
           [java.nio.charset StandardCharsets]
           [java.nio.file Files LinkOption]
           [java.util.concurrent ExecutionException TimeUnit]))

(def client-info
  {:name "polya_forge" :title "Pólya Forge" :version "0.1.0"})

(defn fail! [message data]
  (throw (ex-info message data)))

(defn guard! [ok message data]
  (when-not ok (fail! message data)))

(defn canonical-directory [value]
  (let [file (.getCanonicalFile (io/file (str value)))
        path (.toPath file)]
    (guard! (and (.isAbsolute path)
                 (Files/isDirectory path (make-array LinkOption 0))
                 (not (Files/isSymbolicLink path)))
            "App-server attempt directory is missing or unsafe"
            {:path (str file)})
    (str file)))

(defn request! [send! id method params]
  (send! {:id id :method method :params params}))

(defn notification! [send! method params]
  (send! {:method method :params params}))

(defn receive! [transport event!]
  (let [message ((:receive! transport))]
    (guard! (map? message) "Codex app-server closed without a response" {})
    (event! message)
    (when (and (:method message) (contains? message :id))
      (fail! "Unexpected Codex app-server request"
             {:method (:method message) :id (:id message)}))
    (when (= "error" (:method message))
      (fail! "Codex app-server reported an error" {:error (:params message)}))
    message))

(defn await-response! [transport event! id]
  (loop []
    (let [message (receive! transport event!)]
      (if (= id (:id message))
        (if-let [error (:error message)]
          (fail! "Codex app-server request failed" {:id id :error error})
          (do
            (guard! (contains? message :result)
                    "Codex app-server response has no result"
                    {:id id :response message})
            (:result message)))
        (recur)))))

(defn start-thread-params
  [{:keys [role cwd model developer-instructions]}]
  (cond-> {:model model
           :cwd cwd
           :approvalPolicy "never"
           :sandbox "workspace-write"
           :runtimeWorkspaceRoots [cwd]
           :ephemeral (not= :build role)
           :serviceName "polya_forge"}
    (not (str/blank? developer-instructions))
    (assoc :developerInstructions developer-instructions)))

(defn resume-thread-params
  [{:keys [cwd model conversation-id developer-instructions]}]
  (cond-> {:threadId conversation-id
           :model model
           :cwd cwd
           :approvalPolicy "never"
           :sandbox "workspace-write"
           :runtimeWorkspaceRoots [cwd]}
    (not (str/blank? developer-instructions))
    (assoc :developerInstructions developer-instructions)))

(defn turn-params [{:keys [cwd model effort prompt schema]} thread-id]
  {:threadId thread-id
   :input [{:type "text" :text prompt}]
   :cwd cwd
   :approvalPolicy "never"
   :sandboxPolicy {:type "workspaceWrite"
                   :writableRoots [cwd]
                   :networkAccess false}
   :runtimeWorkspaceRoots [cwd]
   :model model
   :effort effort
   :outputSchema schema})

(defn agent-message [message]
  (let [item (get-in message [:params :item])]
    (when (and (= "item/completed" (:method message))
               (= "agentMessage" (:type item)))
      item)))

(defn completed-agent-messages [turn]
  (filter #(= "agentMessage" (:type %)) (:items turn)))

(defn final-message [messages]
  (or (last (filter #(= "final_answer" (:phase %)) messages))
      (last messages)))

(defn run-turn!
  "Run one turn over a transport with :send! and blocking :receive! functions.

   A nonblank :conversation-id is accepted only for :build, which resumes that
   exact stored thread. Fresh builders start durable threads; all other roles
   start ephemeral threads. Returns the thread/turn identifiers, parsed result,
   and exact final JSON text."
  [{:keys [transport role cwd model effort prompt schema conversation-id event!]
    :as options}]
  (let [event! (or event! (constantly nil))
        send! (:send! transport)
        cwd (canonical-directory cwd)
        options (assoc options :cwd cwd)]
    (guard! (#{:plan :build :verify :remember :reflect} role)
            "Unknown app-server role" {:role role})
    (guard! (and (string? model) (not (str/blank? model))
                 (string? effort) (not (str/blank? effort))
                 (string? prompt) (not (str/blank? prompt))
                 (map? schema))
            "Incomplete app-server turn configuration" {:role role})
    (guard! (or (str/blank? conversation-id) (= :build role))
            "Only a builder may continue a prior conversation"
            {:role role :conversation-id conversation-id})
    (request! send! 0 "initialize"
              {:clientInfo client-info
               :capabilities
               {:experimentalApi true
                :optOutNotificationMethods
                ["item/agentMessage/delta"
                 "item/reasoning/summaryTextDelta"
                 "item/reasoning/summaryPartAdded"
                 "item/reasoning/textDelta"
                 "item/commandExecution/outputDelta"]}})
    (await-response! transport event! 0)
    (notification! send! "initialized" {})
    (let [continuing? (not (str/blank? conversation-id))
          thread-result
          (do
            (request! send! 1
                      (if continuing? "thread/resume" "thread/start")
                      ((if continuing? resume-thread-params start-thread-params)
                       options))
            (await-response! transport event! 1))
          thread-id (get-in thread-result [:thread :id])]
      (guard! (and (string? thread-id) (not (str/blank? thread-id)))
              "Codex app-server returned no thread id" {:result thread-result})
      ;; forge.core already recognizes this normalized exec-compatible record.
      (event! {:type "thread.started" :thread_id thread-id
               :continued continuing?})
      (request! send! 2 "turn/start" (turn-params options thread-id))
      (let [turn-result (await-response! transport event! 2)
            turn-id (get-in turn-result [:turn :id])]
        (guard! (and (string? turn-id) (not (str/blank? turn-id)))
                "Codex app-server returned no turn id" {:result turn-result})
        (loop [messages []]
          (let [message (receive! transport event!)
                messages (cond-> messages
                           (agent-message message) (conj (agent-message message)))]
            (if (and (= "turn/completed" (:method message))
                     (= thread-id (get-in message [:params :threadId]))
                     (= turn-id (get-in message [:params :turn :id])))
              (let [turn (get-in message [:params :turn])
                    messages (into messages (completed-agent-messages turn))
                    final (final-message messages)
                    status (:status turn)
                    text (:text final)]
                (guard! (= "completed" status)
                        "Codex app-server turn did not complete"
                        {:thread-id thread-id :turn-id turn-id
                         :status status :error (:error turn)})
                (guard! (and (string? text) (not (str/blank? text)))
                        "Codex app-server turn has no final agent message"
                        {:thread-id thread-id :turn-id turn-id})
                (let [value (try (json/parse-string text true)
                                 (catch Exception error
                                   (fail! "Codex final message is not JSON"
                                          {:thread-id thread-id :turn-id turn-id
                                           :cause (.getMessage error)})))]
                  {:conversation-id thread-id :turn-id turn-id
                   :continued continuing? :text text :value value}))
              (recur messages))))))))

(defn default-terminate! [^Process child]
  (when (.isAlive child)
    (.destroyForcibly child)
    (.waitFor child 5 TimeUnit/SECONDS))
  true)

(defn open-stdio!
  "Open a JSONL stdio transport. Lifecycle callbacks let the launcher retain
   authority over process-tree registration and termination."
  [{:keys [command cwd stderr env register! unregister! stopping? terminate!]}]
  (let [builder (doto (ProcessBuilder. ^java.util.List (mapv str command))
                  (.directory (io/file cwd)))
        _ (when stderr (.redirectError builder (io/file stderr)))
        process-env (.environment builder)
        _ (.clear process-env)
        _ (doseq [[key value] env] (.put process-env (str key) (str value)))
        child (.start builder)
        _ ((or register! (constantly nil)) child)
        terminate! (or terminate! default-terminate!)
        reader (BufferedReader.
                (InputStreamReader. (.getInputStream child) StandardCharsets/UTF_8))
        writer (BufferedWriter.
                (OutputStreamWriter. (.getOutputStream child) StandardCharsets/UTF_8))
        closed? (atom false)]
    (when (and stopping? @stopping?) (terminate! child))
    {:process child
     :send! (fn [message]
              (locking writer
                (.write writer (json/generate-string message))
                (.newLine writer)
                (.flush writer)))
     :receive! (fn []
                 (when-let [line (.readLine reader)]
                   (json/parse-string line true)))
     :close! (fn []
               (when (compare-and-set! closed? false true)
                 (try (.close writer) (catch Exception _ nil))
                 (try (.close reader) (catch Exception _ nil))
                 (when-not (.waitFor child 1 TimeUnit/SECONDS)
                   (terminate! child))
                 ((or unregister! (constantly nil)) child)))}))

(def timeout-marker (Object.))

(defn invoke!
  "Run one bounded app-server call. Tests may inject :open-transport!; normal
   callers use a real stdio subprocess. The launcher should provide its
   process-tree terminate callback so timeout and shutdown remain quiescent."
  [{:keys [timeout-ms open-transport!] :as options}]
  (guard! (and (int? timeout-ms) (pos? timeout-ms))
          "Invalid app-server timeout" {:timeout-ms timeout-ms})
  (let [open! (or open-transport! open-stdio!)
        transport (open! options)
        task (future (run-turn! (assoc options :transport transport)))]
    (try
      (try
        (let [result (deref task timeout-ms timeout-marker)]
          (when (identical? timeout-marker result)
            (when-let [^Process child (:process transport)]
              ((or (:terminate! options) default-terminate!) child))
            (future-cancel task)
            (fail! "Codex app-server turn timed out" {:timeout-ms timeout-ms}))
          result)
        (catch ExecutionException error
          (throw (or (.getCause error) error))))
      (finally
        (when-let [close! (:close! transport)] (close!))))))

(defn command [codex]
  ;; The launcher injects the frozen call-local AGENTS.md verbatim as
  ;; developerInstructions. Disable ambient AGENTS.md discovery so unrelated
  ;; user/project includes cannot enter a mathematical attempt.
  [(or codex "codex") "app-server" "-c" "project_doc_max_bytes=0" "--stdio"])
