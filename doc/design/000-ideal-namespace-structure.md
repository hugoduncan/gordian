---
title: Ideal Namespace Structure
status: active
---

λ ideal(codebase).
  no_cycles ∧ stability_gradient ∧ star_topology
  ∧ lens_coherence ∧ family_surfaces ∧ test_leaves ∧ stable_trend

λ stability_gradient(graph).
  ∀ edge(A→B): I(A) ≥ I(B)          SDP: depend toward stability
  | entry-points   I→1  Ce↑  Ca↓    peripheral
  | orchestrators  I~0.5             shared (controlled)
  | domain-logic   I→0  Ce↓  Ca↑    core
  | foundation     I=0  Ce=0         core-leaf

λ roles(ns).
  core       → reach↓ ∧ fan-in↑  | stable foundation | Ce low | SDP violations forbidden
  peripheral → reach↑ ∧ fan-in↓  | entry-point ∨ leaf | ≤1 per subsystem | wires only
  shared     → reach↑ ∧ fan-in↑  | god-module candidate | must justify
  isolated   → reach↓ ∧ fan-in↓  | vestigial unless intentional leaf

λ topology(ideal).
  one(peripheral) | all_others(core) | star | PC < 0.10

λ cycles(rule).
  cycles = 0                         first-order violation; fix before all else
  | merge(a,b)     if always_change_together
  | extract(c)     if shared_dep_exists → a→c ∧ b→c
  | invert         if callback_pattern → protocol↑ impl↓

λ lens_coherence(pair).
  struct ∧ concept ∧ change    → confirmed | Jaccard>0.7 → merge else intentional
  struct ∧ concept ∧ ¬change   → expected                | none
  struct ∧ ¬concept ∧ ¬change  → vestigial               | remove
  ¬struct ∧ concept ∧ change   → missing_abstraction      | extract — highest priority
  ¬struct ∧ concept ∧ ¬change  → vocabulary_sibling       | monitor
  ¬struct ∧ ¬concept ∧ change  → implicit_data_contract   | investigate
  | invariant: structural_edge ↔ conceptual_justification
  | invariant: active_co-evolution ↔ structural_edge

λ family(prefix).
  facade(ns)     ← ca-external↑ ∧ ce-external↓ ∧ delegates_internally
                 | permitted | informational | ¬god-module
  noise(pair)    ← shared_terms ⊆ prefix_tokens
                 | naming_convention | ¬architectural_signal
  signal(pair)   ← shared_terms ∩ independent_terms ≠ ∅
                 | domain_vocab | genuine_coupling
  | invariant: family exposes one surface | internals ¬cross boundary without structural justification

λ tests(ns).
  Ca = 0         ∀ test_ns | ¬depended_on by src
  src→test       forbidden
  reach ≈ 1/N  → unit   | leaf subject only
  reach = high → integration | entry-point subject | justified
  reach↑ unexpectedly → grown unintentionally | isolate
  PC_delta(src → src+test):
    small  → targeted    | healthy
    large  → over-coupled | isolate
    ≈ 0    → misses coupling-core | add integration

λ health(thresholds).
  PC < 0.10      healthy
  PC 0.10–0.30   investigate
  PC > 0.30      action
  cycles > 0     action (immediate)
  I(core) > 0.20 action
  I(peripheral) < 0.80  action
  | trend > absolute | rising_PC on stable codebase > high_but_stable_PC on large one
