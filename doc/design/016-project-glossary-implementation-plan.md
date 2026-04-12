# 016 — Project Glossary Implementation Plan

## Goal

Implement `gordian glossary` as a first-class derived conceptual view that
produces a ranked, explainable project vocabulary from existing Gordian
artifacts.

The implementation should remain:
- pure in its core ranking logic
- deterministic
- explainable via explicit evidence
- incremental, with small test-backed slices

---

## Implementation strategy

Build glossary support in three slices:

### Slice A — Pure glossary model
Add a new pure module:

- `src/gordian/glossary.clj`

Responsibilities:
- aggregate term evidence from namespace terms, conceptual pairs, and
  community dominant terms
- distinguish family-noise evidence from independent conceptual evidence
- compute ranked glossary entries
- classify entries with lightweight `:kind` / `:quality` labels
- support filtering by top-N and minimum score

Primary inputs:
- `:ns->terms`
- `:conceptual-pairs`
- community report from `gordian.communities/community-report`

Primary output:
- canonical glossary data structure with `:entries`, `:summary`, `:filters`

### Slice B — Output rendering
Extend `src/gordian/output.clj` with:
- `format-glossary`
- `format-glossary-md`
- `print-glossary`

Responsibilities:
- render ranked entries in text form
- render copy/paste-friendly markdown
- surface evidence compactly and consistently

### Slice C — CLI / pipeline wiring
Extend existing command plumbing:
- `gordian.main/parse-args`
- help text and usage summary
- `gordian.main/glossary-cmd`
- `gordian.envelope/wrap`
- test runner registration in `bb.edn`

Responsibilities:
- add `glossary` subcommand
- add `--top` and `--min-score`
- auto-enable conceptual analysis for glossary mode
- compute community evidence and glossary result
- support text / markdown / EDN / JSON output

---

## Data flow

`gordian glossary` should follow this pipeline:

1. resolve project directories and options
2. run `build-report` with conceptual analysis enabled
3. retain namespace term map (`:ns->terms`) for glossary derivation
4. derive conceptual communities via `communities/community-report`
5. derive glossary entries via `glossary/glossary-report`
6. render or envelope-wrap the result

---

## Ranking constraints

The implementation should satisfy these ordering constraints even if the exact
weights change later:

1. independent conceptual evidence outranks family-prefix-only evidence
2. repeated pair evidence outranks mere local term repetition
3. cross-context recurrence outranks single-source occurrence
4. generic implementation verbs are downgraded, not necessarily removed
5. the output remains deterministic for unchanged inputs

---

## v1 simplifications

To keep the first implementation small and testable:
- use the normalized token as the displayed term in v1
- use single-term entries only (no phrase extraction)
- use communities' `:dominant-terms` as community vocabulary input
- use lightweight heuristic classification for `:kind` and `:quality`
- avoid any generated natural-language definitions

These simplifications preserve future extensibility without blocking a useful v1.

---

## Risks and mitigations

### Risk: generic verbs dominate output
Mitigation:
- add a small implementation-word penalty
- rank pair/community evidence more strongly than namespace frequency

### Risk: family-prefix noise dominates output
Mitigation:
- prefer `:independent-terms` when present
- penalize family-only evidence heavily
- allow suppression by score threshold and top-N filtering

### Risk: no namespace term map available in glossary mode
Mitigation:
- keep `:ns->terms` on the internal report for glossary derivation
- strip it from machine-readable envelope output afterward as an internal key

### Risk: glossary becomes opaque
Mitigation:
- every entry must carry explicit evidence: namespaces, pairs, communities,
  related terms

---

## Deliverables

- `src/gordian/glossary.clj`
- `test/gordian/glossary_test.clj`
- glossary formatters in `src/gordian/output.clj`
- CLI wiring in `src/gordian/main.clj`
- envelope support and tests
- integration tests for command behavior
- test runner registration in `bb.edn`

---

## Definition of done

The feature is done when:
- `gordian glossary` works in text / markdown / EDN / JSON modes
- glossary entries are ranked and evidence-backed
- family-noise terms do not dominate top results
- tests cover pure ranking, rendering, CLI parsing, and command execution
- all existing tests still pass
- implementation lands as a sequence of small commits