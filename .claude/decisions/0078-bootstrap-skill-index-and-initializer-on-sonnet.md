# 0078 · `project-bootstrap` becomes an index over phase references, no rule is read during generation, and `project-initializer` runs on `sonnet`

- **Date:** 2026-09-29
- **Scenario:** lessons-learned-015 topics B and C — `project-bootstrap/SKILL.md` at 1 121 lines / 65 KB, the driver told to read all 13 rules before generating, and that driver on `opus`
- **Decision:** option 1 — spine + phase references, no rule read during generation, initializer on `sonnet` + `effort: high`
- **State:** approved by Lucas Fernandes, 2026-09-29
- **Goes to the generated project:** no — `project-bootstrap`, `init-project` and `project-initializer` are creation pieces, excluded from `export`

## Interview

Answered from the lesson and from the files; no axis needed a question.

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | The skill is 2× the documented ~500-line ceiling. Its § Contract and the agent's `**Reads:**` order every rule read (95 KB ≈ 24k tokens on `opus`) "because step 6.6 copies them" — but step 6.6 is `ArchHook.java export`, which copies and rewrites `paths` deterministically. The reason for the read is dead | "keep as is" |
| 2 — Trigger | `/init-project` → `project-initializer` → procedure. Unchanged | — |
| 3 — Frequency | Once per project, every step runs | A per-step split saves no tokens on its own — every step file is read anyway |
| 5 — Nature | Sequence of steps, with long Maven/Gradle branches of which one run uses only one | Form 4/5 for the procedure |
| 6 — Isolation | Verbose output (`starter.tgz`, build logs) and a fixed tool set still apply to the agent; the model reason does not — the procedure is driven by blueprint YAML, the Initializr and templates | Keeps Form 3; drops reason 3 |
| 7 — Mandatoriness | The deterministic half (copies, `paths` rewrite) is already a hook mode (`export`). Nothing new must "always hold" | Form 7/8 — nothing to add |
| 8 — Destination | Meta-repo only | `export` untouched |
| 10 — Cost of error | A reference split wrong drops an instruction from a generation → a broken project, found only at step 8's build | Weight on "the step spine stays in `SKILL.md`" |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `SKILL.md` becomes the spine (every step keeps its goal, its exit condition and the reference to open); step bodies move to a few **phase** references, the Maven and Gradle branches to one reference each; rule reads cut; agent to `sonnet` + `effort: high` | 8 | **approved** |
| 2 | The lesson's literal proposal: one `references/step-N.md` per step (~20 files); same rule-read cut and model change | 5 | rejected — a per-step split saves no tokens over a per-phase one, costs ~15 extra reads, and separates steps that cite each other |
| 3 | Cut the rule reads and change the model only; `SKILL.md` stays whole | 4 | rejected — fails the < 500-line criterion and keeps the compaction risk on steps 6.6+ |
| 4 | Create nothing | 1 | rejected — the dead read and the size both stay |

### Option 1 (score 8)

**Motivator:** axes 1 + 3 + 5. The saving is not in splitting steps apart — every step runs — but in (a) not reading 95 KB of rules the model does not use, and (b) loading only one build tool's branch.

**Mechanism.**

1. **`SKILL.md` = spine, under 500 lines, target ≈ 300** — frontmatter, the "does not write business code" preamble, § Dependencies, § When NOT to use, § Preconditions, then § Procedure with **every step's heading kept**, each as 2–6 lines: what it produces, the exit condition, and `Read references/<file>.md § <step>` before executing it. § Output contract, § Why, § Contract stay. The first ~5 000 tokens — what survives compaction when the skill is re-attached — then hold the whole order of steps, which is what gets lost today.
2. **References grouped by phase, not per step** — each read once, at the step that opens it, so late steps' text enters context late instead of 60 KB before it is needed:

   | Reference | Holds | Read at |
   |---|---|---|
   | `references/build-maven.md` | the `build.tool: maven` branches of steps 4, 4.6, 8 | step 4, only when `maven` |
   | `references/build-gradle.md` | the `gradle` branches of the same steps | step 4, only when `gradle` |
   | `references/scaffold.md` | steps 4 (Both), 4.5, 4.6 (Both), 4.7, 4.8, 4.9, 4.10 | step 4 |
   | `references/project-files.md` | steps 5, 6, 6.5, 6.6, 7 | step 5 |
   | `references/verify-and-report.md` | steps 8 (Both), 8.5, 8.6 | step 8 |

   Step 3's two Initializr calls stay in the spine, whole: they share one block of prose, and splitting them by tool would write it twice. `blueprint-selection.md` and `dependency-catalog.md` stay as they are.

   **As written:** `SKILL.md` went from 1 121 to 432 lines — under the ceiling, above the ≈ 300 target, because steps 1–3, § Output contract and § Contract's `**Writes**` list stay whole: they are read on every run, and the report shape must survive compaction. Templates stay in `templates/` (invariant 3); nothing is copied, only moved.
3. **No rule is read during generation.** § Applicable rules becomes a table of which template embodies which rule, cited by path and **not read**: the numbers are already inside `checkstyle.xml.example`, `lombok.config.example`, `logback-spring.xml.example`, and the copying is `export`'s. § Contract's "Reads before generating" drops the 13 rule paths and its stale reason. Same edit to the agent's `**Reads:**`.
4. **`project-initializer`: `model: sonnet`, `effort: high`.** Its § Why keeps reasons 1 (context) and 2 (tools) and drops the model reason. Invariant 5 still holds with two reasons.
5. **Guard against the index being ignored** — the spine names the reference on the step heading's own line, so a model that follows the procedure top-down cannot reach a step's body without the read. No hook: the failure (a skipped read) shows at step 8's build and `schema`, which already run.

**Pros:** answers both of the lesson's measurable criteria (< 500 lines; no rule read); halves the Maven/Gradle text per run; the step order survives compaction; late-step instructions are read fresh near the end instead of 60 KB earlier; one file per phase keeps cross-step references (4 ↔ 4.6 ↔ 4.7) inside the same file.

**Cons:** five reads instead of one; a moved paragraph can be lost in transit — mitigated by moving whole sections verbatim and diffing the byte count. The lesson's "generated tree identical" and C's "end-to-end on `sonnet` passes build and `schema`" need a real `/init-project` run with network — **pending verification by the user**, not claimable from here.

**Points cut in the rubric:** multiple reads (−1); verification not runnable here (−1).

### Option 2 (score 5)

Same as 1 with ~20 files. Disagreement with the lesson: every step runs in every generation, so a per-step split buys no tokens over a per-phase one and costs ~15 extra reads, and steps that cite each other (4.6's Checkstyle excludes ↔ 4.7's packages ↔ 8's verify) end up in different files. Worse to maintain, same saving.

### Option 3 (score 4)

Captures the biggest single saving (≈ 24k tokens of rules) and the model change at almost no cost, but leaves `SKILL.md` at ~1 100 lines, fails the lesson's first criterion, and keeps the compaction risk on steps 6.6+.

## References

| Claim | Source |
|---|---|
| `SKILL.md` should stay under ~500 lines, rest in supporting files | https://code.claude.com/docs/en/skills |
| After compaction, only the first 5 000 tokens of each re-attached skill come back (25k total) | same page; lessons-learned-015 § B |
| Step 6.6 copies rules, skills, agents, hook and schema and rewrites `paths` | `.claude/skills/project-bootstrap/SKILL.md` § 6.6; `ArchHook.java export` |
| An agent needs one of three reasons; two remain | `@CLAUDE.md` invariant 5 |
| Code boilerplate stays in `templates/` | `@CLAUDE.md` invariant 3 |
| `effort` is a valid agent field | `.claude/skills/claude-code-architect-designer/references/frontmatter-fields.md` |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/project-bootstrap/SKILL.md` | spine; § Applicable rules → template ↔ rule table, not read; § Contract "Reads" without rules |
| `.claude/skills/project-bootstrap/references/{build-maven,build-gradle,scaffold,project-files,verify-and-report}.md` | new, bodies moved verbatim |
| `.claude/agents/project-initializer.md` | `model: sonnet`, `effort: high`; § Why; `**Reads:**` without rules |
| `.claude/schemas/extensions.json` | nothing, unless `schema` finds an injection or template path moved |
| `README.md`, `docs/{00-visao-geral,01-tipos-de-arquivo,02-init-project}.md`, `docs/en/{00-overview,01-file-types,02-init-project}.md` | `opus` → `sonnet` in the diagrams and the agent table; the model reason replaced by this record |

Written by the main thread, not delegated — steps 1–7 never are, and axis 8 is "meta-repo".
