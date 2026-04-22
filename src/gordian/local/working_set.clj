(ns gordian.local.working-set
  (:require [clojure.set :as set]
            [gordian.local.common :as common]
            [gordian.local.dependency :as dependency]
            [gordian.local.shape :as shape]
            [gordian.local.state :as state]))

(defn- live-shape-assumptions [env]
  (count
   (filter (fn [[_ value-shape]]
             (contains? #{:map :vector :set :seq-like :record/object} (:class value-shape)))
           env)))

(defn- program-point
  "Canonical working-set sample shape.

   Keys:
   - :kind                 => sampling context (:binding-group, :main-path-step, ...)
   - :step-form            => source form that triggered the sample
   - :step-kind            => originating main-step kind
   - :live-bindings        => symbols still live at this point
   - :active-predicates    => number of branch predicates currently carried
   - :mutable-entities     => live mutable bindings
   - :shape-assumptions    => live bindings with non-scalar structural shape assumptions
   - :unresolved-semantics => capped opaque-helper count for the originating step"
  [base-point step kind extras]
  (merge base-point
         {:kind kind
          :step-form (:form step)
          :step-kind (:kind step)}
         extras))

(defn program-points [steps args]
  (let [initial-env (into {}
                          (for [arg args
                                :when (common/symbol-binding? arg)]
                            [arg {:class :unknown :nil? false}]))
        mutable-syms (state/mutable-binding-symbols steps)]
    (loop [env initial-env
           points []
           [step & more] steps]
      (if-not step
        (vec points)
        (let [current-shape (or (:shape step) (shape/classify-shape (:form step) env))
              env'  (if (= :binding (:kind step))
                      (reduce (fn [acc sym] (assoc acc sym current-shape))
                              env
                              (:introduced-symbols step))
                      env)
              base-point {:live-bindings (set (keys env'))
                          :active-predicates (:active-predicates step)
                          :mutable-entities (count (set/intersection mutable-syms (set (keys env'))))
                          :shape-assumptions (live-shape-assumptions env')
                          :unresolved-semantics (dependency/step-unresolved-semantics step)}
              sample-point (fn [kind extras]
                             (program-point base-point step kind extras))
              points' (-> points
                          (cond-> (and (= :binding (:kind step)) (:group-last? step))
                            (conj (sample-point :binding-group {})))
                          (cond-> (and (= :pipeline-stage (:kind step))
                                       (not (dependency/branch-local-step? step)))
                            (conj (sample-point :pipeline-stage {})))
                          (cond-> (and (contains? #{:expr :pipeline-stage} (:kind step))
                                       (not (:branch? step))
                                       (not (dependency/branch-local-step? step)))
                            (conj (sample-point :main-path-step {})))
                          (cond-> (:branch? step)
                            (conj (sample-point :branch-entry {:active-predicates (inc (:active-predicates step))})))
                          (cond-> (#{'cond 'condp 'case} (common/op-of (:form step)))
                            (conj (sample-point :clause-entry {:active-predicates (inc (:active-predicates step))}))))]
          (recur env' points' more))))))
