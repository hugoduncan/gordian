(ns gordian.local.burden)

(def ^:private severity-bands
  [{:max 7.0 :severity {:level :low :label "Low local comprehension burden"}}
   {:max 15.0 :severity {:level :moderate :label "Moderate local comprehension burden"}}
   {:max 25.0 :severity {:level :high :label "High local comprehension burden"}}
   {:max Double/MAX_VALUE :severity {:level :very-high :label "Very high local comprehension burden"}}])

(defn flow-burden [{:keys [branch nest interrupt recursion logic]}]
  (+ branch nest interrupt recursion logic))

(defn state-burden [{:keys [mutation-sites rebindings temporal-dependencies effect-reads effect-writes]}]
  (+ (* 2 mutation-sites)
     rebindings
     temporal-dependencies
     effect-reads
     (* 2 effect-writes)))

(defn shape-burden [{:keys [transitions destructure variant nesting sentinel]}]
  (+ transitions destructure (* 2 variant) nesting sentinel))

(defn oscillation-count [levels]
  (let [transitions (count (filter identity (map (fn [a b] (and a b (not= a b)))
                                                  levels
                                                  (rest levels))))]
    (max 0 (dec transitions))))

(defn abstraction-burden [{:keys [levels distinct-levels incidental]}]
  (let [level-mix (* 2 (max 0 (dec (count distinct-levels))))
        oscillation (oscillation-count levels)]
    (+ level-mix oscillation incidental)))

(defn dependency-burden [{:keys [helpers opaque-stages inversion semantic-jumps]}]
  (+ helpers
     (max 0 (- opaque-stages 2))
     (* 2 inversion)
     (max 0 (dec semantic-jumps))))

(defn working-set [{:keys [program-points]}]
  (let [point-score (fn [{:keys [live-bindings active-predicates mutable-entities shape-assumptions unresolved-semantics]}]
                      (+ (count (remove #{'_} live-bindings))
                         active-predicates
                         mutable-entities
                         shape-assumptions
                         (min 2 unresolved-semantics)))
        samples (mapv point-score program-points)
        peak (apply max 0 samples)
        avg (if (seq samples)
              (/ (double (reduce + 0 samples)) (count samples))
              0.0)
        burden (+ (max 0 (- peak 4))
                  (* 0.5 (max 0 (- avg 3.0))))]
    {:peak peak
     :avg avg
     :burden burden}))

(defn lcc-total
  [{:keys [flow-burden state-burden shape-burden abstraction-burden dependency-burden working-set]}]
  (+ (* 1.0 flow-burden)
     (* 1.3 state-burden)
     (* 1.2 shape-burden)
     (* 1.4 abstraction-burden)
     (* 1.1 dependency-burden)
     (* 1.5 (:burden working-set))))

(defn lcc-severity [total]
  (:severity (first (filter #(<= total (:max %)) severity-bands))))

(defn score-unit [{:keys [evidence] :as unit}]
  (let [flow        (flow-burden (:flow evidence))
        state       (state-burden (:state evidence))
        shape       (shape-burden (:shape evidence))
        abstraction (abstraction-burden (:abstraction evidence))
        dependency  (dependency-burden (:dependency evidence))
        ws          (working-set evidence)
        total       (lcc-total {:flow-burden flow
                                :state-burden state
                                :shape-burden shape
                                :abstraction-burden abstraction
                                :dependency-burden dependency
                                :working-set ws})]
    (assoc unit
           :flow-burden flow
           :state-burden state
           :shape-burden shape
           :abstraction-burden abstraction
           :dependency-burden dependency
           :working-set ws
           :lcc-total total
           :lcc-severity (lcc-severity total))))
