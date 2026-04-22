# Practical guide to Gordian

This document explains how to use Gordian's metrics and commands to make
concrete architectural improvements. The examples are drawn from Gordian's own
self-analysis, but the guidance is intended for real codebases: application
code, libraries, monorepos, and Polylith-style layouts.

It covers:
- whole-project analysis (`analyze`)
- hidden coupling lenses (`--conceptual`, `--change`)
- triage (`diagnose`)
- drill-down (`explain`, `explain-pair`)
- subsystem views (`subgraph`)
- latent architecture groups (`communities`)
- local executable-unit metrics (`complexity`)
- local comprehension burden analysis (`local`)
- before/after deltas (`compare`)
- CI ratchets (`gate`)

---

## 1. Start with the default workflow

In most projects, this sequence gives the highest signal with the least effort:

```bash
gordian
gordian diagnose
gordian explain <namespace>
gordian explain-pair <ns-a> <ns-b>
```

If you are working on a subsystem rather than the whole codebase:

```bash
gordian subgraph <prefix>
```

If you want to enforce architectural direction in CI:

```bash
gordian diagnose --edn > gordian-baseline.edn
gordian gate --baseline gordian-baseline.edn
```

If you want to compare two snapshots directly:

```bash
gordian --edn > before.edn
# make changes
gordian --edn > after.edn
gordian compare before.edn after.edn
```

If the problem is not architecture but local code shape, switch commands:

```bash
gordian complexity .
gordian local .
```

Use them for different questions:
- `complexity` — which executable units are large or branch-heavy?
- `local` — which executable units are hard to understand or modify safely?

A practical rule:
- namespace / subsystem / dependency question → `diagnose`, `explain`, `subgraph`
- branch-count / LOC question → `complexity`
- comprehension-burden / helper-chasing / working-set question → `local`

---

## 2. Project discovery and setup

Gordian can analyze explicit source directories, but in normal use you should
usually point it at the project root or just run it with no arguments.

```bash
gordian
gordian .
gordian /path/to/project
```

When run at a project root, Gordian auto-discovers source trees from common
Clojure layouts:
- `src/`
- `test/` when `--include-tests` is enabled
- Polylith `components/*/src`, `bases/*/src`, etc.
- layouts rooted by `deps.edn`, `bb.edn`, `project.clj`, `workspace.edn`

Useful flags:

```bash
gordian . --include-tests
gordian . --exclude 'user|scratch|dev'
```

You can also set project defaults in `.gordian.edn`:

```edn
{:include-tests false
 :conceptual    0.20
 :change        true
 :change-since  "90 days ago"
 :exclude       ["user" ".*-scratch"]}
```

Use config for local defaults; use CLI flags for one-off investigation.

---

## 3. The whole-project report at a glance

```bash
gordian analyze src/
```

Example:

```
propagation cost: 0.0663  (on average 6.6% of project reachable per change)

namespace               reach   fan-in   Ce   Ca      I  role
─────────────────────────────────────────────────────────────
gordian.main           92.9%    0.0%   13    0  1.00  peripheral
gordian.aggregate       0.0%    7.1%    0    1  0.00  core
gordian.classify        0.0%    7.1%    0    1  0.00  core
...
```

**Propagation cost** is the headline number: the average fraction of the
project transitively reachable from any namespace.

Everything else is per-namespace detail:
- **reach** — how much of the project this namespace depends on, transitively
- **fan-in** — how much of the project depends on this namespace, transitively
- **Ce** — direct outgoing project dependencies
- **Ca** — direct incoming project dependencies
- **I** — instability (`Ce / (Ca + Ce)`)
- **role** — `core`, `peripheral`, `shared`, or `isolated`

### A quick reading strategy

Read the report in this order:
1. cycles
2. propagation cost
3. high-`Ca` namespaces
4. `:shared` namespaces
5. high-instability namespaces with high `Ca`
6. odd `peripheral` namespaces with very high reach

That sequence catches most structural problems quickly.

---

## 4. Structural metrics: what to do with them

## 4.1 Start with cycles

If the cycles section is present, fix those first.

A cycle means the participating namespaces cannot be changed independently.
They are one design unit whether or not the file layout admits it.

Fix options, in order of preference:

1. **Merge them** if they are truly one thing
2. **Extract a shared dependency** that both currently depend on
3. **Invert the dependency** via protocol, callback, or data contract

A cycle is not merely a cleanliness issue. It blocks independent evolution.
Everything else in the report is easier to interpret after cycles are removed.

## 4.2 Read propagation cost as architectural drag

Propagation cost answers:

> If one namespace changes, how much of the project tends to sit in its
> transitive blast radius?

Rules of thumb:
- below `0.20` — typically healthy
- `0.20–0.30` — worth review
- above `0.30` — strong signal of spreading change cost

The number is most useful as a **trend**:

```bash
gordian --edn > before.edn
# refactor
gordian --edn > after.edn
gordian compare before.edn after.edn
```

If propagation cost went down, modularity improved. If it went up, some new
edge or cluster of edges increased the project's transitive connectivity.

## 4.3 Check the instability gradient

Healthy systems tend to have a stability gradient:
- foundations near `I=0`
- orchestration and entry points near `I=1`

Smell:

```
my.app.domain    reach=35%  fan-in=60%   Ce=7  Ca=12  I=0.37  shared
```

If many things depend on a namespace but it itself depends on much, it violates
Robert Martin's Stable Dependencies Principle.

Typical fix:
- extract a stable core model or protocol layer
- isolate infrastructure or orchestration concerns in separate namespaces
- make the highly depended-on part require less

## 4.4 Investigate `:shared` namespaces

`:shared` means high reach and high fan-in.

Some are legitimate:
- central domain protocols
- shared data model layers
- intentional integration hubs

But many `:shared` namespaces indicate god-module drift.

Questions to ask:
- Does this namespace have one clear responsibility?
- Is it both depended on and depending on much because it mixes layers?
- Is it serving as a convenience dumping ground?

If yes, split by reason to change.

## 4.5 Use family-scoped Ca/Ce to avoid false alarms

Modern projects often have families of namespaces, e.g.:
- `my.app.invoice.*`
- `my.app.user.*`
- `psi.agent-session.*`

Raw `Ca`/`Ce` can make a façade namespace look worse than it is. A family entry
point that delegates inward will naturally have many intra-family relationships.

Gordian now exposes family-aware structure:
- `ca-family`
- `ca-external`
- `ce-family`
- `ce-external`

Interpretation:
- **high `ca-external`, low `ce-external`** often means a healthy façade
- **high `ca-external`, high `ce-external`** is more suspicious
- **high internal traffic only** may just reflect family organization

This matters especially in `diagnose`, where Gordian distinguishes true god
modules from façade-shaped modules.

---

## 5. Conceptual coupling: shared language without edges

Structural coupling only sees `require` edges. Conceptual coupling asks whether
namespaces talk about the same concepts, whether or not they import one another.

```bash
gordian . --conceptual 0.30
```

Gordian extracts terms from:
- namespace names
- function names
- docstrings

Then it builds TF-IDF vectors and reports pairs above the chosen similarity
threshold.

Example:

```
conceptual coupling (sim ≥ 0.20):

namespace-a           namespace-b           sim   structural  shared concepts
─────────────────────────────────────────────────────────────────────────────
gordian.aggregate     gordian.close         0.35  no  ←    reach transitive node
gordian.main          gordian.scan          0.23  yes      file scan read
```

### How to read it

- **`yes` structural edge** — the code dependency is also conceptually aligned
- **`←` no structural edge** — the pair shares vocabulary but the dependency
  graph does not acknowledge it

The `←` rows are the discovery signal.

### Three common patterns

**1. Hidden concept**

```
my.app.invoice    my.app.payment    0.45  no  ←   amount due total format
```

Likely causes:
- missing shared abstraction
- duplicate domain logic
- shared data concept with no home

**2. Expected conceptual overlap**

```
my.app.invoice    my.app.invoice-pdf    0.61  yes   invoice line total format
```

This is usually fine. The dependency and the vocabulary agree.

**3. Suspicious structural edge**

```
my.app.render    my.app.database    0.38  yes   row col format table
```

The dependency exists, but the shared language hints that concepts may be
bleeding across layers.

### Family-noise suppression

A common false positive is family naming noise:

```
my.app.invoice.api    my.app.invoice.db
```

These may appear conceptually similar simply because they share the family
prefix `invoice`.

Gordian now separates:
- **family terms** — overlap explained by namespace prefix naming
- **independent terms** — overlap not explained by shared prefix

Interpretation:
- same family + no independent terms = likely naming noise
- same family + independent terms = real shared concept within a subsystem

This is especially useful in `diagnose` and `explain-pair`, where not all
hidden conceptual pairs deserve the same urgency.

### Threshold guidance

| threshold | meaning |
|-----------|---------|
| 0.15–0.25 | exploratory, many weak signals |
| 0.25–0.40 | good routine audit range |
| 0.40+     | only strongest conceptual overlap |

Start at `0.20` when learning a codebase. Raise it when you want a shorter,
higher-confidence list.

---

## 6. Change coupling: what the codebase keeps changing together

Change coupling analyzes git history instead of current code structure.

```bash
gordian . --change
gordian . --change --change-since "90 days ago"
gordian . --change /other/repo
```

Example:

```
change coupling (Jaccard ≥ 0.30):

namespace-a           namespace-b           Jaccard  co  conf-a  conf-b  structural
───────────────────────────────────────────────────────────────────────────────────
gordian.main          gordian.output        0.4000   6   42.9%   85.7%  yes
gordian.conceptual    gordian.scan          0.3750   3   60.0%   50.0%  no  ←
```

### The columns

- **Jaccard** — symmetric co-change score
- **co** — raw co-change count
- **conf-a / conf-b** — directional conditional probabilities
- **structural** — whether a direct code edge exists

### What it reveals

**1. Active implicit coupling**

```
my.app.invoice    my.app.pdf    0.72  no  ←
```

These files evolve together but the require graph does not explain why.
Typical causes:
- hidden shared data shape
- protocol or dispatch contract
- feature split across files that should be closer together

**2. Historical scar**

A pair may show in full history but vanish under `--change-since "90 days ago"`.
That often means the coupling used to be real and has already been refactored
away.

**3. Satellite coupling**

```
my.app.emails    my.app.templates    J=0.55  conf-a=95%  conf-b=30%
```

`emails` almost always moves with `templates`, but `templates` often moves
independently. That suggests a narrow client riding on a broad, unstable surface.

### Windowing matters

Use a recent window to separate active architectural pressure from archaeology:

```bash
gordian . --change --change-since "90 days ago"
```

Recent history is often the most actionable history.

---

## 7. Combine the three lenses

The strongest architectural signal usually comes from **agreement across
lenses**.

| Lens | Sees | Misses |
|------|------|--------|
| Structural | code edges today | history, dynamic/data coupling |
| Conceptual | shared language today | intentionality |
| Change | co-evolution over time | current code shape |

Most actionable cases:

### 7.1 Hidden in both conceptual and change lenses

```
my.app.invoice    my.app.payment   conceptual=yes, change=yes, structural=no
```

This often means:
- real coupling
- active coupling
- unnamed coupling

A missing abstraction is likely.

### 7.2 Structural edge with no support from either other lens

```
my.app.render    my.app.database
structural=yes, conceptual=no, change=no
```

This can indicate:
- vestigial dependency
- very thin dependency with low design importance
- leftover wiring from removed behavior

These edges are worth pruning if unnecessary.

---

## 8. Diagnose mode: triage before deep reading

`diagnose` is the best default command once you already trust the raw metrics.
It synthesizes structure, conceptual overlap, and change history into ranked
findings.

```bash
gordian diagnose
```

`diagnose` auto-enables:
- conceptual coupling at a default exploratory threshold
- change coupling

Example:

```
gordian diagnose — 11 findings

HEALTH
  propagation cost: 5.2% (healthy)
  cycles: none
  namespaces: 18

● MEDIUM  gordian.aggregate ↔ gordian.close
  hidden conceptual coupling — score=0.37
  shared terms: reach, transitive, node
  → no structural edge
```

### Finding categories

Common categories include:
- **cycle** — structural cycle
- **cross-lens-hidden** — hidden in conceptual and change lenses
- **hidden-conceptual** — conceptually coupled, no direct edge
- **hidden-change** — co-changing, no direct edge
- **sdp-violation** — stable namespace depending outward too much
- **god-module** — extreme shared centrality
- **facade** — looks central but is façade-shaped, usually lower concern
- **hub** — very high reach, usually informational

### Use `diagnose` as a queue, not a verdict oracle

`diagnose` is best used to answer:
- what should I inspect first?
- which pairs are surprising?
- where is the architectural risk concentrated?

Then move to `explain`, `explain-pair`, or `subgraph`.

### Severity vs actionability

Severity tells you how concerning a finding is in the abstract.
Actionability tells you how likely it is to be worth working on now.

```bash
gordian diagnose --rank severity
gordian diagnose --rank actionability
```

Use:
- **severity** when doing architecture review
- **actionability** when planning practical refactor work

Actionability helps avoid spending all your time on theoretically serious but
hard-to-move issues while ignoring easy high-leverage improvements.

### Clusters: groups of related findings

A codebase often produces several findings that all point to the same local knot.
Gordian clusters findings by shared namespace involvement.

Use clusters when you want to answer:
- is this one issue or five symptoms of one issue?
- which namespaces form the local refactor surface?

This is especially helpful when planning a single refactor story.

---

## 9. Explain mode: drill into one namespace

After `diagnose`, use `explain` on anything interesting.

```bash
gordian explain gordian.scan
```

This shows:
- metrics for the namespace
- direct project deps and external deps
- direct dependents
- conceptual pairs involving the namespace
- change pairs involving the namespace
- cycle participation

Use it to answer:
- who depends on this?
- what does it depend on directly?
- is it coupled by language, by history, or both?
- if I change this namespace, where should I look next?

### What `explain` is good for

**A suspicious hub**
- check whether high reach is expected orchestration
- inspect whether dependents are broad or local

**An SDP violation**
- inspect the direct outward deps
- identify which one should be inverted or extracted

**A core namespace**
- verify it is actually stable
- see whether tests depend on it directly

---

## 10. Explain-pair: decide what a pair means

`explain-pair` is the best tool when you have a suspicious pair from conceptual
or change coupling.

```bash
gordian explain-pair gordian.aggregate gordian.close
```

It combines:
- structural relationship
- shortest path if one exists
- conceptual score and shared terms
- change coupling data if present
- any diagnose finding for the pair
- a verdict

### Why the verdict matters

Raw facts are useful, but they still leave an interpretation burden. Gordian now
adds a verdict category to summarize what the pair most likely means.

Typical verdicts:
- `expected-structural`
- `family-naming-noise`
- `family-siblings`
- `likely-missing-abstraction`
- `hidden-conceptual`
- `hidden-change`
- `transitive-only`
- `unrelated`

### How to use the verdicts

**`expected-structural`**
- direct edge exists
- usually low urgency unless other signals are extreme

**`family-naming-noise`**
- hidden conceptual overlap explained entirely by shared prefix naming
- usually ignore

**`family-siblings`**
- hidden overlap inside one namespace family with real independent terms
- inspect if the family feels repetitive or scattered

**`likely-missing-abstraction`**
- strongest hidden-coupling signal
- high-value refactor candidate

**`hidden-change`**
- co-change without structural or conceptual support
- often vestigial workflow coupling or implicit contract

**`transitive-only`**
- path exists but no strong hidden signal
- usually informational

This command is often the fastest way to decide whether a suspicious pair is a
real design smell or just benign local structure.

---

## 11. Subgraph mode: analyze one subsystem properly

Most day-to-day work happens inside one family or subsystem, not across the
entire codebase.

```bash
gordian subgraph gordian
gordian subgraph psi.agent-session --rank actionability
```

Subgraph mode answers questions like:
- what namespaces belong to this family?
- how dense is the internal graph?
- are there cycles inside it?
- what crosses its boundary?
- what hidden conceptual or change couplings touch it?
- which local findings matter most?

It reports:
- members
- induced internal metrics (nodes, edges, density, local propagation cost)
- internal cycles
- incoming/outgoing boundary edges
- conceptual and change pairs sliced into `internal` vs `touching`
- findings touching the family
- local clusters of those findings

### When to use subgraph instead of whole-project analysis

Use `subgraph` when:
- a team owns a namespace family
- you are refactoring one bounded area
- whole-project output is too broad
- you want local coupling, not global ranking

### Explain fallback

`explain` now falls back to subgraph mode when you give it a family prefix that
is not an exact namespace:

```bash
gordian explain psi.agent-session
```

If `psi.agent-session` is not itself a concrete namespace but matches several
family members, Gordian returns the subgraph view.

That makes exploratory use much smoother.

### What to look for in a subgraph

- **high internal density** — subsystem is tightly interdependent
- **many outgoing edges** — subsystem leaks outward or depends on too much
- **many incoming edges** — subsystem is an important service boundary
- **many touching hidden pairs** — family boundary may be wrong or incomplete
- **local cycles** — the subsystem is internally tangled even if the global
  graph still looks manageable

---

## 12. Communities: discover latent architecture groups

Subgraph starts with a known prefix. `communities` starts with the graph and
asks what groups naturally exist.

```bash
gordian communities
gordian communities --lens structural
gordian communities --lens conceptual --threshold 0.20
gordian communities --lens change --threshold 0.30
gordian communities --lens combined
```

Communities are discovered from an undirected weighted graph built from one
lens or a combination of lenses.

The output highlights:
- community members
- density
- internal weight
- boundary weight
- dominant terms
- likely bridge namespaces

### When communities is useful

Use `communities` when you want to answer:
- what subsystems exist even if they are not namespace-prefix aligned?
- which namespaces form a natural cluster?
- what are the bridges between groups?
- where are the likely modular boundaries in a messy codebase?

### Lens choice

**Structural lens**
- finds groups already visible in the require graph
- useful for explicit module structure

**Conceptual lens**
- finds groups that share domain language
- useful for discovering latent domain clusters

**Change lens**
- finds groups that evolve together
- useful for real workflow coupling

**Combined lens**
- often the best exploratory default
- highlights groups supported by multiple forms of evidence

### Interpreting bridge namespaces

A bridge namespace often indicates one of:
- legitimate integration point
- façade between communities
- accidental cross-cutting dependency
- architectural boundary under strain

Bridges deserve inspection because they often control how easily the system can
be split or evolved.

---

## 13. Compare mode: evaluate refactors, not just snapshots

`compare` turns two machine-readable snapshots into a structured delta report.

```bash
gordian --edn > before.edn
# make changes
gordian --edn > after.edn
gordian compare before.edn after.edn
```

It reports changes in:
- health
- propagation cost
- namespace metrics
- cycles
- conceptual pairs
- change pairs
- findings

### When to use compare

**Refactor validation**
- did PC go down?
- did cycles disappear?
- did hidden pairs weaken?

**Review support**
- did this branch create new high-risk findings?
- which namespaces changed role?

**Architecture tracking**
- is the codebase becoming more modular over time?

### What to look for

Good signs:
- lower propagation cost
- fewer or smaller cycles
- `shared` → `core` or `shared` → `peripheral` role simplifications
- fewer hidden cross-lens pairs

Bad signs:
- new cycles
- new high findings
- increased density in already-dense areas
- a namespace becoming both more central and more unstable

---

## 14. Gate mode: architecture ratchets in CI

Use `gate` when you want to stop architectural drift instead of merely noticing
it later.

```bash
gordian diagnose --edn > gordian-baseline.edn
gordian gate --baseline gordian-baseline.edn
```

`gate` compares the current report to a saved baseline and evaluates checks.

Common usage:

```bash
gordian gate --baseline gordian-baseline.edn
gordian gate --baseline gordian-baseline.edn --max-pc-delta 0.01
gordian gate --baseline gordian-baseline.edn --fail-on new-cycles,new-high-findings
```

Typical checks include:
- propagation cost delta
- new cycles
- new high findings
- new medium findings

### Recommended adoption path

Start gentle:
1. fail on new cycles
2. fail on new high findings
3. later add a small max propagation-cost delta

That creates a ratchet without blocking useful work too early.

### Why gate is better than ad hoc scripts

You can still script against `--json` or `--edn`, but `gate` bakes in the core
comparison workflow:
- baseline snapshot
- structured diff
- pass/fail result
- machine-readable output
- exit code suitable for CI

Use custom scripts only when you want checks beyond the built-ins.

---

## 15. Tests: reading source + test structure together

Run Gordian across both source and tests:

```bash
gordian src/ test/
# or
gordian . --include-tests
# or the dedicated test architecture mode
gordian tests .
```

Test namespaces appear alongside source namespaces, and their metrics carry
special meaning. `gordian tests` automates the test-specific interpretation
below: executable vs support test namespaces, unit-ish vs integration-ish
profiles, core `Ca` deltas, and propagation-cost delta from `src` to
`src+test`.

## 15.1 Reach distinguishes unit and integration tests

| reach | interpretation |
|-------|----------------|
| very low | likely unit test |
| high | likely integration/wiring test |

Example:

```
gordian.integration-test   48.3%   peripheral
gordian.main-test          48.3%   peripheral
gordian.aggregate-test      3.4%   isolated
gordian.close-test          3.4%   isolated
```

This is exactly what you want:
- integration tests near the pipeline edge
- module tests near the leaf they directly exercise

If a test namespace has surprisingly high reach, it may be testing through too
much machinery.

## 15.2 Core modules should gain Ca from tests

Compare `src/` with `src/ + test/`.

If a stable core namespace gains no extra `Ca` from the test tree, it may not
be tested directly at all.

## 15.3 Test namespaces should have `Ca = 0`

If a test namespace has incoming project deps, something is wrong:
- production code depending on test code
- test namespaces depending on each other
- utilities living in the wrong source tree

Keep tests independent and move shared support to a test-only support path.

---

## 16. Markdown and machine-readable outputs

Human-readable output is good for exploration. Markdown is good for sharing.
EDN/JSON are good for automation.

```bash
gordian --markdown > report.md
gordian diagnose --markdown > findings.md
gordian explain foo.bar --markdown > explain.md
```

Use markdown when you want to:
- attach architecture reports to PRs
- paste findings into docs
- discuss subsystem state asynchronously

Use EDN/JSON when you want to:
- snapshot the architecture
- diff before/after states
- feed dashboards or custom checks
- integrate with CI

All machine-readable output uses a stable schema envelope. See
`doc/schema.md` for the exact contract.

---

## 17. Practical workflows

## 17.1 New codebase: first-hour workflow

```bash
gordian
gordian diagnose
gordian communities
gordian subgraph <largest-family>
```

Goal:
- find major structural shape
- identify obvious hotspots
- discover natural subsystems
- choose one area to inspect deeply

## 17.2 Suspicious pair workflow

```bash
gordian diagnose
gordian explain-pair <ns-a> <ns-b>
gordian explain <ns-a>
gordian explain <ns-b>
```

Goal:
- determine if the pair is real coupling, naming noise, or expected structure

## 17.3 Refactor workflow

```bash
gordian --edn > before.edn
# refactor
gordian --edn > after.edn
gordian compare before.edn after.edn
gordian diagnose
```

Goal:
- validate that architectural pressure actually dropped

## 17.4 Team-owned subsystem workflow

```bash
gordian subgraph my.team.area --rank actionability
gordian explain my.team.area
```

Goal:
- review only the area you own
- prioritize local changes
- understand boundary crossings

## 17.5 CI ratchet workflow

```bash
gordian diagnose --edn > gordian-baseline.edn
# commit baseline
gordian gate --baseline gordian-baseline.edn
```

Goal:
- prevent regressions without demanding immediate perfection

## 17.6 Local hotspot workflow (`complexity`)

```bash
gordian complexity . --sort cc-risk --top 20
gordian complexity . --sort loc --top 20
gordian complexity . --namespace-rollup
gordian complexity . --project-rollup
gordian complexity . --min cc=10 --min loc=20
```

Use this when you want to find executable units that are locally risky because
of branch count or size.

Questions it answers well:
- which functions are the most branching-heavy?
- which units are large enough that review and refactor cost rises?
- which hotspots remain after filtering out small helper functions?

Interpretation:
- high `cc` with modest `loc` often means dense decision logic
- high `loc` with modest `cc` often means long procedural bodies or broad data shaping
- high `cc` and high `loc` together are strong candidates for extraction or simplification
- `cc-risk` is a quick triage sort; `loc` is a useful second pass
- add `--namespace-rollup` or `--project-rollup` only when you explicitly want summary sections beyond the default unit-focused view

`complexity` is local and unit-oriented. It does not tell you whether the
surrounding namespace architecture is healthy.

## 17.7 Local comprehension workflow (`local`)

```bash
gordian local . --sort total --top 20
gordian local . --sort abstraction --top 20
gordian local . --namespace-rollup
gordian local . --project-rollup
gordian local . --min total=12
gordian local . --min abstraction=4 --min working-set=3
```

Use this when cyclomatic complexity alone is not explaining why a unit feels
hard to understand, review, or modify safely.

Questions it answers well:
- which functions overload the reader's working set?
- where is helper chasing or abstraction mixing making comprehension harder?
- which units have unstable return-shape or shape-churn pressure?
- which hotspots look risky even when plain cyclomatic complexity is only moderate?

Interpretation:
- high `working-set` suggests too many live concepts must be tracked at once
- high `abstraction` suggests level-mixing, helper hopping, or opaque stages
- high `shape` suggests return-form or data-shape variability across branches
- high `dependency` suggests local understanding depends on too many external helpers
- high `total` with a balanced burden profile often marks genuinely review-hostile code
- add `--namespace-rollup` or `--project-rollup` only when you explicitly want summary sections beyond the default unit-focused view

`local` complements `complexity`:
- `complexity` tells you about branch count and size
- `local` tells you about comprehension burden

For a stubborn hotspot, run both commands on the same codebase and compare what
rises to the top.

---

## 18. How to tell healthy complexity from unhealthy coupling

Not all coupling is bad.

Healthy patterns:
- one clear peripheral entry point wiring many pure modules
- stable core namespaces with low `Ce`
- façade namespaces with high external `Ca` but low external `Ce`
- expected conceptual overlap inside a coherent domain family
- bridge namespaces that are deliberate integrations

Unhealthy patterns:
- cycles
- high-`Ca`, high-instability namespaces
- many `:shared` namespaces without crisp responsibilities
- cross-lens hidden pairs with no direct edge
- families with lots of touching hidden pairs across the boundary
- high propagation cost trending upward over time

The point is not to eliminate all dependency. The point is to make the real
architecture visible, intentional, and cheap to change.

---

## 19. A good default practice

If you only adopt a small part of Gordian, make it this:

```bash
gordian diagnose
gordian explain-pair <interesting-pair>
gordian subgraph <active-family>
gordian gate --baseline gordian-baseline.edn
```

That combination gives you:
- triage
- interpretation
- local context
- enforcement

Add these when the question shifts from architecture to executable-unit shape:

```bash
gordian complexity . --sort cc-risk --top 20
gordian local . --sort total --top 20
```

Those add:
- branch/size hotspot triage
- comprehension-burden hotspot triage

Which is usually enough to keep both the architecture and the day-to-day code
from quietly tangling itself.
