# 018 — DSM Diagonal Block Partitioning

## Problem

The first DSM implementation used strongly connected components (SCCs) as the
primary diagonal block basis.

That is mathematically clean, but for ordinary Clojure namespace graphs it is
usually not very informative. Namespace require graphs are typically acyclic or
close to acyclic, so SCC collapse often yields only singleton blocks.

This means the SCC-based DSM tends to produce:
- little or no dimensionality reduction
- few or no meaningful diagonal blocks
- a matrix that is correct but not especially informative

The more useful architectural question is not:
- which namespaces are mutually reachable?

but rather:
- how should namespaces be ordered and partitioned so that the dependency marks
  concentrate into coherent diagonal blocks with minimal cross-block leakage?

That is a more native DSM formulation.

## Goal

Replace SCC-first DSM blocking with a matrix-native approach:

1. order namespaces along a dependency-respecting linearization
2. partition that order into contiguous diagonal blocks
3. choose the partition that minimizes a block-structured cost function
4. optionally refine the order locally and recompute the partition

The result should be a DSM whose diagonal blocks represent cohesive modules in
terms of dependency locality, not graph cycles.

---

## Scope

### In scope
- project-internal namespace graph only
- dependency-respecting namespace order
- contiguous diagonal block partitioning
- dynamic programming over split points
- Thebeau-style block cost function
- optional local order refinement by valid adjacent swaps
- block-quotient DSM rendering after partitioning

### Out of scope
- SCCs as primary block basis
- exact minimum linear arrangement solvers
- global simulated annealing in v1
- multi-lens block formation in v1
- non-contiguous cluster membership

---

## Design summary

The revised DSM model has two distinct layers:

1. **ordering** — place namespaces in a good linear order
2. **partitioning** — split that line into contiguous blocks

The quality criterion is:
- keep dependency marks near the diagonal
- make within-block marks cheap
- make cross-block marks expensive

This yields diagonal blocks that represent cohesive dependency-local regions of
the architecture.

---

## Base graph

Let:

- `N` = set of project namespaces
- `E ⊆ N × N` where `(a,b) ∈ E` means namespace `a` directly requires `b`

So the project require graph is:

\[
G=(N,E)
\]

The DSM is built only from project-internal namespaces.
Dependency targets not present in `N` are excluded from the matrix basis.

---

## Stage 1 — Ordering

We first choose a linear order over namespaces.

### Recommended v1 order
Use a dependency-respecting order derived from the project DAG:
- compute a topological order of the require graph
- use depth-first traversal to choose a stable, locality-preserving topo order
- emit post-order (reverse if needed for the chosen triangular convention)
- break ties lexicographically by namespace label

### Why this order
This provides:
- a deterministic baseline
- a dependency-respecting arrangement
- a matrix that is largely lower- or upper-triangular depending on convention
- good initial locality before any numerical optimization

### Conventions
For Gordian, the preferred interpretation is:
- dependees/foundations earlier
- dependers/consumers later

That gives a dependency-respecting order suitable for diagonal block discovery.

---

## Stage 2 — Contiguous partitioning

Given an ordered sequence of namespaces:

\[
[n_1, n_2, \dots, n_k]
\]

we partition it into contiguous blocks by choosing split points:

\[
1 = s_0 < s_1 < \dots < s_m = k
\]

These define blocks:

\[
B_j = [s_{j-1}, s_j)
\]

Each block corresponds to a square submatrix along the diagonal.

### Why contiguous blocks
A DSM is visually interpreted through adjacency in the linear order.
Contiguous blocks are therefore the right abstraction for:
- module boundaries
- diagonal density
- cross-boundary leakage

Non-contiguous clustering may be mathematically valid, but it is not the right
first representation for a matrix-centric tool.

---

## Cost function

We need a cost model that rewards cohesive blocks and penalizes boundary
crossings.

### Boundary + size intuition
For each candidate block:
- boundary crossings should be expensive
- oversized blocks should also be expensive, even if they eliminate crossings

This balances:
- large blocks, which absorb more edges but can become too coarse
- small blocks, which increase cross-block leakage

### Parameters
Let:
- `β > 0` = quadratic block-size penalty coefficient

Current default:
- `β = 0.05`

### Practical scoring model
The current implementation uses a simple O(1)-per-interval block objective:
- boundary-crossing count for the interval
- quadratic size penalty `β · |B|²`
- a mild weak-cohesion penalty when internal edge count falls below a modest
  target internal density

This preserves O(n²) dynamic programming while preventing the degenerate
single-giant-block solution that would arise from boundary-crossing count alone.

### Total partition cost
For block interval `B=[i,j]`, let:
- `cross(B)` = number of marks with exactly one endpoint in `B`
- `|B|` = block size

The block score is:

\[
Cost(B)=cross(B)+\beta \cdot |B|^2 + weakCohesion(B)
\]

and partition score is:

\[
Cost(P)=\sum_{B\in P} Cost(B)
\]

### Interpretation
- boundary crossings are penalized directly
- giant blocks are discouraged by the quadratic size term
- sparse multi-namespace blocks are discouraged by the weak-cohesion term

---

## Dynamic programming formulation

For a fixed order, we want the optimal contiguous partition.

### Interval blocks
Consider an interval block spanning indices `[a,b]`.

If we can efficiently compute:
- internal edge contribution for `[a,b]`
- crossing-edge contribution relative to the partition

then optimal partitioning can be solved by dynamic programming over split
points.

### DP sketch
Let:

\[
DP[t] = \text{minimum cost for partitioning prefixes } [1,t]
\]

Then:

\[
DP[t] = \min_{s \le t} \big(DP[s-1] + BlockCost(s,t)\big)
\]

with backpointers for reconstruction.

### Complexity
- there are `O(n²)` candidate intervals
- with appropriate precomputation, block scoring can be made cheap enough for
  practical `O(n²)` to `O(n³)` total runtime

This is acceptable for typical Clojure namespace counts.

---

## Practical scoring note

The exact decomposition of boundary-crossing cost across blocks can be handled
in multiple equivalent ways. The important invariant is:

- internal edges should incur a cost based on block size
- cross-block edges should incur a much larger global penalty

The implementation should prefer the simplest scoring formulation that is:
- deterministic
- testable
- explainable

A mathematically elegant exact recurrence is less important than preserving the
architectural intent of the objective.

---

## Stage 3 — Quotient block graph

Once the partition is chosen, we collapse namespaces into contiguous blocks.

### Quotient graph
Let:
- `B = {B₁, …, B_m}` be the chosen blocks

Define quotient graph `Q(B)` with:
- nodes = blocks `B_i`
- edge `B_i → B_j` iff there exists `a ∈ B_i` and `b ∈ B_j` such that
  `(a,b) ∈ E`

### Optional weight
Weighted quotient edge:

\[
w(B_i,B_j)=|\{(a,b)\in E \mid a\in B_i, b\in B_j\}|
\]

This quotient graph becomes the block-level DSM basis.

---

## Stage 4 — Order refinement

The initial topological order is good, but not necessarily optimal for DSM
locality.

### Recommendation for v1
Use local deterministic refinement:

1. start from DFS/topological seed order
2. try swapping adjacent namespaces
3. then try swapping adjacent partition blocks
4. accept swap if total partitioned DSM cost decreases
5. recompute optimal partition after accepted swaps
6. repeat until no improving local swap remains

### Constraint
Only permit swaps that preserve dependency validity.

For namespace-level swaps, a simple rule is:
- swap adjacent nodes only if they are incomparable in the dependency DAG

For block-level swaps, the current implementation uses a conservative rule:
- swap adjacent blocks only when every cross-block namespace pair is mutually
  incomparable in the dependency DAG

### Why this is good
This gives:
- deterministic behavior
- monotonic improvement
- relatively simple implementation
- strong practical gains without global optimization complexity

---

## Deferred refinement: MLA-style optimization

If additional optimization is needed later, the ordering objective can be
interpreted as a weighted linear arrangement problem.

For block order `pos : B → {1,…,m}`:

\[
LinearCost(pos)=\sum_{(B_i,B_j)\in E_Q} w(B_i,B_j)\cdot |pos(B_i)-pos(B_j)|
\]

This directly minimizes the distance of dependency marks from the diagonal.

### Notes
- exact minimum linear arrangement is NP-hard
- small to medium namespace graphs can still support heuristic optimization
- simulated annealing or hill climbing can be layered later

This is a natural future extension, but not required for the first useful
implementation.

---

## Full pipeline

### 1. Build project graph

\[
G=(N,E)
\]

project namespaces only.

### 2. Compute dependency-respecting order
- DFS/topological traversal
- stable lexicographic tie-breaks

### 3. Build ordered matrix
- adjacency under the chosen order

### 4. Compute optimal contiguous partition
- dynamic programming over split points
- Thebeau-style cost objective

### 5. Collapse to block quotient graph
- blocks become meta-nodes
- inter-block structural edges counted/weighted

### 6. Optional local order refinement
- adjacent valid swaps
- recompute partition
- keep improvements only

### 7. Render DSM
- diagonal blocks = optimal contiguous partition
- off-diagonal entries = quotient graph edges
- optional intra-block mini-matrices for large or interesting blocks

---

## Why this is better than SCC-first blocking

### SCC-first
Answers:
- which namespaces are mutually reachable?

In Clojure namespace graphs this is often trivial.

### Diagonal block partitioning
Answers:
- how should we segment the dependency-respecting order into coherent modules?
- where does the architecture naturally form dense diagonal regions?
- which boundaries minimize cross-block leakage?

This is a much more useful question for namespace architecture.

---

## Relationship to co-usage formalization

This design does not contradict the earlier co-usage approach.
They operate at different levels:

- co-usage gives a semantic similarity notion
- diagonal partitioning gives a matrix-native contiguous block notion

Possible future synthesis:
- use co-usage similarity to influence the initial order
- use diagonal partitioning to produce the final block segmentation

For v1, however, the partitioning-based design is simpler and more aligned with
DSM practice.

---

## Output implications

The current SCC-specific terms should be revised.

### Current output framing
- collapsed SCC matrix
- cyclic SCC details

### Revised framing
- ordered namespace DSM
- diagonal block partition summary
- quotient block matrix
- optional block detail matrices

This makes the feature about architectural modularization rather than cycle
collapse.

---

## Parameter choices

### `α` (cost exponent)
Recommended initial default:
- `α = 1.5`

Rationale:
- stronger than linear pressure against oversized blocks
- not so aggressive that it fragments everything

### Maximum block count / minimum block size
These may be added later if the optimization produces visually awkward extreme
partitions, but should not be part of the first version unless needed.

---

## Rejected alternatives

### SCC as primary block basis
Rejected because namespace require graphs are usually acyclic and SCC collapse
adds little useful structure.

### Co-usage connected components as first implementation
Useful, but not ideal as the first matrix-native representation because it does
not guarantee contiguous blocks in the final order.

### Full global simulated annealing first
Rejected for v1 because deterministic topo + DP + local swaps already provides
strong value at much lower complexity.

---

## Future extensions

### Co-usage-informed ordering
Use structural or multi-lens similarity to influence initial order.

### Multi-lens partition cost
Extend block cost to include conceptual/change interactions as well as
structural marks.

### Hierarchical partitioning
Allow recursive partitioning within large blocks.

This is now the preferred explanation strategy for large residual blocks:
- keep the top-level partition as the coarse global answer
- recursively decompose sufficiently large blocks on their induced subgraphs
- retain recursion only when it yields a non-trivial child partition

### Compare / gate integration
Diff block partitions and enforce ratchets on block cohesion / boundary leakage.

### Interactive HTML drilldown
Use the partitioned blocks as the main HTML DSM view with block-pair detail
navigation.

---

## Success criteria

The revised DSM model is successful if a user can:

1. run `gordian dsm`
2. see meaningful diagonal blocks even when the require graph has no cycles
3. understand how namespaces were grouped as contiguous modules
4. see reduced cross-block leakage relative to naive ordering
5. use the resulting blocks as plausible subsystem candidates

The key shift is:
- from cycle collapse
- to optimized diagonal modularization

That is the more useful DSM formulation for Clojure namespace architectures.
