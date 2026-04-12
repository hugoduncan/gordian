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
