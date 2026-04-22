(ns gordian.output.other-commands-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [gordian.output :as sut]
            [gordian.output.fixtures :as fx]))

(deftest format-dsm-test
  (let [lines (sut/format-dsm fx/dsm-data)]
    (testing "shows summary counts"
      (is (some #(str/includes? % "blocks: 2") lines))
      (is (some #(str/includes? % "singleton blocks: 1") lines))
      (is (some #(str/includes? % "largest block: 2") lines))
      (is (some #(str/includes? % "ordering: dfs-topo") lines))
      (is (some #(str/includes? % "alpha: 2.0") lines)))
    (testing "block list includes ids and members"
      (is (some #(str/includes? % "B0") lines))
      (is (some #(str/includes? % "a, b") lines)))
    (testing "inter-block edge section shows counted relations"
      (is (some #(str/includes? % "B1 -> B0") lines)))
    (testing "block detail appears"
      (is (some #(str/includes? % "Block B1") lines))
      (is (some #(str/includes? % "mini-matrix edges") lines)))))

(deftest format-dsm-singleton-only-test
  (let [data {:src-dirs ["resources/fixture"]
              :ordering {:strategy :dfs-topo :refined? false :alpha 2.0 :beta 0.05 :nodes ['a]}
              :blocks [{:id 0 :members ['a] :size 1 :internal-edge-count 0 :density 0.0}]
              :edges []
              :summary {:block-count 1
                        :singleton-block-count 1
                        :largest-block-size 1
                        :inter-block-edge-count 0
                        :density 0.0}
              :details []}
        lines (sut/format-dsm data)]
    (is (some #(str/includes? % "Block details") lines))
    (is (some #(str/includes? % "No multi-namespace blocks.") lines))))

(deftest format-dsm-md-test
  (let [lines (sut/format-dsm-md fx/dsm-data)]
    (testing "includes markdown summary table headers"
      (is (some #(str/includes? % "| Metric | Value |") lines)))
    (testing "includes blocks table columns"
      (is (some #(str/includes? % "| Block | Size | Density | Members |") lines)))
    (testing "includes inter-block table columns"
      (is (some #(str/includes? % "| From | To | Edge count |") lines)))
    (testing "renders block detail heading"
      (is (some #(str/includes? % "## Block B1") lines)))))

(deftest format-compare-test
  (let [lines (sut/format-compare fx/compare-diff)
        text  (str/join "\n" lines)]
    (testing "header"
      (is (str/includes? text "gordian compare")))
    (testing "health section shows before → after"
      (is (str/includes? text "21.0%"))
      (is (str/includes? text "15.0%")))
    (testing "namespace changes"
      (is (str/includes? text "d.new"))
      (is (str/includes? text "c.old"))
      (is (str/includes? text "a.core")))
    (testing "cycles"
      (is (str/includes? text "✅ removed")))
    (testing "conceptual pairs"
      (is (str/includes? text "0.28"))
      (is (str/includes? text "0.35"))
      (is (str/includes? text "0.45"))
      (is (str/includes? text "0.32")))
    (testing "findings"
      (is (str/includes? text "hidden-conceptual"))
      (is (str/includes? text "cycle")))))

(deftest format-compare-md-test
  (let [lines (sut/format-compare-md fx/compare-diff)
        text  (str/join "\n" lines)]
    (testing "markdown header"
      (is (str/includes? text "# gordian compare")))
    (testing "health table"
      (is (str/includes? text "| Propagation cost |"))
      (is (str/includes? text "21.0%"))
      (is (str/includes? text "15.0%")))
    (testing "namespace sections"
      (is (str/includes? text "`d.new`"))
      (is (str/includes? text "`c.old`")))
    (testing "cycles section"
      (is (str/includes? text "✅ Removed")))
    (testing "conceptual pairs section"
      (is (str/includes? text "## Conceptual Pairs")))
    (testing "findings section"
      (is (str/includes? text "## Findings")))))

(deftest format-compare-empty-diff-test
  (let [empty-diff {:gordian/command :compare
                    :before {:gordian/version "0.2.0" :src-dirs ["src/"]}
                    :after  {:gordian/version "0.2.0" :src-dirs ["src/"]}
                    :health {:before {:propagation-cost 0.10 :cycle-count 0 :ns-count 5}
                             :after  {:propagation-cost 0.10 :cycle-count 0 :ns-count 5}
                             :delta  {:propagation-cost 0.0 :cycle-count 0 :ns-count 0}}
                    :nodes  {:added [] :removed [] :changed []}
                    :cycles {:added [] :removed []}
                    :conceptual-pairs {:added [] :removed [] :changed []}
                    :change-pairs     {:added [] :removed [] :changed []}
                    :findings         {:added [] :removed []}}
        lines (sut/format-compare empty-diff)
        text  (str/join "\n" lines)]
    (testing "health is always shown"
      (is (str/includes? text "HEALTH")))
    (testing "empty sections are omitted"
      (is (not (str/includes? text "NAMESPACES")))
      (is (not (str/includes? text "CYCLES")))
      (is (not (str/includes? text "CONCEPTUAL PAIRS")))
      (is (not (str/includes? text "FINDINGS"))))))

(deftest format-gate-test
  (let [text (str/join "\n" (sut/format-gate fx/gate-result-data))]
    (is (str/includes? text "gordian gate — FAIL"))
    (is (str/includes? text "baseline.edn"))
    (is (str/includes? text "CHECKS"))
    (is (str/includes? text "pc-delta"))
    (is (str/includes? text "new-cycles"))
    (is (str/includes? text "WARNINGS"))
    (is (str/includes? text "SUMMARY"))))

(deftest format-gate-md-test
  (let [text (str/join "\n" (sut/format-gate-md fx/gate-result-data))]
    (is (str/includes? text "# gordian gate — FAIL"))
    (is (str/includes? text "| Check | Status | Actual | Limit |"))
    (is (str/includes? text "baseline.edn"))
    (is (str/includes? text "## Warnings"))
    (is (str/includes? text "## Summary"))))

(deftest format-subgraph-test
  (let [text (str/join "\n" (sut/format-subgraph fx/subgraph-data))]
    (is (str/includes? text "gordian subgraph — gordian"))
    (is (str/includes? text "MEMBERS"))
    (is (str/includes? text "INTERNAL"))
    (is (str/includes? text "BOUNDARY"))
    (is (str/includes? text "INTERNAL CONCEPTUAL PAIRS"))
    (is (str/includes? text "[act=8.8]"))))

(deftest format-subgraph-md-test
  (let [text (str/join "\n" (sut/format-subgraph-md fx/subgraph-data))]
    (is (str/includes? text "# gordian subgraph — gordian"))
    (is (str/includes? text "## Members"))
    (is (str/includes? text "## Internal"))
    (is (str/includes? text "## Boundary"))
    (is (str/includes? text "## Internal Conceptual Pairs"))
    (is (str/includes? text "[act=8.8]"))))

(deftest format-communities-test
  (let [text (str/join "\n" (sut/format-communities fx/communities-data))]
    (is (str/includes? text "gordian communities — combined"))
    (is (str/includes? text "SUMMARY"))
    (is (str/includes? text "communities: 2"))
    (is (str/includes? text "dominant terms: state, event"))
    (is (str/includes? text "bridges: b"))))

(deftest format-communities-md-test
  (let [text (str/join "\n" (sut/format-communities-md fx/communities-data))]
    (is (str/includes? text "# gordian communities — combined"))
    (is (str/includes? text "## Summary"))
    (is (str/includes? text "## Community 1"))
    (is (str/includes? text "`state`"))
    (is (str/includes? text "### Members"))))

(deftest format-tests-test
  (let [text (str/join "\n" (sut/format-tests fx/tests-data))]
    (is (str/includes? text "gordian tests"))
    (is (str/includes? text "SUMMARY"))
    (is (str/includes? text "INVARIANTS"))
    (is (str/includes? text "CORE COVERAGE"))
    (is (str/includes? text "TEST NAMESPACES"))
    (is (str/includes? text "role=executable"))
    (is (str/includes? text "style=integration-ish"))
    (is (str/includes? text "FINDINGS"))
    (is (str/includes? text "● HIGH"))))

(deftest format-tests-md-test
  (let [text (str/join "\n" (sut/format-tests-md fx/tests-data))]
    (is (str/includes? text "# gordian tests"))
    (is (str/includes? text "## Summary"))
    (is (str/includes? text "## Invariants"))
    (is (str/includes? text "## Core Coverage"))
    (is (str/includes? text "## Test Namespaces"))
    (is (str/includes? text "`integration-ish`"))
    (is (str/includes? text "## Findings"))
    (is (str/includes? text "production namespace depends on test namespace"))))

(deftest format-complexity-test
  (let [lines (sut/format-complexity fx/cyclomatic-data)
        text  (str/join "\n" lines)
        unit-rows (filter #(and (str/includes? % "[arity") (str/includes? % "█")) lines)
        bar-cols  (map #(.indexOf % "█") unit-rows)]
    (is (str/includes? text "gordian complexity"))
    (is (str/includes? text "SUMMARY"))
    (is (str/includes? text "metrics: cyclomatic-complexity, lines-of-code"))
    (is (str/includes? text "namespaces: 2"))
    (is (str/includes? text "units: 3"))
    (is (str/includes? text "bar metric: cc"))
    (is (str/includes? text "sample.core/branchy [arity 1] (cc=3, loc=8)"))
    (is (str/includes? text "UNITS"))
    (is (str/includes? text "unit"))
    (is (str/includes? text "risk"))
    (is (not (str/includes? text "decisions")))
    (is (str/includes? text "loc"))
    (is (str/includes? text "NAMESPACE ROLLUP"))
    (is (str/includes? text "PROJECT ROLLUP"))
    (is (str/includes? text "total-loc"))
    (is (str/includes? text "max-loc"))
    (is (str/includes? text "███"))
    (is (apply = bar-cols))))

(deftest format-complexity-bar-metric-test
  (let [data (assoc fx/cyclomatic-data
                    :options {:sort :ns :bar :loc :mins nil}
                    :bar-metric :loc)
        lines (sut/format-complexity data)
        helper-row (first (filter #(and (str/includes? % "sample.util/helper")
                                        (str/includes? % "█"))
                                  lines))
        branchy-row (first (filter #(and (str/includes? % "sample.core/branchy")
                                         (str/includes? % "█"))
                                   lines))
        bar-count (fn [s] (count (re-seq #"█" s)))]
    (is (str/includes? (str/join "\n" lines) "bar metric: loc"))
    (is (> (bar-count branchy-row)
           (bar-count helper-row)))))

(deftest format-complexity-md-test
  (let [text (str/join "\n" (sut/format-complexity-md fx/cyclomatic-data))]
    (is (str/includes? text "# gordian complexity"))
    (is (str/includes? text "## Summary"))
    (is (str/includes? text "| Metric | Value |"))
    (is (str/includes? text "| Bar metric | `cc` |"))
    (is (str/includes? text "`sample.core/branchy [arity 1]` (cc=3, loc=8)"))
    (is (str/includes? text "## Units"))
    (is (str/includes? text "| Unit | CC | Risk | LOC |"))
    (is (not (str/includes? text "Decisions")))
    (is (str/includes? text "| Namespace | Units | Total CC | Avg CC | Max CC | Total LOC | Avg LOC | Max LOC |"))
    (is (str/includes? text "## Project rollup"))))

(deftest format-local-test
  (let [lines (sut/format-local fx/local-data)
        text  (str/join "\n" lines)
        unit-rows (filter #(and (str/includes? % "[arity") (str/includes? % "█")) lines)
        bar-cols  (map #(.indexOf % "█") unit-rows)]
    (is (str/includes? text "gordian local"))
    (is (str/includes? text "SUMMARY"))
    (is (str/includes? text "total lcc: 10.7"))
    (is (str/includes? text "total basis: normalized burdens"))
    (is (str/includes? text "bar metric: total"))
    (is (str/includes? text "sample.core/branchy [arity 1] (total=5.6)"))
    (is (str/includes? text "UNITS"))
    (is (str/includes? text "flow"))
    (is (str/includes? text "abst"))
    (is (str/includes? text "findings: abstraction-oscillation, working-set-overload"))
    (is (str/includes? text "NAMESPACE ROLLUP"))
    (is (str/includes? text "PROJECT ROLLUP"))
    (is (apply = bar-cols))))

(deftest format-local-bar-metric-test
  (let [data (assoc fx/local-data
                    :options {:sort :ns :bar :working-set :mins nil}
                    :bar-metric :working-set)
        lines (sut/format-local data)
        branchy-row (first (filter #(and (str/includes? % "sample.core/branchy")
                                         (str/includes? % "█"))
                                   lines))
        simple-row (first (filter #(and (str/includes? % "sample.core/simple")
                                        (str/includes? % "█"))
                                  lines))
        bar-count (fn [s] (count (re-seq #"█" s)))]
    (is (str/includes? (str/join "\n" lines) "bar metric: working-set"))
    (is (> (bar-count branchy-row)
           (bar-count simple-row)))))

(deftest format-local-md-test
  (let [text (str/join "\n" (sut/format-local-md fx/local-data))]
    (is (str/includes? text "# gordian local"))
    (is (str/includes? text "## Summary"))
    (is (str/includes? text "| Total LCC | 10.7 |"))
    (is (str/includes? text "| Total basis | `normalized burdens` |"))
    (is (str/includes? text "| Bar metric | `total` |"))
    (is (str/includes? text "`sample.core/branchy [arity 1]` | 5.6 |"))
    (is (str/includes? text "working-set-overload"))
    (is (str/includes? text "## Namespace rollup"))
    (is (str/includes? text "## Project rollup"))))
