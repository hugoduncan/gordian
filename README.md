# gordian

Cut through tangled Clojure namespaces.

`gordian` is a [Babashka](https://babashka.org) script that analyzes the
`require` graph of a Clojure project and reports on how cross-coupled your
namespaces are. It surfaces the structural complexity that line counts and
cyclomatic metrics miss — the kind that makes a codebase hard to change, not
just hard to read.

## What it measures

| Metric | Description |
|---|---|
| **Ca** | Afferent coupling — how many project namespaces require this one |
| **Ce** | Efferent coupling — how many project namespaces this one requires |
| **I** | Instability — `Ce / (Ca + Ce)` ∈ [0, 1] |
| **Propagation cost** | Fraction of project transitively reachable per change (MacCormack 2006) |
| **Cycles** | Strongly connected components in the require graph (Tarjan) |
| **Role** | `core` / `peripheral` / `shared` / `isolated` (MacCormack 2012) |

## Requirements

- [Babashka](https://github.com/babashka/babashka) 1.3+

## Usage

```bash
bb gordian analyze src/
# or: bb gordian src/
```

Writes to stdout and produces two sidecar files:
- `gordian-report.dot` — Graphviz DOT graph (nodes coloured by role)
- `gordian-report.json` — machine-readable report

### Example — gordian on itself

```
gordian — namespace coupling report
src: src/

propagation cost: 0.0900  (on average 9.0% of project reachable per change)

cycles: none

namespace               reach   fan-in   Ce   Ca      I  role
─────────────────────────────────────────────────────────────
gordian.main           90.0%    0.0%    9    0  1.00  peripheral
gordian.aggregate       0.0%   10.0%    0    1  0.00  core
gordian.classify        0.0%   10.0%    0    1  0.00  core
gordian.close           0.0%   10.0%    0    1  0.00  core
gordian.dot             0.0%   10.0%    0    1  0.00  core
gordian.json            0.0%   10.0%    0    1  0.00  core
gordian.metrics         0.0%   10.0%    0    1  0.00  core
gordian.output          0.0%   10.0%    0    1  0.00  core
gordian.scan            0.0%   10.0%    0    1  0.00  core
gordian.scc             0.0%   10.0%    0    1  0.00  core
```

Star topology: `gordian.main` wires everything together; all other modules
are pure, independent, and maximally stable.

## Install via bbin

```bash
bbin install io.github.hugoduncan/gordian
gordian analyze src/
```

## Why the name

Alexander didn't untangle the Gordian knot. He cut it.

## Status

Alpha. Metrics and output format may change.

## License

EPL-2.0
