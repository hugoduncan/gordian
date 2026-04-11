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

(defn stem
  "Reduce a lowercase English word to an approximate stem.
  Rules are applied in order of specificity (longest suffix first) so that
  'nesses' is tried before 'ness', etc.  Not a full Porter stemmer — targeted
  at the patterns that appear in Clojure identifier names and docstrings."
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
  Removes English function words (stop words) and tokens shorter than 2 chars."
  [s]
  (when s
    (->> (str/split (str/lower-case (str s)) #"[^a-zA-Z0-9]+")
         (remove #(< (count %) 2))
         (remove stop-words)
         (map stem)
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

;;; ── pairs ─────────────────────────────────────────────────────────────────

(defn- structural-edge?
  "True if there is a directed edge in either direction between a and b."
  [graph a b]
  (or (contains? (get graph a #{}) b)
      (contains? (get graph b #{}) a)))

(defn conceptual-pairs
  "Compute all namespace pairs whose conceptual similarity meets threshold.
  Returns a vector of maps sorted by :sim desc:
    {:ns-a sym :ns-b sym :sim float
     :structural-edge? bool :shared-terms [term]}
  tfidf     — {ns → {term → weight}} from build-tfidf
  graph     — {ns → #{dep-ns}} structural dependency graph
  threshold — minimum cosine similarity to include (default 0.30)
  n-terms   — number of shared terms to include per pair (default 3)"
  ([tfidf graph] (conceptual-pairs tfidf graph 0.30 3))
  ([tfidf graph threshold] (conceptual-pairs tfidf graph threshold 3))
  ([tfidf graph threshold n-terms]
   (let [nss (vec (keys tfidf))]
     (->> (for [i (range (count nss))
                j (range (inc i) (count nss))
                :let [a   (nss i)
                      b   (nss j)
                      va  (get tfidf a)
                      vb  (get tfidf b)
                      sim (cosine-sim va vb)]
                :when (>= sim threshold)]
            {:ns-a             a
             :ns-b             b
             :sim              sim
             :structural-edge? (structural-edge? graph a b)
             :shared-terms     (coupling-terms va vb n-terms)})
          (sort-by :sim >)
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
