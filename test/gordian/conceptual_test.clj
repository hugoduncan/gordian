(ns gordian.conceptual-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.conceptual :as sut]))

;;; ── tokenize ──────────────────────────────────────────────────────────────

(deftest tokenize-test
  (testing "kebab-case symbol splits on hyphens"
    (is (= ["propagation" "cost"] (sut/tokenize 'propagation-cost))))

  (testing "dot-separated namespace splits on dots"
    (is (= ["gordian" "aggregate"] (sut/tokenize 'gordian.aggregate))))

  (testing "slash-separated (qualified symbol) splits on slash"
    (is (= ["some" "fn"] (sut/tokenize 'some/fn))))

  (testing "lowercases all tokens"
    (is (= ["build" "report"] (sut/tokenize 'Build-Report))))

  (testing "drops single-character tokens"
    (is (= ["ca" "ce"] (sut/tokenize 'ca-ce)))
    (is (= ["format"] (sut/tokenize "format-a"))))

  (testing "plain string tokenizes same as symbol"
    (is (= ["propagation" "cost"] (sut/tokenize "propagation-cost"))))

  (testing "whitespace-separated prose"
    (is (= ["return" "the" "cost"] (sut/tokenize "return the cost"))))

  (testing "underscores treated as separators"
    (is (= ["fan" "in"] (sut/tokenize 'fan_in))))

  (testing "nil returns nil"
    (is (nil? (sut/tokenize nil)))))

;;; ── extract-terms ─────────────────────────────────────────────────────────

(deftest extract-terms-test
  (testing "namespace name tokens always present"
    (let [terms (sut/extract-terms 'gordian.scan [])]
      (is (some #{"gordian"} terms))
      (is (some #{"scan"} terms))))

  (testing "defn symbol tokens extracted"
    (let [forms '[(defn propagation-cost [g] 0)]
          terms (sut/extract-terms 'my.ns forms)]
      (is (some #{"propagation"} terms))
      (is (some #{"cost"} terms))))

  (testing "def symbol tokens extracted"
    (let [forms '[(def parse-opts {})]
          terms (sut/extract-terms 'my.ns forms)]
      (is (some #{"parse"} terms))
      (is (some #{"opts"} terms))))

  (testing "defmulti, defrecord, defprotocol tokens extracted"
    (doseq [head '[defmulti defrecord defprotocol defmacro]]
      (let [forms [(list head 'my-concept [])]
            terms (sut/extract-terms 'my.ns forms)]
        (is (some #{"my"} terms)   (str head " should yield 'my'"))
        (is (some #{"concept"} terms) (str head " should yield 'concept'")))))

  (testing "defn- (private) symbol extracted"
    (let [forms '[(defn- build-tfidf [x] x)]
          terms (sut/extract-terms 'my.ns forms)]
      (is (some #{"build"} terms))
      (is (some #{"tfidf"} terms))))

  (testing "docstring tokens extracted"
    (let [forms '[(defn format-report "Return lines for the coupling report." [r] [])]
          terms (sut/extract-terms 'my.ns forms)]
      (is (some #{"return"} terms))
      (is (some #{"lines"} terms))
      (is (some #{"coupling"} terms))
      (is (some #{"report"} terms))))

  (testing "non-def top-level forms are ignored"
    (let [forms '[(comment anything here) (let [x 1] x) [1 2 3]]
          terms (sut/extract-terms 'my.ns forms)]
      ;; only ns tokens present
      (is (= ["my" "ns"] terms))))

  (testing "multiple defs accumulate terms"
    (let [forms '[(defn scan-file [p] nil)
                  (defn scan-dir  [d] nil)]
          terms (sut/extract-terms 'my.ns forms)]
      (is (some #{"scan"} terms))
      (is (some #{"file"} terms))
      (is (some #{"dir"} terms))))

  (testing "repetition preserved — frequency drives TF"
    (let [forms '[(defn scan-one [x] nil)
                  (defn scan-two [x] nil)
                  (defn scan-three [x] nil)]
          terms (sut/extract-terms 'my.ns forms)
          freq  (frequencies terms)]
      (is (= 3 (get freq "scan"))))))
