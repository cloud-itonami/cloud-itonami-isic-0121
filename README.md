# cloud-itonami-isic-0121

Open Occupation Blueprint for **ISIC Rev. 4 0121**: Growing of grapes.

This repository implements a forkable OSS **vineyard operations
coordinator**: a facility-management robot manages vineyard/block record
logging, field-operation (pruning/spraying/harvest) scheduling, and supply
procurement under a governor-gated actor, so a grape-growing operation
keeps its own operational records and maintains full transparency over
decisions.

**Maturity: `:implemented`.** `src/vineyardops/` implements the
`VineyardOpsAdvisor` (`vineyardops.advisor`) and the independent
`VineyardOperationsGovernor` (`vineyardops.governor`), composed by
`vineyardops.operation` following the itonami actor pattern
(ADR-2607011000): `advise -> govern -> phase-gate -> commit | escalate |
hold`. See [Testing](#testing) below for the current green test count
(`clojure -M:test`).

`vineyardops.operation` is a synchronous stub of this flow (see its
docstring) — production wiring into a `langgraph-clj` StateGraph with
`interrupt-before`/checkpoint-based human-in-the-loop resume for escalated
operations is deferred, mirroring `cloud-itonami-isic-0141`'s own
`cattleops.operation`.

## What this does NOT do

This actor coordinates **back-office logistics only**. It explicitly does **NOT**:

- **Direct field-equipment operation** — remains the grower's exclusive authority
- **Spray-application decisions** — remains the agronomist/grower authority
- **Harvest-timing / economic decisions** — economic authority remains human
- **Direct execution of any kind** — any proposal for direct actuation is a hard block

## HARD invariants (always hold, never overridable)

1. **vineyard-not-registered** — the request's `vineyard-id` must resolve to a
   registered vineyard/block in the Store before any proposal can proceed
2. **no-execution** — every proposal's `:effect` must be `:propose` (the governor
   never directly operates field equipment, never finalizes a spray application)
3. **field-equipment-or-spray-blocked** — `:operate-field-equipment` and
   `:finalize-spray-application` proposals are unconditionally, permanently blocked
4. **op-not-allowed** — any op outside the closed allowlist below is rejected
5. **vineyard-count-invalid** — `:log-vineyard-record` with a non-positive logged
   quantity (vine count / harvest weight / yield estimate / brix reading) is rejected

## Always-escalate operations (human sign-off, regardless of confidence)

- `:flag-crop-health-concern` — any pest (phylloxera)/disease/frost-damage
  concern → automatic escalation
- `:order-supplies` over its category cost threshold (default 500 currency
  units; see `vineyardops.facts/supply-categories`)
- Any proposal with confidence below the Governor's floor (0.7)

## Operational requests (closed allowlist, all `:effect :propose`)

```text
:log-vineyard-record
  — record planting/harvest/yield/brix-test data
  — requires a registered vineyard/block; non-positive quantities are rejected

:schedule-field-operation
  — propose pruning/spraying/harvest scheduling
  — does NOT make or finalize a spray-application decision

:flag-crop-health-concern
  — surface a pest (phylloxera), disease, or frost-damage concern
  — ALWAYS escalates for human review

:order-supplies
  — procurement for rootstock, fertilizer, equipment
  — escalates if cost exceeds its category threshold
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs the
physical domain work**. Here a facility-management robot handles:

- Vineyard/block record logging and entry
- Field-operation scheduling and reminders
- Supply inventory and ordering
- Audit ledger maintenance

The **VineyardOperationsGovernor** is the independent safety layer that gates all
proposals before a robot action is executed. The governor never dispatches hardware
directly; `:high`/`:safety-critical` actions (such as escalated crop-health concerns
or high-cost supply orders) require human sign-off.

## Core Contract

```text
operational request (log, schedule, concern, order)
        |
        v
VineyardOpsAdvisor -> VineyardOperationsGovernor -> phase gate -> commit, or escalate for human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated operation can dispatch a robot action the governor refuses, suppress an
operating record, or hide a crop-health concern without governor approval and audit
evidence.

## Module structure

Mirrors `cloud-itonami-isic-0141` (`cattleops.*`) module-for-module:

- `vineyardops.facts` — reference data: supply-category cost thresholds, grape classes
- `vineyardops.registry` — pure independent verification functions (cost/count/confidence)
- `vineyardops.store` — `Store` protocol + in-memory `MemStore` (vineyard/block registration lookup)
- `vineyardops.advisor` — `Advisor` protocol + `MockAdvisor` (the sealed LLM/decision node)
- `vineyardops.governor` — `VineyardOperationsGovernor`: hard invariants + escalation gates
- `vineyardops.phase` — 0→3 rollout phase gate
- `vineyardops.operation` — composes advisor → governor → phase into one operation run
- `vineyardops.sim` — demo runner (`clojure -M:run`)

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISIC Rev. 4 `0121`). Required capabilities:

- :robotics
- :identity
- :forms
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Testing

```bash
clojure -M:test   # run the suite (see raw output for tests/assertions)
clojure -M:lint   # clj-kondo, 0 errors / 0 warnings
clojure -M:run    # demo runner
```

## License

AGPL-3.0-or-later.
