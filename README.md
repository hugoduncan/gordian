# gordian

Cut through tangled Clojure namespaces.

`gordian` is a [Babashka](https://babashka.org) script that analyzes the
`require` graph of a Clojure project and reports on how cross-coupled your
namespaces are. It surfaces the structural complexity that line counts and
cyclomatic metrics miss — the kind that makes a codebase hard to change, not
just hard to read.

## Requirements

- [Babashka](https://github.com/babashka/babashka) 1.3+
- Git (for change coupling analysis)

## Usage

Top-level usage now focuses on command discovery and global output/help flags:

```bash
gordian <command> [args] [options]
```

When given a project root (a directory containing `deps.edn`, `bb.edn`,
`project.clj`, etc.), gordian **auto-discovers** source directories —
including Polylith `components/*/src`, `bases/*/src`, etc. With no arguments
it defaults to `analyze` on the current directory.

```bash
gordian --help
```

Shows:
- top-level usage
- available commands and summaries
- global options only: `--json`, `--edn`, `--markdown`, `--help`

```bash
gordian <subcommand> --help
```

Shows scoped help for that command, including:
- command-specific usage
- positional arguments
- only the options relevant to that command
- examples for that command

Examples:

```bash
gordian diagnose --help
gordian complexity --help
gordian gate --help
```

```bash
# auto-discover from current directory
# no explicit command = analyze
# `gordian .` remains accepted for backward compatibility
gordian
gordian .

# discover commands / scoped help
gordian --help
gordian diagnose --help
gordian complexity --help

# ranked findings — auto-enables all analysis lenses
gordian diagnose
gordian diagnose .
gordian diagnose --edn > findings.edn

# auto-discover from a specific project
gordian /path/to/project

# include test directories
gordian . --include-tests

# dedicated test architecture analysis
gordian tests .
gordian tests src/ test/

# complexity analysis
gordian complexity .
gordian complexity src/ test/
gordian complexity . --edn > complexity.edn
gordian complexity . --markdown > complexity.md

# local comprehension complexity analysis
gordian local .
gordian local src/
gordian local . --sort abstraction --top 20
gordian local . --min total=12 --min working-set=3
gordian local . --edn > local.edn
gordian local . --markdown > local.md

# exclude namespaces by pattern
gordian . --exclude 'user|scratch'

# explicit source directories (no auto-discovery)
gordian src/
gordian analyze src/ test/

# machine-readable output
gordian --json > report.json
gordian --edn  > report.edn

# markdown output (shareable, PR-attachable)
gordian --markdown > report.md
gordian diagnose --markdown > findings.md

# dependency graph
gordian --dot deps.dot && dot -Tsvg deps.dot > deps.svg

# conceptual coupling — namespaces that share vocabulary
gordian --conceptual 0.30

# change coupling — namespaces that co-change in git history
gordian --change
gordian --change --change-since "90 days ago"
gordian --change /other/repo --change-since "2024-01-01"
```

### Project configuration

Create `.gordian.edn` in your project root for defaults:

```edn
{:src-dirs      ["components" "bases"]  ;; override auto-discovery
 :include-tests false
 :conceptual    0.20
 :change        true
 :change-since  "90 days ago"
 :exclude       ["user" ".*-scratch"]}
```

CLI flags override config values. Config `:src-dirs` replaces auto-discovery.

## Test architecture mode (`tests`)

`gordian tests` is a dedicated structural analysis of the test layer.
It auto-includes test directories when run from a project root.

```bash
gordian tests .
gordian tests src/ test/
gordian tests . --edn > tests.edn
gordian tests . --markdown > tests.md
```

It reports:
- production namespaces that depend on test namespaces
- executable tests vs shared test support namespaces
- unit-ish vs integration-ish test profiles
- core namespaces that gain direct test dependents
- propagation-cost delta from `src` to `src+test`

Interpretation notes:
- **Broad integration-style tests are normal.** Broad unit-style tests are suspicious.
- **Shared test support is normal.** Test support depended on by production code is suspicious.
- **`Ca` delta on core namespaces is a proxy** for whether stable core code is directly exercised by tests.
- **Propagation-cost delta** distinguishes targeted suites from over-coupled suites.

## Complexity mode (`complexity`)

`gordian complexity` analyzes local code metrics for top-level executable
bodies at arity granularity. In v1 it always computes both cyclomatic
complexity and lines of code (LOC).

Note: the legacy `gordian cyclomatic` CLI alias has been removed. Use
`gordian complexity`.

```bash
gordian complexity .
gordian complexity src/
gordian complexity --tests-only .
gordian complexity . --sort cc-risk --top 20
gordian complexity . --sort loc
gordian complexity . --sort ns --bar loc
gordian complexity . --min cc=10
gordian complexity . --min cc=10 --min loc=20
gordian complexity . --edn > complexity.edn
gordian complexity . --json > complexity.json
gordian complexity . --markdown > complexity.md
```

It reports:
- per-unit cyclomatic complexity and lines of code (`defn` arities, `defmethod`, top-level `def` + literal `fn`)
- namespace rollups over canonical units
- project rollup with risk-band counts plus LOC totals
- text/markdown summaries plus machine-readable canonical fields such as `:metrics`, `:scope`, `:options`, `:bar-metric`, `:units`, `:namespace-rollups`, `:project-rollup`, `:max-unit`, and per-unit `:cc` / `:loc`

Current counting rules:
- base complexity `1` per analyzed unit
- `if` / `when` family adds `1`
- `cond` counts every clause pair, including trailing `:else`
- `condp` counts every clause pair and default when present
- `case` counts every branch arm and default when present
- `cond->` counts each condition/form pair
- `and` / `or` add `operand-count - 1`
- each `catch` in `try` adds `1`
- `loop`, `recur`, recursion, `for`, `doseq`, and `while` do not independently add complexity

Scope and ranking controls:
- default with project discovery = source paths only
- `--tests-only` analyzes discovered test paths only
- explicit paths override discovery-based scope selection
- `--sort` supports `cc`, `loc`, `ns`, `var`, and `cc-risk`
- `--bar` supports `cc` and `loc` for human-readable histogram bars
- when `--bar` is omitted, bars default to `loc` for `--sort loc`, otherwise `cc`
- repeatable `--min metric=value` filters displayed unit rows only, e.g. `--min cc=10` or `--min loc=20`
- multiple `--min` constraints combine conjunctively
- namespace and project rollups remain computed from the full analyzed unit set
- `--top` truncates units and namespace-rollup sections independently

This mode complements Gordian's architectural analysis: it measures local
branching complexity within executable units rather than namespace coupling
across the system.

## Local comprehension complexity mode (`local`)

`gordian local` analyzes local executable units using the v1 Local
Comprehension Complexity (LCC) burden-vector model.

```bash
gordian local .
gordian local src/
gordian local --tests-only .
gordian local . --sort abstraction --top 20
gordian local . --sort ns --bar working-set
gordian local . --min total=12
gordian local . --min abstraction=4 --min working-set=3
gordian local . --edn > local.edn
gordian local . --json > local.json
gordian local . --markdown > local.md
```

It reports:
- per-unit burden vectors for top-level `defn` arities and `defmethod` bodies
- burden families: flow, state, shape, abstraction, dependency, working-set
- per-unit findings for high-signal local comprehension burdens
- namespace rollups over burden-family averages
- project rollup with average burdens and finding counts

Current v1 semantics:
- canonical units are top-level `defn` arities and `defmethod` bodies
- nested local helpers are folded into their enclosing top-level unit
- burden scoring is driven by explicit shared evidence rather than whole-tree proxy counts
- abstraction burden uses ordered main-path step labels with level-mix plus oscillation
- shape burden includes branch-outcome variant detection for nilability, coarse shape, and obvious map keyset differences
- dependency burden distinguishes opaque pipeline stages from helper-count presence to avoid double-counting the same stage twice
- working-set sampling uses explicit v1 program points: binding groups, branch entry, threaded stages, and main-path steps
- `regularity-burden` is intentionally omitted from the first executable slice
- `:working-set` reports `:peak`, `:avg`, and derived `:burden`
- raw burden families remain available directly on each unit
- `:lcc-total` is a calibrated headline rollup over normalized burden families
- calibration is derived deterministically from the analyzed unit set using per-family non-zero distribution scales

Scope and ranking controls mirror `complexity` where practical:
- default with project discovery = source paths only
- `--tests-only` analyzes discovered test paths only
- explicit paths override discovery-based scope selection
- `--sort` supports `total`, `flow`, `state`, `shape`, `abstraction`, `dependency`, `working-set`, `ns`, and `var`
- `--bar` supports `total`, `flow`, `state`, `shape`, `abstraction`, `dependency`, and `working-set`
- repeatable `--min metric=value` filters displayed unit rows only
- multiple `--min` constraints combine conjunctively
- namespace and project rollups remain computed from the full analyzed unit set
- `--top` truncates units and namespace-rollup sections independently

This mode complements `gordian complexity`: it focuses on local comprehension
burden for safe change rather than branch count and LOC alone.

## Install via bbin

```bash
bbin install io.github.hugoduncan/gordian
```

## Skills

The `skills/` directory contains AI agent skill definitions for working with gordian and for reasoning about Clojure namespace architecture generally.

| Skill | Description |
|---|---|
| `skills/gordian/` | How to invoke and interpret gordian — commands, flags, metrics, findings, workflow |
| `skills/gordian-plain/` | Same content in plain English (no lambda notation) |
| `skills/namespace-structure/` | Ideal Clojure namespace architecture principles (tool-agnostic) |
| `skills/namespace-structure-plain/` | Same content in plain English |

Skills can be installed into an AI agent using [Skills](https://github.com/vercel-labs/skills):

```bash
npx skills add https://github.com/hugoduncan/gordian/tree/master/skills/gordian
npx skills add https://github.com/hugoduncan/gordian/tree/master/skills/namespace-structure
```

## Development tasks

A few useful Babashka tasks for working on gordian itself:

```bash
bb test         # run test suite
bb kondo        # lint src/ and test/
bb fmt          # check formatting
bb fmt-fix      # apply formatting
bb ref-report   # report public fns used only by tests and fns that could be private
```

`bb ref-report` uses `clojure-lsp` analysis to separate references from `src/`
and `test/`, then prints three sections:
- functions used only in tests
- functions that look like privatization candidates
- unreferenced public functions

This is intended as a maintenance aid for tightening module API surfaces over time.

## What it measures

### Propagation cost

The fraction of the project that is *transitively reachable* on average from
any single namespace, as defined by MacCormack, Rusnak & Baldwin (2006).

> If you change one namespace at random, propagation cost tells you what
> percentage of the project might be affected.

A value of 0 means every namespace is completely independent.  A value of 1
means every namespace transitively reaches every other.  Well-modularised
projects typically sit below 0.20.

### reach

`|transitive-deps(n)| / N` — the share of the project this namespace
*depends on*, directly or indirectly (project-internal only, self excluded).

A high reach means the namespace sits near the top of the dependency stack
and would be impacted by changes anywhere below it.

### fan-in

`|{m : n ∈ transitive-deps(m)}| / N` — the share of the project that
*depends on* this namespace, directly or indirectly.

A high fan-in means many things rely on this namespace; changing it has
wide consequences, so it should be maximally stable.

### Ce — efferent coupling

Number of project namespaces this namespace *directly requires*.
High Ce means "depends on a lot" — a signal of fragility.

### Ca — afferent coupling

Number of project namespaces that *directly require* this namespace.
High Ca means "a lot depends on this" — a signal of responsibility.

### I — instability

`Ce / (Ca + Ce)` ∈ [0, 1], after Robert Martin (1994).

| I | Meaning |
|---|---------|
| 0.0 | Maximally **stable** — nothing it depends on, everything depends on it |
| 1.0 | Maximally **unstable** — depends on much, nothing depends on it |
| 0.5 | Balanced — equal afferent and efferent pressure |

Martin's *Stable Dependencies Principle* says: namespaces should depend
in the direction of increasing stability (lower I).  An unstable namespace
depending on a stable one is fine; the reverse creates fragility.

### role

2×2 classification based on *reach* and *fan-in* relative to their means
across the project (MacCormack, Baldwin & Rusnak, 2012):

| role | reach | fan-in | meaning |
|------|-------|--------|---------|
| `core` | low | high | Stable foundation — depended on by many, depends on little |
| `peripheral` | high | low | Leaf or entry point — depends on much, nothing depends on it |
| `shared` | high | high | Highly coupled in both directions |
| `isolated` | low | low | Loosely connected standalone namespace |

### cycles

Strongly connected components in the require graph detected by Tarjan's
algorithm.  Any namespace inside a cycle cannot be changed in isolation;
the entire cycle must be considered as a unit.  The section is omitted
when no cycles exist.

### Conceptual coupling (`--conceptual`)

Structural coupling (the metrics above) only sees `require` edges.
Conceptual coupling asks a different question: *do these namespaces talk
about the same things, regardless of whether they import each other?*

```bash
gordian src/ --conceptual 0.30
```

gordian extracts terms from namespace names, function names, and docstrings,
builds TF-IDF vectors for each namespace, and reports pairs whose cosine
similarity meets the threshold.  The **shared concepts** column names the
terms driving each similarity score.

```
conceptual coupling (sim ≥ 0.20):

namespace-a           namespace-b           sim   structural  shared concepts
─────────────────────────────────────────────────────────────────────────────
gordian.aggregate     gordian.close         0.35  no  ←    reach transitive node
gordian.conceptual    gordian.scan          0.25  no  ←    term per extract
```

**`←` (no structural edge)** — these namespaces share vocabulary but neither
requires the other.  This is the discovery signal: a shared concept that may
warrant a named abstraction, or evidence of hidden coupling not visible in the
require graph.

**`yes` (structural edge)** — the coupling is also conceptual.  The shared
terms confirm *what* the structural dependency is about.

A threshold of 0.20–0.30 works well for most projects.

### Change coupling (`--change`)

Structural and conceptual coupling both analyse the codebase as it stands
today.  Change coupling analyses the *git history*: namespaces that frequently
appear in the same commit are logically coupled, regardless of whether they
import each other.

```bash
gordian src/ --change                              # git log in current directory
gordian src/ --change /other/repo                  # git log elsewhere
gordian src/ --change --change-since "90 days ago" # limit history window
gordian src/ --change --change-since "2024-01-01"  # or an explicit date
```

gordian runs `git log`, maps changed file paths to namespace symbols, and
reports pairs ranked by Jaccard coupling
(`co-changes / (changes-a + changes-b - co-changes)`).

```
change coupling (Jaccard ≥ 0.30):

namespace-a           namespace-b           Jaccard  co  conf-a  conf-b  structural
───────────────────────────────────────────────────────────────────────────────────
gordian.main          gordian.output        0.4000   6   42.9%   85.7%  yes
gordian.conceptual    gordian.scan          0.3750   3   60.0%   50.0%  no  ←
```

**`←` (no structural edge)** — these namespaces co-change in version control
but neither requires the other.  This is the sharpest change-coupling signal:
an implicit contract not expressed in the `require` graph.

**`conf-a` / `conf-b`** — conditional probabilities: "when `a` changed, `b`
also changed X% of the time".  Asymmetric confidence (conf-a=100%, conf-b=20%)
signals a satellite: `a` always moves with `b`, but `b` moves independently.

**Horizon:** the full git history is used by default.  Old coupling from early
development survives indefinitely in raw counts even after a refactor.
`--change-since` scopes the window to recent pressure:

```bash
gordian src/ --change --change-since "90 days ago"
```

Research (Zimmermann et al. 2005) suggests recent history predicts future
coupling better than full history.  90 days is a reasonable starting point.

### Diagnose mode (`diagnose`)

`gordian diagnose` synthesises all available metrics into ranked findings
with severity levels. It auto-enables conceptual coupling (at threshold 0.15)
and change coupling so you don't have to remember the flags.

```bash
gordian diagnose
```

```
gordian diagnose — 11 findings
src: ./src

HEALTH
  propagation cost: 5.2% (healthy)
  cycles: none
  namespaces: 18

● MEDIUM  gordian.aggregate ↔ gordian.close
  hidden conceptual coupling — score=0.37
  shared terms: reach, transitive, node
  → no structural edge

● LOW  gordian.main
  high-reach hub — 94.4% of project reachable
  Ce=17 I=1.00 role=peripheral

11 findings (0 high, 3 medium, 8 low)
```

Finding categories:
- **cross-lens hidden** (high) — pair is hidden in both conceptual and change lenses
- **cycle** (high) — namespace cycle detected
- **hidden conceptual** (medium/low) — shared vocabulary, no structural edge
- **hidden change** (medium) — co-changing in git, no structural edge
- **SDP violation** (medium) — high Ca but high instability
- **god module** (medium) — shared role with extreme reach and fan-in
- **hub** (low) — very high reach, informational

### Explain mode (`explain` / `explain-pair`)

Drill into a specific namespace or pair to see everything gordian knows.

```bash
gordian explain gordian.scan
```

```
gordian explain — gordian.scan

  role: core    Ca=1  Ce=0  I=0.00
  reach: 0.0%   fan-in: 5.3%

DIRECT DEPENDENCIES (0 project, 2 external)
  project: (none)
  external: babashka.fs, edamame.core

DIRECT DEPENDENTS (1)
  gordian.main

CONCEPTUAL COUPLING (3 pairs)
  gordian.conceptual    score=0.30  hidden  shared: term, per, extract
  gordian.git           score=0.19  hidden  shared: file, clj, src
  gordian.main          score=0.15  structural  shared: file, scan, directory

CHANGE COUPLING (0 pairs)
  (none)

CYCLES: none
```

```bash
gordian explain-pair gordian.aggregate gordian.close
```

```
gordian explain-pair — gordian.aggregate ↔ gordian.close

STRUCTURAL
  direct edge: no
  shortest path: (none)

CONCEPTUAL
  score: 0.39
  shared terms: reach, node, transitive
  hidden: yes

CHANGE COUPLING
  (no data)

DIAGNOSIS
  ● MEDIUM — hidden conceptual coupling — score=0.39
```

## Example — gordian on itself

```
gordian

gordian — namespace coupling report
src: ./src

propagation cost: 0.0663  (on average 6.6% of project reachable per change)

namespace               reach   fan-in   Ce   Ca      I  role
─────────────────────────────────────────────────────────────
gordian.main           92.9%    0.0%   13    0  1.00  peripheral
gordian.aggregate       0.0%    7.1%    0    1  0.00  core
gordian.cc-change       0.0%    7.1%    0    1  0.00  core
gordian.classify        0.0%    7.1%    0    1  0.00  core
gordian.close           0.0%    7.1%    0    1  0.00  core
gordian.conceptual      0.0%    7.1%    0    1  0.00  core
gordian.dot             0.0%    7.1%    0    1  0.00  core
gordian.edn             0.0%    7.1%    0    1  0.00  core
gordian.git             0.0%    7.1%    0    1  0.00  core
gordian.json            0.0%    7.1%    0    1  0.00  core
gordian.metrics         0.0%    7.1%    0    1  0.00  core
gordian.output          0.0%    7.1%    0    1  0.00  core
gordian.scan            0.0%    7.1%    0    1  0.00  core
gordian.scc             0.0%    7.1%    0    1  0.00  core
```

Star topology: `gordian.main` is the single peripheral entry point (I=1) that
wires together independent pure modules.  No cycles, low propagation cost.

## Why the name

Alexander didn't untangle the Gordian knot. He cut it.

## References

- Martin, R. C. (1994). *OO Design Quality Metrics: An Analysis of Dependencies.*
- MacCormack, A., Rusnak, J., & Baldwin, C. Y. (2006). *Exploring the Structure of Complex Software Designs.* Management Science, 52(7).
- MacCormack, A., Baldwin, C. Y., & Rusnak, J. (2012). *Hidden Structure: Using Network Theory to Detect the Evolution of Software Architecture.* Research Policy, 41(8).
- Poshyvanyk, D., & Marcus, A. (2006). *The Conceptual Coupling Metrics for Object-Oriented Systems.* ICSM 2006.
- Zimmermann, T., Weißgerber, P., Diehl, S., & Zeller, A. (2005). *Mining Version Histories to Guide Software Changes.* IEEE Transactions on Software Engineering, 31(6).

## Machine-readable output schema

All `--edn` and `--json` output is wrapped in a standard envelope with
version, schema, command, lens parameters, and threshold metadata.
See [doc/schema.md](doc/schema.md) for the complete output contract.

## Status

Alpha. Output shape is documented at schema version 1. Additive changes
(new keys) are non-breaking. Breaking changes bump the schema integer.

## License

EPL-2.0
