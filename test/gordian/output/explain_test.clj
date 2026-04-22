(ns gordian.output.explain-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [gordian.output :as sut]
            [gordian.output.fixtures :as fx]))

(deftest format-explain-ns-test
  (let [lines (sut/format-explain-ns fx/explain-ns-data)]
    (testing "header shows ns name"
      (is (some #(str/includes? % "gordian.scan") lines)))
    (testing "shows role and metrics"
      (is (some #(str/includes? % "core") lines))
      (is (some #(str/includes? % "Ca=1") lines)))
    (testing "shows external deps"
      (is (some #(str/includes? % "babashka.fs") lines)))
    (testing "shows dependents"
      (is (some #(str/includes? % "gordian.main") lines)))
    (testing "shows conceptual pairs"
      (is (some #(str/includes? % "0.30") lines))
      (is (some #(str/includes? % "hidden") lines)))
    (testing "shows none for empty change pairs"
      (let [change-section (drop-while #(not (str/includes? % "CHANGE COUPLING")) lines)]
        (is (some #(str/includes? % "(none)") change-section))))
    (testing "shows cycles: none"
      (is (some #(str/includes? % "CYCLES: none") lines))))
  (testing "error data → error message"
    (let [lines (sut/format-explain-ns {:error "not found" :available ['a 'b]})]
      (is (some #(str/includes? % "Error:") lines)))))

(deftest format-explain-pair-test
  (let [lines (sut/format-explain-pair fx/explain-pair-data)]
    (testing "header shows both ns names"
      (is (some #(and (str/includes? % "gordian.aggregate")
                      (str/includes? % "gordian.close")) lines)))
    (testing "shows no direct edge"
      (is (some #(str/includes? % "direct edge: no") lines)))
    (testing "shows no shortest path"
      (is (some #(str/includes? % "(none)") lines)))
    (testing "shows conceptual score + terms"
      (is (some #(str/includes? % "0.37") lines))
      (is (some #(str/includes? % "reach, transitive, node") lines)))
    (testing "shows no change data"
      (let [change-section (drop-while #(not (str/includes? % "CHANGE COUPLING")) lines)]
        (is (some #(str/includes? % "(no data)") change-section))))
    (testing "shows diagnosis"
      (is (some #(str/includes? % "● MEDIUM") lines))))
  (testing "error data → error message"
    (let [lines (sut/format-explain-pair {:error "not found" :available ['a]})]
      (is (some #(str/includes? % "Error:") lines)))))

(deftest format-explain-pair-verdict-test
  (testing "verdict section appears in text output"
    (let [data  (assoc fx/explain-pair-data
                       :verdict {:category :family-siblings
                                 :explanation "Family siblings with shared domain vocabulary."})
          lines (sut/format-explain-pair data)]
      (is (some #(str/includes? % "VERDICT") lines))
      (is (some #(str/includes? % "family-siblings") lines))
      (is (some #(str/includes? % "Family siblings") lines))))
  (testing "verdict section appears in markdown output"
    (let [data  (assoc fx/explain-pair-data
                       :verdict {:category :likely-missing-abstraction
                                 :explanation "Likely missing abstraction."})
          lines (sut/format-explain-pair-md data)]
      (is (some #(str/includes? % "## Verdict") lines))
      (is (some #(str/includes? % "likely missing abstraction") lines))
      (is (some #(str/includes? % "🔴") lines))))
  (testing "no verdict → no section"
    (let [lines (sut/format-explain-pair fx/explain-pair-data)]
      (is (not (some #(str/includes? % "VERDICT") lines))))))

(deftest format-explain-ns-md-test
  (let [lines (sut/format-explain-ns-md fx/explain-ns-data)]
    (testing "header contains ns name"
      (is (some #(str/includes? % "gordian.scan") lines)))
    (testing "metrics table has Role, Ca, Ce"
      (is (some #(str/includes? % "| Role |") lines))
      (is (some #(str/includes? % "| Ca |") lines)))
    (testing "external deps with backticks"
      (is (some #(str/includes? % "`babashka.fs`") lines)))
    (testing "conceptual pairs as table"
      (is (some #(str/includes? % "| Namespace |") lines))
      (is (some #(str/includes? % "0.30") lines)))
    (testing "empty change pairs → (none)"
      (let [change-section (drop-while #(not (str/includes? % "## Change Coupling")) lines)]
        (is (some #(str/includes? % "(none)") change-section)))))
  (testing "error → error message"
    (let [lines (sut/format-explain-ns-md {:error "not found" :available ['a 'b]})]
      (is (some #(str/includes? % "Error") lines)))))

(deftest format-explain-pair-md-test
  (let [lines (sut/format-explain-pair-md fx/explain-pair-data)]
    (testing "header contains both ns names"
      (is (some #(and (str/includes? % "gordian.aggregate")
                      (str/includes? % "gordian.close")) lines)))
    (testing "structural table shows edge status"
      (is (some #(str/includes? % "| Direct edge |") lines)))
    (testing "conceptual shows score"
      (is (some #(str/includes? % "0.37") lines)))
    (testing "diagnosis shows severity emoji"
      (is (some #(str/includes? % "🟡") lines))))
  (testing "no change data → (no data)"
    (let [lines (sut/format-explain-pair-md fx/explain-pair-data)
          change-section (drop-while #(not (str/includes? % "## Change Coupling")) lines)]
      (is (some #(str/includes? % "(no data)") change-section)))))
