# 0061 · The runtime's pitfalls leave `CLAUDE.md` for a `docs/` page

- **Date:** 2026-09-28
- **Scenario:** "vamos reduzir claude para proximo de 200 linhas" — `CLAUDE.md` had reached
  286 lines, § Known pitfalls alone accounting for 141 of them (28 items, ~5 lines each),
  after `0060` added three more.
- **Decision:** Option 2 — a new `docs/11-pitfalls.md` in both languages carries the 15
  pitfalls that are facts of the Claude Code **runtime**; `CLAUDE.md` keeps the 13 that are
  facts of **this repository**, compressed, plus one routing row and one pointer sentence.
  216 lines.
- **State:** approved by Lucas Fernandes, on 2026-09-28

## Why the section was the only candidate

| Section | Lines | Verdict |
|---|---|---|
| § Known pitfalls | 141 | The target — half the file, and half of it is not about this repo |
| § Invariants | 63 | Untouched. The wording carries the obligation; compressing a non-negotiable is a different decision, and it was not asked for |
| § Routing | 31 | Untouched, +1 row |
| § Architecture of the AI files | 19 | Two sentences merged into one, −1 line. The diagram stays: it is the dependency map, and nothing else in the repo states it |
| § Commands, § Dependencies | 18 | Untouched |

## Interview

| Axis | Answer | What it eliminated |
|---|---|---|
| 1 — symptom | 286 lines, read in full on every session; `@CLAUDE.md` § Known pitfalls had become the largest always-loaded block in the repository, and 15 of its 28 items describe the runtime, not this `.claude/` | "create nothing" |
| 3 — frequency | A runtime pitfall is needed while someone writes a skill, a hook, an injection or a `.mcp.json` — not in every session | Keeping them in `CLAUDE.md` (Form 5) |
| 5 — nature | Declarative facts, no procedure | Forms 1, 2, 3 |
| 4 — territory | None. They are not about a file territory but about how the runtime reads any of them | **Form 4 (a rule) — and invariant 1 rules it out outright:** every one of these names a skill, an agent, a hook or a command, and `rules/` is a leaf |
| 8 — destination | This repo only. The generated project's `CLAUDE.md` comes from `project-bootstrap/templates/root.CLAUDE.md.example` and keeps its own copy of the two pitfalls it needs; `docs/` does not travel (`export` carries no `docs/` path) | A second copy inside the export payload |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Move them into `claude-help.md` § Pitfalls — the declared owner of "how each piece of Claude Code works", 1401 lines, cited in backticks so never imported | 8 | Rejected by the user |
| 2 | A new `docs/11-pitfalls.md`, both languages, indexed in `docs/README.md` | 8 | **Approved** |
| 3 | Compress in place, nothing leaves | 5 | Rejected — lands at ~215-225 anyway, and what compression cuts is the *why*, which is the half that makes a pitfall stick instead of being reread and ignored |
| 4 | Create nothing | 2 | Rejected — the file only grows; `0060` alone added three items |

### Option 2 — `docs/11-pitfalls.md` (score 8)

**Motivator:** axis 3 — a runtime fact is needed at writing time, not in every session; axis 1
— the section was half the file.

**Pros.** `docs/` is where a reader already goes for how the pieces work, it is bilingual like
every other page there, and the page can carry the reproduction and the "why" at full length
instead of the compressed form `CLAUDE.md` forces. Nothing is lost: 15 items move verbatim, 13
stay compressed, and the pointer sentence names each moved trap so the reader knows a list
exists before needing it.

**Cons.** Two files to keep in step (pt-BR and en), which is the standing cost of every page in
`docs/`. And the same facts are now one hop further from the model: a pitfall in `CLAUDE.md` is
read whether or not anyone asks, one in `docs/` is read when the routing row is followed — which
is exactly the trade being bought, but it is a trade, not a free win.

**Points cut in the rubric:** criterion 5 — a bilingual page doubles the maintenance surface;
criterion 4 — for the moved items, enforcement drops from "always in context" to "cited",
and `ArchHook.java schema` already covers the three that had a mechanism (`$ARGUMENTS`,
injection cwd, hook `settings`).

### Option 1 — `claude-help.md` (score 8)

Same score, rejected by the user's answer. It would have put the runtime facts in the file
already declared as their owner, with no new page and no second language to maintain — at the
cost of appending to a 1401-line reference nobody reads top to bottom.

## References

| Claim | Source |
|---|---|
| A rule may not name a skill, an agent or a command | `@CLAUDE.md` invariant 1 |
| Form 5 costs every prompt of every session; Form 4 costs only on a matching `paths` | `references/decision-matrix.md` § 6 |
| `CLAUDE.md` past ~200 lines is anti-pattern 3, and the redirect is extraction | `references/decision-matrix.md` § 7 |
| A `@path` inside backticks is a citation, not an import | the routing table's existing rows, which cite `@claude-help.md` and every rule this way |
| `docs/` does not travel to a generated project | `export` in `@.claude/schemas/extensions.json` — no `docs/` entry outside `gitignore_lines` |

## Propagation

| File | Change |
|---|---|
| `docs/11-pitfalls.md` | New. 15 runtime pitfalls, grouped: Skills (5), frontmatter and validation (2), hooks (3), `AskUserQuestion` (1), MCP (4). Opens with why the page exists and what stayed in `CLAUDE.md` |
| `docs/en/11-pitfalls.md` | The same page in English |
| `docs/README.md` · `docs/en/README.md` | Indexed after `10-arch-adopt.md` |
| `CLAUDE.md` | § Known pitfalls rewritten: 13 repo-specific items, compressed, plus the pointer sentence naming what moved. New routing row. § Architecture prose merged into one sentence. 286 → 216 lines |

Goes to the generated project: **no.** `docs/` is outside `export`'s payload, and the
generated project's `CLAUDE.md` is produced from `project-bootstrap`'s own template, which
keeps the two pitfalls it needs.
