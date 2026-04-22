(ns gordian.local.evidence
  (:require [clojure.set :as set]))

(def ^:private branch-ops
  '#{if if-let if-some when when-not when-let when-some when-first cond condp case})

(def ^:private if-like-ops
  '#{if if-let if-some when when-not when-let when-some when-first})

(def ^:private half-branch-ops
  '#{when when-not when-let when-some when-first})

(def ^:private transparent-ops
  '#{assoc dissoc update merge select-keys conj into vec set mapv
     map filter keep remove keys vals first rest next get contains?
     inc dec + - * / = < > <= >= not some? nil? empty?
     identity keyword symbol str hash-map array-map vector})

(def ^:private incidental-ops
  '#{log debug info warn error trace println prn printf})

(def ^:private mutation-ops
  '#{swap! reset! compare-and-set! vswap! vreset! set! assoc! conj! disj! pop!})

(def ^:private mutable-cell-constructors
  '#{atom volatile! ref agent})

(def ^:private effect-read-ops
  '#{slurp read-string rand rand-int System/currentTimeMillis System/nanoTime})

(def ^:private effect-write-ops
  '#{spit println prn printf})

(def ^:private inversion-ops
  '#{reduce transduce doseq for future pmap mapcat keep})

(def ^:private shape-ops
  '#{assoc dissoc merge update select-keys conj into vec set mapv keys vals
     hash-map array-map vector})

(def ^:private threading-ops
  '#{-> ->> some-> some->> cond-> cond->>})

(def ^:private sentinel-literals
  #{nil false :ok :error :none :some :left :right :success :failure})

(def ^:private special-forms
  '#{let let* loop loop* do quote var fn fn* recur try catch throw def
     new . if if-let if-some when when-not when-let when-some when-first
     cond condp case letfn})

(defn- seq-form? [x]
  (seq? x))

(defn- op-of [form]
  (when (seq-form? form)
    (first form)))

(defn- tree-forms [body]
  (tree-seq coll? seq body))

(defn- scalar-literal? [x]
  (or (nil? x)
      (true? x)
      (false? x)
      (string? x)
      (number? x)
      (char? x)
      (keyword? x)))

(defn- symbol-binding? [sym]
  (and (symbol? sym)
       (not= '_ sym)
       (not= '& sym)))

(defn- literal-keyword-set [x]
  (when (and (coll? x) (every? keyword? x))
    (set x)))

(defn- binding-symbols [binding]
  (letfn [(collect [x]
            (cond
              (symbol-binding? x) #{x}

              (vector? x)
              (reduce set/union #{} (map collect (remove #{'&} x)))

              (map? x)
              (reduce
               set/union
               #{}
               (concat
                (when-let [as-sym (:as x)] [(collect as-sym)])
                (when-let [ks (:keys x)] [(set (filter symbol-binding? ks))])
                (when-let [ss (:syms x)] [(set (filter symbol-binding? ss))])
                (when-let [ss (:strs x)] [(set (map symbol ss))])
                (for [[k v] x
                      :when (not (contains? #{:as :keys :syms :strs :or} k))]
                  (collect v))))

              :else
              #{}))]
    (collect binding)))

(defn- destructure-symbol-count [binding]
  (count (binding-symbols binding)))

(defn- condition-expr [form]
  (case (op-of form)
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
           :when (seq-form? form)
           :let [op (op-of form)]
           :when (#{'and 'or} op)]
       (* 0.5 (max 0 (- (count (rest form)) 1)))))))

(defn- compound-negation-score [expr]
  (let [forms (tree-seq coll? seq expr)]
    (reduce
     +
     0
     (for [form forms
           :when (and (seq-form? form)
                      (= 'not (op-of form))
                      (seq-form? (second form)))]
       0.5))))

(defn- nested-predicate-score [expr]
  (let [forms (tree-seq coll? seq expr)]
    (reduce
     +
     0
     (for [form forms
           :when (and (seq-form? form)
                      (branch-ops (op-of form)))]
       0.5))))

(defn- logic-score [expr]
  (+ (boolean-connective-extras expr)
     (compound-negation-score expr)
     (nested-predicate-score expr)))

(defn- branch-weight [form]
  (let [op (op-of form)]
    (cond
      (half-branch-ops op) 0.5
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

(defn- classify-shape [form env]
  (cond
    (nil? form) {:class :nil :nil? true}
    (scalar-literal? form) {:class :scalar :nil? false}
    (symbol? form) (or (get env form) {:class :unknown :nil? false})
    (map? form) {:class :map :nil? false :keyset (set (filter keyword? (keys form)))}
    (vector? form) {:class :vector :nil? false}
    (set? form) {:class :set :nil? false}
    (and (seq-form? form) (= 'throw (op-of form))) {:class :throws}
    (seq-form? form)
    (let [op (op-of form)]
      (cond
        (#{'hash-map 'array-map 'assoc 'dissoc 'merge 'update 'select-keys} op)
        (let [base (when (#{'assoc 'dissoc 'merge 'update 'select-keys} op)
                     (classify-shape (second form) env))
              base-keyset (or (:keyset base) #{})
              keyset (case op
                       hash-map (->> (rest form)
                                     (take-nth 2)
                                     (filter keyword?)
                                     set)
                       array-map (->> (rest form)
                                      (take-nth 2)
                                      (filter keyword?)
                                      set)
                       assoc (into base-keyset (filter keyword? (take-nth 2 (drop 2 form))))
                       dissoc (reduce disj base-keyset (filter keyword? (rest (rest form))))
                       merge (into base-keyset
                                   (mapcat #(or (:keyset (classify-shape % env)) #{})
                                           (rest (rest form))))
                       select-keys (or (literal-keyword-set (nth form 2 nil)) base-keyset)
                       base-keyset)]
          {:class :map :nil? false :keyset keyset})

        (#{'vec 'mapv 'vector} op)
        {:class :vector :nil? false}

        (#{'set} op)
        {:class :set :nil? false}

        (#{'map 'filter 'keep 'remove 'keys 'vals 'rest 'next} op)
        {:class :seq-like :nil? false}

        (#{'first 'get 'find} op)
        {:class :scalar :nil? true}

        (#{'some-> 'some->>} op)
        (assoc (classify-shape (second form) env) :nil? true)

        (threading-ops op)
        (classify-shape (second form) env)

        :else
        {:class :unknown :nil? false}))
    :else {:class :unknown :nil? false}))

(defn- same-coarse-shape? [a b]
  (and (= (:class a) (:class b))
       (= (boolean (:nil? a)) (boolean (:nil? b)))
       (or (not= :map (:class a))
           (= (:keyset a) (:keyset b)))))

(defn- variant-outcome? [outcomes]
  (let [outcomes (vec outcomes)]
    (boolean
     (some true?
           (for [a outcomes
                 b outcomes
                 :when (not= a b)]
             (or (not= (:class a) (:class b))
                 (not= (boolean (:nil? a)) (boolean (:nil? b)))
                 (and (= :map (:class a) (:class b))
                      (:keyset a)
                      (:keyset b)
                      (not= (:keyset a) (:keyset b)))
                 (or (= :throws (:class a))
                     (= :throws (:class b)))))))))

(defn- step-abstraction-level [step]
  (let [form (or (:label-form step) (:form step))
        op   (op-of form)]
    (cond
      (or (incidental-ops op)
          (mutation-ops op)
          (effect-read-ops op)
          (effect-write-ops op)
          (mutable-cell-constructors op)
          (= op 'set!)
          (= op 'new)
          (= op '.))
      :mechanism

      (or (threading-ops op)
          (:branch? step)
          (> (count (:calls step)) 1))
      :orchestration

      (or (shape-ops op)
          (map? form)
          (vector? form)
          (set? form))
      :data-shaping

      :else
      :domain)))

(defn- opaque-op? [op]
  (and (symbol? op)
       (not (transparent-ops op))
       (not (branch-ops op))
       (not (special-forms op))
       (not (threading-ops op))))

(defn- direct-call-symbols [form]
  (cond
    (and (seq-form? form) (symbol? (op-of form)))
    [(op-of form)]

    (and (symbol? form) (not (special-forms form)))
    [form]

    :else
    []))

(defn- thread-stages [form]
  (let [op (op-of form)]
    (case op
      (-> ->> some-> some->>)
      (mapv (fn [stage]
              (if (seq-form? stage)
                stage
                (list stage)))
            (drop 2 form))

      (cond-> cond->>)
      (mapv second (partition 2 (drop 2 form)))

      [])))

(defn- binding-step [group-id idx last? binding expr active-predicates]
  {:kind :binding
   :group-id group-id
   :group-last? last?
   :form expr
   :binding binding
   :introduced-symbols (binding-symbols binding)
   :destructure-count (destructure-symbol-count binding)
   :active-predicates active-predicates
   :calls (direct-call-symbols expr)})

(defn- expr-step [kind form active-predicates]
  {:kind kind
   :form form
   :active-predicates active-predicates
   :branch? (boolean (branch-ops (op-of form)))
   :calls (direct-call-symbols form)})

(declare form->steps)

(defn- body->steps [forms active-predicates]
  (mapcat #(form->steps % active-predicates) forms))

(defn- form->steps [form active-predicates]
  (let [op (op-of form)]
    (cond
      (= 'do op)
      (body->steps (rest form) active-predicates)

      (#{'let 'let* 'loop 'loop*} op)
      (let [bindings (partition 2 (second form))
            group-id (gensym "binding-group")]
        (concat
         (map-indexed (fn [idx [binding expr]]
                        (binding-step group-id idx (= idx (dec (count bindings))) binding expr active-predicates))
                      bindings)
         (body->steps (drop 2 form) active-predicates)))

      (threading-ops op)
      (map #(expr-step :pipeline-stage % active-predicates) (thread-stages form))

      (if-like-ops op)
      (concat
       [(expr-step :expr form active-predicates)]
       (body->steps (drop 2 form) (inc active-predicates)))

      :else
      [(expr-step :expr form active-predicates)])))

(defn- main-path-steps [body]
  (vec (body->steps body 0)))

(defn- gather-flow [form depth unit-var]
  (let [op (op-of form)
        base {:branch 0.0
              :nest 0
              :interrupt 0
              :recursion 0
              :logic 0.0
              :max-depth 0}]
    (cond
      (seq-form? form)
      (if (#{'throw 'reduced 'recur} op)
        (assoc base :interrupt 1)
        (let [self-call? (and unit-var (= op unit-var))
              branch-form? (branch-ops op)
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

(defn- branch-outcomes [form env]
  (let [op (op-of form)]
    (cond
      (#{'if 'if-let 'if-some} op)
      [(classify-shape (nth form 2 nil) env)
       (classify-shape (nth form 3 nil) env)]

      (#{'when 'when-not 'when-let 'when-some 'when-first} op)
      [(classify-shape `(do ~@(drop 2 form)) env)
       {:class :nil :nil? true}]

      (= 'cond op)
      (mapv #(classify-shape (second %) env) (partition 2 2 [] (rest form)))

      (= 'condp op)
      (let [clauses (drop 3 form)]
        (if (even? (count clauses))
          (mapv #(classify-shape (second %) env) (partition 2 clauses))
          (conj (mapv #(classify-shape (second %) env)
                      (partition 2 (butlast clauses)))
                (classify-shape (last clauses) env))))

      (= 'case op)
      (let [clauses (drop 2 form)]
        (if (even? (count clauses))
          (mapv #(classify-shape (second %) env) (partition 2 clauses))
          (conj (mapv #(classify-shape (second %) env)
                      (partition 2 (butlast clauses)))
                (classify-shape (last clauses) env))))

      :else
      [])))

(defn- branch-variant-count [form env]
  (let [op (op-of form)]
    (cond
      (if-like-ops op)
      (if (variant-outcome? (branch-outcomes form env)) 1 0)

      (= 'cond op)
      (let [exprs (map second (partition 2 2 [] (rest form)))]
        (if (and (seq exprs)
                 (variant-outcome? (map #(classify-shape % env) exprs)))
          1
          0))

      (= 'condp op)
      (let [clauses (drop 3 form)
            branch-exprs (if (even? (count clauses))
                           (map second (partition 2 clauses))
                           (concat (map second (partition 2 (butlast clauses)))
                                   [(last clauses)]))]
        (if (and (seq branch-exprs)
                 (variant-outcome? (map #(classify-shape % env) branch-exprs)))
          1
          0))

      (= 'case op)
      (let [clauses (drop 2 form)
            branch-exprs (if (even? (count clauses))
                           (map second (partition 2 clauses))
                           (concat (map second (partition 2 (butlast clauses)))
                                   [(last clauses)]))]
        (if (and (seq branch-exprs)
                 (variant-outcome? (map #(classify-shape % env) branch-exprs)))
          1
          0))

      :else
      0)))

(defn- branch-regions [forms env]
  (letfn [(collect [form env]
            (cond
              (not (seq-form? form))
              []

              (#{'let 'let* 'loop 'loop*} (op-of form))
              (let [bindings (partition 2 (second form))
                    env' (reduce (fn [acc [binding expr]]
                                   (reduce (fn [acc2 sym]
                                             (assoc acc2 sym (classify-shape expr acc2)))
                                           acc
                                           (binding-symbols binding)))
                                 env
                                 bindings)]
                (mapcat #(collect % env') (drop 2 form)))

              :else
              (let [children (mapcat #(collect % env) (rest form))]
                (if (branch-ops (op-of form))
                  (let [outcomes (branch-outcomes form env)]
                    (conj (vec children)
                          {:form form
                           :outcomes outcomes
                           :variant? (pos? (branch-variant-count form env))
                           :variant-count (branch-variant-count form env)}))
                  (vec children)))))]
    (vec (mapcat #(collect % env) forms))))

(defn- shape-transition? [a b]
  (and a b
       (or (not= (:class a) (:class b))
           (not= (boolean (:nil? a)) (boolean (:nil? b)))
           (and (= :map (:class a) (:class b))
                (:keyset a)
                (:keyset b)
                (not= (:keyset a) (:keyset b))))))

(defn- step-shapes [steps args]
  (let [initial-env (into {}
                          (for [arg args
                                :when (symbol-binding? arg)]
                            [arg {:class :unknown :nil? false}]))]
    (reduce
     (fn [{:keys [env steps*]} step]
       (if (= :binding (:kind step))
         (let [shape (classify-shape (:form step) env)
               env'  (reduce (fn [acc sym] (assoc acc sym shape)) env (:introduced-symbols step))]
           {:env env'
            :steps* (conj steps* (assoc step :shape shape))})
         {:env env
          :steps* (conj steps* (assoc step :shape (classify-shape (:form step) env)))}))
     {:env initial-env :steps* []}
     steps)))

(defn- main-path-transitions [steps-with-shapes]
  (let [shapes (map :shape steps-with-shapes)]
    (count (filter true? (map shape-transition? shapes (rest shapes))))))

(defn- data-nesting-depth [form]
  (cond
    (map? form) (inc (reduce max 0 (map data-nesting-depth (concat (keys form) (vals form)))))
    (vector? form) (inc (reduce max 0 (map data-nesting-depth form)))
    (set? form) (inc (reduce max 0 (map data-nesting-depth form)))
    (and (seq-form? form) (shape-ops (op-of form)))
    (inc (reduce max 0 (map data-nesting-depth (rest form))))
    :else 0))

(defn- sentinel-count [forms]
  (count
   (filter true?
           (for [form forms
                 :when (seq-form? form)
                 :let [op (op-of form)]
                 :when (or (branch-ops op) (= 'case op) (= '= op))]
             (boolean (some sentinel-literals form))))))

(defn- meaningful-rebindings [steps-with-shapes]
  (let [binding-steps (filter #(= :binding (:kind %)) steps-with-shapes)]
    (loop [seen {}
           [step & more] binding-steps
           total 0]
      (if-not step
        total
        (let [syms (:introduced-symbols step)
              shape (:shape step)
              changed? (some (fn [sym]
                               (let [prev (get seen sym)]
                                 (and prev (not (same-coarse-shape? prev shape)))))
                             syms)
              seen' (reduce (fn [acc sym] (assoc acc sym shape)) seen syms)]
          (recur seen' more (if changed? (inc total) total)))))))

(defn- mutable-binding-symbols [steps-with-shapes]
  (reduce
   set/union
   #{}
   (for [step steps-with-shapes
         :when (and (= :binding (:kind step))
                    (mutable-cell-constructors (op-of (:form step))))]
     (:introduced-symbols step))))

(defn- temporal-dependencies [forms mutable-syms]
  (let [mutations (for [form forms
                        :when (and (seq-form? form)
                                   (mutation-ops (op-of form))
                                   (symbol? (second form))
                                   (mutable-syms (second form)))]
                    (second form))
        reads (for [form forms
                    :when (or (and (seq-form? form)
                                   (= 'deref (op-of form))
                                   (symbol? (second form))
                                   (mutable-syms (second form)))
                              (and (symbol? form)
                                   (mutable-syms form)))]
                (if (seq-form? form) (second form) form))]
    (count (set/intersection (set mutations) (set reads)))))

(defn- non-transparent-call-sites [forms]
  (for [form forms
        :when (and (seq-form? form)
                   (symbol? (op-of form))
                   (opaque-op? (op-of form)))]
    form))

(defn- opaque-chain-data [steps]
  (let [pipeline-steps (filter #(= :pipeline-stage (:kind %)) steps)
        opaque-stages (count (filter #(opaque-op? (op-of (:form %))) pipeline-steps))
        stage-ops (set (keep #(some-> % :form op-of) pipeline-steps))]
    {:opaque-stages opaque-stages
     :stage-ops stage-ops}))

(defn- distinct-semantic-jumps [steps]
  (count
   (set
    (keep (fn [step]
            (let [op (op-of (:form step))]
              (when (opaque-op? op) op)))
          steps))))

(defn- step-unresolved-semantics [step]
  (min 2
       (count
        (filter (fn [sym] (opaque-op? sym))
                (:calls step)))))

(defn- live-shape-assumptions [env]
  (count
   (filter (fn [[_ shape]]
             (contains? #{:map :vector :set :seq-like :record/object} (:class shape)))
           env)))

(defn- program-points [steps args]
  (let [initial-env (into {}
                          (for [arg args
                                :when (symbol-binding? arg)]
                            [arg {:class :unknown :nil? false}]))
        mutable-syms (mutable-binding-symbols steps)]
    (loop [env initial-env
           points []
           [step & more] steps]
      (if-not step
        (vec points)
        (let [shape (or (:shape step) (classify-shape (:form step) env))
              env'  (if (= :binding (:kind step))
                      (reduce (fn [acc sym] (assoc acc sym shape))
                              env
                              (:introduced-symbols step))
                      env)
              base-point {:live-bindings (set (keys env'))
                          :active-predicates (:active-predicates step)
                          :mutable-entities (count (set/intersection mutable-syms (set (keys env'))))
                          :shape-assumptions (live-shape-assumptions env')
                          :unresolved-semantics (step-unresolved-semantics step)}
              sample-point (fn [kind extras]
                             (merge base-point
                                    {:kind kind
                                     :step-form (:form step)
                                     :step-kind (:kind step)}
                                    extras))
              points' (-> points
                          (cond-> (and (= :binding (:kind step)) (:group-last? step))
                            (conj (sample-point :binding-group {})))
                          (cond-> (= :pipeline-stage (:kind step))
                            (conj (sample-point :pipeline-stage {})))
                          (cond-> (and (contains? #{:expr :pipeline-stage} (:kind step))
                                       (not (:branch? step)))
                            (conj (sample-point :main-path-step {})))
                          (cond-> (:branch? step)
                            (conj (sample-point :branch-entry {:active-predicates (inc (:active-predicates step))})))
                          (cond-> (#{'cond 'condp 'case} (op-of (:form step)))
                            (conj (sample-point :clause-entry {:active-predicates (inc (:active-predicates step))}))))]
          (recur env' points' more))))))

(defn extract-evidence
  [{:keys [body args var] :as unit}]
  (let [forms               (filter seq-form? (tree-forms body))
        main-steps          (main-path-steps body)
        {:keys [steps*]}    (step-shapes main-steps args)
        flow                (gather-flow `(do ~@body) 0 var)
        branch-regions      (branch-regions body {})
        mutable-syms        (mutable-binding-symbols steps*)
        mutation-sites      (count (for [form forms
                                         :when (and (mutation-ops (op-of form))
                                                    (symbol? (second form))
                                                    (mutable-syms (second form)))]
                                     form))
        external-writes     (+ (count (filter #(effect-write-ops (op-of %)) forms))
                               (count (for [form forms
                                            :when (and (mutation-ops (op-of form))
                                                       (symbol? (second form))
                                                       (not (mutable-syms (second form))))]
                                        form)))
        helper-sites        (non-transparent-call-sites forms)
        {:keys [opaque-stages stage-ops]} (opaque-chain-data steps*)
        helper-count        (count (remove #(stage-ops (op-of %)) helper-sites))
        levels              (mapv step-abstraction-level steps*)
        program-points'     (program-points steps* args)]
    (assoc unit
           :evidence
           {:main-steps steps*
            :branch-regions branch-regions
            :step-levels levels
            :program-points program-points'
            :flow {:branch (:branch flow)
                   :nest (:nest flow)
                   :interrupt (:interrupt flow)
                   :recursion (min 1 (:recursion flow))
                   :logic (:logic flow)
                   :max-depth (:max-depth flow)}
            :state {:mutation-sites mutation-sites
                    :rebindings (meaningful-rebindings steps*)
                    :temporal-dependencies (temporal-dependencies (tree-forms body) mutable-syms)
                    :effect-reads (count (filter #(effect-read-ops (op-of %)) forms))
                    :effect-writes external-writes
                    :mutable-entities (count mutable-syms)}
            :shape {:transitions (main-path-transitions steps*)
                    :destructure (->> steps*
                                      (filter #(= :binding (:kind %)))
                                      (map :destructure-count)
                                      (map #(max 0 (* 0.5 (- % 3))))
                                      (reduce + 0))
                    :variant (reduce + 0 (map #(or (:variant-count %) 0) branch-regions))
                    :nesting (count (filter #(> (data-nesting-depth %) 2) body))
                    :sentinel (sentinel-count forms)}
            :abstraction {:levels levels
                          :distinct-levels (set levels)
                          :incidental (count (filter #(incidental-ops (op-of %)) forms))}
            :dependency {:helpers helper-count
                         :opaque-stages opaque-stages
                         :inversion (count (filter #(inversion-ops (op-of %)) forms))
                         :semantic-jumps (distinct-semantic-jumps steps*)}})))
