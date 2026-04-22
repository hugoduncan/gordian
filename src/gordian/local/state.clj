(ns gordian.local.state
  (:require [clojure.set :as set]
            [gordian.local.common :as common]
            [gordian.local.shape :as shape]))

(defn meaningful-rebindings [steps-with-shapes]
  (let [binding-steps (filter #(= :binding (:kind %)) steps-with-shapes)]
    (loop [seen {}
           [step & more] binding-steps
           total 0]
      (if-not step
        total
        (let [syms (:introduced-symbols step)
              current-shape (:shape step)
              changed? (some (fn [sym]
                               (let [prev (get seen sym)]
                                 (and prev (not (shape/same-coarse-shape? prev current-shape)))))
                             syms)
              seen' (reduce (fn [acc sym] (assoc acc sym current-shape)) seen syms)]
          (recur seen' more (if changed? (inc total) total)))))))

(defn mutable-binding-symbols [steps-with-shapes]
  (reduce
   set/union
   #{}
   (for [step steps-with-shapes
         :when (and (= :binding (:kind step))
                    (common/mutable-cell-constructors (common/op-of (:form step))))]
     (:introduced-symbols step))))

(defn temporal-dependencies [forms mutable-syms]
  (let [mutations (for [form forms
                        :when (and (common/seq-form? form)
                                   (common/mutation-ops (common/op-of form))
                                   (symbol? (second form))
                                   (mutable-syms (second form)))]
                    (second form))
        reads (for [form forms
                    :when (or (and (common/seq-form? form)
                                   (= 'deref (common/op-of form))
                                   (symbol? (second form))
                                   (mutable-syms (second form)))
                              (and (symbol? form)
                                   (mutable-syms form)))]
                (if (common/seq-form? form) (second form) form))]
    (count (set/intersection (set mutations) (set reads)))))
