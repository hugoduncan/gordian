(ns gordian.output.gate
  (:require [clojure.string :as str]
            [gordian.output.common :as common]))

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
                " " (common/pad-right 20 (name check-name))
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

(defn print-gate
  "Print a human-readable gate report to stdout."
  [result]
  (run! println (format-gate result)))
