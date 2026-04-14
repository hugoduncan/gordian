(ns gordian.cluster
  "Group related findings into clusters via union-find.
  All functions are pure — they take data and return data.")

;;; ── union-find ───────────────────────────────────────────────────────────

(defn- make-uf
  "Create a mutable union-find structure (atom of {:parent {} :rank {}})."
  []
  (atom {:parent {} :rank {}}))

(defn- uf-find
  "Find root of x with path compression."
  [uf x]
  (let [{:keys [parent]} @uf
        root (loop [cur x]
               (let [p (get parent cur cur)]
                 (if (= p cur) cur (recur p))))]
    ;; path compression
    (swap! uf update :parent
           (fn [parent]
             (loop [cur x parent parent]
               (let [p (get parent cur cur)]
                 (if (= p root)
                   parent
                   (recur p (assoc parent cur root)))))))
    root))

(defn- uf-union
  "Union two elements by rank."
  [uf a b]
  (let [ra (uf-find uf a)
        rb (uf-find uf b)]
    (when (not= ra rb)
      (let [{:keys [rank]} @uf
            rank-a (get rank ra 0)
            rank-b (get rank rb 0)]
        (cond
          (< rank-a rank-b)
          (swap! uf assoc-in [:parent ra] rb)

          (> rank-a rank-b)
          (swap! uf assoc-in [:parent rb] ra)

          :else
          (do (swap! uf assoc-in [:parent rb] ra)
              (swap! uf update-in [:rank ra] (fnil inc 0))))))))

;;; ── finding → namespaces ─────────────────────────────────────────────────

(defn- finding-namespaces
  "Extract namespace symbols from a finding's :subject."
  [{:keys [subject]}]
  (cond
    (:members subject) (vec (:members subject))
    (and (:ns-a subject) (:ns-b subject)) [(:ns-a subject) (:ns-b subject)]
    (:ns subject) [(:ns subject)]
    :else []))

;;; ── clustering ───────────────────────────────────────────────────────────

(defn- severity-rank [s]
  (case s :high 0 :medium 1 :low 2 3))

(defn cluster-findings
  "Group related findings into clusters.
  Findings sharing namespace mentions are connected.
  Returns {:clusters [cluster] :unclustered [finding]}
  where cluster is {:namespaces #{sym} :findings [finding]
                    :max-severity keyword :summary string}.
  Only clusters with ≥ 2 findings are emitted; singletons go to :unclustered."
  [findings]
  (if (empty? findings)
    {:clusters [] :unclustered []}
    (let [uf (make-uf)
          ;; extract ns sets per finding
          finding-nss (mapv finding-namespaces findings)
          ;; union namespaces within each finding
          _ (doseq [nss finding-nss]
              (when (> (count nss) 1)
                (doseq [n (rest nss)]
                  (uf-union uf (first nss) n))))
          ;; also union across findings that share a namespace
          ;; for each ns that appears in multiple findings, union their groups
          _ (let [ns-occurrences (reduce (fn [acc [i nss]]
                                           (reduce (fn [a n] (update a n (fnil conj []) i))
                                                   acc nss))
                                         {}
                                         (map-indexed vector finding-nss))]
              (doseq [[_ns idxs] ns-occurrences
                      :when (> (count idxs) 1)]
                (let [first-nss (mapv #(first (nth finding-nss %)) idxs)]
                  (doseq [n (rest first-nss)]
                    (uf-union uf (first first-nss) n)))))
          ;; group findings by root
          groups (group-by (fn [i]
                             (let [nss (nth finding-nss i)]
                               (if (seq nss)
                                 (uf-find uf (first nss))
                                 ::singleton)))
                           (range (count findings)))
          ;; separate singletons from groups
          singleton-idxs (get groups ::singleton [])
          real-groups (dissoc groups ::singleton)
          ;; build clusters
          clusters (->> real-groups
                        vals
                        (map (fn [idxs]
                               (let [fs (mapv #(nth findings %) idxs)
                                     nss (into #{} (mapcat finding-namespaces) fs)
                                     max-sev (first (sort-by severity-rank
                                                             (map :severity fs)))]
                                 {:namespaces   nss
                                  :findings     fs
                                  :max-severity max-sev
                                  :summary      (str (count fs) " finding"
                                                     (when (not= 1 (count fs)) "s")
                                                     " across "
                                                     (count nss) " namespace"
                                                     (when (not= 1 (count nss)) "s"))})))
                        ;; split into real clusters (≥2 findings) and unclustered
                        (group-by #(if (>= (count (:findings %)) 2)
                                     :clustered :unclustered)))
          clustered    (get clusters :clustered [])
          uncl-from-groups (mapcat :findings (get clusters :unclustered []))
          uncl-singletons  (mapv #(nth findings %) singleton-idxs)
          all-unclustered  (into (vec uncl-from-groups) uncl-singletons)]
      {:clusters    (vec (sort-by (fn [c] [(severity-rank (:max-severity c))
                                           (- (count (:findings c)))])
                                  clustered))
       :unclustered all-unclustered})))
