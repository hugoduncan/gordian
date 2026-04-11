(ns gordian.main-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.main :as sut]))

(deftest parse-args-test
  (testing "valid src-dir"
    (is (= {:src-dir "src/"} (sut/parse-args ["src/"]))))

  (testing "extra args are ignored"
    (is (= {:src-dir "src/"} (sut/parse-args ["src/" "--extra"]))))

  (testing "no args yields error"
    (is (contains? (sut/parse-args []) :error)))

  (testing "nil args yields error"
    (is (contains? (sut/parse-args nil) :error))))
