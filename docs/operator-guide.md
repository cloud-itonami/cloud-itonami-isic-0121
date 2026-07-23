# Operator Guide: Vineyard Operations Coordinator

## Overview

The Vineyard Operations Coordinator is a facility-management robot that:

1. **Logs operational data** — planting counts, harvest weights, yield, brix readings
2. **Schedules coordination** — pruning/spraying/harvest field operations, supply orders
3. **Escalates concerns** — any crop health, pest, disease, or frost-damage issue
4. **Maintains transparency** — audit ledger traces all decisions

The robot is **not** the decision-maker. The grower/agronomist make all
decisions about spray application, crop health response, and economic
choices. The robot **proposes** actions and escalates when human input is
needed.

## Operating the Actor

### Prerequisites

1. **Vineyard/Block Registration** — your vineyard/block must be registered in
   the system before any operation can proceed
2. **Authorized User** — operator must be authenticated and authorized
3. **Clear Request Type** — specify what you're doing:
   - `:log-vineyard-record` — record planting/harvest/yield/brix-test data
   - `:schedule-field-operation` — arrange pruning/spraying/harvest
   - `:flag-crop-health-concern` — report a pest/disease/frost concern
   - `:order-supplies` — procurement request

### Workflow

1. **Submit Request**
   ```clojure
   {:vineyard-id "vineyard-001"
    :op :log-vineyard-record
    :record-type "harvest"
    :count 500
    :notes "healthy yield"}
   ```

2. **Actor Processes** (real compiled `langgraph-clj` `StateGraph`,
   `vineyardops.operation/build`, run via `langgraph.graph/run*`)
   - `:advise` — `VineyardOpsAdvisor` proposes an action (`vineyardops.advisor`)
   - `:govern` — `VineyardOperationsGovernor` checks hard invariants and escalation gates (`vineyardops.governor`)
   - `:decide` — rollout-phase constraints applied on top of the Governor's verdict (`vineyardops.phase`)

3. **Outcomes** (`:decision` on the graph's return state)
   - **`:commit`** — operation logged, robot proceeds (`:record` is present, and an
     audit fact `:t :committed` is appended to `vineyardops.store`'s ledger)
   - **`:escalate`** — the graph GENUINELY interrupts (checkpointed) at
     `:request-approval`, held pending human decision (audit fact
     `:t :approval-requested`); nothing is appended to the ledger until a
     human resumes
   - **`:hold`** — operation blocked, hard violation (audit fact `:t :governor-hold`,
     cites `:violations`, appended to the ledger)

### Escalation Scenarios

**Automatic escalation (always human sign-off):**
- `:flag-crop-health-concern` — any pest/disease/frost-damage issue
- Supply orders over cost threshold (default 500 currency units)
- Low confidence operations (< 0.7)

**Hard blocks (no override):**
- `:operate-field-equipment` — direct equipment operation is grower authority
- `:finalize-spray-application` — spray-application decisions are agronomist/grower authority
- Missing/unregistered vineyard/block — must register first

### Resuming Escalated Operations

`vineyardops.operation/build` compiles a REAL `langgraph-clj`
`StateGraph` with `interrupt-before #{:request-approval}`: an
`:escalate` disposition GENUINELY pauses (checkpointed) the compiled
graph at the `:request-approval` node — `(langgraph.graph/run* actor
{:request .. :context ..} {:thread-id tid})` returns with
`:status :interrupted` and `:frontier [:request-approval]`, and
**nothing lands in the ledger yet**. A human operator resumes the SAME
compiled graph/thread with
`(langgraph.graph/run* actor {:approval {:status :approved :by ..}}
{:thread-id tid :resume? true})` (or `:rejected`), which routes to
`:commit` or `:hold` respectively and appends exactly one fact to the
ledger. See `vineyardops.sim`/`test/vineyardops/operation_test.cljc` for
worked examples.

## Audit & Transparency

Every graph run's final state includes an `:audit` vector containing an
advisor-proposal trace and a disposition fact (`:committed`,
`:governor-hold`, `:approval-requested`, `:approval-granted`, or
`:approval-rejected`). The `:commit` and `:hold` graph nodes ALSO
genuinely append the terminal decision fact to `vineyardops.store`'s
append-only audit ledger (`store/ledger` / `store/append-ledger!`) — not
a backend-integration concern left to callers; it is wired into the
compiled graph itself.

- Every proposal produces a trace, regardless of outcome
- Every hold cites the specific Governor rule(s) violated (`:violations`)
- Every escalation cites its `:reason` (always-escalate op / high cost / low confidence)

## Integration

The actor provides a standard protocol (`vineyardops.store/Store`) for backend
integration:

- **Vineyard/block lookup** — `(store/registered-vineyard store vineyard-id)`
- **Audit ledger read** — `(store/ledger store)`
- **Audit ledger append** — `(store/append-ledger! store fact)` (called by the
  compiled graph's `:commit`/`:hold` nodes; not a caller responsibility)

Implementations include in-memory `MemStore` (testing, `vineyardops.store`),
and future Datomic/kotoba-server backends (the same seam point all
cloud-itonami actors use). Record-commit (applying `:record` to the SSoT)
remains an integration responsibility on top of the compiled graph's
return state; ledger-append is now genuinely part of the `Store` protocol
and is wired into `vineyardops.operation/build`'s `:commit`/`:hold` nodes
itself.

## Safety Guarantees

- **No unsupervised decisions** — no spray-application or crop-health
  response decision is made by the robot
- **No suppressed concerns** — crop health concerns cannot be hidden or delayed
- **No unlogged operations** — every action is recorded in the audit ledger
- **No direct execution** — the governor gates every robot action

The robot is safe because:
1. It never decides — it proposes
2. It always escalates when needed
3. It never hides information
4. Every action is auditable
