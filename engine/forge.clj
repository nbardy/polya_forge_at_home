#!/usr/bin/env bb
(require '[cheshire.core :as json]
         '[forge.bundle :as bundle]
         '[forge.core :as core])
(defn output [value] (println (json/generate-string value {:pretty true})))
(def usage
  "Pólya Forge at Home\n\nbb check\nbb run <problem-id> <goal.edn>\nbb resume <run-id>\nbb export <run-id>\nbb inspect <bundle-dir>\n")
(defn need [value message] (or value (core/fail message)))
(defn main [args]
  (let [[command a b] args]
    (case (or command "help")
      "check" (output (core/validate-repository))
      "run" (output (core/with-lock #(core/start-run (need a "run requires a problem ID")
                                                    (need b "run requires a goal path") {})))
      "resume" (output (core/with-lock #(core/resume-run (need a "resume requires a run ID") {})))
      "export" (output (core/with-lock #(bundle/export-run (need a "export requires a run ID"))))
      "inspect" (output (bundle/inspect-bundle (need a "inspect requires a path")))
      "help" (println usage)
      (core/fail "Unknown command" {:command command}))))
(try
  (main (vec *command-line-args*))
  (catch Exception error
    (binding [*out* *err*]
      (output {:status "ERROR" :message (.getMessage error) :data (ex-data error)}))
    (System/exit 1)))
