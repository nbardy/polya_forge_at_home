#!/usr/bin/env bb

(require '[cheshire.core :as json])

(try
  (let [smoke #(deref (requiring-resolve %))
        engine {:schema ((smoke 'forge.fixture/smoke-schema-compatibility))
                :artifact-text ((smoke 'forge.fixture/smoke-artifact-text))
                :blinded-benchmark ((smoke 'forge.fixture/smoke-blinded-endpoint-benchmark))
                :parent-scope ((smoke 'forge.fixture/smoke-parent-scope))
                :round ((smoke 'forge.fixture/smoke-round))
                :endpoint-candidate ((smoke 'forge.fixture/smoke-endpoint-candidate-pause))
                :repair-loop ((smoke 'forge.fixture/smoke-repair-loop))
                :cross-run-repair ((smoke 'forge.fixture/smoke-cross-run-terminal-repair))
                :branch-failure ((smoke 'forge.fixture/smoke-branch-failure-closes))
                :resume ((smoke 'forge.fixture/smoke-resume))}
        result (cond-> engine
                 (not= ["--compatibility-gate"] (vec *command-line-args*))
                 (assoc :process-lifecycle
                        ((smoke 'kernel.fixture/smoke-process-lifecycle))
                        :model-broker-routing
                        ((smoke 'kernel.fixture/smoke-model-broker-routing))
                        :persistent-lead
                        ((smoke 'kernel.codex-app-server-fixture/smoke-persistent-lead))
                        :evolution ((smoke 'kernel.fixture/smoke-evolution))
                        :publication ((smoke 'kernel.fixture/smoke-publication))))]
    (println (json/generate-string (assoc result :status "TEST_PASS")
                                   {:pretty true})))
  (catch Exception error
    (binding [*out* *err*]
      (println (json/generate-string
                {:status "ERROR" :message (.getMessage error) :data (ex-data error)}
                {:pretty true})))
    (System/exit 1)))
