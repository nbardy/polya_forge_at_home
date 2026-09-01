#!/usr/bin/env bb
(require '[cheshire.core :as json]
         '[forge.core :as core])
(defn output [value] (println (json/generate-string value {:pretty true})))
(def usage
  "Pólya Forge at Home\n\nbb check\nbb run <goal.edn>\nbb resume <run-id>\nbb export <run-id>\nbb inspect <bundle-dir>\n")
(defn need [value message] (or value (core/fail message)))
(def arities {"check" 1 "run" 2 "resume" 2 "export" 2 "inspect" 2 "help" 1})
(defn main [args]
  (let [[raw a] args command (or raw "help")
        expected (if raw (get arities command) 0)]
    (when expected
      (core/guard (= expected (count args)) "Wrong command arity"
                  {:command command :expected (dec expected)}))
    (case command
      "check" (output (core/validate-repository))
      "run" (output (core/start-run (need a "run requires a goal path") {}))
      "resume" (output (core/resume-run (need a "resume requires a run ID") {}))
      "export" (output ((requiring-resolve 'forge.bundle/export-run)
                        (need a "export requires a run ID")))
      "inspect" (output ((requiring-resolve 'forge.bundle/inspect-bundle)
                         (need a "inspect requires a path")))
      "help" (println usage)
      (core/fail "Unknown command" {:command command}))))
(try
  (main (vec *command-line-args*))
  (catch Exception error
    (binding [*out* *err*]
      (output {:status "ERROR" :message (.getMessage error) :data (ex-data error)}))
    (System/exit 1)))
