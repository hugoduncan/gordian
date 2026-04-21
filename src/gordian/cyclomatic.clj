(ns gordian.cyclomatic
  "Pure cyclomatic complexity analysis over edamame forms.

  Rules:
  - every function starts at complexity 1
  - +1 for each branching form: if/if-not/if-let/if-some/when/when-not/
    when-let/when-some/when-first
  - +1 for each loop form: doseq/for/while
  - +N for cond/condp, where N is the number of non-default test clauses
  - +N for case, where N is the number of branch arms (default ignored)
  - +(k-1) for boolean chains (and/or) with k operands
  - +1 for each catch clause in try"
  (:require [clojure.string :as str]))

;(set! *warn-on-reflection* true)

(def ^:private branch-ops
  '#{if if-not if-let if-some when when-not when-let when-some when-first})

(def ^:private loop-ops
  '#{doseq for while})

(def ^:private defn-ops
  '#{defn defn-})

(defn- strip-doc-and-attr
  [xs]
  (cond-> xs
    (string? (first xs)) rest
    (map? (first xs)) rest))

(defn- defn-arities
  "Return a vector of {:args [...] :body [...]} arities for a defn-like tail."
  [tail]
  (let [tail (strip-doc-and-attr tail)]
    (cond
      (vector? (first tail))
      [{:args (first tail)
        :body (vec (rest tail))}]

      (seq? (first tail))
      (->> tail
           (filter seq?)
           (mapv (fn [arity]
                   {:args (first arity)
                    :body (vec (rest arity))})))

      :else
      [])))

(defn- cond-branches
  [args]
  (->> args
       (partition 2 2 [])
       (keep (fn [[test _expr]]
               (when (and test (not= :else test))
                 1)))
       count))

(defn- condp-branches
  [args]
  (let [pairs (drop 2 args)]
    (->> pairs
         (partition 2 2 [])
         (keep (fn [[test _expr]]
                 (when (and test (not= :else test))
                   1)))
         count)))

(defn- case-branches
  [args]
  (let [clauses (drop 1 args)
        default? (odd? (count clauses))
        branch-clauses (if default? (butlast clauses) clauses)]
    (/ (count branch-clauses) 2)))

(declare complexity*)

(defn- extra-complexity
  [form]
  (if (seq? form)
    (let [op   (first form)
          args (rest form)]
      (+
       (cond
         (branch-ops op) 1
         (loop-ops op)   1
         (= 'cond op)    (cond-branches args)
         (= 'condp op)   (condp-branches args)
         (= 'case op)    (case-branches args)
         (= 'and op)     (max 0 (dec (count args)))
         (= 'or op)      (max 0 (dec (count args)))
         :else           0)
       (cond
         (= 'quote op) 0
         (= 'var op)   0
         (= 'try op)
         (reduce + 0
                 (map (fn [x]
                        (if (and (seq? x) (= 'catch (first x)))
                          (inc (reduce + 0 (map complexity* (drop 3 x))))
                          (complexity* x)))
                      args))

         :else
         (reduce + 0 (map complexity* form)))))
    (cond
      (map? form)    (reduce + 0 (map complexity* (mapcat identity form)))
      (vector? form) (reduce + 0 (map complexity* form))
      (set? form)    (reduce + 0 (map complexity* form))
      :else          0)))

(defn- complexity*
  [form]
  (extra-complexity form))

(defn arity-complexity
  "Return the cyclomatic complexity of one function arity body."
  [body]
  (+ 1 (reduce + 0 (map complexity* body))))

(defn function-complexities
  "Analyze one parsed file payload {:file :ns :forms}.
   Returns {:file :ns :functions [...]}.

   Each function record includes max complexity across arities and the
   individual arity complexities for transparency."
  [{:keys [file ns forms]}]
  {:file file
   :ns ns
   :functions
   (->> forms
        (keep (fn [form]
                (when (and (seq? form)
                           (defn-ops (first form))
                           (symbol? (second form)))
                  (let [name       (second form)
                        arities    (defn-arities (nnext form))
                        arity-cxs  (mapv (comp arity-complexity :body) arities)]
                    {:ns ns
                     :file file
                     :name name
                     :qualified-name (symbol (str ns) (str name))
                     :arity-count (count arities)
                     :arity-complexities arity-cxs
                     :complexity (apply max 0 arity-cxs)}))))
        vec)})

(defn namespace-summary
  "Build a namespace-level rollup from one file analysis."
  [{:keys [file ns functions]}]
  (let [complexities (map :complexity functions)]
    {:ns ns
     :file file
     :function-count (count functions)
     :total-complexity (reduce + 0 complexities)
     :avg-complexity (if (seq complexities)
                       (/ (double (reduce + 0 complexities)) (count complexities))
                       0.0)
     :max-complexity (apply max 0 complexities)
     :functions (sort-by (juxt (comp - :complexity) :name) functions)}))

(defn rollup
  "Build the full cyclomatic report from scanned file payloads.
   `files` is a seq of {:file :ns :forms}."
  [files]
  (let [namespaces        (->> files
                               (map function-complexities)
                               (map namespace-summary)
                               (sort-by (juxt (comp - :max-complexity) :ns))
                               vec)
        namespace-count   (count namespaces)
        function-count    (reduce + 0 (map :function-count namespaces))
        total-complexity  (reduce + 0 (map :total-complexity namespaces))
        max-function      (first (sort-by (juxt (comp - :complexity) :qualified-name)
                                          (mapcat :functions namespaces)))
        avg-complexity    (if (pos? function-count)
                            (/ (double total-complexity) function-count)
                            0.0)]
    {:gordian/command :cyclomatic
     :summary {:namespace-count namespace-count
               :function-count function-count
               :total-complexity total-complexity
               :avg-complexity avg-complexity
               :max-complexity (or (:complexity max-function) 0)}
     :max-function (select-keys max-function [:ns :name :qualified-name :complexity :file])
     :namespaces namespaces}))
