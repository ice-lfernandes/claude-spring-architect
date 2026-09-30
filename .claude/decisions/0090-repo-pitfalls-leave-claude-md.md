# 0090 · The repository's own pitfalls leave `CLAUDE.md`, and new ones stop landing there

- **Date:** 2026-09-30
- **Scenario:** "o `CLAUDE.md` tem uma seção de pitfalls que é carregada em todo contexto. Ao
  meu ver esses pitfalls fazem muito sentido para duas situações: documentação e
  `claude-code-architect-designer`. Qual sua recomendação?"
- **Decision:** Option 1 — part 2 of `docs/pt-br/11-pitfalls.md` / `docs/en/11-pitfalls.md`
  carries 16 of the 19 repository pitfalls; `CLAUDE.md` keeps three; the designer reads the page
  in Phase 1 and routes new pitfalls there in Phase 4 step 8. 285 → 172 lines.
- **State:** approved by Lucas Fernandes, on 2026-09-30

## Interview

Answered from the repository, not asked: every axis below has evidence on disk, and the user's
own sentence already answers axes 3 and 9.

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | `CLAUDE.md` is at 285 lines. `0061` cut it to 216 on 2026-09-28; two days and six commits later it is back to 285. § Known pitfalls is 133 of those lines (lines 153-285, 19 items), the largest always-loaded block again | "create nothing" |
| 1 — cause of the regrowth | `claude-code-architect-designer` Phase 4 step 8 routes every new blocking hook and every `deny` into `@CLAUDE.md` § Known pitfalls. `0063`, `0064`, `0065`, `0066`, `0075`, `0076`, `0081`, `0082`, `0087` each added a bullet through that route. Moving the bullets without changing the route buys two more days | Any option that does not touch step 8 |
| 3 — frequency | Needed while someone designs or edits a piece of `.claude/`, or writes `docs/` about one — not in a session that only runs `/arch-doctor` or reads the README | Form 5 (always loaded) |
| 5 — nature | Declarative facts | Forms 1, 2, 3 as a *new* piece |
| 4 — territory | `.claude/**` and `docs/**`, but the facts name skills, hooks and agents | **Form 4** — invariant 1: `rules/` is a leaf. Also `export.rules` ships the whole directory, so a meta-repo rule would land in every generated project |
| 7 — mandatoriness | Most bullets already describe a guarantee that exists: `guard` (exit 2 naming class and `write_allow`), `schema` (fails by name), `compose gate` (exit 2 with the failing lines). The block message carries the fix; the always-loaded bullet is its second copy | Forms 7, 8 — nothing new to enforce |
| 8 — destination | This repository only. The generated project's `CLAUDE.md` comes from `project-bootstrap/templates/root.CLAUDE.md.example` and keeps its own section; `docs/` does not travel | A copy in the export payload |
| 9 — integration | `docs/pt-br/11-pitfalls.md` / `docs/en/11-pitfalls.md` already own the runtime pitfalls (`0061`). Six design skills cite `@CLAUDE.md` § Known pitfalls for the `AskUserQuestion` floor — that citation resolves in the **generated** project, whose template keeps the section, so it is unaffected | A second owner beside `docs/11-pitfalls.md` |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Extend `docs/*/11-pitfalls.md` with a "this repository" part; the designer reads it in Phase 1; step 8 routes new pitfalls there; `CLAUDE.md` keeps three one-liners | 8 | **Approved** |
| 2 | `claude-code-architect-designer/references/repo-pitfalls.md` as owner; `docs/` links to it | 6 | Rejected — two owners for one kind of fact (invariant 2) |
| 3 | Nested `CLAUDE.md` files under `.claude/skills/` and `.claude/hooks/`, loaded on demand | 4 | Rejected — fragments one fact set, loading on skill invocation unverified, no precedent |
| 4 | Compress in place | 3 | Rejected — cuts the *why* and leaves step 8 refilling the section |

### Option 1 — `docs/*/11-pitfalls.md`, second part (score 8)

**Motivator:** axis 3 — the two audiences the user named both already read `docs/`, and the
designer can be told to; axis 1 — the regrowth route in step 8.

**Pros.** One owner for every pitfall, runtime and repository, split into two parts on one
page. The page already exists, is bilingual, and is already routed from `CLAUDE.md`. It can
carry the full "why" instead of the compressed form `CLAUDE.md` forces. The designer's Phase 1
adds it to what it reads before asking, so the design audience gets it exactly when it
designs. Step 8 changes destination, which is what stops the regrowth. `CLAUDE.md` lands near
160 lines.

**Cons.** A hand edit of an existing piece outside the designer (fixing `ArchHook.java`,
tweaking a pipeline skill) no longer has the bullets in context. Mitigated by what already
exists: the guard and schema messages name the rule and the fix, and the three facts that
bite with no hook behind them stay in `CLAUDE.md`. Two languages to keep in step — the
standing cost of `docs/`.

**Points cut in the rubric:** criterion 4 — for the moved items, "always in context" drops to
"cited", covered by the hooks' own messages; criterion 5 — bilingual page.

### Option 2 — designer `references/repo-pitfalls.md` (score 6)

Loads exactly when the designer runs, English only, no bilingual cost. But the documentation
audience then reads an internal skill file from a pt-BR page, and the runtime half stays in
`docs/` — two owners for one kind of fact, the split invariant 2 exists to prevent. Criterion
6 also bites: no reference file in this repo is owned by one skill and cited by `docs/`.

### Option 3 — nested `CLAUDE.md` under `.claude/` (score 4)

`@claude-help.md` § 2: a subdirectory's `CLAUDE.md` loads on demand when Claude reads something
there — so it would also cover hand edits. But `.claude/CLAUDE.md` itself is a project location
and loads at launch, so the pitfalls would have to be split across `.claude/skills/CLAUDE.md`,
`.claude/hooks/CLAUDE.md` and more, each a new owner. Whether invoking a skill counts as
"reading" its directory is not stated anywhere, and `schema` lists `.claude/skills/` expecting
only skill directories. Novel design, no precedent, fragments one fact set into several files.

### Option 4 — compress in place (score 3)

Already rejected by `0061` for the same reason: what compression cuts is the *why*. And it
leaves step 8 routing new bullets into the same section.

## References

| Claim | Source |
|---|---|
| A subdirectory's `CLAUDE.md` loads on demand; `./.claude/CLAUDE.md` is a project location loaded at launch | `@claude-help.md` § 2, "How they load" and the locations table |
| Target under 200 lines; past it the file loses rules | `@claude-help.md` § 2, "Writing an effective CLAUDE.md" · `references/decision-matrix.md` § 7, anti-pattern 3 |
| A rule may not name a skill, an agent or a command | `@CLAUDE.md` invariant 1 |
| `export.rules` ships the whole directory | `references/decision-matrix.md` § 6 |
| `docs/` does not travel | `export` in `@.claude/schemas/extensions.json` |
| The previous extraction and its approved shape | `.claude/decisions/0061-claude-md-pitfalls-extraction.md` |
| The route that refills the section | `.claude/skills/claude-code-architect-designer/SKILL.md` Phase 4 step 8, rows "Hook, this repo only" and "`permissions` rule (Form 8)" |

## What stayed in `CLAUDE.md`, and why

The test for each of the 19: does it bite in a session that is not designing or editing a
piece of `.claude/`, with no hook message to explain it?

| Stayed | Why |
|---|---|
| The generated project's `CLAUDE.md` is not this file | The most common mistake when editing this repository (`docs/*/01-…` quotes it); no hook catches it |
| The hooks run the jar, not the source | Any edit of `ArchHook.java` — a bug fix outside the designer included. The `PostToolUse` rebuild covers the common path; the JDK pin and the `.gitignore` un-ignore line do not announce themselves. Compressed: the `.claude/.ci` sentence and the `0084` citation dropped |
| This repository does not run `./mvnw` | A silent exit 0 that reads as a skipped check in any session |

The other 16 moved verbatim (English) and translated (pt-BR): territory and the four guard
bullets, the model-per-class rule, the two spec-folder bullets, the three compose bullets,
the three ownership bullets, rules-without-`paths` and `decisions/`.

## Propagation

| File | Change |
|---|---|
| `docs/pt-br/11-pitfalls.md` · `docs/en/11-pitfalls.md` | Retitled "Pitfalls"; "why this page exists" rewritten for two parts and the "never back into `CLAUDE.md`" line; runtime sections demoted under "Part 1"; new "Part 2 · This repository" with 16 items in six groups |
| `CLAUDE.md` | § Known pitfalls: pointer paragraph + three bullets. Routing row widened to the repository's traps. 285 → 172 lines |
| `.claude/skills/claude-code-architect-designer/SKILL.md` | Phase 1 reads the pitfalls page; step 8 rows "Hook, this repo only" and "`permissions` rule" route to part 2 of the page; `## Contract` **Reads** names it |
| `docs/pt-br/06-claude-code-architect-designer.md` · `docs/en/06-…` | Same three changes mirrored, plus the sequence diagram's read step |
| `CONTRIBUTING.md` | Silent traps now point to `docs/en/11-pitfalls.md` |
| `README.md` | `decisions/` CI row and the `11-pitfalls.md` index row |
| `docs/*/README.md` | Index description covers both parts |
| `docs/*/01-…` | Two runtime citations re-pointed from `CLAUDE.md` § Known pitfalls (stale since `0061`) to `11-pitfalls.md`; the "not this file" quote stays, since the bullet stayed |
| `docs/*/07-ci-validate.md` | `decisions/` row points to `11-pitfalls.md` |

Not touched: the six design skills and `arch-doctor` that cite `@CLAUDE.md` § Known pitfalls.
Those skills travel, and inside a generated project the citation resolves to the section
`project-bootstrap/templates/root.CLAUDE.md.example` writes. Decision records citing the old
section are history and are not rewritten.

Goes to the generated project: **no.** `docs/` is outside `export`, the designer is in
`export.skills.exclude`, and the generated `CLAUDE.md` comes from its own template.
