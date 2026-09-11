(ns vineyardops.facts
  "Reference facts for vineyard operations coordination: supply category
  cost policy and grape-class classification. This namespace contains pure
  lookup functions for domain reference data -- the Governor and Advisor
  consult these instead of inventing thresholds. Mirrors
  `cattleops.facts` (cloud-itonami-isic-0141) in shape.")

(def supply-categories
  "Procurement categories this actor may propose orders for, and the
  default cost threshold above which an order proposal must escalate for
  human sign-off (grower/vineyard-manager)."
  {"rootstock"
   {:id "rootstock" :name "台木" :cost-threshold 500}

   "fertilizer"
   {:id "fertilizer" :name "肥料" :cost-threshold 500}

   "equipment"
   {:id "equipment" :name "設備" :cost-threshold 1000}})

(defn supply-category-by-id [id]
  (get supply-categories id))

(def default-cost-threshold
  "Fallback escalation threshold used when a supply-order proposal doesn't
  cite a known category (never invent a lower bar than this)."
  500)

(def grape-classes
  "End-use classes this actor's vineyard/block records may cover (ISIC
  0121: growing of grapes for wine, for eating, or for raisins)."
  {"wine-grape"  {:id "wine-grape" :name "ワイン用ぶどう"}
   "table-grape" {:id "table-grape" :name "生食用ぶどう"}})

(defn grape-class-by-id [id]
  (get grape-classes id))
