#!/usr/bin/env bb

(require '[cheshire.core :as json])

(try
  (let [smoke #(deref (requiring-resolve %))
        engine {:round ((smoke 'forge.fixture/smoke-round))
                :resume ((smoke 'forge.fixture/smoke-resume))}
        result (cond-> engine
                 (not= ["--engine-gate"] (vec *command-line-args*))
                 (assoc :evolution ((smoke 'kernel.fixture/smoke-evolution))))]
    (println (json/generate-string (assoc result :status "TEST_PASS")
                                   {:pretty true})))
  (catch Exception error
    (binding [*out* *err*]
      (println (json/generate-string
                {:status "ERROR" :message (.getMessage error) :data (ex-data error)}
                {:pretty true})))
    (System/exit 1)))
