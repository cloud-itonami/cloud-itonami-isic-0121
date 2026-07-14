(ns vineyardops.store-test
  (:require [clojure.test :refer [deftest is testing]]
            [vineyardops.store :as store]))

(deftest mem-store-creation
  (testing "Create empty store"
    (let [st (store/mem-store)]
      (is (some? st))
      (is (satisfies? store/Store st))))

  (testing "Create store with initial vineyards"
    (let [vineyards {"vineyard-001" {:id "vineyard-001" :name "Test Vineyard Block"}}
          st (store/mem-store {:initial-vineyards vineyards})]
      (is (some? st))
      (is (satisfies? store/Store st)))))

(deftest registered-vineyard-retrieval
  (testing "Retrieve existing vineyard"
    (let [vineyard {:id "vineyard-001" :name "Test Vineyard Block"}
          st (store/mem-store {:initial-vineyards {"vineyard-001" vineyard}})]
      (is (= vineyard (store/registered-vineyard st "vineyard-001")))))

  (testing "Retrieve non-existent vineyard"
    (let [st (store/mem-store)]
      (is (nil? (store/registered-vineyard st "no-such-vineyard")))))

  (testing "nil vineyard-id returns nil (never falls through to a default)"
    (let [st (store/mem-store {:initial-vineyards {"vineyard-001" {:id "vineyard-001"}}})]
      (is (nil? (store/registered-vineyard st nil))))))

(deftest add-vineyard-test
  (testing "Register a new vineyard"
    (let [st (store/mem-store)
          vineyard-data {:id "vineyard-002" :name "New Vineyard Block"}
          result (store/add-vineyard st "vineyard-002" vineyard-data)]
      (is (= vineyard-data result))
      (is (= vineyard-data (store/registered-vineyard st "vineyard-002")))))

  (testing "Update an existing vineyard"
    (let [st (store/mem-store {:initial-vineyards {"vineyard-001" {:id "vineyard-001"}}})
          updated {:id "vineyard-001" :name "Renamed Vineyard Block"}
          result (store/add-vineyard st "vineyard-001" updated)]
      (is (= updated result))
      (is (= updated (store/registered-vineyard st "vineyard-001"))))))
