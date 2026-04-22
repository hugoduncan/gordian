(ns gordian.local.burden)

(def burden-families
  [:flow :state :shape :abstraction :dependency :working-set])

(def ^:private severity-bands
  [{:max 3.5 :severity {:level :low :label "Low local comprehension burden"}}
   {:max 6.5 :severity {:level :moderate :label "Moderate local comprehension burden"}}
   {:max 9.5 :severity {:level :high :label "High local comprehension burden"}}
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

(defn raw-burdens
  [{:keys [flow-burden state-burden shape-burden abstraction-burden dependency-burden working-set]}]
  {:flow flow-burden
   :state state-burden
   :shape shape-burden
   :abstraction abstraction-burden
   :dependency dependency-burden
   :working-set (:burden working-set)})

(defn non-zero-values [xs]
  (filterv pos? xs))

(defn quantile
  [xs q]
  (let [xs (vec (sort xs))
        n  (count xs)]
    (cond
      (zero? n) 0.0
      (= 1 n) (double (first xs))
      :else (let [idx (int (Math/ceil (* q n)))]
              (double (nth xs (min (dec n) (max 0 (dec idx)))))))))

(defn family-scale
  [values]
  (let [nz (non-zero-values values)]
    (cond
      (seq nz)
      (let [sorted (vec (sort nz))
            p75    (quantile sorted 0.75)
            median (quantile sorted 0.50)]
        (double (max 1.0 (if (<= (count sorted) 3) median p75))))

      :else 1.0)))

(defn calibrate
  [units]
  (let [family-values (reduce (fn [acc unit]
                                (reduce (fn [acc family]
                                          (update acc family conj (get (raw-burdens unit) family 0.0)))
                                        acc
                                        burden-families))
                              (zipmap burden-families (repeat []))
                              units)
        scales (into {}
                     (map (fn [family]
                            [family (family-scale (get family-values family []))])
                          burden-families))
        weights (zipmap burden-families (repeat 1.0))]
    {:transform :log1p-over-scale
     :scale-rule :p75-non-zero-with-sparse-median-fallback
     :weights weights
     :families (into {}
                     (map (fn [family]
                            [family {:scale (double (get scales family 1.0))
                                     :non-zero-count (count (non-zero-values (get family-values family [])))
                                     :sample-count (count (get family-values family []))}])
                          burden-families))}))

(defn normalize-family
  [raw scale]
  (Math/log1p (/ (double raw) (double (max scale 1.0)))))

(defn normalized-burdens
  [raw-burdens calibration]
  (into {}
        (map (fn [family]
               (let [scale (get-in calibration [:families family :scale] 1.0)]
                 [family (normalize-family (get raw-burdens family 0.0) scale)]))
             burden-families)))

(defn lcc-total
  [{:keys [normalized calibration]}]
  (reduce + 0.0
          (map (fn [family]
                 (* (double (get-in calibration [:weights family] 1.0))
                    (double (get normalized family 0.0))))
               burden-families)))

(defn lcc-severity [total]
  (:severity (first (filter #(<= total (:max %)) severity-bands))))

(defn score-unit [{:keys [evidence] :as unit}]
  (let [flow        (flow-burden (:flow evidence))
        state       (state-burden (:state evidence))
        shape       (shape-burden (:shape evidence))
        abstraction (abstraction-burden (:abstraction evidence))
        dependency  (dependency-burden (:dependency evidence))
        ws          (working-set evidence)]
    (assoc unit
           :flow-burden flow
           :state-burden state
           :shape-burden shape
           :abstraction-burden abstraction
           :dependency-burden dependency
           :working-set ws)))

(defn apply-calibration
  [calibration unit]
  (let [raw        (raw-burdens unit)
        normalized (normalized-burdens raw calibration)
        total      (lcc-total {:normalized normalized
                               :calibration calibration})]
    (assoc unit
           :normalized-burdens normalized
           :lcc-calibration {:weights (:weights calibration)
                             :transform (:transform calibration)}
           :lcc-total total
           :lcc-severity (lcc-severity total))))
