# 020 — Diagnose Actionability

## Problem

`gordian diagnose` produces ranked findings but not actionable ones. Each
finding says *what* is wrong; none says *what to do* or *what to run next*.
The principles articulate a direct action for every finding category. That
mapping is currently only in SKILL.md and partially in `explain-pair` verdicts.

This design closes six gaps:

1. **`:action`** — every finding carries the action it implies
2. **Vestigial edges** — `struct ∧ ¬concept ∧ ¬change` is undetected
3. **Next-step command** — pair findings carry the `gordian` command to run
4. **Cycle strategy hints** — merge / extract / invert discriminated from evidence
5. **Asymmetric change coupling** — directional signal currently flattened
6. **Noise suppression** — family-noise findings suppressed by default

---

## 1. `:action` on Every Finding

### The mapping

Derived from `λ lens_coherence` and `λ diagnose.*` in the principles:

| Category | `:action` keyword | Display |
|---|---|---|
| `cycle` | `:fix-cycle` | `fix cycle` |
| `cross-lens-hidden` | `:extract-abstraction` | `extract abstraction` |
| `sdp-violation` | `:split-stable-adapter` | `split: stable model + adapter` |
| `god-module` | `:split-responsibilities` | `split by responsibility` |
| `vestigial-edge` | `:remove-dependency` | `remove dependency` |
| `hidden-conceptual` | `:monitor-or-extract` | `monitor — consider extracting` |
| `hidden-change` | `:investigate-contract` | `investigate implicit contract` |
| `hub` | `:monitor` | `monitor` |
| `facade` | `:none` | `none — informational` |

### Data shape

```clojure
{:severity :high
 :category :cross-lens-hidden
 :subject  {:ns-a foo.bar :ns-b foo.baz}
 :action   :extract-abstraction          ; new
 :reason   "hidden in 2 lenses ..."
 :evidence {...}}
```

### Implementation

- `finding.clj` — `(defn action-for-category [category] ...)` lookup map
- All `find-*` functions in `diagnose.clj` call `(finding/action-for-category category)` and assoc the result
- `output.clj` — display as `→ <action>` on the finding line

### Output example

```
[high]  foo.bar ↔ foo.baz
        hidden in 2 lenses — conceptual=0.34 change=0.61
        → extract abstraction
```

---

## 2. Vestigial Edge Detection

### Signal

`struct ∧ ¬concept ∧ ¬change → remove dependency`

A structural edge (explicit require) with no conceptual overlap and no
co-evolution history. The dependency exists in code but carries no signal
justifying it.

### Data needed

The `report` map already carries `:graph` (`{ns → #{direct-deps}}`).
`diagnose` currently ignores it. Thread it through.

### Detection algorithm

```clojure
(defn- find-vestigial-edges
  [graph nodes conceptual-pairs change-pairs]
  (let [project-nss  (set (keys graph))
        concept-keys (set (map finding/pair-key conceptual-pairs))
        change-keys  (set (map finding/pair-key change-pairs))
        node-map     (into {} (map (juxt :ns identity)) nodes)]
    (into []
          (mapcat
           (fn [[ns-a deps]]
             (keep
              (fn [ns-b]
                (when (contains? project-nss ns-b)
                  (let [k #{ns-a ns-b}]
                    (when (and (not (contains? concept-keys k))
                               (not (contains? change-keys k)))
                      {:severity :low
                       :category :vestigial-edge
                       :action   :remove-dependency
                       :subject  {:ns-a ns-a :ns-b ns-b}
                       :reason   (str (str ns-a) " → " (str ns-b)
                                      " — structural edge with no conceptual"
                                      " or change signal")
                       :evidence {:ns-a ns-a :ns-b ns-b
                                  :instability-a (:instability (get node-map ns-a))
                                  :instability-b (:instability (get node-map ns-b))}}))))
              deps))
           graph))))
```

### Notes

- Only project-internal edges are checked (`contains? project-nss ns-b`)
- Severity is `:low` — the edge may be correct but warrant review
- Requires conceptual lens active to be meaningful; suppress if
  `--conceptual` not enabled (can't distinguish vestigial from unscanned)
- This finding only fires when both conceptual and change lenses are active
  (i.e., `diagnose` already auto-enables both)

### `diagnose` signature change

```clojure
;; before
(defn diagnose [{:keys [cycles nodes conceptual-pairs change-pairs]}])

;; after
(defn diagnose [{:keys [cycles nodes conceptual-pairs change-pairs graph]}])
```

No call-site changes — `report` already carries `:graph`.

---

## 3. Next-Step Command on Pair Findings

### Rationale

Diagnose is a triage queue. The natural next step after a pair finding is
`gordian explain-pair`. After a namespace finding, `gordian explain <ns>`.
After a cluster, `gordian subgraph <prefix>`. Printing this eliminates the
lookup step.

### Data shape

```clojure
{:severity  :high
 :category  :cross-lens-hidden
 :action    :extract-abstraction
 :next-step "gordian explain-pair foo.bar foo.baz"   ; new
 :subject   {:ns-a foo.bar :ns-b foo.baz}
 :reason    "..."
 :evidence  {...}}
```

### Generation rules

| Subject shape | `:next-step` |
|---|---|
| `{:ns-a A :ns-b B}` | `"gordian explain-pair <A> <B>"` |
| `{:ns N}` | `"gordian explain <N>"` |
| `{:members M}` (cycle) | `"gordian subgraph <common-prefix(M)>"` if one exists, else `"gordian explain <highest-ca-member>"` |

### Implementation

- `finding.clj` — `(defn next-step-for [subject graph nodes] ...)` pure fn
- All `find-*` functions assoc `:next-step`
- `output.clj` — print as `$ <next-step>` on the line after `:action`

### Output example

```
[high]  foo.bar ↔ foo.baz
        hidden in 2 lenses — conceptual=0.34 change=0.61
        → extract abstraction
        $ gordian explain-pair foo.bar foo.baz
```

---

## 4. Cycle Resolution Strategy Hints

### Rationale

Cycles are the highest-severity finding but currently carry only
`"N-namespace cycle"`. The three resolution strategies are discriminable
from evidence already available.

### Strategy rules

Evaluated over the member set of the cycle. First match wins.

| Condition | Strategy | Evidence required |
|---|---|---|
| Any member pair has change Jaccard > 0.7 | `:merge` | `change-pairs` |
| Any member pair has `independent-terms ≠ []` | `:extract` | `conceptual-pairs` |
| Any member has `Ca ≥ 3 ∧ I < 0.3` | `:invert` | `nodes` |
| Default | `:investigate` | — |

### Data shape addition

```clojure
{:severity :high
 :category :cycle
 :action   :fix-cycle
 :subject  {:members #{foo.a foo.b foo.c}}
 :reason   "3-namespace cycle — strategy: extract shared dependency"
 :evidence {:members ... :size 3
            :strategy       :extract          ; new
            :strategy-basis {:ns-a foo.a      ; new — which pair triggered it
                             :ns-b foo.b
                             :shared-terms [...]}}}
```

### Implementation

New private fn in `diagnose.clj`:

```clojure
(defn- cycle-strategy
  "Derive a resolution strategy for a cycle from available lens evidence."
  [members change-pairs conceptual-pairs nodes]
  (let [member-set   (set members)
        member-pairs (fn [pairs]
                       (filter (fn [p] (and (contains? member-set (:ns-a p))
                                            (contains? member-set (:ns-b p))))
                               pairs))
        node-map     (into {} (map (juxt :ns identity)) nodes)]
    (or
     ;; merge: two members almost always change together
     (when-let [p (first (filter #(> (:score %) 0.7) (member-pairs change-pairs)))]
       {:strategy :merge
        :basis    {:ns-a (:ns-a p) :ns-b (:ns-b p) :score (:score p)}})
     ;; extract: two members share independent conceptual terms → extract that concept
     (when-let [p (first (filter #(seq (:independent-terms %))
                                 (member-pairs conceptual-pairs)))]
       {:strategy :extract
        :basis    {:ns-a (:ns-a p) :ns-b (:ns-b p)
                   :shared-terms (:independent-terms p)}})
     ;; invert: one member is stable and highly-depended-on → put protocol there
     (when-let [n (first (filter #(and (>= (or (:ca %) 0) 3)
                                       (< (or (:instability %) 1.0) 0.3))
                                 (map #(get node-map %) members)))]
       {:strategy :invert
        :basis    {:ns (:ns n) :ca (:ca n) :instability (:instability n)}})
     {:strategy :investigate :basis nil})))
```

Strategy descriptions for output:

| Strategy | Reason suffix |
|---|---|
| `:merge` | `"— consider merging <A> and <B> (Jaccard=N)"` |
| `:extract` | `"— extract shared concept <terms> from cycle"` |
| `:invert` | `"— invert: put protocol in <ns>, push impl out"` |
| `:investigate` | `"— investigate"` |

---

## 5. Asymmetric Change Coupling

### Rationale

`find-hidden-change` emits a symmetric reason. `confidence-a` and
`confidence-b` are already in evidence. When `conf_a / conf_b > 2.0`
the coupling is directional: A is a satellite of B. The action differs:
*narrow A's interface to B*, not *investigate bilateral contract*.

### Rules

```
ratio = max(conf_a, conf_b) / min(conf_a, conf_b)   ; when both > 0
satellite = argmax(conf_a, conf_b)                    ; the more-dependent one
anchor    = argmin(conf_a, conf_b)

ratio > 2.0 → directional
  reason:  "<satellite> changes whenever <anchor> changes (conf=N%)"
  action:  :narrow-satellite-interface
  evidence: adds :direction {:satellite ns :anchor ns :ratio ratio}

ratio ≤ 2.0 → symmetric
  reason:  "co-changing without structural link (co-changes=N)"
  action:  :investigate-contract   (unchanged)
```

### New `:action` entry

| Condition | `:action` keyword | Display |
|---|---|---|
| directional hidden-change | `:narrow-satellite-interface` | `narrow <satellite>'s interface to <anchor>` |
| symmetric hidden-change | `:investigate-contract` | `investigate implicit contract` |

### Implementation

In `find-hidden-change`, compute ratio before building the finding map.
The action lookup in `finding.clj` stays a static map; the directional
case overrides `:action` at construction time (asymmetry is local logic).

---

## 6. Noise Suppression by Default

### Problem

Family-noise findings (`:low` severity, `family-noise? true`) appear in
the diagnose output and inflate finding counts. By the principles they are
not architectural signals.

### Change

- Default: exclude findings where `(finding/family-noise? f)` is true
- New flag `--show-noise` re-includes them
- Summary line distinguishes signal count from suppressed count:
  `"12 findings (3 high, 5 medium, 4 low) — 7 noise suppressed"`

### Implementation

- `diagnose.clj` — `diagnose` returns all findings unchanged (pure, no suppression)
- `main.clj` — post-diagnose filter: `(remove finding/family-noise? findings)` unless `--show-noise`
- `output.clj` — summary line takes optional `suppressed-count`

---

## Data Shape Summary

Full finding map after changes:

```clojure
{:severity  :high | :medium | :low
 :category  keyword
 :action    keyword                          ; new — always present
 :next-step string | nil                    ; new — pair/ns findings only
 :subject   {:ns-a sym :ns-b sym}           ; or {:ns sym} or {:members set}
 :reason    string
 :evidence  {... :strategy kw              ; cycle only — new
                 :strategy-basis map       ; cycle only — new
                 :direction map}}          ; asymmetric change only — new
```

---

## Output Format

Text, per finding:

```
[high]  foo.bar ↔ foo.baz
        hidden in 2 lenses — conceptual=0.34 change=0.61
        → extract abstraction
        $ gordian explain-pair foo.bar foo.baz

[high]  {foo.a foo.b foo.c}
        3-namespace cycle — extract shared concept [block order]
        → fix cycle
        $ gordian subgraph foo

[medium]  foo.ingest
          high Ca=5 but I=0.72 — depended upon yet unstable
          → split: stable model + adapter
          $ gordian explain foo.ingest

[medium]  foo.writer ↔ foo.reader
          foo.writer changes whenever foo.reader changes (conf=81%)
          → narrow foo.writer's interface to foo.reader
          $ gordian explain-pair foo.writer foo.reader

[low]  foo.loader → foo.util
       structural edge with no conceptual or change signal
       → remove dependency
       $ gordian explain-pair foo.loader foo.util
```

Summary line:

```
14 findings (3 high, 6 medium, 5 low) — 9 noise suppressed (--show-noise to include)
```

---

## Implementation Plan

### Step 1 — `finding.clj`: action map + next-step helper
- `action-for-category` — keyword lookup map covering all 10 categories
- `next-step-for` — pure fn: subject → command string

### Step 2 — `diagnose.clj`: action + next-step on all finders
- Add `:action (finding/action-for-category category)` to every `find-*` fn
- Add `:next-step (finding/next-step-for subject ...)` to all finders
- Add `find-vestigial-edges [graph nodes conceptual-pairs change-pairs]`
- Add `cycle-strategy` helper; wire into `find-cycles`
- Add asymmetry logic to `find-hidden-change`
- Add `find-vestigial-edges` call in `diagnose`
- `diagnose` destructures `:graph` from report

### Step 3 — `output.clj`: render action + next-step
- `format-finding-lines` emits `→ <action>` and `$ <next-step>` lines
- Summary line accepts `suppressed-count`

### Step 4 — `main.clj`: noise suppression flag
- `--show-noise` option (default false)
- Post-diagnose filter in all commands that display findings

### Step 5 — tests
- `finding.clj`: `action-for-category`, `next-step-for`
- `diagnose.clj`: vestigial detection, cycle strategy, asymmetric change
- `output.clj`: new line format
- Integration: noise suppression count

### Step dependency graph

```
Step 1 (finding fns)
  → Step 2 (diagnose logic)
    → Step 3 (output)
    → Step 4 (main flag)
      → Step 5 (tests, concurrent with each step)
```

Steps 3 and 4 are independent of each other. Each step is a single commit.
