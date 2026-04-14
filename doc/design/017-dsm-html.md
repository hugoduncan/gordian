# 017 — DSM HTML Output

## Problem

`gordian dsm` now produces useful structural data and machine-readable output,
but its human-readable forms are constrained by text and markdown.

That limitation is especially noticeable for DSMs because the feature is
fundamentally about:
- matrices
- block structure
- hierarchy
- drilldown from collapsed view to internal SCC details

Text and markdown work well for summaries and tables, but they are a poor fit
for presenting a matrix-centric architectural view. The current output is
correct, but it is harder to read than it should be.

## Goal

Add an HTML output mode for `gordian dsm` that presents the same structural
information in a richer, more readable form.

The HTML report should make it easy to:
- understand the collapsed SCC architecture at a glance
- inspect block metadata without mentally decoding IDs
- drill into non-singleton SCC internals
- use visual layout, spacing, and color to communicate structure

The initial HTML mode should be:
- static
- self-contained
- shareable as a file artifact
- useful without requiring JavaScript

---

## Scope

### In scope
- HTML output for `gordian dsm`
- self-contained HTML document with embedded CSS
- collapsed SCC summary cards
- collapsed SCC matrix as an HTML table
- block summary table
- inter-block dependency table
- collapsible detail sections for non-singleton SCCs
- mini-matrix rendering for SCC internals

### Out of scope
- HTML output for all Gordian commands in v1
- interactive graph visualization
- full namespace-level DSM as default HTML content
- client-side reordering or matrix manipulation
- external CSS/JS assets
- SPA / web app architecture
- SVG/canvas-based rendering

---

## Why HTML is justified here

Most Gordian commands are fundamentally list/report shaped. Their terminal and
markdown outputs are already strong.

DSM is different.

It is inherently:
- two-dimensional
- visually structured
- hierarchical
- better understood through layout than prose

This makes DSM one of the strongest cases in the tool for a richer output
medium.

HTML is the right next step because it provides:
- real tabular layout for matrices
- visual grouping and spacing
- collapsible detail sections
- color and emphasis for structural meaning
- a self-contained shareable artifact

---

## Design principles

### 1. Keep the current DSM data model
HTML should be a rendering layer over the existing `dsm` payload.

It should consume:
- `:collapsed :summary`
- `:collapsed :blocks`
- `:collapsed :edges`
- `:scc-details`

This keeps the design aligned with Gordian's architecture:
- pure analysis first
- presentation later

### 2. Overview first, drilldown second
The page should show:
1. high-level summary
2. collapsed SCC matrix
3. block decoding/reference tables
4. SCC detail sections

This mirrors how humans investigate architecture:
- first understand global shape
- then inspect local knots

### 3. Static-first
The first HTML version should be useful with no JavaScript.

Native HTML elements like `<details>` / `<summary>` are preferred over custom
interactive widgets.

### 4. Use visual structure, not visual noise
The HTML output should look like an architecture report, not a dashboard toy.

Use:
- restrained color
- spacing
- typographic hierarchy
- subtle emphasis

Avoid:
- animation-heavy interaction
- excessive color saturation
- decorative visual complexity

---

## CLI shape

Two viable CLI forms exist:

### Option A — `--html`

```bash
gordian dsm . --html > dsm.html
```

Pros:
- consistent with `--json`, `--edn`, `--markdown`

Cons:
- HTML to stdout is awkward for terminal use
- users may accidentally dump raw HTML into terminal output

### Option B — `--html-file <file>`

```bash
gordian dsm . --html-file dsm.html
```

Pros:
- matches HTML's natural use as a file artifact
- avoids noisy terminal output

Cons:
- introduces a different output-mode pattern from the existing flags

### Recommendation
For DSM specifically, prefer:

```bash
--html-file <file>
```

If a generic HTML mode is later added for all commands, the project can decide
whether to:
- standardize on `--html` for stdout, or
- standardize on file-oriented HTML output

For this design, assume a file-oriented HTML artifact is acceptable.

---

## Page structure

The HTML page should be organized into five major sections.

### 1. Header
Top-level metadata:
- report title
- source dirs
- basis (`SCC`)
- optional ordering note (`dependees-first`)

### 2. Summary cards
Top-line metrics presented in a compact visual row/grid.

### 3. Collapsed SCC matrix
Primary visual artifact for global architecture.

### 4. Reference tables
Supporting textual views:
- block summary table
- inter-block dependency table

### 5. Cyclic SCC details
Expandable sections for each non-singleton SCC, including mini-matrices.

---

## Section 1 — Header

### Purpose
Orient the user immediately.

### Content
- `Gordian DSM`
- source directories analyzed
- basis (`SCC`)
- optional note: `collapsed SCC matrix + SCC internals`

### Example

```text
Gordian DSM
Source: src/
Basis: SCC
```

### HTML sketch

```html
<header class="page-header">
  <h1>Gordian DSM</h1>
  <p>Source: <code>src/</code></p>
  <p>Basis: <code>SCC</code></p>
</header>
```

---

## Section 2 — Summary cards

### Purpose
Expose the most important top-line numbers before the user reads any matrix.

### Metrics
Recommended cards:
- SCC blocks
- cyclic SCCs
- largest SCC
- inter-block edges
- collapsed density

### Example

```text
SCC Blocks        41
Cyclic SCCs        3
Largest SCC        5
Inter-block edges 57
Density         0.0348
```

### HTML structure

```html
<section class="summary-cards">
  <div class="card">
    <div class="label">SCC Blocks</div>
    <div class="value">41</div>
  </div>
  ...
</section>
```

### Styling intent
- grid or flex layout
- clear labels
- large numeric values
- subtle bordered cards

---

## Section 3 — Collapsed SCC matrix

### Purpose
Provide the primary architecture visualization.

This matrix shows the condensation graph after collapsing SCCs.

### Basis
- rows = SCC blocks
- columns = SCC blocks
- cell `(Bi, Bj)` shows the counted inter-block edge relation from `Bi` to `Bj`

### Cell semantics
Each non-empty cell displays `edge-count`.

### Visual rules
- diagonal cells are visually muted
- zero cells are blank/light
- nonzero cells are shaded by intensity
- larger edge counts receive stronger shading
- row and column labels use block IDs (`B0`, `B1`, ...)

### Why block IDs only
Full namespace names are too wide for matrix headers.
The block table beneath the matrix serves as the decoding key.

### Matrix container
The matrix should live inside a horizontally scrollable container:

```html
<div class="matrix-scroll">
  <table class="dsm-matrix">...</table>
</div>
```

This prevents wide matrices from breaking page layout.

### Hover semantics
Tooltips are optional but useful.

Suggested hover content for:
- block headers: block id, size, cyclic?, members
- cells: `B3 -> B7`, edge count

Tooltips are an enhancement, not required for understanding.

### Visual intensity classes
Simple class-based bucketting is enough for v1:
- `edge-1`
- `edge-2`
- `edge-3`
- `edge-4plus`

This avoids continuous color scaling complexity.

### Matrix value thresholding
No thresholding should be introduced here in v1.
The matrix should faithfully display all collapsed inter-block edges from the
existing DSM data.

---

## Section 4 — Reference tables

The matrix is the primary visual, but users still need exact textual views.

### 4a. Block summary table

#### Purpose
Decode block IDs and summarize block metadata.

#### Columns
- Block
- Size
- Cyclic
- Density
- Members

#### Example

| Block | Size | Cyclic | Density | Members |
|---|---:|---|---:|---|
| B0 | 1 | no | 0.00 | `gordian.aggregate` |
| B1 | 3 | yes | 0.83 | `foo.a`, `foo.b`, `foo.c` |

#### Behavior
- block id should link to its SCC detail section when applicable
- members should be readable, not abbreviated

### 4b. Inter-block dependency table

#### Purpose
Expose exact relations in a more scan-friendly form than the matrix.

#### Columns
- From
- To
- Edge count

#### Example

| From | To | Edge count |
|---|---|---:|
| B1 | B0 | 2 |
| B3 | B2 | 1 |

#### Rationale
The matrix shows shape.
The table shows exact values.
Both are useful and complementary.

---

## Section 5 — Cyclic SCC details

### Purpose
Reveal the internal structure hidden by the SCC collapse.

Only non-singleton SCCs should appear here.

### Recommended container
Use native HTML disclosure controls:

```html
<details class="scc-detail">
  <summary>Cyclic SCC B7 — 4 members, density 0.58</summary>
  ...
</details>
```

### Why `<details>` / `<summary>`
- no JavaScript required
- accessible
- simple
- semantically appropriate
- ideal for overview → drilldown flow

### Content per SCC detail section
- block heading / summary line
- size
- density
- full member list
- internal edge count
- internal mini-matrix
- optional raw internal edge list

---

## Mini-matrix design

### Purpose
Show the internal dependency structure of a non-singleton SCC.

### Basis
- rows = local ordered members
- columns = local ordered members
- filled cell indicates internal edge

### Rendering rules
- member ordering follows the current deterministic lexicographic order
- diagonal cells muted
- off-diagonal internal edges shown as filled cells
- values need not show counts; boolean presence is enough in v1

### Why boolean cells are enough
Inside an SCC, internal edge multiplicity is not meaningful in the same way as
collapsed inter-block edge counts. What matters is adjacency structure.

### Labeling strategy
For mini-matrices:
- show local numeric headers (`0`, `1`, `2`, ...)
- include a member legend below or above

or
- show abbreviated namespace names if readability remains acceptable

### Recommendation
Use local numeric indexes plus a legend in v1.
This keeps the mini-matrix compact even when namespaces are long.

---

## Navigation and linking

### Block-to-detail linking
Where possible, block references should be linked.

Examples:
- `B7` in the block table links to `#block-B7`
- `B7` in the matrix header links to the same detail section

### Why this matters
It makes the report navigable without requiring JavaScript.

### Behavior for singleton blocks
Singleton SCCs have no detail sections.
Their block links may simply be plain text rather than anchors.

---

## Styling system

The report should use a small, semantic CSS vocabulary.

### Suggested class groups
- layout:
  - `.page`
  - `.page-header`
  - `.section`
  - `.matrix-scroll`
- summary:
  - `.summary-cards`
  - `.card`
  - `.label`
  - `.value`
- matrix:
  - `.dsm-matrix`
  - `.mini-matrix`
  - `.diag`
  - `.empty`
  - `.edge`
  - `.edge-1`
  - `.edge-2`
  - `.edge-3`
  - `.edge-4plus`
- tabular data:
  - `.block-table`
  - `.edge-table`
- SCC detail:
  - `.scc-detail`
  - `.member-list`
  - `.badge`
  - `.badge-cyclic`

### Style direction
Use a restrained report-like aesthetic:
- white or light neutral background
- monospace where namespace precision matters
- subtle borders and row striping
- blue intensity for matrix cells
- amber accent for cyclic blocks

---

## Accessibility and graceful degradation

The report should remain useful:
- without JavaScript
- with reduced CSS support
- when printed to PDF

### Requirements
- semantic headings (`h1`, `h2`, `h3`)
- real tables for matrices and tabular data
- disclosure controls using native elements
- textual labels for all metrics
- no information encoded only by color

### Matrix accessibility note
If color is used for edge intensity, the numeric value must still be present in
non-empty cells.

---

## Output artifact strategy

### Recommendation
Generate a self-contained HTML file.

That means:
- no external CSS
- no external JS
- no external fonts
- no network dependency

This makes the report:
- easy to open locally
- easy to archive
- easy to attach to CI artifacts or PRs

---

## Relationship to existing output modes

HTML should complement, not replace, existing output modes.

### Text
Best for terminal quick-look.

### Markdown
Best for shareable prose/report artifacts in plain text contexts.

### EDN / JSON
Best for tooling and downstream processing.

### HTML
Best for rich human-readable structural inspection.

This is especially true for DSM, where visual layout materially improves the
user experience.

---

## Rejected alternatives

### 1. Improve text only
Rejected as a complete solution because DSM readability is fundamentally a
visual layout problem, not just a line-formatting problem.

### 2. Full client-side web app
Rejected for v1 because it adds substantial complexity before validating the
report structure itself.

### 3. SVG/canvas matrix renderer first
Rejected for v1 because HTML tables are simpler, more accessible, and already
sufficient for the first useful version.

### 4. Full namespace-level matrix in HTML by default
Rejected because the collapsed SCC basis is still the right abstraction in v1.
Full namespace-level rendering can be added later as an explicit debug/export
mode.

---

## Future extensions

### Highlight interactions
- hover a block row in the summary table and highlight matrix row/column
- click a matrix cell to jump to relevant block pair information

### Block-pair drilldown
Show example namespace-level edges responsible for a collapsed edge.

### Community-based DSM HTML
Support alternate block basis using architecture communities.

### Namespace-level matrix mode
Add a debug/export HTML mode for the raw namespace DSM.

### Compare-aware HTML reports
Render before/after DSM differences visually.

### Gate artifact generation
Produce HTML DSM as a CI artifact during refactor ratchet workflows.

---

## Success criteria

The HTML DSM report is successful if a user can:

1. open the file and understand the coarse architecture immediately
2. identify which SCC blocks are cyclic and how large they are
3. read the collapsed SCC matrix without needing to decode raw EDN/JSON
4. inspect non-singleton SCC internals without scrolling through a giant flat report
5. use the report as a shareable artifact in architectural review

The first version does not need advanced interactivity.
It succeeds by making the existing DSM information materially easier to read.
