✅ gordian v0 alpha feature-complete (2026-04-10)

All core metrics implemented in one session across 11 commits:
- scan → close → aggregate → metrics → scc → classify → output/dot/json
- 193 assertions, 41 tests, 0 failures
- Self-analysis: PC=9%, star topology, no cycles — architecture verified
- bbin-installable: bb gordian analyze src/

Key design wins:
- Pure/IO separation: 9 pure modules + 1 IO wiring module (main)
- Tarjan SCC cycle detection handles cycles in transitive closure too
- Classify uses mean thresholds (adapts to project size)
- bb.edn :extra-paths for test isolation from bbin install path

bb.edn lesson: task-level :requires aliases work; inline `require` in :task
body resolves at analysis time (not runtime), so aliases in task bodies fail.
Use top-level {:requires ...} in the :tasks map instead.
