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

(defn- fn-arities
  "Return a vector of {:args [...] :body [...]} arities for an fn-like tail."
  [tail]
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
    []))

(defn- defn-arities
  "Return a vector of {:args [...] :body [...]} arities for a defn-like tail."
  [tail]
  (fn-arities (strip-doc-and-attr tail)))

(defn- literal-fn-arities
  "Return arities for a top-level literal fn form, or nil if form is not a
  literal fn with an analyzable shape."
  [form]
  (when (and (seq? form)
             (= 'fn (first form)))
    (let [tail (rest form)
          tail (if (symbol? (first tail)) (rest tail) tail)]
      (fn-arities tail))))

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

(defn analyzable-units
  "Extract analyzable top-level units from one parsed file payload
   {:file :ns :forms}. Returns one unit per arity-level executable body.

   Implemented v1 extraction:
   - defn / defn-
   - defmethod
   - top-level def with literal fn value"
  [{:keys [file ns forms origin]}]
  (->> forms
       (mapcat
        (fn [form]
          (cond
            (and (seq? form)
                 (defn-ops (first form))
                 (symbol? (second form)))
            (let [name (second form)]
              (map-indexed
               (fn [i {:keys [args body]}]
                 {:ns ns
                  :var name
                  :kind :defn-arity
                  :arity (count args)
                  :dispatch nil
                  :file file
                  :line nil
                  :origin origin
                  :body body
                  :unit-id [ns name :defn-arity i]})
               (defn-arities (nnext form))))

            (and (seq? form)
                 (= 'defmethod (first form))
                 (symbol? (second form)))
            [{:ns ns
              :var (second form)
              :kind :defmethod
              :arity nil
              :dispatch (nth form 2 nil)
              :file file
              :line nil
              :origin origin
              :body (vec (drop 3 form))
              :unit-id [ns (second form) :defmethod (nth form 2 nil)]}]

            (and (seq? form)
                 (= 'def (first form))
                 (symbol? (second form)))
            (let [name      (second form)
                  value-form (nth form 2 nil)]
              (map-indexed
               (fn [i {:keys [args body]}]
                 {:ns ns
                  :var name
                  :kind :def-fn-arity
                  :arity (count args)
                  :dispatch nil
                  :file file
                  :line nil
                  :origin origin
                  :body body
                  :unit-id [ns name :def-fn-arity i]})
               (or (literal-fn-arities value-form) [])))

            :else
            [])))
       vec))

(defn function-complexities
  "Analyze one parsed file payload {:file :ns :forms}.
   Returns {:file :ns :functions [...]}.

   Each function record includes max complexity across arities and the
   individual arity complexities for transparency."
  [{:keys [file ns] :as parsed-file}]
  (let [units (analyzable-units parsed-file)]
    {:file file
     :ns ns
     :functions
     (->> units
          (group-by (juxt :var :kind :dispatch))
          vals
          (mapv (fn [var-units]
                  (let [{:keys [var]} (first var-units)
                        arity-cxs (mapv (comp arity-complexity :body) var-units)]
                    {:ns ns
                     :file file
                     :name var
                     :qualified-name (symbol (str ns) (str var))
                     :arity-count (count var-units)
                     :arity-complexities arity-cxs
                     :complexity (apply max 0 arity-cxs)})))
          (sort-by (juxt (comp - :complexity) :name))
          vec)}))

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
