(ns gordian.text-test
  "Tests for gordian.text — stemming and tokenization.
  Canonical test location. gordian.conceptual-test also exercises these
  via delegation for backward compatibility."
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.text :as sut]))

(deftest tokenize-basic-test
  (testing "kebab-case symbol"
    (is (= ["propagation" "cost"] (sut/tokenize 'propagation-cost))))
  (testing "dot-separated namespace"
    (is (= ["gordian" "aggregate"] (sut/tokenize 'gordian.aggregate))))
  (testing "lowercases"
    (is (= ["build" "report"] (sut/tokenize 'Build-Report))))
  (testing "drops single-char tokens"
    (is (= ["ca" "ce"] (sut/tokenize 'ca-ce))))
  (testing "nil → nil"
    (is (nil? (sut/tokenize nil)))))

(deftest tokenize-stop-words-test
  (testing "english function words removed"
    (is (= [] (sut/tokenize "the of to and or but"))))
  (testing "domain words kept"
    (is (= ["propagation" "cost"] (sut/tokenize "propagation-cost")))))

(deftest tokenize-stemming-test
  (testing "stems applied"
    (is (= ["return"] (sut/tokenize "returns")))
    (is (= ["scan"]   (sut/tokenize "scanning")))
    (is (= ["reach"]  (sut/tokenize "reachable")))))

(deftest stem-test
  (testing "pass-through"
    (is (= "scan" (sut/stem "scan")))
    (is (= "graph" (sut/stem "graph"))))
  (testing "-ing + dedup"
    (is (= "scan" (sut/stem "scanning")))
    (is (= "coupl" (sut/stem "coupling"))))
  (testing "-ed + dedup"
    (is (= "scan" (sut/stem "scanned"))))
  (testing "-able"
    (is (= "reach" (sut/stem "reachable"))))
  (testing "-es sibilant"
    (is (= "process" (sut/stem "processes")))
    (is (= "namespace" (sut/stem "namespaces"))))
  (testing "protected endings"
    (is (= "class" (sut/stem "class")))
    (is (= "status" (sut/stem "status")))
    (is (= "analysis" (sut/stem "analysis")))))
