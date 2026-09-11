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

;; ----------------------------- audit ledger (append-only) -----------------------------
;; FIX: `ledger`/`append-ledger!` did not exist anywhere in this codebase
;; before this fix -- not dead code, the concept was entirely absent.
;; `vineyardops.operation`'s real compiled StateGraph now wires these
;; genuinely into its `:commit`/`:hold` terminal nodes (see
;; `vineyardops.operation-test`); these are unit-level tests of the Store
;; accessor itself.

(deftest ledger-starts-empty-test
  (testing "a freshly created store's ledger is empty"
    (let [st (store/mem-store)]
      (is (empty? (store/ledger st))))))

(deftest append-ledger-test
  (testing "appending a fact grows the ledger, in append order, and
            returns the appended fact"
    (let [st (store/mem-store)
          fact-1 {:t :committed :op :log-vineyard-record :subject "vineyard-001"}
          fact-2 {:t :governor-hold :op :order-supplies :subject "vineyard-002"}
          appended (store/append-ledger! st fact-1)]
      (is (= fact-1 appended))
      (is (= [fact-1] (store/ledger st)))
      (store/append-ledger! st fact-2)
      (is (= [fact-1 fact-2] (store/ledger st)))))

  (testing "the ledger is independent per store instance"
    (let [st-a (store/mem-store)
          st-b (store/mem-store)]
      (store/append-ledger! st-a {:t :committed :op :order-supplies})
      (is (= 1 (count (store/ledger st-a))))
      (is (empty? (store/ledger st-b))))))
