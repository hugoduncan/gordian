# 014 — Subgraph Views

## Problem

Gordian is strong at whole-project analysis, pair investigation, diffing, and
triage. But most day-to-day architectural work happens within a subsystem, not
across the entire codebase.

Typical user questions are local:
- what are the namespaces under `psi.agent-session.*`?
- how entangled is that subsystem internally?
- what crosses its boundary?
- what hidden conceptual or change couplings exist inside it?
- which findings touching this family are worth acting on first?

Today users must approximate this manually by filtering many reports and
mentally reconstructing the relevant graph. That is high effort and low
signal.

## Goal

Add a first-class subsystem/family view:

```bash
gordian subgraph <prefix>
```

and enhance:

```bash
gordian explain <target>
```

so that `explain` falls back to a subgraph view when `<target>` is not an
exact namespace but does match a namespace family prefix.

This should make Gordian useful at the level teams actually work: one family,
one subsystem, one ownership boundary.

---

## Scope

### In scope
- new `subgraph` subcommand
- namespace family prefix matching
- induced internal graph for matching members
- boundary edge analysis (incoming/outgoing)
- internal structural metrics (node count, edge count, density, PC, cycles)
- internal and touching conceptual/change pairs
- touching findings and local clusters
- `explain` fallback to subgraph mode when no exact namespace exists
- text, markdown, EDN, and JSON output

### Out of scope
- git-ref-aware subgraph comparisons
- explicit boundary pair category in v1
- local per-node metrics alongside global node metrics
- subgraph-specific health heuristics in v1
- fuzzy prefix suggestion/search UX

---

## CLI

### New command

```bash
gordian subgraph <prefix> [options]
```

Examples:

```bash
gordian subgraph psi.agent-session
gordian subgraph gordian --markdown
gordian subgraph gordian --json
gordian subgraph psi.agent-session --rank actionability
```

### Explain fallback

```bash
gordian explain <target>
```

Resolution order:
1. if `<target>` matches an exact namespace, use existing namespace explain
2. else if `<target>` matches one or more namespaces by family prefix, return
   subgraph view
3. else return not-found error

This preserves stable exact-namespace behavior while making exploratory use
ergonomic.

---

## Prefix matching semantics

A prefix `psi.agent-session` matches:
- `psi.agent-session`
- `psi.agent-session.core`
- `psi.agent-session.db.query`

It does not match:
- `psi.agent`
- `psi.agentx.session`

### Predicate

```clojure
(match-prefix? prefix ns-sym) :=
  (or (= (str ns-sym) prefix)
      (str/starts-with? (str ns-sym) (str prefix ".")))
```

This avoids accidental broad matches.

---

## Output shape

Canonical machine-readable shape:

```clojure
{:gordian/command :subgraph
 :prefix          "gordian"
 :members         [gordian.aggregate gordian.classify gordian.close ...]

 :internal
 {:node-count        15
  :edge-count        18
  :density           0.0857
  :propagation-cost  0.12
  :cycles            []
  :nodes             [{:ns ...} ...]}   ; project-global node records for members

 :boundary
 {:incoming-count   3
  :outgoing-count   6
  :incoming         [{:from app.main :to gordian.main} ...]
  :outgoing         [{:from gordian.main :to clojure.edn} ...]
  :external-deps    [app.main clojure.edn clojure.set user]
  :dependents       [app.main user]}

 :pairs
 {:conceptual {:internal [pair ...]
               :touching [pair ...]}
  :change     {:internal [pair ...]
               :touching [pair ...]}}

 :findings         [finding ...]         ; touching family
 :clusters         [cluster ...]         ; reclustered from touching findings

 :summary
 {:finding-counts
  {:hidden-conceptual 4
   :hidden-change 2
   :cross-lens-hidden 1
   :cycle 0
   :hub 1}
  :pair-counts
  {:internal-conceptual 5
   :touching-conceptual 8
   :internal-change 1
   :touching-change 3}
  :boundary
  {:incoming-count 3
   :outgoing-count 6}}}
```

### Design notes
- `:members` are the family member namespaces
- `:internal` reflects metrics on the induced graph only
- `:nodes` are existing project-global node records filtered to members in v1
- `:pairs` are grouped by lens, then by internal vs touching membership
- `:findings` are touching findings
- `:clusters` are reclustered from filtered findings, not merely copied from
  project-global clusters

---

## Concepts

### Members
Namespaces matching the prefix.

### Induced graph
Graph restricted to members and edges between them.

### Boundary
Edges crossing member/non-member boundary.

### Internal pair
A pair where both endpoints are members.

### Touching pair
A pair where at least one endpoint is a member.

### Touching finding
A finding whose subject mentions at least one member namespace.

---

## Internal metrics

### Node count
Number of family members.

### Edge count
Number of directed require edges between members.

### Density
For directed graph:

```text
density = edge-count / (n * (n - 1))
```

For `n < 2`, density is `0.0`.

### Internal propagation cost
Propagation cost computed only over the induced graph.
This reveals subsystem-local entanglement that project-global metrics can hide.

### Cycles
Strongly connected components restricted to the induced graph.

### Nodes
Filtered project-global node metrics for family members. In v1, these remain
project-global rather than recomputed locally.

---

## Boundary metrics

### Incoming edges
Edges from outside family into family.

Shape:

```clojure
{:from outside.ns :to inside.ns}
```

### Outgoing edges
Edges from family to outside.

Shape:

```clojure
{:from inside.ns :to outside.ns}
```

### External deps
Distinct outside namespaces depended on by any member.

### Dependents
Distinct outside namespaces that depend on any member.

These give a concise picture of family encapsulation and leakage.

---

## Pair slicing

Pairs are grouped by lens and sliced two ways:

```clojure
:pairs
{:conceptual {:internal [...]
              :touching [...]} 
 :change     {:internal [...]
              :touching [...]}}
```

### Why both?
- `:internal` shows issues inside the subsystem
- `:touching` shows issues inside or across the boundary

This keeps the v1 shape simple while still supporting both local and boundary
inspection.

---

## Finding slicing and clustering

### Touching findings
A finding touches the family if any namespace in its subject is a member.

Subject rules:
- `{:ns sym}` → touch if `sym` is a member
- `{:ns-a a :ns-b b}` → touch if `a` or `b` is a member
- `{:members #{...}}` → touch if intersection with members is non-empty

### Local clusters
For usability, touching findings should be reclustered locally, rather than
showing the global cluster unchanged.

Reason:
- global clusters may include many unrelated out-of-family findings
- local reclustering better reflects the subsystem-focused task

So v1 should:
1. filter touching findings
2. run clustering over just those findings
3. store the result in `:clusters`

---

## New pure module

### `subgraph.clj`

Public functions:

```clojure
(match-prefix? prefix ns-sym) -> boolean
(members-by-prefix graph prefix) -> [sym]
(induced-graph graph members) -> {sym #{sym}}
(boundary-edges graph members) -> {:incoming [...] :outgoing [...]
                                   :incoming-count n :outgoing-count n
                                   :external-deps [...] :dependents [...]}
(graph-density graph) -> double
(pair-membership pair members) -> :internal | :touching | nil
(filter-pairs pairs members membership) -> [pair]
(finding-touches-members? finding members) -> boolean
(filter-findings findings members) -> [finding]
(summary-counts findings conceptual-pairs change-pairs boundary) -> map
(subgraph-summary report prefix) -> map | {:error ...}
```

---

## Function design details

### `match-prefix?`
Input:
- prefix string
- namespace symbol

Output:
- boolean

### `members-by-prefix`
Input:
- full project graph
- prefix string

Output:
- sorted vector of matching namespace symbols

Returns empty vector when no matches exist.

### `induced-graph`
Input:
- full graph
- set of members

Output:
- graph containing only member nodes and internal edges

Example:

```clojure
{'a #{'b 'x}
 'b #{'c}
 'c #{}}
members = #{'a 'b}

=> {'a #{'b}
    'b #{}}
```

### `boundary-edges`
Input:
- full graph
- member set

Output:

```clojure
{:incoming [{:from 'x :to 'a}]
 :outgoing [{:from 'a :to 'y}]
 :incoming-count 1
 :outgoing-count 1
 :external-deps ['y]
 :dependents ['x]}
```

### `graph-density`
Input:
- directed graph

Output:
- `0.0` for `n < 2`
- else `m / (n*(n-1))`

### `pair-membership`
Input:
- pair
- member set

Output:
- `:internal` when both endpoints are members
- `:touching` when exactly one endpoint is a member
- `nil` when neither endpoint is a member

### `finding-touches-members?`
Input:
- finding
- member set

Output:
- boolean

Should support all current finding subject shapes.

### `subgraph-summary`
Main assembly function.

Input:
- full enriched diagnose-style report
- prefix string

Steps:
1. compute members
2. return error when none match
3. build induced graph
4. compute internal metrics via existing pure modules
5. filter nodes to members
6. slice conceptual/change pairs into internal + touching
7. filter touching findings
8. recluster touching findings
9. build summary counts
10. assemble final map

---

## Reuse of existing modules

Subgraph views should reuse the existing architecture machinery rather than
reimplementing it.

Use:
- `close.clj` and `aggregate.clj` for subgraph-local propagation cost
- `scc.clj` for cycles
- `cluster.clj` for local clustering
- `diagnose.clj` findings generated once globally, then filtered
- `prioritize.clj` actionability already attached to findings upstream

This keeps the subgraph module focused on slicing and assembly.

---

## Command behavior

### `gordian subgraph <prefix>`
Should behave like `diagnose` in terms of analysis richness:
- conceptual lens auto-enabled at `0.15` unless overridden
- change lens auto-enabled unless disabled in future
- findings and clusters computed before slicing
- rank behavior preserved via `--rank severity|actionability`

This gives users a rich subsystem view, not just a structural slice.

### `gordian explain <target>`
If exact namespace exists, preserve current explain behavior.
If exact namespace does not exist but prefix members exist, render subgraph view.

Machine-readable output should still use:

```clojure
:gordian/command :subgraph
```

because the payload shape differs materially from namespace explain.

---

## Text rendering

Suggested structure:

```text
gordian subgraph — gordian
members: 15
rank: actionability

MEMBERS
  gordian.aggregate
  gordian.classify
  gordian.close
  ...

INTERNAL
  nodes: 15
  edges: 18
  density: 8.6%
  propagation cost: 12.0%
  cycles: none

BOUNDARY
  incoming edges: 3
  outgoing edges: 6
  dependents: app.main, user
  external deps: clojure.edn, clojure.set

INTERNAL CONCEPTUAL PAIRS
  ● MEDIUM  gordian.aggregate ↔ gordian.close [act=8.8]
  ● LOW     gordian.close ↔ gordian.scc [act=7.4]

TOUCHING FINDINGS
  ● MEDIUM  gordian.compare ↔ gordian.envelope [act=8.4]
  ● LOW     gordian.main [act=0.8]

CLUSTERS
  ...
```

### Sections
- header
- members
- internal
- boundary
- internal conceptual pairs
- internal change pairs
- touching findings
- clusters

---

## Markdown rendering

Suggested sections:
- `# gordian subgraph — <prefix>`
- `## Members`
- `## Internal`
- `## Boundary`
- `## Internal Conceptual Pairs`
- `## Internal Change Pairs`
- `## Findings`
- `## Clusters`

Use tables for summaries and bullets for details.

---

## Error handling

### No matching members
Return:

```clojure
{:error "no namespaces found for prefix 'foo.bar'"
 :available [gordian.aggregate gordian.close ...]}
```

### One matching member
Still valid. The subgraph becomes a one-node graph.

### No conceptual/change data
Return empty vectors, not errors.

---

## Ranking behavior

Subgraph should honor the same ranking mode as diagnose:

```bash
gordian subgraph gordian --rank severity
gordian subgraph gordian --rank actionability
```

Default remains `severity` for consistency.

Since findings are already enriched upstream, subgraph rendering just preserves
that ordering.

---

## Testing strategy

### `subgraph_test.clj`

#### Prefix matching
- exact match
- dotted prefix match
- false positive prevention

#### Member selection
- multiple members
- no members
- single exact member

#### Induced graph
- removes outside nodes
- keeps only internal edges

#### Boundary edges
- incoming only
- outgoing only
- mixed case
- distinct dep/dependent extraction

#### Density
- empty graph
- one node
- multi-node

#### Pair membership/filtering
- internal pair
- touching pair
- unrelated pair

#### Finding filtering
- `:ns`
- `:ns-a/:ns-b`
- `:members`

#### Full summary
- members correct
- internal counts correct
- boundary counts correct
- pairs sliced correctly
- findings/clusters filtered correctly

### `main_test.clj`
- parse `subgraph` args
- `explain` exact namespace still routes to explain
- `explain` prefix fallback routes to subgraph behavior
- `subgraph` output contains prefix and expected summary markers

### `output_test.clj`
- text renderer contains internal/boundary sections
- markdown renderer contains summary tables and member section
- internal pair rendering includes actionability when present

---

## Schema impact

This is additive.
No schema bump required.

New command:

```clojure
:gordian/command :subgraph
```

New top-level payload keys:
- `prefix`
- `members`
- `internal`
- `boundary`
- `pairs`
- `findings`
- `clusters`
- `summary`

---

## Minimal viable implementation

### v1 includes
- `subgraph` command
- explain fallback to subgraph
- induced graph
- internal metrics: node-count, edge-count, density, PC, cycles
- boundary metrics: incoming/outgoing counts, external deps, dependents
- internal/touching conceptual and change pairs
- touching findings and local clusters
- text / markdown / EDN / JSON output

### Deferred
- explicit boundary-only pair category
- local per-node metrics
- subgraph health heuristic
- prefix suggestion UX
- focus modes (`--focus internal|boundary`)

---

## Implementation order

1. create `subgraph.clj`
2. add pure tests in `subgraph_test.clj`
3. wire `subgraph` subcommand into `main.clj`
4. add `explain` fallback behavior
5. add output renderers in `output.clj`
6. add integration and output tests
7. update schema, PLAN, and state

---

## Success criteria

We are done when:
- `gordian subgraph <prefix>` works end-to-end
- `gordian explain <prefix>` falls back to subgraph mode when no exact namespace exists
- users can inspect family-local structure, boundary edges, hidden pairs, and findings
- all four output modes work
- output remains fully machine-readable and composable
