(ns gordian.local.units)

(def ^:private defn-ops
  '#{defn defn-})

(defn- strip-doc-and-attr
  [xs]
  (cond-> xs
    (string? (first xs)) rest
    (map? (first xs)) rest))

(defn- fn-arities
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
  [tail]
  (fn-arities (strip-doc-and-attr tail)))

(defn- line-span
  [form]
  (when-let [{:keys [row end-row]} (meta form)]
    (when (and row end-row)
      [row end-row])))

(defn- with-line-metadata
  [unit form]
  (if-let [[start-line end-line] (line-span form)]
    (assoc unit :line start-line :end-line end-line)
    (assoc unit :line nil :end-line nil)))

(defn analyzable-units
  "Extract v1 local-analysis units from one parsed file payload.

   Canonical units:
   - top-level defn/defn- arities
   - top-level defmethod bodies

   Nested helpers are folded into the enclosing unit because extraction only
   emits top-level units."
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
                 (with-line-metadata
                   {:ns ns
                    :var name
                    :kind :defn-arity
                    :arity (count args)
                    :dispatch nil
                    :file file
                    :origin origin
                    :args args
                    :body body
                    :unit-id [ns name :defn-arity i]}
                   form))
               (defn-arities (nnext form))))

            (and (seq? form)
                 (= 'defmethod (first form))
                 (symbol? (second form)))
            [(with-line-metadata
               {:ns ns
                :var (second form)
                :kind :defmethod
                :arity nil
                :dispatch (nth form 2 nil)
                :file file
                :origin origin
                :args (vec (nth form 3 []))
                :body (vec (drop 4 form))
                :unit-id [ns (second form) :defmethod (nth form 2 nil)]}
               form)]

            :else
            [])))
       vec))
