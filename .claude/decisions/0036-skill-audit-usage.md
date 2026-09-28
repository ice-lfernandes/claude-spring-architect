# 0036 · Reading the trail back is a skill — and the one the trail doesn't record

- **Date:** 2026-09-15
- **Scenario:** "crie a skill /audit-usage em uma new branch feature" — the option
  `0035` scored at 7 and deferred, now that `history.jsonl` exists to be queried.
- **Decision:** Form 2 skill `.claude/skills/audit-usage/SKILL.md`
  (`disable-model-invocation: true`), copied into the generated project by step 6.7, plus
  `audit.exclude_skills` in `.claude/schemas/extensions.json` and a guard in
  `ArchHook.java auditPrompt` so invoking it closes the open run instead of opening one.
- **State:** approved by Lucas Fernandes, on 2026-09-15
- **Supersedes:** nothing. Completes `0035`, whose option 2 was rejected as premature.

## Why now, when `0035` rejected exactly this

`0035` rejected `/audit-usage` under anti-pattern 9 — a query skill over an empty
ledger. The rejection was about **sequencing**, not about the form: the record itself
said the ledger existed "so it can be born without touching the capture". That
precondition is now met — `history.jsonl` is written on every close — and the capture
was in fact not touched, only the skill-set data it already reads.

## Interview

Not re-run. The scenario, territory, and destination were fixed in `0035`; only the axes
that change with a *reader* are listed.

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — concrete symptom | "What has this project cost me across all runs, and which skill eats the budget" — no single report answers it | — |
| 2 — trigger | Human asking, after the fact | — |
| 4 — territory | `.claude/audit-usage/` in the generated project | — |
| 6 — isolation | None: output is the deliverable, tools are two, model unchanged | 3 |
| 7 — mandatoriness | **Optional** — nothing breaks if nobody ever reads the trail | hook, `permissions.deny` |
| 8 — destination | Generated project. Here it correctly reports the trail is off | — |
| 10 — form | Procedure over data, not a norm | 4, 5 |

Axis 7 is again the deciding one, and this time it points the other way from `0035`:
the *record* must exist always, so it is a hook; the *reading* is on demand, so it is a
skill. The two halves of the feature land on opposite sides of the persuasion line, and
that is the whole reason this is a second record rather than an edit to the first.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Form 2 skill + `exclude_skills` in the schema | 9 | **Approved** |
| 2 | Form 2 skill, no exclusion — let the trail record its own reads | 6 | Rejected — self-reference |
| 3 | Form 1 skill (auto-invocable) instead | 5 | Rejected — silently sidesteps the problem |
| 4 | No skill: a `Commands` row with `cat`/`ls` | 4 | Rejected — leaves the aggregation undone |

### Option 1 — skill + exclusion (score 9)

Everything the per-run report cannot do: sum across runs, rank spend per skill, compute
a failure rate, and say which report to open. That is a procedure over data — Form 2's
definition.

The exclusion earns its complexity twice:

1. **It stops the feedback loop.** Reading the trail would otherwise append
   `<ts>--audit-usage.md` to a versioned directory, and the next read would be mostly
   reads. Reports are one file per run and are never overwritten, so this grows without
   bound.
2. **It closes the open run — the real find.** Within a single session `Stop` renders
   with `closing=false`, so every report reads `⏳ em andamento` until `SessionEnd` or
   the next `/command`. Routing `/audit-usage` through `auditRender(..., true)` means the
   act of asking for the report is what finalizes it. Without this the skill's most
   common use — "how did that go?", right after a run — always showed a partial report.

Point 2 was not anticipated; it surfaced while reading `auditRender` and is why the
guard closes the run rather than merely skipping it.

### Option 2 — no exclusion (score 6)

Simplest diff, and defensible in the abstract: who read an audit trail is itself
auditable. Rejected because the value is near zero against a cost that compounds — and
because it leaves `⏳ em andamento` unfixed.

### Option 3 — auto-invocable (score 5)

Dropping `disable-model-invocation` would sidestep the hook (which keys off that exact
flag) with a one-line frontmatter change and no schema or Java edit. Rejected for two
reasons. It overloads the flag: the field means "only a human invokes this", and using
its *absence* to mean "don't audit this" ties two unrelated decisions to one bit —
invariant 2, differently spelled. And it doesn't close the open run either, so the
`⏳ em andamento` defect survives.

### Option 4 — no skill (score 4)

`ls` and `cat` are already in the root `CLAUDE.md`. They put a JSONL ledger in front of a
human and call it a report.

## Design notes

- **The audited set stays data.** `isOrchestrator` still reads frontmatter; the new
  `isAuditExcluded` reads `audit.exclude_skills`. Neither hardcodes a name in Java —
  invariants 7 and 10. A future reader skill is excluded by editing JSON.
- **The skill writes nothing.** Reading the audit cannot change it, and pruning old
  reports stays the user's command to run. Stated as a hard boundary in the body, since
  nothing enforces it mechanically.
- **Costs are never recomputed.** Runs recorded while `pricing.json` held `null` carry no
  `cost` field; the skill must treat absent as unknown and say how many rows it left out
  of the total. Absent summed as zero would under-report the bill and look authoritative
  doing it — the same discipline as invariant 8, restated inside the skill because the
  copy in the generated project cannot cite this repo's `CLAUDE.md`.
- **No `templates/`, no `references/`, no citation to `blueprints/` or `decisions/`.**
  The skill copies verbatim in step 6.7, with none of the three corrections that step
  applies to the others. That was a constraint on the writing, not a happy accident.
- **`arch-doctor` is excluded too — the concept is "observer", not "trail reader".**
  Left open on the first pass, closed on review. The measurement settled it: today a
  mid-run `/arch-doctor` *already* closes the open run (any second `/command` does) and
  then opens one of its own, whose only product is an empty report at the next `Stop`.
  Excluding it keeps the close, byte for byte, and drops the empty report — strictly
  better, with no case where the old behaviour was preferable. So `exclude_skills` holds
  the skills that **observe** rather than change the project: `audit-usage` reads the
  trail, `arch-doctor` reads the setup. The feedback loop is specific to `audit-usage`;
  the empty report is common to both, and is the general reason for the list.

## References

| Source | Used for |
|---|---|
| `@.claude/decisions/0035-auditoria-execucao-hook.md` | The deferral this record closes; the ledger contract |
| `.claude/hooks/ArchHook.java` §`auditRender`, §`ev` | Field-by-field shape of `history.jsonl` and of the report |
| `@.claude/skills/claude-code-architect-designer/SKILL.md` | Decision matrix §2, score rubric, anti-pattern 9 |
| `@CLAUDE.md` invariants 2, 7, 9, 10 | Single owner, data not code, self-contained project |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/audit-usage/SKILL.md` | Created |
| `.claude/schemas/extensions.json` | `audit.exclude_skills`, folded into the block's single `$comment` |
| `.claude/hooks/ArchHook.java` | `isAuditExcluded` + guard at the top of `auditPrompt` |
| `.claude/skills/project-bootstrap/SKILL.md` | Step 6.7 table row, both verification sweeps, `Writes` glob |
| `.claude/skills/project-bootstrap/templates/root.CLAUDE.md.example` | `Commands` row now `/audit-usage`; routing row names the skill; the stale "Today four are copied" replaced — it had said four while the table listed eleven, so the replacement carries no count at all |
| `CLAUDE.md` | Routing row; the `audit`-is-off pitfall extended with the exclusion |
