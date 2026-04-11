(ns gordian.json
  (:require [cheshire.core :as json]))

;;; JSON serialisation of any gordian output map.
;;;
;;; A generic recursive walker coerces Clojure-idiomatic types
;;; (symbols, keywords, sets) to JSON-friendly equivalents.
;;; This avoids maintaining an exhaustive field list per command.

(defn- serialize
  "Recursively coerce Clojure types to JSON-friendly equivalents.
  - symbol    → string
  - keyword   → name string
  - set       → sorted vector (sorted by str for determinism)
  - map       → recurse values, stringify keyword keys
  - seq/vec   → recurse elements
  - number/string/boolean/nil → pass through"
  [x]
  (cond
    (map? x)
    (into {} (map (fn [[k v]] [(if (keyword? k) (name k) (str k))
                               (serialize v)]))
          x)

    (set? x)
    (vec (sort-by str (map serialize x)))

    (sequential? x)
    (mapv serialize x)

    (symbol? x)
    (str x)

    (keyword? x)
    (name x)

    ;; numbers, strings, booleans, nil — pass through
    :else x))

(defn generate
  "Return a pretty-printed JSON string for any gordian output map.
  Strips the internal :graph key before serialising."
  [report]
  (json/generate-string (serialize (dissoc report :graph))
                        {:pretty true}))
