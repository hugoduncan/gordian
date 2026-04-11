# Design: `gordian diagnose` — ranked findings

## Goal

Synthesise all available metrics into a ranked, human-readable findings
report. Turn gordian from "here are your numbers" into "here's what matters
and why."

## User story

```bash
gordian diagnose .
```

Outputs something like:

```
gordian diagnose — 5 findings
src: ./src  (auto-discovered)

HEALTH
  propagation cost: 5.5% (healthy, <10%)
  cycles: none

● HIGH  gordian.conceptual ↔ gordian.scan
  hidden coupling in 2 lenses:
    conceptual score=0.29 — shared terms: term, per, extract
    change     score=0.33 — co-changed in 3 commits
  no structural edge → consider explicit dependency or shared abstraction

● MEDIUM  gordian.aggregate ↔ gordian.close
  hidden conceptual coupling — score=0.38
    shared terms: reach, transitive, node
  no structural edge → graph-traversal siblings, may warrant shared module

● MEDIUM  gordian.main — high-reach hub
  reach=94.1%, Ce=16, I=1.00 (peripheral)
  wires 16 of 17 modules — expected for entry point, but monitor growth

● LOW  gordian.edn ↔ gordian.json
  hidden conceptual coupling — score=0.15
    shared terms: pretty, report, generate
  low score, similar output serialisers — likely benign

4 findings (1 high, 2 medium, 1 low)
```

## Design decisions

### 1. `diagnose` is a subcommand, not a flag

`gordian diagnose .` — not `gordian . --summary`.

Rationale: diagnose produces a fundamentally different output shape (findings,
not a metrics table). Mixing it with the table via a flag creates confusing
output. Diagnose is a different *mode*, not an *option*.

The subcommand:
- Auto-enables all available lenses with sensible defaults
- Shares all existing flags (--json, --edn, --exclude, etc.)
- Produces findings-only output (no raw table)

### 2. Auto-enable all lenses

When you ask for diagnosis, you want all the data. `diagnose` automatically
enables:
- Conceptual coupling at threshold 0.15 (low bar — we want candidates)
- Change coupling from project root (default `.`)

These defaults can be overridden:
```bash
gordian diagnose . --conceptual 0.30          # stricter conceptual
gordian diagnose . --change-since "90 days"   # scope change window
```

Without auto-enabling, the user has to remember
`gordian . --conceptual 0.20 --change --change-since "90 days ago"` —
exactly the friction that was reported.

### 3. Finding categories

| Category | Source | What it detects |
|----------|--------|-----------------|
| `propagation-cost` | structural | Overall coupling health indicator |
| `cycle` | SCC | Each cycle is a separate finding |
| `cross-lens-hidden` | conceptual + change | Same pair hidden in both lenses — strongest signal |
| `hidden-conceptual` | conceptual | Hidden in conceptual only |
| `hidden-change` | change | Hidden in change only |
| `sdp-violation` | structural | High Ca + high instability (fragile foundation) |
| `god-module` | structural | Shared role with extreme reach and fan-in |
| `hub` | structural | Very high reach (informational) |

### 4. Severity assignment

| Severity | Criteria |
|----------|----------|
| **high** | Cycles (always). Cross-lens hidden coupling. Propagation cost > 0.30. |
| **medium** | Single-lens hidden coupling (score ≥ 0.20). SDP violations. God modules. |
| **low** | Single-lens hidden coupling (score < 0.20). Hubs (informational). Propagation cost ≤ 0.10 health note. |

The thresholds (0.30 for PC, 0.20 for score) are heuristics derived from
literature norms and gordian's own self-analysis. They're hardcoded initially
but could be configurable later.

### 5. Finding data model

```clojure
{:severity  :high | :medium | :low
 :category  :cycle | :cross-lens-hidden | :hidden-conceptual | :hidden-change
            | :sdp-violation | :god-module | :hub | :propagation-cost
 :subject   ;; varies by category:
            ;;   pair → {:ns-a sym :ns-b sym}
            ;;   node → {:ns sym}
            ;;   cycle → {:members #{sym}}
            ;;   health → nil
 :reason    string   ;; one-line human explanation
 :evidence  map}     ;; category-specific supporting data
```

Evidence maps by category:

```clojure
;; :propagation-cost
{:value double :health :healthy | :moderate | :concerning}

;; :cycle
{:members #{sym} :size int}

;; :cross-lens-hidden
{:conceptual-score double :shared-terms [str]
 :change-score double :co-changes int
 :confidence-a double :confidence-b double}

;; :hidden-conceptual
{:score double :shared-terms [str]}

;; :hidden-change
{:score double :co-changes int :confidence-a double :confidence-b double}

;; :sdp-violation
{:ca int :ce int :instability double :role keyword}

;; :god-module
{:reach double :fan-in double :ca int :ce int :role keyword}

;; :hub
{:reach double :ce int :instability double :role keyword}
```

### 6. Cross-lens pair matching

The key algorithmic piece: matching pairs across lenses.

```clojure
(defn pair-key [p] #{(:ns-a p) (:ns-b p)})
```

Index hidden pairs from each lens, then intersect:

```clojure
(let [hidden-c (into {} (map (juxt pair-key identity))
                       (remove :structural-edge? conceptual-pairs))
      hidden-x (into {} (map (juxt pair-key identity))
                       (remove :structural-edge? change-pairs))
      cross-keys (set/intersection (set (keys hidden-c)) (set (keys hidden-x)))]
  ;; cross-keys → :cross-lens-hidden findings
  ;; (keys hidden-c) minus cross-keys → :hidden-conceptual findings
  ;; (keys hidden-x) minus cross-keys → :hidden-change findings
  )
```

### 7. Node-level findings

**SDP violation:** A namespace with high Ca (many things depend on it) but
high instability (it also depends on many things). It *should* be stable but
isn't. Heuristic: `Ca ≥ 2` and `I > 0.5`.

**God module:** A namespace classified as `:shared` (high reach AND high
fan-in) with reach or fan-in > 2× the mean. These are the "everything flows
through here" bottlenecks.

**Hub:** Any namespace with reach > 3× mean. Informational — entry points
naturally have high reach.

### 8. Sorting

Findings are sorted by: severity (high → medium → low), then by score
within severity (highest coupling/worst metric first).

Each finding gets a sort-key:
```clojure
(defn severity-rank [s] (case s :high 0 :medium 1 :low 2))
(defn sort-key [f]
  [(severity-rank (:severity f))
   (- (or (:score (:evidence f))
           (:value (:evidence f))
           0))])
```

### 9. Health section

Always present at the top. Not a "finding" per se — a summary:
- Propagation cost with health label (healthy/moderate/concerning)
- Cycle count
- Namespace count
- Number of findings by severity

### 10. Output format

Human-readable (default):
```
gordian diagnose — 5 findings
src: ./src

HEALTH
  propagation cost: 5.5% (healthy)
  cycles: none
  namespaces: 17

● HIGH  gordian.conceptual ↔ gordian.scan
  hidden coupling in 2 lenses:
    conceptual score=0.29 — shared terms: term, per, extract
    change     score=0.33 — co-changed in 3 commits
  → no structural edge

● MEDIUM  gordian.aggregate ↔ gordian.close
  hidden conceptual coupling — score=0.38
    shared terms: reach, transitive, node
  → no structural edge

4 findings (1 high, 2 medium, 1 low)
```

EDN/JSON: findings array with the full data model, for machine consumption.
The existing `--edn` and `--json` flags work — they emit the report map
which now includes `:findings` when diagnose mode is active.

## Architecture

### New: `diagnose.clj` (pure)

All logic for generating findings from a report map. No IO, no formatting.

```clojure
(ns gordian.diagnose)

(defn pair-key
  "Canonical pair identity — set of two namespace symbols."
  [pair] → #{sym sym})

(defn health
  "Assess overall project health from report metrics.
  Returns {:propagation-cost double :health keyword :cycles int :ns-count int}."
  [report] → map)

(defn find-cross-lens-hidden
  "Pairs hidden in both conceptual and change lenses."
  [conceptual-pairs change-pairs] → [finding])

(defn find-hidden-conceptual
  "Pairs hidden in conceptual lens only (not in change or already cross-lens)."
  [conceptual-pairs cross-keys] → [finding])

(defn find-hidden-change
  "Pairs hidden in change lens only."
  [change-pairs cross-keys] → [finding])

(defn find-sdp-violations
  "Namespaces with high Ca but high instability."
  [nodes] → [finding])

(defn find-god-modules
  "Shared-role namespaces with extreme reach+fan-in."
  [nodes] → [finding])

(defn find-hubs
  "Very high reach namespaces (informational)."
  [nodes] → [finding])

(defn find-cycles
  "Convert SCC cycle sets into findings."
  [cycles] → [finding])

(defn diagnose
  "Generate all findings from a complete report map.
  Returns findings sorted by severity then score."
  [report] → [finding])
```

### Modified: `output.clj`

New function `format-diagnose`:

```clojure
(defn format-diagnose
  "Format findings as human-readable lines.
  report — full report map (for health section context).
  findings — sorted vec of finding maps."
  [report findings] → [string])
```

### Modified: `main.clj`

- `diagnose` recognized as subcommand in `parse-args` (alongside `analyze`)
- New `diagnose-cmd` function wired in `run`
- Auto-enables conceptual (0.15) and change (`.`) when not explicitly set
- Calls `build-report` then `diagnose/diagnose` then formats

## Files changed

| File | Change |
|------|--------|
| new `src/gordian/diagnose.clj` | Finding generation, pure |
| new `test/gordian/diagnose_test.clj` | Unit tests per finding category |
| modify `src/gordian/output.clj` | `format-diagnose` |
| modify `test/gordian/output_test.clj` | Tests for `format-diagnose` |
| modify `src/gordian/main.clj` | `diagnose` subcommand routing |
| modify `test/gordian/main_test.clj` | `diagnose` CLI tests |

## Step-wise implementation plan

### Step 1: `diagnose.clj` — health + cycle findings

Implement `health`, `find-cycles`, `pair-key`. These are the simplest
finding generators — structural only, no cross-lens logic.

**Tests:**
- `health` with low PC → `:healthy`
- `health` with high PC → `:concerning`
- `health` reports correct cycle count and ns count
- `find-cycles` with no cycles → empty
- `find-cycles` with one cycle → one `:high` finding with members
- `find-cycles` with multiple cycles → multiple findings
- `pair-key` produces canonical set

### Step 2: `diagnose.clj` — node-level findings

Implement `find-sdp-violations`, `find-god-modules`, `find-hubs`.

**Tests:**
- SDP: node with Ca=3, I=0.8 → medium finding
- SDP: node with Ca=0 → not a violation (nothing depends on it)
- SDP: node with I=0.0 → not a violation (maximally stable)
- God module: shared node with reach and fan-in > 2× mean → finding
- God module: shared node with normal values → not flagged
- Hub: node with reach > 3× mean → low finding
- Hub: node with normal reach → not flagged

### Step 3: `diagnose.clj` — pair findings + cross-lens

Implement `find-cross-lens-hidden`, `find-hidden-conceptual`,
`find-hidden-change`.

**Tests:**
- Pair hidden in both lenses → one `:high` cross-lens finding
- Pair hidden in conceptual only → `:medium` or `:low` depending on score
- Pair hidden in change only → `:medium`
- Structural-edge pairs → not flagged as hidden
- Empty pair lists → empty findings
- Score threshold: score ≥ 0.20 → medium, score < 0.20 → low

### Step 4: `diagnose.clj` — `diagnose` function + sorting

Wire all finders into `diagnose`. Sort by severity then score.

**Tests:**
- Full report with all data → findings sorted correctly
- Report with no pairs → only structural findings
- Report with no cycles → no cycle findings
- Finding count matches sum of individual finders
- Severity ordering: all :high before :medium before :low

### Step 5: `output.clj` — `format-diagnose`

Render findings as human-readable text. Health section at top, then
findings grouped by severity, then summary count.

**Tests:**
- Empty findings → health section only + "0 findings"
- Mixed severity → correct grouping and symbols (● HIGH, etc.)
- Cross-lens finding → shows both lenses with scores
- Hidden pair → shows "no structural edge"
- Cycle finding → shows member namespaces
- Hub finding → shows reach and role

### Step 6: `main.clj` — `diagnose` subcommand

Wire `diagnose` as subcommand. Auto-enable lenses. Route to
`format-diagnose` or EDN/JSON output.

**Tests:**
- `parse-args ["diagnose" "."]` → recognized as diagnose mode
- `parse-args ["diagnose"]` → defaults to `"."`
- `gordian diagnose resources/fixture-polylith` → produces findings
- `--json` with diagnose → findings in JSON output
- `--edn` with diagnose → findings in EDN output
- Auto-enables conceptual and change when not specified
- Explicit `--conceptual 0.30` overrides default 0.15

### Step 7: Integration + docs

End-to-end test on gordian itself. Update README, PLAN.md, state.md.
