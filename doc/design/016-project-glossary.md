# 016 — Project Vocabulary / Glossary Extraction

## Problem

Gordian already performs conceptual analysis over namespace names, top-level
symbol names, and docstrings. That analysis is currently used to detect
conceptual coupling between namespaces and to summarize communities with
representative terms.

But users also need a different kind of conceptual aid:
- what vocabulary defines this codebase?
- which terms are central to its domain rather than just its implementation?
- which words would help a new reader understand the system quickly?
- which terms deserve a glossary entry in architecture docs or AI context?

Today Gordian can surface pair-local evidence (`:shared-terms`) and
community-local summaries (`:dominant-terms`), but it does not produce a
project-level vocabulary model.

This means the same useful conceptual material is repeatedly computed but not
synthesized into a first-class artifact.

## Goal

Add a first-class glossary / vocabulary view:

```bash
gordian glossary
```

that derives a ranked project vocabulary from existing conceptual-analysis
artifacts.

The command should answer:
- which terms are most characteristic of this project?
- which terms are architecturally important because they connect modules?
- which terms appear to be family/prefix naming noise rather than real domain language?
- which namespaces and communities provide evidence for each term?

The result should support:
- human glossary generation
- architectural onboarding
- AI session orientation
- documentation seeding
- vocabulary drift detection in future work

---

## Scope

### In scope
- new `glossary` subcommand
- project-level term extraction and ranking
- glossary candidate scoring from existing conceptual-analysis signals
- family-noise suppression in glossary ranking
- per-term evidence from namespaces, conceptual pairs, and communities
- text, markdown, EDN, and JSON output
- glossary summaries suitable for humans and downstream tooling

### Out of scope
- natural-language definitions generated from source text
- LLM-generated glossary prose
- synonym merging / ontology construction
- phrase extraction beyond single-token terms
- historical glossary drift / compare mode
- automatic doc updates or README mutation

---

## Design principle

Gordian should treat glossary extraction as a **derived conceptual view**, not
as a separate parsing subsystem.

That means:
- reuse the existing conceptual pipeline wherever possible
- prefer developer-authored vocabulary already mined by Gordian
- rank terms using architectural evidence, not raw lexical frequency alone
- distinguish genuine domain terms from structural naming noise

This keeps glossary generation explainable, deterministic, and aligned with the
rest of Gordian's architectural model.

---

## Vocabulary sources

Glossary candidates should be derived from multiple existing conceptual sources.
No single source is sufficient on its own.

### 1. Namespace-local extracted terms

From the existing conceptual extraction pipeline:
- namespace names
- top-level def names
- docstrings

This is the broadest source of project vocabulary.

Strengths:
- high coverage
- based on intentional developer-authored names
- available for every analyzed namespace

Weaknesses:
- includes generic implementation verbs (`build`, `format`, `parse`)
- includes namespace-family noise
- does not by itself distinguish central from incidental terms

### 2. Conceptual pair evidence

From conceptual pairs:
- `:shared-terms`
- when available, family annotation such as `:independent-terms` and `:family-terms`

This is a stronger signal than raw extraction because the terms have already
survived TF-IDF weighting and pair similarity thresholding.

Strengths:
- architecturally meaningful
- highlights terms that connect multiple namespaces
- naturally explains why a term matters

Weaknesses:
- only covers terms that participate in reported conceptual similarity
- misses distinctive but highly local terms

### 3. Community summaries

From communities:
- internal conceptual evidence
- `:dominant-terms`

This captures subsystem-level vocabulary.

Strengths:
- reveals subdomain language
- naturally groups terms into architectural regions

Weaknesses:
- depends on community analysis being enabled / meaningful
- can flatten term specificity within a large community

### 4. Family-noise classification

From same-family conceptual-pair annotation:
- `:same-family?`
- `:family-terms`
- `:independent-terms`

This is not a source of candidate terms so much as a source of term quality
signals.

It helps answer:
- is this term mostly a family-prefix artifact?
- does it carry conceptual signal beyond namespace organization?

---

## What counts as a glossary-worthy term?

A term is glossary-worthy when it is at least one of:
- widely used across the project
- strongly distinctive in one or a few important namespaces
- repeatedly used as conceptual evidence between namespaces
- representative of a community / subsystem
- clearly part of the project's domain or architectural language

A term is less glossary-worthy when it is primarily:
- a generic implementation verb
- a stop-word-like programming convenience token
- a namespace-family prefix artifact
- a project name token with little explanatory value

This leads to a ranking model rather than a binary filter.

---

## Command

### New subcommand

```bash
gordian glossary [dirs...] [options]
```

Examples:

```bash
gordian glossary
gordian glossary .
gordian glossary . --top 30
gordian glossary . --markdown
gordian glossary . --min-score 2.0
```

### Flags

```text
--top <n>           maximum number of glossary entries to show
--min-score <f>     minimum glossary score required for inclusion
--json              JSON output
--edn               EDN output
--markdown          Markdown output
```

### Defaults
- conceptual analysis enabled automatically
- `--top 25`
- no hard minimum score beyond internal filtering

### Relationship to other commands
- `glossary` is project-level, not namespace-level
- `communities` explains subsystem groupings
- `glossary` explains project vocabulary that emerges from those groupings

---

## Output shape

Canonical machine-readable shape:

```clojure
{:gordian/command :glossary
 :entries
 [{:term              "coupling"
   :score             8.4
   :kind              :architectural
   :quality           :strong
   :namespace-count   5
   :pair-count        7
   :community-count   2
   :same-family-noise? false
   :evidence
   {:namespaces       [gordian.aggregate gordian.conceptual gordian.compare]
    :pairs            [{:ns-a gordian.aggregate
                        :ns-b gordian.close
                        :score 0.31}
                       {:ns-a gordian.compare
                        :ns-b gordian.gate
                        :score 0.24}]
    :communities      [{:id 2 :size 4}]
    :related-terms    ["change" "conceptual" "structural"]}}
  ...]

 :summary
 {:entry-count        25
  :suppressed-count   18
  :source-counts
  {:namespace-terms   241
   :pair-terms         58
   :community-terms    17}
  :kind-counts
  {:architectural 11
   :domain         8
   :implementation 6}}

 :filters
 {:top                25
  :min-score          nil}}
```

### Design notes
- `:term` is the displayed glossary term
- `:score` is the aggregated ranking score
- `:kind` is a coarse classification for readers, not a strict ontology
- `:quality` is a human-facing confidence / usefulness label
- evidence is explicit so the glossary is explainable, not magical
- suppressed terms are counted in summary for transparency even if not shown

---

## Entry model

Each glossary entry represents a single normalized project term.

### Required fields
- `:term`
- `:score`
- `:namespace-count`
- `:pair-count`
- `:community-count`
- `:evidence`

### Optional descriptive fields
- `:kind`
- `:quality`
- `:same-family-noise?`
- `:notes`

### `:kind`

`kind` is a soft interpretation layer for presentation. Suggested values:
- `:domain` — project-specific language about the problem space
- `:architectural` — structural or analysis concepts central to the tool
- `:implementation` — useful but lower-level technical vocabulary
- `:noise` — mostly family-prefix or generic naming signal

This classification is heuristic and should remain lightweight.

### `:quality`

Human-facing usefulness bucket:
- `:strong`
- `:medium`
- `:weak`

This is derived from the ranking score and quality penalties, not authored by
users.

---

## Ranking model

Glossary ranking should combine several independent signals. The key design
choice is that **architectural evidence matters more than raw repetition**.

### Positive signals

#### Namespace breadth
How many namespaces contain the term.

Rationale:
- terms used broadly across the project are likely part of shared vocabulary

#### Distinctiveness
How strongly the term stands out within one or more namespaces.

Rationale:
- highly distinctive terms may deserve a glossary entry even when not broad

#### Pair evidence frequency
How often the term appears in conceptual-pair evidence.

Rationale:
- terms that repeatedly explain conceptual similarity are architecturally meaningful

#### Pair evidence strength
The cumulative or average score of conceptual pairs that mention the term.

Rationale:
- a term appearing in strong conceptual links should rank above a term only
  appearing in marginal links

#### Community presence
How many communities expose the term as a dominant or repeated internal term.

Rationale:
- subsystem language is useful glossary material even if not project-global

#### Cross-context recurrence
Whether the term appears in multiple evidence contexts:
- namespace extraction
- conceptual pairs
- communities

Rationale:
- recurrence across evidence layers is a strong quality signal

### Negative signals

#### Family-prefix noise
If a term appears predominantly as a family token and rarely as an
independent conceptual term, it should be downgraded sharply.

Rationale:
- these terms explain naming conventions more than system concepts

#### Generic implementation language
Terms like `build`, `format`, `load`, `parse`, `result`, `report` may remain
useful but should not dominate the glossary simply due to frequency.

Rationale:
- glossary should foreground project meaning, not boilerplate code verbs

#### Project-name trivialities
The project name itself or ubiquitous module prefix tokens should usually be
suppressed or strongly downgraded.

Rationale:
- they rarely help explain the system

---

## Ranking outcome

The system should produce a single numeric score per term, but the exact
formula is intentionally an implementation detail.

The design requirement is only that the score respects these priorities:

1. repeated strong conceptual evidence > raw local frequency
2. independent terms > family-prefix terms
3. architecturally explanatory terms > generic implementation verbs
4. cross-community / cross-namespace recurrence > isolated repetition

This preserves room for later calibration without changing the user-facing
contract.

---

## Noise suppression

Glossary extraction should inherit the lessons from family-noise suppression in
conceptual coupling.

### Rule 1: prefer independent terms
When pair annotations provide `:independent-terms`, those should be preferred
for glossary evidence over raw `:shared-terms`.

### Rule 2: downgrade family-only terms
If a term appears mostly as `:family-terms` and rarely or never as an
independent term, classify it as low-value or suppress it.

### Rule 3: keep organizational vocabulary available but not dominant
Some family-derived terms may still be useful for orientation. They should not
necessarily vanish entirely, but they should not outrank stronger conceptual
vocabulary.

---

## Surface form vs normalized term

A glossary has a human-facing requirement that differs from conceptual
similarity.

Conceptual analysis benefits from normalization and stemming.
Glossary presentation benefits from readable surface forms.

Therefore glossary design should distinguish:
- **normalized term** — internal key used for aggregation
- **display term** — human-facing label shown in output

### Requirement
Glossary output should prefer readable display terms where possible rather than
raw stems.

Example:
- normalized: `"coupl"`
- display: `"coupling"`

### Why this matters
A glossary is a documentation artifact. Even if stemming improves matching, the
presented term should look like natural project language.

### v1 simplification
If reliable display-form recovery is not yet available, Gordian may initially
present normalized terms, but this should be treated as a quality compromise,
not the desired long-term UX.

---

## Evidence model

Each term should remain explainable through explicit evidence.

### Namespace evidence
Which namespaces contain the term.

Usefulness:
- tells the reader where the term lives
- helps distinguish broad vocabulary from local jargon

### Pair evidence
Which conceptual pairs use the term as shared evidence.

Usefulness:
- shows why the term matters architecturally
- grounds ranking in existing Gordian signals

### Community evidence
Which communities or subsystem summaries feature the term.

Usefulness:
- shows subdomain relevance
- provides clustering context

### Related terms
Terms that often co-occur with the term in the same evidence contexts.

Usefulness:
- supports glossary browsing
- hints at concept neighborhoods

Related terms are secondary evidence, not primary ranking input.

---

## Presentation

### Text output

Text output should emphasize the top-ranked terms and a compact summary of why
those terms matter.

Example shape:

```text
GLOSSARY

1. coupling        score=8.4  namespaces=5  pairs=7  communities=2
   kind: architectural
   related: change, conceptual, structural
   evidence: gordian.aggregate, gordian.conceptual, gordian.compare

2. baseline        score=6.1  namespaces=3  pairs=2  communities=1
   kind: domain
   related: gate, compare, threshold
```

### Markdown output

Markdown should render glossary entries as headings or table rows, with enough
supporting evidence to be pasted into docs or PR comments.

### EDN/JSON output

Machine-readable output should preserve the full evidence structure and all
scores / counts.

---

## Interaction with existing commands

### Conceptual analysis
`glossary` depends on conceptual extraction and should auto-enable the
necessary conceptual pipeline.

### Diagnose
Glossary does not report findings. It reports vocabulary.

However, diagnose findings may later reference glossary terms for improved
explanation.

### Explain / explain-pair
Future integration may allow:
- showing which glossary terms characterize a namespace
- showing which glossary terms dominate a pair or subsystem

### Communities
Glossary should reuse community vocabulary when available, but remain useful
without requiring communities output to be displayed.

---

## UX expectations

A good glossary should feel:
- stable across runs on unchanged code
- deterministic
- interpretable
- useful to someone unfamiliar with the project
- better than a raw word-frequency dump

Users should be able to answer:
- “what concepts define this codebase?”
- “which terms should I know before reading the code?”
- “what architectural language keeps recurring?”

---

## Future extensions

Not part of v1, but compatible with this design:

### Multi-word phrases
Examples:
- propagation cost
- hidden coupling
- baseline workflow

### Generated definitions
Use evidence contexts to draft a one-sentence glossary definition.

### Namespace-local glossary
Show the top terms for a namespace or subgraph.

### Glossary diff / drift
Compare glossary snapshots across refs or releases.

### Doc integration
Emit markdown ready for inclusion in README or architecture docs.

### Ontology / term families
Relate terms such as:
- coupling
- conceptual coupling
- change coupling
- structural coupling

---

## Open questions

### 1. Should glossary depend only on reported conceptual pairs?
Using only reported pairs is simple and explainable, but may miss useful
terms that are distinctive without crossing the threshold into pair evidence.

Recommendation:
- glossary should use both namespace-level conceptual extraction and pair-level
  evidence
- pair evidence should rank terms strongly, not define the entire candidate set

### 2. Should generic implementation verbs be suppressed or merely downgraded?
Full suppression risks hiding important project mechanics.

Recommendation:
- downgrade by default
- preserve if they have strong pair/community evidence

### 3. Should project-name tokens ever appear?
Recommendation:
- default to suppression or severe downgrade unless they carry real evidence
  beyond module naming

### 4. Should glossary classify terms by domain vs architecture?
Recommendation:
- yes, but softly and heuristically
- classification should aid reading, not become a brittle taxonomy

---

## Acceptance criteria

The design is successful when:
- Gordian can emit a ranked glossary of project terms
- the top entries are meaningfully better than raw term frequency
- family-prefix naming noise does not dominate the output
- each term has explicit evidence for why it appears
- output is deterministic and stable
- the result is useful for onboarding and documentation seeding

---

## Summary

Gordian already computes much of the conceptual material needed for a project
vocabulary model. The missing piece is synthesis.

`gordian glossary` should therefore be implemented as a derived conceptual
view that:
- reuses namespace term extraction
- prioritizes conceptual-pair and community evidence
- suppresses family-prefix noise
- presents a ranked, explainable vocabulary for humans and tools

This turns Gordian's existing conceptual analysis from a coupling detector into
an architectural language extractor.