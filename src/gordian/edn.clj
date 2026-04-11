(ns gordian.edn
  (:require [clojure.pprint :as pp]))

;;; EDN serialisation of the gordian report.
;;;
;;; Unlike JSON, EDN preserves Clojure-native types: namespace names are
;;; symbols, roles are keywords, cycles are sets.  No normalisation needed.

(defn- public-report
  "Strip internal implementation keys from the report before serialising."
  [report]
  (dissoc report :graph))

(defn generate
  "Return a pretty-printed EDN string for the gordian report map."
  [report]
  (with-out-str (pp/pprint (public-report report))))
