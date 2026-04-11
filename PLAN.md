# Gordian — Roadmap

Sequenced by leverage and dependency order. Items later in the list often
depend on earlier ones being done first.

---

## 1. Schema normalization

**Status:** ✅ done (63fe1e6)

All coupling pair records now share a common shape:
```clojure
{:ns-a sym :ns-b sym :score float :kind keyword :structural-edge? bool}
```
Conceptual pairs: `:sim` → `:score`, `:kind :conceptual`.
Change pairs: `:coupling` → `:score`, `:kind :change`.
Lens-specific evidence keys (`:shared-terms`, `:co-changes`, `:confidence-a/b`)
remain as flat siblings.

Design doc: `doc/design/001-schema-normalization.md`

Implementation note: the design planned 5 separate steps but pipeline coupling
(producer → output → integration tests) meant the right atomic unit was all
producers + consumers together in one commit. 87 tests, 689 assertions.

---

## 2. `gordian .` auto-discovery + `.gordian.edn` config

**Status:** ✅ done (35c5ccf)

**Auto-discovery:** When given a directory (or `.`), gordian should detect
common Clojure project layouts and infer src dirs without requiring the user
to list them manually.

Layouts to detect:
- `src/` — plain leiningen / deps.edn
- `test/` — included with `--include-tests`
- `components/*/src`, `components/*/test` — Polylith
- `bases/*/src`, `bases/*/test` — Polylith
- `extensions/*/src` — Polylith

Flags:
- `--include-tests` — also scan test dirs (default: src only)
- `--exclude REGEX` — drop matching namespace patterns

**Config file `.gordian.edn`:** Project-local defaults so `gordian .` just
works in CI and day-to-day use.

```edn
{:src-dirs          ["components" "bases" "extensions"]
 :include-tests?    false
 :conceptual        0.20
 :change-since      "90 days ago"
 :exclude-ns        [#"user" #".*-scratch"]}
```

Config is merged with CLI flags; CLI flags win.

Scope:
- `main.clj` — directory-mode detection, config file loading
- new `discover.clj` — layout detection logic (pure)
- `scan.clj` — accept `:exclude-ns` filter

---

## 3. `gordian diagnose .` — summary / ranked findings

**Status:** ✅ done (6785576)

Today gordian emits raw metrics. The most common follow-up is interpretation:
what are the worst namespaces? What are the hidden couplings worth acting on?

Add a `diagnose` subcommand (also reachable as `--summary`) that synthesises
all active lenses into a ranked, human-readable findings report.

Sections:
- Propagation cost + cycle count (top-line health)
- Top high-reach namespaces
- Top high-fan-in namespaces
- Shared / god-module candidates (high Ca + high instability)
- Hidden conceptual pairs (high score, no structural edge)
- Hidden change-coupled pairs (high score, no structural edge)
- Test structure warnings (if `--include-tests`)

Findings should be ranked by severity and annotated with a brief reason.

Example output shape:
```
FINDINGS (gordian diagnose)

[high]  gordian.scan  reach=8, fan-in=4 — high-reach hub
[med]   gordian.close ↔ gordian.aggregate  conceptual sim=0.31, no structural edge
[low]   gordian.dot  Ca=0, Ce=1, I=1.0 — fully peripheral, low risk
```

Scope:
- new `diagnose.clj` — ranking + classification logic (pure)
- `output.clj` — `format-diagnose`
- `main.clj` — `diagnose` subcommand routing

---

## 4. `gordian explain <ns>` / `gordian explain-pair <ns-a> <ns-b>`

**Status:** ✅ done (0530a50)

Turns gordian from a detector into an investigation tool. Given a namespace
or a pair, emit everything gordian knows about it.

`explain <ns>`:
- direct deps (requires)
- direct dependents (fan-in)
- transitive reach (how many ns reachable from it)
- propagation cost
- Ca / Ce / instability
- role (core / peripheral / shared / isolated)
- conceptual pairs it appears in (and shared terms)
- change-coupled pairs it appears in (and co-change commit count)

`explain-pair <ns-a> <ns-b>`:
- structural edge? yes/no
- shortest dependency path (if any)
- conceptual score + shared terms
- change coupling score + co-change commit count
- combined signal summary

Scope:
- new `explain.clj` — drill-down query functions (pure)
- `output.clj` — `format-explain-ns`, `format-explain-pair`
- `main.clj` — `explain` / `explain-pair` subcommand routing

---

## 5. Markdown output

**Status:** ✅ done (2785842)

A middle format between terminal output (ephemeral) and EDN/JSON (machine).
Markdown is shareable, PR-attachable, and renderable in most editors.

```bash
gordian detect .   --markdown > report.md
gordian diagnose . --markdown > findings.md
```

The markdown report mirrors the terminal output structure but uses headers,
tables, and fenced code blocks instead of box-drawing characters.

Scope:
- `output.clj` — `format-*-md` variants, or a rendering pass over the same
  data structures
- `main.clj` — `--markdown` flag

---

## Deferred (v2)

These are real features but require more design or have higher implementation
cost. Revisit once the above are stable.

### Cluster detection (`gordian clusters .`)
Group namespaces into subsystems by coupling density (structural + conceptual
+ change). Output: cluster members, dominant terms, internal coupling density,
hidden-vs-structural ratio. Requires graph community detection algorithm
(e.g. label propagation or modularity optimisation).

### Diff / baseline mode (`gordian diff before.edn after.edn`)
Compare two `--edn` snapshots. Highlight: propagation cost delta, new cycles,
new hidden coupling pairs, namespaces whose reach/fan-in changed materially.
Depends on schema normalization (item 1) being complete.

### Change window comparison (`--compare-windows`)
Classify change coupling findings as active coupling, historical scar,
improving, or worsening by comparing two time windows. Mostly git query
plumbing on top of the existing change coupling logic.

### Test mode (`gordian tests .`)
Dedicated analysis of test architecture: test ns depended on by src ns, high-
reach tests, tests that pull too much of the system, core ns with suspiciously
low test fan-in.

### Presets (`--preset quick|audit|full|tests`)
Named flag bundles. Nice-to-have once the individual flags are stable.
