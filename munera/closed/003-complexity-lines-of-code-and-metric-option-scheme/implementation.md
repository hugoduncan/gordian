2026-04-21

Task completed.

Implemented work:
- added LOC as a second built-in metric for `gordian complexity`
- converged the complexity command onto a stable two-metric model
- replaced cyclomatic-specific threshold CLI with generic repeatable `--min metric=value`
- removed `--min-cc` from the accepted CLI surface with a migration error
- added `loc` as a first-class sort key
- made `--min` a display-only filter over unit rows
- kept namespace and project rollups computed from the full analyzed unit set
- updated text, markdown, EDN/JSON payloads, CLI help, README, and tests
- enabled source-span-based LOC counting by carrying file source and top-level form locations through the scan/complexity pipeline

Canonical v1 behavior now implemented:
- active metrics are always both metrics:
  - `:cyclomatic-complexity`
  - `:lines-of-code`
- canonical payload includes:
  - `:metrics`
  - per-unit `:cc`, `:cc-decision-count`, `:cc-risk`, `:loc`
  - rollup `:total-cc`, `:avg-cc`, `:max-cc`, `:total-loc`, `:avg-loc`, `:max-loc`
  - options metadata with `:mins`
- supported complexity sort keys:
  - `cc`
  - `loc`
  - `cc-risk`
  - `ns`
  - `var`
- supported threshold syntax:
  - `--min cc=10`
  - `--min loc=20`
  - repeatable and conjunctive
- bar rendering follows the primary metric for `cc` and `loc` sorts, else defaults to `cc`

Validation and docs:
- `bb test` passes
- README updated to document the two-metric command surface
- task requirements now satisfied in code and docs

Commit implementing the task:
- `9b124c9 feat: add loc metric to complexity command`
