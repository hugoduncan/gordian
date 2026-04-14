(ns gordian.conceptual
  (:require [clojure.string :as str]
            [gordian.text :as text]))

(def ^:private def-syms
  "Top-level forms whose second element is a name we want to mine."
  #{'defn 'defn- 'def 'def- 'defmulti 'defmethod
    'defprotocol 'defrecord 'deftype 'defmacro})

;;; ── delegated to gordian.text ─────────────────────────────────────────────

(def stem
  "Delegate to gordian.text/stem for backward compatibility."
  text/stem)

(def tokenize
  "Delegate to gordian.text/tokenize for backward compatibility."
  text/tokenize)

;;; ── TF-IDF ────────────────────────────────────────────────────────────────

(defn- term-freqs
  "Return {term → count} for a sequence of terms."
  [terms]
  (frequencies terms))

(defn build-tfidf
  "Convert {ns → [term]} to {ns → {term → tfidf-weight}}.
  TF  = term-count / total-terms-in-namespace
  IDF = log(N / document-frequency)
  Weight = TF × IDF
  Terms absent from a namespace are absent from its map (implicit zero).
  df is computed once sequentially (requires all documents); per-ns weight
  vectors are then computed in parallel (pmap)."
  [ns->terms]
  (let [n  (count ns->terms)
        df (reduce                           ; document frequency per term — no intermediate seq
            (fn [acc terms]
              (reduce (fn [m t] (update m t (fnil inc 0))) acc (distinct terms)))
            {}
            (vals ns->terms))]
    (into {}
          (pmap (fn [[ns-sym terms]]
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

(defn normalize-tfidf
  "Normalize each TF-IDF vector to unit length.
  Returns {ns → {term → normalized-weight}}.
  Cosine similarity of two unit vectors equals their dot product, so
  magnitude computation is eliminated from the O(N²) pair loop.
  Per-ns normalization is independent and runs in parallel (pmap)."
  [tfidf]
  (into {}
        (pmap (fn [[ns v]]
                (let [mag (Math/sqrt (reduce-kv (fn [s _ w] (+ s (* w w))) 0.0 v))]
                  [ns (if (zero? mag)
                        v
                        (into {} (map (fn [[t w]] [t (/ w mag)]) v)))]))
              tfidf)))

;;; ── pairs ─────────────────────────────────────────────────────────────────

(defn- structural-edge?
  "True if there is a directed edge in either direction between a and b."
  [graph a b]
  (or (contains? (get graph a #{}) b)
      (contains? (get graph b #{}) a)))

(defn- dot-and-top-terms
  "Single pass over va: compute dot product and collect top-n shared terms.
  va and vb must be pre-normalized unit vectors (see normalize-tfidf).
  Returns [dot-product top-n-terms-vec].
  Fuses what were previously two separate iterations (cosine-sim + coupling-terms)."
  [va vb n]
  (let [contribs (reduce-kv
                  (fn [acc t wa]
                    (if-let [wb (get vb t)]
                      (conj acc [t (* wa wb)])
                      acc))
                  [] va)
        dot   (transduce (map second) + 0.0 contribs)
        terms (->> contribs (sort-by second >) (take n) (mapv first))]
    [dot terms]))

(defn- pair-chunk-size
  "Target ~200 chunks so each chunk is large enough to amortise pmap
  dispatch overhead (~0.1 ms) while keeping load well balanced.
  Floor of 50 pairs ensures tiny candidate sets don't over-chunk."
  [n-candidates]
  (max 50 (quot n-candidates 200)))

(defn- eval-candidates
  "Evaluate a seq of [a b] candidate pairs against unit-tfidf vectors.
  Returns a *vector* (not a lazy seq) of result maps for pairs meeting threshold.
  Each map has {:ns-a :ns-b :score :kind :conceptual :structural-edge? :shared-terms}.
  Using into [] forces full evaluation inside the pmap future so the work
  runs on a worker thread, not lazily on the calling thread during concat."
  [unit graph threshold n-terms pairs]
  (into []
        (keep (fn [[a b]]
                (let [[sim terms] (dot-and-top-terms (get unit a) (get unit b) n-terms)]
                  (when (>= sim threshold)
                    {:ns-a             a
                     :ns-b             b
                     :score            sim
                     :kind             :conceptual
                     :structural-edge? (structural-edge? graph a b)
                     :shared-terms     terms}))))
        pairs))

(defn conceptual-pairs
  "Compute all namespace pairs whose conceptual similarity meets threshold.
  Returns a vector of maps sorted by :score desc:
    {:ns-a sym :ns-b sym :score float :kind :conceptual
     :structural-edge? bool :shared-terms [term]}
  tfidf     — {ns → {term → weight}} from build-tfidf
  graph     — {ns → #{dep-ns}} structural dependency graph
  threshold — minimum cosine similarity to include (default 0.30)
  n-terms   — number of shared terms to include per pair (default 3)

  Optimisations applied:
  - Vectors pre-normalized to unit length → cosine = dot product (no sqrt per pair).
  - Inverted index (term → #{ns}) prunes pairs with zero dot product before
    any arithmetic: only namespace pairs that share ≥1 term are evaluated.
  - dot product and top-n terms computed in a single fused pass per pair.
  - Candidates partitioned into coarse chunks; pmap dispatches one future per
    chunk rather than one per pair — amortises thread-dispatch overhead for
    the microsecond-scale work of evaluating a single pair."
  ([tfidf graph] (conceptual-pairs tfidf graph 0.30 3))
  ([tfidf graph threshold] (conceptual-pairs tfidf graph threshold 3))
  ([tfidf graph threshold n-terms]
   (let [unit    (normalize-tfidf tfidf)
         ;; inverted index: term → #{ns} (only for ns that have the term)
         t->nss  (reduce-kv
                  (fn [m ns v]
                    (reduce-kv (fn [m2 t _] (update m2 t (fnil conj #{}) ns)) m v))
                  {} unit)
         ;; candidate pairs: ns pairs sharing ≥1 term, deduplicated
         ;; canonical order: str(a) < str(b) so each pair appears once
         candidates (into #{}
                          (mapcat (fn [nss]
                                    (for [a nss b nss
                                          :when (neg? (compare (str a) (str b)))]
                                      [a b]))
                                  (vals t->nss)))
         chunk-size (pair-chunk-size (count candidates))
         pairs      (->> (partition-all chunk-size candidates)
                         (pmap #(eval-candidates unit graph threshold n-terms %))
                         (into [] cat)
                         (sort-by :score >)
                         vec)]
     {:pairs           pairs
      :candidate-count (count candidates)})))

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
  Returns a flat [term] sequence; repetition is intentional — TF uses frequency.
  Uses a single transducer pass — no intermediate lazy sequences."
  [ns-sym forms]
  (into (vec (tokenize ns-sym))
        (comp
         (filter (fn [form]
                   (and (seq? form)
                        (def-syms (first form))
                        (symbol? (second form)))))
         (mapcat (fn [form]
                   (concat (tokenize (second form))
                           (docstring-tokens form)))))
        forms))
