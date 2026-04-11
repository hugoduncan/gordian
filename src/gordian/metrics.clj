(ns gordian.metrics)

;;; Robert Martin (1994) coupling metrics, adapted for Clojure namespaces.
;;;
;;; We count only project-internal edges (deps that are keys in graph) so
;;; that external libraries don't inflate Ce and distort instability scores.
;;;
;;; Ca  afferent coupling  — # project nodes that directly require this ns
;;; Ce  efferent coupling  — # project nodes this ns directly requires
;;; I   instability        — Ce / (Ca + Ce)  ∈ [0, 1]
;;;                          0 = maximally stable   (depended on, depends on nothing)
;;;                          1 = maximally unstable (depends on many, nothing depends on it)

(defn- project-deps
  "Direct deps of `node` that are themselves project nodes (keys of graph)."
  [graph node]
  (let [project-nodes (set (keys graph))]
    (filter project-nodes (get graph node #{}))))

(defn ce
  "Efferent coupling of `node`: # project namespaces it directly requires."
  [graph node]
  (count (project-deps graph node)))

(defn ca
  "Afferent coupling of `node`: # project namespaces that directly require it."
  [graph node]
  (count (filter (fn [[_ deps]] (contains? deps node)) graph)))

(defn instability
  "I = Ce / (Ca + Ce).  Returns 0.0 when both are zero (isolated node)."
  [ca-val ce-val]
  (let [total (+ ca-val ce-val)]
    (if (zero? total) 0.0 (/ (double ce-val) total))))

(defn compute
  "Compute Ca, Ce, instability for every node in `graph`.
  Returns {ns-sym → {:ca int :ce int :instability double}}."
  [graph]
  (into {}
        (map (fn [node]
               (let [ca-v (ca graph node)
                     ce-v (ce graph node)]
                 [node {:ca          ca-v
                        :ce          ce-v
                        :instability (instability ca-v ce-v)}]))
             (keys graph))))
