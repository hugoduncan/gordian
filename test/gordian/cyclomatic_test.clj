(ns gordian.cyclomatic-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.cyclomatic :as sut]
            [gordian.scan :as scan]))

(def fixture-files
  (->> ["resources/fixture/alpha.clj"
        "resources/fixture/beta.clj"
        "resources/fixture/gamma.clj"]
       (map scan/parse-file-all-forms)
       vec))

(deftest arity-complexity-test
  (testing "straight-line body has base complexity 1"
    (is (= 1 (sut/arity-complexity '((+ 1 2))))))

  (testing "if adds one branch"
    (is (= 2 (sut/arity-complexity '((if x 1 2))))))

  (testing "boolean chains add decisions"
    (is (= 3 (sut/arity-complexity '((and a b c)))))
    (is (= 2 (sut/arity-complexity '((or a b))))))

  (testing "cond counts non-default branches"
    (is (= 3 (sut/arity-complexity '((cond a 1 b 2 :else 3))))))

  (testing "case counts explicit branches, ignores default"
    (is (= 3 (sut/arity-complexity '((case x :a 1 :b 2 0))))))

  (testing "try counts catch clauses"
    (is (= 2 (sut/arity-complexity '((try (inc x) (catch Exception _ 0))))))))

(deftest function-complexities-test
  (let [file {:file "src/sample.clj"
              :ns 'sample.core
              :forms '[(ns sample.core)
                       (defn a [] 1)
                       (defn b [x] (if x 1 2))
                       (defn c
                         ([x] x)
                         ([x y] (cond x y :else 0))) ]}
        result (sut/function-complexities file)]
    (is (= 'sample.core (:ns result)))
    (is (= 3 (count (:functions result))))
    (is (= '{a 1 b 2 c 2}
           (into {} (map (juxt :name :complexity) (:functions result)))))
    (is (= [1 2] (:arity-complexities (first (filter #(= 'c (:name %)) (:functions result))))))))

(deftest rollup-test
  (let [result (sut/rollup fixture-files)]
    (is (= :cyclomatic (:gordian/command result)))
    (is (= 3 (get-in result [:summary :namespace-count])))
    (is (= 3 (get-in result [:summary :function-count])))
    (is (= 3 (get-in result [:summary :total-complexity])))
    (is (= 1 (get-in result [:summary :max-complexity])))
    (is (= 'alpha/hello (get-in result [:max-function :qualified-name])))
    (is (= ['alpha 'beta 'gamma] (mapv :ns (:namespaces result))))))
