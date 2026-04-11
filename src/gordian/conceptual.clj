(ns gordian.conceptual
  (:require [clojure.string :as str]))

(def ^:private def-syms
  "Top-level forms whose second element is a name we want to mine."
  #{'defn 'defn- 'def 'def- 'defmulti 'defmethod
    'defprotocol 'defrecord 'deftype 'defmacro})

;;; ── stemming ──────────────────────────────────────────────────────────────

(defn- strip-suffix
  "Return the stem of word after removing suffix, provided the stem would be
  at least min-len characters.  Returns nil otherwise."
  [word suffix min-len]
  (when (and (str/ends-with? word suffix)
             (>= (- (count word) (count suffix)) min-len))
    (subs word 0 (- (count word) (count suffix)))))

(defn- dedup-final
  "Collapse a trailing doubled consonant so that the stemmer can match
  roots across -ing/-ed/-er variants: 'scann' → 'scan', 'coupl' → 'coupl'."
  [s]
  (let [n (count s)]
    (if (and (>= n 2)
             (= (nth s (- n 1)) (nth s (- n 2)))
             (not (#{\a \e \i \o \u} (nth s (- n 1)))))
      (subs s 0 (dec n))
      s)))

(defn- stem*
  "Implementation — see `stem`."
  [word]
  (or
   (strip-suffix word "nesses" 3)              ; emptiness → empti
   (strip-suffix word "ness"   3)              ; randomness → random
   (strip-suffix word "ments"  3)              ; measurements → measur
   (strip-suffix word "ment"   3)              ; measurement → measur
   (strip-suffix word "ables"  3)              ; reachables → reach (rare)
   (strip-suffix word "able"   3)              ; reachable → reach
   (strip-suffix word "ibles"  3)
   (strip-suffix word "ible"   3)              ; compatible → compat
   (some-> (strip-suffix word "ings" 3) dedup-final)  ; couplings → coupl
   (some-> (strip-suffix word "ing"  3) dedup-final)  ; scanning → scan
   (some-> (strip-suffix word "ies"  2) (str "y"))    ; dependencies → dependency
   (some-> (strip-suffix word "ers"  3) dedup-final)  ; scanners → scan
   (some-> (strip-suffix word "ed"   3) dedup-final)  ; scanned → scan
   (some-> (strip-suffix word "er"   3) dedup-final)  ; scanner → scan
   (strip-suffix word "ly"     3)              ; directly → direct, transitively → transitivel
   ;; -es only for sibilant stems (processes → process, indexes → index).
   ;; A stem ending in 'c/l/r/etc' means the 'e' belongs to the root
   ;; (namespaces = namespace+s, not namespac+es) — fall through to -s in that case.
   (when-let [s (strip-suffix word "es" 4)]
     (when (or (str/ends-with? s "ss")
               (str/ends-with? s "x")
               (str/ends-with? s "z"))
       s))
   (when-let [s (strip-suffix word "s" 3)]     ; returns → return, deps → dep
     (when-not (or (str/ends-with? word "ss")  ; class, process — protected
                   (str/ends-with? word "us")  ; status — protected
                   (str/ends-with? word "is")) ; analysis — protected
       s))
   word))

(def stem
  "Reduce a lowercase English word to an approximate stem.
  Rules are applied in order of specificity (longest suffix first) so that
  'nesses' is tried before 'ness', etc.  Not a full Porter stemmer — targeted
  at the patterns that appear in Clojure identifier names and docstrings.
  Memoized: the same token is stemmed at most once per process."
  (memoize stem*))

;;; ── tokenization ──────────────────────────────────────────────────────────

(def ^:private stop-words
  "English function words that are never domain vocabulary.
  IDF alone does not suppress these adequately in small corpora (≤20 ns)."
  #{"a" "an" "the" "of" "to" "in" "for" "on" "at" "by" "from" "with"
    "as" "into" "via" "and" "or" "but" "if" "when" "not" "nor" "so"
    "it" "its" "they" "their" "this" "that" "these" "those" "which"
    "is" "are" "was" "were" "be" "been" "has" "have" "had"
    "do" "does" "did" "can" "will" "would" "should" "could" "may"
    "there" "both" "all" "each" "only" "also" "any" "no" "own"
    "then" "than" "same" "how" "what" "where" "here" "more" "such"})

(defn tokenize
  "Split a string or symbol into lowercase terms.
  Splits on any non-alphanumeric character — handles kebab-case, dots,
  slashes, backticks, braces, markdown punctuation, and all other noise.
  Removes English function words (stop words) and tokens shorter than 2 chars.
  Uses a single transducer pass — no intermediate lazy sequences."
  [s]
  (when s
    (into []
          (comp (remove #(< (count %) 2))
                (remove stop-words)
                (map stem)
                (remove #(< (count %) 2)))
          (str/split (str/lower-case (str s)) #"[^a-zA-Z0-9]+"))))

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

(defn cosine-sim
  "Cosine similarity between two TF-IDF weight maps {term → float}.
  Returns a value in [0.0, 1.0].  Returns 0.0 when either vector is empty."
  [va vb]
  (let [dot   (reduce-kv (fn [s t w] (+ s (* w (get vb t 0.0)))) 0.0 va)
        mag-a (Math/sqrt (reduce-kv (fn [s _ w] (+ s (* w w))) 0.0 va))
        mag-b (Math/sqrt (reduce-kv (fn [s _ w] (+ s (* w w))) 0.0 vb))]
    (if (zero? (* mag-a mag-b)) 0.0 (/ dot (* mag-a mag-b)))))

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
         chunk-size (pair-chunk-size (count candidates))]
     (->> (partition-all chunk-size candidates)
          (pmap #(eval-candidates unit graph threshold n-terms %))
          (into [] cat)          ; eager flatten — avoids lazy apply-concat on main thread
          (sort-by :score >)
          vec))))

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
