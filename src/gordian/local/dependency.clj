(ns gordian.local.dependency
  (:require [gordian.local.common :as common]
            [gordian.local.ops :as ops]))

(defn branch-local-step?
  [step]
  (true? (:branch-local? step)))

(defn opaque-op? [op]
  (and (symbol? op)
       (not (ops/transparent op))
       (not (ops/branch op))
       (not (ops/special-forms op))
       (not (ops/threading op))))

(defn non-transparent-call-sites [forms]
  (for [form forms
        :when (and (common/seq-form? form)
                   (symbol? (common/op-of form))
                   (opaque-op? (common/op-of form)))]
    form))

(defn opaque-chain-data [steps]
  (let [main-path-pipeline-steps (remove branch-local-step?
                                         (filter #(= :pipeline-stage (:kind %)) steps))
        opaque-stages (count (filter #(opaque-op? (common/op-of (:form %))) main-path-pipeline-steps))
        stage-ops (set (keep #(some-> % :form common/op-of) main-path-pipeline-steps))]
    {:opaque-stages opaque-stages
     :stage-ops stage-ops}))

(defn distinct-semantic-jumps [steps]
  (count
   (set
    (keep (fn [step]
            (let [op (common/op-of (:form step))]
              (when (opaque-op? op) op)))
          steps))))

(defn step-unresolved-semantics [step]
  (min 2
       (count
        (filter opaque-op? (:calls step)))))
