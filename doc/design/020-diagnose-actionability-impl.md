# 020 — Diagnose Actionability — Implementation Plan

Five commits. No breaking changes to existing public API.
Each step compiles and passes tests independently.

---

## Step 1 — `finding.clj`: action map and next-step helper

**Commit:** `feat: finding — action-for-category and next-step-for`

### New content in `finding.clj`

```clojure
(def ^:private action-map
  {:cycle                    :fix-cycle
   :cross-lens-hidden        :extract-abstraction
   :sdp-violation            :split-stable-adapter
   :god-module               :split-responsibilities
   :vestigial-edge           :remove-dependency
   :hidden-conceptual        :monitor-or-extract
   :hidden-change            :investigate-contract
   :hub                      :monitor
   :facade                   :none})

(defn action-for-category
  "Action keyword implied by a finding category. Returns nil for unknown."
  [category]
  (get action-map category))

(def action-display
  "Human-readable display string for each action keyword."
  {:fix-cycle                  "fix cycle"
   :extract-abstraction        "extract abstraction"
   :split-stable-adapter       "split: stable model + adapter"
   :split-responsibilities     "split by responsibility"
   :remove-dependency          "remove dependency"
   :narrow-satellite-interface "narrow interface"   ; directional change only
   :monitor-or-extract         "monitor — consider extracting"
   :investigate-contract       "investigate implicit contract"
   :monitor                    "monitor"
   :none                       nil})

(defn- common-ns-prefix
  "Longest common dot-separated prefix shared by all namespace strings.
  Returns nil when there is no shared prefix."
  [ns-strs]
  (when (seq ns-strs)
    (let [parts  (mapv #(str/split (str %) #"\.") ns-strs)
          min-len (apply min (map count parts))
          common  (->> (range min-len)
                       (take-while #(apply = (map (fn [p] (nth p %)) parts)))
                       (mapv #(nth (first parts) %)))]
      (when (seq common)
        (str/join "." common)))))

(defn next-step-for
  "Suggest the most useful gordian command for a finding.
  category — finding category keyword.
  subject  — finding :subject map.
  nodes    — full node list (used for cycle fallback).
  Returns a string command or nil."
  [category subject nodes]
  (case category
    (:cross-lens-hidden :hidden-conceptual :hidden-change :vestigial-edge)
    (str "gordian explain-pair " (:ns-a subject) " " (:ns-b subject))

    (:sdp-violation :god-module :hub :facade)
    (str "gordian explain " (:ns subject))

    :cycle
    (let [members (:members subject)
          prefix  (common-ns-prefix (map str members))]
      (if (seq prefix)
        (str "gordian subgraph " prefix)
        (let [node-map (into {} (map (juxt :ns identity)) nodes)
              best     (apply max-key #(or (:ca (get node-map %)) 0) members)]
          (str "gordian explain " best))))

    nil))
```

### Tests — `test/gordian/finding_test.clj` (new file)

```
action-for-category-test
  all 9 defined categories return a keyword
  unknown category returns nil

action-display-test
  all action keywords have an entry
  :none maps to nil

next-step-for-test
  pair categories → "gordian explain-pair <a> <b>"
  ns categories   → "gordian explain <ns>"
  :cycle with common prefix  → "gordian subgraph <prefix>"
  :cycle without common prefix → "gordian explain <highest-Ca-member>"
  unknown category → nil
```

---

## Step 2 — `diagnose.clj`: action + next-step on all finders, new detectors

**Commit:** `feat: diagnose — action, next-step, vestigial edges, cycle strategy, asymmetric change`

### Changes to all existing `find-*` fns

Add two fields to every constructed finding map:

```clojure
:action    (finding/action-for-category category)
:next-step (finding/next-step-for category subject nodes)
```

The `category` and `subject` locals already exist in each function. `nodes`
is threaded through from `diagnose`.

### `find-cycles` — new signature and strategy

```clojure
;; before
(defn- find-cycles [cycles])

;; after
(defn- find-cycles [cycles change-pairs conceptual-pairs nodes])
```

New private helper:

```clojure
(defn- cycle-strategy
  "Derive a resolution strategy hint for a cycle from available evidence.
  Returns {:strategy keyword :basis map-or-nil}.
  Evaluated top-down; first match wins."
  [members change-pairs conceptual-pairs nodes]
  (let [member-set   (set members)
        in-cycle?    (fn [p] (and (contains? member-set (:ns-a p))
                                  (contains? member-set (:ns-b p))))
        node-map     (into {} (map (juxt :ns identity)) nodes)]
    (or
     ;; merge: a pair inside the cycle co-changes almost always
     (when-let [p (first (filter #(and (in-cycle? %) (> (:score %) 0.7))
                                 change-pairs))]
       {:strategy :merge
        :basis    {:ns-a (:ns-a p) :ns-b (:ns-b p) :score (:score p)}})
     ;; extract: a pair shares independent domain vocabulary
     (when-let [p (first (filter #(and (in-cycle? %)
                                       (seq (:independent-terms %)))
                                 conceptual-pairs))]
       {:strategy :extract
        :basis    {:ns-a (:ns-a p) :ns-b (:ns-b p)
                   :shared-terms (:independent-terms p)}})
     ;; invert: a stable high-Ca member can host the protocol
     (when-let [n (first (filter #(and (>= (or (:ca %) 0) 3)
                                       (< (or (:instability %) 1.0) 0.3))
                                 (map #(get node-map %) members)))]
       {:strategy :invert
        :basis    {:ns (:ns n) :ca (:ca n) :instability (:instability n)}})
     {:strategy :investigate :basis nil})))
```

Strategy → reason suffix map (private):

```clojure
(def ^:private strategy-suffix
  {:merge       (fn [{:keys [ns-a ns-b score]}]
                  (str " — merge " ns-a " and " ns-b
                       " (Jaccard=" (format "%.2f" score) ")"))
   :extract     (fn [{:keys [shared-terms]}]
                  (str " — extract concept ["
                       (str/join " " shared-terms) "] from cycle"))
   :invert      (fn [{:keys [ns instability]}]
                  (str " — invert: put protocol in " ns
                       " (I=" (format "%.2f" instability) ")"))
   :investigate (fn [_] " — investigate")})
```

New `find-cycles` body (sketch):

```clojure
(defn- find-cycles [cycles change-pairs conceptual-pairs nodes]
  (mapv (fn [members]
          (let [subject  {:members members}
                strat    (cycle-strategy members change-pairs conceptual-pairs nodes)
                suffix   ((get strategy-suffix (:strategy strat)) (:basis strat))
                reason   (str (count members) "-namespace cycle" suffix)]
            {:severity  :high
             :category  :cycle
             :action    (finding/action-for-category :cycle)
             :next-step (finding/next-step-for :cycle subject nodes)
             :subject   subject
             :reason    reason
             :evidence  {:members members :size (count members)
                         :strategy       (:strategy strat)
                         :strategy-basis (:basis strat)}}))
        cycles))
```

### `find-hidden-change` — asymmetry detection

```clojure
(defn- change-asymmetry
  "Returns {:directional? bool :satellite ns :anchor ns :ratio double}."
  [ns-a ns-b conf-a conf-b]
  (when (and (pos? conf-a) (pos? conf-b))
    (let [[satellite anchor s-conf a-conf]
          (if (> conf-a conf-b)
            [ns-a ns-b conf-a conf-b]
            [ns-b ns-a conf-b conf-a])
          ratio (/ s-conf a-conf)]
      {:directional? (> ratio 2.0)
       :satellite    satellite
       :anchor       anchor
       :ratio        ratio})))
```

In `find-hidden-change`, compute asymmetry before building the map:

```clojure
(let [asym (change-asymmetry (:ns-a p) (:ns-b p)
                              (:confidence-a p) (:confidence-b p))
      directional? (:directional? asym)
      action   (if directional?
                 :narrow-satellite-interface
                 (finding/action-for-category :hidden-change))
      reason   (if directional?
                 (str (:satellite asym) " changes whenever "
                      (:anchor asym) " changes"
                      " (conf=" (format "%.0f%%" (* 100.0
                                 (max (:confidence-a p)
                                      (:confidence-b p)))) ")")
                 (str "hidden change coupling — score="
                      (format "%.2f" (:score p))
                      " (" (:co-changes p) " co-changes)"))]
  {:severity  :medium
   :category  :hidden-change
   :action    action
   :next-step (finding/next-step-for :hidden-change subject nil)
   :subject   {:ns-a (:ns-a p) :ns-b (:ns-b p)}
   :reason    reason
   :evidence  (cond-> {:score        (:score p)
                        :co-changes   (:co-changes p)
                        :confidence-a (:confidence-a p)
                        :confidence-b (:confidence-b p)}
                directional?
                (assoc :direction {:satellite (:satellite asym)
                                   :anchor    (:anchor asym)
                                   :ratio     (:ratio asym)}))})
```

### New `find-vestigial-edges`

```clojure
(defn- find-vestigial-edges
  "Structural edges with no conceptual signal and no change coupling.
  Only runs when both lenses are present (conceptual-pairs and change-pairs
  are non-nil); returns [] otherwise to avoid false positives."
  [graph nodes conceptual-pairs change-pairs]
  (if (or (nil? conceptual-pairs) (nil? change-pairs))
    []
    (let [project-nss  (set (keys graph))
          concept-keys (into #{} (map finding/pair-key) conceptual-pairs)
          change-keys  (into #{} (map finding/pair-key) change-pairs)
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
                        (let [subject {:ns-a ns-a :ns-b ns-b}]
                          {:severity  :low
                           :category  :vestigial-edge
                           :action    (finding/action-for-category :vestigial-edge)
                           :next-step (finding/next-step-for :vestigial-edge subject nil)
                           :subject   subject
                           :reason    (str ns-a " → " ns-b
                                          " — no conceptual or change signal")
                           :evidence  {:ns-a         ns-a
                                       :ns-b         ns-b
                                       :instability-a (:instability (get node-map ns-a))
                                       :instability-b (:instability (get node-map ns-b))}})))))
                deps))
             graph)))))
```

### `diagnose` — updated signature and body

```clojure
;; before
(defn diagnose [{:keys [cycles nodes conceptual-pairs change-pairs]}])

;; after
(defn diagnose [{:keys [cycles nodes conceptual-pairs change-pairs graph]}])
```

Add `find-vestigial-edges` to the `concat` in `diagnose`:

```clojure
(find-vestigial-edges (or graph {}) (or nodes [])
                      conceptual-pairs change-pairs)
```

Update `find-cycles` call:

```clojure
;; before
(find-cycles (or cycles []))

;; after
(find-cycles (or cycles [])
             (or change-pairs [])
             (or conceptual-pairs [])
             (or nodes []))
```

### Tests — additions to `test/gordian/diagnose_test.clj`

```
all-findings-have-action-test
  every finding in a full diagnose result has a non-nil :action key

all-pair-findings-have-next-step-test
  every pair-subject finding has a non-nil :next-step key
  next-step string starts with "gordian "

find-vestigial-edges-test
  edge with no concept/change signal → :vestigial-edge finding
  edge where pair is in conceptual-pairs → not flagged
  edge where pair is in change-pairs → not flagged
  external dep (not in graph keys) → not flagged
  nil conceptual-pairs → returns []
  nil change-pairs → returns []

cycle-strategy-test
  member pair Jaccard > 0.7 → :merge with basis
  member pair with independent-terms → :extract with shared-terms in basis
  member with Ca≥3 and I<0.3 → :invert with ns in basis
  no evidence → :investigate
  strategy-basis present in cycle finding :evidence

asymmetric-change-test
  conf-a / conf-b > 2 → :narrow-satellite-interface action
  conf-a / conf-b ≤ 2 → :investigate-contract action
  directional finding :evidence has :direction key with :satellite :anchor :ratio
  symmetric finding :evidence has no :direction key
  either conf = 0 → falls back to symmetric (no division by zero)
```

---

## Step 3 — `output.clj`: render action and next-step, suppressed count

**Commit:** `feat: output — action, next-step lines, suppressed-count in diagnose summary`

### `format-finding-lines`

```clojure
;; before
(defn- format-finding-lines [f]
  (concat
   [(str (severity-marker ...) ...)
    (str "  " (:reason f))]
   (format-evidence-lines f)
   [""]))

;; after
(defn- format-finding-lines [f]
  (concat
   [(str (severity-marker ...) ...)
    (str "  " (:reason f))]
   (format-evidence-lines f)
   (when-let [a (:action f)]
     (when-let [disp (finding/action-display a)]
       [(str "  → " disp)]))
   (when-let [ns (:next-step f)]
     [(str "  $ " ns)])
   [""]))
```

### `md-format-finding`

```clojure
;; after — append action and next-step before trailing blank
(concat
  [subject-line "" (:reason f) ""]
  (md-evidence-lines f)
  (when-let [a (:action f)]
    (when-let [disp (finding/action-display a)]
      [(str "**→** " disp) ""]))
  (when-let [ns (:next-step f)]
    [(str "`$ " ns "`") ""])
  [""])
```

### `format-diagnose` — suppressed-count arity

Add a new highest arity:

```clojure
;; existing highest arity
([{:keys [src-dirs]} health findings clusters-data rank] ...)

;; new arity — suppressed-count is optional, nil = no message
([report health findings clusters-data rank suppressed-count]
 (let [... existing let bindings ...]
   ;; replace the summary line at the end:
   (str n-total " finding" (when (not= 1 n-total) "s")
        " (" n-high " high, " n-medium " medium, " n-low " low)"
        (when (pos? (or suppressed-count 0))
          (str " — " suppressed-count " noise suppressed (--show-noise to include)")))))
```

Same pattern for `format-diagnose-md` — update the trailing bold line.

### `print-diagnose`

```clojure
;; new arity
([report health findings clusters rank suppressed-count]
 (run! println (format-diagnose report health findings clusters rank suppressed-count)))
```

### Tests — additions to `test/gordian/output_test.clj`

```
format-finding-lines-action-test
  finding with known :action → output contains "  → <display>"
  finding with :action :none → no action line emitted
  finding with nil :action → no action line emitted

format-finding-lines-next-step-test
  finding with :next-step → output contains "  $ gordian ..."
  finding with nil :next-step → no next-step line emitted

format-diagnose-suppressed-count-test
  suppressed-count > 0 → summary line contains "N noise suppressed"
  suppressed-count 0   → summary line does not mention suppressed
  suppressed-count nil → summary line does not mention suppressed
```

---

## Step 4 — `main.clj`: `--show-noise` flag and suppression filter

**Commit:** `feat: main — --show-noise flag, suppress family-noise findings by default`

### `cli-spec` addition

```clojure
:show-noise {:desc "Include family-naming-noise findings (suppressed by default)"
             :coerce :boolean}
```

### `help-text` addition

```
  --show-noise                  Include family-naming-noise findings in output
```

### `diagnose-cmd` — filter + suppressed count

```clojure
;; before (line 380–384)
findings0   (diagnose/diagnose report)
clusters0   (cluster/cluster-findings findings0)
context     (prioritize/cluster-context ...)
findings    (prioritize/rank-findings findings0 rank context)

;; after
[{:keys [show-noise rank ...]}]
all-findings0   (diagnose/diagnose report)
noise-free0     (if show-noise
                  all-findings0
                  (remove finding/family-noise? all-findings0))
suppressed      (- (count all-findings0) (count noise-free0))
clusters0       (cluster/cluster-findings noise-free0)
context         (prioritize/cluster-context ...)
findings        (prioritize/rank-findings noise-free0 rank context)
```

Thread `suppressed` to text/markdown output calls:

```clojure
markdown (run! println (output/format-diagnose-md report health findings clusters rank suppressed))
:else    (output/print-diagnose report health findings clusters rank suppressed)
```

EDN and JSON output receive `all-findings0` (unfiltered) — no change to
envelope construction. Consumers can filter on `(finding/family-noise? f)`.

### Tests — additions to `test/gordian/main_test.clj` (or integration tests)

```
noise-suppression-test
  by default: family-noise findings absent from text output
  by default: suppressed-count > 0 when noise findings exist
  --show-noise: family-noise findings present in text output
  EDN output: contains noise findings regardless of --show-noise
```

---

## Step 5 — Tests: cross-cutting and integration

**Commit:** `test: diagnose actionability — full coverage`

### `test/gordian/diagnose_test.clj` — integration

Update existing `diagnose-integration-test` fixture to assert:
- All findings have `:action`
- All pair findings have `:next-step` starting with `"gordian "`
- `:vestigial-edge` category present when structural edge has no lens support
- Sorted order still holds with vestigial-edge findings included

### `test/gordian/finding_test.clj`

Assert `action-display` is complete: every value in `action-map` is a key
in `action-display`.

---

## Dependency Graph

```
Step 1  finding.clj (action-for-category, next-step-for)
  └── Step 2  diagnose.clj (all finders, vestigial, strategy, asymmetry)
        ├── Step 3  output.clj (render action + next-step + suppressed)
        └── Step 4  main.clj (--show-noise, filter, thread suppressed)
              └── Step 5  integration tests
```

Steps 3 and 4 are independent of each other after Step 2.

---

## Invariants preserved

- `diagnose` remains a pure function — no IO, no filtering
- All existing finding keys (`:severity :category :subject :reason :evidence`) present on all findings
- New keys (`:action :next-step`) are additions only — no existing consumers break
- EDN/JSON schema: new keys pass through the generic walker unchanged
- `family-noise?` predicate already public — no new API needed for suppression
- Test count expected to grow by ~25–30 assertions across 3 test files
