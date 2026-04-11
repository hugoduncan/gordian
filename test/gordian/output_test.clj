(ns gordian.output-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [gordian.output    :as sut]
            [gordian.scan      :as scan]
            [gordian.close     :as close]
            [gordian.aggregate :as aggregate]))

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
  (testing "contains ns name and formatted percentages"
    (let [row (sut/format-row 20 {:ns 'alpha :reach 0.0 :fan-in 2/3})]
      (is (str/includes? row "alpha"))
      (is (str/includes? row "0.0%"))
      (is (str/includes? row "66.7%"))))

  (testing "row is padded to ns-col width"
    (let [row (sut/format-row 25 {:ns 'x :reach 0.5 :fan-in 0.5})]
      ;; ns portion is padded to 25 chars before the data columns
      (is (= \space (nth row 1))))))

;;; ── format-report ────────────────────────────────────────────────────────

(deftest format-report-test
  (let [metrics {:propagation-cost (/ 3.0 9.0)
                 :nodes [{:ns 'gamma :reach (/ 2.0 3) :fan-in 0.0}
                          {:ns 'beta  :reach (/ 1.0 3) :fan-in (/ 1.0 3)}
                          {:ns 'alpha :reach 0.0       :fan-in (/ 2.0 3)}]}
        lines   (sut/format-report metrics "test/fixture")]

    (testing "header line contains 'gordian'"
      (is (str/includes? (first lines) "gordian")))

    (testing "src-dir appears in second line"
      (is (str/includes? (second lines) "test/fixture")))

    (testing "propagation cost value appears"
      (is (some #(str/includes? % "0.3333") lines)))

    (testing "all namespace names appear"
      (doseq [ns-name ["alpha" "beta" "gamma"]]
        (is (some #(str/includes? % ns-name) lines))))

    (testing "percentage for gamma reach ≈ 66.7%"
      (let [gamma-line (first (filter #(str/includes? % "gamma") lines))]
        (is (str/includes? gamma-line "66.7%"))))))

;;; ── integration: full pipeline on fixture dir ────────────────────────────

(deftest full-pipeline-integration-test
  (let [output (with-out-str
                 (-> "test/fixture"
                     scan/scan
                     close/close
                     aggregate/aggregate
                     (sut/print-report "test/fixture")))]
    (testing "output mentions propagation cost"
      (is (str/includes? output "propagation cost")))

    (testing "all fixture namespaces appear in output"
      (doseq [ns-name ["alpha" "beta" "gamma"]]
        (is (str/includes? output ns-name))))

    (testing "PC ≈ 33.3%"
      (is (str/includes? output "33.3")))))
