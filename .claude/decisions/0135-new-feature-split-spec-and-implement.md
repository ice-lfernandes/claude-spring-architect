# 0135 · `/new-feature` splits into a design skill and an implement skill

- **Date:** 2026-10-08
- **Scenario:** "hoje a skill orquestrador new-feature esta com muitas responsabilidades e papeis: criar specs e iniciar trigger de implementacao. E se quebrassemos para ter mais uma skill orquestradora: new-feature -> new-feature-spec; nova skill: new-feature-implement (faz o trigger com os bloqueios para cenarios de implementacao)"
- **Decision:** option 1 — keep `/new-feature` (design), add `.claude/skills/new-feature-implement/SKILL.md` (Form 2, class `orchestrator`, `model: sonnet`, empty territory); the shared entry guardrail in `.claude/skills/new-feature/references/entry-guardrail.md`
- **State:** approved by Lucas Fernandes, on 2026-10-08
- **Goes to the generated project:** yes — `new-feature` is in `export.include`; every option keeps both flows in the project

## Starting point

`0130` already split the two flows by session: a design run ends at § Approval, and an
`approved` spec is implemented only by `/new-feature UC-NNN-<slug>` (input row 3) after `/clear`.
No option below changes behaviour — they change how many pieces carry it.

| # | Fact | Evidence |
|---|---|---|
| 1 | `new-feature/SKILL.md` is 918 lines, 57,649 bytes (≈14k tokens), loaded whole on every invocation, both flows | `wc` on the file |
| 2 | An implement run uses § Implement (lines 755–824), § Final report, the input table and the worktree checks; steps 1–6 and consolidation (lines 386–708) ride along as cache read on every main-thread turn | `SKILL.md` headings |
| 3 | The skill is pinned to `opus` for consolidation; the implement thread only runs the pre-flight, chains three `Agent` calls and invokes `git-publish` | `SKILL.md` frontmatter, § Implement |
| 4 | `skill_classes.orchestrator` allows `opus` and `sonnet`, territory `docs/use-cases/**`, `docs/lessons-learned/**`; `init-project` already overrides to `write_allow: []` | `extensions.json:214-223` |
| 5 | `ArchHook.java` keys nothing on the name `new-feature` — only Javadoc mentions it | `grep -n new-feature ArchHook.java` |
| 6 | The audit report is `<stamp>--<skill>.md`; both flows sum on one `new-feature` line today | `ArchHook.java:5266` |
| 7 | A project's `audited.json` override is looked up by piece name | `ArchHook.java:4432` (`isAuditExcluded`) |
| 8 | `export.retired` deletes renamed files from an adopted project, file by file; precedent `java-patterns` → `gof-design-patterns` | `extensions.json` `retired` |
| 9 | The exported `settings.json` is overwritten on update, so a `permissions.allow` `Skill(...)` line propagates | `extensions.json` `overwrite` |
| 10 | `SkillTerritoryTest`, `AgentTerritoryTest` and `SweepTest` open a phase with the prompt `/new-feature …`; `JavaTemplatesTest` and `templates.yml` name `new-feature/templates/commons` | `grep` in `.claude/.ci`, `.github/workflows` |
| 11 | A skill's `model` holds for the rest of the turn; a background executor's hand-back arrives as a new turn | `0081` axis 9b; `audit.harness_prompts` |

## What changed since 0067

`0067` rejected a separate implement skill (`/implement-spec`, score 6) and wrote the condition
to revisit it. Its reasons, against the tree today:

| 0067's reason | Now |
|---|---|
| "`/new-feature` also calls [the sequence] at its end" — two callers of one block | `0130` removed that call: a design run ends at § Approval and never implements. The sequence has one caller, and it is a different session |
| A second owner means moving or copying the sequence | Moved, not copied: § Implement and § Operational note left `new-feature/SKILL.md` |
| A second entry guardrail, and a second place to answer the worktree question | One file, `new-feature/references/entry-guardrail.md`, cited by both; each skill keeps only its own input table |
| A class entry, a territory, an `export` entry, a routing row | Paid, and the territory is the gain: `write_allow: []` is narrower than the orchestrator default an implement run had |
| Its own con for the row: "one command, two effect profiles, separated by a table row" | Gone — one command per effect profile |

What 0067 did not have is the measurement: the cost of the design body in every implement turn
(facts 1–2) and the `opus` pin on a thread that designs nothing (fact 3).

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | Cost of the implement run (facts 1–3) and maintenance of one body that carries both flows | create nothing, as the top option |
| 2 — Trigger | Manual, `/command`, both flows | Form 1 |
| 5 — Nature | Procedure | Forms 4, 5 |
| 6 — Isolation | None new — the executor already isolates the implement work | Form 3 |
| 7 — Mandatoriness | Not a guarantee: the guard already enforces each flow's territory | Forms 7, 8 |
| 8 — Destination | Both | — |
| Naming (user) | Asked for **rename both** in the interview; approved option 1 (keep `new-feature`) once the rename's cost was on the table | option 2 |
| Model (user) | `sonnet` for the implement skill | — |
| Templates (user) | Move `templates/commons/` to the implement skill (invariant 3: the pre-flight is what triggers the installer that emits them) | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Keep `new-feature` (design), add `new-feature-implement` | 9 | **Approved** |
| 2 | Rename to `new-feature-spec` + add `new-feature-implement` | 8 | Rejected — same gains as 1 at about four times the churn, and a silent `audited.json` drift in every adopted project |
| 3 | One skill, design procedure moved to `new-feature/references/design-procedure.md` | 6 | Rejected — persuasion where a piece boundary was available; the implement thread stays on `opus` with a wide territory |
| 4 | create nothing | 4 | Rejected — the cost is measured |

Shared by options 1 and 2:

- `new-feature-implement/SKILL.md`, Form 2, `model: sonnet`, class `orchestrator` with
  `overrides.new-feature-implement.write_allow: []` — the executor writes, judged by
  `agent_classes`; the main thread writes nothing. Narrower than today, where an implement run's
  main thread may write any non-frozen `docs/use-cases/**` file.
- Its own closed input table: `approved` → implement; `draft` / no spec → ❌ with the design
  command; `implemented*` → ❌; near miss → the row-5 shape.
- The design skill's row 3 becomes an error row in the row-5 shape that prints
  `/new-feature-implement UC-NNN-<slug>`. It cannot forward: `disable-model-invocation: true`
  forbids the `Skill` call.
- The entry guardrail both need (survey, `HEAD` comparison, worktree, project, disk) moves to
  one `references/entry-guardrail.md` owned by the design skill and cited by path from the
  implement skill — invariant 2.
- § Implement, the implement part of § Final report and § Operational note move to the new
  skill. `0067` and `0130` get a note: the door is now a skill, not a row.
- `templates/commons/` moves to `new-feature-implement/templates/commons/`; the old paths go to
  `export.retired`.
- `audit summary` separates design spend from implement spend by name (fact 6), which is how
  `0130` § Verification is read from now on.
- **Caveat on `sonnet`** (fact 11): the pin holds for the skill's turn. A foreground chain is one
  turn; a background executor's hand-back is a new turn that may run on the session model. The
  saving is measured, not assumed — the next implement report's main-thread line.

### Option 1 — keep `new-feature`, add `new-feature-implement` (score 9)

**Motivator:** axis 1 (cost of the implement run, maintenance).

**Pros:** every pro of the split; no rename. `history.jsonl` keeps the `new-feature` series
(design only from here on); a project's `audited.json` entry for `new-feature` keeps meaning
what it meant; none of the three territory tests' prompts change; the nine design skills'
descriptions ("Piece of the `/new-feature` pipeline") stay true.

**Cons:** asymmetric names. Muscle memory for `/new-feature UC-NNN` over an approved spec
meets an error row once.

**Points cut in the rubric:** criterion 5 (maintenance) — one more piece and a shared reference,
rounded away.

**CI:** `validate › schema` (frontmatter, class, `**Class:**` line, `model` in
`allowed_models`, `export.include`, `retired` entries absent from disk). New case in
`.claude/.ci/SkillTerritoryTest.java` (`validate › hooks-cross-platform`): a
`/new-feature-implement UC-001-x` phase refuses a `Write` to `docs/use-cases/UC-002-y/00-caso-de-uso.md`,
proved red with the override removed. `JavaTemplatesTest` path and both `templates.yml` trigger
lists follow the moved `commons/`.

### Option 2 — rename to `new-feature-spec` + `new-feature-implement` (score 8)

**Motivator:** axis 1, plus symmetric names (the user's choice).

**Pros:** names say what each flow does; the pair sorts together in autocompletion.

**Cons:** about 60 files of prose: nine design-skill descriptions, `git-publish`, the executor,
`commons-logging-installer`, `root.CLAUDE.md.example`, `project-files.md`, `scaffold.md`,
`settings.json.example`, `bug_report.yml`, README, `docs/{pt-br,en}` 00/01/03/08/09, the
`CLAUDE.md` routing table. Every file of the old directory goes to `export.retired`. Three
territory tests and one fixture change their prompts. **Silent in a generated project:** an
`audited.json` that set `"new-feature": false` stops matching and both new skills are audited
again (fact 7) — only a `migrations` note can say so, since `export` never writes that file. The
`history.jsonl` series breaks into three names. Users of generated projects and their
lessons-learned cite the old command.

**Points cut in the rubric:** criterion 5 (maintenance — rename churn with no behaviour gained
over option 1); criterion 7 (propagation — the `audited.json` drift cannot be closed by
`export`, only reported).

**CI:** option 1's, plus the `/new-feature …` prompts in `SkillTerritoryTest`,
`AgentTerritoryTest` and `SweepTest` (and its `--new-feature.md` fixture) renamed; `schema`'s
`retired` check proves the old directory is gone.

### Option 3 — one skill, design procedure in `references/` (score 6)

**Motivator:** axis 1, cost only.

**Pros:** no new command; the implement run never reads steps 1–6 and consolidation. Precedent:
this designer keeps its matrix in `references/`, deliberately out of the body.

**Cons:** the design rows must tell the model to read the reference — persuasion where today the
text is simply there. The implement thread stays on `opus` (one skill, one `model`). The
orchestrator territory stays wide during an implement run. Maintenance does not improve: one
skill still owns both flows.

**Points cut in the rubric:** criterion 4 (enforcement — a reference that may go unread);
criterion 3 (the `opus` pin keeps the implement thread's price); criterion 8 (territory wider
than the implement thread needs).

**CI:** `validate › schema` covers the frontmatter and the reference path; whether the model
reads the reference is not testable.

### Option 4 — create nothing (score 4)

The symptom is measured (facts 1–3), so leaving it rejects a known cost. Recorded so the split
is not re-proposed without reading why it was taken.

## References

| Claim | Source |
|---|---|
| Design and implement already run in separate sessions; row 3 is the door | `@.claude/decisions/0130-executor-split-by-block-group-implement-in-clean-session.md`, `@.claude/decisions/0067-implement-entry-row.md` |
| A skill's `model` holds for the rest of the turn; class sets the allowed models | `@.claude/decisions/0081-skill-model-required-per-class.md` axis 9b |
| A subagent's write is judged by its own `agent_type`, not the open phase | `@.claude/decisions/0059-agent-classes-territory-schema.md` |
| A manual skill cannot be invoked through `Skill` | `@claude-help.md` § Skills; `new-feature/SKILL.md` § Why this is Form 2 |
| A renamed skill's files are deleted from adopted projects through `retired` | `extensions.json` `$comment_retired`; the `java-patterns` entries |
| Audit opt-out is looked up by name in the project's own file | `@.claude/decisions/0111-audit-opt-out-owned-by-the-project.md`; `ArchHook.java:4432` |
| Boilerplate lives in the templates of the skill that emits it | `@CLAUDE.md` invariant 3 |
| One norm, one owning file | `@CLAUDE.md` invariant 2 |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/new-feature-implement/SKILL.md` | **New.** Form 2, `model: sonnet`, class `orchestrator`. Its own closed input table (approved → implement; draft, implemented, near miss, free text → errors with the right command), § Implement and § Operational note moved from `new-feature`, its own final report |
| `.claude/skills/new-feature-implement/templates/commons/**` | Moved from `new-feature/templates/commons/` (`git mv`, 16 files) — invariant 3: the pre-flight triggers the installer that emits them |
| `.claude/skills/new-feature/references/entry-guardrail.md` | **New.** Survey, classification, error rule, worktree, project, disk — moved out of the body, shared by both skills |
| `.claude/skills/new-feature/SKILL.md` | 918 → 725 lines. Description and argument hint say design only; row 3 becomes an error that prints `/clear` + `/new-feature-implement`; row 5 prints the command for the case's status; § End of flow ends at § Approval, which prints `/new-feature-implement`; § Implement, § Operational note and the executor step of § Integrates with removed |
| `.claude/schemas/extensions.json` | `skill_classes.orchestrator`: the skill, `overrides.new-feature-implement.write_allow: []`, the class comment. `export.skills.include`: the skill. `export.retired`: the 16 old `commons/` paths. `migrations`: `new-feature-implement-skill` — note on the command, prompt that carries an `audited.json` opt-out over to the new name |
| `.claude/skills/project-bootstrap/templates/settings.json.example` | `Skill(new-feature-implement)` in `permissions.allow`, next to `Skill(new-feature)` |
| `.claude/skills/project-bootstrap/templates/root.CLAUDE.md.example` · `references/scaffold.md` | The generated project's routing gets the implement row and the `/clear` line names both runs; the installer's trigger |
| `.claude/agents/java-spring-boot-developer.md` | Caller is `/new-feature-implement`: § Integration, § Groups, resume, final summary, failure mode, § Invocation |
| `.claude/agents/commons-logging-installer.md` | Caller and exemplar path |
| `.claude/skills/git-publish/SKILL.md` | Three callers; the guardrail's worktree step cited in the reference; commit type per caller |
| `CLAUDE.md` | Two routing rows: design → `new-feature`, implement → `new-feature-implement` |
| `.claude/.ci/SkillTerritoryTest.java` | Six cases for the implement phase |
| `.claude/.ci/TestsDeferTest.java` | Comment names the new caller |
| `.claude/.ci/JavaTemplatesTest.java` · `.github/workflows/templates.yml` | The moved `commons/` path, in the test and in both trigger lists |
| `README.md` · `docs/{pt-br,en}/00,01,03,07,08,09,README` | Flow 2 is `/new-feature-implement`; the commons path; audit names |
| `.claude/decisions/0067-implement-entry-row.md` · `0130-…` | Note pointing here |

Goes to the generated project: **yes** — `new-feature-implement` is in `export.skills.include`,
`export.retired` deletes the old `commons/` copies from an adopted project, the exported
`settings.json` is overwritten whole on update, and `migrations` prints the note once.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · hooks-cross-platform › SkillTerritoryTest` | An implement phase opens on `/new-feature-implement` (not on its `/new-feature` prefix), refuses the main thread's writes to `docs/use-cases/**` and `src/**`, admits the executor's write by its own class, refuses a `build`-class `Skill` call, admits the pre-flight's `test-architect` | Green on the tree. Red with `overrides.new-feature-implement` removed: `docs/use-cases/ is not the implement thread's — blocked` fails by name, the rest pass. Restored, green |
| `validate › schema` | Both bodies' frontmatter, `model` in `allowed_models`, class sections, `export.include`, `retired` entries absent from disk, the `migrations` entry's fields | Green |
| `templates · java-templates` (path-filtered on the moved `commons/**`) | The 16 exemplars compile and their 3 tests pass from the new path | Green locally: 19 template files compile, 3 test classes pass |

Not testable in CI: whether the `sonnet` pin covers the whole chain (fact 11). The next implement
run's audit report — now under its own name, `--new-feature-implement` — answers it.
