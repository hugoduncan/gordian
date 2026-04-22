# 021 — Local Comprehension Complexity (LCC)

## Problem

Gordian is strong on architecture-scale metrics: coupling, cycles, propagation
cost, family structure, DSM blocks, and cross-lens findings. But it has no
account of **code in the small**.

The existing mainstream local metrics are useful but incomplete:

- **Cyclomatic Complexity** measures path count
- **Cognitive Complexity** improves this with nesting and flow interruption

Both still leave important local maintenance costs under-measured.

In practice, many small units are hard to safely understand and change under
local reasoning constraints not because branching is high, but because they:

- mix abstraction levels
- require tracking many live concepts at once
- churn data shape repeatedly
- hide state or temporal coupling
- depend on too much non-local meaning
- break local regularity

A local metric for Gordian should therefore target a different question:

> how much transient mental state must a reader hold to understand and safely
> change this unit?

This design introduces **Local Comprehension Complexity (LCC)** as a richer,
shape-oriented local metric system.

---

## Goal

Define a local-analysis metric model that:

1. improves on cyclomatic/cognitive complexity for local change work
2. explains *why* a local unit is hard, not only *how much*
3. rewards code that is locally coherent, predictable, resilient to change, and
   understandable primarily from local source
4. defines a clear first version of the metric without collapsing the concept
   into implementation convenience
5. can serve as a normative basis for later Gordian implementation work

---

## Non-goals

### Not a theorem of good code
LCC is not a proof that code is good or bad. It is an evidence-oriented model
of local comprehension burden in service of safe local change.

### Not a replacement for architectural metrics
LCC complements system-scale analysis. A project can have excellent
architecture-scale shape and still contain locally hard functions.

### Not a style-only linter
Formatting and naming consistency matter, but LCC is primarily about
comprehension load, not surface style conformance.

### Not a single scalar only
A rollup score is useful for ranking, but the primary artifact should be a
vector of burdens plus findings.

---

## Framing

Cyclomatic Complexity asks:

> how many independent control-flow paths exist?

Cognitive Complexity asks:

> how hard is the control flow to mentally follow?

LCC asks:

> how much local reasoning burden must a reader carry across flow, state,
> shape, abstraction, and non-local dependency in order to safely understand
> and change this unit?

The emphasis is not generic readability in the abstract, but bounded local
reasoning in service of trustworthy change.

---

## Design principles

A useful local metric should satisfy the following properties.

### 1. Branch sensitivity
More branching should usually increase local cost.

### 2. Nesting sensitivity
Deeper control nesting should cost more than equivalent flat structure.

### 3. State sensitivity
Mutation, rebinding, and temporal dependence should be charged explicitly.

### 4. Shape sensitivity
Repeated or unstable data-shape transitions should increase cost.

### 5. Abstraction sensitivity
Mixing semantic levels in one local unit should increase cost when it prevents
one coherent local purpose from remaining visible.

### 6. Locality sensitivity
Code that requires chasing correctness meaning elsewhere should cost more than
code that is largely understandable from local source.

### 7. Regularity reward
Predictable, pattern-preserving local structure should cost less when it lets
similar things be understood the same way.

### 8. Compositionality
Splitting a hard function into coherent helpers should usually reduce local
burden, even if overall behavior is unchanged.

This is an important difference from path-count-centric metrics.

---

## Normative v1 constraints

The following constraints resolve intentional v1 simplifications so the first
implementation remains stable and comparable across runs.

### Canonical unit of analysis
For v1, the canonical local unit is:
- one top-level `defn` arity
- one top-level `defmethod` implementation body, keyed by multimethod var and
  dispatch value

Nested local units are **not** emitted as standalone report records in v1:
- anonymous `fn`
- `letfn` locals
- other nested helpers

Their burden is folded into the enclosing top-level unit.

### Main path
In v1, the **main path** is not a full CFG-derived notion.
It means the lexical top-level sequence of steps through which a reader is most
likely to construct the unit's primary local story.

Examples:
- top-level forms in a function body
- bindings and final body in a top-level `let`
- stages in `->`, `->>`, `some->`, and `cond->`
- top-level forms inside `do`

Branches are analyzed as subregions, but the parent unit's main path is the
lexically primary top-level step sequence rather than a whole-program path
search.

When branch-local evidence must be merged back into the enclosing unit, v1
uses simple deterministic rules:
- `max` for branch-local scalar burdens
- `union` for evidence collections
- explicit `Variant` charges when branch output shapes differ

### Primary ownership of evidence
Some burden families overlap conceptually. To limit uncontrolled double
counting, v1 assigns a primary owner to each evidence class:

- predicate structure → `Flow`
- mutation/effect sequencing → `State`
- shape transitions / destructuring / variant returns → `Shape`
- semantic-level transitions / incidental concerns → `Abstraction`
- helper opacity / inversion / semantic lookup distance → `Dependency`
- inconsistency / asymmetry / style drift → `Regularity`
- simultaneous tracked facts → `WorkingSet`

`WorkingSet` is derivative: it does not introduce new evidence classes. It
measures how many already-relevant facts are active simultaneously.

### Transparent vs non-trivial helpers
A helper is **transparent** in v1 if it is in a fixed allowlist of obvious core
operations or is a literal constructor/access form whose semantics are evident
locally.

Initial transparent examples include:
- `assoc`, `dissoc`, `update`, `merge`, `select-keys`
- `conj`, `into`, `vec`, `set`, `mapv`
- `map`, `filter`, `keep`, `remove`
- `keys`, `vals`, `first`, `rest`, `next`
- `get`, `contains?`
- `inc`, `dec`, `+`, `-`, `*`, `/`
- `=`, `<`, `>`, `<=`, `>=`
- `not`, `some?`, `nil?`, `empty?`

Threading macros are counted structurally, not as helpers.

A helper is **non-trivial** when it is not in the transparent set and its
correctness meaning is not inferable from local syntax alone.

The allowlist may become configurable later, but is fixed in v1.

### Abstraction-level assignment
Each relevant form receives exactly one dominant abstraction label in v1:
- `:mechanism`
- `:orchestration`
- `:data-shaping`
- `:domain`

Assignment uses deterministic precedence:
1. `:mechanism` for interop, mutation, IO, regex/path/string plumbing,
   indexing, or host-detail operations
2. `:orchestration` for sequencing multiple semantically distinct stages or
   subsystem calls
3. `:data-shaping` for collection/map transformation or normalization
4. `:domain` otherwise for project-specific semantic rules, predicates, and
   helpers

This precedence is intentionally operational rather than philosophically pure.

### Working-set counting rules
At each program point `p`, v1 counts coarse facts as follows:
- `LiveBindings(p)` — live local symbols, excluding `_`
- `ActivePredicates(p)` — one per active dominating branch condition
- `MutableEntitiesTracked(p)` — one per mutable cell or semantically tracked
  accumulator in active use
- `ShapeAssumptions(p)` — one per live non-scalar binding whose coarse shape
  matters; not one per key
- `UnresolvedSemantics(p)` — opaque calls at `p` whose correctness meaning is
  not locally obvious, capped at 2 per program point

### Effects taxonomy
V1 distinguishes:
- **pure operations** — no state/effect charge
- **environmental reads** — effectful reads such as time, env vars,
  filesystem/network/database reads, randomness, and deref of non-local
  mutable state
- **external writes** — effectful writes such as filesystem/network/database
  writes, logging, metrics emission, and mutation of non-local state
- **local mutable operations** — charged primarily under `State/Mutation`, not
  `ExternalEffect`, unless their effects escape local scope

### Variant return-shape rule
Branches differ in shape in v1 if:
1. their coarse shape classes differ, or
2. one branch is nil and the other is non-nil, or
3. both are obvious maps with materially different literal keysets, or
4. one branch throws and another returns normally

### Destructuring count rule
For `Destructure(u)`, `k` is the number of newly introduced local symbols:
- include nested leaves
- include `:as`
- include `&` rest bindings
- exclude `_`
- exclude keywords and default expressions in `:or`

### Regularity in v1
`Regularity` is experimental in v1.
It may surface findings where signals are clear, but it should not drive the
headline `lcc-total` score in the first implementation.

### Multi-arity aggregation
Each arity is analyzed independently.
For multi-arity functions, the top-level aggregate uses `:max` by default and
reports both per-arity results and the selected aggregate.

### Parser capability assumption
V1 assumes syntax-first analysis over parsed forms with source location metadata
sufficient for unit extraction and findings attribution. It does not require
macroexpansion-dependent semantic analysis.

### Additional v1 simplifications
- every `when` / `when-not` contributes a discounted flow cost of `0.5`
- recursion detection is limited to self recursion and `letfn`-local mutual
  recursion
- an opaque chain stage is one step in `->`, `->>`, `some->`, `cond->`, one
  function in `comp`, or one step in a fluent/interop chain
- `SemanticJump` counts distinct non-local definitions a reader must consult
  beyond the first; it is not literally about page layout
- severity bands are provisional placeholders for per-unit totals and are not
  cross-project absolutes

---

## Conceptual model

A local unit in v1 is one top-level `defn` arity or one top-level `defmethod`
implementation body.

LCC in v1 has two related forms:

```text
LCC_vector(u) =
  Flow(u)
+ State(u)
+ Shape(u)
+ Abstraction(u)
+ Dependency(u)
+ Regularity(u)
+ WorkingSet(u)

LCC_total_v1(u) = weighted sum of
  Flow(u)
+ State(u)
+ Shape(u)
+ Abstraction(u)
+ Dependency(u)
+ WorkingSet(u)
```

The primary output is a vector:

```clojure
{:flow-burden        n
 :state-burden       n
 :shape-burden       n
 :abstraction-burden n
 :dependency-burden  n
 :regularity-burden  n
 :working-set        {:peak n :avg x.y :burden n}
 :lcc-total          n}
```

This makes the metric explanatory rather than merely judgmental.

---

## Why existing metrics are insufficient

### Cyclomatic Complexity misses
- deep data transformation with little branching
- mutation-heavy straight-line code
- conditional shape variation
- non-local semantic dependence
- abstraction mixing

### Cognitive Complexity improves flow analysis but still misses
- hidden state and temporal coupling
- local data-shape churn
- semantic level oscillation
- helper chasing / opaque indirection
- local irregularity
- peak working-memory demand

A function can be low on cyclomatic/cognitive complexity and still be hard to
change because it interleaves parsing, policy, stateful effects, and ad hoc
shape changes.

---

## Core burdens

## 1. Flow burden

Measures control-flow simulation cost.

Includes:
- branch points
- control nesting
- early exits / interruption
- recursive structure
- dense predicates and boolean chains

This is where cyclomatic and cognitive complexity mostly live.

### Intended interpretation
High flow burden means the reader must simulate many possible paths or hold
multiple active conditions while reading.

---

## 2. State burden

Measures temporal reasoning cost from evolving state.

Includes:
- mutation sites
- rebinding with semantic drift
- read-after-write dependence
- external effectful reads/writes
- aliasing of evolving entities

### Intended interpretation
High state burden means correctness depends on time, sequencing, or hidden
non-local effects rather than pure local transformation.

---

## 3. Shape burden

Measures difficulty of maintaining stable value expectations through the unit.

Includes:
- intermediate shape transitions
- large/nested destructuring
- conditional return-shape variation
- semantic sentinels such as `nil`/`false`/keywords
- deep nested data construction

### Intended interpretation
High shape burden means the reader must repeatedly revise their local model of
what values are, what invariants they satisfy, and what downstream code may
assume about them.

---

## 4. Abstraction burden

Measures loss of coherent local purpose through semantic level mixing.

Includes:
- mixing mechanism, data shaping, domain rules, and orchestration
- repeated transitions between those levels
- incidental concerns embedded inside primary logic

### Intended interpretation
High abstraction burden means the unit lacks one coherent local story. The
reader is forced to jump repeatedly between *what* the unit is doing and *how*
it is doing it, or between primary logic and incidental concerns.

---

## 5. Dependency burden

Measures how much understanding depends on non-local meaning.

Includes:
- non-trivial helper chasing
- opaque pipelines or combinator stacks
- macro/callback inversion of control
- correctness that depends on multiple distant definitions

### Intended interpretation
High dependency burden means the local unit is not locally self-sufficient for
change reasoning; the reader must fetch correctness-critical semantics from
elsewhere before trusting a modification. This is about semantic lookup
distance, not merely the number of helper calls.

---

## 6. Regularity burden

Measures loss of local predictability.

Includes:
- idiom switching
- naming drift
- special-case branch shape breaking a dominant pattern
- mixed error-signaling styles

### Intended interpretation
High regularity burden means local compression fails. Similar things do not
look or behave similarly, so the reader must repeatedly re-learn the local
rules of the unit. This is not meant to enforce surface style conformity for
its own sake.

---

## 7. Working-set burden

Measures peak simultaneous mental load.

This is one of the central ideas in the design.

At each program point, estimate the number of facts a reader must hold in mind:

- live bindings
- active branch predicates
- mutable/evolving entities being tracked
- shape assumptions currently in play
- unresolved helper semantics needed for correctness

Then compute:
- peak working set
- average working set
- derived working-set burden

### Intended interpretation
High working-set burden means too many facts must be actively maintained at
once for comfortable local reasoning, even if the unit is not especially
branch-heavy. This burden often explains why a unit feels mentally crowded even
when conventional flow metrics remain modest.

---

## Formal v1 definitions

The following definitions are intentionally heuristic-friendly. They are meant
to pin down the metric semantically, not to require one particular
implementation strategy.

## Unit model

Represent a local unit `u` conceptually by:

```text
u = (control-structure, bindings, effects, calls, shapes)
```

Where:
- `control-structure` captures the unit's local branching and nesting burden
- `bindings` captures the locally tracked symbols and their active roles
- `effects` captures mutation and externally visible effect burden
- `calls` captures semantically relevant local or non-local operations
- `shapes` captures coarse value-shape expectations and transitions

---

## A. Flow

```text
Flow(u) = Branch(u) + Nest(u) + Interrupt(u) + Recursion(u) + Logic(u)
```

### Branch
Count decision points.

Occurrences include:
- `if`, `if-let`, `if-some`
- `cond`, `condp`
- `case`
- `when`, `when-let`, `when-some`, `when-not`
- `try` with multiple `catch`
- boolean operators beyond the first inside a condition

Default weights:
- `if`-like form: `1`
- `when`-like form: `0.5`
- each additional `cond`/`case` alternative group: `1`
- each extra `catch`: `1`
- each boolean connective beyond the first in a condition: `0.5`

### Nest
For each control form at control nesting depth `d`:

```text
Nest(u) = Σ max(0, d - 1)
```

### Interrupt
Count flow interruptions:
- early return equivalents
- `throw`
- `reduced`
- non-terminal `recur`

```text
Interrupt(u) = count(interruptions)
```

### Recursion
```text
Recursion(u) =
  1 if self-recursive
  2 if mutual recursion detected locally
  0 otherwise
```

### Logic
Dense predicates cost extra.

Add:
- `+0.5` per boolean connective beyond one
- `+0.5` for compound negation over non-trivial predicate
- `+0.5` for nested predicate structure

---

## B. State

```text
State(u) = Mutation(u) + Rebinding(u) + Temporal(u) + ExternalEffect(u) + Alias(u)
```

### Mutation
```text
Mutation(u) = 2 * count(mutation-sites)
```

### Rebinding
Charge for semantic-role-changing rebindings and accumulator-style drift.

```text
Rebinding(u) = count(meaningful-rebindings)
```

### Temporal
Charge when correctness depends on update/read ordering.

```text
Temporal(u) = count(temporally-significant dependencies)
```

### ExternalEffect
Approximate non-local effect cost as:

```text
ExternalEffect(u) = 1 * effectful-reads + 2 * effectful-writes
```

### Alias
Optional in v1 when inferable:

```text
Alias(u) = count(alias-relations over evolving entities)
```

When aliasing is not inferable in v1, this term is treated as `0`.

---

## C. Shape

```text
Shape(u) = Transition(u) + Destructure(u) + Variant(u) + Nesting(u) + Sentinel(u)
```

### Transition
Charge for non-trivial value-shape transitions on the main path.

```text
Transition(u) = count(nontrivial shape transitions)
```

The purpose of this charge is to reflect repeated revision of local value
expectations, not to penalize transformation as such.

### Destructure
For each destructure form introducing `k` bindings:

```text
Destructure(u) = Σ max(0, k - 3) * 0.5
```

### Variant
Conditionally varying value/return shapes:

```text
Variant(u) = 2 * count(shape-variant branches)
```

### Nesting
Charge for deep nested data construction:

```text
Nesting(u) = count(data-construction sites with depth > 2)
```

### Sentinel
Charge for sentinel-driven semantics:

```text
Sentinel(u) = count(sentinel-based distinctions)
```

---

## D. Abstraction

```text
Abstraction(u) = LevelMix(u) + Oscillation(u) + Incidental(u)
```

### Abstraction levels
Each relevant form is classified heuristically as one dominant level:

- `L0` — mechanism / plumbing / interop / indexing
- `L1` — data shaping / traversal / parsing
- `L2` — domain rule / business meaning
- `L3` — orchestration / workflow coordination

### LevelMix
Let `levels(u)` be the set of semantic levels present.

```text
LevelMix(u) = max(0, |levels(u)| - 1) * 2
```

### Oscillation
Count transitions between adjacent semantic levels on the main path.

```text
Oscillation(u) = count(level transitions after the first)
```

This is one of the most valuable new signals in the design because repeated
level switching often destroys a unit's coherent local purpose.

### Incidental
Charge for incidental concerns embedded in core logic:
- logging
- tracing
- metrics
- retry/cache mechanics
- formatting

```text
Incidental(u) = count(incidental concern insertions)
```

---

## E. Dependency

```text
Dependency(u) = Helper(u) + OpaqueChain(u) + Inversion(u) + SemanticJump(u)
```

### Helper
```text
Helper(u) = count(nontrivial helper call sites)
```

This is a syntactic presence count: how many non-trivial helper calls appear in
the unit. It is only a proxy for non-local semantic dependence, not the main
concept itself.

### OpaqueChain
For pipelines/chains with opaque stages:

```text
OpaqueChain(u) = max(0, opaque-stages - 2)
```

A stage in v1 is:
- one step in `->`, `->>`, `some->`, or `cond->`
- one function in `comp`
- one step in a fluent or interop chain

A stage is opaque when its operator is not transparent and its meaning is not
obvious from local syntax/name.

In v1, `OpaqueChain` is stage-level opacity. `Helper` counts non-trivial call
presence more generally. Helper call sites already charged as opaque chain
stages should not be charged again under `Helper` for the same occurrence.

### Inversion
Callbacks, higher-order inversion, or macro-driven hidden flow:

```text
Inversion(u) = 2 * count(nontrivial inversions)
```

### SemanticJump
Approximate correctness-critical non-local jumps:

```text
SemanticJump(u) = max(0, jumps-required - 1)
```

In v1, this approximates the number of distinct non-local definitions a reader
must consult to validate correctness beyond the first. Unlike `Helper`, which
counts call-site presence, `SemanticJump` models lookup burden across distinct
definitions. These may be:
- same-namespace helper vars
- project vars in other namespaces
- non-transparent library helpers
- non-obvious macro semantics

---

## F. Regularity

```text
Regularity(u) = IdiomSwitch(u) + NamingDrift(u) + PatternBreak(u) + ErrorStyleMix(u)
```

### IdiomSwitch
```text
IdiomSwitch(u) = count(idiom switches)
```

### NamingDrift
```text
NamingDrift(u) = count(naming inconsistencies)
```

### PatternBreak
```text
PatternBreak(u) = count(pattern violations)
```

### ErrorStyleMix
```text
ErrorStyleMix(u) = count(mixed error signaling styles)
```

---

## G. Working set

At each program point `p`, define:

```text
WS(p) = LiveBindings(p)
      + ActivePredicates(p)
      + MutableEntitiesTracked(p)
      + ShapeAssumptions(p)
      + UnresolvedSemantics(p)
```

Then:

```text
PeakWS(u) = max_p WS(p)
AvgWS(u)  = average_p WS(p)
WorkingSet(u) = max(0, PeakWS(u) - 4)
              + 0.5 * max(0, AvgWS(u) - 3)
```

The thresholds are heuristic allowances for normal chunking.

---

## Composite rollup

A weighted rollup is useful for ranking, but should remain secondary to the
burden vector.

```text
LCC_total(u) =
  1.0 * Flow(u)
+ 1.3 * State(u)
+ 1.2 * Shape(u)
+ 1.4 * Abstraction(u)
+ 1.1 * Dependency(u)
+ 1.5 * WorkingSet(u)
```

`Regularity` is intentionally excluded from the headline v1 rollup while it
remains experimental.

### Rationale
The weights intentionally emphasize:
- working-set burden
- abstraction burden
- state burden

These are often more predictive of local change difficulty than control flow
alone.

---

## Severity bands

Initial reporting bands:

```text
0–7    low
8–15   moderate
16–25  high
26+    very high
```

These are provisional and should be calibrated against a benchmark corpus.
They apply to the headline v1 `lcc-total`, not directly to each burden
dimension.

---

## Findings model

LCC should emit findings, not only numeric scores.

Example shape:

```clojure
{:kind :abstraction-oscillation
 :severity :high
 :score 7
 :message "Domain rules and parsing/plumbing alternate 5 times in one function"
 :evidence {...}}
```

Suggested finding kinds:
- `:deep-control-nesting`
- `:predicate-density`
- `:mutable-state-tracking`
- `:temporal-coupling`
- `:shape-churn`
- `:conditional-return-shape`
- `:abstraction-mix`
- `:abstraction-oscillation`
- `:opaque-pipeline`
- `:helper-chasing`
- `:idiom-switching`
- `:working-set-overload`

This aligns the feature with Gordian’s existing finding-oriented style.

---

## Conceptual unit model

The metric is defined over local units such as:
- each top-level `defn` arity
- each top-level `defmethod` body

Nested local helpers are conceptually folded into the enclosing top-level unit
for v1 rather than treated as standalone local units.

For multi-arity functions:
- each arity is a distinct local unit for analysis
- any higher-level summary may treat the function as an aggregate over arities

---

## Clojure-oriented heuristic interpretation

Analyze in v1:
- each top-level `defn` arity separately
- each top-level `defmethod` body

Do not emit standalone local-unit records in v1 for:
- anonymous `fn`
- `letfn` locals
- other nested helpers

These nested forms contribute to the enclosing top-level unit instead.

For multi-arity fns:
- report each arity independently
- provide aggregate as `:max` by default

---

## Clojure-specific heuristics

## 1. Flow heuristics

Treat as control/decision forms:
- `if`, `if-let`, `if-some`
- `when`, `when-let`, `when-some`, `when-not`
- `cond`, `condp`, `case`
- `and`, `or` in condition contexts
- `some->`, `some->>` as conditional propagation
- `try`/`catch`
- `loop`/`recur`
- explicit recursion
- branching reducers or nested control-bearing lambdas

Discount forms that usually flatten rather than obscure flow:
- `if-let`
- `when-let`
- `some->`
- `cond->` / `cond->>`

In v1, every `when` / `when-not` contributes a discounted flow cost of `0.5`.

The design should avoid penalizing idioms that remove duplication or flatten
nesting.

---

## 2. State heuristics

Detect local mutation/effect patterns such as:
- `swap!`, `reset!`, `compare-and-set!`
- `vswap!`, `vreset!`
- `set!`, `alter-var-root`
- transient mutation (`assoc!`, `conj!`, `persistent!`)
- Java mutator interop
- state cell creation inside unit (`atom`, `volatile!`, `ref`, `agent`)

Charge extra when:
- a stateful cell is updated then later read for semantics
- multiple accumulators are carried through `loop/recur`
- the same symbol is rebound through several semantic phases

---

## 3. Shape heuristics

Infer coarse shape classes such as:
- scalar
- map
- vector
- set
- seq-like
- nilable-X
- record/object when obvious

Detect shape changes across bindings and major stages.

Examples of shape-bearing operators:
- map-like: `assoc`, `dissoc`, `merge`, `update`, `select-keys`, `group-by`, `frequencies`
- seq-like: `map`, `filter`, `keep`, `remove`, `keys`, `vals`
- collection concretizers: `into`, `vec`, `set`, `mapv`

Charge for:
- repeated shape transitions
- large destructuring
- map/keyset variation across branches
- sentinel-driven branching semantics

This is a strong fit for Clojure because local code often consists of data
transformation pipelines.

---

## 4. Abstraction heuristics

Heuristically label forms as one dominant level:

### `:mechanism`
Examples:
- interop
- file/path/string/regex mechanics
- mutable state operations
- low-level parsing detail

### `:orchestration`
Examples:
- wiring multiple stages/subsystems
- top-level workflow sequencing

### `:data-shaping`
Examples:
- `map`, `reduce`, `filter`, `group-by`, `assoc`, `merge`, `update`
- structural transformation and normalization

### `:domain`
Examples:
- project-specific domain rules and predicates
- domain-specific helper calls

Then detect:
- number of levels present
- transition count between levels
- incidental concerns embedded inside main logic

Abstraction oscillation should be treated as a first-class finding source.

---

## 5. Dependency heuristics

Classify calls as:
- transparent core op
- non-trivial core combinator
- local helper
- same-namespace helper
- project helper in another namespace
- library helper
- macro with hidden flow

Dependency burden rises with:
- non-trivial helper count
- opaque pipeline stages
- callback-heavy inversion
- correctness dependence on several non-local definitions

The design should distinguish **transparent composition** from **semantic
indirection**. A pipeline of obvious operations is better than a shorter but
opaque helper stack.

---

## 6. Regularity heuristics

Look for local inconsistency such as:
- mixed threading and nested-call idioms for the same role
- mixed access styles (`(:k m)` vs `get` vs `find`) without reason
- naming drift for same semantic role
- one-off branch that breaks clause symmetry
- mixed signaling styles (`nil`, exception, tagged map, boolean)

Regularity is a low-level but important compression mechanism.

---

## 7. Working-set heuristics

Approximate program points at:
- `let` / `loop` bindings
- branch entry points
- top-level threaded pipeline stages
- `do` subforms
- clause bodies in `cond` / `case`

Estimate at each point:
- live bindings
- active predicates
- mutable entities tracked
- shape assumptions still in play
- unresolved helper semantics required for correctness

Then report:
- `:peak`
- `:avg`
- `:peak-location`
- component contribution breakdown where practical

In v1, one live non-scalar binding contributes one shape assumption regardless
of how many keys or fields it may imply.

This is likely to be the highest-value new metric in the feature.

---

## Conceptual result shape

The metric conceptually yields:
- a burden vector
- an optional headline rollup for ranking
- findings that explain the main burden sources
- evidence categories sufficient to justify those findings

For v1, `regularity-burden` may remain visible even when excluded from the
headline rollup.

---

## Interpretive summary

LCC is intended to characterize local units that are difficult to safely
understand and modify under bounded local reasoning.

Low-burden units tend to exhibit:
- one coherent local purpose
- stable value expectations and invariants
- bounded simultaneous mental load
- explicit and limited state/effect reasoning
- mostly local semantic legibility
- predictable internal structure

High-burden units tend to exhibit:
- entangled concerns or semantic level mixing
- repeated revision of value expectations
- hidden temporal dependence
- correctness meaning distributed across too many external definitions
- overloaded working memory demands
- locally inconsistent structures that resist compression

This makes LCC an explanatory metric for safe local change reasoning, not
merely another path count or size metric.

---

## Calibration plan

The raw formulas and weights should be treated as initial defaults, not final
truth.

Calibration should use a benchmark corpus of local functions classified by
human judgment, including examples such as:
- low-branch but hard due to state or abstraction mixing
- moderate-branch but easy due to flat shape and good decomposition
- transformation-heavy shape churn
- callback/macro-heavy local inversion
- well-shaped pure pipelines

This should tune:
- threshold bands
- burden weights
- syntax discounts
- false-positive controls

---

## Resolved v1 decisions

### 1. Nested local functions
Anonymous local `fn`s and other nested helpers remain folded into the enclosing
parent unit in v1.

Rationale:
- LCC v1 is defined around the burden of safely understanding and changing a
  top-level local unit
- nested helpers often exist to serve that enclosing unit's local story
- treating them as standalone units too early would blur the unit model and
  shift the metric toward finer-grained reporting before the core concept is
  fully stabilized

A later version may revisit standalone reporting for nested units, but that is
not part of the v1 metric definition.

### 2. Abstraction-level inference aggressiveness
Abstraction-level inference should be conservative in v1.

V1 should prefer:
- clear, high-confidence level distinctions
- under-classification rather than speculative classification
- stable conceptual categories over project-specific nuance

The purpose of abstraction burden in v1 is to detect meaningful loss of
coherent local purpose, not to perform maximal semantic labeling.

### 3. Map-shape and keyset sensitivity
V1 includes only coarse map-shape or keyset sensitivity.

The metric should treat shape variation as conceptually important when it
changes local value expectations in an obvious way, especially:
- nil vs non-nil distinctions
- map vs non-map distinctions
- materially different literal keysets across branches when they are locally
  evident

It should not require fine-grained inferred keyset semantics everywhere. The
metric's purpose is to capture instability of local invariants, not to become a
full structural type system.

### 4. Essential vs provisional dependency burden
Essential dependency burden in v1 includes:
- semantic lookup distance
- opaque helper chasing
- opaque chain stages
- inversion of control that hides correctness-relevant flow

Secondary or provisional dependency aspects include:
- deep cross-unit semantic reconstruction
- rich library-specific semantic modeling
- project-specific transparency registries

Dependency burden is therefore anchored in non-local meaning required for safe
change reasoning, not in raw call counts or generic coupling.

### 5. Severity-band interpretation
Severity bands in v1 are interpretive heuristics for ranking and discussion,
not cross-project absolutes.

They should be read as:
- relative indicators of local change burden within a codebase or comparison set
- useful defaults for triage
- subject to later calibration

The burden vector and findings remain primary. Severity bands summarize the
headline rollup, but they do not replace interpretation of the underlying
burden dimensions.

---

## Recommendation

Proceed with **LCC v1** as a local, burden-vector-based complexity model.

The key innovations over existing metrics are:
- **working-set burden**
- **abstraction oscillation / abstraction burden**
- **shape burden**
- **explicit state burden**

Cyclomatic and Cognitive Complexity remain useful, but only as part of flow
burden rather than the whole local story.

In short:

> Gordian should measure not just how many paths a function has, but how much
> mind it takes to safely understand and change one.
