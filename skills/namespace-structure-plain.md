# Ideal Namespace Structure — Principles

A tool-agnostic statement of what good Clojure namespace organisation looks like. Use to evaluate, design, or critique namespace architecture in any project.

---

## The Core Invariant: Dependencies Point Toward Stability

Every `require` edge from namespace A to namespace B means A depends on B. For the codebase to be maintainable, A should be *less stable* than B — it should be acceptable for A to change when B changes, but not the reverse.

This creates a stability gradient from top to bottom:

| Layer | Stability | Character |
|---|---|---|
| Entry points | Unstable | Change freely; depended on by few; depend on many |
| Orchestrators | Moderate | Change deliberately; bounded on both sides |
| Domain logic | Stable | Change rarely; depended on by many; depend on few |
| Foundation | Fixed | Never change; no project dependencies |

Violating this — a stable namespace depending on an unstable one — creates fragility. Every change to the unstable thing risks breaking the stable thing.

---

## Namespace Roles

Every namespace falls into one of four roles based on how many things depend on it (fan-in) and how many things it depends on (reach):

**Core** — depended on by many, depends on few. The stable foundation of the codebase. Should almost never require other project namespaces.

**Peripheral** — depends on many, depended on by few. Entry points and leaf nodes. Ideally there is at most one per subsystem — the wiring module that assembles everything else. Changes here don't propagate.

**Shared** — depended on by many *and* depends on many. A god-module candidate. When you find one, ask whether it has a single clear responsibility. If not, split it.

**Isolated** — depended on by few and depends on few. Either intentionally a leaf node or a vestigial namespace that should be removed.

**Ideal topology:** One peripheral namespace wires everything; all others are core. A change to any single namespace reaches a small fraction of the codebase.

---

## No Cycles — The First-Order Rule

Cyclic dependencies make independent change impossible. Two namespaces in a cycle cannot be changed independently, deployed separately, or understood in isolation.

Fix cycles before anything else. Three strategies:

1. **Merge** — if the two namespaces always change together, they are effectively one thing. Merge them.
2. **Extract** — if both depend on a third concept, extract that concept into a new namespace and have both depend on it.
3. **Invert** — if the cycle is a callback pattern, put a protocol in the more stable namespace and move the implementation out.

---

## Three-Lens Coherence

A dependency between two namespaces can be evaluated against three independent signals:

- **Structural:** does an explicit `require` edge exist?
- **Conceptual:** do they share domain vocabulary?
- **Change:** do they co-evolve in git history?

These signals should be consistent. The combinations and what they mean:

| Structural | Conceptual | Change | Meaning | Action |
|---|---|---|---|---|
| Yes | Yes | Yes | Confirmed, intentional coupling | None (or merge if inseparable) |
| Yes | Yes | No | Expected coupling | None |
| Yes | No | No | **Vestigial dependency** | Remove it |
| No | Yes | Yes | **Missing abstraction** | Extract — highest priority |
| No | Yes | No | Vocabulary sibling | Monitor |
| No | No | Yes | Implicit data contract | Investigate |

**Two invariants follow:**
- Every structural dependency should have a conceptual justification. If two namespaces require each other but share no vocabulary and never co-change, the dependency may no longer be needed.
- Every active co-evolution should be acknowledged by a structural dependency. If two namespaces consistently change together but have no explicit relationship, something is being left implicit.

---

## Family Structure

Namespaces sharing a common prefix form a *family* (e.g. `app.user.*`). Families should behave like modules:

**Façade pattern (permitted):** A family can have one namespace that many external callers depend on, while internally it delegates to siblings. This looks like a god-module from the outside but is actually a boundary coordinator. It is informational, not alarming.

**Noise vs. signal:** Namespaces in the same family naturally share vocabulary from the prefix itself. This shared vocabulary is naming convention, not architectural coupling. Only vocabulary that is *independent* of the prefix counts as a genuine coupling signal.

**Invariants:**
- A family should expose one surface to the outside world.
- Internal family namespaces should not be required directly by namespaces outside the family unless there is a specific structural justification.

---

## Test Namespace Invariants

- No production namespace depends on a test namespace. Tests are leaves.
- No source code edges should point into test directories.
- A test that requires only its direct subject is a unit test (expected).
- A test that requires an entry point is an integration test (justified).
- A test whose dependencies grew unexpectedly broad has drifted — isolate it.
- Adding tests should not dramatically increase how much of the codebase a single change can touch.

---

## Health Principles

**A change should touch a small fraction of the codebase.** Propagation cost is the key top-line metric. When it rises on a previously stable codebase, that is a real signal regardless of absolute value.

**No cycles.** Always the first thing to fix.

**Stable things must not depend on unstable things.** Each SDP violation is a fragility risk.

**Trend matters more than absolute value.** A codebase growing steadily more coupled is more concerning than a large but stable propagation cost.
