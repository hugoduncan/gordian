(ns gordian.dot
  (:require [clojure.string :as str]))

;;; Graphviz DOT output for the namespace dependency graph.
;;; Nodes are coloured by core/periphery role.

(def ^:private role-color
  {:core       "#a8d8a8"   ; light green  — stable foundation
   :peripheral "#ffb3b3"   ; light red    — leaf / entry point
   :shared     "#ffe0b2"   ; light orange — coupled both ways
   :isolated   "#e0e0e0"   ; light gray   — standalone
   nil         "#ffffff"}) ; white        — no role computed

(defn- quote-id [s] (str "\"" s "\""))

(defn- node-attrs
  "DOT attribute string for a node map."
  [{:keys [ns role instability]}]
  (let [color (get role-color role "#ffffff")
        label (cond-> (str ns)
                instability (str "\\nI=" (format "%.2f" instability))
                role        (str " (" (name role) ")"))]
    (str "fillcolor=" (quote-id color) " label=" (quote-id label))))

(defn- node-lines
  "One DOT node declaration per namespace."
  [nodes]
  (map (fn [n] (str "  " (quote-id (str (:ns n)))
                    " [" (node-attrs n) "];"))
       nodes))

(defn- edge-lines
  "One DOT edge per direct dependency, project-internal only."
  [graph project-ns-set]
  (for [[src deps] (sort-by (comp str first) graph)
        dep        (sort (map str (filter project-ns-set deps)))]
    (str "  " (quote-id (str src)) " -> " (quote-id dep) ";")))

(defn generate
  "Produce a Graphviz DOT string from a report map.
  `report` — {:graph {ns→#{deps}} :nodes [{:ns :role :instability …}] :src-dir …}"
  [{:keys [graph nodes src-dir]}]
  (let [project-nodes (set (keys graph))
        header        [(str "// gordian — " src-dir)
                       "digraph gordian {"
                       "  rankdir=LR;"
                       "  node [shape=box fontname=\"monospace\" style=filled];"
                       ""]
        node-section  (node-lines nodes)
        edge-section  (cons "" (edge-lines graph project-nodes))
        footer        ["}"]
        all-lines     (concat header node-section edge-section footer)]
    (str/join "\n" all-lines)))
