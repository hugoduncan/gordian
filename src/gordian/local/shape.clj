(ns gordian.local.shape
  (:require [gordian.local.common :as common]))

(defn classify-shape [form env]
  (cond
    (nil? form) {:class :nil :nil? true}
    (common/scalar-literal? form) {:class :scalar :nil? false}
    (symbol? form) (or (get env form) {:class :unknown :nil? false})
    (map? form) {:class :map :nil? false :keyset (set (filter keyword? (keys form)))}
    (vector? form) {:class :vector :nil? false}
    (set? form) {:class :set :nil? false}
    (and (common/seq-form? form) (= 'throw (common/op-of form))) {:class :throws}
    (common/seq-form? form)
    (let [op (common/op-of form)]
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
                       select-keys (or (common/literal-keyword-set (nth form 2 nil)) base-keyset)
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

        (common/threading-ops op)
        (classify-shape (second form) env)

        :else
        {:class :unknown :nil? false}))
    :else {:class :unknown :nil? false}))

(defn same-coarse-shape? [a b]
  (and (= (:class a) (:class b))
       (= (boolean (:nil? a)) (boolean (:nil? b)))
       (or (not= :map (:class a))
           (= (:keyset a) (:keyset b)))))

(defn variant-outcome? [outcomes]
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

(defn branch-outcomes [form env]
  (let [op (common/op-of form)]
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

(defn branch-variant-count [form env]
  (let [op (common/op-of form)]
    (cond
      (common/if-like-ops op)
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

(defn branch-regions [forms env]
  (letfn [(collect [form env]
            (cond
              (not (common/seq-form? form))
              []

              (#{'let 'let* 'loop 'loop*} (common/op-of form))
              (let [bindings (partition 2 (second form))
                    env' (reduce (fn [acc [binding expr]]
                                   (reduce (fn [acc2 sym]
                                             (assoc acc2 sym (classify-shape expr acc2)))
                                           acc
                                           (common/binding-symbols binding)))
                                 env
                                 bindings)]
                (mapcat #(collect % env') (drop 2 form)))

              :else
              (let [children (mapcat #(collect % env) (rest form))]
                (if (common/branch-ops (common/op-of form))
                  (let [outcomes (branch-outcomes form env)
                        variant-count (branch-variant-count form env)]
                    (conj (vec children)
                          {:form form
                           :outcomes outcomes
                           :variant? (pos? variant-count)
                           :variant-count variant-count}))
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

(defn step-shapes [steps args]
  (let [initial-env (into {}
                          (for [arg args
                                :when (common/symbol-binding? arg)]
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

(defn main-path-transitions [steps-with-shapes]
  (let [shapes (map :shape steps-with-shapes)]
    (count (filter true? (map shape-transition? shapes (rest shapes))))))

(defn data-nesting-depth [form]
  (cond
    (map? form) (inc (reduce max 0 (map data-nesting-depth (concat (keys form) (vals form)))))
    (vector? form) (inc (reduce max 0 (map data-nesting-depth form)))
    (set? form) (inc (reduce max 0 (map data-nesting-depth form)))
    (and (common/seq-form? form) (common/shape-ops (common/op-of form)))
    (inc (reduce max 0 (map data-nesting-depth (rest form))))
    :else 0))

(defn sentinel-count [forms]
  (count
   (filter true?
           (for [form forms
                 :when (common/seq-form? form)
                 :let [op (common/op-of form)]
                 :when (or (common/branch-ops op) (= 'case op) (= '= op))]
             (boolean (some common/sentinel-literals form))))))
