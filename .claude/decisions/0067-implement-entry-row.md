# 0067 · The second half of the pipeline gets a front door — a `/new-feature` input row, not a new skill

- **Date:** 2026-09-28
- **Scenario:** lessons-learned-014 § 3 — everything `/new-feature` § End of flow owns happened
  outside any run, reconstructed by hand, because there is no supported way to reach the
  executor offer once the run that approved the spec has ended.
- **Decision:** No new piece. A row in `/new-feature`'s input table:
  `EXACT` + `FOLDER` + status `approved` enters § End of flow at the delegation, skipping steps
  1–6 and consolidation.
- **State:** approved by Lucas Fernandes, on 2026-09-28.

*Note (0130):* this row is now the **only** door to implementation. A design run ends at
§ Approval, commits the spec and prints `/clear` + `/new-feature UC-NNN-<slug>`; the *Implement
now / Not now* question is gone.
@.claude/decisions/0130-executor-split-by-block-group-implement-in-clean-session.md.

## The gap, stated as a lifecycle and not as an omission

Input row 3 refuses an `approved` argument outright — *"approved spec is immutable — describe
the change as a new feature"*. That is the right answer for **editing** a spec and the wrong
answer for **implementing** one. Put it next to the pipeline's own closing advice (run `/clear`
before the next feature) and the normal, recommended life of an approved spec reads:

1. the run that approved it ends,
2. the context is cleared,
3. from then on there is **no supported way to reach the executor offer**.

Everything § End of flow guarantees then becomes something the model either remembers or drops:
the one-time setup pre-flight (the ArchUnit and commons-logging greps), the `Agent` delegation
with the spec path, the `CHANGELOG.md` writes consolidation step 2 mandates, the final report
with its four mandated findings, and the `git-publish` chaining with a `feat(UC-NNN-slug): …`
message shape. The run this record comes from happened to keep them only because the user had
invoked `/new-feature` minutes earlier for an unrelated argument and the skill text was still in
context. That is an accident, not a mechanism.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | Five obligations re-derived from memory outside any run, because the argument that names them is refused | 9 (create nothing) |
| 2 — trigger | The user types `/new-feature UC-NNN-slug` over a spec already approved | 1 (auto-invocable): it is a side effect on `src/`, always manual |
| 5 — nature | A sequence of steps that already exists, written down, in one self-contained section | 4 · 5 (a norm cannot carry a procedure) |
| 9 — integration | § End of flow is already a block with no dependency on steps 1–6: it reads the spec and delegates | A second owner for the same sequence |
| — entry point | Straight to the delegation, no question: whoever typed the command over an `approved` spec has already asked for it | Re-asking *Implement now / Not now* |
| 8 — destination | Both — `new-feature` is in `export.skills.include`, so the row ships with it | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | A row in `/new-feature`'s input table | 8 | **Approved** |
| 2 | A new `/implement-spec` skill of class `orchestrator` | 6 | Rejected — a second owner for a sequence that exists once, plus a class, a territory, an `export` entry and a routing row |
| 3 | Create nothing; document the manual sequence somewhere | 3 | Rejected — that is what the run already did from memory, and it is what dropped nothing only by luck |

### Option 1 — the row (score 8)

**Motivator:** axis 9. § End of flow's "Executor offer" is already self-contained: it does not
read anything steps 1–6 produced beyond the spec on disk. Reaching it from the input table costs
one row and no new structure.

**Shape.**

- A row between the current 2 and 3: `EXACT`, `FOLDER`, status `approved` → **implement**: run
  § End of flow from the delegation onwards, skipping steps 1–6, consolidation and the approval
  question.
- Row 3 narrows to `implemented` and `implemented-blocked` — the two states where the work is
  already done and the answer really is "describe the change as a new feature". Its error text
  stops being wrong for the third case it used to swallow.
- **No question before delegating.** The command *is* the request. § Executor offer keeps its
  `AskUserQuestion` for the path that arrives there from an approval in the same run, where the
  user has not yet said whether to implement.
- The entry guardrail (worktree, project, disk) runs unchanged, and matters more here than on a
  design run: this path writes `src/`.

**Pros:** one owner for the sequence, so a fix to the pre-flight or to the report's four
mandated findings lands once. No class, no territory, no `export` entry, no routing row. The
guarantee that was lost is exactly the guarantee restored — the same block, reached by a
supported path.

**Cons:** `/new-feature`'s input table now carries two success paths with quite different
effects, one docs-only and one that writes `src/` and chains `git-publish`. The table is the
only thing keeping them apart, which raises what a wrong classification costs. Criterion 5.

**Points cut in the rubric:** § 8 criterion 5 (one command, two effect profiles, separated by a
table row).

### Option 2 — `/implement-spec` (score 6)

A skill of class `orchestrator` owning exactly that sequence, which `/new-feature` also calls at
its end. Cleaner as a name: the two halves of the pipeline get one front door each.

Rejected on cost and on invariant 2. The sequence exists once today; a second owner means either
moving it (and `/new-feature` then calls out to it, which is fine) or copying it (which
diverges). Moving it is the defensible version, and it still buys a `skill_classes` entry, a
`write_allow`, an `export.skills.include` entry, a routing row in `@CLAUDE.md`, a second entry
guardrail, and a second place where "does the worktree decision apply" has to be answered — for
a gap whose whole content is "one argument shape is refused where it should be accepted".

Worth revisiting only if a second caller appears for that sequence.

### Option 3 — create nothing (score 3)

Write the manual sequence in a reference and rely on whoever implements a spec to follow it.
That is precisely what this run did, from a context that happened to hold the skill text, and
the record exists because nobody can tell which of the five obligations a less lucky run would
have dropped.

## References

| Claim | Source |
|---|---|
| Row 3 refuses `approved` and that is the wrong answer for implementing | `.claude/skills/new-feature/SKILL.md` § Entry guardrail, input table |
| § End of flow owns the pre-flight, the delegation, the changelogs, the report and the `git-publish` chaining | `.claude/skills/new-feature/SKILL.md` § End of flow |
| A skill with a side effect is manual, never auto-invocable | `references/decision-matrix.md` § 4 |
| One owner per procedure; a second copy diverges on the first update | `@CLAUDE.md` invariant 2 |
| A new skill owes a class, a territory and an `export` entry | `@CLAUDE.md` invariant 9 · `skill_classes` |
| `new-feature` travels to the generated project | `export.skills.include` |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/new-feature/SKILL.md` | the new input row; row 3 narrowed to the two closed states; § End of flow says which entry points reach the delegation and which of them asks first; the usage block gains the third form |
| `.claude/skills/new-feature/SKILL.md` frontmatter | `description` names the implement path, or the row is unreachable by anyone reading the listing |
| `CLAUDE.md` routing table | the `/new-feature` row says it also implements an approved spec |
| `docs/03-new-feature.md`, `docs/en/03-new-feature.md` | the input table is reproduced there and would otherwise disagree |

Goes to the generated project: **yes** — `new-feature` is in `export.skills.include`, and the
row travels inside its `SKILL.md`. No `export` change needed.

**Restart warning:** none. No hook registration, no `settings.json`.

## What shipped, beyond the row itself

Three consequences the proposal implied and the edit made explicit:

- **Row 3 split in two.** The old row swallowed three statuses with one error. It is now row 3
  (`approved` → implement) and row 4 (`implemented` / `implemented-blocked` → `already
  implemented — describe the change as a new feature`). The old text — *approved spec is
  immutable* — was literally true and told the wrong person the wrong thing.
- **Rows renumbered**, 4→5 through 7→8. One citation elsewhere pointed at a row by number
  (`java-spring-boot-developer.md`, the open-case block); it now names the row's condition
  instead, so the next insertion does not silently invalidate it.
- **Two headings in § End of flow stopped claiming "always asked".** § Approval is asked on
  every run that consolidated a spec — not on a run that entered at row 3 — and § Executor offer
  asks only when the run itself just approved. A table at the top of § End of flow states which
  entry point reaches which.

## Verification

`claude plugin validate .claude/skills` passes and `java .claude/hooks/ArchHook.java schema`
passes — the latter is what reads the frontmatter this change edited. There is no mode that
executes an input table, so the row's behaviour is prose verified by reading: the eight rows are
mutually exclusive on `(EXACT, FOLDER, status)` and every status a spec can carry is matched by
exactly one of rows 2, 3 and 4.
