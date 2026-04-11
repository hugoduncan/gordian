# Design: `gordian explain` / `gordian explain-pair`

## Goal

Drill-down investigation tool. When diagnose surfaces a finding, explain
lets you see everything gordian knows about a specific namespace or pair.

## User stories

### `explain <ns>`

```bash
gordian explain gordian.scan
```

```
gordian explain — gordian.scan

  role: core    Ca=1  Ce=0  I=0.00
  reach: 0.0%   fan-in: 5.9%

DIRECT DEPENDENCIES (0 project, 2 external)
  project: (none)
  external: babashka.fs, edamame.core

DIRECT DEPENDENTS (1)
  gordian.main

CONCEPTUAL COUPLING (3 pairs)
  gordian.conceptual    score=0.30  hidden      shared: term, per, extract
  gordian.git           score=0.18  hidden      shared: file, clj, src
  gordian.main          score=0.16  structural  shared: file, scan, read

CHANGE COUPLING (1 pair)
  gordian.conceptual    score=0.33  hidden      3 co-changes

CYCLES: none
```

### `explain-pair <ns-a> <ns-b>`

```bash
gordian explain-pair gordian.aggregate gordian.close
```

```
gordian explain-pair — gordian.aggregate ↔ gordian.close

STRUCTURAL
  direct edge: no (neither direction)
  shortest path: (none — no transitive path in either direction)

CONCEPTUAL
  score: 0.37
  shared terms: reach, transitive, node
  hidden: yes (no structural edge)

CHANGE COUPLING
  (no data above threshold)

DIAGNOSIS
  ● MEDIUM — hidden conceptual coupling
```

## Design decisions

### 1. Explain always runs from project root

Explain needs the full analysis (graph, pairs, metrics). The namespace
arguments are symbols, not directories. Discovery always uses `.`
(current directory).

```bash
gordian explain gordian.scan                    # from project root
gordian explain gordian.scan --conceptual 0.30  # override threshold
```

### 2. Auto-enable all lenses (like diagnose)

Same rationale as diagnose: when investigating, you want all the data.
Conceptual at 0.15, change coupling enabled by default.

### 3. Namespace validation

If the requested namespace isn't in the project graph, emit a clear error:
```
Error: namespace 'foo.bar' not found in project
Available namespaces: gordian.scan, gordian.main, ...
```

### 4. Shortest path (BFS with parent tracking)

For explain-pair, show the shortest directed dependency path between two
namespaces (if one exists). Try both directions: A→B and B→A.

This is a simple BFS variant that tracks parents. The existing `close.clj`
BFS doesn't track parents, so this is a new function in `explain.clj`.

Only project-internal edges are traversed (external deps are not keys in
the graph, so BFS naturally stops there).

### 5. Pair data from report

Explain doesn't re-compute coupling. It queries the report's
`:conceptual-pairs` and `:change-pairs` vectors, filtering for pairs
that involve the requested namespace(s).

### 6. Diagnosis annotation

For explain-pair, include what diagnose would say about this pair.
Run the relevant finding generators on just this pair's data to show
the diagnosis inline.

### 7. Output format

Human-readable by default. `--edn` and `--json` emit a structured map:

```clojure
;; explain ns
{:ns sym
 :metrics {:reach :fan-in :ca :ce :instability :role}
 :direct-deps {:project [sym] :external [sym]}
 :direct-dependents [sym]
 :conceptual-pairs [pair]
 :change-pairs [pair]
 :cycles [#{sym}]}

;; explain pair
{:ns-a sym :ns-b sym
 :structural {:direct-edge? bool :direction :a→b | :b→a | :none
              :shortest-path [sym] | nil}
 :conceptual pair | nil
 :change pair | nil
 :finding finding | nil}
```

### 8. Subcommand parsing

`explain` and `explain-pair` are subcommands. Their positional args are
namespace symbols, not directories.

```
gordian explain <ns> [options]
gordian explain-pair <ns-a> <ns-b> [options]
```

parse-args detects the subcommand and interprets remaining positional args
accordingly. Project root is always `.` for explain commands.

## Architecture

### New: `explain.clj` (pure)

```clojure
(ns gordian.explain)

(defn shortest-path
  "BFS with parent tracking. Returns [start ... end] or nil.
  Only traverses project-internal edges (keys of graph)."
  [graph from to] → [sym] | nil)

(defn direct-deps
  "Direct dependencies of ns, split into project and external.
  Returns {:project [sym] :external [sym]}."
  [graph ns-sym] → map)

(defn direct-dependents
  "Project namespaces that directly depend on ns-sym."
  [graph ns-sym] → [sym])

(defn ns-pairs
  "Filter pairs that involve ns-sym."
  [pairs ns-sym] → [pair])

(defn explain-ns
  "Everything gordian knows about a single namespace.
  Returns structured map for rendering or serialisation."
  [report ns-sym] → map)

(defn explain-pair-data
  "Everything gordian knows about a pair of namespaces.
  Returns structured map for rendering or serialisation."
  [report ns-a ns-b] → map)
```

### Modified: `output.clj`

```clojure
(defn format-explain-ns [data] → [string])
(defn format-explain-pair [data] → [string])
(defn print-explain-ns [data])
(defn print-explain-pair [data])
```

### Modified: `main.clj`

- `parse-args` recognizes `explain` and `explain-pair` subcommands
- New `explain-cmd` and `explain-pair-cmd` functions
- `run` dispatches on `:command`

## Files changed

| File | Change |
|------|--------|
| new `src/gordian/explain.clj` | Query functions (pure) |
| new `test/gordian/explain_test.clj` | Unit tests |
| modify `src/gordian/output.clj` | `format-explain-ns`, `format-explain-pair` |
| modify `test/gordian/output_test.clj` | Output tests |
| modify `src/gordian/main.clj` | Subcommand routing |
| modify `test/gordian/main_test.clj` | CLI + integration tests |

## Step-wise implementation plan

### Step 1: `explain.clj` — `shortest-path`, `direct-deps`, `direct-dependents`, `ns-pairs`

Building blocks. All pure functions operating on graph and pair data.

Tests:
- `shortest-path` finds direct path (A→B when A requires B)
- `shortest-path` finds multi-hop path (A→B→C returns [A B C])
- `shortest-path` returns nil when no path exists
- `shortest-path` handles cycles without infinite loop
- `shortest-path` from X to X returns nil (self)
- `direct-deps` splits project vs external
- `direct-deps` for ns with no deps → both empty
- `direct-dependents` finds all ns that require given ns
- `direct-dependents` for ns nobody requires → empty
- `ns-pairs` filters pairs involving a given ns
- `ns-pairs` with empty pairs → empty

### Step 2: `explain.clj` — `explain-ns`, `explain-pair-data`

Composite functions that assemble full explanations from report data.

Tests:
- `explain-ns` returns all expected keys
- `explain-ns` includes node metrics
- `explain-ns` includes conceptual pairs for the ns
- `explain-ns` includes change pairs for the ns
- `explain-ns` reports cycles the ns belongs to
- `explain-ns` for unknown ns → `:error` key
- `explain-pair-data` includes structural edge info
- `explain-pair-data` tries both path directions
- `explain-pair-data` includes conceptual data when pair exists
- `explain-pair-data` includes nil conceptual when pair doesn't exist
- `explain-pair-data` includes finding when pair is hidden

### Step 3: `output.clj` — `format-explain-ns`, `format-explain-pair`

Human-readable rendering.

Tests:
- `format-explain-ns` shows role, metrics, deps, dependents
- `format-explain-ns` shows conceptual pairs with terms
- `format-explain-ns` shows "none" for empty sections
- `format-explain-pair` shows structural edge status
- `format-explain-pair` shows shortest path when found
- `format-explain-pair` shows conceptual score + terms
- `format-explain-pair` shows diagnosis when finding present

### Step 4: `main.clj` — `explain` and `explain-pair` subcommands

Parsing, routing, auto-enable lenses.

Tests:
- `parse-args ["explain" "gordian.scan"]` → `{:command :explain :explain-ns gordian.scan}`
- `parse-args ["explain"]` → error (ns required)
- `parse-args ["explain-pair" "a" "b"]` → `{:command :explain-pair ...}`
- `parse-args ["explain-pair" "a"]` → error (two ns required)
- Integration: `explain` on gordian.scan → produces output
- Integration: `explain-pair` on aggregate/close → produces output
- `--edn` with explain → structured map
- Help text mentions explain and explain-pair

### Step 5: Docs

- Update README with explain examples
- Update PLAN.md status
- Update state.md
