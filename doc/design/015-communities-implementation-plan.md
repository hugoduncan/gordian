# 015 — Communities Implementation Checklist

## Slice A — Pure graph construction
- [ ] Create `src/gordian/communities.clj`
- [ ] Implement canonical undirected edge representation
- [ ] Implement `undirected-structural-edges`
- [ ] Implement `conceptual-edges`
- [ ] Implement `change-edges`
- [ ] Implement `combined-edges`
- [ ] Implement `threshold-edges`
- [ ] Implement `adjacency-map`
- [ ] Implement `connected-components`
- [ ] Add `test/gordian/communities_test.clj`
- [ ] Add tests for each pure function above

## Slice B — Pure community metrics
- [ ] Implement `community-edge-count`
- [ ] Implement `community-density`
- [ ] Implement `internal-boundary-weight`
- [ ] Implement `bridge-namespaces`
- [ ] Implement `dominant-terms`
- [ ] Implement `community-report`
- [ ] Add tests for all community metrics
- [ ] Add full report assembly tests

## Slice C — CLI and command wiring
- [ ] Add `communities` command to CLI usage and parser
- [ ] Add `--lens` option
- [ ] Add `--threshold` option
- [ ] Add `communities-cmd` to `main.clj`
- [ ] Build report with appropriate active lenses
- [ ] Add parser and integration tests in `main_test.clj`

## Slice D — Output
- [ ] Add `format-communities`
- [ ] Add `format-communities-md`
- [ ] Add `print-communities`
- [ ] Add text output tests
- [ ] Add markdown output tests

## Slice E — Docs and schema
- [ ] Add `:communities` command to schema docs
- [ ] Update PLAN.md
- [ ] Update mementum/state.md
- [ ] Run full tests
- [ ] Smoke test text/markdown/EDN/JSON
