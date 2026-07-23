#!/usr/bin/env bb

(require '[cheshire.core :as json]
         '[forge.fixture :as fixture])

(try
  (println (json/generate-string (fixture/test-all) {:pretty true}))
  (catch Exception error
    (binding [*out* *err*]
      (println (json/generate-string
                {:status "ERROR" :message (.getMessage error) :data (ex-data error)}
                {:pretty true})))
    (System/exit 1)))
