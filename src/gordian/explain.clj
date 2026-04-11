(ns gordian.explain
  "Drill-down query functions for namespace and pair investigation.
  All functions are pure — they query report data structures."
  (:require [gordian.diagnose :as diagnose]))

;;; ── graph queries ────────────────────────────────────────────────────────

(defn shortest-path
  "BFS with parent tracking from `from` to `to` over directed graph.
  Returns [from ... to] or nil if no path exists.
  Only traverses project-internal edges (keys of graph).
  Returns nil for self-paths (from = to)."
  [graph from to]
  (when (and (not= from to) (contains? graph from) (contains? graph to))
    (let [project-nss (set (keys graph))]
      (loop [queue (into clojure.lang.PersistentQueue/EMPTY
                         (filter project-nss (get graph from #{})))
             visited #{from}
             parents (into {} (map (fn [n] [n from]))
                           (filter project-nss (get graph from #{})))]
        (when (seq queue)
          (let [node (peek queue)]
            (if (= node to)
              ;; reconstruct path
              (loop [path (list to) cur to]
                (let [p (get parents cur)]
                  (if (= p from)
                    (vec (cons from path))
                    (recur (cons p path) p))))
              ;; continue BFS
              (let [new-visited (conj visited node)
                    neighbors   (remove new-visited
                                        (filter project-nss (get graph node #{})))]
                (recur (into (pop queue) neighbors)
                       new-visited
                       (into parents (map (fn [n] [n node]) neighbors)))))))))))

(defn direct-deps
  "Direct dependencies of ns-sym, split into project and external.
  project-nss — set of all project namespace symbols (keys of graph)."
  [graph project-nss ns-sym]
  (let [deps (get graph ns-sym #{})]
    {:project  (vec (sort-by str (filter project-nss deps)))
     :external (vec (sort-by str (remove project-nss deps)))}))

(defn direct-dependents
  "Project namespaces that directly require ns-sym."
  [graph ns-sym]
  (vec (sort-by str
                (keep (fn [[k deps]] (when (contains? deps ns-sym) k))
                      graph))))

(defn ns-pairs
  "Filter pairs to those involving ns-sym (as :ns-a or :ns-b)."
  [pairs ns-sym]
  (filterv (fn [p] (or (= ns-sym (:ns-a p))
                       (= ns-sym (:ns-b p))))
           pairs))

;;; ── verdict ──────────────────────────────────────────────────────────────

(defn verdict
  "Derive an opinionated interpretation from explain-pair signals.
  Returns {:category keyword :explanation string}.
  Rules evaluated top-down, first match wins.
  structural — {:direct-edge? bool :shortest-path vec-or-nil}
  conceptual — conceptual pair map or nil (with family annotation)
  change     — change pair map or nil"
  [structural conceptual change]
  (let [direct?      (:direct-edge? structural)
        path?        (some? (:shortest-path structural))
        concept?     (some? conceptual)
        change?      (some? change)
        same-fam?    (:same-family? conceptual)
        independent? (seq (:independent-terms conceptual))]
    (cond
      direct?
      {:category    :expected-structural
       :explanation "Expected — direct structural dependency."}

      (and concept? same-fam? (not independent?))
      {:category    :family-naming-noise
       :explanation "Likely naming similarity — shared terms are from namespace prefix."}

      (and concept? same-fam? independent?)
      {:category    :family-siblings
       :explanation "Family siblings with shared domain vocabulary — consider whether a shared abstraction is warranted."}

      (and concept? change? (not same-fam?))
      {:category    :likely-missing-abstraction
       :explanation "Likely missing abstraction — hidden in both conceptual and change lenses."}

      (and concept? (not change?) (not same-fam?))
      {:category    :hidden-conceptual
       :explanation "Hidden conceptual coupling — shared vocabulary with no structural link."}

      (and (not concept?) change?)
      {:category    :hidden-change
       :explanation "Hidden change coupling — co-changing without structural or conceptual link. Possible vestigial dependency."}

      path?
      {:category    :transitive-only
       :explanation "Transitively connected only — no direct coupling signal."}

      :else
      {:category    :unrelated
       :explanation "No coupling detected."})))

;;; ── composite explain functions ─────────────────────────────────────────

(defn explain-ns
  "Everything gordian knows about a single namespace.
  Returns structured map, or map with :error key if ns not found."
  [{:keys [graph nodes conceptual-pairs change-pairs cycles]} ns-sym]
  (let [project-nss (set (keys graph))]
    (if-not (contains? project-nss ns-sym)
      {:error (str "namespace '" ns-sym "' not found in project")
       :available (vec (sort-by str project-nss))}
      (let [node (first (filter #(= ns-sym (:ns %)) nodes))]
        {:ns                ns-sym
         :metrics           (select-keys node [:reach :fan-in :ca :ce :instability :role
                                               :family :ca-family :ca-external
                                               :ce-family :ce-external])
         :direct-deps       (direct-deps graph project-nss ns-sym)
         :direct-dependents (direct-dependents graph ns-sym)
         :conceptual-pairs  (ns-pairs (or conceptual-pairs []) ns-sym)
         :change-pairs      (ns-pairs (or change-pairs []) ns-sym)
         :cycles            (filterv #(contains? % ns-sym) (or cycles []))}))))

(defn explain-pair-data
  "Everything gordian knows about a pair of namespaces.
  Returns structured map, or map with :error key if either ns not found."
  [{:keys [graph conceptual-pairs change-pairs]} ns-a ns-b]
  (let [project-nss (set (keys graph))
        missing     (remove project-nss [ns-a ns-b])]
    (if (seq missing)
      {:error (str "namespace(s) not found: " (pr-str (vec missing)))
       :available (vec (sort-by str project-nss))}
      (let [pk          #{ns-a ns-b}
            edge-a->b   (contains? (get graph ns-a #{}) ns-b)
            edge-b->a   (contains? (get graph ns-b #{}) ns-a)
            direct-edge (or edge-a->b edge-b->a)
            direction   (cond edge-a->b :a->b edge-b->a :b->a :else nil)
            path-ab     (shortest-path graph ns-a ns-b)
            path-ba     (shortest-path graph ns-b ns-a)
            path        (or path-ab path-ba)
            c-pair      (first (filter #(= pk (diagnose/pair-key %))
                                       (or conceptual-pairs [])))
            x-pair      (first (filter #(= pk (diagnose/pair-key %))
                                       (or change-pairs [])))
            ;; derive finding if hidden
            finding     (when (and (or c-pair x-pair)
                                   (not direct-edge))
                          (cond
                            (and c-pair x-pair)
                            (first (:findings
                                    (diagnose/find-cross-lens-hidden
                                     [c-pair] [x-pair])))
                            c-pair
                            (first (diagnose/find-hidden-conceptual
                                    [c-pair] #{}))
                            x-pair
                            (first (diagnose/find-hidden-change
                                    [x-pair] #{}))))
            structural-data {:direct-edge?  direct-edge
                             :direction     direction
                             :shortest-path path}]
        {:ns-a       ns-a
         :ns-b       ns-b
         :structural structural-data
         :conceptual c-pair
         :change     x-pair
         :finding    finding
         :verdict    (verdict structural-data c-pair x-pair)}))))
