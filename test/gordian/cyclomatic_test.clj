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
  (let [tmp (java.io.File/createTempFile "gordian-cyclomatic" ".clj")
        source "(ns sample.core)\n(defn a [] 1)\n(defn b [x] (if x 1 2))\n(defn c\n  ([x] x)\n  ([x y] (cond x y :else 0)))\n(defmethod render :html [x] (when x :ok))\n(def helper (fn\n              ([x] x)\n              ([x y] (or x y))))\n"]
    (spit tmp source)
    (let [file  (assoc (scan/parse-file-all-forms (.getPath tmp)) :origin :src)
          units (sut/analyzable-units file)]
      (is (= 7 (count units)))
      (is (= 2 (count (filter #(= 'c (:var %)) units))))
      (is (= 1 (count (filter #(= :defmethod (:kind %)) units))))
      (is (= 2 (count (filter #(= :def-fn-arity (:kind %)) units))))
      (is (= #{:src} (set (map :origin units))))
      (is (= :html (:dispatch (first (filter #(= :defmethod (:kind %)) units)))))
      (is (every? pos? (map :loc units))))
    (.delete tmp)))

(deftest cc-risk-test
  (is (= {:level :simple :label "Simple, low risk"} (sut/cc-risk 10)))
  (is (= {:level :moderate :label "Moderate complexity, moderate risk"} (sut/cc-risk 11)))
  (is (= {:level :high :label "High complexity, high risk"} (sut/cc-risk 21)))
  (is (= {:level :untestable :label "Untestable, very high risk"} (sut/cc-risk 51))))

(deftest sort-filter-and-truncation-test
  (let [units [{:ns 'b.core :var 'z :kind :defn-arity :arity 1 :dispatch nil :cc 4 :loc 8 :cc-risk (sut/cc-risk 4)}
               {:ns 'a.core :var 'x :kind :defn-arity :arity 1 :dispatch nil :cc 9 :loc 5 :cc-risk (sut/cc-risk 9)}
               {:ns 'a.core :var 'y :kind :defn-arity :arity 1 :dispatch nil :cc 11 :loc 20 :cc-risk (sut/cc-risk 11)}]
        mins  {:cc 9 :loc 5}]
    (is (= ['a.core 'a.core 'b.core] (mapv :ns (sut/sort-units units :ns))))
    (is (= ['y 'x 'z] (mapv :var (sut/sort-units units :cc-risk))))
    (is (= ['y 'z 'x] (mapv :var (sut/sort-units units :loc))))
    (is (= [:cc 10] (sut/parse-min-expression "cc=10")))
    (is (= [:loc 20] (sut/parse-min-expression "loc=20")))
    (is (nil? (sut/parse-min-expression "bogus=10")))
    (is (nil? (sut/parse-min-expression "cc=0")))
    (is (= ['x 'y] (mapv :var (sut/filter-units-by-mins units mins))))
    (is (= 2 (count (sut/truncate-section units 2))))))

(deftest rollup-test
  (let [result (sut/rollup fixture-files)]
    (is (= :complexity (:gordian/command result)))
    (is (= [:cyclomatic-complexity :lines-of-code] (:metrics result)))
    (is (= 3 (count (:units result))))
    (is (every? :loc (:units result)))
    (is (= ['alpha 'beta 'gamma] (mapv :ns (:namespace-rollups result))))
    (is (= 3 (get-in result [:project-rollup :unit-count])))
    (is (= 3 (get-in result [:project-rollup :namespace-count])))
    (is (= 3 (get-in result [:project-rollup :total-cc])))
    (is (pos? (get-in result [:project-rollup :total-loc])))
    (is (= 1 (get-in result [:project-rollup :max-cc])))
    (is (pos? (get-in result [:project-rollup :max-loc])))
    (is (= 'alpha (get-in result [:max-unit :ns])))
    (is (= 'hello (get-in result [:max-unit :var])))
    (is (= {:simple 3 :moderate 0 :high 0 :untestable 0}
           (get-in result [:project-rollup :cc-risk-counts])))))
