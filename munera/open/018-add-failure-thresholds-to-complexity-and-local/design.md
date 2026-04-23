Add explicit failure-threshold options to `gordian complexity` and `gordian local` so they can be used in project automation to enforce maximum allowed local-code metric levels via command-native pass/fail behavior.

## Intent

- let `gordian complexity` and `gordian local` participate directly in CI and other project automation workflows
- preserve their existing exploratory/reporting workflows while adding an explicit enforcement mode
- make threshold failures reviewable in both machine-readable output and process exit status
- keep the command surfaces parallel where the concepts are parallel, without erasing meaningful differences between cyclomatic/LOC metrics and local burden metrics

## Problem statement

`gordian complexity` and `gordian local` currently support display filtering via `--min`, sorting, truncation, and optional rollups, but they do not provide command-native pass/fail semantics.

That creates a gap for automation:
- users can identify hotspots interactively
- users can export EDN/JSON and write custom wrappers
- but users cannot ask the commands themselves to fail when a project exceeds a maximum tolerated level

As a result, projects that want to enforce local-code quality budgets must build ad hoc wrappers around the reports. That duplicates policy logic outside Gordian, makes scripts less portable, and weakens the value of these commands as automation surfaces.

The desired behavior is:
- users can specify one or more maximum allowed thresholds directly on `gordian complexity`
- users can specify one or more maximum allowed thresholds directly on `gordian local`
- the commands retain their normal report sections and additionally evaluate whether any analyzed unit violates the configured maxima
- when one or more fail thresholds are supplied, the command exits `0` iff every configured threshold passes over the full analyzed unit set; otherwise it exits `1`
- failure details are explicit in text, markdown, EDN, and JSON output so CI logs and downstream tooling can explain what happened

## Scope

### In scope

- define explicit CLI options for pass/fail thresholds on `gordian complexity`
- define explicit CLI options for pass/fail thresholds on `gordian local`
- allow multiple threshold checks in one invocation
- define the threshold subject for v1 and make that choice explicit
- define exact comparison semantics at the threshold boundary
- define behavior when zero units are analyzed
- record enforcement configuration and results in the canonical machine-readable payloads
- make exit-code behavior explicit and test-covered
- update scoped help, README, and practical guidance for automation use
- add tests for passing and failing threshold scenarios for both commands

### Out of scope

- changing existing scoring semantics for complexity or local burden
- replacing `gordian gate` as the main architecture-ratchet command
- introducing a generic cross-command policy DSL
- arbitrary boolean expressions across multiple metrics
- baseline comparison or historical ratcheting for local metrics in this task

### Explicitly deferred

- compare/gate integration for local-metric thresholds
- project-configured default fail thresholds in `.gordian.edn`
- severity-band policy options beyond explicit max-threshold checks
- richer policy composition such as per-namespace thresholds or weighted multi-metric formulas
- enforcement over namespace rollups or project rollups

## Acceptance criteria

- `gordian complexity` exposes one or more explicit options for maximum allowed metric thresholds
- `gordian local` exposes one or more explicit options for maximum allowed metric thresholds
- threshold semantics are explicit and documented
- multiple max-threshold checks can be specified together
- each check fails only when `value > threshold`; equality passes
- overall pass/fail semantics are explicit: the command passes iff all configured checks pass
- the commands evaluate thresholds against the authoritative canonical report data, not formatter-only state
- thresholds are evaluated over the full analyzed unit set after normal scope/path resolution and analysis, but before display shaping such as `--min` filtering or `--top` truncation
- zero analyzed units pass vacuously and produce explicit enforcement metadata including unit count `0`
- pass/fail results are present in EDN/JSON in a reviewable canonical shape
- when no fail thresholds are supplied, `:enforcement` is omitted entirely from the canonical payload
- canonical EDN/JSON includes all violating units, not a truncated subset
- text/markdown output includes an explicit pass/fail section or summary when fail thresholds are requested
- human-readable failure output surfaces violating units even when normal unit display is filtered or truncated
- when fail thresholds are supplied, commands return exit code `0` when thresholds pass and exit code `1` when thresholds fail
- when no fail thresholds are supplied, existing command behavior remains unchanged
- existing `--min` continues to mean display filtering only and is clearly distinguished from fail thresholds
- tests cover:
  - pass case for `complexity`
  - fail case for `complexity`
  - combined thresholds for `complexity`
  - pass case for `local`
  - fail case for `local`
  - combined thresholds for `local`
  - equality boundary behavior
  - zero-unit behavior
  - machine-readable payload shape
  - malformed / unknown / non-numeric threshold validation
  - unchanged behavior when no fail thresholds are supplied
- full suite passes

## Minimum concepts

- **display filter** — existing `--min metric=value` behavior that affects displayed units only
- **fail threshold** — a maximum allowed metric value whose violation causes command failure
- **analysis population** — the full analyzed unit set after scope resolution and analysis, before display shaping
- **threshold subject** — the report entity being checked; in v1 this is always an analyzed unit
- **check result** — the per-threshold evaluation outcome including configured threshold, pass/fail, and violation count
- **violation subject** — one analyzed unit whose metric value exceeded a configured threshold
- **enforcement result** — canonical pass/fail data describing configured thresholds, violating subjects, overall outcome, and analyzed unit count
- **automation mode** — ordinary command execution with threshold options present, causing explicit pass/fail exit behavior

## Design direction

Prefer explicit max-threshold options that parallel the existing metric-targeted option model.

The preferred behavior is:
1. keep `--min metric=value` as display-only filtering
2. add a distinct max-threshold option for enforcement, repeatable as needed
3. evaluate enforcement over canonical analyzed units after report assembly and before process exit
4. emit explicit enforcement results into the payload and human-readable output

A likely option model is:
- `gordian complexity --fail-above cc=20`
- `gordian complexity --fail-above loc=80`
- `gordian local --fail-above total=15`
- `gordian local --fail-above working-set.peak=7.5`

This keeps the command surfaces parallel with existing metric-targeted syntax while making the semantics clearly distinct from `--min`.

## Threshold subject direction

Prefer unit-level enforcement in v1.

Rationale:
- both commands are fundamentally unit-hotspot commands
- unit-level thresholds answer the most direct automation question: “does any executable unit exceed our allowed ceiling?”
- namespace/project rollups are optional summary sections and should not become hidden enforcement subjects unless explicitly designed
- keeping v1 unit-focused avoids ambiguity about averages vs maxima for rollups

So the preferred v1 behavior is:
- thresholds are checked against the full analyzed unit set
- display filtering via `--min` and truncation via `--top` do not change threshold evaluation
- optional rollup sections remain informational unless a later task expands enforcement to them explicitly

## Threshold semantics direction

Lock the boundary and aggregation semantics explicitly.

Preferred v1 behavior:
- a unit violates a threshold check only when `value > threshold`
- `value == threshold` passes
- each configured threshold is evaluated independently over the full analyzed unit set
- the overall enforcement result passes iff every configured threshold passes
- if zero units are analyzed, the enforcement result passes vacuously and records unit count `0`

## Numeric validation direction

Prefer validation that matches each command’s authoritative metric surface.

Preferred v1 behavior:
- `complexity --fail-above` accepts the existing complexity metric vocabulary (`cc`, `loc`) and strictly positive integer thresholds
- `local --fail-above` reuses the authoritative local metric-resolution seam already used for local metric targeting and accepts strictly positive numeric thresholds, including decimals, for numeric metrics
- malformed, unknown, and non-numeric threshold expressions are rejected with clear command errors

## Canonical payload direction

Prefer one explicit enforcement section in the canonical report payload.

A suitable v1 shape is:

```edn
:enforcement
{:subject :units
 :unit-count 42
 :checks [{:metric :cyclomatic-complexity
           :metric-token "cc"
           :threshold 20
           :passed? false
           :violation-count 2
           :max-observed 31}
          {:metric :lines-of-code
           :metric-token "loc"
           :threshold 80
           :passed? true
           :violation-count 0
           :max-observed 57}]
 :violations [{:metric :cyclomatic-complexity
               :metric-token "cc"
               :threshold 20
               :actual 31
               :ns 'foo.bar
               :var 'tricky-fn
               :arity 2}
              {:metric :cyclomatic-complexity
               :metric-token "cc"
               :threshold 20
               :actual 24
               :ns 'foo.baz
               :var 'other-fn
               :arity 1}]
 :passed? false}
```

The exact field names may change slightly during implementation, but the task should preserve these semantic requirements:
- `:enforcement` is present only when fail thresholds are supplied
- explicit threshold subject (`:units`)
- explicit analyzed unit count
- one result per configured check
- each check and violation records a canonical metric identifier
- each check and violation also records the original CLI metric token for reviewability
- all violating units present canonically
- one overall pass/fail outcome

## Human-readable output direction

Prefer a dedicated enforcement section when thresholds are active.

Preferred v1 behavior:
- when no fail thresholds are supplied, existing human-readable output remains unchanged
- when fail thresholds are supplied, text/markdown include an explicit enforcement summary
- when violations exist, text/markdown include a dedicated failures section that surfaces violating units independently of the normal unit table
- human-readable output may summarize or truncate for readability if needed, but canonical EDN/JSON must retain all violations
- violations are ordered deterministically
- if failures are rendered grouped by check, groups follow configured check order and violations within each group are ordered by descending `:actual`, then `:ns`, then `:var`, then `:arity`
- if failures are rendered as one combined list instead, rows are ordered by configured check order, then descending `:actual`, then `:ns`, then `:var`, then `:arity`

## Possible implementation approaches

### Approach A — repeatable generic `--fail-above metric=value` on both commands (preferred)

- add one repeatable option to each command
- parse it using the same metric-resolution seam already used for sorting/filtering where appropriate
- evaluate over canonical units after report assembly
- add an `:enforcement` payload section

Pros:
- compact and parallel with existing `--min metric=value`
- scalable to multiple metrics without option explosion
- easy to document and script

Cons:
- requires careful wording so users do not confuse it with display filters

### Approach B — metric-specific flags such as `--max-cc`, `--max-loc`, `--max-total`

Pros:
- very explicit in help

Cons:
- inconsistent across commands
- duplicates the older complexity-specific flag style already moved away from elsewhere
- becomes awkward for `local` arbitrary numeric keys

### Approach C — separate `check` subcommands for local-metric commands

Pros:
- very explicit policy surface

Cons:
- much larger than requested
- splits exploration from automation unnecessarily
- duplicates most of the analysis/report logic

## Architectural guidance

Follow existing Gordian preferences:
- canonical report first
- explicit option metadata in the payload
- enforcement should operate on the same authoritative report data shown to users
- keep `complexity` and `local` parallel where semantics map cleanly
- preserve current non-threshold behavior unchanged unless fail options are supplied
- reuse the existing metric-resolution seams rather than introducing parallel schema knowledge

Avoid:
- overloading `--min` with fail semantics
- evaluating only the displayed/truncated subset of units
- implementing pass/fail logic only in formatters or shell wrappers
- silently failing without emitting reviewable violation details
- allowing `--top` or `--min` to hide the reason for a reported failure

## Relationship to existing Gordian workflows

This task does not replace `gordian gate`.

Use:
- `complexity --fail-above ...` or `local --fail-above ...` for absolute local-metric ceilings over the current analyzed unit population
- `gate` for baseline/ratchet architecture checks and diff-oriented policy

## Risks

- users may confuse display filtering (`--min`) and fail thresholds if naming is not explicit enough
- exit-code changes could accidentally affect current workflows if threshold behavior leaks into default execution
- `local` arbitrary numeric keys require careful reuse of the authoritative metric-resolution seam so enforcement does not fork its own schema logic
- output could become noisy if enforcement reporting is not shaped carefully
- large violation sets could make human-readable output unwieldy if failures are not summarized carefully

## Mitigations

- use a distinct option name like `--fail-above`
- activate pass/fail behavior only when the option is present
- define exit semantics explicitly: threshold pass => `0`, threshold fail => `1`, ordinary parse/usage/runtime errors retain existing behavior
- evaluate thresholds against the full canonical unit set and record both configuration and all violations explicitly
- reuse existing metric-resolution seams for `complexity` and `local` rather than inventing parallel validation layers
- add focused tests for unchanged default behavior, explicit fail cases, equality boundaries, and zero-unit behavior

## Done means

The task is done when `gordian complexity` and `gordian local` both support explicit command-native max-threshold enforcement over analyzed units, both preserve existing exploratory behavior when no fail thresholds are supplied, both emit reviewable enforcement results in human-readable and machine-readable output, both return deterministic pass/fail exit codes in automation scenarios, equality and zero-unit semantics are explicit and test-covered, and the docs/help/tests converge on that model.
