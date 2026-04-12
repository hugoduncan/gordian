(ns gordian.html
  (:require [clojure.string :as str]))

(defn escape-html
  "Escape a string for safe HTML text/attribute rendering."
  [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")
      (str/replace "'" "&#39;")))

(defn join-html
  "Concatenate HTML fragments in order."
  [xs]
  (apply str xs))

(defn tag
  "Render an HTML element string.
  Attrs are emitted in deterministic key order."
  ([tag-name body] (tag tag-name {} body))
  ([tag-name attrs body]
   (let [attr-str (->> attrs
                       (sort-by (comp str key))
                       (map (fn [[k v]]
                              (str " " (name k) "=\"" (escape-html v) "\"")))
                       join-html)]
     (str "<" tag-name attr-str ">"
          body
          "</" tag-name ">"))))

(defn page
  "Render a complete HTML document."
  [title body]
  (str "<!DOCTYPE html>"
       (tag "html" {:lang "en"}
            (str (tag "head"
                      (str (tag "meta" {:charset "utf-8"} "")
                           (tag "meta" {:name "viewport"
                                        :content "width=device-width, initial-scale=1"} "")
                           (tag "title" (escape-html title))))
                 (tag "body" body)))))
