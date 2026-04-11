(ns gordian.output
  (:require [clojure.string :as str]))

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
      [(str "gordian — namespace coupling report")
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
    :cycle          (str (str/join " → " (sort (map str (:members subject)))))
    (:cross-lens-hidden :hidden-conceptual :hidden-change)
    (str (:ns-a subject) " ↔ " (:ns-b subject))
    (str (:ns subject))))

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
    [(str "  shared terms: " (str/join ", " (:shared-terms evidence)))
     "  → no structural edge"]

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

    :hub
    [(str "  Ce=" (:ce evidence)
          " I=" (format "%.2f" (or (:instability evidence) 0.0))
          " role=" (name (or (:role evidence) :unknown)))]

    []))

(defn format-diagnose
  "Format findings as human-readable lines.
  health — map from diagnose/health.
  findings — sorted vec of finding maps."
  [{:keys [src-dirs]} health findings]
  (let [count-sev (fn [s] (count (filter #(= s (:severity %)) findings)))
        n-high    (count-sev :high)
        n-medium  (count-sev :medium)
        n-low     (count-sev :low)
        n-total   (count findings)]
    (into
     [(str "gordian diagnose — " n-total " finding" (when (not= 1 n-total) "s"))
      (str "src: " (str/join " " src-dirs))
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
      (mapcat (fn [f]
                (concat
                 [(str (severity-marker (:severity f))
                       "  " (format-finding-subject f))
                  (str "  " (:reason f))]
                 (format-evidence-lines f)
                 [""]))
              findings)
      [(str n-total " finding" (when (not= 1 n-total) "s")
            " (" n-high " high, " n-medium " medium, " n-low " low)")]))))

;;; ── IO ───────────────────────────────────────────────────────────────────

(defn print-report
  "Print a human-readable coupling report to stdout.
  `report` — unified map from gordian.main/build-report."
  [report]
  (run! println (format-report report)))

(defn print-diagnose
  "Print a human-readable diagnose report to stdout."
  [report health findings]
  (run! println (format-diagnose report health findings)))
