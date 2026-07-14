(ns vineyardops.advisor
  "VineyardOpsAdvisor -- the contained LLM/decision node. This actor's
  intelligence layer proposes back-office coordination actions (vineyard
  record logging, field-operation scheduling, crop-health-concern flags,
  supply procurement) based on vineyard/block state and operator input.
  The advisor is SEALED into the `:advise` step of the operation flow;
  every proposal is routed through the independent Governor before
  committing.

  The advisor makes proposals but has NO direct authority. Proposals are
  always censored by:
    1. Governor (vineyard registration, closed-op allowlist, cost/health
       gates)
    2. Phase gate (rollout stage)
    3. Human operator (for escalated actions)

  Current implementation is a mock advisor for testing. Production should
  use langchain/Claude or similar LLM backend (same seam point as
  `cattleops.advisor`, cloud-itonami-isic-0141)."
  )

;; Protocol for swappable advisor implementations
(defprotocol Advisor
  (-advise [advisor store request]
    "Given store and request, return a proposal map with :op, :effect,
    :value, :cites, :summary, :confidence (plus any op-specific top-level
    keys the Governor independently verifies, e.g. :count/:cost)."))

;; Mock advisor for testing
(defrecord MockAdvisor []
  Advisor
  (-advise [_advisor _store request]
    (let [{:keys [op vineyard-id]} request]
      (case op
        :log-vineyard-record
        {:op :log-vineyard-record
         :effect :propose
         :count (:count request 0)
         :value {:vineyard-id vineyard-id
                 :record-type (:record-type request "harvest")
                 :count (:count request 0)
                 :notes (:notes request "unspecified")}
         :cites ["operator-submitted-record"]
         :summary "Vineyard record (planting/harvest/yield/brix-test) entry logged from operator submission"
         :confidence 0.9}

        :schedule-field-operation
        {:op :schedule-field-operation
         :effect :propose
         :value {:vineyard-id vineyard-id
                 :requested-date (:requested-date request)
                 :operation-type (:operation-type request "pruning")}
         :cites ["operator-scheduling-request"]
         :summary "Field operation (pruning/spraying/harvest) scheduling proposed per operator request"
         :confidence 0.85}

        :flag-crop-health-concern
        {:op :flag-crop-health-concern
         :effect :propose
         :concern (:concern request "unspecified concern")
         :value {:vineyard-id vineyard-id
                 :concern (:concern request "unspecified concern")
                 :recommended-action "agronomist-review"}
         :cites ["operator-observation"]
         :summary "Crop health concern (pest/phylloxera/disease/frost-damage) flagged for agronomist review"
         :confidence 0.8}

        :order-supplies
        {:op :order-supplies
         :effect :propose
         :cost (:cost request 0)
         :value {:vineyard-id vineyard-id
                 :category (:category request "rootstock")
                 :cost (:cost request 0)}
         :cites ["operator-procurement-request"]
         :summary "Supply order (rootstock/fertilizer/equipment) proposed for vineyard block"
         :confidence 0.85}

        ;; fallback -- unrecognized op. The Governor's closed allowlist
        ;; independently rejects this regardless of what the advisor says.
        {:op op
         :effect :propose
         :value {}
         :cites []
         :summary "Operation not recognized"
         :confidence 0.0}))))

(defn mock-advisor []
  (MockAdvisor.))

(defn trace
  "Audit trail entry for an advisor proposal. Recorded whenever a proposal
  is generated, regardless of whether it's approved."
  [request proposal]
  {:t :advisor-proposal
   :op (:op request)
   :vineyard-id (:vineyard-id request)
   :proposal-summary (:summary proposal)
   :confidence (:confidence proposal)})
