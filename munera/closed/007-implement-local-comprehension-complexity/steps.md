- [ ] Phase 1: add `gordian local` command shell and stub pipeline hook
- [ ] Phase 1: implement complexity-like scope resolution for `local` (default discovered source paths, `--source-only`, `--tests-only`, explicit path override)
- [ ] Phase 1: implement and test CLI validation for `--sort`, `--top`, `--min`, `--bar`, scope flags, and output mode exclusivity
- [ ] Phase 1: lock the v1 CLI/report shape for `local` around the complexity-like option model and compact burden-vector tables

- [ ] Phase 2: implement top-level local-unit extraction for `defn` arities
- [ ] Phase 2: implement top-level local-unit extraction for `defmethod` bodies
- [ ] Phase 2: preserve provenance fields needed for reporting (`:ns`, `:var`, `:arity`, `:dispatch`, `:file`, `:line`, `:origin`)
- [ ] Phase 2: test nested local helper folding into enclosing top-level units

- [ ] Phase 3: implement shared evidence extraction for main-path steps and branch regions
- [ ] Phase 3: implement shared evidence extraction for control forms, nesting depth, and condition structure
- [ ] Phase 3: implement shared evidence extraction for bindings, rebinding relations, and mutable/effect observations
- [ ] Phase 3: implement shared evidence extraction for call/operator classification and coarse shape observations
- [ ] Phase 3: implement shared evidence extraction for working-set program points
- [ ] Phase 3: test representative evidence extraction cases across control, calls, bindings, effects, and shapes

- [ ] Phase 4: implement `flow-burden` scoring according to `021b`
- [ ] Phase 4: implement `abstraction-burden` scoring according to `021b`
- [ ] Phase 4: implement `shape-burden` scoring according to `021b`
- [ ] Phase 4: implement `dependency-burden` scoring according to `021b`
- [ ] Phase 4: implement `state-burden` scoring according to `021b`
- [ ] Phase 4: implement `working-set` scoring according to `021b`
- [ ] Phase 4: test burden-family exclusions and anti-double-counting behavior
- [ ] Phase 4: keep `regularity-burden` omitted unless a conservative implementation clearly falls out naturally

- [ ] Phase 5: implement high-confidence finding derivation for flow findings (`:deep-control-nesting`, `:predicate-density` if supported)
- [ ] Phase 5: implement high-confidence finding derivation for state findings (`:mutable-state-tracking`, `:temporal-coupling` if supported)
- [ ] Phase 5: implement high-confidence finding derivation for shape findings (`:shape-churn`, `:conditional-return-shape`)
- [ ] Phase 5: implement high-confidence finding derivation for abstraction findings (`:abstraction-mix`, `:abstraction-oscillation`)
- [ ] Phase 5: implement high-confidence finding derivation for dependency findings (`:opaque-pipeline`, `:helper-chasing` where supported)
- [ ] Phase 5: implement high-confidence finding derivation for working-set findings (`:working-set-overload`)
- [ ] Phase 5: test finding trigger thresholds against recorded evidence

- [ ] Phase 6: assemble canonical unit-level LCC report rows
- [ ] Phase 6: assemble namespace rollups using average burden-family rollups
- [ ] Phase 6: assemble project rollup using average burden-family rollups
- [ ] Phase 6: implement default ranking and `--sort` behavior at the report layer
- [ ] Phase 6: implement `--top`, `--min`, and `--bar` semantics at the report/finalizer layer
- [ ] Phase 6: omit `regularity-burden` from first-slice payloads unless implemented
- [ ] Phase 6: test canonical EDN report shape and option semantics

- [ ] Phase 7: implement text output for `gordian local`
- [ ] Phase 7: implement markdown output for `gordian local`
- [ ] Phase 7: implement EDN output for `gordian local`
- [ ] Phase 7: implement JSON output for `gordian local`
- [ ] Phase 7: test that bars follow the selected `--bar` metric and findings remain visible in human output

- [ ] Phase 8: update scoped help and command examples for `gordian local`
- [ ] Phase 8: update README / docs for the new command
- [ ] Phase 8: add representative end-to-end command tests
- [ ] Phase 8: final review for naming, data-shape consistency, and output clarity
