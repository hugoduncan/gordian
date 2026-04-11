(ns gordian.output-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [gordian.output :as sut]
            [gordian.main   :as main]))

(def fixture-report
  "Unified report matching the fixture graph (alpha←beta←gamma, no cycles)."
  {:src-dir          "test/fixture"
   :propagation-cost (/ 3.0 9.0)
   :cycles           []
   :nodes [{:ns 'gamma :reach (/ 2.0 3) :fan-in 0.0
             :ca 0 :ce 2 :instability 1.0}
            {:ns 'beta  :reach (/ 1.0 3) :fan-in (/ 1.0 3)
             :ca 1 :ce 1 :instability 0.5}
            {:ns 'alpha :reach 0.0       :fan-in (/ 2.0 3)
             :ca 2 :ce 0 :instability 0.0}]})

;;; ── column-widths ────────────────────────────────────────────────────────

(deftest column-widths-test
  (testing "minimum width is 20"
    (is (= 20 (:ns-col (sut/column-widths [{:ns 'a}])))))

  (testing "expands for long names"
    (let [long-ns 'very.long.namespace.name.here]
      (is (= (count (str long-ns))
             (:ns-col (sut/column-widths [{:ns long-ns}])))))))

;;; ── format-row ───────────────────────────────────────────────────────────

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

;;; ── format-report ────────────────────────────────────────────────────────

(deftest format-report-test
  (let [lines (sut/format-report fixture-report)]
    (testing "header contains 'gordian'"
      (is (str/includes? (first lines) "gordian")))

    (testing "src-dir appears"
      (is (str/includes? (second lines) "test/fixture")))

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
      (is (some #(str/includes? % "0.00") lines)))))

;;; ── cycles section in format-report ─────────────────────────────────────

(deftest format-report-cycles-test
  (testing "no cycles → 'cycles: none'"
    (let [lines (sut/format-report fixture-report)]
      (is (some #(clojure.string/includes? % "cycles: none") lines))))

  (testing "with cycles → lists members"
    (let [report (assoc fixture-report :cycles [#{'a 'b}])
          lines  (sut/format-report report)]
      (is (some #(clojure.string/includes? % "cycles:") lines))
      (is (some #(and (clojure.string/includes? % "a")
                      (clojure.string/includes? % "b")) lines))
      (is (some #(clojure.string/includes? % "2 namespaces") lines)))))

;;; ── integration: full pipeline via build-report ──────────────────────────

(deftest full-pipeline-integration-test
  (let [output (with-out-str
                 (-> "test/fixture"
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
      (is (str/includes? output "Ca")))))
