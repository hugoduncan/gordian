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

;;; ── TF-IDF ────────────────────────────────────────────────────────────────

(defn term-freqs
  "Return {term → count} for a sequence of terms."
  [terms]
  (frequencies terms))

(defn build-tfidf
  "Convert {ns → [term]} to {ns → {term → tfidf-weight}}.
  TF  = term-count / total-terms-in-namespace
  IDF = log(N / document-frequency)
  Weight = TF × IDF
  Terms absent from a namespace are absent from its map (implicit zero)."
  [ns->terms]
  (let [n  (count ns->terms)
        df (->> (vals ns->terms)            ; document frequency per term
                (mapcat distinct)
                frequencies)]
    (into {}
      (map (fn [[ns-sym terms]]
             (let [tf    (term-freqs terms)
                   total (count terms)]
               [ns-sym
                (into {}
                  (keep (fn [[t cnt]]
                          (let [w (* (/ (double cnt) total)
                                     (Math/log (/ (double n) (get df t 1))))]
                            (when (pos? w) [t w])))
                        tf))]))
           ns->terms))))

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
