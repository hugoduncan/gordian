(ns gordian.conceptual-test
  (:require [clojure.test :refer [deftest is testing]]
            [gordian.conceptual :as sut]))

;;; ── tokenize ──────────────────────────────────────────────────────────────

(deftest tokenize-test
  (testing "kebab-case symbol splits on hyphens"
    (is (= ["propagation" "cost"] (sut/tokenize 'propagation-cost))))

  (testing "dot-separated namespace splits on dots"
    (is (= ["gordian" "aggregate"] (sut/tokenize 'gordian.aggregate))))

  (testing "slash-separated (qualified symbol) splits on slash"
    (is (= ["some" "fn"] (sut/tokenize 'some/fn))))

  (testing "lowercases all tokens"
    (is (= ["build" "report"] (sut/tokenize 'Build-Report))))

  (testing "drops single-character tokens"
    (is (= ["ca" "ce"] (sut/tokenize 'ca-ce)))
    (is (= ["format"] (sut/tokenize "format-a"))))

  (testing "plain string tokenizes same as symbol"
    (is (= ["propagation" "cost"] (sut/tokenize "propagation-cost"))))

  (testing "whitespace-separated prose — stop words removed"
    (is (= ["return" "cost"] (sut/tokenize "return the cost"))))

  (testing "underscores treated as separators; 'in' is a stop word"
    (is (= ["fan"] (sut/tokenize 'fan_in))))

  (testing "nil returns nil"
    (is (nil? (sut/tokenize nil))))

  (testing "english function words removed"
    (is (= [] (sut/tokenize "the of to and or but")))
    (is (= [] (sut/tokenize "is are was were be been")))
    (is (= [] (sut/tokenize "it its this that which"))))

  (testing "domain words are not stop-worded"
    (is (= ["propagation" "cost"] (sut/tokenize "propagation-cost")))
    (is (= ["report"] (sut/tokenize "report")))
    (is (= ["scan"] (sut/tokenize "scan"))))

  (testing "backtick-wrapped token — markdown code style"
    (is (= ["graph"] (sut/tokenize "`graph`"))))

  (testing "brace-contaminated token from docstring"
    (is (= ["node"] (sut/tokenize "{node}"))))

  (testing "mixed clojure syntax noise stripped"
    (is (= ["return" "dep"] (sut/tokenize "return: #{dep}"))))

  (testing "ellipsis and punctuation stripped"
    (is (= ["ns" "deps"] (sut/tokenize "{ns→#{deps}}")))))

;;; ── term-freqs ────────────────────────────────────────────────────────────

(deftest term-freqs-test
  (testing "counts occurrences correctly"
    (is (= {"scan" 3 "file" 1 "dir" 2}
           (sut/term-freqs ["scan" "file" "scan" "dir" "scan" "dir"]))))

  (testing "empty sequence → empty map"
    (is (= {} (sut/term-freqs []))))

  (testing "single term"
    (is (= {"cost" 1} (sut/term-freqs ["cost"])))))

;;; ── build-tfidf ───────────────────────────────────────────────────────────

(def ^:private small-corpus
  "Three namespaces with controlled term overlap for TF-IDF verification.
  'cost' appears in ns-a and ns-b (common).
  'propagation' and 'scan' appear only in ns-a (specific).
  'report' appears only in ns-b (specific).
  'format' appears in ns-b and ns-c (common)."
  {'ns-a ["cost" "cost" "propagation" "scan"]
   'ns-b ["cost" "format" "report" "report"]
   'ns-c ["format" "format" "output" "output"]})

(deftest build-tfidf-test
  (let [tfidf (sut/build-tfidf small-corpus)]

    (testing "returns a map keyed by ns syms"
      (is (= '#{ns-a ns-b ns-c} (set (keys tfidf)))))

    (testing "each namespace value is a map of term → weight"
      (doseq [[_ tv] tfidf]
        (is (map? tv))
        (is (every? string? (keys tv)))
        (is (every? number? (vals tv)))))

    (testing "all weights are positive"
      (doseq [[_ tv] tfidf
              [_ w]  tv]
        (is (pos? w))))

    (testing "term absent from a namespace is absent from its map"
      (is (nil? (get-in tfidf ['ns-a "report"])))
      (is (nil? (get-in tfidf ['ns-c "cost"]))))

    (testing "unique terms weight more than shared terms within same namespace"
      ;; In ns-a: 'propagation' (df=1) should outweigh 'cost' (df=2) despite same TF
      (let [tv (get tfidf 'ns-a)]
        (is (> (get tv "propagation") (get tv "cost")))))

    (testing "higher frequency term weighs more than lower frequency within same namespace"
      ;; In ns-b: 'report' appears twice, 'cost' once — but 'report' is also unique (df=1)
      ;; vs 'cost' df=2, so 'report' wins on both TF and IDF
      (let [tv (get tfidf 'ns-b)]
        (is (> (get tv "report") (get tv "cost")))))

    (testing "single-namespace corpus — IDF is log(1/1)=0, all weights zero → empty maps"
      (let [solo (sut/build-tfidf {'only-ns ["alpha" "beta"]})]
        (is (= {} (get solo 'only-ns)))))))

;;; ── cosine-sim ────────────────────────────────────────────────────────────

(deftest cosine-sim-test
  (testing "identical vectors → 1.0"
    (let [v {"cost" 0.5 "scan" 0.3}]
      (is (< (Math/abs (- 1.0 (sut/cosine-sim v v))) 1e-9))))

  (testing "orthogonal vectors (no shared terms) → 0.0"
    (is (= 0.0 (sut/cosine-sim {"cost" 1.0} {"scan" 1.0}))))

  (testing "partial overlap → value in (0, 1)"
    ;; {a 1 b 1} vs {a 1 c 1}: dot=1, mag-a=√2, mag-b=√2 → 0.5
    (let [sim (sut/cosine-sim {"aa" 1.0 "bb" 1.0} {"aa" 1.0 "cc" 1.0})]
      (is (< 0.0 sim 1.0))
      (is (< (Math/abs (- 0.5 sim)) 1e-9))))

  (testing "empty vector → 0.0"
    (is (= 0.0 (sut/cosine-sim {} {"cost" 1.0})))
    (is (= 0.0 (sut/cosine-sim {"cost" 1.0} {})))
    (is (= 0.0 (sut/cosine-sim {} {})))))

;;; ── coupling-terms ────────────────────────────────────────────────────────

(deftest coupling-terms-test
  (let [va {"cost" 0.8 "scan" 0.5 "report" 0.3}
        vb {"cost" 0.7 "scan" 0.4 "output" 0.9}]

    (testing "returns only shared terms"
      (let [terms (set (sut/coupling-terms va vb 10))]
        (is (contains? terms "cost"))
        (is (contains? terms "scan"))
        (is (not (contains? terms "report")))   ; only in va
        (is (not (contains? terms "output")))))  ; only in vb

    (testing "ordered by joint contribution desc"
      ;; cost: 0.8×0.7=0.56, scan: 0.5×0.4=0.20 → cost first
      (let [terms (sut/coupling-terms va vb 10)]
        (is (= "cost" (first terms)))
        (is (= "scan" (second terms)))))

    (testing "n limits result count"
      (is (= 1 (count (sut/coupling-terms va vb 1))))
      (is (= 2 (count (sut/coupling-terms va vb 2)))))

    (testing "n larger than shared terms → returns all shared terms"
      (is (= 2 (count (sut/coupling-terms va vb 100)))))

    (testing "no shared terms → empty vector"
      (is (= [] (sut/coupling-terms {"aa" 1.0} {"bb" 1.0} 3))))))

;;; ── conceptual-pairs ──────────────────────────────────────────────────────

(def ^:private small-tfidf
  (sut/build-tfidf small-corpus))

(def ^:private small-graph
  "ns-a → ns-b edge only; ns-b ↔ ns-c has no structural edge."
  {'ns-a #{'ns-b}
   'ns-b #{}
   'ns-c #{}})

(deftest conceptual-pairs-test
  (let [pairs (sut/conceptual-pairs small-tfidf small-graph 0.01 3)]

    (testing "returns a vector"
      (is (vector? pairs)))

    (testing "each entry has required keys"
      (doseq [p pairs]
        (is (contains? p :ns-a))
        (is (contains? p :ns-b))
        (is (contains? p :sim))
        (is (contains? p :structural-edge?))
        (is (contains? p :shared-terms))))

    (testing "sorted by sim descending"
      (let [sims (map :sim pairs)]
        (is (= sims (sort > sims)))))

    (testing "all returned sims are >= threshold"
      (doseq [{:keys [sim]} pairs]
        (is (>= sim 0.01))))

    (testing "ns-a ↔ ns-b has structural edge"
      (let [p (first (filter #(= #{(:ns-a %) (:ns-b %)} #{'ns-a 'ns-b}) pairs))]
        (is (true? (:structural-edge? p)))))

    (testing "ns-b ↔ ns-c has no structural edge"
      (let [p (first (filter #(= #{(:ns-a %) (:ns-b %)} #{'ns-b 'ns-c}) pairs))]
        (when p   ; may be above threshold
          (is (false? (:structural-edge? p))))))

    (testing "ns-a ↔ ns-c (zero overlap) excluded even at low threshold"
      (let [ac (filter #(= #{(:ns-a %) (:ns-b %)} #{'ns-a 'ns-c}) pairs)]
        (is (empty? ac))))

    (testing "shared-terms vector present for each pair"
      (doseq [{:keys [shared-terms]} pairs]
        (is (vector? shared-terms))))

    (testing "ns-a ↔ ns-b shared term is 'cost'"
      (let [p (first (filter #(= #{(:ns-a %) (:ns-b %)} #{'ns-a 'ns-b}) pairs))]
        (is (= ["cost"] (:shared-terms p))))))

  (testing "threshold filters out all pairs"
    (is (empty? (sut/conceptual-pairs small-tfidf small-graph 1.0 3))))

  (testing "empty tfidf → empty result"
    (is (empty? (sut/conceptual-pairs {} {} 0.01 3)))))

;;; ── extract-terms ─────────────────────────────────────────────────────────

(deftest extract-terms-test
  (testing "namespace name tokens always present"
    (let [terms (sut/extract-terms 'gordian.scan [])]
      (is (some #{"gordian"} terms))
      (is (some #{"scan"} terms))))

  (testing "defn symbol tokens extracted"
    (let [forms '[(defn propagation-cost [g] 0)]
          terms (sut/extract-terms 'my.ns forms)]
      (is (some #{"propagation"} terms))
      (is (some #{"cost"} terms))))

  (testing "def symbol tokens extracted"
    (let [forms '[(def parse-opts {})]
          terms (sut/extract-terms 'my.ns forms)]
      (is (some #{"parse"} terms))
      (is (some #{"opts"} terms))))

  (testing "defmulti, defrecord, defprotocol tokens extracted"
    (doseq [head '[defmulti defrecord defprotocol defmacro]]
      (let [forms [(list head 'my-concept [])]
            terms (sut/extract-terms 'my.ns forms)]
        (is (some #{"my"} terms)   (str head " should yield 'my'"))
        (is (some #{"concept"} terms) (str head " should yield 'concept'")))))

  (testing "defn- (private) symbol extracted"
    (let [forms '[(defn- build-tfidf [x] x)]
          terms (sut/extract-terms 'my.ns forms)]
      (is (some #{"build"} terms))
      (is (some #{"tfidf"} terms))))

  (testing "docstring tokens extracted"
    (let [forms '[(defn format-report "Return lines for the coupling report." [r] [])]
          terms (sut/extract-terms 'my.ns forms)]
      (is (some #{"return"} terms))
      (is (some #{"lines"} terms))
      (is (some #{"coupling"} terms))
      (is (some #{"report"} terms))))

  (testing "non-def top-level forms are ignored"
    (let [forms '[(comment anything here) (let [x 1] x) [1 2 3]]
          terms (sut/extract-terms 'my.ns forms)]
      ;; only ns tokens present
      (is (= ["my" "ns"] terms))))

  (testing "multiple defs accumulate terms"
    (let [forms '[(defn scan-file [p] nil)
                  (defn scan-dir  [d] nil)]
          terms (sut/extract-terms 'my.ns forms)]
      (is (some #{"scan"} terms))
      (is (some #{"file"} terms))
      (is (some #{"dir"} terms))))

  (testing "repetition preserved — frequency drives TF"
    (let [forms '[(defn scan-one [x] nil)
                  (defn scan-two [x] nil)
                  (defn scan-three [x] nil)]
          terms (sut/extract-terms 'my.ns forms)
          freq  (frequencies terms)]
      (is (= 3 (get freq "scan"))))))
