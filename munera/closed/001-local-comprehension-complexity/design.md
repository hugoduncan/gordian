Define Gordian Local Comprehension Complexity (LCC) as a metric design.

## Intent

- define a new Gordian local-analysis metric for code in the small
- measure local comprehension burden rather than only path count or code size
- complement, not replace, `gordian complexity`
- converge on a clear, rigorous, reviewable definition of the metric before implementation work begins
- use the `code-shaper` lens to guide the metric so it rewards simplicity, consistency, robustness, and local comprehensibility

`gordian complexity` answers questions such as:
- which local units are branch-heavy?
- which local units are large?

LCC should answer a different question:
- which local units are hard to understand and safely change, and why?

The metric should be guided by the `code-shaper` lens:
- simple code should tend toward lower local comprehension burden
- consistent code should tend toward lower local comprehension burden
- robust code should tend toward lower local comprehension burden because it preserves orthogonality and enforceable invariants
- locally comprehensible code should be understandable primarily from local source

## Problem statement

Gordian is strong at architecture-scale analysis and already has a lightweight local metric command via `gordian complexity`. What it does not yet have is a metric definition for local comprehension burden.

Cyclomatic complexity and LOC are useful but incomplete. They under-measure units that are difficult because they:
- mix abstraction levels
- require tracking many live concepts at once
- churn data shape repeatedly
- hide temporal/stateful reasoning
- depend on non-local semantic lookup
- remain locally irregular despite modest branching

The core problem of this task is therefore conceptual, not implementation-oriented:
- define what Local Comprehension Complexity is
- define what it measures and does not measure
- define its dimensions and how they relate
- define its intended interpretation
- define the boundaries and simplifications required for a coherent first version of the metric

The design basis is `doc/design/021-local-comprehension-complexity.md`, which is the main source document for the metric definition. This task exists to refine and converge that metric definition itself.

## Scope

### In scope

- define the metric’s intent and question clearly
- define the conceptual dimensions of Local Comprehension Complexity
- define the relationships between burden dimensions and any aggregate/rollup notion
- define the canonical unit(s) of analysis for the metric
- define the normative boundaries, assumptions, and simplifications of the metric’s first version
- define what evidence belongs primarily to which burden dimension
- define what kinds of findings or interpretations the metric is meant to support
- make explicit how the metric reflects `code-shaper` qualities:
  - simplicity
  - consistency
  - robustness
  - local comprehensibility
- define what is intentionally excluded from the metric
- resolve ambiguities or overlaps in the metric definition
- produce a stable metric definition suitable for later implementation planning

### Out of scope

- implementation plans
- parser/tooling choice
- module boundaries
- data-flow design
- CLI design
- output/rendering design
- report schema design
- code sequencing / small-commit implementation planning
- validation of a specific implementation strategy
- compare/gate/workflow integration

### Explicitly deferred

- how the metric will be implemented
- what parser or analyzer should be used
- what command surface will expose the metric
- what machine-readable payload shape should be emitted
- what parts of the metric should be approximated first in code
- calibration against a corpus as part of implementation rollout

## Non-goals

- do not merge LCC into `gordian complexity`
- do not prematurely constrain the metric definition to fit one implementation approach
- do not turn this task into parser, CLI, or architecture design
- do not optimize for implementation convenience at the expense of metric clarity
- do not define a generic local-metric framework

## Acceptance criteria

This task is complete when the metric definition is converged clearly enough that a later implementation-planning task can begin without re-deciding the meaning of the metric.

### Metric intent

- the metric’s core question is stated clearly
- the metric is positioned clearly relative to cyclomatic complexity, cognitive complexity, and LOC
- the intended interpretation of the metric is stated clearly

### Conceptual structure

- the burden dimensions are defined clearly
- the role of each burden dimension is distinguished from the others
- overlaps between dimensions are resolved or bounded explicitly
- the role of any aggregate or rollup score is defined relative to the burden vector

### Unit model

- the canonical unit(s) of analysis are defined conceptually
- treatment of nested local helpers is defined conceptually for the metric version being specified
- any aggregation across multiple related units is defined conceptually if included

### Normative boundaries

- the first-version simplifications and assumptions are stated explicitly
- what the metric does not attempt to capture is stated explicitly
- evidence ownership across burden dimensions is stated explicitly enough to avoid uncontrolled double counting

### Interpretive output

- the kinds of findings or interpretations the metric should support are defined conceptually
- the relationship between burden vector, findings, and any headline score is stated clearly
- provisional severity/interpretation bands are defined only as part of the metric meaning, not implementation detail

### Task output

- the metric definition is captured in a form suitable to act as a normative source for later implementation planning
- conceptual questions material to the v1 metric definition are answered or explicitly resolved as deferred beyond v1

## Minimum concepts

- **local comprehension complexity** — the mental burden required to understand and safely change a local unit
- **local unit** — the conceptual thing to which the metric applies
- **burden family** — one dimension of local comprehension cost
- **burden vector** — the explanatory multi-dimensional result
- **headline rollup** — any secondary aggregate score used for ranking
- **finding** — an interpretation the metric is designed to support
- **working set** — simultaneous mental load carried during local reasoning
- **main path** — the primary local sequence of reasoning used by the metric
- **normative simplification** — an intentional first-version boundary that keeps the metric coherent

## Design questions this task must answer

A good metric definition for LCC should answer the following.

### 1. What is the metric’s central question?

The metric should sharply answer:
- what kind of local difficulty it measures
- why that differs from branch count, size, or style metrics

### 2. What are the minimum burden dimensions?

The design should identify the minimum dimensions that explain local comprehension burden without introducing unnecessary overlap or conceptual noise.

### 3. What belongs to each burden dimension?

The design should define the intended semantic territory of each dimension, such as:
- flow
- state
- shape
- abstraction
- dependency
- regularity
- working-set

The task should refine these where needed, but it should keep them conceptually distinct.

### 4. What is the role of the burden vector versus any total score?

The design should make clear whether the metric is primarily:
- explanatory
- ranking-oriented
- interpretive
- or some combination, with priority stated explicitly

### 5. What conceptual simplifications are required for v1?

The design should identify first-version boundaries that preserve coherence without prematurely collapsing the concept into implementation convenience.

### 6. What interpretations/findings should the metric support?

The design should define what kinds of explanatory conclusions the metric is intended to make possible.

### 7. How should the metric reflect code-shape quality?

The design should explain how Local Comprehension Complexity relates to the `code-shaper` qualities:
- simplicity
- consistency
- robustness
- local comprehensibility

In particular, it should clarify which burden dimensions primarily signal failures of:
- single responsibility
- mixing computation and flow control
- inconsistent data shape or idiom choice
- weakened orthogonality
- code whose meaning must be fetched from non-local context

## Architecture guidance

This task does **not** define implementation architecture.

The only architectural guidance relevant here is conceptual:
- the metric definition should be implementation-independent enough to support more than one reasonable implementation approach later
- the metric definition should still be precise enough to act as a normative source for future implementation work

## Risks

- the metric becomes too implementation-shaped too early
- burden dimensions overlap ambiguously
- the design tries to answer too many adjacent problems at once
- a headline score obscures the explanatory vector
- the metric becomes a style metric or a branch-count variant instead of a comprehension metric
- the metric loses contact with the `code-shaper` qualities it is meant to operationalize

## Mitigations

- prioritize conceptual clarity over implementation detail
- define explicit scope boundaries and non-goals
- define primary ownership of evidence across dimensions
- keep the burden vector primary and any rollup secondary
- use `code-shaper` as a guiding evaluative lens when refining burden dimensions and interpretations
- record open questions explicitly instead of smuggling them into vague wording

## Done means

Task `001-local-comprehension-complexity` is complete when:
- the Local Comprehension Complexity metric is clearly defined
- its intent, dimensions, boundaries, and interpretations are converged
- implementation concerns have been intentionally excluded from the task
- the resulting design is suitable to serve as the normative basis for a later implementation-planning task

This task is therefore a **metric-definition task**, not an implementation-planning task.
