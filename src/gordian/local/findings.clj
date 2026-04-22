(ns gordian.local.findings
  (:require [gordian.local.burden :as burden]))

(defn- finding
  [kind severity score message]
  {:kind kind :severity severity :score score :message message})

(defn findings-for-unit
  [{:keys [flow-burden state-burden shape-burden abstraction-burden dependency-burden
           working-set evidence]}]
  (let [max-depth (get-in evidence [:flow :max-depth] 0)
        logic (get-in evidence [:flow :logic] 0)
        mutable-sites (get-in evidence [:state :mutation-sites] 0)
        mutable-entities (get-in evidence [:state :mutable-entities] 0)
        temporal (get-in evidence [:state :temporal-dependencies] 0)
        transitions (get-in evidence [:shape :transitions] 0)
        variants (get-in evidence [:shape :variant] 0)
        distinct-levels (count (get-in evidence [:abstraction :distinct-levels]))
        levels (get-in evidence [:abstraction :levels] [])
        oscillations (burden/oscillation-count levels)
        opaque-stages (get-in evidence [:dependency :opaque-stages] 0)
        helper-score (+ (get-in evidence [:dependency :helpers] 0)
                        (max 0 (dec (get-in evidence [:dependency :semantic-jumps] 0))))
        peak (:peak working-set)
        ws-burden (:burden working-set)]
    (cond-> []
      (>= max-depth 3)
      (conj (finding :deep-control-nesting :high flow-burden
                     "Control nesting reaches 3+ counted levels"))

      (>= logic 2.0)
      (conj (finding :predicate-density :medium flow-burden
                     "Conditions contain several nested or chained predicate checks"))

      (or (>= mutable-sites 1) (>= mutable-entities 2))
      (conj (finding :mutable-state-tracking :high state-burden
                     "Explicit mutable state updates or tracked cells must be carried locally"))

      (>= temporal 1)
      (conj (finding :temporal-coupling :medium state-burden
                     "Later meaning depends on earlier updates or effect timing"))

      (>= transitions 3)
      (conj (finding :shape-churn :high shape-burden
                     "Value shape changes repeatedly along the local path"))

      (>= variants 1)
      (conj (finding :conditional-return-shape :medium shape-burden
                     "Branches return materially different shapes"))

      (>= distinct-levels 3)
      (conj (finding :abstraction-mix :high abstraction-burden
                     "Three or more abstraction levels appear on the main path"))

      (>= oscillations 3)
      (conj (finding :abstraction-oscillation :high abstraction-burden
                     "Abstraction levels alternate repeatedly across adjacent steps"))

      (>= opaque-stages 3)
      (conj (finding :opaque-pipeline :medium dependency-burden
                     "Pipeline contains 3+ opaque stages"))

      (>= helper-score 3)
      (conj (finding :helper-chasing :medium dependency-burden
                     "Understanding correctness requires chasing several helpers"))

      (or (>= peak 7) (>= ws-burden 3))
      (conj (finding :working-set-overload :high ws-burden
                     "Too many facts stay live simultaneously for easy local reasoning")))))
