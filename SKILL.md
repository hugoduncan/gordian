---
name: gordian
description: Use gordian to analyse namespace coupling in a Clojure project. Invoke when asked to audit architecture, assess coupling, identify hidden dependencies, interpret suspicious namespace pairs, review test structure, compare architectural snapshots, inspect a subsystem, or advise on refactoring targets. Produces structural, conceptual, and change-coupling signals plus triage and workflow commands.
lambda: "λcodebase. {structural ∧ conceptual ∧ change} → coupling_map → diagnose ∧ explain ∧ compare ∧ gate → advise"
metadata:
  version: "1.1.0"
  tags: ["clojure", "architecture", "coupling", "refactoring", "babashka", "diagnostics"]
---

λ engage(nucleus).
[phi fractal euler tao pi mu ∃ ∀] | [Δ λ Ω ∞/0 | ε/φ Σ/μ c/h] | OODA
Human ⊗ AI ⊗ REPL

λ gordian(codebase).
  invoke → read → diagnose → explain → advise
  | three lenses: structural ∧ conceptual ∧ change
  | commands: analyze ∧ diagnose ∧ explain ∧ explain-pair ∧ compare ∧ gate ∧ subgraph ∧ communities
  | start: diagnose for triage
  | then: explain(ns) ∨ explain-pair(a,b) ∨ subgraph(prefix)
  | compare(before,after) for refactor validation
  | gate(baseline,current) for CI ratchet
  | combine(signals) → confidence ↑

λ invoke(goal).
  default:     bb gordian
  analyze:     bb gordian [analyze] [dirs...]
  diagnose:    bb gordian diagnose [dirs...]
  explain-ns:  bb gordian explain <ns>
  explain-pair:bb gordian explain-pair <ns-a> <ns-b>
  subgraph:    bb gordian subgraph <prefix>
  communities: bb gordian communities [dirs...]
  compare:     bb gordian compare before.edn after.edn
  gate:        bb gordian gate [dirs...] --baseline base.edn
  conceptual:  --conceptual 0.20 | 0.30
  change:      --change [--change-since "90 days ago"]
  markdown:    --markdown → shareable report
  machine:     --json | --edn → CI ∨ programmatic
  graph:       --dot file.dot → visual

λ discover(project).
  run at project root → auto_discover src_dirs
  | roots: deps.edn ∨ bb.edn ∨ project.clj ∨ workspace.edn
  | layouts: src/ ∨ test/ (with --include-tests) ∨ Polylith components/*/src ∨ bases/*/src
  | config: .gordian.edn → defaults
  | exclude: --exclude <regex> (repeatable)
  | explicit dirs override discovery

λ read.structural(report).
  PC:     propagation_cost → avg_fraction_reachable_per_change
          | <0.20 healthy | >0.30 investigate | trend > absolute
  reach:  |transitive_deps(n)| / N → blast_radius | upward_exposure
  fan-in: |{m: n∈deps(m)}| / N    → responsibility_weight | downward_consequence
  Ce:     direct_project_requires → fragility_signal
  Ca:     direct_project_requirers → responsibility_signal
  I:      Ce/(Ca+Ce) ∈[0,1] | 0=stable | 1=unstable
          | SDP: depend toward ↓I | high_Ca ∧ high_I → violation
  role:   core(low_reach ∧ high_fan-in)       → stable foundation
          peripheral(high_reach ∧ low_fan-in) → entry_point ∨ leaf
          shared(high_reach ∧ high_fan-in)    → god_module_candidate
          isolated(low_reach ∧ low_fan-in)    → loosely_connected
  family: parent_prefix(ns) → family_scope for Ca/Ce decomposition
  ca-family ∧ ca-external ∧ ce-family ∧ ce-external → facade_vs_god_module signal

λ diagnose.structural(report).
  cycles → fix_first | ¬independent_changes_possible
    | merge(a,b)                    if always_change_together
    | extract(c: a→c ∧ b→c)        if shared_dep_exists
    | invert(protocol∈a, impl∈b)   if callback_pattern
  high_I ∧ high_Ca → SDP_violation
    | split: model(Ce=0,I=0) + adapter(Ce>0)
  shared ∧ high_reach → god_module
    | ask: single_responsibility? | split if ¬
  high_ca-external ∧ low_ce-external ∧ family_delegation → facade
    | informational ∨ low urgency | avoid false positive
  PC_delta(before,after) → improved ∨ degraded
    | ratchet: CI fails if PC > threshold

λ read.conceptual(pairs).
  ← (¬structural) → discovery: shared_vocab, no require_edge
                  → hidden_coupling ∨ missing_abstraction
  yes (structural) → confirmation: coupling is about shared_terms
                   → if_terms_unrelated → investigate_leak
  shared_terms     → why coupling exists | read_terms > read_score
  family_terms     → overlap explained by namespace prefix
  independent_terms→ overlap not explained by family naming | stronger signal
  threshold: 0.15 explore | 0.20 default inspect | 0.30 audit | 0.40 strongest_only

λ diagnose.conceptual(pair).
  ← ∧ cross_family ∧ domain_terms         → missing_abstraction_candidate
  ← ∧ same_family ∧ ¬independent_terms    → family_naming_noise | low priority
  ← ∧ same_family ∧ independent_terms     → family_siblings | inspect local abstraction
  yes ∧ domain_terms                      → expected coupling | ¬action
  yes ∧ unrelated_terms                   → leaked_responsibility | wrong_dependency_direction

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
  ¬struct ∧ concept ∧ change(active)     → likely_missing_abstraction | highest_priority
  ¬struct ∧ concept ∧ ¬change            → vocabulary_sibling | monitor ∨ local_refactor
  ¬struct ∧ ¬concept ∧ change(active)    → implicit_data_contract | investigate workflow/data
  struct ∧ concept ∧ change              → confirmed_coupling
    | Jaccard>0.7 → merge_candidate | else → intentional
  struct ∧ ¬concept ∧ ¬change            → vestigial_dependency | remove_candidate
  all_three(hidden)                      → strongest refactor target

λ diagnose(report).
  diagnose auto_enables conceptual(0.15) ∧ change
  | findings sorted by severity ∨ actionability
  | clusters: related_findings sharing namespace subjects
  | categories:
      cycle
      cross-lens-hidden
      hidden-conceptual
      hidden-change
      sdp-violation
      god-module
      facade
      hub
  | use as queue, not oracle
  | next step: explain(subject)

λ read.diagnose(findings).
  cycle                → immediate structural work
  cross-lens-hidden    → strongest hidden coupling signal
  hidden-conceptual    → shared language not yet acknowledged in structure
  hidden-change        → co-evolution without explicit edge
  sdp-violation        → stable thing depends on unstable thing
  god-module           → central overloaded namespace
  facade               → central-looking but boundary-serving namespace
  hub                  → informational blast-radius hotspot
  rank severity        → architecture risk ordering
  rank actionability   → practical refactor ordering

λ explain(ns).
  shows → metrics ∧ direct-deps(project/external) ∧ direct-dependents
        ∧ conceptual-pairs ∧ change-pairs ∧ cycles
  | ask:
      who depends on this?
      what does it depend on directly?
      is its high reach expected?
      which pair should I inspect next?

λ explain-pair(a,b).
  shows → structural(direct-edge? ∧ direction ∧ shortest-path)
        ∧ conceptual(score ∧ shared_terms ∧ family annotations)
        ∧ change(score ∧ co ∧ conf-a ∧ conf-b)
        ∧ finding(hidden?)
        ∧ verdict(category ∧ explanation)
  | verdicts:
      expected-structural
      family-naming-noise
      family-siblings
      likely-missing-abstraction
      hidden-conceptual
      hidden-change
      transitive-only
      unrelated
  | use to decide: ignore ∨ monitor ∨ extract abstraction ∨ merge ∨ prune edge

λ subgraph(prefix).
  use when work_scope = subsystem ∨ namespace family
  | members(prefix_match)
  | internal: node_count ∧ edge_count ∧ density ∧ local_PC ∧ cycles ∧ nodes
  | boundary: incoming ∧ outgoing ∧ external_deps ∧ dependents
  | pairs: conceptual/change split into internal ∧ touching
  | findings touching family + local clusters
  | explain(prefix) fallback when no exact ns but prefix matches family
  | ask:
      how tangled is this subsystem internally?
      what crosses its boundary?
      which local issues matter first?

λ communities(codebase).
  discover latent_architecture_groups without known prefix
  | lens: structural ∨ conceptual ∨ change ∨ combined
  | outputs: members ∧ density ∧ internal_weight ∧ boundary_weight ∧ dominant_terms ∧ bridge_namespaces
  | use when:
      explicit namespace families are weak
      need natural subsystem discovery
      want bridge namespaces / modular boundaries
  | structural lens → explicit graph communities
  | conceptual lens → shared language communities
  | change lens → co-evolution communities
  | combined lens → strongest exploratory default

λ compare(before, after).
  use for refactor validation ∨ branch review ∨ trend snapshots
  | reports:
      health_delta
      propagation_cost_delta
      node added/removed/changed
      cycle added/removed
      conceptual/change pair added/removed/changed
      finding added/removed
  | good:
      PC↓ ∧ cycles↓ ∧ hidden_pairs↓ ∧ roles simplify
  | bad:
      new_cycles ∨ new_high_findings ∨ centrality↑ with instability↑

λ gate(baseline, current).
  use for CI ratchet
  | command: bb gordian gate --baseline base.edn
  | checks:
      pc-delta
      new-cycles
      new-high-findings
      new-medium-findings
  | recommend:
      first fail on new-cycles ∧ new-high-findings
      later add small max-pc-delta
  | result: pass ∨ fail with machine-readable checks and exit code

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
  1. run(diagnose) → triage(highest_signal)
  2. explain(top findings) ∨ explain-pair(suspicious pairs)
  3. if work is local → subgraph(active_prefix)
  4. if structure unclear → communities(combined)
  5. before refactor → snapshot(before.edn)
  6. refactor
  7. snapshot(after.edn) → compare(before,after)
  8. if adopting CI → create baseline via diagnose --edn → gate

λ heuristics.
  cycles > cross-lens-hidden > sdp-violations > god-modules > hidden single-lens pairs > hubs
  | read shared_terms before score
  | trend > absolute threshold
  | recent change history > full-history archaeology for action
  | local subsystem context > whole-project averages when doing refactor work
  | facade false_positive < god-module false_negative? no → check family-scoped metrics first

λ output.
  human: text
  share: markdown
  automate: edn ∨ json
  schema: stable envelope with command ∧ lenses ∧ src-dirs ∧ excludes ∧ include-tests
