# 012 — Gate Mode (CI / Refactor Ratchet)

## Problem

After architectural cleanup, teams need a way to prevent regressions.
Today Gordian can analyze, diagnose, explain, compare, and cluster, but it
cannot answer the operational question:

> should this change be allowed to merge?

The common workflow is:
1. establish a known-good baseline snapshot
2. make refactors or feature changes
3. ensure architecture did not regress beyond agreed limits

Without a gate, users must manually inspect compare output. That does not
scale in CI.

## Goal

Add a new command:

```bash
gordian gate --baseline gordian-baseline.edn
```

that compares the current codebase against a saved snapshot and exits:
- `0` when all gate checks pass
- `1` when any gate check fails

This turns Gordian into a ratchet: architectural quality can improve over
time without requiring an immediate big-bang cleanup.

---

## Scope

### In scope
- file-based baseline input (`--baseline <file>`)
- evaluate current codebase by running Gordian live
- compare baseline vs current using existing compare machinery
- configurable pass/fail checks
- human-readable, markdown, EDN, and JSON output
- CI-friendly exit code semantics

### Out of scope
- `--ref HEAD~4` / git-ref comparison (deferred)
- auto-writing a new baseline
- historical trend storage
- per-cluster gating rules

---

## CLI

### Basic

```bash
gordian gate --baseline gordian-baseline.edn
```

Runs a current analysis (same source resolution behavior as `analyze`) and
compares it against the baseline snapshot.

### Examples

```bash
gordian gate --baseline gordian-baseline.edn

gordian gate . --baseline gordian-baseline.edn --max-pc-delta 0.01

gordian gate . --baseline gordian-baseline.edn --max-new-medium-findings 2

gordian gate . --baseline gordian-baseline.edn --fail-on new-cycles,new-high-findings

gordian gate . --baseline gordian-baseline.edn --markdown
```

### Flags

```text
--baseline <file>              Saved EDN snapshot to compare against (required)
--max-pc-delta <float>         Maximum allowed increase in propagation cost
--max-new-high-findings <n>    Maximum newly introduced high-severity findings
--max-new-medium-findings <n>  Maximum newly introduced medium-severity findings
--fail-on <csv>                Named checks to enable strictly
--json                         JSON output
--edn                          EDN output
--markdown                     Markdown output
```

### Default behavior

If no explicit thresholds are given, gate runs a conservative default rule set:
- no new cycles
- no increase in high-severity finding count
- propagation cost delta ≤ `0.01`

This default should be useful immediately in CI without forcing too many
configuration decisions.

---

## Inputs

### Baseline snapshot
A previously saved Gordian EDN report.
Expected to be schema ≥ 1 and typically created via:

```bash
gordian diagnose . --edn > gordian-baseline.edn
```

Baseline may come from:
- `analyze`
- `diagnose`

`diagnose` is preferred because findings are directly available.
If baseline lacks findings, gate should still operate on structural checks and
synthesize current findings as needed.

### Current snapshot
Produced live by running the normal report pipeline on the current codebase.
Recommended default: current gate should behave like `diagnose`, because gate
is concerned with findings as well as graph metrics.

So the effective current pipeline is:
- conceptual auto-enabled at `0.15` unless overridden
- change auto-enabled unless disabled in future extensions
- findings + clusters computed

---

## Architecture

### Reuse existing modules
- `main.clj` — command routing and option parsing
- `compare.clj` — baseline vs current diff generation
- `diagnose.clj` — current finding generation
- `cluster.clj` — optional context only; not required for gate result
- `envelope.clj` — machine-readable metadata wrapping
- `output.clj` — render gate results

### New module
- `gate.clj` — pure rule evaluation

This keeps policy separate from comparison and rendering.

---

## Data flow

```text
baseline.edn + current codebase
  → build current report
  → compare/baseline-vs-current
  → gate/evaluate-checks
  → gate result map
  → output renderer / exit code
```

---

## Output shape

```clojure
{:gordian/command :gate
 :baseline-file   "gordian-baseline.edn"
 :result          :pass
 :checks
 [{:name   :pc-delta
   :status :pass
   :limit  0.01
   :actual 0.004}
  {:name   :new-cycles
   :status :pass
   :actual 0}
  {:name   :new-high-findings
   :status :fail
   :limit  0
   :actual 2}]
 :summary
 {:passed 2
  :failed 1
  :total  3}
 :compare {...}}
```

### Notes
- `:compare` embeds the same diff map produced by `compare/compare-reports`
- `:result` is derived from all check statuses
- additive metadata may include `:src-dirs`, `:lenses`, `:include-tests`, etc.

---

## Checks

Checks are small predicates over the compare result.
Each check produces a standardized shape:

```clojure
{:name   keyword
 :status :pass | :fail
 :limit  any
 :actual any
 :reason string}
```

### Initial check set

#### 1. `:pc-delta`
Fail if propagation cost increased above threshold.

```clojure
(actual = (get-in diff [:health :delta :propagation-cost]))
pass?   = (<= actual limit)
```

Default limit: `0.01`

#### 2. `:new-cycles`
Fail if any new cycles were introduced.

```clojure
(actual = (count (get-in diff [:cycles :added])))
pass?   = (zero? actual)
```

Default limit: `0`

#### 3. `:new-high-findings`
Fail if any new `:high` severity findings were added.

```clojure
(actual = count of added findings where severity = :high)
pass?   = (<= actual limit)
```

Default limit: `0`

#### 4. `:new-medium-findings`
Useful but not enabled by default.

```clojure
(actual = count of added findings where severity = :medium)
pass?   = (<= actual limit)
```

Default limit when explicitly enabled: `0`

---

## `--fail-on` semantics

`--fail-on` allows explicit selection of checks:

```bash
gordian gate --baseline base.edn --fail-on new-cycles,new-high-findings
```

Interpretation:
- run only named strict checks, unless threshold flags also add more
- unknown check names are an error

Recognized names for v1:
- `pc-delta`
- `new-cycles`
- `new-high-findings`
- `new-medium-findings`

This avoids baking all policy into bespoke flags.

---

## Behavior with baseline limitations

### Baseline from `analyze`, no findings
If baseline has no `:findings`, then finding-based checks are still possible if:
- we synthesize missing baseline findings as `[]`
- or optionally re-diagnose from baseline payload if enough data exists

For v1, keep it simple:
- if baseline has no findings, treat baseline finding set as empty
- therefore all current findings appear as newly added

This makes `diagnose`-based baselines the recommended workflow.

### Baseline schema mismatch
- missing `gordian/schema` → error
- unsupported future schema → error with guidance

### Source dirs differ
If baseline `:src-dirs` differ from current resolved dirs:
- do not fail automatically
- include a warning in output

The diff remains meaningful, but humans should know the comparison basis changed.

---

## Exit semantics

- `0` → all checks passed
- `1` → one or more checks failed
- `2` → invalid invocation / unreadable baseline / schema mismatch

This keeps CI behavior simple and scriptable.

---

## Rendering

### Text

```text
gordian gate — FAIL
baseline: gordian-baseline.edn
src: src/

CHECKS
  ✅ pc-delta            actual=+0.004 limit=+0.010
  ❌ new-cycles          actual=1      limit=0
  ✅ new-high-findings   actual=0      limit=0

SUMMARY
  2 passed, 1 failed
```

Optional: append a truncated compare summary below the checks.

### Markdown
Suitable for CI artifacts / PR comments.

```markdown
# gordian gate — FAIL

| Check | Status | Actual | Limit |
|-------|--------|--------|-------|
| pc-delta | ✅ pass | +0.004 | +0.010 |
| new-cycles | ❌ fail | 1 | 0 |
```

### EDN / JSON
Should expose the full gate result map for automation.

---

## Pure API design

Namespace: `gordian.gate`

### Functions

```clojure
(check-pc-delta diff limit)                 -> check
(check-new-cycles diff)                     -> check
(check-new-findings diff severity limit)    -> check
(resolve-checks opts diff)                  -> [check]
(gate-result checks)                        -> :pass | :fail
(summarize checks)                          -> {:passed n :failed n :total n}
(gate-report baseline-file diff checks)     -> map
```

### Rationale
- each check is independently testable
- rule composition is explicit
- CLI policy stays thin in `main.clj`

---

## `main.clj` integration

### Parse args
Add subcommand:

```text
gordian gate [<dir-or-src>...] --baseline <file> [options]
```

### Command flow
1. parse opts
2. require `--baseline`
3. resolve current source dirs normally
4. build current diagnose-style report
5. read baseline EDN file
6. `compare/compare-reports baseline current`
7. `gate/resolve-checks`
8. render and exit with pass/fail code

### Note
Unlike `compare`, `gate` compares saved baseline vs live current, not two files.

---

## Testing strategy

### Unit tests — `gate_test.clj`
- `check-pc-delta`
- `check-new-cycles`
- `check-new-findings`
- `gate-result`
- `gate-report`
- `resolve-checks` with defaults and explicit flags

### Main tests
- parse `gate` args
- error when `--baseline` missing
- machine output generation
- exit semantics may need testing at a lower seam than `System/exit`

### Output tests
- text output contains pass/fail summary
- markdown table renders correctly

---

## Design decisions

### Why baseline file first?
It is the simplest useful abstraction.
Teams can already commit a baseline EDN into the repo and use it in CI.
No git checkout complexity is needed.

### Why build current report live instead of requiring two files?
That is already covered by `compare`.
`gate` exists for automation on the current workspace.

### Why use `diagnose` semantics for current report?
Because gate needs findings, not just raw graph changes.
This gives sensible defaults for real CI use.

### Why not fail on every new medium finding by default?
Too noisy for adoption.
The first gate should be conservative enough that teams will actually enable it.

---

## Deferred extensions

### 1. `--ref` support

```bash
gordian gate --ref-main origin/main --max-pc-delta 0.01
```

Would compare current branch vs another git ref directly.
More complex due to temp worktrees / subprocesses.

### 2. Baseline update workflow

```bash
gordian gate --baseline gordian.edn --write-baseline
```

Useful but dangerous. Better left explicit outside the tool at first.

### 3. Category-specific checks
- `new-cross-lens-hidden`
- `new-hidden-change`
- `new-facade`
- `role-regressions`

### 4. Cluster-aware checks
- fail if a cluster exceeds size threshold
- fail if a cluster contains any new high finding

---

## Implementation plan

### Step 1
Create `gate.clj` with pure checks and report assembly.

### Step 2
Wire `gate` subcommand into `main.clj`.

### Step 3
Add text/markdown output functions in `output.clj`.

### Step 4
Add tests and CI-oriented examples.

### Step 5
Document in schema / README / PLAN / state.

---

## Success criteria

We are done when:
- `gordian gate --baseline base.edn` works end-to-end
- it returns useful pass/fail output
- it exits non-zero on regression
- it is fully machine-readable in EDN/JSON
- defaults are strict enough to matter, but not too noisy to adopt
