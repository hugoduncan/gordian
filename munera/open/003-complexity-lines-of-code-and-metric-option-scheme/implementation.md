2026-04-21

Task refined collaboratively to extend `gordian complexity` with lines-of-code and to converge the command onto a stable two-metric model.

Requested work:
- add lines-of-code to the Gordian complexity sub-command
- define a good scheme for options like `--top`, `--sort`, and thresholds/filters so their metric target is explicit without introducing metric-selection UI yet

Current design status:
- intent, scope, minimum concepts, non-goals, schema direction, CLI direction, and implementation sequence are now defined
- migration policy is now also locked: `--min-cc` is removed rather than retained as a compatibility alias

Locked decisions:
- active metrics in v1 are always both metrics: cyclomatic complexity and LOC
- there is no `--metric` / `--metrics` selection in v1; both metrics are computed implicitly
- canonical threshold/filter syntax is generic and metric-qualified rather than cyclomatic-specific
- LOC is measured over the whole reported unit form span
- canonical sort keys are `cc`, `loc`, `cc-risk`, `ns`, and `var`
- `--top` stays metric-agnostic and truncates after sort order is applied
- both metrics always appear together in human and machine-readable output
- `--min-cc` is removed from accepted CLI; guidance should point users to `--min cc=...`

Precise LOC rule now locked:
- count physical source lines from the first line of the reported unit form through the last line of that form, inclusive
- exclude blank lines
- exclude comment-only lines matching `^\s*;+.*$`
- count lines containing code plus trailing comments
- count docstring and multi-line string lines by the same physical-line rule
- do not add semantic handling for reader-discard or metadata beyond the literal source span

Canonical CLI direction:
- keep `gordian complexity` as the single command surface
- use generic metric-qualified threshold syntax: repeated `--min metric=value`
- use `--sort` to choose the primary ranking metric/key
- if sorting by `cc` or `loc`, bars should reflect that metric; otherwise bars default to `cc`
- reject `--min-cc` and produce an error that tells users to use `--min cc=...`

Canonical schema direction:
- always emit `:metrics [:cyclomatic-complexity :lines-of-code]`
- extend unit records with `:loc`
- extend namespace and project rollups with `:total-loc`, `:avg-loc`, and `:max-loc`
- keep compact metric-qualified fields rather than nested per-metric maps
- represent applied thresholds in options as `:mins {...}`

Completion target:
- implementation-ready design and sequencing for an incremental change set that adds LOC and converges the complexity CLI toward a stable always-on two-metric model without over-abstracting into a generic metric framework
