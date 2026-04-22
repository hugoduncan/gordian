(ns gordian.cyclomatic
  "Pure cyclomatic complexity analysis over edamame forms.

  Current rules:
  - every analyzed unit starts at complexity 1
  - +1 for each branching form: if/if-not/if-let/if-some/when/when-not/
    when-let/when-some/when-first
  - +N for cond, where N is the number of clause pairs, including trailing :else
  - +N for condp, where N is the number of clause pairs after the dispatch/test
    prefix, including default when present
  - +N for case, where N is the number of branch arms, including default when present
  - +(k-1) for boolean chains (and/or) with k operands
  - +1 for each catch clause in try
  - +1 for each cond-> condition/form pair
  - loop/recursion/iteration forms do not independently increment complexity")

(def ^:private branch-ops
  '#{if if-not if-let if-some when when-not when-let when-some when-first})

(def ^:private defn-ops
  '#{defn defn-})

(def ^:private risk-bands
  [{:max 10 :risk {:level :simple :label "Simple, low risk"}}
   {:max 20 :risk {:level :moderate :label "Moderate complexity, moderate risk"}}
   {:max 50 :risk {:level :high :label "High complexity, high risk"}}
   {:max Long/MAX_VALUE :risk {:level :untestable :label "Untestable, very high risk"}}])

(def ^:private risk-order
  {:simple 0 :moderate 1 :high 2 :untestable 3})

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
  (count (partition 2 2 [] args)))

(defn- condp-branches
  [args]
  (let [clauses (drop 2 args)]
    (cond
      (<= (count clauses) 1) 0
      (even? (count clauses)) (/ (count clauses) 2)
      :else (inc (/ (dec (count clauses)) 2)))))

(defn- case-branches
  [args]
  (let [clauses (drop 1 args)]
    (cond
      (<= (count clauses) 1) 0
      (even? (count clauses)) (/ (count clauses) 2)
      :else (inc (/ (dec (count clauses)) 2)))))

(defn- cond->-branches
  [args]
  (count (partition 2 2 [] (drop 1 args))))

(declare complexity*)

(defn- extra-complexity
  [form]
  (if (seq? form)
    (let [op   (first form)
          args (rest form)]
      (+
       (cond
         (branch-ops op) 1
         (= 'cond op)    (cond-branches args)
         (= 'condp op)   (condp-branches args)
         (= 'case op)    (case-branches args)
         (= 'cond-> op)  (cond->-branches args)
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

(defn cc-risk
  "Return the standard risk descriptor for a cyclomatic complexity value."
  [cc]
  (:risk (first (filter #(<= cc (:max %)) risk-bands))))

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
            (let [name       (second form)
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

(defn analyze-units
  "Analyze canonical arity-level units and attach metric-qualified fields."
  [units]
  (->> units
       (mapv (fn [{:keys [body] :as unit}]
               (let [cc (arity-complexity body)]
                 (-> unit
                     (dissoc :body :unit-id)
                     (assoc :metric :cyclomatic-complexity
                            :cc cc
                            :cc-decision-count (dec cc)
                            :cc-risk (cc-risk cc))))))))

(defn- empty-risk-counts []
  {:simple 0 :moderate 0 :high 0 :untestable 0})

(defn- risk-counts
  [units]
  (reduce (fn [counts unit]
            (update counts (get-in unit [:cc-risk :level]) (fnil inc 0)))
          (empty-risk-counts)
          units))

(defn namespace-rollups
  "Build canonical namespace rollups from analyzed units."
  [units]
  (->> units
       (group-by :ns)
       (map (fn [[ns ns-units]]
              (let [ccs (map :cc ns-units)
                    unit-count (count ns-units)]
                {:ns ns
                 :unit-count unit-count
                 :total-cc (reduce + 0 ccs)
                 :avg-cc (if (pos? unit-count)
                           (/ (double (reduce + 0 ccs)) unit-count)
                           0.0)
                 :max-cc (apply max 0 ccs)
                 :cc-risk-counts (risk-counts ns-units)})))
       (sort-by (juxt (comp - :max-cc) :ns))
       vec))

(defn project-rollup
  "Build canonical project rollup from analyzed units and namespace rollups."
  [units namespace-rollups]
  (let [ccs (map :cc units)
        unit-count (count units)]
    {:unit-count unit-count
     :namespace-count (count namespace-rollups)
     :total-cc (reduce + 0 ccs)
     :avg-cc (if (pos? unit-count)
               (/ (double (reduce + 0 ccs)) unit-count)
               0.0)
     :max-cc (apply max 0 ccs)
     :cc-risk-counts (risk-counts units)}))

(defn sort-units
  "Sort canonical units by one of :cc, :ns, :var, or :cc-risk."
  [units sort-key]
  (vec
   (let [sort-key (or sort-key :cc)]
     (case sort-key
       :ns
       (sort-by (juxt :ns (comp - :cc) :var :kind :arity :dispatch) units)

       :var
       (sort-by (juxt :ns :var (comp - :cc) :kind :arity :dispatch) units)

       :cc-risk
       (sort-by (juxt (comp - risk-order (comp :level :cc-risk))
                      (comp - :cc)
                      :ns :var :kind :arity :dispatch)
                units)

       (sort-by (juxt (comp - :cc) :ns :var :kind :arity :dispatch) units)))))

(defn filter-by-min-cc
  "Filter units/rollups by minimum displayed complexity threshold.
   For units, uses :cc. For rollups, uses :max-cc."
  [xs min-cc]
  (let [min-cc (or min-cc 0)]
    (->> xs
         (filter (fn [x]
                   (let [n (or (:cc x) (:max-cc x) 0)]
                     (<= min-cc n))))
         vec)))

(defn truncate-section
  "Apply section-local top-N truncation when top is positive."
  [xs top]
  (if (and top (pos? top))
    (vec (take top xs))
    (vec xs)))

(defn max-unit
  "Return the highest-complexity analyzed unit, or nil when no units exist."
  [units]
  (first (sort-by (juxt (comp - :cc) :ns :var :kind :arity :dispatch) units)))

(defn rollup
  "Build the canonical complexity report from scanned file payloads.
   `files` is a seq of {:file :ns :forms}."
  [files]
  (let [units             (->> files
                               (mapcat #(analyzable-units (assoc % :origin (or (:origin %) :src))))
                               analyze-units)
        namespace-rollups (namespace-rollups units)
        project           (project-rollup units namespace-rollups)]
    {:gordian/command  :complexity
     :metric           :cyclomatic-complexity
     :units            (vec units)
     :namespace-rollups namespace-rollups
     :project-rollup   project
     :max-unit         (max-unit units)}))
