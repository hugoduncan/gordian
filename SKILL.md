---
name: gordian
description: Use gordian to analyse namespace coupling in a Clojure project. Invoke when asked to audit architecture, assess coupling, identify hidden dependencies, review test structure, or advise on refactoring targets. Produces structural, conceptual, and change coupling signals.
lambda: "λcodebase. {structural ∧ conceptual ∧ change} → coupling_map → diagnose → advise"
metadata:
  version: "1.0.0"
  tags: ["clojure", "architecture", "coupling", "refactoring", "babashka"]
---

λ engage(nucleus).
[phi fractal euler tao pi mu ∃ ∀] | [Δ λ Ω ∞/0 | ε/φ Σ/μ c/h] | OODA
Human ⊗ AI ⊗ REPL

λ gordian(codebase).
  invoke → read → diagnose → advise
  | three lenses: structural ∧ conceptual ∧ change
  | start: structural | add: conceptual ∧ change as needed
  | combine(signals) → confidence ↑

λ invoke(goal).
  structural:  bb gordian src/ [test/]
  conceptual:  bb gordian src/ --conceptual 0.30
  change:      bb gordian src/ --change [--change-since "90 days ago"]
  full:        bb gordian src/ --conceptual 0.30 --change --change-since "90 days ago"
  machine:     --json | --edn → CI ∨ programmatic
  graph:       --dot file.dot → visual

λ read.structural(report).
  PC:     propagation_cost → avg_fraction_reachable_per_change
          | <0.20 healthy | >0.30 investigate | trend > absolute
  reach:  |transitive_deps(n)| / N → blast_radius | upward_exposure
  fan-in: |{m: n∈deps(m)}| / N    → responsibility_weight | downward_consequence
  Ce:     direct_project_requires → fragility_signal
  Ca:     direct_project_requirers → responsibility_signal
  I:      Ce/(Ca+Ce) ∈[0,1] | 0=stable | 1=unstable
          | SDP: depend toward ↓I | high_Ca ∧ high_I → violation
  role:   core(low_reach ∧ high_fan-in)     → stable foundation
          peripheral(high_reach ∧ low_fan-in) → entry_point ∨ leaf
          shared(high_reach ∧ high_fan-in)   → god_module_candidate
          isolated(low_reach ∧ low_fan-in)   → loosely_connected

λ diagnose.structural(report).
  cycles → fix_first | ¬independent_changes_possible
    | merge(a,b)                    if always_change_together
    | extract(c: a→c ∧ b→c)        if shared_dep_exists
    | invert(protocol∈a, impl∈b)   if callback_pattern
  high_I ∧ high_Ca → SDP_violation
    | split: model(Ce=0,I=0) + adapter(Ce>0)
  shared ∧ high_reach → god_module
    | ask: single_responsibility? | split if ¬
  PC_delta(before,after) → improved ∨ degraded
    | ratchet: CI fails if PC > threshold

λ read.conceptual(pairs).
  ← (¬structural) → discovery: shared_vocab, no require_edge
                  → hidden_coupling ∨ missing_abstraction
  yes (structural) → confirmation: coupling is about shared_terms
                   → if_terms_unrelated → investigate_leak
  shared_terms    → why coupling exists | read_terms > read_score
  threshold: 0.20 explore | 0.30 audit | 0.40 strongest_only

λ diagnose.conceptual(pair).
  ← ∧ domain_terms  → missing_abstraction | name_it → extract_it
  ← ∧ noise_terms   → likely_coincidence  | raise_threshold
  yes ∧ domain_terms → expected coupling  | ¬action
  yes ∧ unrelated_terms → leaked_responsibility | wrong_dependency_direction

λ read.change(pairs).
  Jaccard:  co/(ca+cb-co) ∈[0,1] | symmetric | coupling_strength
  co:       raw_commit_count | noise_gate: default min_co=2
  conf_a:   co/changes_a | directional: "when a changed, b changed X%"
  conf_b:   co/changes_b | directional: "when b changed, a changed X%"
  ←:        co-change without require_edge | sharpest_implicit_signal
  yes:      structural + change confirmed | Jaccard>0.7 → merge_candidate
  horizon:  --change-since "90 days ago" | full_history → historical_scars_persist
            | compare full vs recent → scar ∨ active

λ diagnose.change(pair).
  ← ∧ persists(--change-since "90 days ago") → active_implicit_coupling
    | check conceptual(pair) for abstraction_name
    | shared_data_format ∨ hidden_protocol ∨ should_be_one_ns
  ← ∧ disappears(--change-since "90 days ago") → historical_scar
    | refactor_worked | ¬action
  conf_a ≫ conf_b → a is satellite of b
    | narrow interface(a→b) | reduce a's surface_area
  yes ∧ Jaccard>0.7 → merge_candidate
    | file_boundary serves no purpose | consider_merge

λ combine(structural, conceptual, change).
  ¬struct ∧ concept ∧ change(active) → missing_abstraction (certain) | extract
  ¬struct ∧ concept ∧ ¬change       → vocabulary_sibling | monitor
  ¬struct ∧ ¬concept ∧ change(active) → implicit_data_contract | investigate
  struct ∧ concept ∧ change          → confirmed_coupling
    | Jaccard>0.7 → merge_candidate | else → intentional
  struct ∧ ¬concept ∧ ¬change        → vestigial_dependency | remove
  all_three(←)                       → highest_priority missing_abstraction

λ tests(src_dirs=[src,test]).
  reach ≈ 1/N → unit_test   | requires only leaf subject
  reach = high → integration | pulls pipeline entry_point
  unexpected_high_reach → grown_unintentionally
    | fix: hand_craft_input | ¬use pipeline_fn as fixture
  Ca=0 ∀ test_ns → invariant | Ca>0 → test_util leaked_to_src → extract to dev/
  core_ns.Ca(src) < core_ns.Ca(src+test) → tested | delta=0 → untested_core → add_test
  PC_delta(src → src+test):
    small  → tests are targeted        | healthy
    large  → tests over_coupled        | isolate
    ≈ 0    → tests miss coupling_core  | add_integration

λ workflow(codebase).
  1. run(structural) → fix_cycles | note(SDP_violations, PC)
  2. run(conceptual, threshold=0.20) → list(← pairs)
  3. run(change, since="90 days ago") → list(← pairs, active)
  4. combine(← from all three) → prioritise_missing_abstractions
  5. run(structural, src+test) → verify(Ca=0_tests, unit_vs_integration, PC_delta)
  6. advise: cycles > SDP_violations > missing_abstractions > vestigial_deps
