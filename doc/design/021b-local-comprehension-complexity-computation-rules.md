# 021b — Local Comprehension Complexity computation rules

This document refines `021-local-comprehension-complexity.md` into a more
implementation-ready v1 computation specification.

Purpose:
- preserve the semantic intent of the LCC metric
- make burden-family computation rules explicit enough for implementation
- reduce algorithmic ambiguity without overreaching into full semantic analysis
- provide burden-by-burden counting rules, exclusions, anti-double-counting constraints, and initial finding triggers

This document is subordinate to `021-local-comprehension-complexity.md`:
- `021` defines the metric semantically
- `021b` defines the preferred v1 computation rules for implementing it

---

## Global v1 principles

### Syntax-first, deterministic, conservative

V1 computation is syntax-first and deterministic.

Prefer:
- explicit local evidence
- conservative under-counting to speculative over-counting
- stable repeatable scoring over semantic cleverness

### Primary evidence ownership

Each evidence event has one primary burden owner:
- flow evidence → `Flow`
- state/effect sequencing evidence → `State`
- shape transition/destructuring/variant evidence → `Shape`
- semantic-level/incidental evidence → `Abstraction`
- opacity / semantic lookup / inversion evidence → `Dependency`
- predictability/inconsistency evidence → `Regularity`
- simultaneous active-fact evidence → `WorkingSet`

`WorkingSet` remains derivative:
- it may reuse already-discovered evidence
- it should not create independent primary evidence classes that also directly charge another burden

### Branch merge rules

When branch-local evidence is merged back into the enclosing unit:
- evidence collections merge by union unless a burden-family rule says otherwise
- branch-return shape differences may create explicit `Variant` evidence for `Shape`
- branch-local findings are emitted at unit scope when their supporting evidence survives merge

Burden-family merge defaults in v1:
- `Flow` is computed structurally from the traversed control forms, not by taking a branch-local `max`
- `State`, `Dependency`, and `Shape` consume merged evidence and apply their own counting rules
- branch-local scalar summaries used only for finding support may merge by `max` where appropriate

### Main-path rule

Unless a burden family says otherwise, sequence-based computations run over the unit’s main path:
- top-level function body steps
- top-level `let` bindings and final body
- top-level `do` subforms
- stages in `->`, `->>`, `some->`, `cond->`

The main path is an approximation of primary local reading order, not a full CFG.

---

## Unit preprocessing and shared evidence

Before burden scoring, the implementation should extract a shared evidence model
for each local unit.

Minimum shared evidence categories:
- unit identity / provenance
- main-path steps
- branch regions
- control forms with nesting depth
- call sites and operator classification
- bindings and rebinding relations
- mutable/effectful operations
- coarse shape observations
- candidate program points for working-set estimation

This evidence model need not be publicly exposed as-is, but burden-family
scoring should be expressed in terms of it.

---

## 1. Flow burden computation rules

```text
Flow(u) = Branch(u) + Nest(u) + Interrupt(u) + Recursion(u) + Logic(u)
```

### Inputs
- control forms
- condition expressions
- branch structure
- recursion evidence
- interruption forms

### Counted events

#### Branch
Count these decision forms:
- `if`, `if-let`, `if-some` → `+1`
- `when`, `when-not`, `when-let`, `when-some`, `when-first` → `+0.5`
- `cond` → `+1` per clause pair
- `condp` → `+1` per clause pair plus default when present
- `case` → `+1` per branch arm plus default when present
- each `catch` clause beyond the first → `+1`

`Branch` counts control-form structure only. Predicate-density extras belong under `Logic`, not `Branch`.

#### Nest
For each control-bearing form at nesting depth `d`:
- add `max(0, d - 1)`

Control-bearing forms for nesting:
- `if` family
- `when` family
- `cond`, `condp`, `case`
- `try` / `catch`
- looping/recursive branch structures when they create nested control regions

#### Interrupt
Count:
- `throw`
- `reduced`
- non-terminal `recur`

Do not attempt broad “early return equivalent” inference in v1 beyond explicit forms.

#### Recursion
- self-recursion detected within the same local unit → `+1`
- local mutual recursion in `letfn` → `+2`
- otherwise `0`

#### Logic
Add:
- `+0.5` per boolean connective beyond one in a condition
- `+0.5` for compound negation over a non-trivial predicate
- `+0.5` for nested predicate structure inside a condition

### Exclusions
Do not independently add flow cost for:
- `loop` / `recur` as such, beyond explicit recursion/interruption rules
- `for`, `doseq`, `while`
- threading macros by themselves
- higher-order sequence calls by themselves

### Finding triggers
- `:deep-control-nesting` when maximum nesting depth exceeds a conservative v1 default threshold (`>= 3` counted nesting levels)
- `:predicate-density` when logic contribution or condition complexity exceeds a conservative v1 default threshold

---

## 2. State burden computation rules

```text
State(u) = Mutation(u) + Rebinding(u) + Temporal(u) + ExternalEffect(u) + Alias(u)
```

### Inputs
- mutation/effectful forms
- binding timeline
- state-cell creation and use
- read-after-write relations

### Counted events

#### Mutation
Count explicit local mutation/update sites, weight `2` each:
- `swap!`, `reset!`, `compare-and-set!`
- `vswap!`, `vreset!`
- `set!`
- transient updates such as `assoc!`, `conj!`
- obvious Java mutator interop against locally scoped mutable entities

State-cell creation alone (`atom`, `volatile!`, `ref`, `agent`) does not count as mutation by itself, but it creates a mutable entity that may contribute to temporal reasoning and working-set tracking.

Precedence rule:
- if the mutated target is local to the unit, charge `Mutation`
- if the mutated target is non-local, escaping, global, or externally visible, charge `ExternalEffect`
- do not charge both `Mutation` and `ExternalEffect` for the same site in v1

#### Rebinding
Count `+1` for each meaningful rebinding.

V1 meaningful rebinding rule:
- a local symbol is rebound later in the same unit
- and the rebound value changes the symbol’s coarse role in a way a reader must notice to keep reasoning straight

Count as meaningful rebinding when one or more of these holds:
- coarse shape class changes
- nilability status changes materially
- the symbol is rebound multiple times on the main path and clearly shifts from source/input role to accumulator/result carrier
- the symbol is rebound across a major main-path stage boundary and no longer preserves the same obvious coarse role

Do not count:
- fresh bindings of new symbols
- trivial shadowing in nested locals that does not survive to outer reasoning
- rebinding that preserves the same obvious coarse role and shape

#### Temporal
Count `+1` for each temporally-significant dependency.

V1 temporally-significant dependency rule:
- correctness of a later step depends on a prior update/write having happened earlier in the unit
- and that dependency is not just lexical data flow through immutable values

Count obvious cases such as:
- mutate state cell then later read/deref it for semantics
- update loop/accumulator state whose value controls later branching or returned meaning
- multiple mutable entities whose relative update/read ordering matters

#### ExternalEffect
Count:
- effectful read → `+1`
- effectful write → `+2`

Effectful reads include obvious operations such as:
- current time
- env vars
- filesystem/network/database reads
- randomness
- deref of non-local mutable state

Effectful writes include obvious operations such as:
- filesystem/network/database writes
- logging / metrics emission
- mutation of non-local state

#### Alias
V1 initial implementation rule:
- default `Alias(u) = 0`
- only count aliasing later if a clearly conservative explicit alias rule is added

### Exclusions
Do not count under `State`:
- plain immutable data transformation
- helper calls solely because they are non-local
- shape changes unless they arise from mutation/rebinding evidence

### Finding triggers
- `:mutable-state-tracking` when explicit mutation sites or mutable entities tracked exceed a conservative threshold
- `:temporal-coupling` when temporal dependencies are detected

---

## 3. Shape burden computation rules

```text
Shape(u) = Transition(u) + Destructure(u) + Variant(u) + Nesting(u) + Sentinel(u)
```

### Inputs
- coarse shape observations per step/binding
- destructuring forms
- branch return shapes
- nested literal/data construction
- sentinel-driven branch logic

### Coarse shape classes
Use only coarse shape classes in v1:
- scalar
- map
- vector
- set
- seq-like
- nilable-X
- record/object when obvious
- unknown

### Counted events

#### Transition
Count `+1` for each non-trivial shape transition on the main path.

V1 non-trivial shape transition rule:
- coarse shape class changes, or
- nilability meaning changes materially, or
- a value enters/leaves an obvious sentinel-governed state, or
- a map-like value undergoes an obvious locally visible keyset/invariant shift, limited in v1 to cases such as literal-key `assoc`, `dissoc`, `select-keys`, or branch-variant literal keysets that change what downstream code may assume

Do not count every map→map or seq→seq transformation automatically.
Count only when local value expectations materially change.

#### Destructure
For each destructuring form introducing `k` new local symbols:
- add `max(0, k - 3) * 0.5`

Use the normative count rule from `021`.

#### Variant
Count `+2` for each shape-variant branch outcome.

Use the normative variant rule from `021`:
- different coarse shape classes
- nil vs non-nil
- materially different obvious literal keysets
- throw vs normal return

#### Nesting
Count `+1` for each data-construction site with nesting depth greater than `2`.

V1 depth rule:
- compute literal/construction nesting only for obvious nested collection/map construction
- do not attempt deep semantic reconstruction through arbitrary helper calls

#### Sentinel
Count `+1` for each sentinel-based distinction that materially affects local reasoning.

Count obvious sentinel use involving:
- `nil`
- `false`
- literal keywords / tags used directly in branch conditions, equality checks, `case`, or `cond` dispatch as control-bearing distinctions

### Exclusions
Do not count:
- plain transformation chains that preserve coarse value expectations
- speculative keyset inference beyond obvious literal or immediately local evidence
- shape evidence already charged solely as state mutation unless it also changes local value expectations independently

### Finding triggers
- `:shape-churn` when `Transition` exceeds a conservative v1 default threshold (`>= 3`)
- `:conditional-return-shape` when `Variant >= 2`

---

## 4. Abstraction burden computation rules

```text
Abstraction(u) = LevelMix(u) + Oscillation(u) + Incidental(u)
```

### Inputs
- main-path steps
- dominant abstraction label per step
- incidental concern evidence

### Step classification rule
In v1, classify abstraction at the level of main-path steps, not arbitrary subexpressions.

Each step receives one dominant abstraction label using the precedence defined in `021`:
1. `:mechanism`
2. `:orchestration`
3. `:data-shaping`
4. `:domain`

This avoids overreacting to small embedded subexpressions.

### Counted events

#### LevelMix
Let `levels(u)` be the set of labels present across main-path steps.

```text
LevelMix(u) = max(0, |levels(u)| - 1) * 2
```

#### Oscillation
Traverse the main-path step label sequence and count transitions between adjacent differing levels after the first transition.

```text
Oscillation(u) = count(adjacent step-level transitions after the first)
```

#### Incidental
Count `+1` for each incidental concern insertion visible on the main path, such as:
- logging
- tracing
- metrics
- retry/cache behavior
- formatting / presentation concerns embedded in core logic

### Exclusions
Do not count:
- internal subexpression variation inside a step when the step still has one clear dominant level
- speculative domain classification based on weak naming evidence

### Finding triggers
- `:abstraction-mix` when three or more abstraction levels are present or when level mix exceeds a conservative v1 default threshold
- `:abstraction-oscillation` when oscillation exceeds a conservative v1 default threshold (`>= 3` transitions)

---

## 5. Dependency burden computation rules

```text
Dependency(u) = Helper(u) + OpaqueChain(u) + Inversion(u) + SemanticJump(u)
```

### Inputs
- call sites
- helper/operator classification
- chain structures
- callback/macro inversion forms
- distinct non-local definition references

### Counted events

#### Helper
Count `+1` for each non-trivial helper call site not already charged as an opaque chain stage.

Non-trivial helper rule:
- helper/operator is not in the transparent set
- and its semantics are not locally obvious from syntax alone

#### OpaqueChain
For each chain/pipeline, count opaque stages and add:

```text
OpaqueChain(u) = max(0, opaque-stages - 2)
```

Opaque stage rule uses the definition from `021`.

#### Inversion
Count `+2` for each non-trivial inversion.

V1 inversion rule:
- obvious callback-style control transfer
- macro or combinator that hides execution order or control structure in a way that matters for correctness reasoning

Do not infer inversion broadly from any higher-order function. Count only obvious high-signal cases.

#### SemanticJump
Count distinct non-local definitions a reader would need beyond the first to validate correctness meaning:

```text
SemanticJump(u) = max(0, jumps-required - 1)
```

V1 conservative rule for `jumps-required`:
- count distinct non-transparent helper/macro/library definitions referenced on the main path
- bucket by definition identity when known; otherwise bucket by operator symbol identity rather than raw call-site identity
- do not attempt transitive semantic reconstruction across the call graph

A single non-transparent helper occurrence may contribute to both:
- `Helper`, as local opaque/helper presence
- `SemanticJump`, as distinct lookup breadth

These are intentionally different signals and should not be fully deduplicated.

### Exclusions
Do not count:
- transparent helpers
- helper call sites already charged as opaque chain stages
- raw call count without opacity/non-local semantic significance

### Finding triggers
- `:opaque-pipeline` when an opaque chain exceeds a conservative v1 default threshold (`>= 3` opaque stages)
- `:helper-chasing` when helper count plus semantic jump exceeds a conservative v1 default threshold

---

## 6. Working-set burden computation rules

```text
WS(p) = LiveBindings(p)
      + ActivePredicates(p)
      + MutableEntitiesTracked(p)
      + ShapeAssumptions(p)
      + UnresolvedSemantics(p)
```

### Program points
Sample only these program points in v1:
- after each top-level `let` / `loop` binding group
- at branch entry points
- at each top-level threaded pipeline stage
- at each `do` subform on the main path
- at each `cond` / `case` clause body entry

Do not attempt CFG-complete program-point coverage.

### Component rules

#### LiveBindings(p)
- count live local symbols in lexical scope at `p`
- exclude `_`

#### ActivePredicates(p)
- count one per active dominating branch condition at `p`

#### MutableEntitiesTracked(p)
- count one per mutable cell or semantically tracked accumulator still relevant at `p`

#### ShapeAssumptions(p)
- count one per live non-scalar binding whose coarse shape still matters at `p`
- in v1, assume a live non-scalar binding still matters unless it is clearly dead, shadowed, or no longer lexically in scope
- do not count per key/field

#### UnresolvedSemantics(p)
- count opaque calls/operations in the step associated with `p` whose correctness meaning is not locally obvious
- cap at `2` per program point

### Aggregate rule
Compute:

```text
PeakWS(u) = max_p WS(p)
AvgWS(u)  = average_p WS(p)
WorkingSet(u) = max(0, PeakWS(u) - 4)
              + 0.5 * max(0, AvgWS(u) - 3)
```

### Exclusions
Do not attempt:
- full liveness analysis beyond lexical approximation
- semantic stack modeling beyond the five defined components

### Finding triggers
- `:working-set-overload` when `PeakWS` or derived working-set burden exceeds a conservative v1 default threshold (`PeakWS >= 7` or derived burden `>= 3`)

---

## 7. Regularity burden

`Regularity` remains out of the first executable slice unless a clearly
conservative, high-signal implementation falls out naturally after the core
burden families are complete.

Initial payload rule:
- omit `regularity-burden` entirely from first-slice machine-readable payloads unless it is actually implemented

If implemented later, it should follow the same pattern:
- counted events
- exclusions
- anti-double-counting constraints
- conservative finding triggers

---

## 8. LCC total and severity

### Headline rollup
Use the v1 weighted total from `021`:

```text
LCC_total(u) =
  1.0 * Flow(u)
+ 1.3 * State(u)
+ 1.2 * Shape(u)
+ 1.4 * Abstraction(u)
+ 1.1 * Dependency(u)
+ 1.5 * WorkingSet(u)
```

Exclude `Regularity` from the initial v1 total.

### Severity bands
Use the provisional v1 bands from `021`:
- `0–7` low
- `8–15` moderate
- `16–25` high
- `26+` very high

Treat these as interpretive defaults, not cross-project absolutes.

---

## 9. Initial implementation guidance

If implementation sequencing must prioritize, the highest-value order is:
1. shared evidence extraction
2. Flow
3. Abstraction
4. Shape
5. Dependency
6. State
7. Working-set

This is not the conceptual importance ordering; it is the suggested order for
reaching a stable, conservative first implementation.

`WorkingSet` comes later because its scoring depends on evidence already needed
for bindings, mutable entities, shape assumptions, and unresolved semantics.

If state burden is implemented earlier, its rebinding and temporal rules should
be kept especially narrow until examples justify widening them.
