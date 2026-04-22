(ns gordian.local.evidence)

(def ^:private branch-ops
  '#{if if-let if-some when when-not when-let when-some when-first cond condp case})

(def ^:private half-branch-ops
  '#{when when-not when-let when-some when-first})

(def ^:private transparent-ops
  '#{assoc dissoc update merge select-keys conj into vec set mapv
     map filter keep remove keys vals first rest next get contains?
     inc dec + - * / = < > <= >= not some? nil? empty?
     identity keyword symbol str})

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
  '#{assoc dissoc merge update select-keys conj into vec set mapv keys vals hash-map array-map vector})

(def ^:private sentinel-literals
  #{nil false :ok :error :none :some :left :right :success :failure})

(defn- branch-weight [op form]
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
      (count (take-nth 2 clauses)))
    :else 0))

(defn- tree-forms [body]
  (tree-seq coll? seq body))

(defn- seq-form? [x] (seq? x))

(defn- op-of [form]
  (when (seq-form? form)
    (first form)))

(defn- logic-score [form]
  (let [forms (tree-forms [form])]
    (+ (* 0.5 (count (filter #(#{'and 'or} (op-of %)) forms)))
       (* 0.5 (count (filter #(and (seq-form? %) (= 'not (first %)) (seq? (second %))) forms))))))

(defn- destructure-symbol-count [bindings]
  (letfn [(syms [x]
            (cond
              (symbol? x) (if (= '_ x) [] [x])
              (vector? x) (mapcat syms x)
              (map? x) (mapcat syms (concat (keys x) (vals x)))
              (seq? x) (mapcat syms x)
              :else []))]
    (count (syms bindings))))

(defn- shape-transition-count [forms]
  (count (filter #(shape-ops (op-of %)) forms)))

(defn- helper-forms [forms]
  (filter (fn [form]
            (and (seq-form? form)
                 (symbol? (first form))
                 (not (contains? transparent-ops (first form)))
                 (not (contains? branch-ops (first form)))
                 (not (contains? '#{let let* loop loop* do quote var fn fn* recur try catch throw def} (first form)))))
          forms))

(defn- program-points [body args]
  (let [top-forms (vec body)
        arg-count (count (filter symbol? args))]
    (mapv (fn [idx form]
            {:bindings (set (concat (filter symbol? args)
                                    (when (and (seq-form? form) (= 'let (first form)))
                                      (map first (partition 2 2 [] (second form))))))
             :shape-bindings (+ (count (filter #(shape-ops (op-of %)) (tree-forms [form])))
                                (if (and (seq-form? form) (= 'let (first form))) 1 0))
             :mutable-count (count (filter #(or (mutable-cell-constructors (op-of %))
                                                (mutation-ops (op-of %)))
                                           (tree-forms [form])))
             :opaque-count (min 2 (count (helper-forms (tree-forms [form]))))
             :active-predicates (+ (if (branch-ops (op-of form)) 1 0)
                                   (if (> idx 0) 1 0)
                                   arg-count)})
          (range)
          top-forms)))

(defn extract-evidence
  [{:keys [body args] :as unit}]
  (let [forms            (filter seq-form? (tree-forms body))
        branch-forms     (filter #(branch-ops (op-of %)) forms)
        mutation-forms   (filter #(mutation-ops (op-of %)) forms)
        mutable-forms    (filter #(mutable-cell-constructors (op-of %)) forms)
        helper-forms     (helper-forms forms)
        distinct-levels  (cond-> []
                           (seq body) (conj :domain)
                           (seq (filter #(shape-ops (op-of %)) forms)) (conj :data-shaping)
                           (seq (concat mutation-forms
                                        (filter #(effect-read-ops (op-of %)) forms)
                                        (filter #(effect-write-ops (op-of %)) forms)
                                        (filter #(incidental-ops (op-of %)) forms))) (conj :mechanism)
                           (> (count body) 1) (conj :orchestration))
        levels           (vec (distinct distinct-levels))
        effects-read     (count (filter #(effect-read-ops (op-of %)) forms))
        effects-write    (count (filter #(effect-write-ops (op-of %)) forms))
        branch-score     (reduce + 0 (map #(branch-weight (op-of %) %) branch-forms))
        max-depth        (reduce max 0 (map #(count (take-while seq? (rest (tree-seq seq? seq %)))) branch-forms))
        variant-count    (count (filter #(#{'if 'if-let 'if-some 'when 'when-not 'cond 'case 'condp} (op-of %)) branch-forms))
        destructure      (->> forms
                              (filter #(#{'let 'let*} (op-of %)))
                              (map #(destructure-symbol-count (second %)))
                              (map #(max 0 (* 0.5 (- % 3))))
                              (reduce + 0))]
    (assoc unit
           :evidence
           {:main-steps (vec body)
            :step-levels levels
            :program-points (program-points body args)
            :flow {:branch branch-score
                   :nest (max 0 (dec max-depth))
                   :interrupt (count (filter #(#{'throw 'reduced 'recur} (op-of %)) forms))
                   :recursion 0
                   :logic (reduce + 0 (map logic-score branch-forms))
                   :max-depth max-depth}
            :state {:mutation-sites (count mutation-forms)
                    :rebindings (count (filter #(#{'let 'let*} (op-of %)) forms))
                    :temporal-dependencies (if (and (seq mutation-forms)
                                                    (or (pos? effects-read) (pos? effects-write)))
                                             1 0)
                    :effect-reads effects-read
                    :effect-writes effects-write
                    :mutable-entities (count mutable-forms)}
            :shape {:transitions (shape-transition-count forms)
                    :destructure destructure
                    :variant variant-count
                    :nesting (count (filter #(shape-ops (op-of %)) forms))
                    :sentinel (count (filter (fn [form] (some sentinel-literals form)) forms))}
            :abstraction {:levels levels
                          :distinct-levels (set levels)
                          :incidental (count (filter #(incidental-ops (op-of %)) forms))}
            :dependency {:helpers (count helper-forms)
                         :opaque-stages (count helper-forms)
                         :inversion (count (filter #(inversion-ops (op-of %)) forms))
                         :semantic-jumps (count (set (map op-of helper-forms)))}})))
