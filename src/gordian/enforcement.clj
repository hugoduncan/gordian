(ns gordian.enforcement)

(defn sort-violations
  [violations]
  (vec
   (sort-by (juxt :check-index
                  (comp - double :actual)
                  :ns :var :arity :dispatch)
            violations)))

(defn evaluate
  [{:keys [units checks metric-value unit->violation]}]
  (let [unit-count  (count units)
        checks      (map-indexed vector checks)
        evaluations (mapv (fn [[idx {:keys [metric metric-token threshold] :as check}]]
                            (let [actuals     (map #(double (metric-value % metric-token)) units)
                                  max-observed (if (seq actuals) (apply max actuals) 0.0)
                                  violating    (->> units
                                                    (keep (fn [unit]
                                                            (let [actual (double (metric-value unit metric-token))]
                                                              (when (> actual threshold)
                                                                (assoc (unit->violation unit)
                                                                       :check-index idx
                                                                       :metric metric
                                                                       :metric-token metric-token
                                                                       :threshold threshold
                                                                       :actual actual)))))
                                                    sort-violations)]
                              {:check (assoc check
                                             :passed? (empty? violating)
                                             :violation-count (count violating)
                                             :max-observed max-observed)
                               :violations violating}))
                          checks)
        checks      (mapv :check evaluations)
        violations  (into [] cat (map :violations evaluations))]
    {:subject :units
     :unit-count unit-count
     :checks checks
     :violations violations
     :passed? (every? :passed? checks)}))
