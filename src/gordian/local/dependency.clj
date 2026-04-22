(ns gordian.local.dependency
  (:require [gordian.local.common :as common]))

(defn opaque-op? [op]
  (and (symbol? op)
       (not (common/transparent-ops op))
       (not (common/branch-ops op))
       (not (common/special-forms op))
       (not (common/threading-ops op))))

(defn non-transparent-call-sites [forms]
  (for [form forms
        :when (and (common/seq-form? form)
                   (symbol? (common/op-of form))
                   (opaque-op? (common/op-of form)))]
    form))

(defn opaque-chain-data [steps]
  (let [pipeline-steps (filter #(= :pipeline-stage (:kind %)) steps)
        opaque-stages (count (filter #(opaque-op? (common/op-of (:form %))) pipeline-steps))
        stage-ops (set (keep #(some-> % :form common/op-of) pipeline-steps))]
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
