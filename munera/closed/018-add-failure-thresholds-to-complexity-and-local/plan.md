Approach:
- add explicit command-native enforcement thresholds to `complexity` and `local` using one repeatable max-threshold option shape
- keep enforcement unit-focused in v1 and evaluate against the full analyzed unit set
- preserve existing reporting behavior when no fail thresholds are supplied
- implement canonical enforcement payloads first, then exit semantics, then docs/tests

Execution phases:

### Phase 1 — lock policy surface, payload shape, and semantics
- lock the preferred option shape for both commands as repeatable `--fail-above metric=value`
- define the exact enforcement subject for v1 as analyzed units only
- define boundary semantics explicitly: a check fails only when `value > threshold`; equality passes
- define aggregation semantics explicitly: the overall result passes iff all configured checks pass
- define zero-unit behavior explicitly: pass vacuously and record analyzed unit count `0`
- define exit-code semantics explicitly: threshold pass => `0`, threshold fail => `1`, ordinary parse/usage/runtime errors retain existing behavior
- lock the canonical `:enforcement` payload shape and required semantic fields
- lock omission semantics explicitly: when no fail thresholds are supplied, `:enforcement` is absent from the canonical payload
- lock metric identity semantics explicitly: canonical metric identifier plus original CLI metric token are both recorded in enforcement results
- identify the existing metric-resolution seams in `complexity` and `local` that enforcement should reuse
- identify the command/report seams where enforcement results should be attached without contaminating formatter-only code

Exit condition:
- one explicit, reviewable enforcement model is defined for both commands

### Phase 2 — complexity CLI and canonical enforcement
- add `complexity` CLI parsing and validation for repeatable fail thresholds
- reuse the existing complexity metric vocabulary (`cc`, `loc`) for fail-threshold parsing
- lock validation semantics for complexity thresholds as strictly positive integers
- implement canonical unit-level enforcement evaluation for complexity reports
- attach enforcement configuration and results to the canonical complexity payload
- ensure enforcement is evaluated over the full analyzed unit set after scope resolution and analysis, ignoring `--min`, `--top`, and rollup inclusion choices
- ensure all violating units are retained canonically

Exit condition:
- `complexity` can evaluate configured fail thresholds against its full analyzed unit set and expose canonical results

### Phase 3 — local CLI and canonical enforcement
- add `local` CLI parsing and validation for repeatable fail thresholds
- reuse the existing local metric-resolution seam so built-in aliases and dotted numeric keys work consistently for enforcement
- lock validation semantics for local thresholds as strictly positive numeric values, including decimals, for numeric metrics
- implement canonical unit-level enforcement evaluation for local reports
- attach enforcement configuration and results to the canonical local payload
- ensure enforcement is evaluated over the full analyzed unit set after scope resolution and analysis, ignoring `--min`, `--top`, and rollup inclusion choices
- ensure all violating units are retained canonically

Exit condition:
- `local` can evaluate configured fail thresholds against its full analyzed unit set and expose canonical results

### Phase 4 — exit behavior and rendered output
- update command execution so fail-threshold violations produce exit code `1` while pass cases remain `0`
- keep current behavior unchanged when no fail thresholds are requested
- add a compact enforcement summary/section to text and markdown output when thresholds are active
- add a dedicated failures section so violating units remain visible even when the normal unit table is filtered or truncated
- ensure EDN/JSON make configured thresholds, per-check results, analyzed unit count, all violations, and overall outcome explicit and reviewable
- define deterministic ordering for human-readable violations and per-check summaries, using configured check order and stable value/name tie-breaks

Exit condition:
- enforcement is visible in output and wired to deterministic process success/failure

### Phase 5 — docs and help convergence
- update scoped help for `gordian complexity`
- update scoped help for `gordian local`
- update `README.md` examples and automation guidance
- explicitly distinguish display filters (`--min`) from fail thresholds (`--fail-above`)
- document that enforcement evaluates the full analyzed unit set, not the displayed subset
- document equality boundary behavior and zero-unit pass behavior
- clarify when to use local-metric fail thresholds vs `gordian gate`

Exit condition:
- docs and help match the shipped automation semantics

### Phase 6 — validation and review
- add focused tests for parse/validation of fail thresholds on both commands
- add tests for malformed, unknown, and non-numeric threshold expressions
- add passing and failing integration tests for both commands
- add equality-boundary tests for both commands
- add zero-unit behavior tests
- add machine-readable payload tests for enforcement shape and all-violations retention
- add tests showing `--min` and `--top` do not affect enforcement outcomes or failure explanation visibility
- add tests that default no-threshold behavior remains unchanged
- run representative manual sanity checks
- run full suite

Exit condition:
- enforcement behavior is test-covered, reviewable, and backward compatible except for the explicitly requested new exit behavior when thresholds are present

Expected shape:
- both commands accept repeatable `--fail-above metric=value`
- thresholds apply to full analyzed units, not filtered/truncated display subsets
- canonical payloads contain explicit enforcement configuration, per-check results, unit counts, and all violations
- text/markdown render an explicit pass/fail summary and dedicated failures section when enforcement is active
- commands exit `0` on pass and `1` on fail when thresholds are active
- existing behavior remains unchanged when fail thresholds are absent
