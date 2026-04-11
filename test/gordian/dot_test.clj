(ns gordian.dot-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [gordian.dot :as sut]))

;;; Hand-crafted fixture — tests dot/generate in isolation.
;;; The :graph key is required by generate for edge rendering.
(def fixture-report
  {:src-dirs         ["resources/fixture"]
   :propagation-cost (/ 3.0 9.0)
   :cycles           []
   :graph            {'alpha #{}
                      'beta  '#{alpha}
                      'gamma '#{alpha beta}}
   :nodes [{:ns 'gamma :reach (/ 2.0 3) :fan-in 0.0
             :ca 0 :ce 2 :instability 1.0  :role :peripheral}
            {:ns 'beta  :reach (/ 1.0 3) :fan-in (/ 1.0 3)
             :ca 1 :ce 1 :instability 0.5  :role :shared}
            {:ns 'alpha :reach 0.0       :fan-in (/ 2.0 3)
             :ca 2 :ce 0 :instability 0.0  :role :core}]})

;;; ── generate structure ───────────────────────────────────────────────────

(deftest generate-structure-test
  (testing "starts with digraph declaration"
    (is (str/includes? (sut/generate fixture-report) "digraph gordian {")))

  (testing "ends with closing brace"
    (is (str/ends-with? (str/trim (sut/generate fixture-report)) "}")))

  (testing "includes rankdir"
    (is (str/includes? (sut/generate fixture-report) "rankdir")))

  (testing "src-dirs appear in header comment"
    (is (str/includes? (sut/generate fixture-report) "resources/fixture")))

  (testing "empty graph produces valid skeleton"
    (let [dot (sut/generate {:src-dirs ["x"] :graph {} :nodes []})]
      (is (str/includes? dot "digraph gordian {"))
      (is (str/ends-with? (str/trim dot) "}")))))

;;; ── nodes ────────────────────────────────────────────────────────────────

(deftest generate-nodes-test
  (let [dot (sut/generate fixture-report)]
    (testing "all fixture namespaces appear as DOT nodes"
      (doseq [ns-name ["alpha" "beta" "gamma"]]
        (is (str/includes? dot (str "\"" ns-name "\"")))))

    (testing "node includes instability value"
      (is (str/includes? dot "I=1.00"))
      (is (str/includes? dot "I=0.50"))
      (is (str/includes? dot "I=0.00")))

    (testing "node includes role"
      (is (str/includes? dot "core"))
      (is (str/includes? dot "peripheral")))

    (testing "node has fillcolor attribute"
      (is (str/includes? dot "fillcolor")))))

;;; ── role colours ─────────────────────────────────────────────────────────

(deftest role-color-test
  (let [lines (str/split-lines (sut/generate fixture-report))]
    (testing "core node gets green fill"
      (let [line (first (filter #(and (str/includes? % "\"alpha\"")
                                      (str/includes? % "fillcolor")) lines))]
        (is (str/includes? line "#a8d8a8"))))

    (testing "peripheral node gets red fill"
      (let [line (first (filter #(and (str/includes? % "\"gamma\"")
                                      (str/includes? % "fillcolor")) lines))]
        (is (str/includes? line "#ffb3b3"))))))

;;; ── edges ────────────────────────────────────────────────────────────────

(deftest generate-edges-test
  (let [dot (sut/generate fixture-report)]
    (testing "beta→alpha edge present"
      (is (str/includes? dot "\"beta\" -> \"alpha\"")))

    (testing "gamma→alpha edge present"
      (is (str/includes? dot "\"gamma\" -> \"alpha\"")))

    (testing "gamma→beta edge present"
      (is (str/includes? dot "\"gamma\" -> \"beta\"")))

    (testing "no self-edges for acyclic fixture"
      (is (not (str/includes? dot "\"alpha\" -> \"alpha\""))))))

(deftest external-dep-not-an-edge-test
  (let [report {:src-dirs ["src"]
                :graph    {'A '#{ext-lib}}
                :nodes    [{:ns 'A :role :peripheral :instability 1.0}]}
        dot    (sut/generate report)]
    (testing "external dep produces no edge"
      (is (not (str/includes? dot "ext-lib"))))))


