# Implementation Plan: `gordian diagnose`

Design: `doc/design/003-diagnose.md`

## Step 1: `diagnose.clj` — `pair-key`, `health`, `find-cycles`

**Source:** new `src/gordian/diagnose.clj`
**Test:** new `test/gordian/diagnose_test.clj`
**Register** in `bb.edn`

Functions:
- `pair-key [pair]` → `#{:ns-a :ns-b}` canonical identity
- `health [report]` → `{:propagation-cost :health :cycle-count :ns-count}`
- `find-cycles [cycles]` → `[{:severity :high :category :cycle ...}]`

Test fixture: minimal report maps built inline.

Tests:
- `pair-key` produces set of two ns symbols
- `pair-key` is order-independent
- `health` with PC=0.05 → `:healthy`
- `health` with PC=0.20 → `:moderate`
- `health` with PC=0.40 → `:concerning`
- `health` reports correct cycle-count and ns-count
- `find-cycles` with empty cycles → `[]`
- `find-cycles` with one cycle → one finding, severity `:high`, category `:cycle`
- `find-cycles` finding has `:members` in `:subject` and `:size` in `:evidence`
- `find-cycles` with two cycles → two findings

## Step 2: `diagnose.clj` — `find-sdp-violations`, `find-god-modules`, `find-hubs`

Tests use inline node vectors.

Functions:
- `find-sdp-violations [nodes]` → findings for Ca≥2, I>0.5
- `find-god-modules [nodes]` → findings for :shared with extreme values
- `find-hubs [nodes]` → findings for reach > 3× mean

Tests:
- SDP: `{:ca 3 :instability 0.8}` → medium finding
- SDP: `{:ca 0 :instability 1.0}` → not flagged (nothing depends on it)
- SDP: `{:ca 3 :instability 0.0}` → not flagged (stable)
- SDP: `{:ca 1 :instability 0.8}` → not flagged (Ca < 2)
- God: shared node, reach=0.6, fan-in=0.6 (mean ~0.15) → medium finding
- God: shared node, normal values → not flagged
- God: core node with high fan-in → not flagged (not shared)
- Hub: node with reach=0.9, mean=0.1 → low finding
- Hub: node with reach=0.15, mean=0.1 → not flagged (< 3× mean)
- Hub: empty nodes → no findings

## Step 3: `diagnose.clj` — pair findings

Functions:
- `find-cross-lens-hidden [conceptual-pairs change-pairs]` → high findings
- `find-hidden-conceptual [conceptual-pairs cross-keys]` → medium/low findings
- `find-hidden-change [change-pairs cross-keys]` → medium/low findings

Test fixture: pair vectors with mixed structural/hidden.

Tests:
- Cross-lens: pair hidden in both → one `:high` finding with both scores
- Cross-lens: pair hidden in one only → not cross-lens
- Cross-lens: structural pair in both → not flagged
- Cross-lens: empty pairs → empty
- Hidden-conceptual: hidden pair score≥0.20 → `:medium`
- Hidden-conceptual: hidden pair score<0.20 → `:low`
- Hidden-conceptual: structural pair → not flagged
- Hidden-conceptual: pair already in cross-keys → not flagged
- Hidden-change: hidden pair → `:medium` (change is always ≥ threshold)
- Hidden-change: structural pair → not flagged
- Hidden-change: pair in cross-keys → not flagged

## Step 4: `diagnose.clj` — `diagnose` + sorting

Function:
- `diagnose [report]` → sorted `[finding]`

Tests:
- Full report → all finding categories present
- Sorted: all :high before :medium before :low
- Within severity: higher scores first
- Report with no pairs → only structural + health findings
- Report with no cycles → no cycle findings
- Report with no conceptual-pairs key → no conceptual findings
- Report with no change-pairs key → no change findings
- Minimal report (just nodes, no optional lenses) → works

## Step 5: `output.clj` — `format-diagnose`

Function:
- `format-diagnose [report findings]` → `[string]`

Tests:
- Empty findings → health section + "0 findings"
- Health section shows PC, cycles, ns count
- High finding shows `● HIGH` marker
- Cross-lens finding shows both lenses
- Hidden-conceptual shows score + shared terms
- Cycle finding shows member namespaces
- Summary line shows counts per severity
- Hub finding shows reach
- SDP finding shows Ca and I

## Step 6: `main.clj` — `diagnose` subcommand

Changes:
- `parse-args` recognizes `"diagnose"` subcommand → `{:command :diagnose}`
- New `diagnose-cmd` function: auto-enables lenses, builds report, runs
  diagnose, formats output
- `run` dispatches on `:command`

Tests:
- `parse-args ["diagnose" "."]` → `{:command :diagnose :src-dirs ["."]}`
- `parse-args ["diagnose"]` → `{:command :diagnose :src-dirs ["."]}`
- `parse-args ["diagnose" "--conceptual" "0.30"]` → overrides default
- `parse-args ["src/"]` → no `:command` (backward compat)
- Integration: `diagnose` on fixture-project produces output
- Integration: `diagnose` on gordian itself produces findings
- `--edn` with diagnose → `:findings` key in output
- `--json` with diagnose → findings array in JSON

## Step 7: Docs + PLAN

- Update README with `gordian diagnose` example
- Update PLAN.md status
- Update state.md
