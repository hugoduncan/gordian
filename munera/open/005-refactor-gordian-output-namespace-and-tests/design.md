Refactor the oversized `gordian.output` namespace and its test suite into smaller, command-focused modules while preserving Gordian’s public behavior.

## Intent

- reduce the structural and cognitive load concentrated in `src/gordian/output.clj`
- reduce the maintenance burden and navigation cost of `test/gordian/output_test.clj`
- preserve the existing command surface and output semantics while improving internal structure
- make future output work cheaper by aligning module boundaries with Gordian command families

## Problem statement

`gordian.output` has become a catch-all formatting namespace for many commands and formats.
The file is now very large (~1777 LOC), and its test file is similarly large (~1118 LOC).
That creates several problems:

- low local comprehensibility: unrelated formatting concerns are interleaved
- high change surface: small output changes risk broad incidental edits
- poor test locality: tests for one command live beside many unrelated assertions
- weak architectural signal: the current namespace boundary hides natural subdomains

The problem is not primarily feature behavior. The problem is structural concentration.
This task should therefore be a refactor-first task: preserve behavior, improve boundaries.

## Scope

### In scope

- split `src/gordian/output.clj` into smaller namespaces
- split `test/gordian/output_test.clj` into matching, smaller test namespaces
- define the target namespace decomposition and ownership boundaries
- move shared formatting helpers to a minimal shared layer where justified
- preserve existing public command behavior and output text/markdown semantics
- preserve existing EDN/JSON output behavior except where wiring must move
- update requires/usages across the codebase to the new namespace layout

### Out of scope

- changing Gordian command semantics
- redesigning report schemas
- changing ranking/diagnostic rules
- adding new output modes
- broad CLI redesign
- behavior changes bundled together with the refactor unless required to keep tests passing

## Non-goals

- do not use this refactor to rewrite all output logic from scratch
- do not combine this task with unrelated feature work
- do not introduce a speculative abstraction framework for output rendering
- do not force uniformity where a thin command-specific formatter is clearer

## Design direction

### Preserve one public façade

Keep a stable public façade namespace:
- `gordian.output`

Its role after the refactor:
- re-export or delegate the currently consumed public entry points
- remain the compatibility surface for callers such as `gordian.main`
- avoid keeping most implementation detail in the façade itself

### Split by command/domain, not by micro-abstraction

Preferred decomposition is command-oriented, because the file is currently large due to command accumulation.
The decomposition should follow user-facing report families, not arbitrary helper taxonomy.

Candidate shape:

- `gordian.output.common`
  - padding/rule/bar helpers
  - small reusable table/section helpers only where truly shared
- `gordian.output.analyze`
  - `format-report`
  - conceptual/change coupling helpers used only by analyze output
- `gordian.output.diagnose`
  - diagnose text/markdown formatting and cluster rendering helpers
- `gordian.output.explain`
  - namespace and pair explain text/markdown formatting
- `gordian.output.compare`
- `gordian.output.subgraph`
- `gordian.output.communities`
- `gordian.output.tests`
- `gordian.output.gate`
- `gordian.output.dsm`
- `gordian.output.complexity`

Exact grouping can be adjusted, but the end state should have strong locality and obvious ownership.
Small related commands may share a namespace if that materially improves cohesion.

### Keep helpers close to the command that uses them

A helper should stay command-local unless it is genuinely reused.
This task should prefer small local private helpers over a large new shared utility namespace.

### Mirror production structure in tests

Preferred test structure mirrors output structure:

- `test/gordian/output/analyze_test.clj`
- `test/gordian/output/diagnose_test.clj`
- `test/gordian/output/explain_test.clj`
- …
- `test/gordian/output/complexity_test.clj`

Keep shared test fixtures small and explicit. Extract a common fixture/helper ns only if repetition is real and stable.

## Architectural constraints

- preserve pure-core style for formatter functions
- keep `gordian.output` as a compatibility façade
- avoid cyclic dependencies between output namespaces
- shared helpers should depend on nothing command-specific
- test namespaces should target the narrowest production namespace possible

## Acceptance criteria

- `src/gordian/output.clj` is reduced to a thin façade or similarly small compatibility layer
- formatter implementation is distributed across smaller, command-focused namespaces
- `test/gordian/output_test.clj` is replaced or reduced in favor of smaller command-focused test namespaces
- existing output behavior remains unchanged unless an explicitly documented incidental fix is required
- tests still cover text and markdown formatting for the supported command families
- the resulting namespace structure is easier to navigate and change locally

## Done means

The task is complete when Gordian’s output formatting code is structurally decomposed into cohesive namespaces, the test suite mirrors that decomposition, and the public behavior remains stable.