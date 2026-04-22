(ns gordian.output.tests
  (:require [clojure.string :as str]
            [gordian.output.common :as common]))

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
           (str "  " (common/pad-right 24 (str ns))
                " role=" (common/pad-right 11 (name test-role))
                " style=" (common/pad-right 16 (name test-style))
                " reach=" (format "%5.1f%%" (* 100.0 (or reach 0.0)))
                " ca=" (or ca 0)
                " ce=" (or ce 0)
                " graph-role=" (name role)))
         test-namespaces)
    (when (seq findings)
      (concat ["" "FINDINGS"]
              (mapcat common/format-finding-lines findings))))))

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
              (mapcat common/md-format-finding findings))))))

(defn print-tests
  "Print a human-readable tests report to stdout."
  [result]
  (run! println (format-tests result)))
