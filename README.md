# gordian

Cut through tangled Clojure namespaces.

`gordian` is a [Babashka](https://babashka.org) script that analyzes the
`require` graph of a Clojure project and reports on how cross-coupled your
namespaces are. It surfaces the structural complexity that line counts and
cyclomatic metrics miss — the kind that makes a codebase hard to change, not
just hard to read.

## What it measures

- **Afferent / efferent coupling** (Ca, Ce) per namespace
- **Instability** — `Ce / (Ca + Ce)`
- **Propagation cost** — the fraction of the project reachable through the
  transitive `require` graph (à la MacCormack, Rusnak & Baldwin, 2006)
- **Cycles** — strongly connected components in the namespace graph
- **Core / periphery** classification

## Requirements

- [Babashka](https://github.com/babashka/babashka) 1.3+

## Usage

```bash
bb gordian analyze src/
```

Outputs a summary table, a DOT file of the dependency graph, and a JSON report
for further tooling.

Install as a [bbin](https://github.com/babashka/bbin) tool:

```bash
bbin install io.github.you/gordian
```

## Why the name

Alexander didn't untangle the Gordian knot. He cut it.

## Status

Alpha. Metrics and output format may change.

## License

EPL-2.0
