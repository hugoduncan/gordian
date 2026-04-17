---
name: namespace-structure
description: The ideal Clojure namespace structure — principles for dependency direction, coupling coherence, family boundaries, and test isolation. Tool-agnostic. Use to evaluate, design, or critique namespace organisation in any Clojure project.
lambda: "λcodebase. no_cycles ∧ stability_gradient ∧ lens_coherence ∧ family_surfaces ∧ test_leaves → sound_architecture"
metadata:
  version: "1.0.0"
  tags: ["clojure", "architecture", "namespace", "coupling", "design", "principles"]
---

λ ideal(codebase).
  no_cycles ∧ stability_gradient ∧ minimal_entry_points
  ∧ lens_coherence ∧ family_surfaces ∧ test_leaves ∧ stable_trend

λ stability_gradient(graph).
  ∀ edge(A→B): A less_stable_than B    depend toward stability
  | entry-points   change freely        many dependents ∧ few dependencies
  | orchestrators  change deliberately  bounded dependents ∧ bounded dependencies
  | domain-logic   change rarely        few dependents ∧ many dependencies
  | foundation     change never         no project dependencies

λ roles(ns).
  core       → stable foundation   | depended on by many | depends on few
  peripheral → entry-point ∨ leaf  | depended on by few  | depends on many | ≤1 per subsystem
  shared     → god-module candidate | depended on by many ∧ depends on many | must justify
  isolated   → vestigial unless intentional leaf

λ topology(ideal).
  one(peripheral) | all_others(core) | star
  | a change ripples to a small fraction of the codebase

λ cycles(rule).
  cycles = 0                         first-order violation; fix before all else
  | merge(a,b)     if always_change_together
  | extract(c)     if shared_dep_exists → a→c ∧ b→c
  | invert         if callback_pattern → protocol↑ impl↓

λ lens_coherence(pair).
  explicit_dep ∧ shared_vocab ∧ co-evolves  → confirmed | consider merge if inseparable
  explicit_dep ∧ shared_vocab ∧ independent → expected                | none
  explicit_dep ∧ unrelated    ∧ independent → vestigial               | remove
  implicit     ∧ shared_vocab ∧ co-evolves  → missing_abstraction     | extract — highest priority
  implicit     ∧ shared_vocab ∧ independent → vocabulary_sibling      | monitor
  implicit     ∧ unrelated    ∧ co-evolves  → implicit_data_contract  | investigate
  | invariant: every dependency has a conceptual justification
  | invariant: every active co-evolution is acknowledged by a dependency

λ family(prefix).
  facade(ns)  ← many external dependents ∧ few external dependencies ∧ delegates internally
              | permitted | boundary coordinator | ¬god-module
  noise(pair) ← shared vocabulary ⊆ naming_convention
              | ¬architectural signal
  signal(pair)← shared vocabulary ∩ domain_concepts ≠ ∅
              | genuine coupling
  | invariant: a family exposes one surface
  | invariant: internals do not cross family boundaries without explicit dependency

λ tests(ns).
  no production code depends on test code
  tests depend on a small slice of production code → unit
  tests depend on an entry point              → integration | justified
  unexpected broad test dependencies          → grown unintentionally | isolate
  adding tests should not dramatically widen the blast radius of a change

λ health(principles).
  a change should touch a small fraction of the codebase
  no cycles
  stable things must not depend on unstable things
  | trend matters more than absolute value
  | rising coupling on a stable codebase is a stronger signal than high-but-stable coupling on a growing one
