(ns gordian.cyclomatic
  "Pure local complexity analysis over edamame forms.

  Current built-in metrics:
  - cyclomatic complexity
  - lines of code (LOC)

  Cyclomatic rules:
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
  - loop/recursion/iteration forms do not independently increment complexity

  LOC rules:
  - count non-blank, non-comment physical lines
  - span is the whole reported unit form, not just the executable body
  - comment-only means ^\\s*;+.*$")

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

(def ^:private metrics
  [:cyclomatic-complexity :lines-of-code])

(def ^:private min-metrics
  #{:cc :loc})

(def ^:private bar-metrics
  #{:cc :loc})

(def ^:private comment-only-line
  #"^\s*;+.*$")

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

(defn- line-span
  [form]
  (when-let [{:keys [row end-row]} (meta form)]
    (when (and row end-row)
      [row end-row])))

(defn- count-loc-in-span
  [source [start-line end-line]]
  (let [lines (clojure.string/split-lines source)]
    (->> (subvec (vec lines) (dec start-line) (min (count lines) end-line))
         (remove clojure.string/blank?)
         (remove #(re-matches comment-only-line %))
         count)))

(defn- with-line-and-loc
  [source unit form]
  (if-let [[start-line end-line] (line-span form)]
    (assoc unit
           :line start-line
           :end-line end-line
           :loc (count-loc-in-span source [start-line end-line]))
    (assoc unit :line nil :end-line nil :loc 0)))

(defn analyzable-units
  "Extract analyzable top-level units from one parsed file payload
   {:file :ns :forms :source}. Returns one unit per arity-level executable body.

   Implemented v1 extraction:
   - defn / defn-
   - defmethod
   - top-level def with literal fn value"
  [{:keys [file ns forms origin source]}]
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
                 (with-line-and-loc
                   source
                   {:ns ns
                    :var name
                    :kind :defn-arity
                    :arity (count args)
                    :dispatch nil
                    :file file
                    :origin origin
                    :body body
                    :unit-id [ns name :defn-arity i]}
                   form))
               (defn-arities (nnext form))))

            (and (seq? form)
                 (= 'defmethod (first form))
                 (symbol? (second form)))
            [(with-line-and-loc
               source
               {:ns ns
                :var (second form)
                :kind :defmethod
                :arity nil
                :dispatch (nth form 2 nil)
                :file file
                :origin origin
                :body (vec (drop 3 form))
                :unit-id [ns (second form) :defmethod (nth form 2 nil)]}
               form)]

            (and (seq? form)
                 (= 'def (first form))
                 (symbol? (second form)))
            (let [name       (second form)
                  value-form (nth form 2 nil)]
              (map-indexed
               (fn [i {:keys [args body]}]
                 (with-line-and-loc
                   source
                   {:ns ns
                    :var name
                    :kind :def-fn-arity
                    :arity (count args)
                    :dispatch nil
                    :file file
                    :origin origin
                    :body body
                    :unit-id [ns name :def-fn-arity i]}
                   form))
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
                     (assoc :cc cc
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
                    locs (map :loc ns-units)
                    unit-count (count ns-units)]
                {:ns ns
                 :unit-count unit-count
                 :total-cc (reduce + 0 ccs)
                 :avg-cc (if (pos? unit-count)
                           (/ (double (reduce + 0 ccs)) unit-count)
                           0.0)
                 :max-cc (apply max 0 ccs)
                 :cc-risk-counts (risk-counts ns-units)
                 :total-loc (reduce + 0 locs)
                 :avg-loc (if (pos? unit-count)
                            (/ (double (reduce + 0 locs)) unit-count)
                            0.0)
                 :max-loc (apply max 0 locs)})))
       (sort-by (juxt (comp - :max-cc) (comp - :max-loc) :ns))
       vec))

(defn project-rollup
  "Build canonical project rollup from analyzed units and namespace rollups."
  [units namespace-rollups]
  (let [ccs (map :cc units)
        locs (map :loc units)
        unit-count (count units)]
    {:unit-count unit-count
     :namespace-count (count namespace-rollups)
     :total-cc (reduce + 0 ccs)
     :avg-cc (if (pos? unit-count)
               (/ (double (reduce + 0 ccs)) unit-count)
               0.0)
     :max-cc (apply max 0 ccs)
     :cc-risk-counts (risk-counts units)
     :total-loc (reduce + 0 locs)
     :avg-loc (if (pos? unit-count)
                (/ (double (reduce + 0 locs)) unit-count)
                0.0)
     :max-loc (apply max 0 locs)}))

(defn sort-units
  "Sort canonical units by one of :cc, :loc, :ns, :var, or :cc-risk."
  [units sort-key]
  (vec
   (let [sort-key (or sort-key :cc)]
     (case sort-key
       :loc
       (sort-by (juxt (comp - :loc) (comp - :cc) :ns :var :kind :arity :dispatch) units)

       :ns
       (sort-by (juxt :ns (comp - :cc) (comp - :loc) :var :kind :arity :dispatch) units)

       :var
       (sort-by (juxt :ns :var (comp - :cc) (comp - :loc) :kind :arity :dispatch) units)

       :cc-risk
       (sort-by (juxt (comp - risk-order (comp :level :cc-risk))
                      (comp - :cc)
                      (comp - :loc)
                      :ns :var :kind :arity :dispatch)
                units)

       (sort-by (juxt (comp - :cc) (comp - :loc) :ns :var :kind :arity :dispatch) units)))))

(defn parse-min-expression
  "Parse one metric-qualified minimum threshold expression like `cc=10`.
   Returns [:metric n] or nil for malformed expressions."
  [expr]
  (when-let [[_ metric n] (re-matches #"([a-z-]+)=(\d+)" (str expr))]
    (let [metric (keyword metric)
          n      (parse-long n)]
      (when (and (min-metrics metric) (pos? n))
        [metric n]))))

(defn mins-map
  "Build canonical minimum-threshold map from parsed CLI opts.
   Repeatable `--min` values combine into one metric->threshold map."
  [{:keys [min]}]
  (when (seq min)
    (into {} (keep parse-min-expression) min)))

(defn unit-satisfies-mins?
  [mins unit]
  (every? (fn [[metric threshold]]
            (<= threshold (get unit metric 0)))
          mins))

(defn filter-units-by-mins
  "Display-only filter over unit rows. All mins must be satisfied."
  [units mins]
  (if (seq mins)
    (into [] (filter #(unit-satisfies-mins? mins %)) units)
    (vec units)))

(defn truncate-section
  "Apply section-local top-N truncation when top is positive."
  [xs top]
  (if (and top (pos? top))
    (vec (take top xs))
    (vec xs)))

(defn complexity-scope
  "Build canonical complexity scope metadata from resolved typed paths.
   `mode` is :discovered or :explicit."
  [mode paths]
  {:mode    mode
   :source? (boolean (some #(= :src (:kind %)) paths))
   :tests?  (boolean (some #(= :test (:kind %)) paths))
   :paths   (mapv :dir paths)})

(defn effective-bar-metric
  "Return the effective histogram bar metric for human-readable complexity output.
   Explicit :bar wins; otherwise :loc when sorting by :loc; else :cc."
  [{:keys [sort bar]}]
  (or bar
      (when (= :loc sort) :loc)
      :cc))

(defn complexity-options
  "Build canonical complexity options metadata from resolved CLI opts."
  [{:keys [sort top bar namespace-rollup project-rollup] :as opts}]
  {:sort sort
   :top top
   :bar bar
   :namespace-rollup (boolean namespace-rollup)
   :project-rollup (boolean project-rollup)
   :mins (mins-map opts)})

(defn- sort-rollups
  [rollups sort-key]
  (vec
   (case (or sort-key :cc)
     :loc (sort-by (juxt (comp - :max-loc) (comp - :max-cc) :ns) rollups)
     :ns  (sort-by (juxt :ns (comp - :max-cc) (comp - :max-loc)) rollups)
     :var (sort-by (juxt :ns (comp - :max-cc) (comp - :max-loc)) rollups)
     :cc-risk (sort-by (juxt (comp - :max-cc) (comp - :max-loc) :ns) rollups)
     (sort-by (juxt (comp - :max-cc) (comp - :max-loc) :ns) rollups))))

(defn finalize-report
  "Attach complexity metadata and apply display shaping in pure code.
   `mode` is :discovered or :explicit; `paths` is resolved typed scan paths."
  [report mode paths opts]
  (let [mins    (mins-map opts)
        options (complexity-options opts)]
    (cond-> (-> report
                (assoc :gordian/command :complexity
                       :metrics metrics
                       :src-dirs (mapv :dir paths)
                       :scope (complexity-scope mode paths)
                       :options options
                       :bar-metric (effective-bar-metric opts))
                (update :units filter-units-by-mins mins)
                (update :units sort-units (:sort opts))
                (update :units truncate-section (:top opts))
                (update :namespace-rollups sort-rollups (:sort opts))
                (update :namespace-rollups truncate-section (:top opts)))
      (not (:namespace-rollup options)) (dissoc :namespace-rollups)
      (not (:project-rollup options)) (dissoc :project-rollup))))

(defn max-unit
  "Return the highest-complexity analyzed unit, or nil when no units exist."
  [units]
  (first (sort-by (juxt (comp - :cc) :ns :var :kind :arity :dispatch) units)))

(defn rollup
  "Build the canonical complexity report from scanned file payloads.
   `files` is a seq of {:file :ns :forms :source}."
  [files]
  (let [units             (->> files
                               (mapcat #(analyzable-units (assoc % :origin (or (:origin %) :src))))
                               analyze-units)
        namespace-rollups (namespace-rollups units)
        project           (project-rollup units namespace-rollups)]
    {:gordian/command   :complexity
     :metrics           metrics
     :units             (vec units)
     :namespace-rollups namespace-rollups
     :project-rollup    project
     :max-unit          (max-unit units)}))
