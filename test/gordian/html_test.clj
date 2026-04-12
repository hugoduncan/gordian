(ns gordian.html-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.html :as sut]))

(deftest escape-html-test
  (testing "escapes five special characters"
    (is (= "&amp;&lt;&gt;&quot;&#39;"
           (sut/escape-html "&<>\"'"))))

  (testing "plain text unchanged"
    (is (= "hello" (sut/escape-html "hello")))))

(deftest join-html-test
  (is (= "<a></a><b></b>"
         (sut/join-html ["<a></a>" "<b></b>"]))))

(deftest tag-test
  (testing "renders element with no attrs"
    (is (= "<div>x</div>"
           (sut/tag "div" "x"))))

  (testing "renders attrs in deterministic order"
    (is (= "<div a=\"1\" b=\"2\">x</div>"
           (sut/tag "div" {:b 2 :a 1} "x"))))

  (testing "escapes attribute values"
    (is (= "<div title=\"a&amp;b\">x</div>"
           (sut/tag "div" {:title "a&b"} "x"))))

  (testing "preserves nested body content"
    (is (= "<div><span>x</span></div>"
           (sut/tag "div" "<span>x</span>")))))

(deftest page-test
  (let [html (sut/page "DSM" "<main>body</main>")]
    (is (.startsWith html "<!DOCTYPE html>"))
    (is (.contains html "<title>DSM</title>"))
    (is (.contains html "<body><main>body</main></body>"))))

(def summary
  {:block-count 12
   :cyclic-block-count 2
   :largest-block-size 4
   :inter-block-edge-count 9
   :density 0.1364})

(def blocks
  [{:id 0 :size 1 :cyclic? false :density 0.0 :members ['gordian.aggregate]}
   {:id 1 :size 3 :cyclic? true :density 0.83 :members ['foo.a 'foo.b 'foo.c]}])

(def edges
  [{:from 1 :to 0 :edge-count 2}])

(deftest summary-cards-test
  (let [html (sut/summary-cards summary)]
    (is (.contains html "SCC Blocks"))
    (is (.contains html ">12<"))
    (is (.contains html "Cyclic SCCs"))
    (is (.contains html "Largest SCC"))
    (is (.contains html "Inter-block edges"))
    (is (.contains html "0.1364"))))

(deftest block-table-test
  (let [html (sut/block-table blocks)]
    (is (.contains html "<th>Block</th>"))
    (is (.contains html "B0"))
    (is (.contains html "foo.a, foo.b, foo.c"))
    (is (.contains html "yes"))))

(deftest edge-table-test
  (testing "includes headers and counted edges"
    (let [html (sut/edge-table edges)]
      (is (.contains html "<th>From</th>"))
      (is (.contains html "<th>To</th>"))
      (is (.contains html "<th>Edge count</th>"))
      (is (.contains html "B1"))
      (is (.contains html "B0"))
      (is (.contains html ">2<"))))

  (testing "renders empty state"
    (let [html (sut/edge-table [])]
      (is (.contains html "(none)")))))
