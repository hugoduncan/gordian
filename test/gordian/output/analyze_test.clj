(ns gordian.output.analyze-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [gordian.main :as main]
            [gordian.output :as sut]
            [gordian.output.fixtures :as fx]))

(deftest column-widths-test
  (testing "minimum width is 20"
    (is (= 20 (:ns-col (sut/column-widths [{:ns 'a}])))))

  (testing "expands for long names"
    (let [long-ns 'very.long.namespace.name.here]
      (is (= (count (str long-ns))
             (:ns-col (sut/column-widths [{:ns long-ns}])))))))

(deftest format-row-test
  (testing "contains ns name and key metrics"
    (let [row (sut/format-row 20 {:ns 'gamma :reach (/ 2.0 3) :fan-in 0.0
                                  :ca 0 :ce 2 :instability 1.0})]
      (is (str/includes? row "gamma"))
      (is (str/includes? row "66.7%"))
      (is (str/includes? row "0.0%"))
      (is (str/includes? row "1.00"))))

  (testing "missing metrics fall back to dash"
    (let [row (sut/format-row 20 {:ns 'x :reach 0.0 :fan-in 0.0})]
      (is (str/includes? row "-")))))

(deftest format-report-test
  (let [lines (sut/format-report fx/fixture-report)]
    (testing "header contains 'gordian'"
      (is (str/includes? (first lines) "gordian")))
    (testing "src-dir appears"
      (is (str/includes? (second lines) "resources/fixture")))
    (testing "propagation cost value appears"
      (is (some #(str/includes? % "0.3333") lines)))
    (testing "all namespace names appear"
      (doseq [ns-name ["alpha" "beta" "gamma"]]
        (is (some #(str/includes? % ns-name) lines))))
    (testing "column header includes Ca, Ce, I"
      (let [header (some #(str/includes? % "Ce") lines)]
        (is header)))
    (testing "instability values appear"
      (is (some #(str/includes? % "1.00") lines))
      (is (some #(str/includes? % "0.50") lines))
      (is (some #(str/includes? % "0.00") lines)))
    (testing "role column present in header"
      (is (some #(str/includes? % "role") lines)))
    (testing "role values appear"
      (is (some #(str/includes? % "core") lines))
      (is (some #(str/includes? % "peripheral") lines))
      (is (some #(str/includes? % "shared") lines)))))

(deftest format-report-cycles-test
  (testing "no cycles → cycle section absent entirely"
    (let [lines (sut/format-report fx/fixture-report)]
      (is (not (some #(clojure.string/includes? % "cycles") lines)))))

  (testing "with cycles → lists members"
    (let [report (assoc fx/fixture-report :cycles [#{'a 'b}])
          lines  (sut/format-report report)]
      (is (some #(clojure.string/includes? % "cycles:") lines))
      (is (some #(and (clojure.string/includes? % "a")
                      (clojure.string/includes? % "b")) lines))
      (is (some #(clojure.string/includes? % "2 namespaces") lines)))))

(deftest format-conceptual-test
  (testing "empty pairs → empty vector (section omitted)"
    (is (= [] (sut/format-conceptual [] 0.30))))
  (testing "returns a non-empty vector of strings"
    (let [lines (sut/format-conceptual fx/sample-pairs 0.30)]
      (is (seq lines))
      (is (every? string? lines))))
  (testing "header includes threshold"
    (let [lines (sut/format-conceptual fx/sample-pairs 0.30)]
      (is (some #(str/includes? % "0.30") lines))))
  (testing "column header present"
    (let [lines (sut/format-conceptual fx/sample-pairs 0.30)]
      (is (some #(str/includes? % "namespace-a") lines))
      (is (some #(str/includes? % "structural") lines))
      (is (some #(str/includes? % "shared concepts") lines))))
  (testing "both namespace names appear"
    (let [lines (sut/format-conceptual fx/sample-pairs 0.30)]
      (is (some #(str/includes? % "gordian.output") lines))
      (is (some #(str/includes? % "gordian.json") lines))))
  (testing "no-edge row shows ← and shared terms"
    (let [lines (sut/format-conceptual fx/sample-pairs 0.30)
          no-row (first (filter #(str/includes? % "gordian.output") lines))]
      (is (str/includes? no-row "←"))
      (is (str/includes? no-row "report"))
      (is (str/includes? no-row "format"))))
  (testing "structural-edge row shows 'yes' and shared terms"
    (let [lines (sut/format-conceptual fx/sample-pairs 0.30)
          yes-row (first (filter #(str/includes? % "gordian.classify") lines))]
      (is (str/includes? yes-row "yes"))
      (is (str/includes? yes-row "cycle"))))
  (testing "similarity score appears"
    (let [lines (sut/format-conceptual fx/sample-pairs 0.30)]
      (is (some #(str/includes? % "0.54") lines))
      (is (some #(str/includes? % "0.48") lines)))))

(deftest full-pipeline-integration-test
  (let [output (with-out-str
                 (-> ["resources/fixture"]
                     main/build-report
                     sut/print-report))]
    (testing "output mentions propagation cost"
      (is (str/includes? output "propagation cost")))
    (testing "all fixture namespaces appear"
      (doseq [ns-name ["alpha" "beta" "gamma"]]
        (is (str/includes? output ns-name))))
    (testing "PC ≈ 33.3%"
      (is (str/includes? output "33.3")))
    (testing "instability column present"
      (is (str/includes? output "1.00")))
    (testing "Ce and Ca columns present"
      (is (str/includes? output "Ce"))
      (is (str/includes? output "Ca")))
    (testing "role column present"
      (is (str/includes? output "role"))
      (is (str/includes? output "core"))
      (is (str/includes? output "peripheral")))))

(deftest format-change-coupling-test
  (testing "empty pairs → empty vector (section omitted)"
    (is (= [] (sut/format-change-coupling [] 0.30))))
  (testing "returns a non-empty vector of strings"
    (let [lines (sut/format-change-coupling fx/change-pairs 0.30)]
      (is (seq lines))
      (is (every? string? lines))))
  (testing "header includes threshold"
    (let [lines (sut/format-change-coupling fx/change-pairs 0.30)]
      (is (some #(str/includes? % "0.30") lines))))
  (testing "column header present"
    (let [lines (sut/format-change-coupling fx/change-pairs 0.30)]
      (is (some #(str/includes? % "namespace-a") lines))
      (is (some #(str/includes? % "Jaccard") lines))
      (is (some #(str/includes? % "conf-a") lines))
      (is (some #(str/includes? % "structural") lines))))
  (testing "both ns names appear in output"
    (let [lines (sut/format-change-coupling fx/change-pairs 0.30)]
      (is (some #(str/includes? % "gordian.output") lines))
      (is (some #(str/includes? % "gordian.main") lines))
      (is (some #(str/includes? % "gordian.scan") lines))))
  (testing "coupling values appear"
    (let [lines (sut/format-change-coupling fx/change-pairs 0.30)]
      (is (some #(str/includes? % "0.6667") lines))
      (is (some #(str/includes? % "0.5000") lines))))
  (testing "co-change count appears"
    (let [lines (sut/format-change-coupling fx/change-pairs 0.30)]
      (is (some #(str/includes? % " 2") lines))))
  (testing "structural-edge row shows 'yes' without ←"
    (let [lines  (sut/format-change-coupling fx/change-pairs 0.30)
          yes-row (first (filter #(str/includes? % "gordian.main") lines))]
      (is (str/includes? yes-row "yes"))
      (is (not (str/includes? yes-row "←")))))
  (testing "non-structural row shows ← discovery marker"
    (let [lines   (sut/format-change-coupling fx/change-pairs 0.30)
          no-row  (first (filter #(str/includes? % "gordian.scan") lines))]
      (is (str/includes? no-row "←"))
      (is (not (str/includes? no-row "yes")))))
  (testing "change section absent from report when no :change-pairs"
    (let [lines (sut/format-report fx/fixture-report)]
      (is (not (some #(str/includes? % "change coupling") lines)))))
  (testing "change section present in report when :change-pairs provided"
    (let [report (assoc fx/fixture-report
                        :change-pairs fx/change-pairs
                        :change-threshold 0.30)
          lines  (sut/format-report report)]
      (is (some #(str/includes? % "change coupling") lines))
      (is (some #(str/includes? % "gordian.main") lines)))))

(deftest format-report-md-test
  (let [lines (sut/format-report-md fx/fixture-report)]
    (testing "header starts with # Gordian"
      (is (str/starts-with? (first lines) "# Gordian")))
    (testing "summary table contains propagation cost"
      (is (some #(str/includes? % "33.3%") lines)))
    (testing "namespace table has header"
      (is (some #(str/includes? % "| Namespace |") lines)))
    (testing "all fixture ns names appear"
      (doseq [ns-name ["alpha" "beta" "gamma"]]
        (is (some #(str/includes? % ns-name) lines))))
    (testing "no cycles → no cycles section"
      (is (not (some #(str/includes? % "## Cycles") lines)))))
  (testing "conceptual section present when pairs provided"
    (let [report (assoc fx/fixture-report
                        :conceptual-pairs fx/sample-pairs
                        :conceptual-threshold 0.30)
          lines  (sut/format-report-md report)]
      (is (some #(str/includes? % "## Conceptual Coupling") lines))
      (is (some #(str/includes? % "gordian.output") lines))))
  (testing "conceptual section absent when no pairs"
    (let [lines (sut/format-report-md fx/fixture-report)]
      (is (not (some #(str/includes? % "## Conceptual") lines)))))
  (testing "change section present when pairs provided"
    (let [report (assoc fx/fixture-report
                        :change-pairs fx/change-pairs
                        :change-threshold 0.30)
          lines  (sut/format-report-md report)]
      (is (some #(str/includes? % "## Change Coupling") lines))))
  (testing "cycles listed when present"
    (let [report (assoc fx/fixture-report :cycles [#{'a 'b}])
          lines  (sut/format-report-md report)]
      (is (some #(str/includes? % "## Cycles") lines))
      (is (some #(and (str/includes? % "a") (str/includes? % "b")) lines)))))
