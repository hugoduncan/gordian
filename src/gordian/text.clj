(ns gordian.text
  "Text processing utilities: stemming and tokenization.
  General-purpose — used by conceptual coupling, family detection, etc."
  (:require [clojure.string :as str]))

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
   (strip-suffix word "nesses" 3)
   (strip-suffix word "ness"   3)
   (strip-suffix word "ments"  3)
   (strip-suffix word "ment"   3)
   (strip-suffix word "ables"  3)
   (strip-suffix word "able"   3)
   (strip-suffix word "ibles"  3)
   (strip-suffix word "ible"   3)
   (some-> (strip-suffix word "ings" 3) dedup-final)
   (some-> (strip-suffix word "ing"  3) dedup-final)
   (some-> (strip-suffix word "ies"  2) (str "y"))
   (some-> (strip-suffix word "ers"  3) dedup-final)
   (some-> (strip-suffix word "ed"   3) dedup-final)
   (some-> (strip-suffix word "er"   3) dedup-final)
   (strip-suffix word "ly"     3)
   (when-let [s (strip-suffix word "es" 4)]
     (when (or (str/ends-with? s "ss")
               (str/ends-with? s "x")
               (str/ends-with? s "z"))
       s))
   (when-let [s (strip-suffix word "s" 3)]
     (when-not (or (str/ends-with? word "ss")
                   (str/ends-with? word "us")
                   (str/ends-with? word "is"))
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
  "Split a string or symbol into lowercase stemmed terms.
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
