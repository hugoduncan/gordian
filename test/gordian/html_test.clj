(ns gordian.html-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.html :as sut]))

(def escape-html #'gordian.html/escape-html)
(def join-html #'gordian.html/join-html)
(def tag #'gordian.html/tag)
(def page #'gordian.html/page)
(def summary-cards #'gordian.html/summary-cards)
(def block-table #'gordian.html/block-table)
(def edge-table #'gordian.html/edge-table)
(def edge-intensity-class #'gordian.html/edge-intensity-class)
(def collapsed-matrix #'gordian.html/collapsed-matrix)
(def mini-matrix #'gordian.html/mini-matrix)
(def block-detail-section #'gordian.html/block-detail-section)
(def block-details-section #'gordian.html/block-details-section)

(deftest escape-html-test
  (testing "escapes five special characters"
    (is (= "&amp;&lt;&gt;&quot;&#39;"
           (escape-html "&<>\"'"))))

  (testing "plain text unchanged"
    (is (= "hello" (escape-html "hello")))))

(deftest join-html-test
  (is (= "<a></a><b></b>"
         (join-html ["<a></a>" "<b></b>"]))))

(deftest tag-test
  (testing "renders element with no attrs"
    (is (= "<div>x</div>"
           (tag "div" "x"))))

  (testing "renders attrs in deterministic order"
    (is (= "<div a=\"1\" b=\"2\">x</div>"
           (tag "div" {:b 2 :a 1} "x"))))

  (testing "escapes attribute values"
    (is (= "<div title=\"a&amp;b\">x</div>"
           (tag "div" {:title "a&b"} "x"))))

  (testing "preserves nested body content"
    (is (= "<div><span>x</span></div>"
           (tag "div" "<span>x</span>")))))

(deftest page-test
  (let [html (page "DSM" "<main>body</main>")]
    (is (.startsWith html "<!DOCTYPE html>"))
    (is (.contains html "<title>DSM</title>"))
    (is (.contains html "<body><main>body</main></body>"))))

(def summary
  {:block-count 12
   :singleton-block-count 2
   :largest-block-size 4
   :inter-block-edge-count 9
   :density 0.1364})

(def ordering {:strategy :dfs-topo :refined? false :alpha 2.0 :beta 0.05 :nodes ['gordian.aggregate 'foo.a 'foo.b 'foo.c]})

(def blocks
  [{:id 0 :size 1 :density 0.0 :members ['gordian.aggregate]}
   {:id 1 :size 3 :density 0.83 :members ['foo.a 'foo.b 'foo.c]}])

(def edges
  [{:from 1 :to 0 :edge-count 2}])

(def details
  [{:id 1
    :members ['foo.a 'foo.b 'foo.c]
    :size 3
    :internal-edges [[0 1] [1 2] [2 0]]
    :internal-edge-count 3
    :density 0.5}])

(deftest summary-cards-test
  (let [html (summary-cards summary)]
    (is (.contains html "Blocks"))
    (is (.contains html ">12<"))
    (is (.contains html "Singleton blocks"))
    (is (.contains html "Largest block"))
    (is (.contains html "Inter-block edges"))
    (is (.contains html "0.1364"))))

(deftest block-table-test
  (let [html (block-table blocks)]
    (is (.contains html "<th>Block</th>"))
    (is (.contains html "B0 · gordian.aggregate"))
    (is (.contains html "B1 · foo.a +2"))
    (is (.contains html "foo.a, foo.b, foo.c"))
    (is (.contains html "href=\"#block-B1\""))
    (is (.contains html "title=\"Jump to Block B1\""))))

(deftest edge-table-test
  (testing "includes headers and counted edges"
    (let [html (edge-table edges)]
      (is (.contains html "<th>From</th>"))
      (is (.contains html "<th>To</th>"))
      (is (.contains html "<th>Edge count</th>"))
      (is (.contains html "B1"))
      (is (.contains html "B0"))
      (is (.contains html ">2<"))))

  (testing "renders empty state"
    (let [html (edge-table [])]
      (is (.contains html "(none)")))))

(deftest edge-intensity-class-test
  (is (= "empty" (edge-intensity-class 0)))
  (is (= "edge-1" (edge-intensity-class 1)))
  (is (= "edge-2" (edge-intensity-class 2)))
  (is (= "edge-3" (edge-intensity-class 3)))
  (is (= "edge-4plus" (edge-intensity-class 4))))

(deftest collapsed-matrix-test
  (let [html (collapsed-matrix blocks edges)]
    (testing "includes row and column block headers with namespace labels"
      (is (.contains html ">B0 · gordian.aggregate<"))
      (is (.contains html ">B1 · foo.a +2<")))

    (testing "header cells include tooltips"
      (is (.contains html "title=\"B1: size=3, members=foo.a, foo.b, foo.c\"")))

    (testing "diagonal cells render with diagonal class"
      (is (.contains html "class=\"diag\"")))

    (testing "empty off-diagonal cells render empty state"
      (is (.contains html "class=\"empty\"")))

    (testing "nonzero cells include edge count text and tooltip"
      (is (.contains html "class=\"edge edge-2\""))
      (is (.contains html ">2<"))
      (is (.contains html "title=\"B1 -&gt; B0: 2 edges\"")))

    (testing "renders deterministically"
      (is (= html (collapsed-matrix blocks edges))))

    (testing "single-block input handled"
      (is (.contains (collapsed-matrix [{:id 0 :size 1 :cyclic? false :members ['a]}] []) ">B0 · a<")))))

(deftest mini-matrix-test
  (let [html (mini-matrix (first details))]
    (is (.contains html "<table"))
    (is (.contains html ">foo.a<"))
    (is (.contains html ">foo.b<"))
    (is (.contains html ">foo.c<"))
    (is (.contains html ">X<"))))

(deftest block-detail-section-test
  (let [html (block-detail-section (first details))]
    (is (.contains html "<details"))
    (is (.contains html "id=\"block-B1\""))
    (is (.contains html "<summary>"))
    (is (.contains html "Block B1"))
    (is (.contains html "foo.a, foo.b, foo.c"))
    (is (.contains html "Internal edges: 3"))))

(deftest block-details-section-test
  (testing "omits empty detail section"
    (is (nil? (block-details-section []))))

  (testing "multiple details preserve order"
    (let [html (block-details-section (conj details
                                            {:id 3 :members ['z.a 'z.b] :size 2
                                             :internal-edges [[0 1] [1 0]]
                                             :internal-edge-count 2 :density 1.0}))]
      (is (< (.indexOf html "Block B1")
             (.indexOf html "Block B3"))))))

(deftest dsm-html-test
  (let [data {:src-dirs ["resources/fixture"]
              :ordering ordering
              :summary summary
              :blocks blocks
              :edges edges
              :details details}
        html (sut/dsm-html data)]
    (is (.startsWith html "<!DOCTYPE html>"))
    (is (.contains html "<title>Gordian DSM</title>"))
    (is (.contains html "Source: <code>resources/fixture</code>"))
    (is (.contains html "Ordering: <code>dfs-topo</code>"))
    (is (.contains html "Alpha: <code>2.0</code>"))
    (is (.contains html "Refined: <code>false</code>"))
    (is (.contains html "<h2>Summary</h2>"))
    (is (.contains html "<h2>Block DSM</h2>"))
    (is (.contains html "<h2>Blocks</h2>"))
    (is (.contains html "<h2>Inter-block Dependencies</h2>"))
    (is (.contains html "Block Details"))
    (is (.contains html "<style>"))))

(deftest dsm-html-no-details-test
  (let [data {:src-dirs ["resources/fixture"]
              :ordering ordering
              :summary (assoc summary :block-count 1 :singleton-block-count 1 :largest-block-size 1)
              :blocks [{:id 0 :size 1 :density 0.0 :members ['a]}]
              :edges []
              :details []}
        html (sut/dsm-html data)]
    (is (.contains html "Block Details"))
    (is (.contains html "No multi-namespace blocks."))))
