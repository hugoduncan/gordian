(ns gordian.output
  (:require [clojure.string :as str]
            [gordian.finding :as finding]))

;;; ── formatting helpers ───────────────────────────────────────────────────

(defn- pct [x]      (format "%5.1f%%" (* 100.0 x)))
(defn- pad-right [n s] (format (str "%-" n "s") s))
(defn- pad-left  [n s] (format (str "%" n "s")  s))

(defn column-widths
  "Return {:ns-col int} sized to fit the longest namespace name."
  [nodes]
  {:ns-col (max 20 (apply max 0 (map #(count (str (:ns %))) nodes)))})

(defn format-row
  "Format one node row as a string."
  [ns-col {:keys [ns reach fan-in ca ce instability role]}]
  (str (pad-right ns-col (str ns))
       "  " (pct reach)
       "  " (pct fan-in)
       "  " (pad-left 3 (str (or ce "-")))
       "  " (pad-left 3 (str (or ca "-")))
       "  " (if instability (format "%4.2f" instability) "   -")
       "  " (if role (name role) "")))

(defn- format-cycles
  "Return lines describing detected cycles.
  Returns nil when there are no cycles — the section is omitted entirely."
  [cycles]
  (when (seq cycles)
    (into ["cycles:"]
          (map-indexed (fn [i members]
                         (str "  [" (inc i) "] "
                              (str/join " → " (sort (map str members)))
                              "  (" (count members) " namespaces)"))
                       cycles))))

;;; ── conceptual coupling section ──────────────────────────────────────────

(defn- conceptual-ns-col
  "Column width fitting the longest namespace name across all pairs."
  [pairs]
  (apply max 20
         (mapcat (fn [{:keys [ns-a ns-b]}]
                   [(count (str ns-a)) (count (str ns-b))])
                 pairs)))

(defn format-conceptual
  "Return a vector of lines for the conceptual coupling section.
  Returns [] when pairs is empty — the section is omitted entirely.
  Shared terms shown for all pairs: ← flags no-structural-edge rows (the
  discovery rows); terms on structural rows show what the coupling is about."
  [pairs threshold]
  (if (empty? pairs)
    []
    (let [ns-col (conceptual-ns-col pairs)
          header (str (pad-right ns-col "namespace-a")
                      "  " (pad-right ns-col "namespace-b")
                      "  score  structural  shared concepts")
          rule   (apply str (repeat (count header) "─"))]
      (into
       [(str "conceptual coupling (score ≥ " (format "%.2f" (double threshold)) "):")
        ""
        header
        rule]
       (map (fn [{:keys [ns-a ns-b score structural-edge? shared-terms]}]
              (str (pad-right ns-col (str ns-a))
                   "  " (pad-right ns-col (str ns-b))
                   "  " (format "%4.2f" (double score))
                   "  " (if structural-edge?
                          (str "yes      " (str/join " " shared-terms))
                          (str "no  ←    " (str/join " " shared-terms)))))
            pairs)))))

;;; ── change coupling section ─────────────────────────────────────────────

(defn- change-ns-col
  "Column width fitting the longest namespace name across all pairs."
  [pairs]
  (apply max 20
         (mapcat (fn [{:keys [ns-a ns-b]}]
                   [(count (str ns-a)) (count (str ns-b))])
                 pairs)))

(defn format-change-coupling
  "Return a vector of lines for the change coupling section.
  Returns [] when pairs is empty — section omitted entirely.
  Columns: namespace-a  namespace-b  Jaccard  co  conf-a  conf-b  structural
  ← flags non-structural-edge rows (the discovery signal)."
  [pairs threshold]
  (if (empty? pairs)
    []
    (let [ns-col (change-ns-col pairs)
          header (str (pad-right ns-col "namespace-a")
                      "  " (pad-right ns-col "namespace-b")
                      "  Jaccard  co  conf-a  conf-b  structural")
          rule   (apply str (repeat (count header) "─"))]
      (into
       [(str "change coupling (Jaccard ≥ " (format "%.2f" (double threshold)) "):")
        ""
        header
        rule]
       (map (fn [{:keys [ns-a ns-b score co-changes
                         confidence-a confidence-b structural-edge?]}]
              (str (pad-right ns-col (str ns-a))
                   "  " (pad-right ns-col (str ns-b))
                   "  " (format "%6.4f" (double score))
                   "  " (pad-left 2 (str co-changes))
                   "  " (format "%5.1f%%" (* 100.0 confidence-a))
                   "  " (format "%5.1f%%" (* 100.0 confidence-b))
                   "  " (if structural-edge?
                          "yes"
                          "no  ←")))
            pairs)))))

;;; ── full report ──────────────────────────────────────────────────────────

(defn format-report
  "Return a vector of lines for the full coupling report.
  `report` — {:src-dirs :propagation-cost :cycles :nodes [node-maps]}.
  Cycles section omitted when none present.
  Conceptual section appended when :conceptual-pairs is present.
  Change coupling section appended when :change-pairs is present."
  [{:keys [src-dirs propagation-cost cycles nodes
           conceptual-pairs conceptual-threshold
           change-pairs change-threshold]}]
  (let [{:keys [ns-col]} (column-widths nodes)
        header (str (pad-right ns-col "namespace")
                    "    reach   fan-in   Ce   Ca      I  role")
        rule   (apply str (repeat (count header) "─"))
        cycle-lines (format-cycles cycles)
        middle (if cycle-lines
                 (concat [""] cycle-lines ["" header rule])
                 ["" header rule])
        conceptual-lines (when (seq conceptual-pairs)
                           (into [""] (format-conceptual conceptual-pairs
                                                         conceptual-threshold)))
        change-lines (when (seq change-pairs)
                       (into [""] (format-change-coupling change-pairs
                                                          change-threshold)))]
    (into
     (into
      ["gordian — namespace coupling report"
       (str "src: " (str/join " " src-dirs))
       ""
       (str "propagation cost: " (format "%.4f" propagation-cost)
            "  (on average " (format "%.1f" (* 100.0 propagation-cost))
            "% of project reachable per change)")]
      middle)
     (concat
      (map (partial format-row ns-col) nodes)
      [""]
      conceptual-lines
      change-lines))))

;;; ── diagnose output ──────────────────────────────────────────────────────

(defn- format-finding-subject [{:keys [category subject]}]
  (case category
    :cycle          (str/join " → " (sort (map str (:members subject))))
    (:cross-lens-hidden :hidden-conceptual :hidden-change :vestigial-edge)
    (str (:ns-a subject) " ↔ " (:ns-b subject))
    (or (some-> (:ns subject) str)
        (some-> (:suite subject) name)
        "")))

(defn- severity-marker [severity]
  (case severity
    :high   "● HIGH"
    :medium "● MEDIUM"
    :low    "● LOW"))

(defn- format-evidence-lines [{:keys [category evidence]}]
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

(defn- format-finding-lines
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

(defn- indent-finding-lines
  "Indent every non-blank line from format-finding-lines by prefix."
  [prefix f]
  (map #(if (str/blank? %) "" (str prefix %)) (format-finding-lines f)))

(defn- format-cluster-section
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

(defn format-diagnose
  "Format findings as human-readable lines.
  health           — map from diagnose/health.
  findings         — sorted vec of finding maps.
  clusters-data    — optional {:clusters [...] :unclustered [...]} from cluster/cluster-findings.
  rank             — :severity or :actionability.
  suppressed-count — optional count of noise findings suppressed from display."
  ([report health findings]
   (format-diagnose report health findings nil :severity nil))
  ([report health findings clusters-data]
   (format-diagnose report health findings clusters-data :severity nil))
  ([report health findings clusters-data rank]
   (format-diagnose report health findings clusters-data rank nil nil))
  ([report health findings clusters-data rank suppressed-count]
   (format-diagnose report health findings clusters-data rank suppressed-count nil))
  ([{:keys [src-dirs]} health findings clusters-data rank suppressed-count truncated-from]
   (let [count-sev (fn [s] (count (filter #(= s (:severity %)) findings)))
         n-high    (count-sev :high)
         n-medium  (count-sev :medium)
         n-low     (count-sev :low)
         n-total   (count findings)
         has-clusters? (seq (:clusters clusters-data))
         summary   (str (if truncated-from
                          (str "top " n-total " of " truncated-from " findings")
                          (str n-total " finding" (when (not= 1 n-total) "s")))
                        " (" n-high " high, " n-medium " medium, " n-low " low)"
                        (when (pos? (or suppressed-count 0))
                          (str " — " suppressed-count
                               " noise suppressed (--show-noise to include)")))]
     (into
      [(str "gordian diagnose — "
            (if truncated-from
              (str "top " n-total " of " truncated-from " findings")
              (str n-total " finding" (when (not= 1 n-total) "s"))))
       (str "src: " (str/join " " src-dirs))
       (str "rank: " (name rank))
       ""
       "HEALTH"
       (str "  propagation cost: "
            (format "%.1f%%" (* 100.0 (:propagation-cost health)))
            " (" (name (:health health)) ")")
       (str "  cycles: " (if (zero? (:cycle-count health))
                           "none"
                           (:cycle-count health)))
       (str "  namespaces: " (:ns-count health))
       ""]
      (concat
       ;; When clusters exist, show clusters first, then unclustered
       (if has-clusters?
         (concat
          (format-cluster-section (:clusters clusters-data))
          (when (seq (:unclustered clusters-data))
            (concat
             ["UNCLUSTERED" ""]
             (mapcat format-finding-lines (:unclustered clusters-data)))))
         ;; No clustering: flat list as before
         (mapcat format-finding-lines findings))
       [summary])))))

;;; ── explain output ───────────────────────────────────────────────────────

(defn- format-pair-line
  "Format one coupling pair as a line for explain-ns output.
  context-ns — the namespace being explained; we show the other side."
  [ns-col context-ns pair]
  (let [other (if (= context-ns (:ns-a pair)) (:ns-b pair) (:ns-a pair))]
    (str "  " (pad-right ns-col (str other))
         "  score=" (format "%.2f" (:score pair))
         "  " (if (:structural-edge? pair) "structural" "hidden")
         (when (seq (:shared-terms pair))
           (str "  shared: " (str/join ", " (:shared-terms pair))))
         (when (:co-changes pair)
           (str "  " (:co-changes pair) " co-changes")))))

(defn format-explain-ns
  "Format explain-ns data as human-readable lines."
  [{:keys [ns metrics direct-deps direct-dependents
           conceptual-pairs change-pairs cycles error available]}]
  (if error
    [(str "Error: " error)
     (str "Available namespaces: " (str/join ", " (map str available)))]
    (let [ns-col (max 20 (apply max 0
                                (map #(count (str (or (:ns-a %) (:ns-b %))))
                                     (concat conceptual-pairs change-pairs))))]
      (into
       [(str "gordian explain — " ns)
        ""
        (str "  role: " (if (:role metrics) (name (:role metrics)) "unknown")
             "    Ca=" (or (:ca metrics) "-")
             "  Ce=" (or (:ce metrics) "-")
             "  I=" (if (:instability metrics)
                      (format "%.2f" (:instability metrics))
                      "-"))
        (str "  reach: " (if (:reach metrics)
                           (format "%.1f%%" (* 100.0 (:reach metrics)))
                           "-")
             "   fan-in: " (if (:fan-in metrics)
                             (format "%.1f%%" (* 100.0 (:fan-in metrics)))
                             "-"))
        (str "  family: " (or (:family metrics) "-")
             "    Ca-fam=" (or (:ca-family metrics) "-")
             "  Ca-ext=" (or (:ca-external metrics) "-")
             "  Ce-fam=" (or (:ce-family metrics) "-")
             "  Ce-ext=" (or (:ce-external metrics) "-"))
        ""
        (str "DIRECT DEPENDENCIES (" (count (:project direct-deps))
             " project, " (count (:external direct-deps)) " external)")
        (str "  project: " (if (seq (:project direct-deps))
                             (str/join ", " (map str (:project direct-deps)))
                             "(none)"))
        (str "  external: " (if (seq (:external direct-deps))
                              (str/join ", " (map str (:external direct-deps)))
                              "(none)"))
        ""
        (str "DIRECT DEPENDENTS (" (count direct-dependents) ")")]
       (concat
        (if (seq direct-dependents)
          (mapv #(str "  " %) direct-dependents)
          ["  (none)"])
        [""
         (str "CONCEPTUAL COUPLING (" (count conceptual-pairs) " pairs)")]
        (if (seq conceptual-pairs)
          (mapv #(format-pair-line ns-col ns %) conceptual-pairs)
          ["  (none)"])
        [""
         (str "CHANGE COUPLING (" (count change-pairs) " pairs)")]
        (if (seq change-pairs)
          (mapv #(format-pair-line ns-col ns %) change-pairs)
          ["  (none)"])
        [""
         (str "CYCLES: " (if (seq cycles)
                           (str (count cycles) " — "
                                (str/join "; " (map #(str/join ", " (sort (map str %)))
                                                    cycles)))
                           "none"))])))))

(defn format-explain-pair
  "Format explain-pair data as human-readable lines."
  [{:keys [ns-a ns-b structural conceptual change finding verdict error available]}]
  (if error
    [(str "Error: " error)
     (str "Available namespaces: " (str/join ", " (map str available)))]
    (into
     [(str "gordian explain-pair — " ns-a " ↔ " ns-b)
      ""
      "STRUCTURAL"
      (str "  direct edge: "
           (if (:direct-edge? structural)
             (str "yes (" (name (:direction structural)) ")")
             "no"))
      (str "  shortest path: "
           (if-let [p (:shortest-path structural)]
             (str/join " → " (map str p))
             "(none)"))]
     (concat
      [""
       "CONCEPTUAL"]
      (if conceptual
        [(str "  score: " (format "%.2f" (:score conceptual)))
         (str "  shared terms: " (str/join ", " (:shared-terms conceptual)))
         (str "  hidden: " (if (:structural-edge? conceptual) "no" "yes"))]
        ["  (no data)"])
      [""
       "CHANGE COUPLING"]
      (if change
        [(str "  score: " (format "%.2f" (:score change)))
         (str "  co-changes: " (:co-changes change))
         (str "  confidence: " (format "%.0f%%/%.0f%%"
                                       (* 100.0 (:confidence-a change))
                                       (* 100.0 (:confidence-b change))))]
        ["  (no data)"])
      (when finding
        [""
         "DIAGNOSIS"
         (str "  " (case (:severity finding)
                     :high   "● HIGH"
                     :medium "● MEDIUM"
                     :low    "● LOW")
              " — " (:reason finding))])
      (when verdict
        [""
         "VERDICT"
         (str "  " (name (:category verdict)))
         (str "  " (:explanation verdict))])))))

;;; ── markdown output ──────────────────────────────────────────────────────

(defn- md-pct [x] (format "%.1f%%" (* 100.0 x)))

(defn format-report-md
  "Markdown rendering of the analyze report."
  [{:keys [src-dirs propagation-cost cycles nodes
           conceptual-pairs conceptual-threshold
           change-pairs change-threshold]}]
  (into
   ["# Gordian — Namespace Coupling Report"
    ""
    (str "**Source:** `" (str/join " " src-dirs) "`")
    ""
    "## Summary"
    ""
    "| Metric | Value |"
    "|--------|-------|"
    (str "| Propagation cost | " (md-pct propagation-cost) " |")
    (str "| Namespaces | " (count nodes) " |")
    (str "| Cycles | " (count cycles) " |")]
   (concat
    ;; cycles
    (when (seq cycles)
      (into ["" "## Cycles" ""]
            (map-indexed
             (fn [i members]
               (str (inc i) ". "
                    (str/join " → " (sort (map str members)))
                    " (" (count members) " namespaces)"))
             cycles)))
    ;; namespace table
    ["" "## Namespace Metrics" ""
     "| Namespace | Reach | Fan-in | Ce | Ca | I | Role |"
     "|-----------|-------|--------|----|----|---|------|"]
    (map (fn [{:keys [ns reach fan-in ca ce instability role]}]
           (str "| " ns
                " | " (md-pct reach)
                " | " (md-pct fan-in)
                " | " (or ce "-")
                " | " (or ca "-")
                " | " (if instability (format "%.2f" instability) "-")
                " | " (if role (name role) "")
                " |"))
         nodes)
    ;; conceptual
    (when (seq conceptual-pairs)
      (into ["" (str "## Conceptual Coupling (score ≥ "
                     (format "%.2f" (double conceptual-threshold)) ")")
             ""
             "| Namespace A | Namespace B | Score | Structural | Shared Concepts |"
             "|-------------|-------------|-------|------------|-----------------|"]
            (map (fn [{:keys [ns-a ns-b score structural-edge? shared-terms]}]
                   (str "| " ns-a
                        " | " ns-b
                        " | " (format "%.2f" (double score))
                        " | " (if structural-edge? "yes" "no ←")
                        " | " (str/join ", " shared-terms)
                        " |"))
                 conceptual-pairs)))
    ;; change
    (when (seq change-pairs)
      (into ["" (str "## Change Coupling (Jaccard ≥ "
                     (format "%.2f" (double change-threshold)) ")")
             ""
             "| Namespace A | Namespace B | Jaccard | Co | Conf A | Conf B | Structural |"
             "|-------------|-------------|---------|-----|--------|--------|------------|"]
            (map (fn [{:keys [ns-a ns-b score co-changes
                              confidence-a confidence-b structural-edge?]}]
                   (str "| " ns-a
                        " | " ns-b
                        " | " (format "%.4f" (double score))
                        " | " co-changes
                        " | " (md-pct confidence-a)
                        " | " (md-pct confidence-b)
                        " | " (if structural-edge? "yes" "no ←")
                        " |"))
                 change-pairs))))))

(defn- md-severity-heading [severity]
  (case severity
    :high   "## 🔴 HIGH"
    :medium "## 🟡 MEDIUM"
    :low    "## 🟢 LOW"))

(defn- md-evidence-lines [{:keys [category evidence]}]
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

(defn- md-format-finding [f]
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

(defn- md-format-cluster-section [clusters]
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

(defn format-diagnose-md
  "Markdown rendering of findings.
  clusters-data    — optional {:clusters [...] :unclustered [...]}.
  rank             — :severity or :actionability.
  suppressed-count — optional count of noise findings suppressed from display.
  truncated-from   — optional total count before --top truncation."
  ([report health findings]
   (format-diagnose-md report health findings nil :severity nil nil))
  ([report health findings clusters-data]
   (format-diagnose-md report health findings clusters-data :severity nil nil))
  ([report health findings clusters-data rank]
   (format-diagnose-md report health findings clusters-data rank nil nil))
  ([report health findings clusters-data rank suppressed-count]
   (format-diagnose-md report health findings clusters-data rank suppressed-count nil))
  ([{:keys [src-dirs]} health findings clusters-data rank suppressed-count truncated-from]
   (let [count-sev (fn [s] (count (filter #(= s (:severity %)) findings)))
         n-high    (count-sev :high)
         n-medium  (count-sev :medium)
         n-low     (count-sev :low)
         n-total   (count findings)
         has-clusters? (seq (:clusters clusters-data))]
     (into
      [(str "# Gordian Diagnose — "
            (if truncated-from
              (str "Top " n-total " of " truncated-from " Findings")
              (str n-total " Finding" (when (not= 1 n-total) "s"))))
       ""
       (str "**Source:** `" (str/join " " src-dirs) "`")
       ""
       (str "**Rank:** `" (name rank) "`")
       ""
       "## Health"
       ""
       "| Metric | Value |"
       "|--------|-------|"
       (str "| Propagation cost | " (md-pct (:propagation-cost health))
            " (" (name (:health health)) ") |")
       (str "| Cycles | " (if (zero? (:cycle-count health))
                            "none"
                            (:cycle-count health)) " |")
       (str "| Namespaces | " (:ns-count health) " |")]
      (concat
       (if has-clusters?
         (concat
          (md-format-cluster-section (:clusters clusters-data))
          (when (seq (:unclustered clusters-data))
            (concat
             ["## Unclustered" ""]
             (mapcat md-format-finding (:unclustered clusters-data)))))
         ;; No clustering: by-severity grouping as before
         (let [grouped  (group-by :severity findings)]
           (mapcat identity
                   (keep (fn [sev]
                           (when-let [fs (seq (get grouped sev))]
                             (into [(md-severity-heading sev) ""]
                                   (mapcat md-format-finding fs))))
                         [:high :medium :low]))))
       ["---"
        ""
        (str "**"
             (if truncated-from
               (str "top " n-total " of " truncated-from " findings")
               (str n-total " finding" (when (not= 1 n-total) "s")))
             "** (" n-high " high, " n-medium " medium, " n-low " low)"
             (when (pos? (or suppressed-count 0))
               (str " — " suppressed-count
                    " noise suppressed (--show-noise to include)")))])))))

(defn format-explain-ns-md
  "Markdown rendering of explain-ns data."
  [{:keys [ns metrics direct-deps direct-dependents
           conceptual-pairs change-pairs cycles error available]}]
  (if error
    [(str "**Error:** " error)
     ""
     (str "Available namespaces: " (str/join ", " (map #(str "`" % "`") available)))]
    (into
     [(str "# Gordian Explain — " ns)
      ""
      "| Metric | Value |"
      "|--------|-------|"
      (str "| Role | " (if (:role metrics) (name (:role metrics)) "-") " |")
      (str "| Ca | " (or (:ca metrics) "-") " |")
      (str "| Ce | " (or (:ce metrics) "-") " |")
      (str "| I | " (if (:instability metrics)
                      (format "%.2f" (:instability metrics)) "-") " |")
      (str "| Reach | " (if (:reach metrics)
                          (md-pct (:reach metrics)) "-") " |")
      (str "| Fan-in | " (if (:fan-in metrics)
                           (md-pct (:fan-in metrics)) "-") " |")
      (str "| Family | `" (or (:family metrics) "-") "` |")
      (str "| Ca-family | " (or (:ca-family metrics) "-") " |")
      (str "| Ca-external | " (or (:ca-external metrics) "-") " |")
      (str "| Ce-family | " (or (:ce-family metrics) "-") " |")
      (str "| Ce-external | " (or (:ce-external metrics) "-") " |")]
     (concat
      ;; deps
      ["" "## Direct Dependencies" ""
       (str "- **Project:** "
            (if (seq (:project direct-deps))
              (str/join ", " (map #(str "`" % "`") (:project direct-deps)))
              "(none)"))
       (str "- **External:** "
            (if (seq (:external direct-deps))
              (str/join ", " (map #(str "`" % "`") (:external direct-deps)))
              "(none)"))]
      ;; dependents
      ["" "## Direct Dependents" ""]
      (if (seq direct-dependents)
        (mapv #(str "- `" % "`") direct-dependents)
        ["(none)"])
      ;; conceptual
      ["" (str "## Conceptual Coupling (" (count conceptual-pairs) " pairs)") ""]
      (if (seq conceptual-pairs)
        (into ["| Namespace | Score | Edge | Shared Terms |"
               "|-----------|-------|------|--------------|"]
              (map (fn [p]
                     (let [other (if (= ns (:ns-a p)) (:ns-b p) (:ns-a p))]
                       (str "| " other
                            " | " (format "%.2f" (:score p))
                            " | " (if (:structural-edge? p) "structural" "hidden")
                            " | " (str/join ", " (or (:shared-terms p) []))
                            " |")))
                   conceptual-pairs))
        ["(none)"])
      ;; change
      ["" (str "## Change Coupling (" (count change-pairs) " pairs)") ""]
      (if (seq change-pairs)
        (into ["| Namespace | Score | Co | Conf A | Conf B |"
               "|-----------|-------|----|--------|--------|"]
              (map (fn [p]
                     (let [other (if (= ns (:ns-a p)) (:ns-b p) (:ns-a p))]
                       (str "| " other
                            " | " (format "%.2f" (:score p))
                            " | " (:co-changes p)
                            " | " (md-pct (:confidence-a p))
                            " | " (md-pct (:confidence-b p))
                            " |")))
                   change-pairs))
        ["(none)"])
      ;; cycles
      ["" "## Cycles" ""
       (if (seq cycles)
         (str/join "; " (map #(str/join ", " (sort (map str %))) cycles))
         "none")]))))

(def ^:private verdict-emoji
  {:expected-structural       ""
   :family-naming-noise       ""
   :family-siblings           "🟡"
   :likely-missing-abstraction "🔴"
   :hidden-conceptual          "🟡"
   :hidden-change              "🟡"
   :transitive-only            "🟢"
   :unrelated                  ""})

(defn format-explain-pair-md
  "Markdown rendering of explain-pair data."
  [{:keys [ns-a ns-b structural conceptual change finding verdict error available]}]
  (if error
    [(str "**Error:** " error)
     ""
     (str "Available namespaces: " (str/join ", " (map #(str "`" % "`") available)))]
    (into
     [(str "# Gordian Explain-Pair — " ns-a " ↔ " ns-b)
      ""
      "## Structural"
      ""
      "| Property | Value |"
      "|----------|-------|"
      (str "| Direct edge | "
           (if (:direct-edge? structural)
             (str "yes (" (name (:direction structural)) ")")
             "no") " |")
      (str "| Shortest path | "
           (if-let [p (:shortest-path structural)]
             (str/join " → " (map str p))
             "(none)") " |")]
     (concat
      ;; conceptual
      ["" "## Conceptual" ""]
      (if conceptual
        ["| Property | Value |"
         "|----------|-------|"
         (str "| Score | " (format "%.2f" (:score conceptual)) " |")
         (str "| Shared terms | " (str/join ", " (:shared-terms conceptual)) " |")
         (str "| Hidden | " (if (:structural-edge? conceptual) "no" "yes") " |")]
        ["(no data)"])
      ;; change
      ["" "## Change Coupling" ""]
      (if change
        ["| Property | Value |"
         "|----------|-------|"
         (str "| Score | " (format "%.2f" (:score change)) " |")
         (str "| Co-changes | " (:co-changes change) " |")
         (str "| Confidence | " (format "%.0f%%/%.0f%%"
                                        (* 100.0 (:confidence-a change))
                                        (* 100.0 (:confidence-b change))) " |")]
        ["(no data)"])
      ;; diagnosis
      (when finding
        ["" "## Diagnosis" ""
         (str (case (:severity finding)
                :high   "🔴"
                :medium "🟡"
                :low    "🟢")
              " **" (str/upper-case (name (:severity finding)))
              "** — " (:reason finding))])
      ;; verdict
      (when verdict
        (let [emoji (get verdict-emoji (:category verdict) "")]
          ["" "## Verdict" ""
           (str (when (seq emoji) (str emoji " "))
                "**" (str/replace (name (:category verdict)) "-" " ")
                "** — " (:explanation verdict))]))))))

;;; ── compare output (text) ─────────────────────────────────────────────────

(defn- arrow [delta]
  (cond (pos? delta) "↑" (neg? delta) "↓" :else "→"))

(defn- delta-str [v fmt]
  (let [s (format fmt v)]
    (cond (pos? v) (str "+" s)
          :else    s)))

(defn- format-compare-health [{:keys [before after delta]}]
  [(str "  propagation cost: "
        (format "%.1f%%" (* 100.0 (:propagation-cost before)))
        " → " (format "%.1f%%" (* 100.0 (:propagation-cost after)))
        "  " (arrow (:propagation-cost delta))
        (delta-str (:propagation-cost delta) "%.1f%%"))
   (str "  cycles: " (:cycle-count before) " → " (:cycle-count after)
        (when (not= 0 (:cycle-count delta))
          (str "  " (arrow (:cycle-count delta))
               (delta-str (:cycle-count delta) "%d"))))
   (str "  namespaces: " (:ns-count before) " → " (:ns-count after)
        (when (not= 0 (:ns-count delta))
          (str "  " (arrow (:ns-count delta))
               (delta-str (:ns-count delta) "%d"))))])

(defn- format-compare-nodes [{:keys [added removed changed]}]
  (concat
   (when (seq removed)
     (cons "  Removed:"
           (mapv #(str "    - " (:ns %)) removed)))
   (when (seq added)
     (cons "  Added:"
           (mapv #(str "    + " (:ns %)) added)))
   (when (seq changed)
     (cons "  Changed:"
           (mapcat (fn [{:keys [ns delta]}]
                     [(str "    " ns
                           (when-let [r (:role delta)]
                             (str "  role: " (:before r) " → " (:after r)))
                           (when-let [d (:instability delta)]
                             (str "  I" (delta-str d "%.2f")))
                           (when-let [d (:reach delta)]
                             (str "  reach" (delta-str (* 100.0 d) "%.1f%%")))
                           (when-let [d (:ca delta)]
                             (str "  Ca" (delta-str d "%.0f")))
                           (when-let [d (:ce delta)]
                             (str "  Ce" (delta-str d "%.0f"))))])
                   changed)))))

(defn- format-compare-cycles [{:keys [added removed]}]
  (concat
   (when (seq removed)
     (mapv #(str "  ✅ removed: " (str/join " ↔ " (sort-by str %))) removed))
   (when (seq added)
     (mapv #(str "  🔴 added: " (str/join " ↔ " (sort-by str %))) added))))

(defn- format-compare-pairs [{:keys [added removed changed]}]
  (concat
   (when (seq removed)
     (mapv #(str "  ✅ removed: " (:ns-a %) " ↔ " (:ns-b %)
                 " (was " (format "%.2f" (get-in % [:score])) ")")
           removed))
   (when (seq added)
     (mapv #(str "  🔴 added: " (:ns-a %) " ↔ " (:ns-b %)
                 " (" (format "%.2f" (:score %)) ")")
           added))
   (when (seq changed)
     (mapv #(str "  " (arrow (get-in % [:delta :score])) " "
                 (:ns-a %) " ↔ " (:ns-b %)
                 "  " (format "%.2f" (get-in % [:before :score]))
                 " → " (format "%.2f" (get-in % [:after :score]))
                 "  (" (delta-str (get-in % [:delta :score]) "%.2f") ")")
           changed))))

(defn- format-compare-findings [{:keys [added removed]}]
  (concat
   (when (seq removed)
     (mapv #(str "  ✅ " (name (:category %)) ": "
                 (format-finding-subject %) " — " (:reason %))
           removed))
   (when (seq added)
     (mapv #(str "  🔴 " (name (:category %)) ": "
                 (format-finding-subject %) " — " (:reason %))
           added))))

(defn- section-if-nonempty
  "Emit header + lines only when content is non-empty."
  [header lines]
  (when (seq lines)
    (into [header] lines)))

(defn format-compare
  "Format a compare diff as human-readable lines."
  [diff]
  (let [health   (:health diff)
        nodes    (:nodes diff)
        cycles   (:cycles diff)
        c-pairs  (:conceptual-pairs diff)
        x-pairs  (:change-pairs diff)
        findings (:findings diff)]
    (into
     ["gordian compare"
      ""
      "HEALTH"]
     (concat
      (format-compare-health health)
      [""]
      (section-if-nonempty "NAMESPACES" (format-compare-nodes nodes))
      (when (seq (format-compare-nodes nodes)) [""])
      (section-if-nonempty "CYCLES" (format-compare-cycles cycles))
      (when (seq (format-compare-cycles cycles)) [""])
      (section-if-nonempty "CONCEPTUAL PAIRS" (format-compare-pairs c-pairs))
      (when (seq (format-compare-pairs c-pairs)) [""])
      (section-if-nonempty "CHANGE PAIRS" (format-compare-pairs x-pairs))
      (when (seq (format-compare-pairs x-pairs)) [""])
      (section-if-nonempty "FINDINGS" (format-compare-findings findings))))))

;;; ── compare output (markdown) ────────────────────────────────────────────

(defn format-compare-md
  "Format a compare diff as markdown lines."
  [diff]
  (let [health   (:health diff)
        nodes    (:nodes diff)
        cycles   (:cycles diff)
        c-pairs  (:conceptual-pairs diff)
        x-pairs  (:change-pairs diff)
        findings (:findings diff)]
    (into
     ["# gordian compare" ""]
     (concat
      ;; health table
      ["## Health" ""
       "| Metric | Before | After | Δ |"
       "|--------|--------|-------|---|"
       (str "| Propagation cost | "
            (format "%.1f%%" (* 100.0 (get-in health [:before :propagation-cost])))
            " | "
            (format "%.1f%%" (* 100.0 (get-in health [:after :propagation-cost])))
            " | " (delta-str (get-in health [:delta :propagation-cost]) "%.1f%%") " |")
       (str "| Cycles | " (get-in health [:before :cycle-count])
            " | " (get-in health [:after :cycle-count])
            " | " (delta-str (get-in health [:delta :cycle-count]) "%d") " |")
       (str "| Namespaces | " (get-in health [:before :ns-count])
            " | " (get-in health [:after :ns-count])
            " | " (delta-str (get-in health [:delta :ns-count]) "%d") " |")
       ""]
      ;; namespaces
      (when (or (seq (:added nodes)) (seq (:removed nodes)) (seq (:changed nodes)))
        (concat
         ["## Namespaces" ""]
         (when (seq (:added nodes))
           (cons "**Added:**"
                 (conj (mapv #(str "- `" (:ns %) "`") (:added nodes)) "")))
         (when (seq (:removed nodes))
           (cons "**Removed:**"
                 (conj (mapv #(str "- `" (:ns %) "`") (:removed nodes)) "")))
         (when (seq (:changed nodes))
           (concat
            ["**Changed:**" ""
             "| Namespace | Metric | Before | After | Δ |"
             "|-----------|--------|--------|-------|---|"]
            (mapcat (fn [{:keys [ns before after delta]}]
                      (keep (fn [k]
                              (when-let [d (get delta k)]
                                (if (map? d)
                                  (str "| `" ns "` | " (name k) " | " (:before d) " | " (:after d) " | — |")
                                  (str "| `" ns "` | " (name k) " | "
                                       (let [bv (get before k)] (if (float? bv) (format "%.2f" bv) bv))
                                       " | "
                                       (let [av (get after k)] (if (float? av) (format "%.2f" av) av))
                                       " | " (if (float? d) (delta-str d "%.2f") (delta-str d "%d")) " |"))))
                            (keys delta)))
                    (:changed nodes))
            [""]))))
      ;; cycles
      (when (or (seq (:added cycles)) (seq (:removed cycles)))
        (concat
         ["## Cycles" ""]
         (when (seq (:removed cycles))
           (mapv #(str "- ✅ Removed: " (str/join " ↔ " (sort-by str %))) (:removed cycles)))
         (when (seq (:added cycles))
           (mapv #(str "- 🔴 Added: " (str/join " ↔ " (sort-by str %))) (:added cycles)))
         [""]))
      ;; conceptual pairs
      (when (or (seq (:added c-pairs)) (seq (:removed c-pairs)) (seq (:changed c-pairs)))
        (concat
         ["## Conceptual Pairs" ""]
         (when (seq (:removed c-pairs))
           (mapv #(str "- ✅ `" (:ns-a %) "` ↔ `" (:ns-b %) "` (was " (format "%.2f" (:score %)) ")")
                 (:removed c-pairs)))
         (when (seq (:added c-pairs))
           (mapv #(str "- 🔴 `" (:ns-a %) "` ↔ `" (:ns-b %) "` (" (format "%.2f" (:score %)) ")")
                 (:added c-pairs)))
         (when (seq (:changed c-pairs))
           (mapv #(str "- " (arrow (get-in % [:delta :score]))
                       " `" (:ns-a %) "` ↔ `" (:ns-b %) "`"
                       " " (format "%.2f" (get-in % [:before :score]))
                       " → " (format "%.2f" (get-in % [:after :score]))
                       " (" (delta-str (get-in % [:delta :score]) "%.2f") ")")
                 (:changed c-pairs)))
         [""]))
      ;; change pairs
      (when (or (seq (:added x-pairs)) (seq (:removed x-pairs)) (seq (:changed x-pairs)))
        (concat
         ["## Change Pairs" ""]
         (when (seq (:removed x-pairs))
           (mapv #(str "- ✅ `" (:ns-a %) "` ↔ `" (:ns-b %) "` (was " (format "%.2f" (:score %)) ")")
                 (:removed x-pairs)))
         (when (seq (:added x-pairs))
           (mapv #(str "- 🔴 `" (:ns-a %) "` ↔ `" (:ns-b %) "` (" (format "%.2f" (:score %)) ")")
                 (:added x-pairs)))
         (when (seq (:changed x-pairs))
           (mapv #(str "- " (arrow (get-in % [:delta :score]))
                       " `" (:ns-a %) "` ↔ `" (:ns-b %) "`"
                       " " (format "%.2f" (get-in % [:before :score]))
                       " → " (format "%.2f" (get-in % [:after :score]))
                       " (" (delta-str (get-in % [:delta :score]) "%.2f") ")")
                 (:changed x-pairs)))
         [""]))
      ;; findings
      (when (or (seq (:added findings)) (seq (:removed findings)))
        (concat
         ["## Findings" ""]
         (when (seq (:removed findings))
           (mapv #(str "- ✅ " (name (:category %)) ": " (:reason %))
                 (:removed findings)))
         (when (seq (:added findings))
           (mapv #(str "- 🔴 " (name (:category %)) ": " (:reason %))
                 (:added findings)))))))))

;;; ── subgraph output ──────────────────────────────────────────────────────

(defn- format-subgraph-pair-line [pair]
  (str "  " (:ns-a pair) " ↔ " (:ns-b pair)
       (when-let [a (:actionability-score pair)]
         (str " [act=" (format "%.1f" a) "]"))
       (when-let [s (:score pair)]
         (str " score=" (format "%.2f" s)))))

(defn format-subgraph
  "Format subgraph/family view as human-readable lines."
  [{:keys [prefix rank-by members internal boundary pairs findings clusters error]}]
  (if error
    [(str "Error: " error)]
    (into
     [(str "gordian subgraph — " prefix)
      (str "members: " (count members))
      (str "rank: " (name rank-by))
      ""
      "MEMBERS"]
     (concat
      (map #(str "  " %) members)
      ["" "INTERNAL"
       (str "  nodes: " (get internal :node-count))
       (str "  edges: " (get internal :edge-count))
       (str "  density: " (format "%.1f%%" (* 100.0 (get internal :density 0.0))))
       (str "  propagation cost: " (format "%.1f%%" (* 100.0 (get internal :propagation-cost 0.0))))
       (str "  cycles: " (if (seq (:cycles internal)) (count (:cycles internal)) "none"))
       ""
       "BOUNDARY"
       (str "  incoming edges: " (:incoming-count boundary))
       (str "  outgoing edges: " (:outgoing-count boundary))
       (str "  dependents: " (if (seq (:dependents boundary))
                               (str/join ", " (:dependents boundary))
                               "none"))
       (str "  external deps: " (if (seq (:external-deps boundary))
                                  (str/join ", " (:external-deps boundary))
                                  "none"))]
      (when (seq (get-in pairs [:conceptual :internal]))
        (concat ["" "INTERNAL CONCEPTUAL PAIRS"]
                (map format-subgraph-pair-line (get-in pairs [:conceptual :internal]))))
      (when (seq (get-in pairs [:change :internal]))
        (concat ["" "INTERNAL CHANGE PAIRS"]
                (map format-subgraph-pair-line (get-in pairs [:change :internal]))))
      (when (seq findings)
        (concat ["" "TOUCHING FINDINGS"]
                (mapcat format-finding-lines findings)))
      (when (seq clusters)
        (format-cluster-section clusters))))))

(defn format-subgraph-md
  "Format subgraph/family view as markdown lines."
  [{:keys [prefix rank-by members internal boundary pairs findings clusters error]}]
  (if error
    [(str "**Error:** " error)]
    (into
     [(str "# gordian subgraph — " prefix)
      ""
      (str "**Rank:** `" (name rank-by) "`")
      ""
      "## Members"
      ""]
     (concat
      (map #(str "- `" % "`") members)
      ["" "## Internal" ""
       "| Metric | Value |"
       "|--------|-------|"
       (str "| Nodes | " (get internal :node-count) " |")
       (str "| Edges | " (get internal :edge-count) " |")
       (str "| Density | " (format "%.1f%%" (* 100.0 (get internal :density 0.0))) " |")
       (str "| Propagation cost | " (format "%.1f%%" (* 100.0 (get internal :propagation-cost 0.0))) " |")
       (str "| Cycles | " (if (seq (:cycles internal)) (count (:cycles internal)) "none") " |")
       ""
       "## Boundary"
       ""
       "| Metric | Value |"
       "|--------|-------|"
       (str "| Incoming edges | " (:incoming-count boundary) " |")
       (str "| Outgoing edges | " (:outgoing-count boundary) " |")
       (str "| Dependents | " (if (seq (:dependents boundary))
                                (str/join ", " (:dependents boundary))
                                "none") " |")
       (str "| External deps | " (if (seq (:external-deps boundary))
                                   (str/join ", " (:external-deps boundary))
                                   "none") " |")]
      (when (seq (get-in pairs [:conceptual :internal]))
        (concat ["" "## Internal Conceptual Pairs" ""]
                (map (fn [pair]
                       (str "- `" (:ns-a pair) "` ↔ `" (:ns-b pair) "`"
                            (when-let [a (:actionability-score pair)]
                              (str " [act=" (format "%.1f" a) "]"))
                            (when-let [s (:score pair)]
                              (str " score=" (format "%.2f" s)))))
                     (get-in pairs [:conceptual :internal]))))
      (when (seq findings)
        (concat ["" "## Findings" ""]
                (mapcat md-format-finding findings)))
      (when (seq clusters)
        (md-format-cluster-section clusters))))))

;;; ── communities output ───────────────────────────────────────────────────

(defn format-communities
  "Format communities as human-readable lines."
  [{:keys [lens threshold communities summary]}]
  (into
   [(str "gordian communities — " (name lens)
         (when threshold (str " (threshold " threshold ")")))
    ""
    "SUMMARY"
    (str "  communities: " (:community-count summary))
    (str "  largest: " (:largest-size summary))
    (str "  singletons: " (:singleton-count summary))]
   (mapcat (fn [{:keys [id members size density internal-weight boundary-weight dominant-terms bridge-namespaces]}]
             [""
              (str "[" id "] " size " namespaces")
              (str "  members: " (str/join ", " members))
              (str "  density: " (format "%.1f%%" (* 100.0 density)))
              (str "  internal weight: " (format "%.2f" internal-weight))
              (str "  boundary weight: " (format "%.2f" boundary-weight))
              (str "  dominant terms: " (if (seq dominant-terms)
                                          (str/join ", " dominant-terms)
                                          "none"))
              (str "  bridges: " (if (seq bridge-namespaces)
                                   (str/join ", " bridge-namespaces)
                                   "none"))])
           communities)))

(defn format-communities-md
  "Format communities as markdown lines."
  [{:keys [lens threshold communities summary]}]
  (into
   [(str "# gordian communities — " (name lens))
    ""
    (when threshold (str "**Threshold:** `" threshold "`"))
    ""
    "## Summary"
    ""
    "| Metric | Value |"
    "|--------|-------|"
    (str "| Communities | " (:community-count summary) " |")
    (str "| Largest | " (:largest-size summary) " |")
    (str "| Singletons | " (:singleton-count summary) " |")]
   (mapcat (fn [{:keys [id members size density internal-weight boundary-weight dominant-terms bridge-namespaces]}]
             [""
              (str "## Community " id)
              ""
              (str "- **Size:** " size)
              (str "- **Density:** " (format "%.1f%%" (* 100.0 density)))
              (str "- **Internal weight:** " (format "%.2f" internal-weight))
              (str "- **Boundary weight:** " (format "%.2f" boundary-weight))
              (str "- **Dominant terms:** " (if (seq dominant-terms)
                                              (str/join ", " (map #(str "`" % "`") dominant-terms))
                                              "none"))
              (str "- **Bridges:** " (if (seq bridge-namespaces)
                                       (str/join ", " (map #(str "`" % "`") bridge-namespaces))
                                       "none"))
              ""
              "### Members"
              ""
              (str/join "\n" (map #(str "- `" % "`") members))])
           communities)))

;;; ── tests output ─────────────────────────────────────────────────────────

(defn- format-counts-map [m]
  (if (seq m)
    (str "{" (str/join ", " (map (fn [[k v]] (str (name k) "=" v)) m)) "}")
    "{}"))

(defn format-tests
  "Format the tests command report as human-readable lines."
  [{:keys [summary test-namespaces invariants core-coverage pc-summary findings]}]
  (into
   ["gordian tests"
    ""
    "SUMMARY"
    (str "  src namespaces: " (:src-count summary))
    (str "  test namespaces: " (:test-count summary))
    (str "  roles: " (format-counts-map (:test-role-counts summary)))
    (str "  styles: " (format-counts-map (:test-style-counts summary)))
    (str "  propagation cost src: " (format "%.1f%%" (* 100.0 (:pc-src summary))))
    (str "  propagation cost src+test: " (format "%.1f%%" (* 100.0 (:pc-with-tests summary))))
    (str "  propagation cost delta: " (format "%.1f%%" (* 100.0 (:pc-delta summary))))
    ""
    "INVARIANTS"
    (str "  src→test edges: " (:src->test-edge-count summary))
    (str "  support leaked to src: " (:test-support-leaked-to-src-count summary))
    (str "  executable tests with incoming deps: " (:executable-tests-with-incoming-deps-count summary))
    (str "  shared test support: " (count (get invariants :shared-test-support)))
    ""
    "CORE COVERAGE"
    (str "  tested core: " (count (get core-coverage :tested-core)))
    (str "  untested core: " (count (get core-coverage :untested-core)))
    (str "  pc interpretation: " (name (:interpretation pc-summary)))
    ""
    "TEST NAMESPACES"]
   (concat
    (map (fn [{:keys [ns test-role test-style reach ca ce role]}]
           (str "  " (pad-right 24 (str ns))
                " role=" (pad-right 11 (name test-role))
                " style=" (pad-right 16 (name test-style))
                " reach=" (format "%5.1f%%" (* 100.0 (or reach 0.0)))
                " ca=" (or ca 0)
                " ce=" (or ce 0)
                " graph-role=" (name role)))
         test-namespaces)
    (when (seq findings)
      (concat ["" "FINDINGS"]
              (mapcat format-finding-lines findings))))))

(defn format-tests-md
  "Format the tests command report as markdown lines."
  [{:keys [summary test-namespaces invariants core-coverage pc-summary findings]}]
  (into
   ["# gordian tests"
    ""
    "## Summary"
    ""
    "| Metric | Value |"
    "|--------|-------|"
    (str "| Src namespaces | " (:src-count summary) " |")
    (str "| Test namespaces | " (:test-count summary) " |")
    (str "| Test roles | `" (:test-role-counts summary) "` |")
    (str "| Test styles | `" (:test-style-counts summary) "` |")
    (str "| Propagation cost src | " (format "%.1f%%" (* 100.0 (:pc-src summary))) " |")
    (str "| Propagation cost src+test | " (format "%.1f%%" (* 100.0 (:pc-with-tests summary))) " |")
    (str "| Propagation cost delta | " (format "%.1f%%" (* 100.0 (:pc-delta summary))) " |")
    ""
    "## Invariants"
    ""
    "| Metric | Value |"
    "|--------|-------|"
    (str "| Src→test edges | " (:src->test-edge-count summary) " |")
    (str "| Support leaked to src | " (:test-support-leaked-to-src-count summary) " |")
    (str "| Executable tests with incoming deps | " (:executable-tests-with-incoming-deps-count summary) " |")
    (str "| Shared test support | " (count (get invariants :shared-test-support)) " |")
    ""
    "## Core Coverage"
    ""
    "| Metric | Value |"
    "|--------|-------|"
    (str "| Tested core | " (count (get core-coverage :tested-core)) " |")
    (str "| Untested core | " (count (get core-coverage :untested-core)) " |")
    (str "| PC interpretation | `" (name (:interpretation pc-summary)) "` |")
    ""
    "## Test Namespaces"
    ""
    "| Namespace | Test role | Test style | Reach | Ca | Ce | Graph role |"
    "|-----------|-----------|------------|-------|----|----|------------|"]
   (concat
    (map (fn [{:keys [ns test-role test-style reach ca ce role]}]
           (str "| `" ns "` | `" (name test-role) "` | `" (name test-style) "` | "
                (format "%.1f%%" (* 100.0 (or reach 0.0)))
                " | " (or ca 0)
                " | " (or ce 0)
                " | `" (name role) "` |"))
         test-namespaces)
    (when (seq findings)
      (concat ["" "## Findings" ""]
              (mapcat md-format-finding findings))))))

;;; ── gate output ──────────────────────────────────────────────────────────

(defn- gate-status-marker [status]
  (if (= :pass status) "✅" "❌"))

(defn format-gate
  "Format a gate result as human-readable lines."
  [{:keys [result baseline-file src-dirs checks summary warnings]}]
  (into
   [(str "gordian gate — " (str/upper-case (name result)))
    (str "baseline: " baseline-file)
    (str "src: " (str/join " " src-dirs))
    ""
    "CHECKS"]
   (concat
    (map (fn [{check-name :name :keys [status actual limit]}]
           (str "  " (gate-status-marker status)
                " " (pad-right 20 (name check-name))
                " actual=" actual
                (when (some? limit)
                  (str " limit=" limit))))
         checks)
    (when (seq warnings)
      (concat
       ["" "WARNINGS"]
       (map (fn [w]
              (case (:kind w)
                :src-dirs-mismatch
                (str "  ⚠ src-dirs differ: baseline=" (:baseline w)
                     " current=" (:current w))
                (str "  ⚠ " w)))
            warnings)))
    [""
     "SUMMARY"
     (str "  " (:passed summary) " passed, " (:failed summary) " failed")])))

(defn format-gate-md
  "Format a gate result as markdown lines."
  [{:keys [result baseline-file src-dirs checks summary warnings]}]
  (into
   [(str "# gordian gate — " (str/upper-case (name result)))
    ""
    (str "**Baseline:** `" baseline-file "`")
    ""
    (str "**Source:** `" (str/join " " src-dirs) "`")
    ""
    "## Checks"
    ""
    "| Check | Status | Actual | Limit |"
    "|-------|--------|--------|-------|"]
   (concat
    (map (fn [{check-name :name :keys [status actual limit]}]
           (str "| " (name check-name) " | "
                (if (= :pass status) "✅ pass" "❌ fail")
                " | " actual " | " (or limit "—") " |"))
         checks)
    (when (seq warnings)
      (concat
       ["" "## Warnings" ""]
       (map (fn [w]
              (case (:kind w)
                :src-dirs-mismatch
                (str "- baseline src-dirs: `" (str/join " " (:baseline w))
                     "`, current src-dirs: `" (str/join " " (:current w)) "`")
                (str "- `" w "`")))
            warnings)))
    [""
     "## Summary"
     ""
     (str "**" (:passed summary) " passed, " (:failed summary) " failed**")])))

;;; ── IO ───────────────────────────────────────────────────────────────────

(defn print-report
  "Print a human-readable coupling report to stdout.
  `report` — unified map from gordian.main/build-report."
  [report]
  (run! println (format-report report)))

(defn print-diagnose
  "Print a human-readable diagnose report to stdout."
  ([report health findings]
   (run! println (format-diagnose report health findings)))
  ([report health findings clusters]
   (run! println (format-diagnose report health findings clusters)))
  ([report health findings clusters rank]
   (run! println (format-diagnose report health findings clusters rank)))
  ([report health findings clusters rank suppressed-count]
   (run! println (format-diagnose report health findings clusters rank suppressed-count)))
  ([report health findings clusters rank suppressed-count truncated-from]
   (run! println (format-diagnose report health findings clusters rank suppressed-count truncated-from))))

(defn print-explain-ns
  "Print a human-readable explain-ns report to stdout."
  [data]
  (run! println (format-explain-ns data)))

(defn print-explain-pair
  "Print a human-readable explain-pair report to stdout."
  [data]
  (run! println (format-explain-pair data)))

(defn print-compare
  "Print a human-readable compare report to stdout."
  [diff]
  (run! println (format-compare diff)))

(defn- format-block-members [members]
  (str/join ", " (map str members)))

(defn- format-collapsed-edge-line [{:keys [from to edge-count]}]
  (str "  B" from " -> B" to "  " edge-count " edge"
       (when (not= 1 edge-count) "s")))

(defn format-dsm
  "Return a vector of lines for DSM output."
  [{:keys [src-dirs summary blocks edges details ordering]}]
  (let [{:keys [block-count singleton-block-count largest-block-size
                inter-block-edge-count density]} summary
        block-lines (map (fn [{:keys [id size density members]}]
                           (str "  B" id
                                "  size " size
                                "  density " (format "%.2f" (double density))
                                "  members: " (format-block-members members)))
                         blocks)
        edge-lines  (if (seq edges)
                      (map format-collapsed-edge-line edges)
                      ["  (none)"])
        detail-lines (when (seq details)
                       (mapcat (fn [{:keys [id size members internal-edge-count density internal-edges]}]
                                 [(str "Block B" id)
                                  (str "  size: " size)
                                  (str "  members: " (format-block-members members))
                                  (str "  internal edges: " internal-edge-count)
                                  (str "  density: " (format "%.2f" (double density)))
                                  (str "  mini-matrix edges: " (pr-str internal-edges))
                                  ""])
                               details))]
    (vec
     (concat
      ["gordian dsm"
       (str "src: " (str/join " " src-dirs))
       ""
       "Diagonal block partition"
       (str "  ordering: " (name (:strategy ordering)))
       (str "  refined: " (if (:refined? ordering) "yes" "no"))
       (str "  alpha: " (format "%.1f" (double (:alpha ordering))))
       (str "  blocks: " block-count)
       (str "  singleton blocks: " singleton-block-count)
       (str "  largest block: " largest-block-size)
       ""
       "Block DSM"
       (str "  inter-block edges: " inter-block-edge-count)
       (str "  density: " (format "%.4f" (double density)))
       ""
       "Blocks"]
      block-lines
      ["" "Inter-block edges"]
      edge-lines
      (concat ["" "Block details" ""]
              (if (seq detail-lines)
                detail-lines
                ["No multi-namespace blocks."]))))))

(defn format-dsm-md
  "Return markdown lines for DSM output."
  [{:keys [src-dirs summary blocks edges details ordering]}]
  (vec
   (concat
    ["# Gordian DSM"
     ""
     (str "Source: `" (str/join " " src-dirs) "`")
     ""
     "## Summary"
     ""
     "| Metric | Value |"
     "|---|---:|"
     (str "| Ordering | " (name (:strategy ordering)) " |")
     (str "| Refined | " (if (:refined? ordering) "yes" "no") " |")
     (str "| Alpha | " (format "%.1f" (double (:alpha ordering))) " |")
     (str "| Blocks | " (:block-count summary) " |")
     (str "| Singleton blocks | " (:singleton-block-count summary) " |")
     (str "| Largest block | " (:largest-block-size summary) " |")
     (str "| Inter-block edges | " (:inter-block-edge-count summary) " |")
     (str "| Density | " (format "%.4f" (double (:density summary))) " |")
     ""
     "## Blocks"
     ""
     "| Block | Size | Density | Members |"
     "|---|---:|---:|---|"]
    (map (fn [{:keys [id size density members]}]
           (str "| B" id " | " size
                " | " (format "%.2f" (double density))
                " | `" (str/join "`, `" (map str members)) "` |"))
         blocks)
    ["" "## Inter-block edges" ""
     "| From | To | Edge count |"
     "|---|---|---:|"]
    (if (seq edges)
      (map (fn [{:keys [from to edge-count]}]
             (str "| B" from " | B" to " | " edge-count " |"))
           edges)
      ["| (none) |  | 0 |"])
    ["" "## Block details" ""]
    (if (seq details)
      (mapcat (fn [{:keys [id size members internal-edge-count density internal-edges]}]
                [""
                 (str "## Block B" id)
                 ""
                 (str "- Size: " size)
                 (str "- Members: `" (str/join "`, `" (map str members)) "`")
                 (str "- Internal edges: " internal-edge-count)
                 (str "- Density: " (format "%.2f" (double density)))
                 (str "- Mini-matrix edges: `" (pr-str internal-edges) "`")])
              details)
      ["No multi-namespace blocks."]))))

(defn print-subgraph
  "Print a human-readable subgraph report to stdout."
  [data]
  (run! println (format-subgraph data)))

(defn print-communities
  "Print a human-readable communities report to stdout."
  [data]
  (run! println (format-communities data)))

(defn print-dsm
  "Print a human-readable DSM report to stdout."
  [data]
  (run! println (format-dsm data)))

(defn print-gate
  "Print a human-readable gate report to stdout."
  [result]
  (run! println (format-gate result)))

;;; ── cyclomatic output ───────────────────────────────────────────────────

(defn- bar
  [n]
  (apply str (repeat (max 0 (min 50 (int n))) "█")))

(defn- rule
  [n]
  (apply str (repeat n "-")))

(defn- complexity-unit-label
  [{:keys [ns var arity]}]
  (str ns "/" var " [arity " arity "]"))

(defn- complexity-max-unit-label
  [max-unit]
  (str (:ns max-unit) "/" (:var max-unit)
       " [arity " (:arity max-unit) "]"
       " (" (:cc max-unit) ")"))

(defn- complexity-unit-header
  [unit-label-width bar-col-gap]
  [(str "  " (pad-right unit-label-width "unit")
        "  " (pad-left 4 "cc")
        "  " (pad-right 10 "risk")
        "  " (pad-left 9 "decisions")
        bar-col-gap "bar")
   (str "  " (rule unit-label-width)
        "  " (rule 4)
        "  " (rule 10)
        "  " (rule 9)
        bar-col-gap (rule 3))])

(defn- complexity-unit-row
  [unit-label-width bar-col-gap {:keys [cc cc-decision-count cc-risk] :as unit}]
  (str "  " (pad-right unit-label-width (complexity-unit-label unit))
       "  " (pad-left 4 cc)
       "  " (pad-right 10 (name (:level cc-risk)))
       "  " (pad-left 9 cc-decision-count)
       bar-col-gap (bar cc)))

(defn- complexity-rollup-header
  [ns-label-width bar-col-gap]
  [(str "  " (pad-right ns-label-width "namespace")
        "  " (pad-left 5 "units")
        "  " (pad-left 5 "total")
        "  " (pad-left 6 "avg")
        "  " (pad-left 4 "max")
        bar-col-gap "bar")
   (str "  " (rule ns-label-width)
        "  " (rule 5)
        "  " (rule 5)
        "  " (rule 6)
        "  " (rule 4)
        bar-col-gap (rule 3))])

(defn- complexity-rollup-row
  [ns-label-width bar-col-gap {:keys [ns unit-count total-cc avg-cc max-cc]}]
  (str "  " (pad-right ns-label-width (str ns))
       "  " (pad-left 5 unit-count)
       "  " (pad-left 5 total-cc)
       "  " (pad-left 6 (format "%.2f" (double avg-cc)))
       "  " (pad-left 4 max-cc)
       bar-col-gap (bar max-cc)))

(defn format-cyclomatic
  "Format cyclomatic complexity report as human-readable lines."
  [{:keys [src-dirs units namespace-rollups project-rollup max-unit]}]
  (let [unit-label-width (max 10 (apply max (concat [10] (map (comp count complexity-unit-label) units))))
        ns-label-width   (max 10 (apply max (concat [10] (map #(count (str (:ns %))) namespace-rollups))))
        bar-col-gap      "  "]
    (into
     ["gordian complexity"
      (str "src: " (str/join " " src-dirs))
      ""
      "SUMMARY"
      (str "  namespaces: " (:namespace-count project-rollup))
      (str "  units: " (:unit-count project-rollup))
      (str "  total complexity: " (:total-cc project-rollup))
      (str "  avg complexity: " (format "%.2f" (double (:avg-cc project-rollup))))
      (str "  max complexity: " (:max-cc project-rollup))
      (str "  max unit: " (if max-unit
                            (complexity-max-unit-label max-unit)
                            "(none)"))
      ""
      "UNITS"
      (first (complexity-unit-header unit-label-width bar-col-gap))
      (second (complexity-unit-header unit-label-width bar-col-gap))]
     (concat
      (if (seq units)
        (map (partial complexity-unit-row unit-label-width bar-col-gap)
             units)
        ["  (none)"])
      [""
       "NAMESPACE ROLLUP"
       (first (complexity-rollup-header ns-label-width bar-col-gap))
       (second (complexity-rollup-header ns-label-width bar-col-gap))]
      (if (seq namespace-rollups)
        (map (partial complexity-rollup-row ns-label-width bar-col-gap)
             namespace-rollups)
        ["  (none)"])
      [""
       "PROJECT ROLLUP"
       (str "  units=" (:unit-count project-rollup)
            " namespaces=" (:namespace-count project-rollup)
            " total=" (:total-cc project-rollup)
            " avg=" (format "%.2f" (double (:avg-cc project-rollup)))
            " max=" (:max-cc project-rollup))
       (str "  simple=" (get-in project-rollup [:cc-risk-counts :simple])
            " moderate=" (get-in project-rollup [:cc-risk-counts :moderate])
            " high=" (get-in project-rollup [:cc-risk-counts :high])
            " untestable=" (get-in project-rollup [:cc-risk-counts :untestable]))]))))

(defn format-cyclomatic-md
  "Format cyclomatic complexity report as markdown lines."
  [{:keys [src-dirs units namespace-rollups project-rollup max-unit]}]
  (into
   ["# gordian complexity"
    ""
    (str "**Source:** `" (str/join " " src-dirs) "`")
    ""
    "## Summary"
    ""
    "| Metric | Value |"
    "|--------|-------|"
    (str "| Namespaces | " (:namespace-count project-rollup) " |")
    (str "| Units | " (:unit-count project-rollup) " |")
    (str "| Total complexity | " (:total-cc project-rollup) " |")
    (str "| Avg complexity | " (format "%.2f" (double (:avg-cc project-rollup))) " |")
    (str "| Max complexity | " (:max-cc project-rollup) " |")
    (str "| Max unit | " (if max-unit
                           (str "`" (:ns max-unit) "/" (:var max-unit) " [arity " (:arity max-unit) "]` (" (:cc max-unit) ")")
                           "(none)") " |")
    ""
    "## Units"
    ""
    "| Unit | CC | Risk | Decisions |"
    "|------|----|------|-----------|"]
   (concat
    (if (seq units)
      (map (fn [{:keys [ns var arity cc cc-decision-count cc-risk]}]
             (str "| `" ns "/" var " [arity " arity "]` | "
                  cc " | " (name (:level cc-risk)) " | " cc-decision-count " |"))
           units)
      ["| (none) | 0 | n/a | 0 |"])
    [""
     "## Namespace rollup"
     ""
     "| Namespace | Units | Total CC | Avg CC | Max CC |"
     "|-----------|-------|----------|--------|--------|"]
    (if (seq namespace-rollups)
      (map (fn [{:keys [ns unit-count total-cc avg-cc max-cc]}]
             (str "| `" ns "` | " unit-count
                  " | " total-cc
                  " | " (format "%.2f" (double avg-cc))
                  " | " max-cc " |"))
           namespace-rollups)
      ["| (none) | 0 | 0 | 0.00 | 0 |"])
    [""
     "## Project rollup"
     ""
     (str "- **Units:** " (:unit-count project-rollup))
     (str "- **Namespaces:** " (:namespace-count project-rollup))
     (str "- **Total CC:** " (:total-cc project-rollup))
     (str "- **Avg CC:** " (format "%.2f" (double (:avg-cc project-rollup))))
     (str "- **Max CC:** " (:max-cc project-rollup))
     (str "- **Risk counts:** `" (pr-str (:cc-risk-counts project-rollup)) "`")])))

(defn print-tests
  "Print a human-readable tests report to stdout."
  [result]
  (run! println (format-tests result)))

(defn print-cyclomatic
  "Print a human-readable cyclomatic report to stdout."
  [result]
  (run! println (format-cyclomatic result)))
