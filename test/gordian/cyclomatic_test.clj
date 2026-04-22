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

  (testing "cond counts default branch when present"
    (is (= 4 (sut/arity-complexity '((cond a 1 b 2 :else 3))))))

  (testing "condp counts default branch when present"
    (is (= 4 (sut/arity-complexity '((condp = x :a 1 :b 2 0))))))

  (testing "case counts default branch when present"
    (is (= 4 (sut/arity-complexity '((case x :a 1 :b 2 0))))))

  (testing "cond-> counts condition/form pairs"
    (is (= 3 (sut/arity-complexity '((cond-> x a (assoc :a 1) b (assoc :b 2)))))))

  (testing "looping/iteration forms do not independently increment complexity"
    (is (= 1 (sut/arity-complexity '((while p (inc x))))) )
    (is (= 1 (sut/arity-complexity '((for [x xs] (inc x))))) )
    (is (= 1 (sut/arity-complexity '((doseq [x xs] (inc x))))) ))

  (testing "branches inside looping forms still count normally"
    (is (= 2 (sut/arity-complexity '((while p (if x 1 2)))))))

  (testing "try counts catch clauses"
    (is (= 2 (sut/arity-complexity '((try (inc x) (catch Exception _ 0))))))))

(deftest analyzable-units-test
  (let [file {:file "src/sample.clj"
              :ns 'sample.core
              :origin :src
              :forms '[(ns sample.core)
                       (defn a [] 1)
                       (defn b [x] (if x 1 2))
                       (defn c
                         ([x] x)
                         ([x y] (cond x y :else 0)))
                       (defmethod render :html [x] (when x :ok))
                       (def helper (fn
                                     ([x] x)
                                     ([x y] (or x y))))]}
        units (sut/analyzable-units file)]
    (is (= 7 (count units)))
    (is (= 2 (count (filter #(= 'c (:var %)) units))))
    (is (= 1 (count (filter #(= :defmethod (:kind %)) units))))
    (is (= 2 (count (filter #(= :def-fn-arity (:kind %)) units))))
    (is (= #{:src} (set (map :origin units))))
    (is (= :html (:dispatch (first (filter #(= :defmethod (:kind %)) units)))))))

(deftest cc-risk-test
  (is (= {:level :simple :label "Simple, low risk"} (sut/cc-risk 10)))
  (is (= {:level :moderate :label "Moderate complexity, moderate risk"} (sut/cc-risk 11)))
  (is (= {:level :high :label "High complexity, high risk"} (sut/cc-risk 21)))
  (is (= {:level :untestable :label "Untestable, very high risk"} (sut/cc-risk 51))))

(deftest sort-filter-and-truncation-test
  (let [units [{:ns 'b.core :var 'z :kind :defn-arity :arity 1 :dispatch nil :cc 4 :cc-risk (sut/cc-risk 4)}
               {:ns 'a.core :var 'x :kind :defn-arity :arity 1 :dispatch nil :cc 9 :cc-risk (sut/cc-risk 9)}
               {:ns 'a.core :var 'y :kind :defn-arity :arity 1 :dispatch nil :cc 11 :cc-risk (sut/cc-risk 11)}]
        rollups [{:ns 'a.core :max-cc 9}
                 {:ns 'b.core :max-cc 4}]]
    (is (= ['a.core 'a.core 'b.core] (mapv :ns (sut/sort-units units :ns))))
    (is (= ['y 'x 'z] (mapv :var (sut/sort-units units :cc-risk))))
    (is (= ['x 'y] (mapv :var (sut/filter-by-min-cc units 9))))
    (is (= ['a.core] (mapv :ns (sut/filter-by-min-cc rollups 9))))
    (is (= 2 (count (sut/truncate-section units 2))))))

(deftest rollup-test
  (let [result (sut/rollup fixture-files)]
    (is (= :complexity (:gordian/command result)))
    (is (= :cyclomatic-complexity (:metric result)))
    (is (= 3 (count (:units result))))
    (is (= ['alpha 'beta 'gamma] (mapv :ns (:namespace-rollups result))))
    (is (= 3 (get-in result [:project-rollup :unit-count])))
    (is (= 3 (get-in result [:project-rollup :namespace-count])))
    (is (= 3 (get-in result [:project-rollup :total-cc])))
    (is (= 1 (get-in result [:project-rollup :max-cc])))
    (is (= 'alpha (get-in result [:max-unit :ns])))
    (is (= 'hello (get-in result [:max-unit :var])))
    (is (= {:simple 3 :moderate 0 :high 0 :untestable 0}
           (get-in result [:project-rollup :cc-risk-counts])))))
