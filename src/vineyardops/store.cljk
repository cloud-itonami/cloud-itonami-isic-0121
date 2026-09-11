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
  \"is this vineyard-id registered, and if so what's on file\".

  FIX (this commit): the append-only audit ledger (`ledger`/
  `append-ledger!`) is this actor's core missing plumbing before this
  fix -- no such function existed anywhere in the codebase at all, worse
  than the usual dead-code-ledger pattern in some sibling actors: here
  even the concept was entirely absent from `src/`, despite
  `blueprint.edn`'s `:required-technologies [... :audit-ledger]` implying
  one should exist. `vineyardops.operation`'s `:commit`/`:hold` graph
  nodes now append every committed/held/approval-rejected decision fact
  here, so a vineyard/block's operating history (every
  `:log-vineyard-record`/`:schedule-field-operation`/
  `:flag-crop-health-concern`/`:order-supplies` decision) is always a
  query over an immutable log -- the same discipline every sibling
  `cloud-itonami-isic-*` actor's ledger provides. The ledger stays
  append-only; all pre-existing accessors below (`registered-vineyard`,
  `mem-store`, `add-vineyard`) are UNCHANGED.")

;; Protocol for swappable store implementations
(defprotocol Store
  (registered-vineyard [store vineyard-id]
    "Retrieve a registered vineyard/block record by ID. Returns nil if the
    vineyard-id is nil or not registered.")
  (ledger [store]
    "The append-only audit ledger: every committed/held/approval-rejected
    decision fact, in append order.")
  (append-ledger! [store fact]
    "Append one immutable decision fact to the ledger. Returns the fact."))

;; In-memory implementation (MemStore) for development/testing
(defrecord MemStore [vineyards audit-ledger]
  Store
  (registered-vineyard [_store vineyard-id]
    (when vineyard-id
      (get @vineyards vineyard-id)))

  (ledger [_store]
    @audit-ledger)

  (append-ledger! [_store fact]
    (swap! audit-ledger conj fact)
    fact))

(defn mem-store
  "Create an in-memory store. `initial-vineyards` is an optional map of
  vineyard-id -> vineyard-record."
  [& [{:keys [initial-vineyards] :or {initial-vineyards {}}}]]
  (MemStore. (atom initial-vineyards) (atom [])))

(defn add-vineyard
  "Register or update a vineyard/block in the store. Used by tests and
  simulation."
  [^MemStore store vineyard-id vineyard-data]
  (swap! (:vineyards store) assoc vineyard-id vineyard-data)
  vineyard-data)
