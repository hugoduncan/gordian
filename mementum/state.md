# Gordian — Working State

**Last updated:** 2026-04-24

## What this project is

`gordian` is a Babashka CLI for analyzing Clojure codebases.

Primary lenses:
- structural namespace coupling
- conceptual coupling
- change coupling
- architectural findings and drill-downs
- local executable-unit metrics (`complexity`, `local`)
- test architecture analysis
- DSM / subsystem / community views

Default behavior:
- `gordian` with no explicit command defaults to `analyze`
- project roots are auto-discovered from cwd / supplied dir
- explicit source dirs remain supported

## Current status

**v0.2.0 alpha** with a broad command surface implemented.

Current validated baseline:
- 354 tests
- 3169 assertions
- 0 failures
- 0 errors

## Current command surface

```text
gordian <command> [args] [options]

Commands:
  analyze
  diagnose
  compare
  gate
  subgraph
  communities
  dsm
  tests
  complexity
  local
  explain
  explain-pair
```

Human/machine output modes:
- text (default)
- `--markdown`
- `--edn`
- `--json`

Output modes are mutually exclusive.

## Local-metric enforcement

Both local-metric commands now support command-native pass/fail thresholds:
- `gordian complexity --fail-above metric=value`
- `gordian local --fail-above metric=value`

Semantics:
- thresholds apply to analyzed **units**
- fail iff `value > threshold`
- equality passes
- multiple checks combine conjunctively
- evaluation uses the full analyzed unit population, not the display-filtered subset
- zero-unit populations pass vacuously
- when thresholds are present:
  - canonical payload includes `:enforcement`
  - exit code is `0` on pass, `1` on fail

Important correctness note:
- `complexity` enforcement must evaluate against canonical pre-display units
- display shaping such as `--min` and `--top` must not affect enforcement results

## Architecture

High-level shape:
- thin IO shell at the edges
- mostly pure analysis/report assembly in the middle
- command-focused output formatters

### Core structural analysis

```text
scan.clj         edamame parse .clj → dependency graph          IO
close.clj        transitive closure                             pure
aggregate.clj    propagation cost, reach, fan-in               pure
metrics.clj      Ca, Ce, instability                           pure
scc.clj          cycle detection                               pure
classify.clj     core/peripheral/shared/isolated roles         pure
family.clj       family-scoped metrics / façade interpretation pure
filter.clj       namespace exclusion                           pure
discover.clj     project layout detection                      pure
config.clj       .gordian.edn loading + merge                  thin IO
```

### Cross-lens / workflow analysis

```text
conceptual.clj   TF-IDF + conceptual pairs                     pure
cc_change.clj    change-coupling pairs                         pure
diagnose.clj     ranked findings                              pure
explain.clj      namespace / pair drill-down                  pure
compare.clj      snapshot diff                                pure
cluster.clj      finding clustering                           pure
gate.clj         CI / ratchet checks                          pure
prioritize.clj   actionability ranking                        pure
subgraph.clj     subsystem slicing                            pure
communities.clj  community discovery                          pure
dsm.clj          dependency structure matrix                  pure
tests.clj        test architecture analysis                   pure
```

### Local metrics

```text
cyclomatic.clj   cyclomatic complexity + LOC                  pure
local/units.clj  local analyzable unit extraction             pure
local/evidence.clj
local/burden.clj
local/findings.clj
local/report.clj Local Comprehension Complexity pipeline      pure
enforcement.clj  shared unit-threshold evaluation             pure
```

### Output / entry

```text
output/*.clj     command-focused formatters                   pure
output/enforcement.clj threshold rendering                    pure
envelope.clj     machine-readable metadata envelope           pure
main.clj         command execution / IO wiring                IO
cli/*            command registry, parsing, help              pure-ish
```

## Important command-specific invariants

### `complexity`
- canonical units are arity-level executable units
- built-in metrics are always:
  - `:cyclomatic-complexity`
  - `:lines-of-code`
- `--min` is display-only filtering
- `--fail-above` is enforcement
- rollups are opt-in

### `local`
- canonical units are top-level `defn` arities and `defmethod` bodies
- built-in aliases and dotted numeric keys share one metric-resolution seam
- `--min` is display-only filtering
- `--fail-above` is enforcement
- rollups are opt-in

Open task queued:
- move `local` emitted top-level `:units` toward display-shaped output semantics while preserving canonical unit populations for enforcement internally

### `gate`
- still the baseline/diff-oriented policy command
- local fail thresholds do **not** replace `gate`

## Auto-discovery and scope semantics

Project-root mode:
- source paths auto-discovered from project layout
- `tests` forces inclusion of discovered test dirs
- `complexity` / `local` default to discovered source paths only
- `--tests-only` switches those commands to discovered test paths
- explicit paths override discovery-based scope selection

Paths are normalized via `fs/normalize`.
This matters for:
- stable output payloads
- change-coupling path matching
- avoiding `./` prefix mismatches

## Durable implementation notes

### Parsing / scanning
- full-file parsing is used where conceptual/local metrics need it
- `edamame` must be configured with `:syntax-quote true`
  - otherwise macro bodies with syntax-quote can hang/skew parsing

### Conceptual analysis
- tokenization and stemming were tuned to reduce punctuation / stop-word noise
- candidate-pair evaluation must force work inside `pmap` futures
  - avoid lazy seqs escaping futures

### JSON / EDN payloads
- canonical payload first; formatters should not invent semantics
- envelope metadata is part of reproducibility
- `:enforcement` is omitted entirely when threshold options are absent

### Output shaping
- display filters and truncation are presentation concerns
- policy decisions must use canonical analyzed populations

## Self-analysis snapshot

Historically, gordian tends toward a star-like topology:
- `gordian.main` is the primary orchestration/peripheral namespace
- most analysis namespaces are pure and structurally stable

Use:
```bash
bb gordian src/
```
for a fresh current self-analysis rather than relying on stale embedded metrics.

## What belongs elsewhere

Not kept here anymore:
- per-task tracking
- implementation queue / ordering
- open/closed task state
- step-by-step session history
- task decomposition and acceptance tracking

That information belongs under:
- `munera/`

## Useful files

- `mementum/state.md` — this operational orientation
- `munera/plan.md` — open-task queue
- `README.md` — user-facing command docs
- `src/gordian/main.clj` — command wiring
- `src/gordian/cli/registry.clj` — authoritative CLI surface
