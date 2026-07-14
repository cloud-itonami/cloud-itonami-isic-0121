(ns vineyardops.sim
  "Simple simulation/demo runner for the Vineyard Operations Coordinator
  actor. Used to validate that the actor flow compiles and basic proposal
  flow works. Mirrors `cattleops.sim` (cloud-itonami-isic-0141)."
  (:require [vineyardops.operation :as operation]
            [vineyardops.store :as store]))

(defn demo
  "Run a simple demo scenario: register a vineyard block, propose a
  vineyard-record log, and check the disposition flow."
  []
  (let [;; Create store with a registered vineyard block
        st (store/mem-store
            {:initial-vineyards
             {"vineyard-001"
              {:id "vineyard-001"
               :name "Test Vineyard Block"
               :grape-class "wine-grape"}}})

        ;; Build actor
        actor (operation/build st)

        ;; Create a request to log a vineyard record
        request {:op :log-vineyard-record
                 :vineyard-id "vineyard-001"
                 :record-type "harvest"
                 :count 500
                 :notes "healthy yield"}

        ;; Context with phase 0 (simulation)
        context {:actor-id "vineyard-ops-01"
                 :role :vineyard-operator
                 :phase :phase-0}]

    (println "=== Vineyard Operations Coordinator Demo ===")
    (println "Demo vineyard block: vineyard-001")
    (println "Request: log-vineyard-record")
    (println "Phase: phase-0 (simulation)")
    (println "Expected: escalate (phase-0 forces human review of all commits)")
    (println)
    (let [result (actor request context)]
      (println "Result disposition:" (:disposition result))
      result)))

(defn -main
  "clojure -M:run entrypoint."
  [& _args]
  (demo))

(comment
  ;; In a real REPL:
  (demo)
)
