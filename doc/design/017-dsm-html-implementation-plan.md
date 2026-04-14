# 017 — DSM HTML Output Implementation Plan

Design: `doc/design/017-dsm-html.md`

## Overview

Implement HTML output for `gordian dsm` as a new rendering layer over the
existing DSM data model.

The first version should:
- be self-contained
- require no JavaScript
- use embedded CSS
- present the collapsed SCC matrix clearly
- provide expandable SCC detail sections

This should follow Gordian's normal architecture:
- pure data shaping stays in `gordian.dsm`
- HTML rendering lives in a focused output/rendering namespace
- CLI wiring happens in `main.clj`

## High-level slices

### Slice A — HTML rendering core
Introduce an HTML renderer for DSM reports.

### Slice B — CLI and command output wiring
Add file-oriented HTML output support for `gordian dsm`.

### Slice C — Output polish and verification
Refine matrix styling/bucketting, verify file artifact behavior, and update docs.

---

## File plan

### New source
- `src/gordian/html.clj`

### Existing source to extend
- `src/gordian/main.clj`
- `src/gordian/output.clj` (possibly only for shared helpers if needed)

### New tests
- `test/gordian/html_test.clj`

### Existing tests to extend
- `test/gordian/main_test.clj`

### Docs/state
- `doc/schema.md`
- `PLAN.md`
- `mementum/state.md`

---

## Design choices to preserve during implementation

### 1. HTML is for DSM first
Do not generalize prematurely to all commands.
The first HTML renderer should target `dsm` only.

### 2. Self-contained output
Emit one HTML document with embedded CSS and no external assets.

### 3. Static-first
Use semantic HTML and native `<details>` / `<summary>` for expand/collapse.
Do not require JavaScript.

### 4. Reuse existing DSM payload
Render directly from the existing `dsm` output shape:
- `:collapsed :summary`
- `:collapsed :blocks`
- `:collapsed :edges`
- `:scc-details`

---

## Step-wise implementation plan

## Step 1 — `html.clj` foundation and safe HTML helpers

**Source:** new `src/gordian/html.clj`
**Test:** new `test/gordian/html_test.clj`

Create the new HTML rendering namespace and implement the minimum safe HTML
building helpers.

### Functions
- `escape-html [s]` → escaped string
- `tag [name attrs body]` → HTML element string
- `page [title body]` → complete HTML document skeleton
- `join-html [xs]` → concatenation helper

### Behavior
- escape `&`, `<`, `>`, `"`, and `'`
- attributes rendered in stable order for deterministic tests
- `page` includes doctype, head, title, charset, and body wrapper

### Tests
Add tests for:
- `escape-html` escapes all five special characters
- `escape-html` leaves plain text unchanged
- `tag` renders element with no attrs
- `tag` renders attrs in deterministic order
- `tag` escapes attribute values
- `tag` preserves nested body content
- `page` returns full HTML document with doctype and title
- `join-html` concatenates fragments in order

### Exit criteria
- HTML renderer foundation exists and is test-covered

---

## Step 2 — Summary cards and block/edge tables

Implement the non-matrix structural sections first.

### Functions
- `summary-cards [summary]`
- `block-table [blocks]`
- `edge-table [edges]`
- helper renderers for block rows / edge rows

### Behavior
- summary cards show SCC blocks, cyclic SCCs, largest SCC, inter-block edges, density
- block table includes id, size, cyclic, density, members
- edge table includes from, to, edge-count
- singleton and cyclic blocks visually distinguishable via classes/badges

### Tests
Add tests for:
- summary cards include all five metrics
- block table includes headers and block ids
- block table includes member names
- cyclic block row indicates cyclic state
- edge table includes from/to/edge-count headers
- edge table includes counted block edges
- edge table renders sensible empty state when no edges exist

### Exit criteria
- HTML has readable textual/reference sections before matrix work begins

---

## Step 3 — Collapsed SCC matrix HTML rendering

Implement the primary matrix artifact.

### Functions
- `edge-intensity-class [edge-count]`
- `collapsed-matrix [blocks edges]`
- helper lookup functions for block edge values

### Behavior
- render a real HTML `<table>` for the collapsed SCC matrix
- use block ids as row/column headers
- diagonal cells use muted class
- empty cells use empty class
- nonzero cells show edge count and intensity class
- matrix wrapped in horizontally scrollable container

### Tests
Add tests for:
- matrix includes row and column block headers
- diagonal cells render with diagonal class
- empty off-diagonal cells render empty state
- nonzero cells include edge count text
- edge-intensity buckets map correctly for 1, 2, 3, and 4+
- matrix renders deterministically for stable input
- matrix handles single-block input without error

### Exit criteria
- collapsed SCC matrix is rendered as a readable HTML table

---

## Step 4 — SCC detail sections and mini-matrices

Implement the drilldown sections for non-singleton SCCs.

### Functions
- `mini-matrix [detail]`
- `scc-detail-section [detail]`
- `scc-details-section [details]`

### Behavior
- each non-singleton SCC detail is rendered inside `<details>` / `<summary>`
- summary line includes block id, size, and density
- detail body includes members, internal edge count, and mini-matrix
- mini-matrix uses local coordinate basis from `:members`
- empty detail section omitted when there are no non-singleton SCCs

### Tests
Add tests for:
- detail section renders `<details>` and `<summary>`
- detail section includes block id, size, density, and members
- mini-matrix includes local row/column headers
- mini-matrix marks internal edges
- singleton-only input omits SCC detail section entirely
- multiple detail sections preserve block-id order

### Exit criteria
- internal SCC structure is rendered as compact drilldown sections

---

## Step 5 — Full DSM HTML page assembly

Combine all HTML sections into one self-contained report.

### Functions
- `dsm-html [data]` → full HTML document string
- embedded CSS constant or generator

### Behavior
The full page should include:
1. header
2. summary cards
3. collapsed matrix
4. block table
5. inter-block edge table
6. SCC detail sections

### Tests
Add tests for:
- `dsm-html` returns a complete HTML document
- page includes report title and source dirs
- page includes summary section heading
- page includes collapsed matrix section heading
- page includes block table section heading
- page includes inter-block dependency section heading
- page includes cyclic SCC details section when present
- page omits cyclic SCC details section when absent
- page includes embedded CSS

### Exit criteria
- one function can render the complete DSM HTML artifact

---

## Step 6 — CLI wiring for HTML file output

Wire HTML generation into the `dsm` command path.

### CLI choice
Implement:

```text
--html-file <file>
```

for `gordian dsm`.

### Changes
- extend CLI spec with `:html-file`
- allow `dsm` command to write HTML artifact when `--html-file` is present
- retain existing text/markdown/EDN/JSON behavior
- decide precedence/error behavior with other output flags

### Recommended output rule
For v1, `--html-file` should be allowed alongside normal stdout modes:
- stdout continues to emit selected text/markdown/EDN/JSON output
- HTML file is written as an additional artifact

Alternative stricter behavior could be chosen later, but additive artifact
behavior is the most ergonomic for CI and local use.

### Tests
**File:** `test/gordian/main_test.clj`

Add tests for:
- `parse-args ["dsm" "--html-file" "out.html"]` captures html-file path
- `dsm-cmd` writes HTML file when `:html-file` provided
- written file contains HTML doctype and title
- `dsm-cmd` still emits EDN when both `:edn true` and `:html-file` are provided
- `dsm-cmd` still emits JSON when both `:json true` and `:html-file` are provided
- integration: fixture DSM with html-file creates non-empty file

### Exit criteria
- `gordian dsm` can generate a shareable HTML artifact from CLI

---

## Step 7 — Output polish

Refine readability and robustness after end-to-end flow exists.

### Polish targets
- improve cell intensity bucket classes
- add title/tooltips on matrix headers and cells
- add anchor links from block ids to SCC detail sections
- improve member list formatting for long namespaces
- ensure page remains readable when block count is moderately large

### Tests
Add tests for:
- matrix header cells include tooltip/title attributes
- nonzero matrix cells include tooltip/title attributes
- block ids link to detail anchors for cyclic SCCs
- generated HTML remains deterministic for the same input

### Exit criteria
- HTML report is materially easier to use than text output for DSM inspection

---

## Step 8 — Docs, schema, state, and smoke tests

Document the new HTML artifact mode and record project state.

### Changes
- update `doc/schema.md` if HTML output conventions are documented there
- update `PLAN.md`
- update `mementum/state.md`

### Verification tasks
- run full test suite
- smoke test:
  - `gordian dsm . --html-file dsm.html`
  - `gordian dsm . --edn --html-file dsm.html`
  - open resulting HTML locally and visually inspect

### Exit criteria
- docs reflect HTML artifact support
- state reflects implementation
- HTML output works end-to-end

---

## Suggested implementation order summary

1. HTML helpers and page skeleton
2. summary cards and tables
3. collapsed matrix
4. SCC details and mini-matrices
5. full HTML page assembly
6. CLI `--html-file` wiring
7. polish (tooltips/anchors/readability)
8. docs/state/smoke tests

This sequence keeps the feature incremental and testable while ensuring that
HTML remains a pure rendering layer over already-existing DSM data.
