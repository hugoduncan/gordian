# Practical guide to coupling metrics

This document explains how to use the metrics gordian produces to make
concrete improvements to both source and test code.  The examples are drawn
from gordian's own self-analysis.

---

## The report at a glance

```
gordian analyze src/

propagation cost: 0.0663  (on average 6.6% of project reachable per change)

namespace               reach   fan-in   Ce   Ca      I  role
─────────────────────────────────────────────────────────────
gordian.main           92.9%    0.0%   13    0  1.00  peripheral
gordian.aggregate       0.0%    7.1%    0    1  0.00  core
gordian.cc-change       0.0%    7.1%    0    1  0.00  core
gordian.classify        0.0%    7.1%    0    1  0.00  core
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
gordian src/   →   PC = 0.0663
```

Star topology: one peripheral entry point (`gordian.main`) and independent
core modules.  PC is low because no module transitively depends on any other
project module.

---

## Conceptual coupling

Structural coupling (the `require` graph) is not the only way namespaces
can be coupled.  Two namespaces that share vocabulary — the same function
names, the same domain terms in their docstrings — are *conceptually coupled*
even if neither imports the other.

```bash
gordian src/ --conceptual 0.30
```

gordian extracts terms from namespace names, function names, and docstrings,
applies TF-IDF weighting (so common English words and noise are suppressed),
and reports pairs whose cosine similarity meets the threshold.

### Reading the output

```
conceptual coupling (sim ≥ 0.20):

namespace-a           namespace-b           sim   structural  shared concepts
─────────────────────────────────────────────────────────────────────────────
gordian.aggregate     gordian.close         0.35  no  ←    reach transitive node
gordian.main          gordian.scan          0.23  yes      file scan read
```

Each row shows a pair of namespaces, their similarity score, whether a
structural (`require`) edge exists, and the terms most responsible for the
similarity.

**`←` rows (no structural edge)** are the primary discovery signal.
`gordian.aggregate` and `gordian.close` share vocabulary around reachability
(`reach`, `transitive`, `node`) but neither requires the other.  This is
expected here — they are independent algorithms on the same graph domain —
but in a less deliberate codebase it might reveal a hidden abstraction waiting
to be named.

**`yes` rows (structural edge)** confirm that the structural coupling is
*about* the right thing.  `gordian.main` and `gordian.scan` are coupled on
file/directory IO vocabulary (`file`, `scan`, `read`), which is exactly what
you'd expect: `main` orchestrates `scan`'s IO work.  If the shared terms
looked unrelated to what the dependency is supposed to be, that would be worth
investigating.

### What the shared concepts column tells you

The similarity score tells you how similar two namespaces are.  The shared
concepts tell you *why*.  Without the terms you would have to read both files
to understand the relationship; with them the nature of the coupling is
immediately visible.

Three patterns to watch for:

**1. Hidden concept (← row, domain terms)**

```
my.app.invoice    my.app.payment    0.45  no  ←   amount due total format
```

Both namespaces talk about amounts, due dates, and formatting but neither
requires the other.  This is often a sign that a shared abstraction (`Money`,
`LineItem`) exists in the domain but has not been given a home in the code.

**2. Accidental concept (yes row, unrelated terms)**

```
my.app.render    my.app.database    0.38  yes    row col format table
```

`render` requires `database`, and they share table/column vocabulary.  The
structural dependency is real, but the conceptual overlap suggests rendering
logic has drifted into the database layer (or vice versa).  The shared terms
name the leakage.

**3. Expected concept (yes row, domain terms)**

```
my.app.invoice    my.app.invoice-pdf    0.61  yes    invoice line total format
```

High similarity, structural edge, domain terms: the coupling is intentional and
well-named.  Nothing to do here.

### Threshold guidance

| threshold | what you see |
|-----------|-------------|
| 0.15–0.25 | Many pairs, including weak signals; useful for exploration |
| 0.25–0.40 | Strong signals only; good for routine audits |
| 0.40+     | Only the most tightly coupled pairs |

Start at 0.20 to explore.  For a routine check, 0.30 is a reasonable default.
The score is most useful as a ranking — the highest-scoring `←` rows are the
most actionable.

---

## Change coupling

Structural coupling and conceptual coupling analyse the codebase as it stands
today.  Change coupling analyses the *git history*: namespaces that frequently
appear together in the same commit are logically coupled, regardless of whether
they import each other.

```bash
gordian src/ --change                              # full history, current repo
gordian src/ --change --change-since "90 days ago" # recent history only
gordian src/ --change /other/repo                  # another repo
```

### The horizon problem

By default gordian uses the full git history.  This is maximally stable — more
commits means more statistical confidence — but old coupling survives
indefinitely even after deliberate refactors.  A pair that was decoupled six
months ago will still appear in full-history results.

`--change-since` scopes the window:

```bash
gordian src/ --change --change-since "90 days ago"
gordian src/ --change --change-since "6 months ago"
gordian src/ --change --change-since "2024-01-01"
```

The argument is passed directly to `git log --since`, so any format git accepts
works.  Research (Zimmermann et al. 2005) found that recent history predicts
future coupling better than full history; 90 days is a reasonable starting point
for an active project.  Use full history on young codebases where 90 days covers
most of the development.

To distinguish a historical scar from an active coupling, compare results with
and without `--change-since`:

```bash
gordian src/ --change            # does the pair appear?
gordian src/ --change --change-since "90 days ago"  # still appears?
```

A pair that vanishes with a recent window is archaeological — the coupling
existed but has been resolved.  A pair that persists is still active.

### Reading the output

```
change coupling (Jaccard ≥ 0.30):

namespace-a           namespace-b           Jaccard  co  conf-a  conf-b  structural
───────────────────────────────────────────────────────────────────────────────────
gordian.main          gordian.output        0.4000   6   42.9%   85.7%  yes
gordian.conceptual    gordian.scan          0.3750   3   60.0%   50.0%  no  ←
```

**Jaccard** — symmetric coupling score: `co / (changes-a + changes-b - co)`.
Ranges from 0 (never co-change) to 1 (always change together and never
independently).

**co** — raw count of commits containing both namespaces.  Low values (1–2)
on short histories are noise; use `min-co` (default 2) to gate them out.

**conf-a / conf-b** — conditional probabilities: "when `a` changed, `b` also
changed X% of the time" and vice versa.  These are *directional* where Jaccard
is symmetric.

**structural** — whether a `require` edge exists in either direction.

### Patterns

**1. Historical scar (← row, disappears with `--change-since`)**

```
gordian.conceptual    gordian.scan    0.38  no  ←
```

The pair shows in full history but not in a recent window.  The namespaces
changed together during early development when they were structurally coupled;
the structural edge was later deliberately removed.  The git log still carries
the memory of that coupling.  No action needed — the refactor worked.

**2. Active implicit coupling (← row, persists with `--change-since`)**

```
my.app.invoice    my.app.pdf    0.72  no  ←   conf-a=90%  conf-b=65%
```

These namespaces change together regularly but have no structural edge.  This
is the sharpest signal change coupling produces: an implicit contract that the
`require` graph cannot see.  Common causes:

- A shared data format or schema defined nowhere in code
- A protocol implemented in one namespace and consumed in the other via dynamic
  dispatch, reflection, or string-keyed data
- Two halves of a feature that belong in one namespace but were split

The shared terms from `--conceptual` often name the missing abstraction.

**3. Confirmed structural coupling (yes row)**

```
gordian.main    gordian.output    0.40  yes   conf-a=43%  conf-b=86%
```

A structural edge exists *and* the namespaces co-change often.  The coupling is
real and acknowledged.  High Jaccard here (> 0.7) raises a different question:
are these two namespaces actually one thing?  If they always change together
and share a structural edge, consider whether the file boundary serves any
purpose.

**4. Asymmetric confidence — satellite coupling**

```
my.app.emails    my.app.templates    0.55  yes   conf-a=95%  conf-b=30%
```

`conf-a=95%` means nearly every time `emails` changed, `templates` also
changed.  `conf-b=30%` means `templates` often changes independently.
`emails` is a satellite of `templates`: it cannot move without `templates`
moving too, but the reverse is not true.

This asymmetry signals that `emails` is fragile with respect to `templates`.
The interface between them — what `emails` depends on in `templates` — may be
too wide.  Narrowing it (extracting a smaller protocol or data contract) would
reduce the satellite's surface area.

### Combining structural, conceptual, and change signals

The three coupling signals see different things:

| Signal | Sees | Blind to |
|--------|------|----------|
| Structural | `require` edges today | Dynamic dispatch, data contracts, history |
| Conceptual | Shared vocabulary today | Whether coupling is intentional |
| Change | Co-evolution over time | Current code structure |

The most actionable rows are those where **change coupling confirms a
conceptual coupling** with no structural edge:

```
my.app.invoice    my.app.payment    0.45  no  ←   [conceptual]
my.app.invoice    my.app.payment    0.61  no  ←   [change coupling]
```

Three signals, no structural edge: the coupling is real, active, and unnamed.
A missing abstraction is almost certain.

Conversely, a structural edge with *no* change coupling and *no* conceptual
coupling is worth scrutinising:

```
my.app.render    my.app.database    yes  [structural]
                                    —    [no conceptual overlap]
                                    —    [rarely co-change]
```

The `require` edge exists but the namespaces almost never change together and
share no vocabulary.  The dependency may be vestigial — added for a feature
that was later removed — or the coupling is so thin (a single value, a single
function call) that it carries no design weight.

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
gordian src/ test/   →   PC = 0.0809

gordian.integration-test   48.3%   peripheral  ← pipeline tests, correctly labelled
gordian.main-test          48.3%   peripheral  ← tests the wiring layer, expected
gordian.output-test        48.3%   peripheral  ← tests formatted output via pipeline
gordian.aggregate-test      3.4%   isolated    ← unit test
gordian.close-test          3.4%   isolated    ← unit test
gordian.conceptual-test     3.4%   isolated    ← unit test
gordian.dot-test            3.4%   isolated    ← unit test
...
```

The module tests each require exactly one project namespace with no further
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
gordian src/        →  PC = 0.0663
gordian src/ test/  →  PC = 0.0809
```

The increase reflects how broadly the tests connect to the source graph.  A
small increase is healthy: tests are targeted.  A large increase means tests
are pulling in wide swathes of the project, which often indicates either
over-coupled tests or insufficient isolation in the source itself.

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
