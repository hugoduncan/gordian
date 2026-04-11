# Implementation Plan: Markdown Output

Design: `doc/design/005-markdown.md`

## Step 1: `output.clj` — `format-report-md`

New function in output.clj. No CLI wiring yet — just the formatter + tests.

Tests:
- Header line starts with `# Gordian`
- Summary table contains propagation cost percentage
- Namespace metrics table has `| Namespace |` header
- All fixture ns names appear in output
- Conceptual section present when pairs provided (has `## Conceptual`)
- Conceptual section absent when no pairs
- Change section present when pairs provided
- Cycles listed when present
- "(none)" when no cycles

## Step 2: `output.clj` — `format-diagnose-md`

Tests:
- Header contains finding count
- Health table has propagation cost
- 🔴 marker for HIGH findings
- 🟡 marker for MEDIUM findings
- 🟢 marker for LOW findings
- Cross-lens finding shows both scores
- Summary line at bottom
- Empty findings → "0 findings"

## Step 3: `output.clj` — `format-explain-ns-md` + `format-explain-pair-md`

Tests for explain-ns-md:
- Header contains ns name
- Metrics table has Role, Ca, Ce
- Deps section lists external deps with backticks
- Conceptual pairs as markdown table
- "(none)" for empty sections
- Error → error message

Tests for explain-pair-md:
- Header contains both ns names
- Structural table shows edge status
- Conceptual section shows score
- Diagnosis shows severity emoji
- "(no data)" for absent lenses

## Step 4: `main.clj` — `--markdown` flag + routing

- Add `:markdown` to cli-spec
- Mutual exclusion: json + markdown, edn + markdown → error
- Each command function: add `markdown` branch
- Help text mentions --markdown

Tests:
- `--markdown` parsed as boolean
- `--markdown` + `--json` → error
- `--markdown` + `--edn` → error
- Each command with --markdown produces markdown (contains `# Gordian`)
- Help mentions `--markdown`

## Step 5: Docs + PLAN
