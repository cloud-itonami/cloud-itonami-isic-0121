(ns vineyardops.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [vineyardops.governor :as gov]
            [vineyardops.store :as store]))

(deftest hard-violations-no-vineyard-id
  (testing "Hard violation: missing vineyard-id"
    (let [req {}
          prop {:op :log-vineyard-record :effect :propose}
          s (store/mem-store)
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (seq (:violations verdict)))
      (is (some #(= :vineyard-not-registered (:rule %)) (:violations verdict))))))

(deftest hard-violations-unregistered-vineyard
  (testing "Hard violation: vineyard-id present but not registered"
    (let [req {:vineyard-id "vineyard-001"}
          prop {:op :log-vineyard-record :effect :propose}
          s (store/mem-store)
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :vineyard-not-registered (:rule %)) (:violations verdict))))))

(deftest hard-violations-effect-not-propose
  (testing "Hard violation: effect is not :propose"
    (let [vineyard {:id "vineyard-001" :name "Test Vineyard Block"}
          s (store/mem-store {:initial-vineyards {"vineyard-001" vineyard}})
          req {:vineyard-id "vineyard-001"}
          prop {:op :log-vineyard-record :effect :execute}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :no-execution (:rule %)) (:violations verdict))))))

(deftest hard-violations-field-equipment-blocked
  (testing "Hard violation: direct field-equipment operation is permanently blocked"
    (let [vineyard {:id "vineyard-001" :name "Test Vineyard Block"}
          s (store/mem-store {:initial-vineyards {"vineyard-001" vineyard}})
          req {:vineyard-id "vineyard-001"}
          prop {:op :operate-field-equipment :effect :propose}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :field-equipment-or-spray-blocked (:rule %)) (:violations verdict))))))

(deftest hard-violations-spray-application-blocked
  (testing "Hard violation: finalizing a spray-application decision is permanently blocked"
    (let [vineyard {:id "vineyard-001" :name "Test Vineyard Block"}
          s (store/mem-store {:initial-vineyards {"vineyard-001" vineyard}})
          req {:vineyard-id "vineyard-001"}
          prop {:op :finalize-spray-application :effect :propose}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :field-equipment-or-spray-blocked (:rule %)) (:violations verdict))))))

(deftest hard-violations-op-not-allowed
  (testing "Hard violation: op outside the closed allowlist"
    (let [vineyard {:id "vineyard-001" :name "Test Vineyard Block"}
          s (store/mem-store {:initial-vineyards {"vineyard-001" vineyard}})
          req {:vineyard-id "vineyard-001"}
          prop {:op :dispatch-robot-arm :effect :propose}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :op-not-allowed (:rule %)) (:violations verdict))))))

(deftest hard-violations-vineyard-count-invalid
  (testing "Hard violation: non-positive logged vineyard-record quantity"
    (let [vineyard {:id "vineyard-001" :name "Test Vineyard Block"}
          s (store/mem-store {:initial-vineyards {"vineyard-001" vineyard}})
          req {:vineyard-id "vineyard-001"}
          prop {:op :log-vineyard-record :effect :propose :count 0 :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :vineyard-count-invalid (:rule %)) (:violations verdict))))))

(deftest ok-vineyard-record-logging
  (testing "OK: valid vineyard-record logging with a registered vineyard block"
    (let [vineyard {:id "vineyard-001" :name "Test Vineyard Block"}
          s (store/mem-store {:initial-vineyards {"vineyard-001" vineyard}})
          req {:vineyard-id "vineyard-001"}
          prop {:op :log-vineyard-record :effect :propose :count 500 :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:hard? verdict)))
      (is (not (:escalate? verdict))))))

(deftest escalation-crop-health-concern
  (testing "Escalation: crop health concern ALWAYS escalates, even at high confidence"
    (let [vineyard {:id "vineyard-001" :name "Test Vineyard Block"}
          s (store/mem-store {:initial-vineyards {"vineyard-001" vineyard}})
          req {:vineyard-id "vineyard-001"}
          prop {:op :flag-crop-health-concern :effect :propose
                :concern "フィロキセラの可能性" :confidence 0.95}
          verdict (gov/check req nil prop s)]
      (is (not (:hard? verdict)))
      (is (:escalate? verdict))
      (is (:high-stakes? verdict)))))

(deftest escalation-low-confidence
  (testing "Escalation: confidence below the floor"
    (let [vineyard {:id "vineyard-001" :name "Test Vineyard Block"}
          s (store/mem-store {:initial-vineyards {"vineyard-001" vineyard}})
          req {:vineyard-id "vineyard-001"}
          prop {:op :log-vineyard-record :effect :propose :count 500 :confidence 0.5}
          verdict (gov/check req nil prop s)]
      (is (not (:hard? verdict)))
      (is (:escalate? verdict)))))

(deftest escalation-supply-order-high-cost
  (testing "Escalation: supply order over the (default) cost threshold"
    (let [vineyard {:id "vineyard-001" :name "Test Vineyard Block"}
          s (store/mem-store {:initial-vineyards {"vineyard-001" vineyard}})
          req {:vineyard-id "vineyard-001"}
          prop {:op :order-supplies :effect :propose :cost 1000 :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (not (:hard? verdict)))
      (is (:escalate? verdict)))))

(deftest escalation-supply-order-category-specific-threshold
  (testing "Escalation: supply order over its category-specific threshold (equipment: 1000)"
    (let [vineyard {:id "vineyard-001" :name "Test Vineyard Block"}
          s (store/mem-store {:initial-vineyards {"vineyard-001" vineyard}})
          req {:vineyard-id "vineyard-001"}
          prop {:op :order-supplies :effect :propose :cost 1200 :confidence 0.9
                :value {:category "equipment"}}
          verdict (gov/check req nil prop s)]
      (is (:escalate? verdict))))

  (testing "OK: equipment order under its higher category threshold"
    (let [vineyard {:id "vineyard-001" :name "Test Vineyard Block"}
          s (store/mem-store {:initial-vineyards {"vineyard-001" vineyard}})
          req {:vineyard-id "vineyard-001"}
          prop {:op :order-supplies :effect :propose :cost 800 :confidence 0.9
                :value {:category "equipment"}}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:escalate? verdict))))))

(deftest ok-supply-order-low-cost
  (testing "OK: supply order under the cost threshold"
    (let [vineyard {:id "vineyard-001" :name "Test Vineyard Block"}
          s (store/mem-store {:initial-vineyards {"vineyard-001" vineyard}})
          req {:vineyard-id "vineyard-001"}
          prop {:op :order-supplies :effect :propose :cost 100 :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:escalate? verdict))))))

(deftest ok-schedule-field-operation
  (testing "OK: scheduling a field operation is a routine coordination op"
    (let [vineyard {:id "vineyard-001" :name "Test Vineyard Block"}
          s (store/mem-store {:initial-vineyards {"vineyard-001" vineyard}})
          req {:vineyard-id "vineyard-001"}
          prop {:op :schedule-field-operation :effect :propose :confidence 0.85}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:escalate? verdict))))))
