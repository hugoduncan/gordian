(ns gordian.output.common
  (:require [clojure.string :as str]
            [gordian.finding :as finding]))

(defn pct [x]
  (format "%5.1f%%" (* 100.0 x)))

(defn md-pct [x]
  (format "%.1f%%" (* 100.0 x)))

(defn pad-right [n s]
  (format (str "%-" n "s") s))

(defn pad-left [n s]
  (format (str "%" n "s") s))

(defn bar [n]
  (apply str (repeat (max 0 (min 50 (int n))) "█")))

(defn rule [n]
  (apply str (repeat n "-")))

(defn unit-summary-counts
  "Return canonical summary counts for unit-oriented reports that expose a single unit population."
  [project-rollup units]
  {:namespace-count (or (:namespace-count project-rollup)
                        (count (set (map :ns units))))
   :unit-count      (or (:unit-count project-rollup)
                        (count units))})

(defn local-display-summary-counts
  "Return both displayed and analyzed summary counts for local reports."
  [{:keys [project-rollup canonical-summary units]}]
  {:displayed-namespace-count (count (set (map :ns units)))
   :displayed-unit-count      (count units)
   :analyzed-namespace-count  (or (:namespace-count project-rollup)
                                  (count (:namespaces canonical-summary))
                                  (count (set (map :ns units))))
   :analyzed-unit-count       (or (:unit-count project-rollup)
                                  (:unit-count canonical-summary)
                                  (count units))})

(defn format-finding-subject [{:keys [category subject]}]
  (case category
    :cycle          (str/join " → " (sort (map str (:members subject))))
    (:cross-lens-hidden :hidden-conceptual :hidden-change :vestigial-edge)
    (str (:ns-a subject) " ↔ " (:ns-b subject))
    (or (some-> (:ns subject) str)
        (some-> (:suite subject) name)
        "")))

(defn severity-marker [severity]
  (case severity
    :high   "● HIGH"
    :medium "● MEDIUM"
    :low    "● LOW"))

(defn format-evidence-lines [{:keys [category evidence]}]
  (case category
    :cross-lens-hidden
    [(str "  conceptual score=" (format "%.2f" (:conceptual-score evidence))
          " — shared terms: " (str/join ", " (:shared-terms evidence)))
     (str "  change     score=" (format "%.2f" (:change-score evidence))
          " — " (:co-changes evidence) " co-changes")
     "  → no structural edge"]

    :hidden-conceptual
    (if (and (:same-family? evidence)
             (seq (:family-terms evidence)))
      (cond-> [(str "  shared terms: " (str/join ", " (:shared-terms evidence)))]
        (seq (:family-terms evidence))
        (conj (str "  family terms: " (str/join ", " (:family-terms evidence))))
        (seq (:independent-terms evidence))
        (conj (str "  independent: " (str/join ", " (:independent-terms evidence))))
        true
        (conj "  → no structural edge"))
      [(str "  shared terms: " (str/join ", " (:shared-terms evidence)))
       "  → no structural edge"])

    :hidden-change
    [(str "  " (:co-changes evidence) " co-changes"
          ", conf=" (format "%.0f%%/%.0f%%"
                            (* 100.0 (:confidence-a evidence))
                            (* 100.0 (:confidence-b evidence))))
     "  → no structural edge"]

    :cycle
    [(str "  members: " (str/join ", " (sort (map str (:members evidence)))))]

    :sdp-violation
    [(str "  Ca=" (:ca evidence) " Ce=" (:ce evidence)
          " I=" (format "%.2f" (:instability evidence)))]

    :god-module
    [(str "  reach=" (format "%.1f%%" (* 100.0 (:reach evidence)))
          " fan-in=" (format "%.1f%%" (* 100.0 (:fan-in evidence))))]

    :facade
    [(str "  Ca-ext=" (:ca-external evidence)
          " Ce-ext=" (:ce-external evidence)
          " Ce-fam=" (:ce-family evidence)
          " family=" (:family evidence))]

    :hub
    [(str "  Ce=" (:ce evidence)
          " I=" (format "%.2f" (or (:instability evidence) 0.0))
          " role=" (name (or (:role evidence) :unknown)))]

    []))

(defn format-finding-lines
  "Format a single finding as lines (reusable for both flat and clustered)."
  [f]
  (concat
   [(str (severity-marker (:severity f))
         "  " (format-finding-subject f)
         (when-let [a (:actionability-score f)]
           (str "  [act=" (format "%.1f" a) "]")))
    (str "  " (:reason f))]
   (format-evidence-lines f)
   (when-let [disp (some-> (:action f) finding/action-display)]
     [(str "  → " disp)])
   (when-let [ns (:next-step f)]
     [(str "  $ " ns)])
   [""]))

(defn indent-finding-lines
  "Indent every non-blank line from format-finding-lines by prefix."
  [prefix f]
  (map #(if (str/blank? %) "" (str prefix %)) (format-finding-lines f)))

(defn format-cluster-section
  "Format clusters as lines. Returns nil when no clusters."
  [clusters]
  (when (seq clusters)
    (concat
     ["" "CLUSTERS" ""]
     (mapcat (fn [{:keys [namespaces findings max-severity summary]}]
               (concat
                [(str (severity-marker max-severity)
                      "  cluster: " (str/join ", " (sort-by str namespaces)))
                 (str "  " summary)
                 ""]
                (mapcat (partial indent-finding-lines "    ") findings)
                [""]))
             clusters))))

(defn md-severity-heading [severity]
  (case severity
    :high   "## 🔴 HIGH"
    :medium "## 🟡 MEDIUM"
    :low    "## 🟢 LOW"))

(defn md-evidence-lines [{:keys [category evidence]}]
  (case category
    :cross-lens-hidden
    [(str "- **Conceptual** score=" (format "%.2f" (:conceptual-score evidence))
          " — shared terms: " (str/join ", " (:shared-terms evidence)))
     (str "- **Change** score=" (format "%.2f" (:change-score evidence))
          " — " (:co-changes evidence) " co-changes")
     "- → No structural edge"]

    :hidden-conceptual
    (if (and (:same-family? evidence)
             (seq (:family-terms evidence)))
      (cond-> [(str "- Shared terms: " (str/join ", " (:shared-terms evidence)))]
        (seq (:family-terms evidence))
        (conj (str "- Family terms: " (str/join ", " (:family-terms evidence))))
        (seq (:independent-terms evidence))
        (conj (str "- Independent: " (str/join ", " (:independent-terms evidence))))
        true
        (conj "- → No structural edge"))
      [(str "- Shared terms: " (str/join ", " (:shared-terms evidence)))
       "- → No structural edge"])

    :hidden-change
    [(str "- " (:co-changes evidence) " co-changes"
          ", conf=" (format "%.0f%%/%.0f%%"
                            (* 100.0 (:confidence-a evidence))
                            (* 100.0 (:confidence-b evidence))))
     "- → No structural edge"]

    :cycle
    [(str "- Members: " (str/join ", " (sort (map str (:members evidence)))))]

    :sdp-violation
    [(str "- Ca=" (:ca evidence) " Ce=" (:ce evidence)
          " I=" (format "%.2f" (:instability evidence)))]

    :god-module
    [(str "- Reach=" (md-pct (:reach evidence))
          " Fan-in=" (md-pct (:fan-in evidence)))]

    :facade
    [(str "- Ca-ext=" (:ca-external evidence)
          " Ce-ext=" (:ce-external evidence)
          " Ce-fam=" (:ce-family evidence)
          " family=`" (:family evidence) "`")]

    :hub
    [(str "- Ce=" (:ce evidence)
          " I=" (format "%.2f" (or (:instability evidence) 0.0))
          " role=" (name (or (:role evidence) :unknown)))]

    []))

(defn md-format-finding [f]
  (concat
   [(str "### " (format-finding-subject f)
         (when-let [a (:actionability-score f)]
           (str " [act=" (format "%.1f" a) "]")))
    ""
    (:reason f)
    ""]
   (md-evidence-lines f)
   (when-let [disp (some-> (:action f) finding/action-display)]
     [(str "**→** " disp) ""])
   (when-let [ns (:next-step f)]
     [(str "`$ " ns "`") ""])
   [""]))

(defn md-format-cluster-section [clusters]
  (when (seq clusters)
    (concat
     ["## Clusters" ""]
     (mapcat (fn [{:keys [namespaces findings max-severity summary]}]
               (concat
                [(str "### " (case max-severity :high "🔴" :medium "🟡" :low "🟢" "")
                      " Cluster: " (str/join ", " (map #(str "`" % "`")
                                                       (sort-by str namespaces))))
                 ""
                 (str "*" summary "*")
                 ""]
                (mapcat md-format-finding findings)))
             clusters))))

(defn arrow [delta]
  (cond (pos? delta) "↑" (neg? delta) "↓" :else "→"))

(defn delta-str [v fmt]
  (let [s (format fmt v)]
    (cond (pos? v) (str "+" s)
          :else    s)))
