# 019 — Hierarchy / Usage Misalignment via DSM

## Problem

Gordian can already detect structural coupling, hidden conceptual/change
relationships, cycles, hubs, and DSM diagonal blocks. But it does not yet
answer a common architecture question:

- does the namespace hierarchy reflect the way the code is actually used?

In many Clojure systems, namespace names encode an intended decomposition:

- subsystem ownership (`foo.billing.*`, `foo.identity.*`)
- layered structure (`foo.api.*`, `foo.service.*`, `foo.store.*`)
- implementation boundaries (`foo.parser.*`, `foo.runtime.*`)
- packaging hints (`foo.ui.*`, `foo.model.*`, `foo.http.*`)

That naming hierarchy is a kind of architectural claim. The require graph and
DSM expose actual structural behavior. When these disagree, the mismatch is
often a useful signal:

- a named family is not actually cohesive
- multiple named families behave like one module
- a namespace sits in the wrong family
- hierarchy implies direction that code violates
- family boundaries are more porous than their names suggest

Today Gordian has some relevant building blocks:

- family/prefix analysis
- façade-aware interpretation
- family-noise suppression
- DSM block partitioning
- explain/diagnose reporting pipelines

But there is no dedicated analysis that compares **intended grouping by
namespace hierarchy** against **observed grouping by structural usage**.

---

## Goal

Add a hierarchy-alignment analysis that uses DSM and family structure to flag
places where namespace hierarchy appears misaligned with actual dependency
behavior.

The feature should not claim that the hierarchy is "wrong" in an absolute
sense. It should produce ranked **misalignment signals** that help a human ask:

- are these namespaces named/grouped well?
- should this family be split or merged?
- is this namespace misplaced?
- does the hierarchy encode a direction that code violates?

---

## Framing

This feature is best described as:

**namespace hierarchy / usage misalignment analysis**

not:

- hierarchy correctness proof
- naming lint
- package policy checker only

The intended output is architectural evidence, not hard law.

---

## Why DSM is a good fit

DSM already computes a discovered structural decomposition:

- ordering over namespaces
- contiguous diagonal blocks representing dependency-local regions
- inter-block dependency leakage
- residual large mixed blocks that indicate entanglement or broad coupling

That makes DSM useful as a comparison target for hierarchy.

Conceptually:

- **hierarchy partition** = declared or implied decomposition from namespace prefixes
- **DSM partition** = discovered decomposition from actual dependency structure

Misalignment between these two partitions is itself meaningful.

Examples:

- one namespace family scattered across many DSM blocks
- one DSM block mixing several hierarchy families
- sibling families with stronger mutual coupling than internal cohesion
- a namespace structurally clustering with a different family than its own
- dependencies flowing opposite to the direction implied by the hierarchy

---

## Scope

### In scope
- derive hierarchy families from namespace prefixes
- compare hierarchy families against DSM blocks
- compute family-level and namespace-level misalignment metrics
- optionally model expected family direction rules
- surface ranked findings in diagnose-style output
- add machine-readable schema for hierarchy alignment data
- support text, markdown, EDN, and JSON output

### Out of scope for v1
- fully automatic inference of semantic architecture layers
- rename suggestions with guaranteed correctness
- multi-lens hierarchy alignment using conceptual/change coupling
- full ontology of namespace meaning beyond prefix structure
- automatic rewrite/refactor generation

---

## Core idea

Treat namespace prefixes as a proposed architecture partition, then compare that
partition with the DSM partition and the structural edge graph.

There are three complementary questions:

1. **family cohesion**
   - do members of a named family mostly live together structurally?

2. **block purity**
   - does a DSM block mostly correspond to one named family?

3. **direction agreement**
   - do structural dependencies respect the direction implied or configured by
     the hierarchy?

The feature should combine these into findings rather than relying on one raw
metric.

---

## Conceptual model

### Namespace hierarchy family

A family is a grouping induced from namespace prefixes.

Examples at depth 2:

- `gordian.scan`
- `gordian.output`
- `gordian.dsm`
- `gordian.test-fixtures`

Examples at depth 3:

- `foo.http.server`
- `foo.http.client`
- `foo.auth.jwt`

Family derivation should be configurable because the right architectural cut is
project-specific.

### Hierarchy partition

Given namespace set `N`, choose a family function:

\[
family : N \to F
\]

where `F` is the set of hierarchy families.

This induces a partition of namespaces by naming.

### DSM partition

Given the existing DSM ordering and contiguous block partitioning, each
namespace belongs to exactly one DSM block:

\[
block : N \to B
\]

where `B` is the set of DSM blocks.

### Misalignment

Misalignment is any strong disagreement between `family(n)` and `block(n)`, or
between hierarchy-implied direction and observed edge direction.

---

## Kinds of hierarchy mismatch

### 1. Scattered family

A family is spread across many DSM blocks.

Interpretation:
- the family may be too broad
- the namespaces may not form one cohesive unit
- the hierarchy may be grouping by naming convention rather than dependency
  behavior

### 2. Mixed block

A DSM block contains namespaces from several families with low purity.

Interpretation:
- multiple named families behave like one structural module
- a shared abstraction may be missing
- boundaries may be artificial or premature

### 3. Strong cross-family coupling

Two hierarchy families are strongly coupled to each other relative to their own
internal cohesion.

Interpretation:
- the split may be artificial
- a family pair may deserve a common parent subsystem or intermediate layer
- a namespace may be misplaced and creating leakage

### 4. Misplaced namespace

A namespace appears structurally more aligned with another family than with its
own.

Interpretation:
- rename or move candidate
- family membership by name may not match role in the graph

### 5. Reverse hierarchy dependency

An edge goes opposite to expected family direction.

Interpretation:
- hierarchy implies layer/order that code does not respect
- naming may be inverted or architecture may be leaking upward

### 6. Porous family boundary

A family has weak internal connectivity and heavy external dependence.

Interpretation:
- family is not a real module boundary
- family may be too fine-grained or incorrectly composed

---

## Design dimensions

## 1. Family derivation

### Default approach
Use namespace prefix depth.

Example:

- depth 2 on `gordian.output.html` => family `gordian.output`
- depth 3 on `foo.http.server.middleware` => family `foo.http.server`

### Configuration options

```clojure
{:hierarchy {:mode :prefix
             :depth 2}}
```

Potential future modes:
- explicit regex grouping
- explicit namespace → family map
- mixed rules with fallbacks

### Why configurable depth matters

Too shallow:
- nearly everything falls into one family

Too deep:
- every family becomes tiny and metrics become noisy

A practical default is likely depth 2, with override support.

---

## 2. Family-level metrics

These metrics summarize whether a named family behaves like a coherent module.

### Family fragmentation

How many DSM blocks contain members of family `F`?

\[
fragmentation(F) = |\{ block(n) \mid family(n)=F \}|
\]

High fragmentation suggests the family is structurally scattered.

Possible normalized variant:
- distribution entropy of family members across blocks

### Dominant-block share

Let the dominant block for family `F` be the block containing the most members
of `F`.

\[
dominantShare(F) = \frac{max_b\;|\{n \mid family(n)=F \land block(n)=b\}|}{|F|}
\]

Low dominant share means no single DSM block captures the family.

### Internal vs external structural edge balance

For family `F`:
- internal edges: both endpoints in `F`
- external edges: exactly one endpoint in `F`

Signals:
- low internal / high external => weak family boundary
- strong dependence on one other family => likely split or placement issue

### Cross-family concentration

For each family pair `(F,G)`:
- count edges from `F` to `G`
- count edges in both directions
- normalize by family sizes or edge volume

High bilateral coupling is a candidate finding.

---

## 3. DSM-block metrics

These metrics ask whether discovered structural blocks correspond to named
families.

### Block purity

For a DSM block `B`, let the dominant family be the family with the most
members in the block.

\[
purity(B) = \frac{max_f\;|\{n \mid block(n)=B \land family(n)=f\}|}{|B|}
\]

Low purity suggests the block mixes multiple families.

### Family count per block

\[
familyCount(B) = |\{ family(n) \mid block(n)=B \}|
\]

A high count is not always bad, but large low-purity blocks are suspicious.

### Boundary explanation

For each block, summarize:
- family composition
- dominant family share
- inter-family edges inside the block
- inter-block edges by family

This helps explain *why* a block exists.

---

## 4. Namespace-level metrics

These metrics support actionable findings.

### Own-family affinity vs best-other-family affinity

For namespace `n`, compare:
- structural connections to its own family
- structural connections to every other family

If some other family strongly dominates, `n` is a candidate misplaced
namespace.

A simple affinity score can combine:
- direct deps into family
- direct dependents from family
- same-block membership weight
- shortest-path proximity or local neighborhood overlap (future)

### Block-majority disagreement

If `n` sits in a DSM block whose dominant family is not `family(n)`, and `n`
interacts more with that dominant family than with its own, flag it.

This will often be the most actionable signal.

---

## 5. Direction rules

Some hierarchies imply allowed dependency direction.

Examples:
- `foo.api -> foo.service -> foo.store`
- `foo.ui -> foo.app -> foo.domain -> foo.db`
- `foo.impl.*` should not be required by `foo.public.*`

This cannot be inferred reliably from prefixes alone, so v1 should support
optional configuration.

### Proposed config

```clojure
{:hierarchy {:mode :prefix
             :depth 2
             :order ["foo.ui" "foo.app" "foo.domain" "foo.db"]}}
```

or

```clojure
{:hierarchy {:rules [{:from "foo.domain" :to "foo.ui" :allow false}
                     {:from "foo.store"  :to "foo.api" :allow false}]}}
```

### Finding type

If structural edges violate configured order/rules, emit
`reverse-hierarchy-dependency` findings.

This is likely the strongest signal when teams intentionally encode layering in
namespace names.

---

## Findings

The feature should produce ranked findings rather than only metrics.

### scattered-family

A family spans many DSM blocks with low dominant-block share.

Example interpretation:
- hierarchy family is too broad
- concerns grouped by name are not one structural unit

### mixed-block

A DSM block contains several families with low purity and meaningful internal
cross-family edges.

Example interpretation:
- structural module crosses naming boundaries

### strong-cross-family-coupling

Two families have unusually high bilateral coupling relative to their own
internal cohesion.

Example interpretation:
- split may be artificial
- hidden shared subsystem may exist

### misplaced-namespace

A namespace aligns more strongly with a family other than its declared family.

Example interpretation:
- move/rename candidate

### reverse-hierarchy-dependency

A structural dependency violates configured family direction rules.

Example interpretation:
- hierarchy claims layering that code does not honor

### porous-family-boundary

A family has weak internal cohesion and heavy cross-boundary interaction.

Example interpretation:
- family is not acting like a module boundary

---

## Output shape

This analysis could appear either as:

1. a new subcommand, e.g. `gordian hierarchy`
2. a section within `gordian diagnose`
3. a section appended to `gordian dsm`

### Recommendation

Use a **new pure analysis module** plus integration into both:
- `diagnose` for ranked findings
- `dsm` for explanatory summaries

Reason:
- diagnose is the right home for ranked architectural concerns
- dsm is the right home for block/family comparison tables

---

## Proposed report sections

### Family summary

For each family:
- member count
- dominant DSM block
- dominant-block share
- number of blocks touched
- internal edge count
- external edge count
- strongest coupled other family

### Block summary

For each DSM block:
- size
- family composition
- dominant family
- block purity
- internal inter-family edge count
- top coupled adjacent/external blocks

### Namespace candidates

For flagged namespaces:
- declared family
- best-matching other family
- current block
- evidence summary

### Direction violations

When configured:
- violating family pair
- edge count
- representative namespaces

---

## Machine-readable schema sketch

```clojure
{:hierarchy
 {:config {:mode :prefix
           :depth 2
           :order [...]} ; optional
  :families
  [{:family "gordian.output"
    :member-count 4
    :blocks-touched 2
    :dominant-block 7
    :dominant-block-share 0.75
    :internal-edge-count 3
    :external-edge-count 8
    :strongest-other-family {:family "gordian.dsm"
                             :edge-count 5}
    :fragmentation-score 2
    :porous? true}]
  :blocks
  [{:block-id 7
    :size 6
    :dominant-family "gordian.output"
    :purity 0.50
    :family-count 3
    :families [{:family "gordian.output" :count 3}
               {:family "gordian.dsm" :count 2}
               {:family "gordian.main" :count 1}]}]
  :namespace-candidates
  [{:ns 'gordian.html
    :declared-family "gordian.output"
    :best-other-family "gordian.dsm"
    :block-id 7
    :evidence {:same-block-dominant-family "gordian.dsm"
               :own-family-affinity 0.20
               :other-family-affinity 0.67}}]
  :direction-violations
  [{:from-family "foo.domain"
    :to-family "foo.ui"
    :edge-count 3
    :examples [['foo.domain.user 'foo.ui.views]
               ['foo.domain.order 'foo.ui.format]]}]
  :findings [...]}}
```

Final field naming should align with existing Gordian schema conventions.

---

## Heuristics and false-positive control

This feature must be conservative. Hierarchy mismatch is suggestive, not
self-evidently wrong.

### Expected exceptions
- façade namespaces
- `main` / orchestration namespaces
- public API aggregators
- utility/cross-cutting namespaces
- compatibility shims
- test support namespaces

### Existing Gordian knowledge that should be reused
- façade detection
- family-noise suppression
- family-scoped metrics
- explain-pair verdict framing

### Recommendation

Hierarchy findings should include interpretation text such as:
- "may indicate"
- "candidate split/merge"
- "structurally aligned with"
- "violates configured hierarchy rule"

Avoid language like:
- "incorrect namespace"
- "must move"

---

## Initial algorithm sketch

## Phase 1 — derive families
- choose family depth or configured mapping
- map each project namespace to a family

## Phase 2 — join with DSM blocks
- run existing DSM analysis
- derive `family -> block distribution`
- derive `block -> family composition`

## Phase 3 — compute metrics
- family fragmentation
- dominant-block share
- block purity
- cross-family edge counts
- namespace own-family vs best-other-family affinity

## Phase 4 — emit findings
- scattered-family
- mixed-block
- strong-cross-family-coupling
- misplaced-namespace
- reverse-hierarchy-dependency
- porous-family-boundary

## Phase 5 — integrate presentation
- summary tables in `dsm`
- ranked findings in `diagnose`
- drilldown support in `explain` / `subgraph` later

---

## Example interpretations

### Scattered family

`gordian.output.*` appears across 4 DSM blocks, with only 42% of members in its
largest block.

Interpretation:
- `output` may be grouping several concerns (formatting, markdown, html,
  command-specific rendering) that do not form one cohesive structural module.

### Mixed block

DSM block 6 contains members of `gordian.scan`, `gordian.config`,
`gordian.discover`, and `gordian.main`, with purity 0.25.

Interpretation:
- this region behaves like one startup/assembly subsystem despite being split
  across several naming families.

### Misplaced namespace

`foo.http.auth` is named under `http` but clusters structurally with
`foo.security.*`.

Interpretation:
- candidate move/rename, or evidence that the current namespace name reflects
  delivery mechanism rather than core responsibility.

### Reverse hierarchy dependency

`foo.domain.*` depends on `foo.ui.*` in violation of configured family order.

Interpretation:
- layering implied by the hierarchy is not currently upheld.

---

## CLI possibilities

### Option A — integrate into diagnose

```bash
gordian diagnose . --hierarchy-depth 2
```

Pros:
- naturally yields ranked findings
- easy adoption

Cons:
- less room for detailed family/block tables

### Option B — integrate into dsm

```bash
gordian dsm . --hierarchy-depth 2
```

Pros:
- aligns directly with block comparison

Cons:
- less natural for finding-oriented workflows

### Option C — dedicated command

```bash
gordian hierarchy . --depth 2
```

Pros:
- focused report
- clearer conceptual boundary

Cons:
- more surface area

### Recommendation

Start with:
- pure analysis module
- `diagnose` findings integration
- `dsm` summary integration

A dedicated command can come later if the report becomes rich enough.

---

## Open questions

1. What default family depth is most useful across typical Clojure projects?
2. Should family derivation stop at known leaf role names like `core`, `impl`,
   `api`, `test`, or remain purely positional in v1?
3. Should namespace-level affinity be based only on direct edges, or also on
   same-block membership and local neighborhood overlap?
4. How should façade namespaces be down-weighted in family purity and
   fragmentation metrics?
5. Should direction rules be simple total order, partial order, or explicit
   pairwise allow/deny rules first?
6. Should `subgraph` gain a family-alignment explanation mode later?

---

## Recommendation

Proceed with a first version framed as:

**DSM-assisted hierarchy alignment analysis**

v1 should focus on:
- family derivation by prefix depth
- DSM block purity and family fragmentation
- strong cross-family coupling
- misplaced namespace candidates
- optional configured hierarchy direction violations

This is likely to produce useful architectural signals with modest
implementation complexity because it reuses existing Gordian capabilities
instead of introducing a completely new lens.

---

## Likely implementation shape

New pure module candidates:
- `hierarchy.clj` — family derivation, metrics, findings
- or `alignment.clj` — if the concept broadens beyond hierarchy later

Likely touched integration points:
- `dsm.clj` — expose block/family comparison data
- `diagnose.clj` — add hierarchy misalignment findings
- `output.clj` — text/markdown formatting
- `envelope.clj` — schema exposure if surfaced in command payloads
- `config.clj` / CLI parsing — family depth and optional direction rules

---

## Summary

Namespace hierarchy is an architectural hypothesis.
DSM reveals actual structural grouping.

Comparing the two can surface high-value signals such as:
- scattered families
- mixed blocks
- porous boundaries
- misplaced namespaces
- reverse hierarchy dependencies

This should be implemented as a conservative, evidence-oriented analysis of
**hierarchy / usage misalignment**, not a hard correctness checker.
