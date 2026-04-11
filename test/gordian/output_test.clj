(ns gordian.output-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [gordian.output :as sut]
            [gordian.main   :as main]))

(def fixture-report
  "Unified report matching the fixture graph (alpha←beta←gamma, no cycles)."
  {:src-dirs         ["resources/fixture"]
   :propagation-cost (/ 3.0 9.0)
   :cycles           []
   :nodes [{:ns 'gamma :reach (/ 2.0 3) :fan-in 0.0
            :ca 0 :ce 2 :instability 1.0  :role :peripheral}
           {:ns 'beta  :reach (/ 1.0 3) :fan-in (/ 1.0 3)
            :ca 1 :ce 1 :instability 0.5  :role :shared}
           {:ns 'alpha :reach 0.0       :fan-in (/ 2.0 3)
            :ca 2 :ce 0 :instability 0.0  :role :core}]})

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
      (is (some #(str/includes? % "core")       lines))
      (is (some #(str/includes? % "peripheral") lines))
      (is (some #(str/includes? % "shared")     lines)))))

;;; ── cycles section in format-report ─────────────────────────────────────

(deftest format-report-cycles-test
  (testing "no cycles → cycle section absent entirely"
    (let [lines (sut/format-report fixture-report)]
      (is (not (some #(clojure.string/includes? % "cycles") lines)))))

  (testing "with cycles → lists members"
    (let [report (assoc fixture-report :cycles [#{'a 'b}])
          lines  (sut/format-report report)]
      (is (some #(clojure.string/includes? % "cycles:") lines))
      (is (some #(and (clojure.string/includes? % "a")
                      (clojure.string/includes? % "b")) lines))
      (is (some #(clojure.string/includes? % "2 namespaces") lines)))))

;;; ── format-conceptual ────────────────────────────────────────────────────

(def ^:private sample-pairs
  [{:ns-a 'gordian.output  :ns-b 'gordian.json   :sim 0.54
    :structural-edge? false :shared-terms ["report" "format" "lines"]}
   {:ns-a 'gordian.classify :ns-b 'gordian.scc   :sim 0.48
    :structural-edge? true  :shared-terms ["cycle" "node"]}])

(deftest format-conceptual-test
  (testing "empty pairs → empty vector (section omitted)"
    (is (= [] (sut/format-conceptual [] 0.30))))

  (testing "returns a non-empty vector of strings"
    (let [lines (sut/format-conceptual sample-pairs 0.30)]
      (is (seq lines))
      (is (every? string? lines))))

  (testing "header includes threshold"
    (let [lines (sut/format-conceptual sample-pairs 0.30)]
      (is (some #(str/includes? % "0.30") lines))))

  (testing "column header present"
    (let [lines (sut/format-conceptual sample-pairs 0.30)]
      (is (some #(str/includes? % "namespace-a") lines))
      (is (some #(str/includes? % "structural") lines))
      (is (some #(str/includes? % "shared concepts") lines))))

  (testing "both namespace names appear"
    (let [lines (sut/format-conceptual sample-pairs 0.30)]
      (is (some #(str/includes? % "gordian.output") lines))
      (is (some #(str/includes? % "gordian.json") lines))))

  (testing "no-edge row shows ← and shared terms"
    (let [lines (sut/format-conceptual sample-pairs 0.30)
          no-row (first (filter #(str/includes? % "gordian.output") lines))]
      (is (str/includes? no-row "←"))
      (is (str/includes? no-row "report"))
      (is (str/includes? no-row "format"))))

  (testing "structural-edge row shows 'yes' and shared terms"
    (let [lines (sut/format-conceptual sample-pairs 0.30)
          yes-row (first (filter #(str/includes? % "gordian.classify") lines))]
      (is (str/includes? yes-row "yes"))
      (is (str/includes? yes-row "cycle"))))

  (testing "similarity score appears"
    (let [lines (sut/format-conceptual sample-pairs 0.30)]
      (is (some #(str/includes? % "0.54") lines))
      (is (some #(str/includes? % "0.48") lines)))))

;;; ── integration: full pipeline via build-report ──────────────────────────

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

;;; ── format-change-coupling ───────────────────────────────────────────────

(def ^:private change-pairs
  [{:ns-a 'gordian.output :ns-b 'gordian.main
    :coupling 0.6667 :co-changes 2
    :confidence-a 0.6667 :confidence-b 1.0
    :structural-edge? true}
   {:ns-a 'gordian.scan :ns-b 'gordian.output
    :coupling 0.5 :co-changes 2
    :confidence-a 0.6667 :confidence-b 0.6667
    :structural-edge? false}])

(deftest format-change-coupling-test
  (testing "empty pairs → empty vector (section omitted)"
    (is (= [] (sut/format-change-coupling [] 0.30))))

  (testing "returns a non-empty vector of strings"
    (let [lines (sut/format-change-coupling change-pairs 0.30)]
      (is (seq lines))
      (is (every? string? lines))))

  (testing "header includes threshold"
    (let [lines (sut/format-change-coupling change-pairs 0.30)]
      (is (some #(str/includes? % "0.30") lines))))

  (testing "column header present"
    (let [lines (sut/format-change-coupling change-pairs 0.30)]
      (is (some #(str/includes? % "namespace-a") lines))
      (is (some #(str/includes? % "Jaccard") lines))
      (is (some #(str/includes? % "conf-a") lines))
      (is (some #(str/includes? % "structural") lines))))

  (testing "both ns names appear in output"
    (let [lines (sut/format-change-coupling change-pairs 0.30)]
      (is (some #(str/includes? % "gordian.output") lines))
      (is (some #(str/includes? % "gordian.main") lines))
      (is (some #(str/includes? % "gordian.scan") lines))))

  (testing "coupling values appear"
    (let [lines (sut/format-change-coupling change-pairs 0.30)]
      (is (some #(str/includes? % "0.6667") lines))
      (is (some #(str/includes? % "0.5000") lines))))

  (testing "co-change count appears"
    (let [lines (sut/format-change-coupling change-pairs 0.30)]
      (is (some #(str/includes? % " 2") lines))))

  (testing "structural-edge row shows 'yes' without ←"
    (let [lines  (sut/format-change-coupling change-pairs 0.30)
          yes-row (first (filter #(str/includes? % "gordian.main") lines))]
      (is (str/includes? yes-row "yes"))
      (is (not (str/includes? yes-row "←")))))

  (testing "non-structural row shows ← discovery marker"
    (let [lines   (sut/format-change-coupling change-pairs 0.30)
          no-row  (first (filter #(str/includes? % "gordian.scan") lines))]
      (is (str/includes? no-row "←"))
      (is (not (str/includes? no-row "yes")))))

  (testing "change section absent from report when no :change-pairs"
    (let [lines (sut/format-report fixture-report)]
      (is (not (some #(str/includes? % "change coupling") lines)))))

  (testing "change section present in report when :change-pairs provided"
    (let [report (assoc fixture-report
                        :change-pairs change-pairs
                        :change-threshold 0.30)
          lines  (sut/format-report report)]
      (is (some #(str/includes? % "change coupling") lines))
      (is (some #(str/includes? % "gordian.main") lines)))))
