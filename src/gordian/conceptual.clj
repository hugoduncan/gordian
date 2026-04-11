(ns gordian.conceptual
  (:require [clojure.string :as str]))

(def ^:private def-syms
  "Top-level forms whose second element is a name we want to mine."
  #{'defn 'defn- 'def 'def- 'defmulti 'defmethod
    'defprotocol 'defrecord 'deftype 'defmacro})

;;; ── tokenization ──────────────────────────────────────────────────────────

(defn tokenize
  "Split a string or symbol into lowercase terms.
  Splits on hyphens, underscores, dots, slashes, and whitespace.
  Drops tokens shorter than 2 characters."
  [s]
  (when s
    (->> (str/split (str/lower-case (str s)) #"[-_./\s]+")
         (remove #(< (count %) 2)))))

;;; ── term extraction ───────────────────────────────────────────────────────

(defn- docstring-tokens [form]
  (when (string? (nth form 2 nil))
    (mapcat tokenize (str/split (nth form 2) #"\s+"))))

(defn extract-terms
  "Extract conceptual terms from a namespace's parsed forms.
  Mines:
    - namespace name tokens
    - top-level def'd symbol name tokens (defn, def, defmulti, defrecord, …)
    - docstring tokens (first string after the def'd name)
  Returns a flat [term] sequence; repetition is intentional — TF uses frequency."
  [ns-sym forms]
  (->> forms
       (mapcat (fn [form]
                 (when (and (seq? form)
                            (def-syms (first form))
                            (symbol? (second form)))
                   (concat (tokenize (second form))
                           (docstring-tokens form)))))
       (concat (tokenize ns-sym))
       vec))
