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
  --dot  <file>   Write Graphviz DOT graph to <file>
  --json          Output JSON to stdout (suppresses human-readable table)
  --edn           Output EDN to stdout (suppresses human-readable table)
  --help          Show this help message
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

## Example — gordian on itself

```
gordian analyze src/

gordian — namespace coupling report
src: src/

propagation cost: 0.0900  (on average 9.0% of project reachable per change)

namespace               reach   fan-in   Ce   Ca      I  role
─────────────────────────────────────────────────────────────
gordian.main           90.0%    0.0%    9    0  1.00  peripheral
gordian.aggregate       0.0%   10.0%    0    1  0.00  core
gordian.classify        0.0%   10.0%    0    1  0.00  core
gordian.close           0.0%   10.0%    0    1  0.00  core
gordian.dot             0.0%   10.0%    0    1  0.00  core
gordian.edn             0.0%   10.0%    0    1  0.00  core
gordian.json            0.0%   10.0%    0    1  0.00  core
gordian.metrics         0.0%   10.0%    0    1  0.00  core
gordian.output          0.0%   10.0%    0    1  0.00  core
gordian.scan            0.0%   10.0%    0    1  0.00  core
gordian.scc             0.0%   10.0%    0    1  0.00  core
```

Star topology: `gordian.main` is the single entry point (peripheral, I=1)
that wires together ten independent pure modules (all core, I=0).  No
cycles, low propagation cost.

## Why the name

Alexander didn't untangle the Gordian knot. He cut it.

## References

- Martin, R. C. (1994). *OO Design Quality Metrics: An Analysis of Dependencies.*
- MacCormack, A., Rusnak, J., & Baldwin, C. Y. (2006). *Exploring the Structure of Complex Software Designs.* Management Science, 52(7).
- MacCormack, A., Baldwin, C. Y., & Rusnak, J. (2012). *Hidden Structure: Using Network Theory to Detect the Evolution of Software Architecture.* Research Policy, 41(8).

## Status

Alpha. Metrics and output format may change.

## License

EPL-2.0
