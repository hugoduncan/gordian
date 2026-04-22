(ns gordian.output.communities
  (:require [clojure.string :as str]))

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

(defn print-communities
  "Print a human-readable communities report to stdout."
  [data]
  (run! println (format-communities data)))
