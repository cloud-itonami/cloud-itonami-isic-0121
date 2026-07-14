(ns vineyardops.facts-test
  (:require [clojure.test :refer [deftest is are testing]]
            [vineyardops.facts :as facts]))

(deftest supply-category-lookup
  (testing "Lookup valid supply category"
    (let [c (facts/supply-category-by-id "rootstock")]
      (is (= "rootstock" (:id c)))
      (is (= "台木" (:name c)))))

  (testing "Lookup invalid supply category"
    (is (nil? (facts/supply-category-by-id "unknown")))))

(deftest supply-category-cost-thresholds
  (testing "Category-specific cost thresholds"
    (are [id expected] (= expected (:cost-threshold (facts/supply-category-by-id id)))
      "rootstock"   500
      "fertilizer"  500
      "equipment"   1000)))

(deftest default-cost-threshold-value
  (testing "Default fallback threshold matches the conservative baseline"
    (is (= 500 facts/default-cost-threshold))))

(deftest grape-class-lookup
  (testing "Lookup valid grape class"
    (are [id expected-name] (= expected-name (:name (facts/grape-class-by-id id)))
      "wine-grape"  "ワイン用ぶどう"
      "table-grape" "生食用ぶどう"))

  (testing "Lookup invalid grape class"
    (is (nil? (facts/grape-class-by-id "unknown")))))
