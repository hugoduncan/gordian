(ns gordian.local.steps
  (:require [gordian.local.common :as common]
            [gordian.local.ops :as ops]))

(defn direct-call-symbols [form]
  (cond
    (and (common/seq-form? form) (symbol? (common/op-of form)))
    [(common/op-of form)]

    (and (symbol? form) (not (ops/special-forms form)))
    [form]

    :else
    []))

(defn- thread-stages [form]
  (case (common/op-of form)
    (-> ->> some-> some->>)
    (mapv (fn [stage]
            (if (common/seq-form? stage)
              stage
              (list stage)))
          (drop 2 form))

    (cond-> cond->>)
    (mapv second (partition 2 (drop 2 form)))

    []))

(defn- binding-step
  "Canonical main-step shape for a binding introduction.

   Keys:
   - :kind               => :binding
   - :group-id           => synthetic id shared by one let/loop binding vector
   - :group-last?        => true on the final binding in that vector
   - :form               => bound expression
   - :binding            => original binding form
   - :introduced-symbols => symbols introduced by the binding form
   - :destructure-count  => number of introduced symbols
   - :active-predicates  => branch predicates currently in scope
   - :branch-local?      => true when this step occurs inside a branch body
   - :calls              => direct call symbols visible at this step"
  [group-id last? binding expr active-predicates]
  {:kind :binding
   :group-id group-id
   :group-last? last?
   :form expr
   :binding binding
   :introduced-symbols (common/binding-symbols binding)
   :destructure-count (common/destructure-symbol-count binding)
   :active-predicates active-predicates
   :branch-local? (pos? active-predicates)
   :calls (direct-call-symbols expr)})

(defn- expr-step
  "Canonical main-step shape for a non-binding expression on the tracked main path."
  [kind form active-predicates]
  {:kind kind
   :form form
   :active-predicates active-predicates
   :branch-local? (pos? active-predicates)
   :branch? (boolean (ops/branch (common/op-of form)))
   :calls (direct-call-symbols form)})

(declare form->steps)

(defn- body->steps [forms active-predicates]
  (mapcat #(form->steps % active-predicates) forms))

(defn form->steps [form active-predicates]
  (let [op (common/op-of form)]
    (cond
      (= 'do op)
      (body->steps (rest form) active-predicates)

      (#{'let 'let* 'loop 'loop*} op)
      (let [bindings (partition 2 (second form))
            group-id (gensym "binding-group")]
        (concat
         (map-indexed (fn [idx [binding expr]]
                        (binding-step group-id (= idx (dec (count bindings))) binding expr active-predicates))
                      bindings)
         (body->steps (drop 2 form) active-predicates)))

      (ops/threading op)
      (map #(expr-step :pipeline-stage % active-predicates) (thread-stages form))

      (ops/if-like op)
      (concat
       [(expr-step :expr form active-predicates)]
       (body->steps (drop 2 form) (inc active-predicates)))

      :else
      [(expr-step :expr form active-predicates)])))

(defn main-path-steps [body]
  (vec (body->steps body 0)))

(defn step-abstraction-level [step]
  (let [form (or (:label-form step) (:form step))
        op   (common/op-of form)]
    (cond
      (or (ops/incidental op)
          (ops/mutation op)
          (ops/effect-read op)
          (ops/effect-write op)
          (ops/mutable-cell-constructors op)
          (= op 'set!)
          (= op 'new)
          (= op '.))
      :mechanism

      (or (ops/threading op)
          (:branch? step)
          (> (count (:calls step)) 1))
      :orchestration

      (or (ops/shape op)
          (map? form)
          (vector? form)
          (set? form))
      :data-shaping

      :else
      :domain)))
