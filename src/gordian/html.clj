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

(defn summary-cards
  [summary]
  (tag "section" {:class "summary-cards"}
       (join-html
        [(tag "div" {:class "card"}
              (str (tag "div" {:class "label"} "SCC Blocks")
                   (tag "div" {:class "value"} (:block-count summary))))
         (tag "div" {:class "card"}
              (str (tag "div" {:class "label"} "Cyclic SCCs")
                   (tag "div" {:class "value"} (:cyclic-block-count summary))))
         (tag "div" {:class "card"}
              (str (tag "div" {:class "label"} "Largest SCC")
                   (tag "div" {:class "value"} (:largest-block-size summary))))
         (tag "div" {:class "card"}
              (str (tag "div" {:class "label"} "Inter-block edges")
                   (tag "div" {:class "value"} (:inter-block-edge-count summary))))
         (tag "div" {:class "card"}
              (str (tag "div" {:class "label"} "Density")
                   (tag "div" {:class "value"} (format "%.4f" (double (:density summary))))))])))

(defn- block-row
  [{:keys [id size cyclic? density members]}]
  (tag "tr"
       (str (tag "td" (str "B" id))
            (tag "td" size)
            (tag "td" (if cyclic? "yes" "no"))
            (tag "td" (format "%.2f" (double density)))
            (tag "td" (str/join ", " (map str members))))))

(defn block-table
  [blocks]
  (tag "table" {:class "block-table"}
       (str (tag "thead"
                 (tag "tr"
                      (str (tag "th" "Block")
                           (tag "th" "Size")
                           (tag "th" "Cyclic")
                           (tag "th" "Density")
                           (tag "th" "Members"))))
            (tag "tbody" (join-html (map block-row blocks))))))

(defn- edge-row
  [{:keys [from to edge-count]}]
  (tag "tr"
       (str (tag "td" (str "B" from))
            (tag "td" (str "B" to))
            (tag "td" edge-count))))

(defn edge-table
  [edges]
  (tag "table" {:class "edge-table"}
       (str (tag "thead"
                 (tag "tr"
                      (str (tag "th" "From")
                           (tag "th" "To")
                           (tag "th" "Edge count"))))
            (tag "tbody"
                 (if (seq edges)
                   (join-html (map edge-row edges))
                   (tag "tr"
                        (str (tag "td" {:colspan 3} "(none)"))))))))

(defn edge-intensity-class
  [edge-count]
  (cond
    (<= edge-count 0) "empty"
    (= edge-count 1)  "edge-1"
    (= edge-count 2)  "edge-2"
    (= edge-count 3)  "edge-3"
    :else             "edge-4plus"))

(defn- edge-lookup
  [edges]
  (into {}
        (map (fn [{:keys [from to edge-count]}]
               [[from to] edge-count]))
        edges))

(defn collapsed-matrix
  [blocks edges]
  (let [ids    (mapv :id blocks)
        lookup (edge-lookup edges)]
    (tag "div" {:class "matrix-scroll"}
         (tag "table" {:class "dsm-matrix"}
              (str (tag "thead"
                        (tag "tr"
                             (str (tag "th" "")
                                  (join-html (map (fn [id] (tag "th" (str "B" id))) ids)))))
                   (tag "tbody"
                        (join-html
                         (map (fn [row-id]
                                (tag "tr"
                                     (str (tag "th" (str "B" row-id))
                                          (join-html
                                           (map (fn [col-id]
                                                  (cond
                                                    (= row-id col-id)
                                                    (tag "td" {:class "diag"} "")

                                                    :else
                                                    (let [edge-count (get lookup [row-id col-id] 0)]
                                                      (if (pos? edge-count)
                                                        (tag "td" {:class (str "edge " (edge-intensity-class edge-count))}
                                                             edge-count)
                                                        (tag "td" {:class "empty"} "")))))
                                                ids)))))
                              ids))))))))
