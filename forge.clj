#!/usr/bin/env bb

(require '[kernel.launcher :as launcher])

(try
  (launcher/main (vec *command-line-args*))
  (catch Exception error
    (binding [*out* *err*]
      (println "FORGE ERROR:" (.getMessage error))
      (when-let [data (ex-data error)] (prn data)))
    (System/exit 1)))
