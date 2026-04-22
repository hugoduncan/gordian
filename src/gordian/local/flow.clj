(ns gordian.local.flow
  (:require [gordian.local.common :as common]
            [gordian.local.ops :as ops]))

(defn- condition-expr [form]
  (case (common/op-of form)
    if (second form)
    if-let (nth form 1 nil)
    if-some (nth form 1 nil)
    when (second form)
    when-not (second form)
    when-let (nth form 1 nil)
    when-some (nth form 1 nil)
    when-first (nth form 1 nil)
    condp (second form)
    nil))

(defn- boolean-connective-extras [expr]
  (let [forms (tree-seq coll? seq expr)]
    (reduce
     +
     0
     (for [form forms
           :when (common/seq-form? form)
           :let [op (common/op-of form)]
           :when (#{'and 'or} op)]
       (* 0.5 (max 0 (- (count (rest form)) 1)))))))

(defn- compound-negation-score [expr]
  (let [forms (tree-seq coll? seq expr)]
    (reduce
     +
     0
     (for [form forms
           :when (and (common/seq-form? form)
                      (= 'not (common/op-of form))
                      (common/seq-form? (second form)))]
       0.5))))

(defn- nested-predicate-score [expr]
  (let [forms (tree-seq coll? seq expr)]
    (reduce
     +
     0
     (for [form forms
           :when (and (common/seq-form? form)
                      (ops/branch (common/op-of form)))]
       0.5))))

(defn- logic-score [expr]
  (+ (boolean-connective-extras expr)
     (compound-negation-score expr)
     (nested-predicate-score expr)))

(defn- branch-weight [form]
  (let [op (common/op-of form)]
    (cond
      (ops/half-branch op) 0.5
      (#{'if 'if-let 'if-some} op) 1
      (= 'cond op) (count (partition 2 2 [] (rest form)))
      (= 'condp op)
      (let [clauses (drop 3 form)]
        (cond
          (<= (count clauses) 1) 0
          (even? (count clauses)) (/ (count clauses) 2)
          :else (inc (/ (dec (count clauses)) 2))))
      (= 'case op)
      (let [clauses (drop 2 form)]
        (if (even? (count clauses))
          (/ (count clauses) 2)
          (inc (/ (dec (count clauses)) 2))))
      :else 0)))

(defn gather-flow [form depth unit-var]
  (let [op (common/op-of form)
        base {:branch 0.0
              :nest 0
              :interrupt 0
              :recursion 0
              :logic 0.0
              :max-depth 0}]
    (cond
      (common/seq-form? form)
      (if (#{'throw 'reduced 'recur} op)
        (assoc base :interrupt 1)
        (let [self-call? (and unit-var (= op unit-var))
              branch-form? (ops/branch op)
              depth' (if branch-form? (inc depth) depth)
              children (map #(gather-flow % depth' unit-var) (rest form))
              combined (apply merge-with + base children)
              branch (if branch-form? (branch-weight form) 0.0)
              logic (if branch-form? (logic-score (condition-expr form)) 0.0)
              nest  (if branch-form? (max 0 (dec depth')) 0)
              max-depth (max (:max-depth combined)
                             (if branch-form? depth' 0))]
          (-> combined
              (update :branch + branch)
              (update :logic + logic)
              (update :nest + nest)
              (update :recursion + (if self-call? 1 0))
              (assoc :max-depth max-depth))))

      (coll? form)
      (apply merge-with + base (map #(gather-flow % depth unit-var) form))

      :else
      base)))
