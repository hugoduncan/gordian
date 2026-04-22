Enable `gordian local` ranking and filtering controls to operate on arbitrary per-unit numeric keys, rather than only a fixed built-in burden-family allowlist.

## Intent

- make `gordian local` more flexible for exploratory analysis without requiring a code change for every newly useful numeric field
- preserve the existing ergonomic built-in burden-family workflow while allowing advanced users to target any numeric per-unit signal already present in the canonical payload
- keep the canonical local report as the source of truth and derive sort/filter behavior from explicit report shape rather than duplicated option-specific hardcoding
- provide a narrow, reviewable path to support future local metrics such as `working-set` subfields or newly added numeric evidence-derived scores

## Problem statement

`gordian local` currently hardcodes the metrics allowed by `--sort`, `--min`, and `--bar`:
- `total`
- `flow`
- `state`
- `shape`
- `abstraction`
- `dependency`
- `working-set`
- plus `ns` and `var` for sort only

This works for the current burden-family surface, but it creates friction in three ways:

1. adding a new useful per-unit numeric field requires touching CLI validation, parsing, report metric lookup, and often docs/tests even when the value is already present in the canonical unit payload
2. subfields already present in the report, such as `:working-set :peak` and `:working-set :avg`, cannot be targeted directly even though they are meaningful and numeric
3. the implementation duplicates knowledge of which metrics exist instead of deriving that from a small explicit metric-resolution seam

The result is a command that is easy to use for the fixed happy path, but unnecessarily closed for advanced local analysis.

The desired behavior is:
- keep existing built-in aliases working
- allow `--sort`, `--min`, and `--bar` to target arbitrary per-unit numeric keys through a documented key syntax
- validate against an explicit authoritative documented local numeric schema surface, not an ad hoc scattered allowlist
- keep non-numeric keys rejected with clear errors

## Scope

### In scope

- define how `gordian local` names arbitrary per-unit numeric keys in CLI options
- extend `--sort` to accept arbitrary numeric per-unit keys in addition to existing built-ins and `ns` / `var`
- extend `--min metric=value` to accept arbitrary numeric per-unit keys
- extend `--bar` to accept arbitrary numeric per-unit keys that are meaningful for histogram display
- centralize metric/key resolution in `gordian.local.report` or an adjacent local-report seam
- preserve existing built-in names and semantics for current burden-family metrics
- support nested numeric per-unit keys where clearly justified by the documented authoritative local numeric schema surface
- add tests covering valid arbitrary keys, invalid keys, and backward compatibility
- update README/help text to explain the supported key syntax and examples

### Out of scope

- changing burden formulas or local findings semantics
- redesigning the canonical local unit payload
- adding arbitrary expression evaluation in CLI options
- changing `gordian complexity` in this task
- introducing project-configured custom metric aliases unless it falls out trivially and clearly

### Explicitly deferred

- generic arbitrary-key support across all Gordian commands
- sorting/filtering by non-numeric values beyond existing `ns` / `var` cases
- arbitrary derived expressions such as sums, ratios, or boolean predicates in `--min`
- wildcard or regex key selection

## Acceptance criteria

- `gordian local --sort` accepts existing built-ins unchanged
- `gordian local --sort` also accepts documented arbitrary numeric per-unit keys
- `gordian local --min metric=value` accepts documented arbitrary numeric per-unit keys
- `gordian local --bar` accepts documented arbitrary numeric per-unit keys
- nested numeric keys from the authoritative documented local numeric schema surface can be addressed by the chosen syntax
- the authoritative source of supported numeric keys is explicit and reviewable
- when namespace rollups are requested with an arbitrary numeric sort key, rollups sort by the average of that same metric across units in the namespace
- non-numeric keys are rejected with clear error messages
- unknown keys are rejected with clear error messages
- existing examples such as `--sort abstraction`, `--min total=12`, and `--bar working-set` keep working
- sorting/filtering/bar rendering all resolve through one explicit metric-resolution seam rather than duplicated hardcoded mappings
- README and scoped help document the key syntax with at least one nested example
- full suite passes

## Minimum concepts

- **built-in metric alias** — a stable short CLI name such as `total` or `working-set` mapped to an existing numeric field
- **arbitrary per-unit numeric key** — any numeric field present on the canonical local unit payload that can be addressed directly by the chosen CLI syntax
- **nested key path** — a way to refer to nested numeric fields such as working-set peak/avg/burden
- **metric-resolution seam** — the single place where a CLI metric token resolves to a numeric extractor over a unit
- **numeric-only validation** — command-level rejection of keys that do not resolve to numeric values on local units

## Design direction

Prefer explicit key-path support plus backward-compatible aliases.

The preferred behavior is:
1. keep the current built-in aliases (`total`, `flow`, `state`, `shape`, `abstraction`, `dependency`, `working-set`)
2. add a documented path syntax for arbitrary numeric keys, preferably hyphenated aliases for current built-ins plus dotted paths for nested fields
3. resolve all sort/filter/bar metrics through one explicit extractor lookup layer
4. validate requested keys against canonical local unit shape and numeric values

A likely useful key model is:
- aliases:
  - `total` -> `:lcc-total`
  - `flow` -> `:flow-burden`
  - `working-set` -> `[:working-set :burden]`
- direct paths:
  - `lcc-total`
  - `flow-burden`
  - `working-set.burden`
  - `working-set.peak`
  - `working-set.avg`

This preserves ergonomic short names while opening the door to the actual payload surface.

## Possible implementation approaches

### Approach A — aliases plus dotted key-path syntax (preferred)

- keep the current built-in aliases
- parse arbitrary metric tokens as either aliases or dotted paths
- convert dotted paths into nested keyword paths
- build one resolver function used by `valid-sort-key?`, `valid-bar-metric?`, `parse-min-expression`, `metric-value`, and help text generation

Pros:
- backward compatible
- easy for users to read and type
- supports nested fields naturally
- small, explicit, and reviewable

Cons:
- needs careful validation and helpful error messages

### Approach B — EDN keyword/vector syntax in CLI

- require values like `:lcc-total` or `[:working-set :peak]`

Pros:
- maps directly to Clojure data
- minimal ambiguity

Cons:
- worse CLI ergonomics
- harder to document for ordinary users
- more shell quoting friction

### Approach C — fixed expanded allowlist only

- add a few more hardcoded keys such as `working-set-peak` and `working-set-avg`

Pros:
- smallest implementation

Cons:
- does not actually solve the extensibility problem
- repeats the current pattern at a slightly larger scale

## Architectural guidance

Follow existing Gordian preferences:
- preserve canonical local report data as the source of truth
- keep CLI parsing/validation narrow and explicit
- centralize metric resolution instead of spreading field knowledge across parse, report, and output code
- keep existing short aliases stable

Avoid:
- deriving supported keys from formatter code
- accepting non-numeric arbitrary keys and failing later in sorting/rendering
- introducing a full expression language for one task
- widening the task into a broader `complexity` redesign

## Risks

- arbitrary-key syntax could become ambiguous or awkward if not chosen carefully
- validation based only on one unit could accidentally allow sparse/missing fields or reject legitimate fields in empty reports
- extending `--bar` to arbitrary keys could expose values that do not render usefully in current human-readable output
- the task could drift into report-schema redesign if the current unit shape feels slightly inconvenient

## Mitigations

- keep the syntax simple and document it with concrete examples
- validate against explicit known aliases plus canonical unit field traversal rules
- treat numeric extractability as the gate for `--sort` / `--min` / `--bar`
- keep the first slice narrow: arbitrary numeric keys already present on units, not computed expressions
- add focused tests around empty/unitless reports and nested paths

## Done means

The task is done when `gordian local` still supports the current friendly burden-family controls, but `--sort`, `--min`, and `--bar` can also target documented arbitrary numeric per-unit keys through one explicit metric-resolution seam, with clear validation, updated docs/help, and full-suite coverage.
