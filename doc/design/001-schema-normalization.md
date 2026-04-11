# Design: Schema Normalization (Option C — flat + `:kind` tag)

## Goal

Align pair record shapes across conceptual and change coupling lenses so
downstream consumers (diagnose, explain, diff, merged rankings) can operate
on pairs generically without knowing which lens produced them.

## Current shapes

```clojure
;; conceptual pair (from conceptual/conceptual-pairs)
{:ns-a             sym
 :ns-b             sym
 :sim              float          ;; cosine similarity
 :structural-edge? bool
 :shared-terms     [str]}

;; change pair (from cc-change/change-coupling-pairs)
{:ns-a             sym
 :ns-b             sym
 :coupling         float          ;; Jaccard index
 :co-changes       int
 :confidence-a     float
 :confidence-b     float
 :structural-edge? bool}
```

## Target shapes

```clojure
;; conceptual pair
{:ns-a             sym
 :ns-b             sym
 :score            float          ;; was :sim — cosine similarity
 :kind             :conceptual
 :structural-edge? bool
 :shared-terms     [str]}         ;; lens-specific evidence

;; change pair
{:ns-a             sym
 :ns-b             sym
 :score            float          ;; was :coupling — Jaccard index
 :kind             :change
 :co-changes       int            ;; lens-specific evidence
 :confidence-a     float          ;; lens-specific evidence
 :confidence-b     float          ;; lens-specific evidence
 :structural-edge? bool}
```

### Common keys (all pair records)

| Key                | Type    | Semantics                                    |
|--------------------|---------|----------------------------------------------|
| `:ns-a`            | symbol  | first namespace (canonical: str(a) < str(b)) |
| `:ns-b`            | symbol  | second namespace                             |
| `:score`           | double  | primary coupling metric, lens-dependent       |
| `:kind`            | keyword | `:conceptual` or `:change`                    |
| `:structural-edge?`| boolean | true if require edge exists between a and b   |

### Evidence keys (lens-specific, always present on their kind)

| Key              | Kind          | Type   | Semantics                      |
|------------------|---------------|--------|--------------------------------|
| `:shared-terms`  | `:conceptual` | [str]  | top N shared TF-IDF terms      |
| `:co-changes`    | `:change`     | int    | raw co-change commit count     |
| `:confidence-a`  | `:change`     | double | P(b changes | a changes)      |
| `:confidence-b`  | `:change`     | double | P(a changes | b changes)      |

## Report map keys

The report map keys also change for consistency:

| Before                    | After                     |
|---------------------------|---------------------------|
| `:conceptual-pairs`       | `:conceptual-pairs`       |
| `:conceptual-threshold`   | `:conceptual-threshold`   |
| `:change-pairs`           | `:change-pairs`           |
| `:change-threshold`       | `:change-threshold`       |

No changes to report-level keys — they're already fine.

## Downstream consumer implications

### `output.clj` — `format-conceptual`, `format-change-coupling`

Both format functions destructure pair maps. Changes:
- `format-conceptual`: read `:score` instead of `:sim`
- `format-change-coupling`: read `:score` instead of `:coupling`

### `json.clj` — `generate`

Currently only serializes `{:src-dirs :propagation-cost :cycles :nodes}`.
Pair data is dropped. When we later add pair data to JSON output, it will
use the normalized keys. No change needed now, but noted for awareness.

### `edn.clj` — `generate`

Passes entire report (minus `:graph`) through `pprint`. Pair data flows
through automatically. The new shapes will appear in EDN output with no
code change — just different keys in the data.

### `main.clj` — `build-report`

Produces the report map. No change — the pair records are produced by
`conceptual/conceptual-pairs` and `cc-change/change-coupling-pairs`; main
just assocs them into the report.

### Future `diagnose.clj`

Can now do:
```clojure
;; merge all pairs, rank by score, filter hidden
(->> (concat conceptual-pairs change-pairs)
     (remove :structural-edge?)
     (sort-by :score >))

;; cross-reference: find ns pairs that appear in both lenses
(let [hidden-conceptual (set (map pair-key (remove :structural-edge? conceptual-pairs)))
      hidden-change     (set (map pair-key (remove :structural-edge? change-pairs)))]
  (set/intersection hidden-conceptual hidden-change))
```

The `:kind` tag enables dispatch for evidence display without `cond` on
optional keys:

```clojure
(case (:kind pair)
  :conceptual (str "shared terms: " (:shared-terms pair))
  :change     (str "co-changes: " (:co-changes pair)))
```

### Future `explain.clj`

Can group all pairs for a namespace:
```clojure
(filter #(or (= ns (:ns-a %)) (= ns (:ns-b %)))
        (concat conceptual-pairs change-pairs))
```

Then display grouped by `:kind`.

### Future `diff`

Match on `[:kind :ns-a :ns-b]` between two snapshots, compare `:score`.

## Files changed

### Source (4 files)

| File                    | Change                                          |
|-------------------------|--------------------------------------------------|
| `src/gordian/conceptual.clj` | `:sim` → `:score`, add `:kind :conceptual`    |
| `src/gordian/cc_change.clj`  | `:coupling` → `:score`, add `:kind :change`   |
| `src/gordian/output.clj`     | destructure `:score` in both format fns        |
| `src/gordian/main.clj`       | no code change (pair records flow through)     |

### Tests (4 files)

| File                              | Change                                       |
|-----------------------------------|----------------------------------------------|
| `test/gordian/conceptual_test.clj`| `:sim` → `:score`, add `:kind` assertions     |
| `test/gordian/cc_change_test.clj` | `:coupling` → `:score`, add `:kind` assertions|
| `test/gordian/output_test.clj`    | fixture data: `:sim` → `:score`, `:coupling` → `:score`, add `:kind` |
| `test/gordian/integration_test.clj` | `:coupling` → `:score`, add `:kind` assertion|

## What does NOT change

- Node records (`{:ns :reach :fan-in :ca :ce :instability :role}`) — untouched
- Report-level keys (`:conceptual-pairs`, `:change-pairs`, etc.) — untouched
- `json.clj` — doesn't serialize pairs yet
- `edn.clj` — passthrough, no code change
- `dot.clj` — no pair data
- `scan.clj`, `close.clj`, `aggregate.clj`, `metrics.clj`, `scc.clj`,
  `classify.clj`, `git.clj` — no pair data

## Step-wise implementation plan

Each step is a single commit. Tests pass at every step.

### Step 1: `conceptual.clj` + `conceptual_test.clj`

Source change in `conceptual.clj`:
- `eval-candidates`: emit `:score` instead of `:sim`, add `:kind :conceptual`
- `conceptual-pairs`: sort by `:score` instead of `:sim`; update docstring

Test changes in `conceptual_test.clj`:
- All `(contains? p :sim)` → `(contains? p :score)`
- All `(map :sim pairs)` → `(map :score pairs)`
- All `(:sim ...)` → `(:score ...)`
- Add `(is (= :conceptual (:kind p)))` assertion in "each entry has required keys"
- Add `(is (contains? p :kind))` assertion

Run: `bb test` — all 87 tests pass.

### Step 2: `cc_change.clj` + `cc_change_test.clj`

Source change in `cc_change.clj`:
- `change-coupling-pairs`: emit `:score` instead of `:coupling`, add `:kind :change`
- Sort by `:score` instead of `:coupling`; update docstring

Test changes in `cc_change_test.clj`:
- All `(contains? p :coupling)` → `(contains? p :score)`
- All `(:coupling ...)` → `(:score ...)`
- All `(map :coupling pairs)` → `(map :score pairs)`
- Add `(is (= :change (:kind p)))` assertion in "each entry has required keys"
- Add `(is (contains? p :kind))` assertion

Run: `bb test` — all 87 tests pass.

### Step 3: `output.clj` + `output_test.clj`

Source change in `output.clj`:
- `format-conceptual`: destructure `:score` instead of `:sim`
- `format-change-coupling`: destructure `:score` instead of `:coupling`

Test changes in `output_test.clj`:
- `sample-pairs` fixture: `:sim` → `:score`, add `:kind :conceptual`
- `change-pairs` fixture: `:coupling` → `:score`, add `:kind :change`

(The tests assert on string output — "0.54", "0.6667" — which remain the
same since the values haven't changed, only the key names in the input maps.)

Run: `bb test` — all 87 tests pass.

### Step 4: `integration_test.clj`

Test changes in `integration_test.clj`:
- `change-coupling-pipeline-test`: `:coupling` → `:score`, add `:kind` assertion

Run: `bb test` — all 87 tests pass.

### Step 5: Docstrings + PLAN.md

- Update any remaining docstrings in `conceptual.clj` and `cc_change.clj`
  that reference the old key names
- Mark item 1 in PLAN.md as done
- Update `mementum/state.md` with session summary
