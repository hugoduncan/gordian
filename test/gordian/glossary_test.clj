(ns gordian.glossary-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.glossary :as sut]))

(def ^:private ns->terms
  {'a.core ["coupling" "baseline" "build"]
   'b.util ["coupling" "threshold"]
   'c.main ["build" "report"]})

(def ^:private conceptual-pairs
  [{:ns-a 'a.core :ns-b 'b.util :score 0.40
    :shared-terms ["family" "coupling"]
    :same-family? true
    :family-terms ["family"]
    :independent-terms ["coupling"]}
   {:ns-a 'a.core :ns-b 'c.main :score 0.20
    :shared-terms ["baseline"]}
   {:ns-a 'x.alpha :ns-b 'x.beta :score 0.35
    :shared-terms ["session"]
    :same-family? true
    :family-terms ["session"]
    :independent-terms []}])

(def ^:private communities
  [{:id 1 :size 2 :dominant-terms ["coupling" "threshold"]}
   {:id 2 :size 3 :dominant-terms ["baseline" "gate"]}])

(deftest namespace-term-stats-test
  (let [stats (sut/namespace-term-stats ns->terms)]
    (testing "namespace breadth counted correctly"
      (is (= 2 (:namespace-count (get stats "coupling"))))
      (is (= #{'a.core 'b.util} (:namespaces (get stats "coupling")))))))

(deftest pair-term-stats-test
  (let [stats (sut/pair-term-stats conceptual-pairs)]
    (testing "prefers independent terms over raw shared terms"
      (is (= 1 (:pair-count (get stats "coupling"))))
      (is (= nil (get stats "family"))))

    (testing "family-only terms accumulate family-only count"
      (is (= 1 (:family-only-count (get stats "session")))))))

(deftest community-term-stats-test
  (let [stats (sut/community-term-stats communities)]
    (testing "community evidence increments community count"
      (is (= 1 (:community-count (get stats "coupling"))))
      (is (= [{:id 1 :size 2}] (:communities (get stats "coupling")))))))

(deftest glossary-entries-test
  (let [entries (sut/glossary-entries {:ns->terms ns->terms
                                       :conceptual-pairs conceptual-pairs
                                       :communities communities}
                                      {})
        by-term (into {} (map (juxt :term identity)) entries)]
    (testing "entries are sorted by score descending"
      (is (apply >= (map :score entries))))

    (testing "family-only terms are penalized relative to independent terms"
      (is (> (:score (get by-term "coupling"))
             (:score (get by-term "session")))))

    (testing "entry evidence includes namespaces, pairs, communities, related terms"
      (let [e (get by-term "coupling")]
        (is (= ['a.core 'b.util] (get-in e [:evidence :namespaces])))
        (is (= 1 (count (get-in e [:evidence :pairs]))))
        (is (= [{:id 1 :size 2}] (get-in e [:evidence :communities])))
        (is (vector? (get-in e [:evidence :related-terms])))))

    (testing ":top limits entry count"
      (is (= 2 (count (sut/glossary-entries {:ns->terms ns->terms
                                             :conceptual-pairs conceptual-pairs
                                             :communities communities}
                                            {:top 2})))))

    (testing ":min-score filters low-scoring entries"
      (is (every? #(>= (:score %) 2.0)
                  (sut/glossary-entries {:ns->terms ns->terms
                                         :conceptual-pairs conceptual-pairs
                                         :communities communities}
                                        {:min-score 2.0}))))))

(deftest glossary-report-test
  (let [report (sut/glossary-report {:ns->terms ns->terms
                                     :conceptual-pairs conceptual-pairs
                                     :communities communities}
                                    {:top 3 :min-score 1.0})]
    (testing "canonical report shape"
      (is (= :glossary (:gordian/command report)))
      (is (vector? (:entries report)))
      (is (= {:top 3 :min-score 1.0} (:filters report))))

    (testing "summary present"
      (is (= 3 (get-in report [:summary :entry-count])))
      (is (map? (get-in report [:summary :source-counts])))
      (is (map? (get-in report [:summary :kind-counts]))))))