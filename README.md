# gordian

Cut through tangled Clojure namespaces.

`gordian` is a [Babashka](https://babashka.org) script that analyzes the
`require` graph of a Clojure project and reports on how cross-coupled your
namespaces are. It surfaces the structural complexity that line counts and
cyclomatic metrics miss — the kind that makes a codebase hard to change, not
just hard to read.

## Requirements

- [Babashka](https://github.com/babashka/babashka) 1.3+

## Usage

```bash
gordian [analyze] <src-dir>... [options]

Options:
  --dot  <file>         Write Graphviz DOT graph to <file>
  --json                Output JSON to stdout (suppresses human-readable table)
  --edn                 Output EDN to stdout (suppresses human-readable table)
  --conceptual <float>  Conceptual coupling analysis at given similarity threshold
  --help                Show this help message
```

```bash
# single source tree
gordian src/

# source and tests together
gordian analyze src/ test/

# machine-readable output
gordian src/ --json > report.json
gordian src/ --edn  > report.edn

# dependency graph
gordian src/ --dot deps.dot && dot -Tsvg deps.dot > deps.svg

# conceptual coupling — namespaces that share vocabulary
gordian src/ --conceptual 0.30
```

## Install via bbin

```bash
bbin install io.github.hugoduncan/gordian
```

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
gordian.close         gordian.aggregate     0.31  no  ←    reach transitive close
gordian.scan          gordian.main          0.23  yes      file src read
```

**`←` (no structural edge)** — these namespaces share vocabulary but neither
requires the other.  This is the discovery signal: a shared concept that may
warrant a named abstraction, or evidence of hidden coupling not visible in the
require graph.

**`yes` (structural edge)** — the coupling is also conceptual.  The shared
terms confirm *what* the structural dependency is about.  Structural dependencies
whose shared terms look unrelated are worth scrutinising.

A threshold of 0.20–0.30 works well for most projects.  Start lower to see
more pairs; raise it to focus on the strongest signals.

## Example — gordian on itself

```
gordian src/

gordian — namespace coupling report
src: src/

propagation cost: 0.0833  (on average 8.3% of project reachable per change)

namespace               reach   fan-in   Ce   Ca      I  role
─────────────────────────────────────────────────────────────
gordian.main           91.7%    0.0%   11    0  1.00  peripheral
gordian.scan            8.3%    8.3%    1    1  0.50  peripheral
gordian.aggregate       0.0%    8.3%    0    1  0.00  isolated
gordian.classify        0.0%    8.3%    0    1  0.00  isolated
gordian.close           0.0%    8.3%    0    1  0.00  isolated
gordian.conceptual      0.0%   16.7%    0    2  0.00  core
gordian.dot             0.0%    8.3%    0    1  0.00  isolated
gordian.edn             0.0%    8.3%    0    1  0.00  isolated
gordian.json            0.0%    8.3%    0    1  0.00  isolated
gordian.metrics         0.0%    8.3%    0    1  0.00  isolated
gordian.output          0.0%    8.3%    0    1  0.00  isolated
gordian.scc             0.0%    8.3%    0    1  0.00  isolated
```

Star topology: `gordian.main` is the single peripheral entry point (I=1) that
wires together independent pure modules.  `gordian.conceptual` is `core`
(Ca=2) — both `scan` and `main` depend on it.  No cycles, low propagation cost.

## Why the name

Alexander didn't untangle the Gordian knot. He cut it.

## References

- Martin, R. C. (1994). *OO Design Quality Metrics: An Analysis of Dependencies.*
- MacCormack, A., Rusnak, J., & Baldwin, C. Y. (2006). *Exploring the Structure of Complex Software Designs.* Management Science, 52(7).
- MacCormack, A., Baldwin, C. Y., & Rusnak, J. (2012). *Hidden Structure: Using Network Theory to Detect the Evolution of Software Architecture.* Research Policy, 41(8).
- Poshyvanyk, D., & Marcus, A. (2006). *The Conceptual Coupling Metrics for Object-Oriented Systems.* ICSM 2006.

## Status

Alpha. Metrics and output format may change.

## License

EPL-2.0
