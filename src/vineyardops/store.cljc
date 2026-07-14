(ns vineyardops.store
  "Store abstraction for vineyard block/planting records. Current
  implementation is an in-memory map; production should migrate to
  Datomic/kotoba-server (the same seam point all cloud-itonami actors
  use). Mirrors `cattleops.store` (cloud-itonami-isic-0141) in shape.

  A registered vineyard block is the minimal unit of authority: a
  vineyard/block must be registered before ANY proposal referencing it can
  be considered by the Governor (see `vineyardops.governor`'s
  `vineyard-registered` invariant). Vineyard data is opaque to this
  namespace -- callers/backends decide what a vineyard record contains
  (name, location, variety, planted area, etc); this Store only answers
  \"is this vineyard-id registered, and if so what's on file\".")

;; Protocol for swappable store implementations
(defprotocol Store
  (registered-vineyard [store vineyard-id]
    "Retrieve a registered vineyard/block record by ID. Returns nil if the
    vineyard-id is nil or not registered."))

;; In-memory implementation (MemStore) for development/testing
(defrecord MemStore [vineyards]
  Store
  (registered-vineyard [_store vineyard-id]
    (when vineyard-id
      (get @vineyards vineyard-id))))

(defn mem-store
  "Create an in-memory store. `initial-vineyards` is an optional map of
  vineyard-id -> vineyard-record."
  [& [{:keys [initial-vineyards] :or {initial-vineyards {}}}]]
  (MemStore. (atom initial-vineyards)))

(defn add-vineyard
  "Register or update a vineyard/block in the store. Used by tests and
  simulation."
  [^MemStore store vineyard-id vineyard-data]
  (swap! (:vineyards store) assoc vineyard-id vineyard-data)
  vineyard-data)
