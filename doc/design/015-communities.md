# 015 — Communities

## Problem

Current Gordian clustering groups findings. That helps organize symptoms, but
it is not the same as discovering architectural communities.

Finding clusters answer:
- which findings belong together?

Community detection should answer:
- which namespaces form a latent subsystem?
- which namespaces are tightly connected even if no finding currently mentions them?
- which namespaces act as bridges between otherwise separate groups?
- where are the natural modular boundaries in the codebase?

This is the difference between clustering reported issues and discovering the
underlying structure of the system.

## Goal

Add a new command:

```bash
gordian communities
```

with configurable lens selection:

```bash
gordian communities --lens structural
gordian communities --lens conceptual --threshold 0.20
gordian communities --lens change --threshold 0.30
gordian communities --lens combined --threshold 0.75
```

The command should reveal:
- architecture communities as groups of namespaces
- their internal cohesion
- their external boundary weight
- dominant vocabulary
- likely bridge namespaces

---

## Scope

### In scope
- new `communities` subcommand
- structural / conceptual / change / combined graph construction
- thresholded community graph
- connected-components community detection for v1
- internal weight, boundary weight, density
- dominant terms derived from conceptual shared terms
- bridge namespace heuristic
- text, markdown, EDN, and JSON output

### Out of scope
- Louvain / modularity optimization
- label propagation
- dynamic threshold selection
- community-aware gate checks
- historical community diffs

---

## CLI

### New subcommand

```bash
gordian communities [dirs...] [options]
```

Examples:

```bash
gordian communities
gordian communities . --lens structural
gordian communities . --lens conceptual --threshold 0.20
gordian communities . --lens combined
gordian communities . --markdown
```

### Flags

```text
--lens <mode>        structural | conceptual | change | combined
--threshold <float>  minimum edge weight for inclusion
--json               JSON output
--edn                EDN output
--markdown           Markdown output
```

### Defaults
- `--lens combined`
- threshold defaults by lens:
  - structural: all edges included (threshold ignored)
  - conceptual: `0.15`
  - change: `0.30`
  - combined: `0.75`

---

## Design choice: algorithm

### Recommendation for v1
Use **connected components on a thresholded undirected weighted graph**.

Why:
- simple
- deterministic
- easy to explain
- easy to test
- sufficient for first implementation

This is not full modularity optimization, but it provides strong architectural
signal without adding algorithmic complexity or instability.

### Deferred algorithms
Potential future extensions:
- label propagation
- Louvain / modularity maximization
- edge-betweenness clustering

For v1, deterministic components are the right tradeoff.

---

## Graph construction

Community detection operates on an **undirected weighted graph**.

### Structural lens
Input:
- project require graph (directed)

Transform:
- if `a -> b` or `b -> a`, create undirected edge `{a,b}` with weight `1.0`
- direct structural adjacency dominates this lens

### Conceptual lens
Input:
- conceptual pairs

Transform:
- include pairs whose score ≥ threshold
- undirected edge weight = conceptual score

### Change lens
Input:
- change pairs

Transform:
- include pairs whose score ≥ threshold
- undirected edge weight = change score

### Combined lens
Input:
- structural graph
- conceptual pairs
- change pairs

Transform:
- merge the three contributions into one undirected weighted graph

---

## Combined weight formula

For unordered pair `{a,b}`:

```text
w(a,b) = structural + conceptual + change
```

Where:

### Structural contribution

```text
1.0 if direct structural edge exists, else 0.0
```

### Conceptual contribution

```text
0.7 * conceptual-score
```

### Change contribution

```text
0.7 * change-score
```

Example:
- structural edge exists
- conceptual score = 0.30
- change score = 0.40

Then:

```text
1.0 + 0.21 + 0.28 = 1.49
```

### Why this formula
- structural edges remain first-class
- conceptual and change reinforce or reveal hidden communities
- weights stay additive and interpretable

---

## Edge representation

Use a canonical undirected shape:

```clojure
{:a sym
 :b sym
 :weight double
 :sources #{:structural :conceptual :change}}
```

Invariant:

```clojure
(str a) < (str b)
```

This makes edge identity stable and deduplicable.

### Why include `:sources`
It explains why the edge exists.
Examples:
- `#{:structural}`
- `#{:conceptual}`
- `#{:structural :conceptual}`
- `#{:conceptual :change}`

This is important for interpretability and future UI work.

---

## Thresholding

Once the weighted undirected graph is assembled:
- keep only edges with `weight >= threshold`
- build connected components over the remaining graph

### Default thresholds
- structural: all edges
- conceptual: `0.15`
- change: `0.30`
- combined: `0.75`

### Why `0.75` for combined
- structural-only edges (`1.0`) survive
- strong hidden multi-lens edges can survive
- weak conceptual-only naming noise is filtered out

---

## Output shape

```clojure
{:gordian/command :communities
 :lens            :combined
 :threshold       0.75
 :communities
 [{:id                1
   :members           [sym ...]
   :size              8
   :edge-count        11
   :density           0.39
   :internal-weight   7.3
   :boundary-weight   2.1
   :dominant-terms    ["state" "event" "transition"]
   :bridge-namespaces [foo.core bar.facade]
   :edges             [{:a x :b y :weight 1.2 :sources #{:structural :conceptual}} ...]}
  ...]
 :summary
 {:community-count 4
  :largest-size 8
  :singleton-count 2}}
```

---

## Community fields

### `:members`
Sorted vector of namespace symbols.

### `:size`
Count of members.

### `:edge-count`
Number of threshold-passing internal edges.

### `:density`
Unweighted density:

```text
edge-count / possible-edge-count
```

for an undirected graph.

Reason for unweighted density in v1:
- easier to explain
- less sensitive to calibration details

### `:internal-weight`
Sum of weights of internal edges within the community.

### `:boundary-weight`
Sum of weights of edges from community members to nodes outside the community.

This gives a cohesion-vs-leakage view.

### `:dominant-terms`
Representative terms for the community.

### `:bridge-namespaces`
Top members with strongest external connectivity to other communities.

### `:edges`
Internal defining edges of the community.
Useful in EDN/JSON, usually omitted or summarized in human output.

---

## Dominant terms

### Goal
Provide a semantic summary of a community.

Example:

```clojure
:dominant-terms ["commit" "change" "coupling"]
```

### V1 source
Approximate from conceptual shared terms:
- collect `:shared-terms` from conceptual pairs where both endpoints are in the community
- frequency rank
- top 3–5 terms

### If no conceptual data exists
Return `[]`

This keeps v1 simple while still giving meaningful summaries when conceptual
analysis is enabled.

---

## Bridge namespace heuristic

### Goal
Identify members connecting the community to the rest of the graph.

### V1 heuristic
For each member in a community:
- sum weights of edges from that member to nodes outside the community
- sort descending
- return top 3 as `:bridge-namespaces`

### Why this works
It surfaces:
- façades
- shared infrastructure nodes
- integration seams
- likely extraction boundaries

This is not formal centrality, but it is useful and interpretable.

---

## New pure module

### `communities.clj`

Public functions:

```clojure
(canonical-edge a b) -> {:a sym :b sym}

(undirected-structural-edges graph) -> [edge]
(conceptual-edges pairs threshold) -> [edge]
(change-edges pairs threshold) -> [edge]
(combined-edges report opts) -> [edge]

(threshold-edges edges threshold) -> [edge]
(adjacency-map edges) -> {sym #{sym}}
(connected-components adjacency all-nodes) -> [#{sym}]

(community-edge-count members edges) -> int
(community-density members edges) -> double
(internal-boundary-weight members edges) -> {:internal-weight x :boundary-weight y}
(bridge-namespaces members edges) -> [sym]
(dominant-terms report members) -> [string]

(community-report report opts) -> full-output-map
```

---

## Function behavior

### `undirected-structural-edges`
Convert directed structural graph to unique undirected edges with weight `1.0`
and source `#{:structural}`.

### `conceptual-edges`
Convert conceptual pairs above threshold into weighted undirected edges with
source `#{:conceptual}`.

### `change-edges`
Convert change pairs above threshold into weighted undirected edges with source
`#{:change}`.

### `combined-edges`
Merge structural, conceptual, and change contributions by pair key, summing
weights and unioning source sets.

### `threshold-edges`
Filter edges by weight.

### `adjacency-map`
Build undirected adjacency from thresholded edges.

### `connected-components`
Return community member sets. Include singleton nodes with no edges.

### `community-density`
For community size `n` and internal edge count `m`:

```text
m / (n*(n-1)/2)
```

Return `0.0` when `n < 2`.

### `internal-boundary-weight`
For a member set:
- internal weight = sum of weights where both endpoints are members
- boundary weight = sum of weights where exactly one endpoint is a member

### `dominant-terms`
Top terms from internal conceptual pair `:shared-terms`.

### `bridge-namespaces`
Top members by external edge-weight sum.

### `community-report`
Assemble final machine-readable output.

---

## Sorting communities

Recommended sort order:
1. size descending
2. internal-weight descending
3. lexicographic first member as tie-break

This makes output deterministic and stable.

---

## Rendering

## Text output

Example:

```text
gordian communities — combined (threshold 0.75)

SUMMARY
  communities: 4
  largest: 8
  singletons: 2

[1] 8 namespaces
  members: gordian.aggregate, gordian.close, gordian.metrics, ...
  density: 41.0%
  internal weight: 7.3
  boundary weight: 2.1
  dominant terms: reach, transitive, node
  bridges: gordian.diagnose, gordian.family
```

## Markdown output

Example:

```markdown
# gordian communities — combined

## Summary

| Metric | Value |
|--------|-------|
| Communities | 4 |
| Largest | 8 |
| Singletons | 2 |

## Community 1

- **Size:** 8
- **Density:** 41.0%
- **Internal weight:** 7.3
- **Boundary weight:** 2.1
- **Dominant terms:** `reach`, `transitive`, `node`
- **Bridges:** `gordian.diagnose`, `gordian.family`
```

---

## Relationship to existing features

### Findings clusters vs communities
- finding clusters = clustered symptoms
- communities = discovered latent structure

Both are useful and complementary.

### Subgraph views vs communities
- subgraph = user-specified top-down slice
- communities = discovered bottom-up slice

Potential future workflow:
- discover community via `communities`
- inspect in detail via `subgraph`

---

## Edge cases

### No conceptual/change data
Structural lens still works.

### Threshold too high
All nodes may become singletons. This is valid and should not error.

### Empty graph
Return empty community vector and zero summary counts.

### Very large communities
Still valid. Output should remain deterministic.

---

## Testing strategy

### `communities_test.clj`
- structural edge conversion
- conceptual/change edge conversion
- combined edge merge semantics
- thresholding
- connected components
- singleton preservation
- density calculation
- internal/boundary weight calculation
- bridge namespace heuristic
- dominant term extraction
- full `community-report`

### `main_test.clj`
- parse `communities` args
- default lens/threshold behavior
- text/markdown/edn/json smoke tests

### `output_test.clj`
- text renderer includes summary and communities
- markdown renderer includes tables and member sections
- dominant terms and bridges rendered

---

## Schema impact

Additive only.
No schema bump required.

New command:

```clojure
:gordian/command :communities
```

---

## Implementation order

1. `communities.clj` — edge builders + connected components
2. `communities.clj` — per-community metrics + final report
3. `main.clj` — CLI parsing and command wiring
4. `output.clj` — text and markdown rendering
5. tests + docs

---

## Success criteria

We are done when:
- `gordian communities` works end-to-end
- all four lens modes work
- communities are deterministic and interpretable
- dominant terms and bridges help explain them
- text/markdown/EDN/JSON all work
- full test suite remains green
