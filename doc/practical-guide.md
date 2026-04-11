# Practical guide to coupling metrics

This document explains how to use the metrics gordian produces to make
concrete improvements to both source and test code.  The examples are drawn
from gordian's own self-analysis.

---

## The report at a glance

```
gordian analyze src/

propagation cost: 0.0826  (on average 8.3% of project reachable per change)

namespace               reach   fan-in   Ce   Ca      I  role
─────────────────────────────────────────────────────────────
gordian.main           90.9%    0.0%   10    0  1.00  peripheral
gordian.aggregate       0.0%    9.1%    0    1  0.00  core
...
```

**Propagation cost** is the headline number — average fraction of the project
transitively reachable from any one namespace.  Everything else is per-namespace
detail.

---

## Source files

### 1. Start with cycles

If the cycles section is present, fix those first.  A cycle means two or more
namespaces cannot be changed independently; they are effectively one module even
if they live in separate files.

Options, in order of preference:

1. **Merge them** — if `a` and `b` always change together, they are one thing
2. **Extract the shared dependency** — find what `a` and `b` both need and pull
   it into a third namespace `c`; both then depend on `c`, the cycle is gone
3. **Invert via a protocol** — if `a` depends on `b` for a callback, define
   the callback protocol in `a` and have `b` implement it

### 2. Check the instability gradient

In a healthy architecture instability increases from the inside out.  Foundation
namespaces should sit near I=0; entry points, adapters, and CLI handlers near
I=1.

**What to look for:** a core or domain namespace with high I.

```
;; Smell: load-bearing namespace that itself depends on much
my.app.domain    reach=35%  fan-in=60%   Ce=7  Ca=12  I=0.37  shared
```

`my.app.domain` is depended on by 12 namespaces but itself requires 7 others.
A change anywhere in those 7 propagates to all 12.  This violates the **Stable
Dependencies Principle**: stable things (high Ca) should not depend on unstable
things (high Ce).

**Fix:** split the namespace.  Extract the part with no outward dependencies into
`my.app.domain.model` (Ce=0, I=0).  The parts that need infrastructure become
separate adapters.

### 3. Investigate :shared namespaces

`:shared` means high reach *and* high fan-in — the namespace sits in the middle
of everything.  A small number of shared namespaces is normal (utility layers,
protocols).  Many shared namespaces, or one with very high reach, signals a
"god module".

Ask: does this namespace have a single, clear responsibility?  If not, split it.

### 4. Read reach as blast radius

`reach` answers: *if something I depend on changes, how exposed am I?*

A namespace with 80% reach will be touched by changes anywhere in 80% of the
project.  That is fragile regardless of how stable each individual dependency is.

The companion metric is `fan-in`, which answers the inverse: *if I change, how
many things might need to change?*  A namespace with both high reach and high
fan-in is the most expensive to change in the project.

### 5. Track propagation cost over time

Run gordian before and after a refactor.  If PC went down, coupling improved.
If it went up, a new dependency was introduced that spreads cost.

Well-modularised projects tend to sit below 0.20.  Above 0.30 is worth
investigating.  The number is most useful as a trend, not an absolute threshold.

**Gordian on itself:**

```
gordian src/   →   PC = 0.0826
```

Star topology: one peripheral entry point (`gordian.main`) and ten independent
core modules.  PC is low because no module transitively depends on any other
project module.

---

## Test files

Run gordian over source *and* tests together:

```
gordian src/ test/
```

Test namespaces appear alongside source namespaces.  Their metrics carry
specific meaning.

### reach reveals unit vs integration tests

`reach` is the most direct unit/integration discriminator available without
reading the test code.

| reach | what it means |
|-------|---------------|
| ≈ 1/N | Unit test — requires only its subject module, which itself has no project deps |
| high  | Integration test — transitively pulls in a pipeline entry point |

**Gordian on itself:**

```
gordian src/ test/   →   PC = 0.0983

gordian.integration-test   47.8%   isolated  ← pipeline tests, correctly labelled
gordian.main-test          47.8%             ← tests the wiring layer, expected
gordian.output-test        47.8%             ← tests formatted output via pipeline
gordian.aggregate-test      4.3%   isolated  ← unit test
gordian.close-test          4.3%   isolated  ← unit test
gordian.dot-test            4.3%   isolated  ← unit test
gordian.scc-test            4.3%   isolated  ← unit test
...
```

The nine module tests each require exactly one project namespace with no further
project deps; they are pure unit tests and the metric confirms it.

**A test namespace with unexpectedly high reach** is the most common signal that
something has been written as an integration test without intent.  The typical
cause is using a pipeline function (e.g., `build-report`, `analyze`) as a test
fixture for a serialisation or formatting test.  The fix is to hand-craft the
input data instead — the test then only requires the module under test.

### Check that core modules are tested

Look at `:core` namespaces in the `src/` view.  Note which ones have high Ca
(many things depend on them).  Then run `src/ test/` and check whether those
same namespaces gained any Ca from the test tree.

If a :core namespace has Ca=8 in source but Ca=0 in source+test, nothing tests
it directly.  It is the most relied-upon code in the project and the least
directly exercised.

**Gordian on itself:**

```
;; src/ view
gordian.close    Ca=1   core   (only gordian.main depends on it)

;; src/ test/ view
gordian.close    Ca=2   core   (main + close-test)
```

`gordian.close-test` shows up in the Ca count, confirming direct test coverage.
A Ca difference of zero between the two views flags a missing test.

### Test namespaces must have Ca = 0

In the combined `src/ test/` view, every test namespace should have Ca = 0.
No production code should require a test namespace; no test should be required
by another test.

A test namespace with Ca > 0 means one of:

- **Test utilities used by source** — extract them to a `dev/` or
  `test-support/` source tree visible only in the test classpath
- **One test importing another** — break the dependency; each test namespace
  should be fully self-contained

### Interpret the PC delta

```
gordian src/        →  PC = 0.0826
gordian src/ test/  →  PC = 0.0983
```

The increase (0.0826 → 0.0983) reflects how broadly the tests connect to the
source graph.  A small increase is healthy: tests are targeted.  A large
increase means tests are pulling in wide swathes of the project, which often
indicates either over-coupled tests or insufficient isolation in the source
itself.

If PC barely changes when you add tests, your tests are not exercising the
coupling structure — likely all integration tests testing only the outermost
layer, or unit tests on leaf namespaces leaving the connected core untested.

---

## The unit/integration boundary in practice

When a test file mixes unit and integration tests, the namespace-level metric
reflects the worst case — the whole file looks integrated even if 90% of the
tests are unit tests.  Separation requires physical separation into files.

A practical convention:

```
test/myapp/core_test.clj          ← unit tests; requires only myapp.core
test/myapp/integration_test.clj   ← integration tests; requires myapp.main
```

Running gordian confirms the boundary is clean:

```
myapp.core-test         4%  reach   isolated   ← unit
myapp.integration-test  60% reach   peripheral ← integration
```

If `myapp.core-test` shows high reach, the unit file has grown an integration
dependency and the boundary has blurred.

---

## CI integration

gordian outputs JSON, which makes threshold checks straightforward:

```bash
# fail the build if source propagation cost exceeds 0.20
pc=$(gordian src/ --json | bb -i '(-> (read) :propagation-cost)')
bb -e "(when (> $pc 0.20) (System/exit 1))"
```

Run this on every commit to give the architecture a ratchet: it can improve or
hold, but cannot silently degrade.

Tighter checks are possible: fail if any namespace has both Ca > 3 and I > 0.5,
or if any cycles are introduced.  The JSON output contains everything needed.
