(ns gordian.local.steps
  (:require [gordian.local.common :as common]))

(defn direct-call-symbols [form]
  (cond
    (and (common/seq-form? form) (symbol? (common/op-of form)))
    [(common/op-of form)]

    (and (symbol? form) (not (common/special-forms form)))
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

(defn- binding-step [group-id last? binding expr active-predicates]
  {:kind :binding
   :group-id group-id
   :group-last? last?
   :form expr
   :binding binding
   :introduced-symbols (common/binding-symbols binding)
   :destructure-count (common/destructure-symbol-count binding)
   :active-predicates active-predicates
   :calls (direct-call-symbols expr)})

(defn- expr-step [kind form active-predicates]
  {:kind kind
   :form form
   :active-predicates active-predicates
   :branch? (boolean (common/branch-ops (common/op-of form)))
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

      (common/threading-ops op)
      (map #(expr-step :pipeline-stage % active-predicates) (thread-stages form))

      (common/if-like-ops op)
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
      (or (common/incidental-ops op)
          (common/mutation-ops op)
          (common/effect-read-ops op)
          (common/effect-write-ops op)
          (common/mutable-cell-constructors op)
          (= op 'set!)
          (= op 'new)
          (= op '.))
      :mechanism

      (or (common/threading-ops op)
          (:branch? step)
          (> (count (:calls step)) 1))
      :orchestration

      (or (common/shape-ops op)
          (map? form)
          (vector? form)
          (set? form))
      :data-shaping

      :else
      :domain)))
