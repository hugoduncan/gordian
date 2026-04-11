# Phase A — Schema Envelope: Implementation Plan

## Problem

Machine-readable output (`--edn`, `--json`) has three issues:

1. **No metadata** — no version, no schema marker, no indication of which
   lenses ran, what thresholds were used, or what was excluded. Output is
   not self-describing or reproducible.

2. **JSON drops data** — `json.clj/generate` destructures only 4 keys
   (`src-dirs propagation-cost cycles nodes`). Conceptual pairs, change
   pairs, findings, and health are silently dropped. Diagnose `--json` is
   broken: returns the same output as analyze.

3. **No lens diagnostics** — when change coupling reports 0 pairs, the user
   can't distinguish "lens disabled", "no qualifying pairs", or "too few
   commits". Same for conceptual.

## Design

### Envelope shape

Every command's machine-readable output (EDN and JSON) wraps results in a
standard envelope:

```clojure
{;; ── envelope ──
 :gordian/schema   1                     ;; integer, bumped on breaking changes
 :gordian/command  :analyze              ;; :analyze | :diagnose | :explain | :explain-pair

 :lenses
 {:structural true                       ;; always true
  :conceptual {:enabled true
               :threshold 0.20}          ;; or {:enabled false}
  :change     {:enabled true
               :repo-dir "."
               :since "90 days ago"      ;; or nil
               :candidate-pairs 42       ;; pairs evaluated (before threshold)
               :reported-pairs 3}}       ;; pairs that met threshold

 :src-dirs    ["./src"]
 :excludes    ["user" "scratch"]         ;; or [] when none
 :include-tests false

 ;; ── command-specific payload ──
 ;; (varies by command — see below)
 }
```

### Per-command payload keys

**analyze:**
```
:propagation-cost  :cycles  :nodes
:conceptual-pairs  :conceptual-threshold    ;; when lens enabled
:change-pairs      :change-threshold        ;; when lens enabled
```

**diagnose:** (same as analyze plus)
```
:health    :findings
```

**explain:**
```
:ns  :metrics  :direct-deps  :direct-dependents
:conceptual-pairs  :change-pairs  :cycles
```

**explain-pair:**
```
:ns-a  :ns-b  :structural  :conceptual  :change  :finding
```

### Where the envelope is assembled

**Not in json.clj or edn.clj.** Those are serializers. The envelope is
assembled in `main.clj` at each command's output boundary — the same place
that currently calls `report-json/generate` or `report-edn/generate`.

A new pure function `envelope` in main.clj (or a small `gordian.envelope`
module) takes opts + report → wrapped map. Each command function calls it
before serialization.

### Candidate-pairs / reported-pairs tracking

To report how many pairs were *evaluated* vs *reported*, we need the
pre-threshold counts. Two options:

**Option A — return counts from coupling functions.**
`conceptual-pairs` and `change-coupling-pairs` already filter by threshold.
Wrap return to include metadata:
```clojure
{:pairs [...] :candidate-count 42}
```
`build-report` stashes `{:conceptual-candidate-pairs 42}` etc. on the report.

**Option B — count in build-report before/after.**
Less invasive. The candidate count is the number of *non-zero similarity*
pairs, which the inverted index already computes. But we don't have it
after the fact.

**Decision: Option A.** Minimal change — `conceptual-pairs` already computes
candidates via the inverted index. Return `{:pairs [...] :candidate-count n}`
instead of bare vector. Same for `change-coupling-pairs`. Two call sites
each, easy to update.

### JSON fix

`json.clj/generate` currently cherry-picks 4 keys. Two options:

**Option A — generic serialization.** Walk the whole map and coerce types
(symbols→strings, keywords→strings, sets→sorted-vecs). No more field list.

**Option B — exhaustive field list.** Add every key explicitly.

**Decision: Option A.** The envelope means the output shape varies by
command. A generic walker is simpler and won't drift. Use a recursive
`serialize` function that handles all Clojure types → JSON-friendly types.

`edn.clj/generate` already works generically (dissoc :graph, pprint). Just
needs to also dissoc :graph from nested data (currently fine — :graph only
appears at top level).

### Human-readable output

The envelope metadata is *not* added to human-readable (default) or markdown
output in Phase A. The terminal format already shows src-dirs and thresholds
inline. Adding lens metadata to human output is a future enhancement (or
could be added to markdown as a YAML frontmatter block).

### Version source

Add `:version "0.2.0"` to a `gordian.version` namespace (single def, or
inline in bb.edn metadata). Not worth a build system for a bb script —
just a def.

Actually: simpler. A `def` in `main.clj`:
```clojure
(def version "0.2.0")
```
Referenced in the envelope. Bumped manually on releases.

---

## Implementation Steps

### Step 1: Fix JSON serialization (bug fix)

Replace cherry-picked `json.clj/generate` with generic walker.

**Files:** `json.clj`, `json-test`
**Change:** `generate` takes any map, recursively coerces:
- symbol → string
- keyword → string (for values) or keyword-name (for map keys)
- set → sorted vector of serialized elements
- map → recurse
- vector/list → recurse
- numbers, strings, booleans, nil → pass through
- Dissoc `:graph` (internal)

**Tests:**
- Existing json tests still pass (same output for analyze)
- New test: diagnose report with `:findings` and `:health` serializes correctly
- New test: conceptual-pairs and change-pairs appear in JSON output

### Step 2: Candidate-pair counts from coupling functions

**Files:** `conceptual.clj`, `cc_change.clj`, `main.clj`
**Change in conceptual.clj:**
- `conceptual-pairs` returns `{:pairs [...] :candidate-count n}` instead of vector
- candidate-count = number of pairs evaluated by inverted index (count of candidate set)

**Change in cc_change.clj:**
- `change-coupling-pairs` returns `{:pairs [...] :candidate-count n}`
- candidate-count = number of pairs with ≥1 co-change (before Jaccard threshold)

**Change in main.clj:**
- `build-report` destructures the new return shape
- Stashes `:conceptual-candidate-count` and `:change-candidate-count` on report

**Tests:**
- Update conceptual-test: check return is map with :pairs and :candidate-count
- Update cc-change-test: same
- Update any integration tests that check report keys

### Step 3: Envelope assembly

**Files:** new `envelope.clj` (or inline in main.clj — prefer separate, small, pure, testable)
**Function:** `(wrap-envelope opts report command)` → envelope map

```clojure
(defn wrap-envelope
  "Wrap a report map in the standard gordian envelope.
  opts    — resolved CLI options
  report  — output of build-report (or explain/explain-pair data)
  command — :analyze | :diagnose | :explain | :explain-pair"
  [opts report command]
  (let [conceptual-on? (some? (:conceptual-threshold report))
        change-on?     (some? (:change-threshold report))]
    (merge
     {:gordian/schema  1
      :gordian/command command
      :lenses
      {:structural true
       :conceptual (if conceptual-on?
                     {:enabled true
                      :threshold (:conceptual-threshold report)
                      :candidate-pairs (or (:conceptual-candidate-count report) 0)
                      :reported-pairs  (count (:conceptual-pairs report))}
                     {:enabled false})
       :change     (if change-on?
                     (cond-> {:enabled true
                              :candidate-pairs (or (:change-candidate-count report) 0)
                              :reported-pairs  (count (:change-pairs report))}
                       (:change-since opts) (assoc :since (:change-since opts))
                       (:change opts)       (assoc :repo-dir (if (string? (:change opts))
                                                               (:change opts) ".")))
                     {:enabled false})}
      :src-dirs       (or (:src-dirs opts) (:src-dirs report))
      :excludes       (or (:exclude opts) [])
      :include-tests  (boolean (:include-tests opts))}
     ;; strip internal keys, keep payload
     (dissoc report :graph :src-dirs
             :conceptual-threshold :change-threshold
             :conceptual-candidate-count :change-candidate-count))))
```

For explain/explain-pair: the envelope is simpler (no report-level lens data
embedded in the explain result). The `opts` carry the lens config; the
explain data is the payload.

**Tests:**
- `envelope-test`: given opts + report → correct envelope keys
- Envelope includes lens metadata
- Disabled lenses show `{:enabled false}`
- candidate/reported counts correct

### Step 4: Wire envelope into command outputs

**Files:** `main.clj`
**Change:** Each of `analyze`, `diagnose-cmd`, `explain-cmd`, `explain-pair-cmd`:
- Before `(report-json/generate ...)` or `(report-edn/generate ...)`, call
  `(envelope/wrap-envelope opts data command-kw)`
- Human-readable and markdown paths unchanged (no envelope)

Concretely:

```clojure
;; analyze, json branch:
json (println (report-json/generate
                (envelope/wrap-envelope opts report :analyze)))
;; analyze, edn branch:
edn  (print (report-edn/generate
               (envelope/wrap-envelope opts report :analyze)))
```

Same pattern for diagnose (`:diagnose`), explain (`:explain`),
explain-pair (`:explain-pair`).

For diagnose: the report already has `:findings` and `:health` assoc'd
before serialization. The envelope wraps that whole thing.

**Tests:**
- Integration test: `bb gordian --edn` output has `:gordian/schema` key
- Integration test: `bb gordian diagnose --json` has `:findings` key (fixes bug)
- Integration test: `bb gordian --edn --conceptual 0.20` has lens metadata

### Step 5: Version def

**Files:** `main.clj` (add `(def version "0.2.0")`)
**Change:** `envelope.clj` or `wrap-envelope` includes `:gordian/version version`

No bb.edn change needed. Version is for output metadata only.

### Step 6: Schema documentation

**Files:** `doc/schema.md` (new)
**Content:** Document the envelope shape and per-command payload keys.
Short, reference-oriented. Example outputs for each command.

Also add a brief note to README pointing to `doc/schema.md`.

---

## Step Dependency Graph

```
Step 1 (fix JSON) ──────────────┐
Step 2 (candidate counts) ──────┼──→ Step 3 (envelope) ──→ Step 4 (wire) ──→ Step 6 (docs)
                                │                                    ↑
                                │         Step 5 (version def) ──────┘
                                │
```

Steps 1 and 2 are independent of each other.
Step 3 depends on 2 (needs candidate counts in report).
Step 4 depends on 1 + 3 (needs working JSON + envelope).
Step 5 is trivial, can go anywhere before 4.
Step 6 is last.

---

## Test Strategy

| Step | New Tests | Modified Tests |
|------|-----------|----------------|
| 1 | json-test: diagnose+explain maps serialize | json-test: existing analyze cases |
| 2 | conceptual-test: candidate-count | cc-change-test: candidate-count |
| 3 | envelope-test: full envelope shape | — |
| 4 | integration-test: edn/json envelope keys | integration-test: diagnose json has findings |
| 5 | — | — |
| 6 | — | — |

Estimated: ~15-20 new assertions across 4 test files.

---

## What This Does NOT Do

- Does not change human-readable or markdown output format
- Does not add `gordian schema` subcommand (low priority, future)
- Does not version the schema in a machine-checkable way (semver on schema
  integer is sufficient for now)
- Does not change any coupling algorithm or finding logic
- Does not add YAML frontmatter to markdown output

---

## Risk Assessment

**Low risk.** Changes are additive (envelope wraps existing data) except:
- JSON fix is a behavioral change — output gains keys it was dropping.
  This is a bug fix, not a breaking change (nobody could have been relying
  on missing data).
- Coupling functions return `{:pairs [...] :candidate-count n}` instead of
  bare vector. Two call sites in `main.clj`, plus tests. Contained change.

**Migration:** None. Schema version 1 is the first documented schema.
Previous output had no version marker, so consumers already had to be
tolerant.
