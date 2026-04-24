Approach:
- change `gordian local` so emitted top-level `:units` is the shaped unit population users actually asked to see
- preserve canonical analyzed units for enforcement internally rather than exposing them in the emitted payload
- remove or simplify `:display` where that distinction is no longer needed
- converge code, docs, and tests on one visible output model

Execution phases:

### Phase 1 — lock desired emitted-output semantics in tests
- add or update tests that describe the intended machine-readable behavior for `--min`, `--sort`, and `--top`
- make the desired distinction explicit: emitted `:units` is shaped, enforcement population remains canonical and internal
- identify any tests/fixtures that currently lock the older separate `:units` vs `:display :units` contract

Exit condition:
- the desired output contract is test-visible before implementation changes

### Phase 2 — reshape local report/output data
- change the local report-finalization/output path so top-level `:units` reflects display shaping
- keep canonical analyzed units available to command execution for enforcement without requiring exposure in the emitted payload
- remove or simplify `:display` if it becomes redundant
- ensure any remaining report sections have a clear basis and coherent output contract

Exit condition:
- emitted `:units` reflects visible shaping and internal enforcement still has access to canonical units

### Phase 3 — update formatters and machine-readable outputs
- ensure text and markdown behavior remains aligned with the new emitted `:units` model
- ensure EDN and JSON expose the shaped unit population at top level
- preserve or clarify any remaining output seams only where still justified

Exit condition:
- all output modes converge on the same visible unit-population semantics

### Phase 4 — docs, fixtures, and validation
- update README/help/examples if they describe or imply the old split payload model
- update/remove obsolete fixtures and tests that depend on separate canonical top-level `:units` vs `:display :units`
- add or keep regression coverage showing enforcement still uses the full canonical analyzed population
- run representative sanity checks and the full suite

Exit condition:
- docs/tests/fixtures all match the shipped shaped-emitted-units behavior

Expected shape:
- top-level emitted `:units` reflects `--min`, `--sort`, and `--top`
- enforcement still evaluates the full canonical analyzed population
- `:display` is removed or simplified if no longer necessary
- `local` output semantics become easier for machine-readable consumers to understand
