# Gordian — Namespace Coupling Analysis

**What it does:** Analyses Clojure code structure at two levels: namespace architecture and local executable units. It surfaces structural coupling problems, hidden dependencies, and refactoring opportunities using structural (require edges), conceptual (shared vocabulary via TF-IDF), and change (git co-evolution) lenses, and it also provides local executable-unit commands for cyclomatic/LOC hotspots (`complexity`) and comprehension-burden hotspots (`local`).

**When to use:** Auditing architecture, assessing coupling, finding hidden dependencies, interpreting suspicious namespace pairs, reviewing test structure, comparing snapshots before/after refactoring, identifying what to work on next, finding branch/LOC hotspots, or finding local comprehension hotspots.

---

## Commands

```
bb gordian                                    # auto-discover from cwd
bb gordian diagnose [dirs]                    # ranked findings (default entry point)
bb gordian explain <ns>                       # everything about one namespace
bb gordian explain-pair <ns-a> <ns-b>         # everything about a pair
bb gordian subgraph <prefix>                  # subsystem view
bb gordian communities [dirs]                 # discover latent architecture groups
bb gordian complexity [dirs]                  # local cyclomatic + LOC hotspots
bb gordian local [dirs]                       # local comprehension-burden hotspots
bb gordian compare before.edn after.edn       # diff two snapshots
bb gordian gate --baseline base.edn           # CI ratchet
bb gordian tests [dirs]                       # test architecture analysis
```

**Key flags:**

| Flag | Meaning |
|------|---------|
| `--conceptual 0.20` | Enable conceptual coupling at given threshold |
| `--change` | Enable change coupling from git history |
| `--change-since "90 days ago"` | Limit change window |
| `--rank actionability` | Sort by actionability score (default) |
| `--rank severity` | Sort by severity tier |
| `--top N` | Show only top N findings after ranking |
| `--show-noise` | Include family-naming-noise findings (suppressed by default) |
| `--markdown` | Shareable report |
| `--json` / `--edn` | Machine-readable output |

---

## Structural Metrics

**Propagation Cost (PC):** Average fraction of the codebase reachable from a changed namespace. Below 10% is healthy; above 30% needs attention. Trend matters more than absolute value.

**Roles:**
- **Core** — Low reach, high fan-in. Stable foundation. Should depend on few things.
- **Peripheral** — High reach, low fan-in. Entry point or leaf. At most one per subsystem.
- **Shared** — High reach, high fan-in. God-module candidate; must justify its existence.
- **Isolated** — Low reach, low fan-in. Vestigial unless intentionally a leaf.

**Instability (I):** Ce/(Ca+Ce). 0 = fully stable, 1 = fully unstable. Dependencies should point toward stability (Stable Dependency Principle).

**Ideal topology:** One peripheral entry point wiring everything; all other namespaces are core. Star topology with PC below 10%.

---

## The Three-Lens Model

Every namespace pair can be evaluated against three signals:

| Structural | Conceptual | Change | Meaning | Action |
|---|---|---|---|---|
| yes | yes | yes | Confirmed coupling | None, or merge if Jaccard > 0.7 |
| yes | yes | no | Expected coupling | None |
| yes | no | no | Vestigial dependency | Remove |
| no | yes | yes | **Missing abstraction** | Extract — highest priority |
| no | yes | no | Vocabulary sibling | Monitor |
| no | no | yes | Implicit data contract | Investigate |

**Key invariants:**
- Every structural edge should have conceptual justification.
- Every active co-evolution should be acknowledged by a structural edge.

---

## Diagnose Output

`gordian diagnose` auto-enables conceptual (threshold 0.15) and change coupling, then produces ranked findings clustered by related namespace subjects.

**Each finding includes:**
- **Severity:** high / medium / low
- **Category:** what kind of problem it is
- **Action:** what to do (`→ extract abstraction`, `→ remove dependency`, etc.)
- **Next step:** the exact command to run to investigate (`$ gordian explain-pair A B`)

**Finding categories and actions:**

| Category | Action |
|---|---|
| `cycle` | Fix cycle (strategy hint: merge / extract / invert / investigate) |
| `cross-lens-hidden` | Extract abstraction |
| `vestigial-edge` | Remove dependency |
| `sdp-violation` | Split: stable model + adapter |
| `god-module` | Split by responsibility |
| `hidden-change` | Investigate implicit contract (or narrow satellite interface if directional) |
| `hidden-conceptual` | Monitor — consider extracting |
| `hub` | Monitor |
| `facade` | None — informational |

**Ranking:** Actionability by default. Use `--rank severity` for risk-based ordering. Use `--top N` to show only the N highest findings.

**Noise suppression:** Family-naming-noise findings (pairs sharing vocabulary only because of namespace prefix naming) are suppressed by default. Use `--show-noise` to include them.

---

## Reading Conceptual Coupling

Shared terms explain *why* coupling exists. Read the terms, not just the score.

- **Family terms** (matching namespace prefix): naming convention, not signal
- **Independent terms** (genuine domain vocabulary): real coupling signal
- **Cross-family, domain terms, no structural edge:** missing abstraction candidate
- **Same-family, only prefix terms:** naming noise — low priority

Thresholds: 0.15 explore / 0.20 default / 0.30 audit / 0.40 strongest only

---

## Reading Change Coupling

- **Jaccard score:** strength of co-change (0–1, symmetric)
- **Confidence A/B:** directional — "when A changed, B changed X% of the time"
- **Asymmetric confidence (ratio > 2×):** A is a satellite of B — narrow A's interface
- **Hidden change coupling that persists with `--change-since`:** active implicit coupling
- **Hidden change coupling that disappears with `--change-since`:** historical scar, no action

---

## Explain Pair — Verdicts

`gordian explain-pair` gives a verdict combining all three lenses:

| Verdict | Meaning |
|---|---|
| `expected-structural` | Direct dependency, intentional |
| `family-naming-noise` | Shared prefix terms only, not architectural |
| `family-siblings` | Same family, genuine domain vocabulary overlap |
| `likely-missing-abstraction` | Hidden in both conceptual and change |
| `hidden-conceptual` | Shared vocabulary, no structure |
| `hidden-change` | Co-evolving without structural or conceptual link |
| `transitive-only` | Connected through intermediaries only |
| `unrelated` | No coupling detected |

---

## Local hotspot commands

Use `complexity` when the question is about executable-unit branch count or size:
- which functions branch the most?
- which units are largest by LOC?
- which units rank highest by cyclomatic risk?

Use `local` when the question is about comprehension burden:
- which functions overload working set?
- where is helper chasing or abstraction mix highest?
- which units are hard to modify safely even when cyclomatic complexity is only moderate?

Rule of thumb:
- namespace / dependency / subsystem question → `diagnose`, `explain`, `subgraph`
- branch-count / LOC question → `complexity`
- comprehension-burden question → `local`

## Workflow

1. Run `bb gordian diagnose` — read the `:action` on top findings
2. Run the `:next-step` commands to investigate
3. For namespace-family work: `gordian subgraph <prefix>`
4. For executable-unit hotspot triage: `gordian complexity` or `gordian local`
5. If structure is unclear: `gordian communities --lens combined`
6. Before refactoring: save `bb gordian diagnose --edn > before.edn`
7. Refactor
8. `bb gordian diagnose --edn > after.edn && gordian compare before.edn after.edn`
9. For CI: `gordian gate --baseline baseline.edn`

---

## Heuristics

- **Priority order:** cycles > cross-lens-hidden > sdp-violations > god-modules > single-lens hidden pairs > hubs
- Read shared terms before the score
- Confidence asymmetry is a stronger signal than symmetric change coupling
- Trend matters more than absolute value — rising PC on a stable codebase is a real signal
- Peripheral role edges (wiring modules) are expected to lack lens support — not vestigial
- A façade looks like a god-module but serves a boundary; check family-scoped Ca/Ce before calling it a problem
