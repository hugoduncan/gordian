# Gordian Output Schema

Version: **0.2.0** · Schema: **1**

All machine-readable output (EDN via `--edn`, JSON via `--json`) is wrapped
in a standard envelope. Human-readable and markdown output are not affected.

---

## Envelope

Every command's output includes these top-level keys:

| Key | Type | Description |
|-----|------|-------------|
| `gordian/version` | string | Gordian version (e.g. `"0.2.0"`) |
| `gordian/schema` | integer | Schema version. Bumped on breaking changes. |
| `gordian/command` | keyword | `analyze`, `diagnose`, `compare`, `gate`, `subgraph`, `communities`, `dsm`, `tests`, `complexity`, `cyclomatic`, `explain`, or `explain-pair` |
| `lenses` | map | Which analysis lenses ran and their parameters |
| `src-dirs` | vector of strings | Source directories analysed |
| `excludes` | vector of strings | Namespace exclusion patterns applied |
| `include-tests` | boolean | Whether test directories were included |

### Lenses

```edn
{:structural true
 :conceptual {:enabled true
              :threshold 0.20
              :candidate-pairs 170    ; pairs sharing ≥1 term (evaluated)
              :reported-pairs 8}      ; pairs meeting threshold
 :change     {:enabled true
              :threshold 0.30
              :candidate-pairs 42     ; project-internal pairs with ≥1 co-change
              :reported-pairs 3       ; pairs meeting threshold + min-co
              :repo-dir "."
              :since "90 days ago"}}  ; nil if not specified
```

When a lens is disabled: `{:enabled false}`.

**Interpreting zero reported-pairs:** If `candidate-pairs` is positive but
`reported-pairs` is zero, the lens ran successfully but no pairs met the
threshold. If `candidate-pairs` is also zero, no qualifying input existed
(e.g. no co-changing namespace pairs in git history).

---

## analyze

Command: `gordian [analyze] [dirs...] --edn`

Payload keys (alongside envelope):

| Key | Type | Description |
|-----|------|-------------|
| `propagation-cost` | double | Fraction of project reachable per random change |
| `cycles` | vector of sets | Strongly connected components (Tarjan) |
| `nodes` | vector of maps | Per-namespace metrics |
| `conceptual-pairs` | vector of maps | Conceptual coupling pairs (when lens enabled) |
| `change-pairs` | vector of maps | Change coupling pairs (when lens enabled) |

### Node shape

```edn
{:ns          symbol      ; namespace name
 :reach       double      ; transitive reach as fraction of project
 :fan-in      double      ; transitive fan-in as fraction of project
 :ca          integer     ; afferent coupling (direct dependents)
 :ce          integer     ; efferent coupling (direct dependencies)
 :instability double      ; Ce / (Ca + Ce), ∈ [0, 1]
 :role        keyword     ; :core, :peripheral, :shared, or :isolated
 :family      string      ; namespace family prefix (parent, drop last segment)
 :ca-family   integer     ; direct dependents in same family
 :ca-external integer     ; direct dependents outside family
 :ce-family   integer     ; direct dependencies in same family
 :ce-external integer}    ; direct dependencies outside family
```

Family-scoped metrics decompose Ca and Ce by namespace family boundary.
`Ca = Ca-family + Ca-external`, `Ce = Ce-family + Ce-external`.
Family is determined by dropping the last dot-separated segment
(`gordian.scan` → `"gordian"`, `psi.agent-session.core` → `"psi.agent-session"`).

### Conceptual pair shape

```edn
{:ns-a             symbol
 :ns-b             symbol
 :score            double      ; cosine similarity
 :kind             :conceptual
 :structural-edge? boolean     ; true if require edge exists
 :shared-terms     [string]    ; top terms driving similarity
 :same-family?     boolean     ; true if both ns share family prefix
 :family-terms     [string]    ; shared terms derived from ns prefix (noise)
 :independent-terms [string]}  ; shared terms NOT from prefix (signal)
```

When `:same-family?` is true and `:independent-terms` is empty, the
conceptual overlap is entirely from namespace naming convention — likely
not actionable. Diagnose downgrades these to `:low` severity with a
"likely naming similarity" annotation.

### Change pair shape

```edn
{:ns-a             symbol
 :ns-b             symbol
 :score            double      ; Jaccard coupling
 :kind             :change
 :co-changes       integer     ; commits containing both
 :confidence-a     double      ; P(b changed | a changed)
 :confidence-b     double      ; P(a changed | b changed)
 :structural-edge? boolean}
```

---

## diagnose

Command: `gordian diagnose [dirs...] --edn`

Same payload as analyze, plus:

| Key | Type | Description |
|-----|------|-------------|
| `health` | map | Overall project health assessment |
| `findings` | vector of maps | Ranked findings sorted by severity |

### Health shape

```edn
{:propagation-cost double
 :health           keyword    ; :healthy, :moderate, or :concerning
 :cycle-count      integer
 :ns-count         integer}
```

### Finding shape

```edn
{:severity            keyword  ; :high, :medium, or :low
 :category            keyword  ; see categories below
 :subject             map      ; {:ns sym} or {:ns-a sym :ns-b sym} or {:members set}
 :reason              string   ; human-readable explanation
 :evidence            map      ; category-specific evidence
 :actionability-score double}  ; relative prioritization score
```

Finding categories:
- `cycle` — namespace cycle (high)
- `cross-lens-hidden` — hidden in both conceptual + change lenses (high)
- `hidden-conceptual` — shared vocabulary, no structural edge (medium/low)
- `hidden-change` — co-changing in git, no structural edge (medium)
- `sdp-violation` — high Ca but high instability (medium)
- `god-module` — shared role with extreme reach and fan-in (medium)
- `facade` — would be god-module but matches façade pattern: high Ca-external,
  low Ce-external, delegates to family siblings (low)
- `hub` — very high reach (low)

### Cluster shape

Diagnose output also includes clusters — groups of related findings that
share namespace mentions, connected via union-find:

| Key | Type | Description |
|-----|------|-------------|
| `clusters` | vector of maps | Grouped related findings |
| `unclustered` | vector of maps | Findings not part of any cluster |
| `rank-by` | keyword | `:severity` or `:actionability` |

```edn
{:namespaces   #{sym}       ; all namespaces in this cluster
 :findings     [finding]    ; findings in this cluster
 :max-severity keyword      ; highest severity in cluster
 :summary      string}      ; e.g. "3 findings across 4 namespaces"
```

Only clusters with ≥ 2 findings are emitted. Singleton findings appear
in `:unclustered`.

---

## compare

Command: `gordian compare <before.edn> <after.edn> --edn`

Compares two saved EDN snapshots and produces a diff report.

| Key | Type | Description |
|-----|------|-------------|
| `gordian/command` | keyword | `:compare` |
| `before` | map | Metadata from the before snapshot |
| `after` | map | Metadata from the after snapshot |
| `health` | map | `{:before map :after map :delta map}` |
| `nodes` | map | `{:added [node] :removed [node] :changed [diff]}` |
| `cycles` | map | `{:added [set] :removed [set]}` |
| `conceptual-pairs` | map | `{:added [pair] :removed [pair] :changed [diff]}` |
| `change-pairs` | map | `{:added [pair] :removed [pair] :changed [diff]}` |
| `findings` | map | `{:added [finding] :removed [finding]}` |

### Node diff shape

```edn
{:ns     symbol
 :before {:reach ... :role ...}
 :after  {:reach ... :role ...}
 :delta  {:reach -0.05 :role {:before :shared :after :core}}}
```

### Pair diff shape

```edn
{:ns-a   symbol
 :ns-b   symbol
 :before {:score 0.45}
 :after  {:score 0.32}
 :delta  {:score -0.13}}
```

---

## gate

Command: `gordian gate [dirs...] --baseline <file> --edn`

Evaluates CI / ratchet checks by comparing a saved baseline snapshot against
current code.

| Key | Type | Description |
|-----|------|-------------|
| `gordian/command` | keyword | `:gate` |
| `baseline-file` | string | Path to baseline EDN snapshot |
| `result` | keyword | `:pass` or `:fail` |
| `checks` | vector of maps | Evaluated gate checks |
| `summary` | map | `{:passed n :failed n :total n}` |
| `warnings` | vector of maps | Non-fatal warnings |
| `compare` | map | Embedded compare diff |

### Gate check shape

```edn
{:name   keyword       ; :pc-delta, :new-cycles, :new-high-findings, ...
 :status keyword       ; :pass or :fail
 :limit  any
 :actual any
 :reason string}
```

### Warning shape

```edn
{:kind     keyword     ; e.g. :src-dirs-mismatch
 :baseline any
 :current  any}
```

---

## complexity / cyclomatic

Command: `gordian complexity [dirs...] --edn`

`gordian cyclomatic` is currently supported as a compatibility alias.

Cyclomatic complexity analysis is independent of the structural/conceptual/change
pair lenses. The standard envelope is still present, but the payload is focused
on canonical arity-level executable-unit complexity plus rollups.

Payload keys (alongside envelope):

| Key | Type | Description |
|-----|------|-------------|
| `metric` | keyword | `:cyclomatic-complexity` |
| `scope` | map | Resolved analysis scope metadata for reproducibility |
| `options` | map | Applied complexity display/sort options for reproducibility |
| `units` | vector of maps | Canonical arity-level analyzed units |
| `namespace-rollups` | vector of maps | Canonical namespace rollups |
| `project-rollup` | map | Canonical project rollup |
| `max-unit` | map or nil | Highest-complexity analyzed unit |

### Scope shape

```edn
{:mode    :discovered|:explicit
 :source? boolean
 :tests?  boolean
 :paths   [string ...]}
```

### Options shape

```edn
{:sort   :cc|:ns|:var|:cc-risk|nil
 :top    pos-int-or-nil
 :min-cc non-negative-int-or-nil}
```

### Canonical unit shape

```edn
{:metric            :cyclomatic-complexity
 :ns                symbol
 :var               symbol
 :kind              keyword
 :arity             integer-or-nil
 :dispatch          any
 :file              string
 :line              integer-or-nil
 :origin            :src|:test
 :cc                integer
 :cc-decision-count integer
 :cc-risk           {:level keyword :label string}}
```

### Namespace rollup shape

```edn
{:ns             symbol
 :unit-count     integer
 :total-cc       integer
 :avg-cc         double
 :max-cc         integer
 :cc-risk-counts {:simple integer
                  :moderate integer
                  :high integer
                  :untestable integer}}
```

### Project rollup shape

```edn
{:unit-count     integer
 :namespace-count integer
 :total-cc       integer
 :avg-cc         double
 :max-cc         integer
 :cc-risk-counts {:simple integer
                  :moderate integer
                  :high integer
                  :untestable integer}}
```

### Max-unit shape

```edn
{:metric            :cyclomatic-complexity
 :ns                symbol
 :var               symbol
 :kind              keyword
 :arity             integer-or-nil
 :dispatch          any
 :file              string
 :line              integer-or-nil
 :origin            :src|:test
 :cc                integer
 :cc-decision-count integer
 :cc-risk           {:level keyword :label string}}
```

The canonical machine-readable fields are `metric`, `units`,
`namespace-rollups`, `project-rollup`, and `max-unit`.

---

## subgraph

Command: `gordian subgraph <prefix> --edn`

Subsystem/family view for namespaces matching a dotted prefix.

| Key | Type | Description |
|-----|------|-------------|
| `gordian/command` | keyword | `:subgraph` |
| `prefix` | string | Requested namespace family prefix |
| `members` | vector of symbols | Matching namespaces |
| `rank-by` | keyword | `:severity` or `:actionability` |
| `internal` | map | Induced subgraph metrics |
| `boundary` | map | Incoming/outgoing boundary edges and summaries |
| `pairs` | map | Conceptual/change pairs sliced into `:internal` and `:touching` |
| `findings` | vector | Findings touching the family |
| `clusters` | vector | Reclusered local clusters from touching findings |
| `unclustered` | vector | Touching findings not in any local cluster |
| `summary` | map | Aggregate counts |

### Internal shape

```edn
{:node-count        integer
 :edge-count        integer
 :density           double
 :propagation-cost  double
 :cycles            [set]
 :nodes             [node]}
```

### Boundary shape

```edn
{:incoming        [{:from sym :to sym}]
 :outgoing        [{:from sym :to sym}]
 :incoming-count  integer
 :outgoing-count  integer
 :external-deps   [sym]
 :dependents      [sym]}
```

### Pair slice shape

```edn
{:conceptual {:internal [pair]
              :touching [pair]}
 :change     {:internal [pair]
              :touching [pair]}}
```

---

## communities

Command: `gordian communities [dirs...] --edn`

Detect latent architecture communities from structural, conceptual, change,
or combined signals.

| Key | Type | Description |
|-----|------|-------------|
| `gordian/command` | keyword | `:communities` |
| `lens` | keyword | `:structural`, `:conceptual`, `:change`, or `:combined` |
| `threshold` | double or nil | Edge threshold used |
| `communities` | vector of maps | Discovered communities |
| `summary` | map | Top-line counts |

### Community shape

```edn
{:id                integer
 :members           [sym]
 :size              integer
 :edge-count        integer
 :density           double
 :internal-weight   double
 :boundary-weight   double
 :dominant-terms    [string]
 :bridge-namespaces [sym]
 :edges             [edge]}
```

### Edge shape

```edn
{:a       symbol
 :b       symbol
 :weight  double
 :sources #{keyword}}
```

---

## dsm

Command: `gordian dsm [dirs...] --edn`

Dependency Structure Matrix view using dependency-respecting ordering and
contiguous diagonal block partitioning.

| Key | Type | Description |
|-----|------|-------------|
| `gordian/command` | keyword | `:dsm` |
| `basis` | keyword | `:diagonal-blocks` |
| `ordering` | map | Ordering strategy and ordered namespace basis |
| `blocks` | vector of maps | Contiguous diagonal blocks |
| `edges` | vector of maps | Inter-block edge counts |
| `summary` | map | Top-line block metrics |
| `details` | vector of maps | Block detail mini-matrices |

### Ordering shape

```edn
{:strategy keyword    ; e.g. :dfs-topo
 :alpha    double     ; block cost exponent
 :nodes    [sym]}
```

### Block shape

```edn
{:id                  integer
 :members             [sym]
 :size                integer
 :internal-edge-count integer
 :density             double}
```

### Edge shape

```edn
{:from       integer
 :to         integer
 :edge-count integer}
```

`edge-count` is the number of namespace-level structural edges crossing from
block `:from` to block `:to`.

### Summary shape

```edn
{:block-count            integer
 :singleton-block-count  integer
 :largest-block-size     integer
 :inter-block-edge-count integer
 :density                double}
```

### Detail shape

```edn
{:id                  integer
 :members             [sym]
 :size                integer
 :internal-edges      [[int int]]
 :internal-edge-count integer
 :density             double}
```

`internal-edges` are local mini-matrix coordinates relative to the ordered
`:members` vector.

---

## explain

Command: `gordian explain <ns> --edn`

Payload keys (alongside envelope):

| Key | Type | Description |
|-----|------|-------------|
| `ns` | symbol | The namespace being explained |
| `metrics` | map | `{:reach :fan-in :ca :ce :instability :role}` |
| `direct-deps` | map | `{:project [sym] :external [sym]}` |
| `direct-dependents` | vector of symbols | Namespaces that require this one |
| `conceptual-pairs` | vector of maps | Conceptual pairs involving this ns |
| `change-pairs` | vector of maps | Change pairs involving this ns |
| `cycles` | vector of sets | Cycles this namespace participates in |

Error case (namespace not found):
```edn
{:error     string
 :available [symbol]}
```

When the explain lens auto-enables conceptual and change coupling, the
envelope's `:lenses` section reflects what actually ran (same as diagnose).

---

## explain-pair

Command: `gordian explain-pair <ns-a> <ns-b> --edn`

Payload keys (alongside envelope):

| Key | Type | Description |
|-----|------|-------------|
| `ns-a` | symbol | First namespace |
| `ns-b` | symbol | Second namespace |
| `structural` | map | `{:direct-edge? bool :direction kw :shortest-path [sym]}` |
| `conceptual` | map or nil | Conceptual pair data (if any, with family annotation) |
| `change` | map or nil | Change pair data (if any) |
| `finding` | map or nil | Diagnosis finding for this pair (if hidden) |
| `verdict` | map | Opinionated interpretation (see below) |

### Verdict shape

```edn
{:category    keyword    ; see categories below
 :explanation string}    ; human-readable interpretation
```

Verdict categories (first-match rule evaluation):

| Category | Condition | Meaning |
|----------|-----------|---------|
| `expected-structural` | Direct edge exists | Intentional, visible in code |
| `family-naming-noise` | Hidden + same-family + no independent terms | Pure namespace prefix overlap |
| `family-siblings` | Hidden + same-family + independent terms | Shared domain vocabulary within family |
| `likely-missing-abstraction` | Hidden + conceptual + change + cross-family | Strongest hidden coupling signal |
| `hidden-conceptual` | Hidden + conceptual only + cross-family | Shared vocabulary, no behavioral link |
| `hidden-change` | Hidden + change only | Vestigial or implicit contract |
| `transitive-only` | Path exists, no coupling signals | Low concern |
| `unrelated` | Nothing | No coupling detected |

---

## JSON notes

In JSON output:
- Symbols become strings (`gordian.scan` → `"gordian.scan"`)
- Keywords become strings (`:core` → `"core"`)
- Sets become sorted arrays
- Namespaced keywords preserve namespace (`gordian/version` → `"gordian/version"`)

---

## Versioning policy

- `gordian/schema` is an integer, incremented on breaking output changes
- Additive changes (new keys) do not bump the schema version
- Consumers should tolerate unknown keys
