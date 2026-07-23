# Contributing

**Maturity: `:implemented`** — `src/vineyardops/` implements the reference
VineyardOpsAdvisor / VineyardOperationsGovernor actor as a genuinely
compiled `langgraph-clj` `StateGraph` (real `interrupt-before`/
checkpoint-based human-in-the-loop resume for escalated operations, see
`operation.cljc`), backed by an append-only audit ledger
(`vineyardops.store/ledger` + `append-ledger!`).
Contributions that extend coverage are welcome: a Datomic/kotoba-server
`Store` backend, a real LLM `Advisor` implementation, additional Governor
rules, and grape-class/terroir reference-data expansion in
`vineyardops.facts`. Open an issue or PR. License: AGPL-3.0-or-later.
