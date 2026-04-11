(ns gordian.json
  (:require [cheshire.core :as json]))

;;; JSON serialisation of the gordian report.
;;;
;;; Clojure-idiomatic types (symbols, keywords, sets) are normalised
;;; to JSON-friendly equivalents before encoding.

(defn- serialize-node
  "Normalise a node map for JSON output."
  [{:keys [ns reach fan-in ca ce instability role] :as node}]
  (cond-> {:ns          (str ns)
           :reach       (double reach)
           :fan-in      (double fan-in)}
    (some? ca)          (assoc :ca ca)
    (some? ce)          (assoc :ce ce)
    (some? instability) (assoc :instability (double instability))
    (some? role)        (assoc :role (name role))))

(defn- serialize-cycle
  "Sort cycle members alphabetically for deterministic output."
  [cycle-set]
  (sort (map str cycle-set)))

(defn generate
  "Return a pretty-printed JSON string for the gordian report map."
  [{:keys [src-dir propagation-cost cycles nodes]}]
  (json/generate-string
   {:src-dir          src-dir
    :propagation-cost (double propagation-cost)
    :cycles           (map serialize-cycle cycles)
    :nodes            (map serialize-node nodes)}
   {:pretty true}))
