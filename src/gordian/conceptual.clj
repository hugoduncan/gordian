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

;;; ── similarity ────────────────────────────────────────────────────────────

(defn cosine-sim
  "Cosine similarity between two TF-IDF weight maps {term → float}.
  Returns a value in [0.0, 1.0].  Returns 0.0 when either vector is empty."
  [va vb]
  (let [dot   (reduce-kv (fn [s t w] (+ s (* w (get vb t 0.0)))) 0.0 va)
        mag-a (Math/sqrt (reduce-kv (fn [s _ w] (+ s (* w w))) 0.0 va))
        mag-b (Math/sqrt (reduce-kv (fn [s _ w] (+ s (* w w))) 0.0 vb))]
    (if (zero? (* mag-a mag-b)) 0.0 (/ dot (* mag-a mag-b)))))

(defn coupling-terms
  "Return the top-n terms driving the similarity between two TF-IDF vectors.
  Ranks shared terms by their joint contribution (weight-a × weight-b) desc.
  Terms absent from either vector contribute zero and are omitted."
  [va vb n]
  (->> (keys va)
       (keep (fn [t]
               (when-let [wb (get vb t)]
                 [t (* (get va t) wb)])))
       (sort-by second >)
       (take n)
       (mapv first)))

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
