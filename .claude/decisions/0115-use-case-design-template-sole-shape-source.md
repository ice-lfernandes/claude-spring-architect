# 0115 · `use-case-design`'s template is the only source of the parent spec's shape

- **Date:** 2026-10-02
- **Scenario:** Issue #79, triaged at `7861f8b`: `SKILL.md:217` ("the header says so")
  orphaned after decision 0094 removed the `Partial status` line from the template, and
  nothing tells `use-case-design` the template is the only source of the spec's shape — old
  sibling specs can be copied as format exemplars.
- **Decision:** Option A — two sentences in `.claude/skills/use-case-design/SKILL.md` — plus,
  at the user's request on approval, the `migrations` entry
  `use-case-spec-partial-status-line` in `.claude/schemas/extensions.json`, so `/arch-adopt`
  tells an existing project about the stale line and hands it a prompt that cleans only the
  folders the guard leaves open.
- **State:** approved by Lucas Fernandes, on 2026-10-02

## Reproduced on disk before classifying

Checked against `7861f8b` by `issue-verifier` (layer `static`); no commit since touches
`.claude/skills/use-case-design/`. Only the confirmed rows are kept — the issue's proposed fix
and its unproven claim (that the model *did* copy a sibling in the reported run) are not inputs.

| Claim | On disk |
|---|---|
| Step 8 generates `00-caso-de-uso.md` from the template | Confirmed, `use-case-design/SKILL.md:192-193` |
| The template has no `Partial status` line | Confirmed at HEAD, `v0.14.0` and `v0.14.3`; removed in `e6e7ec6` (`0094`, option 1.1), first shipped in `v0.9.1` |
| No runtime file writes `Partial status` | Confirmed — it appears only in `0093`, `0094` and `lessons-learned-016.md`, all history |
| `SKILL.md` still promises a status in the header | Confirmed, `SKILL.md:217` — "It's an incomplete spec, not an invalid one — and the header says so." Dates from `55588bd`; `0094`'s propagation table listed only the template and the 13 examples, so the sentence was orphaned |
| Nothing names the template as the only source of the file's shape | Confirmed. Step 4 (`SKILL.md:151-156`) sends the model into existing approved specs as a contract, and `allowed-tools` grants `grep`/`tail`, which can print a sibling file in full |
| Nothing removes the stale line from existing specs | Confirmed — `arch-adopt` writes only `.claude/**`, `.gitignore`, `CLAUDE.md`; no `migrations` entry; `doctor` has `uc_references`/`bl_references` only |

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | Orphaned sentence confirmed on disk; pre-`v0.9.1` specs in adopted projects still carry the dead line | create nothing |
| 5 — nature | A procedure instruction inside a skill that already owns the file | new skill, rule, agent |
| 7 — mandatoriness | Persuasion is enough — the cost of a miss is one dead header line, minutes to remove | Form 7a/7c (`schema`/`guard` check on retired strings), Form 8 |
| 8 — destination | Both — `use-case-design` is in `export.skills.include` | — |
| 9 — scope | `use-case-design` only — the one observed case; no other partial template has a retired line on record | the same sentence across every design skill |
| — on approval | Add a note when `/arch-adopt` updates a generated project, to fix its current specs | — (adds the `migrations` entry to A; D stays rejected for the frozen folders) |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| A | Two sentences in `use-case-design/SKILL.md`: replace the orphaned promise at `:217`, and state at step 8 that the template is the only source of the shape | 8 | **Approved**, with a `migrations` entry added |
| B | A — plus the same sentence in every design skill that generates a partial | 5 | Rejected by axis 9 — anticipation, no observed case outside `00-caso-de-uso.md` |
| C | `schema` or `guard` refuses a write of `00-caso-de-uso.md` carrying a string the template retired (list in `extensions.json`) | 2 | Rejected by axis 7 — a process per write against one dead string; the mirror of invariant 6 |
| D | `/arch-adopt`, `/arch-doctor` or a `migrations` entry strips the line from existing specs | 1 | Rejected — `guard.frozen_statuses` refuses edits in every `approved`/`implemented` folder (`0066`); `arch-adopt` would leave the territory `0093` set; `doctor` never writes |
| E | create nothing | 2 | Rejected — `:217` states something false at HEAD |

### Option A — `use-case-design/SKILL.md` (score 8)

**Motivator:** axes 1 and 5 — the owner of the file states its shape wrongly and never names
its source.

**Pros:** fixes the owner; same mechanism `0094` chose (prose, no hook). Once the template is
named as the only source, old specs stop mattering as exemplars, so they need no migration.
Travels to every generated project on the next `/arch-adopt`.

**Cons:** persuasion — a model that ignores it still copies. Accepted by axis 7.

**Shape, per the verifier's correction:** step 8 names the template as the only source; it
adds no new reason to read siblings. Step 4 already reads approved specs as a contract
(aggregate, names), and the number comes from the folder injection (step 7).

**Points cut in the rubric:** criterion on guarantee — persuasion where a check was possible;
taken deliberately per axis 7.

**CI:** `validate` · `design` › `frontmatter schema` already covers the skill (class, sections,
export entry). The new sentences are persuasion: nothing testable in CI, because no check can
prove a model does not imitate a file it read.

### Option B (score 5)

Same text in `domain-modeling`, `persistence-architect`, `rest-api-architect`,
`security-architect`, `messaging-architect`, `jobs-architect`, `test-architect`. Seven files
for a failure seen in one. Reopen if a second partial template retires a line.

### Option C (score 2)

A list of retired strings in `extensions.json`, read by `schema` over `docs/use-cases/**` or
by `guard` at write time. Blocks on a cosmetic line; the list only grows.

### Option D (score 1)

Every pre-`v0.9.1` spec in the reporter's project is `implemented` — frozen. `lessons-learned-016`
§ 1 already recorded that this line "becomes permanent at the exact moment it becomes wrong".

**What survives of D, on approval:** the `migrations` channel, the one `0104` built for a
convention change that leaves artifacts behind, carries the news without writing anything.
`arch-adopt` prints the `note` and the `prompt` and runs neither. The `prompt` asks the user to
approve, splits the files by their folder's `status:`, and deletes the line only in folders
`guard.frozen_statuses` leaves open. For frozen files it prints path and line, so the user can
delete the line by hand outside Claude Code or keep it as history. It is never told to flip a
`status:` to get past the guard. The `note` says the line is harmless where it stays, because
step 8's sentence already stops it from spreading into new specs.

## References

| Claim | Source |
|---|---|
| The line was removed on purpose; `status:` and the files on disk answer it | `@.claude/decisions/0094-lessons-learned-016-lifecycle-and-publish.md` option 1.1 |
| Frozen spec folders refuse edits | `@.claude/decisions/0066-spec-state-machine.md`, `guard.frozen_statuses` in `extensions.json` |
| `arch-adopt`'s territory | `@.claude/decisions/0093-lessons-learned-016-fact-sources.md`, `skill_classes.build.overrides.arch-adopt` |
| A hook against a failure nobody needs guaranteed is waste | `@CLAUDE.md` invariant 6 (mirror) |
| Triaged issue's confirmed rows are the reproduction | `@.claude/decisions/0103-issue-filing-and-skeptical-triage.md` |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/use-case-design/SKILL.md` § Spec structure | "and the header says so" → which partials exist is read from the folder, never written into the file |
| `.claude/skills/use-case-design/SKILL.md` step 8 | The template is the only source of the file's shape; an existing `00-caso-de-uso.md` is a contract, never a format exemplar |
| `.claude/schemas/extensions.json` `migrations.entries` | `use-case-spec-partial-status-line`, the seven blueprints the other entries name (`custom-template` excluded, as in every entry) |

Goes to the generated project: **yes** — `use-case-design` is in `export.skills.include`, and
`extensions.json` is in `export.copy`. `/arch-adopt` shows the migration once, on the first
update after this change; a project generated afterwards carries the id and never sees it.

## CI coverage

`validate` · `design` › `frontmatter schema` already covers both files. It checks the skill's
class, sections and export entry, and the migration's `required_fields`, `id_pattern` and
`blueprints`. No test added: the two sentences are persuasion, and no check can prove a model
does not imitate a file it read.

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · design › frontmatter schema` | The migration entry is well-formed and names existing blueprints | green on the tree (exit 0) · red with the id changed to `Use_Case_Bad`: exit 2, "migrations.entries[6] Use_Case_Bad — id does not match [a-z0-9]+(-[a-z0-9]+)*" |
