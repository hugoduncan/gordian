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
              (str (tag "div" {:class "label"} "Blocks")
                   (tag "div" {:class "value"} (:block-count summary))))
         (tag "div" {:class "card"}
              (str (tag "div" {:class "label"} "Singleton blocks")
                   (tag "div" {:class "value"} (:singleton-block-count summary))))
         (tag "div" {:class "card"}
              (str (tag "div" {:class "label"} "Largest block")
                   (tag "div" {:class "value"} (:largest-block-size summary))))
         (tag "div" {:class "card"}
              (str (tag "div" {:class "label"} "Inter-block edges")
                   (tag "div" {:class "value"} (:inter-block-edge-count summary))))
         (tag "div" {:class "card"}
              (str (tag "div" {:class "label"} "Density")
                   (tag "div" {:class "value"} (format "%.4f" (double (:density summary))))))])))

(defn- block-anchor-id [id]
  (str "block-B" id))

(defn- block-label
  [{:keys [id size members]}]
  (if (= size 1)
    (str "B" id " · " (first members))
    (str "B" id " · " (first members) " +" (dec size))))

(defn- block-row
  [{:keys [id size density members] :as block}]
  (tag "tr"
       (str (tag "td"
                 (if (< 1 size)
                   (tag "a" {:href (str "#" (block-anchor-id id))
                             :title (str "Jump to Block B" id)}
                        (block-label block))
                   (block-label block)))
            (tag "td" size)
            (tag "td" (format "%.2f" (double density)))
            (tag "td" (str/join ", " (map str members))))))

(defn block-table
  [blocks]
  (tag "table" {:class "block-table"}
       (str (tag "thead"
                 (tag "tr"
                      (str (tag "th" "Block")
                           (tag "th" "Size")
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
  (let [ids         (mapv :id blocks)
        block-by-id (into {} (map (juxt :id identity) blocks))
        lookup      (edge-lookup edges)
        header-cell (fn [id]
                      (let [{:keys [size members] :as block} (get block-by-id id)]
                        (tag "th"
                             {:title (str "B" id ": size=" size
                                          ", members=" (str/join ", " (map str members)))}
                             (block-label block))))
        body-cell   (fn [row-id col-id]
                      (if (= row-id col-id)
                        (tag "td" {:class "diag"} "")
                        (let [edge-count (get lookup [row-id col-id] 0)]
                          (if (pos? edge-count)
                            (tag "td"
                                 {:class (str "edge " (edge-intensity-class edge-count))
                                  :title (str "B" row-id " -> B" col-id
                                              ": " edge-count " edge"
                                              (when (not= 1 edge-count) "s"))}
                                 edge-count)
                            (tag "td" {:class "empty"} "")))))]
    (tag "div" {:class "matrix-scroll"}
         (tag "table" {:class "dsm-matrix"}
              (str
               (tag "thead"
                    (tag "tr"
                         (str (tag "th" "")
                              (join-html (map header-cell ids)))))
               (tag "tbody"
                    (join-html
                     (map (fn [row-id]
                            (let [{:keys [size members] :as block} (get block-by-id row-id)]
                              (tag "tr"
                                   (str (tag "th"
                                             {:title (str "B" row-id ": size=" size
                                                          ", members=" (str/join ", " (map str members)))}
                                             (block-label block))
                                        (join-html (map (fn [col-id] (body-cell row-id col-id)) ids))))))
                          ids))))))))

(defn mini-matrix
  [{:keys [members internal-edges]}]
  (let [size     (count members)
        edge-set (set internal-edges)
        idxs     (range size)]
    (tag "table" {:class "mini-matrix"}
         (str
          (tag "thead"
               (tag "tr"
                    (str (tag "th" "")
                         (join-html (map (fn [i] (tag "th" i)) idxs)))))
          (tag "tbody"
               (join-html
                (map (fn [row]
                       (tag "tr"
                            (str (tag "th" row)
                                 (join-html
                                  (map (fn [col]
                                         (cond
                                           (= row col) (tag "td" {:class "diag"} "")
                                           (contains? edge-set [row col])
                                           (tag "td" {:class "edge edge-1"} "X")
                                           :else (tag "td" {:class "empty"} "")))
                                       idxs)))))
                     idxs)))))))

(defn block-detail-section
  [{:keys [id size members internal-edge-count density] :as detail}]
  (tag "details" {:class "scc-detail"
                  :id (block-anchor-id id)}
       (str
        (tag "summary"
             (str "Block B" id " — " size " namespaces, density "
                  (format "%.2f" (double density))))
        (tag "div" {:class "scc-body"}
             (str (tag "p" (str "Members: " (str/join ", " (map str members))))
                  (tag "p" (str "Internal edges: " internal-edge-count))
                  (mini-matrix detail))))))

(declare block-details-section)

(def css
  "Embedded CSS for self-contained DSM HTML reports."
  (str
   "body{font-family:-apple-system,BlinkMacSystemFont,Segoe UI,sans-serif;margin:24px;color:#1f2937;}"
   ".page{max-width:1200px;margin:0 auto;}"
   ".summary-cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px;margin:20px 0;}"
   ".card{border:1px solid #d1d5db;border-radius:8px;padding:12px;background:#f9fafb;}"
   ".label{font-size:12px;color:#6b7280;text-transform:uppercase;}"
   ".value{font-size:24px;font-weight:700;margin-top:4px;}"
   ".matrix-scroll{overflow-x:auto;margin:16px 0;}"
   "table{border-collapse:collapse;}"
   "th,td{border:1px solid #d1d5db;padding:6px 8px;text-align:center;}"
   "th{background:#f3f4f6;}"
   ".diag{background:#e5e7eb;}"
   ".empty{background:#ffffff;}"
   ".edge{color:#111827;font-weight:600;}"
   ".edge-1{background:#dbeafe;}"
   ".edge-2{background:#93c5fd;}"
   ".edge-3{background:#60a5fa;}"
   ".edge-4plus{background:#2563eb;color:#ffffff;}"
   ".block-table,.edge-table,.dsm-matrix,.mini-matrix{margin:12px 0;}"
   ".scc-detail{margin:12px 0;border:1px solid #d1d5db;border-radius:8px;padding:8px;background:#f9fafb;}"
   ".scc-details-empty{margin:12px 0;padding:12px;border:1px dashed #d1d5db;border-radius:8px;background:#fafafa;color:#4b5563;}"
   ".scc-body{margin-top:8px;}"
   "code{background:#f3f4f6;padding:2px 4px;border-radius:4px;}"))

(defn dsm-html
  [{:keys [src-dirs summary blocks edges details ordering]}]
  (let [details-section (if (seq details)
                          (block-details-section details)
                          (tag "section" {:class "scc-details-empty"}
                               (str (tag "h2" "Block Details")
                                    (tag "p" "No multi-namespace blocks."))))]
    (page
     "Gordian DSM"
     (tag "main" {:class "page"}
          (str
           (tag "style" css)
           (tag "header" {:class "page-header"}
                (str (tag "h1" "Gordian DSM")
                     (tag "p" (str "Source: " (tag "code" (str/join " " src-dirs))))
                     (tag "p" (str "Ordering: " (tag "code" (name (:strategy ordering)))))
                     (tag "p" (str "Alpha: " (tag "code" (format "%.1f" (double (:alpha ordering))))))))
           (tag "section"
                (str (tag "h2" "Summary")
                     (summary-cards summary)))
           (tag "section"
                (str (tag "h2" "Block DSM")
                     (collapsed-matrix blocks edges)))
           (tag "section"
                (str (tag "h2" "Blocks")
                     (block-table blocks)))
           (tag "section"
                (str (tag "h2" "Inter-block Dependencies")
                     (edge-table edges)))
           details-section)))))

(defn block-details-section
  [details]
  (when (seq details)
    (tag "section" {:class "scc-details"}
         (str (tag "h2" "Block Details")
              (join-html (map block-detail-section details))))))
