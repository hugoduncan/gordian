(ns gordian.output.summary-counts-test
  (:require [clojure.test :refer [deftest is]]
            [gordian.output.common :as common]))

(deftest local-summary-counts-prefers-project-rollup
  (let [project-rollup {:namespace-count 3 :unit-count 8}
        units [{:ns 'a.core} {:ns 'a.core} {:ns 'b.core}]]
    (is (= {:namespace-count 3 :unit-count 8}
           (common/local-summary-counts project-rollup units)))))

(deftest local-summary-counts-derives-from-units-when-project-rollup-absent
  (let [units [{:ns 'a.core} {:ns 'a.core} {:ns 'b.core} {:ns 'c.core}]]
    (is (= {:namespace-count 3 :unit-count 4}
           (common/local-summary-counts nil units)))))
