# 016 — DSM Implementation Plan

Design: `doc/design/016-dsm.md`

## Overview

Implement `gordian dsm` in small, testable slices.

The feature has two primary artifacts:
- a **collapsed SCC matrix** over the condensation graph
- **detail mini-matrices** for each non-singleton SCC

Implementation should follow Gordian's existing architecture:
- pure data shaping in a new `gordian.dsm` namespace
- CLI and orchestration in `main.clj`
- rendering in `output.clj`
- machine-readable output through existing EDN/JSON envelope conventions

## High-level slices

### Slice A — Pure SCC/DSM core
Create the new pure namespace and implement SCC-derived matrix shaping.

### Slice B — Collapsed DSM report assembly
Build the top-level command data shape from graph + SCC inputs.

### Slice C — CLI wiring
Add `dsm` command parsing and command execution.

### Slice D — Output
Render text and markdown views for collapsed matrix and SCC details.

### Slice E — Docs and project state
Update docs, PLAN/state, and smoke-test output modes.

---

## File plan

### New source
- `src/gordian/dsm.clj`

### New tests
- `test/gordian/dsm_test.clj`

### Existing files to extend
- `src/gordian/main.clj`
- `src/gordian/output.clj`
- `test/gordian/main_test.clj`
- `test/gordian/output_test.clj`
- `doc/schema.md` (if command-shape docs are maintained there)
- `PLAN.md`
- `mementum/state.md`

---

## Step-wise implementation plan

## Step 1 — `gordian.dsm` foundation: SCC block ordering and labels

**Source:** new `src/gordian/dsm.clj`

Implement the minimal pure building blocks needed to talk about SCCs as a
matrix basis.

### Functions
- `block-label [members]` → stable label basis for ordering/debugging
- `ordered-sccs [graph]` → SCCs in deterministic topological order
- `index-blocks [ordered-sccs]` → block metadata with stable `:id`

### Behavior
- use existing SCC functionality as the source of SCC membership
- include singleton SCCs
- order SCCs by topological order of the condensation graph
- use stable lexicographic tie-breaking

### Tests
**File:** `test/gordian/dsm_test.clj`

Add tests for:
- `block-label` on singleton SCC → member name
- `block-label` on multi-member SCC → lexicographically smallest member name
- `ordered-sccs` on acyclic graph → singleton SCCs in topological order
- `ordered-sccs` on graph with one cycle → multi-member SCC appears as one block
- `ordered-sccs` is deterministic under map key order variation
- `index-blocks` assigns consecutive ids starting at 0
- `index-blocks` preserves ordered block membership
- `index-blocks` marks block size correctly
- `index-blocks` can distinguish singleton vs non-singleton blocks via size

### Exit criteria
- deterministic ordered SCC basis exists
- block ids and membership metadata are stable and test-covered

---

## Step 2 — `gordian.dsm` pure collapsed edges and block metrics

Implement the pure shape of the collapsed SCC matrix.

### Functions
- `ns->block-id [blocks]` → namespace to block-id map
- `collapsed-edges [graph blocks]` → inter-block edges with edge counts
- `internal-edge-count [graph members]` → namespace-level internal edge count
- `block-density [graph members]` → directed density within block
- `annotate-blocks [graph blocks]` → add `:cyclic?`, `:internal-edge-count`, `:density`

### Behavior
- collapsed edges count namespace-level edges crossing SCC boundaries
- no self-edges in collapsed edge output
- `:cyclic?` is true for SCC size > 1
- singleton density is `0.0`

### Tests
Add tests for:
- `ns->block-id` maps all members to correct block id
- `collapsed-edges` on simple chain `a -> b -> c` gives two block edges
- `collapsed-edges` merges multiple namespace edges between same blocks into one edge-counted record
- `collapsed-edges` excludes edges internal to the same SCC
- `internal-edge-count` counts only edges with both endpoints inside members
- `block-density` for size 1 is `0.0`
- `block-density` for 2-node mutual dependency is `1.0`
- `block-density` for sparse 3-node SCC is computed correctly
- `annotate-blocks` adds `:size`, `:cyclic?`, `:internal-edge-count`, `:density`
- `annotate-blocks` leaves member ordering stable

### Exit criteria
- collapsed SCC graph can be represented as block records plus counted inter-block edges
- all block-local metrics are available as pure functions

---

## Step 3 — `gordian.dsm` SCC detail mini-matrix data

Implement detail records for each non-singleton SCC.

### Functions
- `ordered-members [members]` → deterministic local member order
- `internal-edge-coords [graph ordered-members]` → local `[i j]` coordinates
- `scc-detail [graph block]` → one SCC detail record
- `scc-details [graph blocks]` → all non-singleton SCC detail records

### Behavior
- detail records only for SCCs with size > 1
- local member order is lexicographic
- coordinates use local member indexes

### Tests
Add tests for:
- `ordered-members` sorts lexicographically
- `internal-edge-coords` on 2-node mutual dep returns `[[0 1] [1 0]]`
- `internal-edge-coords` omits non-existent edges
- `scc-detail` includes `:id`, `:members`, `:size`, `:internal-edges`, `:internal-edge-count`, `:density`
- `scc-detail` density matches `block-density`
- `scc-details` excludes singleton SCCs
- `scc-details` includes multiple cyclic SCCs in block-id order

### Exit criteria
- non-singleton SCC mini-matrices are available as stable machine-readable data

---

## Step 4 — `gordian.dsm` top-level command data assembly

Assemble the complete pure command payload from a structural graph.

### Functions
- `collapsed-summary [blocks edges]` → top-level collapsed metrics
- `dsm-report [graph]` → complete `:collapsed` + `:scc-details` structure

### Behavior
`dsm-report` should produce the canonical internal data model used by CLI and
output layers.

### Tests
Add tests for:
- `collapsed-summary` reports correct `:block-count`
- `collapsed-summary` reports correct `:singleton-block-count`
- `collapsed-summary` reports correct `:cyclic-block-count`
- `collapsed-summary` reports correct `:largest-block-size`
- `collapsed-summary` reports correct `:inter-block-edge-count`
- `collapsed-summary` density is `0.0` for 0/1 blocks
- `collapsed-summary` density is correct for a multi-block graph
- `dsm-report` returns both `:collapsed` and `:scc-details`
- `dsm-report` includes all SCCs in `:collapsed :blocks`
- `dsm-report` includes only non-singleton SCCs in `:scc-details`
- `dsm-report` is deterministic for the same input graph

### Exit criteria
- one pure function can build the complete DSM payload for downstream use

---

## Step 5 — `main.clj` CLI parsing and command wiring

Wire the new command into the existing CLI and pipeline.

### Changes
- add `dsm` subcommand to usage/help
- add parser support for `dsm`
- add `dsm-cmd` execution path in `main.clj`
- build graph using the same discovery/filter pipeline as structural commands
- envelope output as `{:gordian/command :dsm ...}`

### Tests
**File:** `test/gordian/main_test.clj`

Add tests for:
- `parse-args ["dsm"]` → `{:command :dsm}` with default dir behavior
- `parse-args ["dsm" "."]` → `{:command :dsm :src-dirs ["."]}` or equivalent resolved behavior
- `parse-args ["dsm" "--include-tests"]` enables tests inclusion
- `parse-args ["dsm" "--exclude" "foo\\..*"]` preserves exclude regex
- integration: `dsm` on fixture project returns non-empty output
- integration: `dsm --edn` returns envelope with `:gordian/command :dsm`
- integration: `dsm --edn` contains `:collapsed`
- integration: `dsm --edn` contains `:scc-details`
- integration: `dsm --json` emits JSON object with command marker and dsm content

### Exit criteria
- `gordian dsm` runs through the standard CLI path and produces machine-readable output

---

## Step 6 — `output.clj` text rendering for DSM

Add text output for the new command.

### Functions
- `format-dsm`
- any private helpers needed for block list, matrix rendering, sparse edge listing, and SCC detail sections

### Rendering goals
Text output should include:
1. SCC decomposition summary
2. collapsed block summary
3. block listing
4. collapsed matrix or sparse inter-block edge list
5. cyclic SCC detail sections

### Tests
**File:** `test/gordian/output_test.clj`

Add tests for:
- `format-dsm` shows summary counts for blocks/cyclic blocks/largest block
- block list includes block ids and members
- collapsed edge section shows counted block relations
- small collapsed graph renders matrix-like output or equivalent explicit block relations
- non-singleton SCC detail section appears when present
- singleton-only graph omits cyclic SCC detail section
- SCC detail section shows members and density
- empty/minimal graph renders without error

### Exit criteria
- text output is readable and complete for both small acyclic and cyclic examples

---

## Step 7 — `output.clj` markdown rendering for DSM

Add markdown output for the new command.

### Functions
- `format-dsm-md`

### Rendering goals
Markdown should include:
- summary table
- blocks table
- inter-block relation table or compact matrix representation
- per-SCC detail subsections for non-singletons

### Tests
Add tests for:
- `format-dsm-md` includes markdown summary table headers
- blocks table includes block id/size/cyclic/density/members columns
- inter-block table includes from/to/edge-count columns
- cyclic SCC detail subsections render headings
- singleton-only input omits cyclic SCC details section
- markdown output is stable for deterministic input ordering

### Exit criteria
- markdown output is suitable for reports/docs and mirrors the text information architecture

---

## Step 8 — schema/docs/state update

Document the new command and record project state.

### Changes
- update `doc/schema.md` with `:gordian/command :dsm` output shape
- update `PLAN.md`
- update `mementum/state.md`

### Verification tasks
- run full test suite
- smoke test:
  - `gordian dsm`
  - `gordian dsm --markdown`
  - `gordian dsm --edn`
  - `gordian dsm --json`

### Exit criteria
- docs reflect shipped behavior
- state records the feature
- all output modes work end-to-end

---

## Suggested implementation order summary

1. `dsm.clj` ordered SCC basis
2. collapsed edges + block metrics
3. non-singleton SCC detail matrices
4. full pure `dsm-report`
5. CLI wiring in `main.clj`
6. text output
7. markdown output
8. docs/state/smoke tests

This sequence keeps the highest-signal work pure and testable first, then adds
command and rendering layers afterward.
