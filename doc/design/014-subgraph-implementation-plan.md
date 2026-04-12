# 014 — Subgraph Views Implementation Checklist

## Slice A — Pure subgraph core
- [ ] Create `src/gordian/subgraph.clj`
- [ ] Implement `match-prefix?`
- [ ] Implement `members-by-prefix`
- [ ] Implement `induced-graph`
- [ ] Implement `graph-density`
- [ ] Implement `boundary-edges`
- [ ] Implement `pair-membership`
- [ ] Implement `filter-pairs`
- [ ] Implement `finding-touches-members?`
- [ ] Implement `filter-findings`
- [ ] Implement `summary-counts`
- [ ] Implement `subgraph-summary`
- [ ] Add `test/gordian/subgraph_test.clj`
- [ ] Add tests for each pure function

## Slice B — CLI and command wiring
- [ ] Add `subgraph` command to CLI usage and parser
- [ ] Add `subgraph-cmd` to `main.clj`
- [ ] Build enriched diagnose-style report before slicing
- [ ] Add `explain` fallback to subgraph when no exact ns match exists
- [ ] Add parser and integration tests in `main_test.clj`

## Slice C — Output
- [ ] Add `format-subgraph`
- [ ] Add `format-subgraph-md`
- [ ] Add `print-subgraph`
- [ ] Add text output tests
- [ ] Add markdown output tests

## Slice D — Docs and schema
- [ ] Add `:subgraph` command to schema docs
- [ ] Update PLAN.md
- [ ] Update mementum/state.md
- [ ] Run full tests
- [ ] Smoke test text/markdown/EDN/JSON
