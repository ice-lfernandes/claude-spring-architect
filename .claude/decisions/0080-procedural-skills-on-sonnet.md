# 0080 · Procedural skills pin a `model` and an `effort`; design skills keep inheriting

- **Date:** 2026-09-29
- **Scenario:** lessons-learned-015 topic D — `git-publish`, `docker-architect`, `arch-doctor` and `audit-usage` declare no `model`/`effort`, so they run on the session's model (Opus 5.5 today) for procedural work
- **Decision:** option 0 — `docker-architect` on `opus` (effort inherited), `arch-doctor` on `sonnet` + `high`, `git-publish` and `audit-usage` on `sonnet` + `low`
- **State:** approved by Lucas Fernandes, 2026-09-29
- **Goes to the generated project:** yes — the four travel through `export`, frontmatter included; no manifest change

## Interview

Answered from the lesson and the files; no axis needed a question.

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | Opus paying for `git init`/`commit`, for running `ArchHook.java doctor` and rendering it, for rendering `audit summary` | "keep as is", for three of the four |
| 2 — Trigger | `git-publish`: chained by `project-initializer` and by `/new-feature`'s end of flow, behind two confirmations. `arch-doctor`, `audit-usage`: manual (`disable-model-invocation`). `docker-architect`: model-invocable and `/docker-architect` | — |
| 5 — Nature | Frontmatter of existing skills | No new piece |
| 7 — Mandatoriness | None: a weaker model here degrades, it does not break a guarantee | Form 7/8 |
| 8 — Destination | Both — the four are in `export.skills.include` | — |
| 9 — Integration | Runtime semantics: a skill's `model`/`effort` override lasts **for the rest of the turn** (`claude-code-docs/02-skills.md`). A model-invocable skill fired mid-turn in an Opus design conversation drops that turn's tail to its model | Weight against pinning a model-invocable skill whose output needs judgment |
| 10 — Cost of error | `git-publish`/`arch-doctor`/`audit-usage`: minutes, and the output is shown before anything irreversible. `docker-architect`: a wrong compose shipped once (the `kafka` advertised-listener block, day one) — now caught by `compose gate` on `Stop` | Weight on "the check that catches it is a hook, not the model" |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 0 | `opus` (effort inherited) on `docker-architect`; `sonnet` + `high` on `arch-doctor`; `sonnet` + `low` on `git-publish`, `audit-usage` — proposed by the user during review | 8 | **approved** |
| 1 | `sonnet` + `effort: low` on `git-publish`, `arch-doctor`, `audit-usage`; `sonnet` + `effort: medium` on `docker-architect` | 7 | not chosen — `low` under-serves `arch-doctor`'s diagnosis; `sonnet` on the one skill with an observed design failure |
| 2 | Option 1 for the three procedural ones; `docker-architect` keeps inheriting | 6 | not chosen — on a `sonnet` session the compose design would run on `sonnet` |
| 3 | `haiku` on `arch-doctor`/`audit-usage` (pure rendering), `sonnet` elsewhere | 4 | rejected |
| 4 | Create nothing | 2 | rejected |

### Option 0 (score 8)

**Motivator:** axes 1 + 10, weighted per skill instead of per group.

- **`docker-architect` → `opus`, no `effort`.** The pin only changes anything on a session that is *not* on Opus: there it raises the compose design to Opus for the rest of the turn. That is the point — the kafka advertised-listener block is the one design failure observed among the four, and `compose gate` only catches it after the write. On an Opus session the pin is a no-op, so axis 9's mid-turn drop never happens. Cost: every compose edit in a generated project pays Opus, even for a user who picked `sonnet` on purpose.
- **`arch-doctor` → `sonnet` + `high`.** `doctor` computes the report, but the skill's job is the cause and the concrete fixing command — judgment that `low` under-serves.
- **`git-publish`, `audit-usage` → `sonnet` + `low`.** Fixed procedure behind two gates; ranking and rendering over sums `audit summary` already did.

**Points cut:** Opus forced on sessions that chose otherwise (−1); no measured before/after (−1).

### Option 1 (score 7)

**Motivator:** axes 1 + 10. Three skills are fixed procedures whose output the user reads before anything happens; `docker-architect` is the one with judgment, but its failure mode already has a deterministic check (`compose gate`, `ArchHook.java compose`'s advertised-address and tag checks).

**Mechanism.** Two frontmatter lines per skill (`model`, `effort`) — native fields, listed in `extensions.json` and `frontmatter-fields.md`. No body change except one sentence under each `## Why this is a skill` / contract section citing this record, so the choice travels with the file. Design skills (`use-case-design`, `domain-modeling`, `rest-api-architect`, `persistence-architect`, `messaging-architect`, `test-architect`, `java-patterns`) keep inheriting.

**Pros:** cheaper on every run of the four; nothing enforced changes.

**Cons:** axis 9 — when `docker-architect` fires mid-turn inside an Opus conversation, the rest of that turn runs on `sonnet`; `effort: low` on `git-publish` has to still read its state check (ignored files, dirty tree) correctly — the two confirmations are the backstop.

**Points cut:** mid-turn override on a model-invocable skill (−1); `sonnet` on the one skill with an observed design failure, relying on the gate (−1); no measured before/after (−1).

### Option 2 (score 6)

Same saving on the three with no judgment; `docker-architect` stays on the session model, so the mid-turn drop never happens and the compose design keeps Opus. Costs Opus on every compose edit.

### Option 3 (score 4)

`haiku` for rendering is cheapest, but `arch-doctor` explains failures and suggests fixes, and `audit-usage` ranks spend — a small model inventing a cause in a diagnostic is worse than a slower report.

## References

| Claim | Source |
|---|---|
| `model`/`effort` override the model for the rest of the turn | `.claude/claude-code-docs/02-skills.md`; `claude-help.md` § skill fields |
| Both are recognized skill fields | `.claude/schemas/extensions.json`; `claude-code-architect-designer/references/frontmatter-fields.md` |
| `compose gate` catches the observed compose failure | `@CLAUDE.md` § Known pitfalls; decision 0064 |
| The four travel | `export.skills.include` in `.claude/schemas/extensions.json` |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/{git-publish,arch-doctor,audit-usage,docker-architect}/SKILL.md` | `model`, `effort`; one line citing this record |
| `docs/01-tipos-de-arquivo.md`, `docs/en/01-file-types.md` | nothing — neither lists a skill's model |

Whether `model` becomes a required field per skill class is a guarantee, and has its own
record: `0081`.
