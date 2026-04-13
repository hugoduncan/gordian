# 018 — DSM Diagonal Block Partitioning Implementation Plan

Design: `doc/design/018-dsm-diagonal-block-partitioning.md`

## Overview

Replace the current SCC-first DSM basis with a matrix-native pipeline:

1. build a project-only dependency DAG-like graph
2. compute a stable dependency-respecting order
3. partition that linear order into contiguous diagonal blocks
4. score partitions with a Thebeau-style cost function
5. build a quotient block graph from the chosen partition
6. render the result through existing DSM text/markdown/EDN/JSON/HTML paths

The implementation should preserve Gordian's overall architecture:
- pure graph and partition logic in `gordian.dsm`
- rendering layers in `output.clj` and `html.clj`
- CLI wiring in `main.clj`

## High-level slices

### Slice A — Order + interval/block cost foundation
Introduce ordered-DSM concepts independent of SCCs.

### Slice B — Dynamic programming partitioning
Implement optimal contiguous segmentation for a fixed order.

### Slice C — Quotient graph and report shape
Build the block-level report structure from the partition.

### Slice D — Rendering adaptation
Update text/markdown/HTML renderers to the new block model.

### Slice E — Optional local refinement
Add deterministic adjacent-swap improvement if needed after the first useful version lands.

---

## File plan

### Existing source to revise
- `src/gordian/dsm.clj`
- `src/gordian/output.clj`
- `src/gordian/html.clj`
- `src/gordian/main.clj`

### Existing tests to revise/extend
- `test/gordian/dsm_test.clj`
- `test/gordian/output_test.clj`
- `test/gordian/html_test.clj`
- `test/gordian/main_test.clj`

### Docs/state
- `doc/schema.md`
- `PLAN.md`
- `mementum/state.md`

---

## Migration strategy

The current DSM shape is SCC-oriented:
- `:collapsed`
- `:scc-details`
- `:cyclic?`

The revised design is partition-oriented.

### Recommendation
Keep the top-level command `:dsm`, but revise the payload shape to reflect the
new semantics.

Suggested new structure:

```clojure
{:basis     :diagonal-blocks
 :ordering  {...}
 :blocks    [...]
 :edges     [...]
 :summary   {...}
 :details   [...]} ; optional block detail matrices
```

If backward compatibility for existing DSM machine consumers matters, retain
`:collapsed` as an interim compatibility wrapper and add the new structure
inside it. Otherwise, prefer a clean replacement.

---

## Step-wise implementation plan

## Step 1 — Project graph + stable dependency-respecting order

**Source:** `src/gordian/dsm.clj`
**Test:** `test/gordian/dsm_test.clj`

Implement the new namespace ordering foundation.

### Functions
- `project-graph [graph]`
- `reverse-graph [graph]`
- `topo-order [graph]`
- `dfs-topo-order [graph]`
- `ordered-nodes [graph]`

### Behavior
- order only project namespaces
- dependency-respecting order
- deterministic tie-break by lexicographic namespace name
- emit dependees-first order consistent with current Gordian convention

### Tests
Add tests for:
- `project-graph` removes external dependency targets
- `reverse-graph` inverts edges correctly
- `topo-order` orders simple chains dependees-first
- `dfs-topo-order` is deterministic under map key variation
- `ordered-nodes` returns all project namespaces exactly once
- `ordered-nodes` preserves dependency validity on acyclic examples
- `ordered-nodes` stable tie-break puts `myapp.billing.*` before `myapp.reporting.*`

### Exit criteria
- a deterministic project-only namespace order exists independent of SCCs

---

## Step 2 — Ordered matrix primitives and interval statistics

Implement the matrix statistics needed for block scoring.

### Functions
- `index-of [ordered-nodes]`
- `ordered-edges [graph ordered-nodes]`
- `interval? [a b i]`
- `internal-edge-count [ordered-edges a b]`
- `cross-edge-count [ordered-edges a b n]`
- interval precomputation helpers as needed

### Behavior
- represent edges in index space
- support efficient scoring of interval blocks `[a,b]`
- only project-internal edges count

### Tests
Add tests for:
- `index-of` maps nodes to positions correctly
- `ordered-edges` converts namespace edges to index edges
- `internal-edge-count` counts edges entirely within interval
- `cross-edge-count` counts interval boundary crossings correctly
- empty and singleton intervals behave correctly
- interval stats are deterministic and bounded

### Exit criteria
- ordered matrix edge statistics are available for partition scoring

---

## Step 3 — Block cost function

Implement the Thebeau-style scoring model for one candidate interval block.

### Functions
- `block-cost [interval-stats n alpha a b]`
- helpers for `|B|^alpha`, matrix penalty, etc.

### Behavior
- internal marks cost `|B|^alpha`
- cross-boundary marks cost `n^alpha`
- support configurable `alpha`
- default alpha should be introduced in a single obvious place
- allow a small additional sparsity penalty for weakly cohesive multi-namespace
  blocks when evidence shows the pure objective over-absorbs residual nodes

### Tests
Add tests for:
- singleton block cost is finite and deterministic
- larger blocks increase internal-edge cost appropriately
- crossing edges are more expensive than internal edges for same count
- alpha affects block cost monotonically
- cost behavior matches hand-worked examples

### Exit criteria
- interval blocks can be scored directly and transparently

---

## Step 4 — Dynamic programming partitioner

Implement optimal contiguous partitioning for a fixed namespace order.

### Functions
- `optimal-partition [graph ordered-nodes alpha]`
- `reconstruct-partition [backpointers]`
- block interval helpers

### Behavior
- consider all split points
- compute minimum-cost prefix partition
- return contiguous blocks in order
- deterministic result when multiple partitions tie

### Tests
Add tests for:
- empty graph yields empty partition
- singleton graph yields one block
- simple chain yields sensible partition
- dense local cluster plus sparse tail produces multi-block partition
- deterministic tie-break on equal-cost partitions
- returned blocks are contiguous and non-overlapping
- returned blocks cover all ordered namespaces exactly once

### Exit criteria
- optimal contiguous diagonal partitioning works for fixed orders

---

## Step 5 — Quotient block graph and block records

Build the new block-level DSM structure from the partition.

### Functions
- `index-blocks [ordered-blocks]`
- `ns->block-id [blocks]`
- `block-members [ordered-nodes intervals]`
- `block-edge-counts [graph blocks]`
- `block-density [graph members]`
- `annotate-blocks [graph blocks]`

### Behavior
- each block is a contiguous interval mapped back to namespace members
- inter-block edges counted from original graph
- block density computed from within-block edges
- no SCC semantics in primary block model

### Tests
Add tests for:
- `block-members` maps interval partitions back to ordered namespaces correctly
- `ns->block-id` maps namespaces to containing block
- `block-edge-counts` counts quotient edges correctly
- `annotate-blocks` adds size/density/internal-edge-count
- block records preserve order and contiguity

### Exit criteria
- partitioned blocks can be treated as first-class DSM units

---

## Step 6 — New DSM report assembly

Assemble the complete pure DSM payload in its revised block-partition form.

### Functions
- `dsm-report [graph]`
- `summary [blocks edges order-meta]`

### Suggested payload shape

```clojure
{:basis    :diagonal-blocks
 :ordering {:strategy :dfs-topo
            :alpha    1.5
            :nodes    [...]}
 :blocks   [{:id ... :members ... :size ... :density ... :internal-edge-count ...}]
 :edges    [{:from ... :to ... :edge-count ...}]
 :summary  {:block-count ... :largest-block-size ... :inter-block-edge-count ... :density ...}
 :details  [{:id ... :members ... :internal-edges ...}]}
```

### Tests
Add tests for:
- `dsm-report` returns basis/order/blocks/edges/summary
- report contains all project namespaces exactly once across blocks
- report excludes external namespaces from basis
- report is deterministic for same graph input
- details correspond to block members and internal edges

### Exit criteria
- one pure report function produces the new DSM model end-to-end

---

## Step 7 — Text and markdown rendering migration

Update `output.clj` to render partitioned diagonal blocks instead of SCC blocks.

### Changes
- revise `format-dsm`
- revise `format-dsm-md`
- revise any helper wording referencing SCCs/cycles

### Output changes
Text/markdown should now emphasize:
- ordering strategy
- block count / largest block / inter-block edges
- diagonal blocks as modules
- no SCC/cycle language unless explicitly derived as extra annotation

### Tests
Add/update tests for:
- summary headings reflect block-partition semantics
- block labels and members still appear
- inter-block edges still render
- detail sections render block internals rather than SCC internals
- singleton-only cases remain readable

### Exit criteria
- human-readable DSM output matches the new model

---

## Step 8 — HTML rendering migration

Update `html.clj` to reflect block-partition semantics.

### Changes
- revise section labels from SCC-oriented wording to block-partition wording
- keep matrix/table/detail structure
- keep namespace-rich labels in matrix headers
- no “no non-singleton SCCs” message; replace with block-oriented empty/detail semantics

### Tests
Add/update tests for:
- HTML headings reflect block-partition terminology
- block table and matrix remain readable
- detail section wording no longer references SCC unless explicitly retained as annotation
- HTML still renders complete self-contained document

### Exit criteria
- HTML DSM matches the new architecture model and terminology

---

## Step 9 — CLI stability and output compatibility

Ensure `gordian dsm` continues to work through existing command paths.

### Changes
- keep `dsm-cmd` wiring intact
- preserve `--edn`, `--json`, `--markdown`, `--html-file`
- decide whether to expose `alpha`/ordering strategy as flags now or later

### Recommendation
Do not add extra CLI flags in the first migration unless required.
Ship a strong default first.

### Tests
Add/update tests for:
- `dsm-cmd` returns revised payload shape via EDN/JSON
- `dsm-cmd` still writes HTML file
- fixture integration outputs remain non-empty and parseable

### Exit criteria
- command UX remains stable while internals and payload semantics evolve

---

## Step 10 — Optional local refinement

Add deterministic adjacent-swap improvement if the initial partition quality is insufficient.

### Functions
- `swap-cost-delta`
- `valid-adjacent-swap?`
- `block-swap-valid?`
- `refine-order [graph ordered-nodes alpha]`

### Behavior
- first swap adjacent incomparable nodes
- then swap adjacent blocks whose members are pairwise incomparable
- accept only cost-lowering swaps
- rerun partition after accepted swaps
- terminate at local optimum

### Tests
Add tests for:
- invalid swaps that violate dependency order are rejected
- valid incomparable swaps can be accepted
- refinement never worsens total cost
- refinement deterministic for same input

### Exit criteria
- optional post-order improvement exists without introducing nondeterminism

---

## Step 11 — Docs, schema, state, and smoke tests

Document the redesign and record project state.

### Changes
- update `doc/schema.md` to revised DSM payload shape
- update `PLAN.md`
- update `mementum/state.md`

### Verification tasks
- run full test suite
- smoke test text, markdown, EDN, JSON, HTML on:
  - gordian itself
  - a larger Polylith-style project

### Exit criteria
- docs reflect new semantics
- project state records the redesign
- output modes all work with the new model

---

## Suggested implementation order summary

1. project-only order foundation
2. ordered edge statistics
3. interval block cost
4. dynamic programming partitioner
5. quotient block graph
6. revised pure `dsm-report`
7. text/markdown migration
8. HTML migration
9. CLI compatibility verification
10. optional local refinement
11. docs/state/smoke tests

This sequence minimizes disruption by building the new pure model first, then
repointing renderers and command output after the core partitioning semantics
are stable.
