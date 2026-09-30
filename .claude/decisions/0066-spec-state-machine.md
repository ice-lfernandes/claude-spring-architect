# 0066 · The spec's state machine gets a third state, a written order, and a toggle that survives the close

- **Date:** 2026-09-28
- **Scenario:** lessons-learned-014 §§ 4 and 5 — a spec closed to `implemented` before its
  checklist was ticked, freezing 23 unchecked boxes forever; and two halves of the executor's
  own contract disagreeing about what status an implemented-but-unreachable case leaves behind,
  with the hook's state machine admitting neither answer's escape route.
- **Decision:** Form 7c plus prose in two bodies — `guard`'s status data in
  `.claude/schemas/extensions.json`, the two exemption checks in `.claude/hooks/ArchHook.java`,
  the order and the precedence in `.claude/agents/java-spring-boot-developer.md`, the lifecycle
  and input tables in `.claude/skills/new-feature/SKILL.md`.

## Why §§ 4 and 5 are one decision and not two

Both are the same object: the set of `status` values a spec may carry, and which writes the
frozen-folder guard admits for each. § 4 asks for one more admissible write on an existing
value; § 5 asks for one more value. Splitting them would mean touching
`guard.frozen_statuses`, `isStatusClose` and `isChecklistToggle` twice, and the second pass
would have to re-read the first's record to know what the state machine now is. Invariant 2
applies to the state machine as much as to a norm: one owner, one change.

## § 4 — what happened, and what is irreversible

`UC-003-spec.md` reads `status: implemented` with 23 `[ ]` and zero `[x]`, although all 19
fixed steps plus M1–M3 are done. The frozen-folder guard admits two edits inside a closed
folder — `isStatusClose` and `isChecklistToggle` — and both require the status to still be
`approved`. Closing the status first therefore froze the checklist permanently, and the
attempted revert was refused too.

No document states the order. `ArchHook.java` implements exactly one valid sequence (tick, then
close) and neither `java-spring-boot-developer.md` nor `/new-feature`'s lifecycle table says so.

**Note on the existing damage:** the 23 boxes in that project's spec become tickable the moment
this ships, because the toggle stops depending on the status. Nothing here rewrites them — that
is a write in the target project, not in this meta-repository.

## § 5 — the contradiction, stated precisely

Three statements, all currently true, and no two of them compatible:

1. `java-spring-boot-developer.md` § *An approved use case left unreachable* — "Blocks 1-3 are
   on disk and compile; the spec stays `approved`."
2. The same file, § step 19 — the spec closes from `approved` to `implemented` on a green build.
3. `ArchHook.java guard` admits the `approved → implemented` transition and no other, so a
   close made under 2 cannot be undone to satisfy 1.

The run followed 2, closed the spec, could not revert, and the hook's own advice — *set
`status: draft` by hand* — is wrong for this case: `draft` is an **open** case per the lifecycle
table, so it would block the next `/new-feature` at input row 6.

The concept "implemented but not reachable end to end" exists in prose in three places and in
the state machine in none. That is what makes it unrepresentable rather than merely
undocumented.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | 23 boxes frozen unticked in a real spec; a close that could not be reverted; `UC-002` left unreachable with no state that says so | 9 (create nothing) |
| 7 — mandatoriness | Mixed, and that is the whole shape of this decision: *which writes a frozen folder admits* cannot fail (hook), *which order to write them in* is a procedure the executor follows (prose) | Neither alone |
| 15 — reaction | The hook admits more; it does not newly block anything | Form 8 |
| 16 — existing mode | `guard` holds both exemptions already; what changes is the data they read | A new mode |
| — § 4 scope | Both fixes: the order in the contract **and** the toggle admissible after the close | Either alone |
| — § 5 form | A third value, `implemented-blocked`, admitted as a legal close and naming the blocking case | Precedence in prose alone |
| — § 5 routing | Not open (row 6 lets the next run start) and refused by row 3 exactly like `approved` and `implemented` | Treating it as open |
| 8 — destination | Both, automatically: `ArchHook.java` and `extensions.json` travel whole with `export`, and both edited bodies are already in `export`'s include list | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `implemented-blocked` as data + toggle freed from the status + the order written down | 8 | **Approved** |
| 2 | Precedence in the executor's contract only — the spec stays `approved` | 6 | Rejected — leaves green code on disk under a status that says it was never implemented |
| 3 | Precedence plus an `implemented → approved` revert in the hook | 6 | Rejected as the answer, folded in as a rider: the revert is worth having, the missing state is not fixed by it |
| 4 | Order in the contract only, hook untouched | 4 | Rejected — invariant 6; the next wrong order freezes another checklist for good |

### Option 1 (score 8)

**Motivator:** axes 7 and 16 together. Two of the three fixes are data the hook already reads;
one is genuinely a procedure and belongs in prose.

**Shape — the data.** `guard` stops carrying transition rules inside the Java (invariant 10):

- `frozen_statuses` gains `implemented-blocked`.
- `status_transitions` — a map from the status a spec currently carries to the values a close may
  write. `approved → [implemented, implemented-blocked]`, and nothing else, so `draft` is still
  not closable by a tool and neither closed state walks further on its own.
- `checklist_toggle_statuses` — every status whose spec still admits `[ ]` → `[x]`. All three of
  `approved`, `implemented`, `implemented-blocked`: a toggle is monotone and carries none of the
  risk immutability exists to prevent, which is § 4's second fix expressed as data rather than
  as a condition in the Java.

`isStatusClose` and `isChecklistToggle` read those two lists instead of comparing against the
literal `approved`. The `Stop` sweep's `isSpecEditLegalOnDisk` reads them too, so the three
callers cannot drift apart.

**Shape — the prose.**

- `java-spring-boot-developer.md`: the checklist is ticked **before** the status line is closed,
  and the status flip is the last write the agent makes to the folder. Plus the precedence its
  two halves lacked — an approved case left unreachable closes to `implemented-blocked` naming
  the blocking case, not to `implemented`, and not by leaving the spec untouched.
- `/new-feature`: the lifecycle table gains the row, and input row 3 lists the third value next
  to the two it already refuses.

**Pros:** the state that three documents describe becomes representable, so the report stops
being the only place it exists. § 4's freeze becomes impossible rather than discouraged. Every
rule stays in one place — the hook reads the data, the prose says the order, and nobody
duplicates the other.

**Cons:** a new status value is a string eight places already spell literally
(`use-case-design` § 138, § 151, § 232; `new-feature` § 123, § 128, § 199; the executor's
§ 109, § 129). Each is a read of "approved or implemented" that now has a third member, and one
missed leaves a spec in a state some piece treats as unknown. Criterion 5.

**Points cut in the rubric:** § 8 criterion 5 (a third value to keep in step across eight
literal readings).

### Option 2 — precedence in prose only (score 6)

Fix the contradiction by picking a winner: the unreachable case wins, the spec stays `approved`,
the blocker goes into the report and the altered case's `CHANGELOG.md`. Zero Java, zero data.

Rejected because it keeps the information loss that § 5 is about. A spec reading `approved` over
code that is on disk, green and committed misdescribes the repository, and `/new-feature`'s own
survey shows it as a case never implemented. It also leaves the irreversible close of § 4's
sibling shape untouched: a wrong close still has no way back.

### Option 3 — precedence plus a revert (score 6)

Admit `implemented → approved` so a mistaken close has an exit. Rejected as the answer: it makes
the wrong state fixable instead of making the right state expressible. **Folded in as a rider of
option 1** — `status_transitions` is the natural place for it, and the run that could not revert
is the evidence it is worth one line of data.

### Option 4 — order in the contract only (score 4)

State the sequence and leave the hook as it is. Capped for straining invariant 6: the failure is
observed, the guarantee is one list away, and prose about write order is exactly what the
executor already had — in the form of nothing at all.

## References

| Claim | Source |
|---|---|
| A frozen folder admits `isStatusClose`, `isChecklistToggle` and an exempt basename, and the first two require `approved` | `ArchHook.java` `isStatusClose`, `isChecklistToggle` |
| `draft` is an open case, so reopening a closed spec blocks the next run | `.claude/skills/new-feature/SKILL.md` § Spec lifecycle, input row 6 |
| The executor's two halves disagree about an unreachable case's status | `.claude/agents/java-spring-boot-developer.md` § *An approved use case left unreachable* vs step 19 |
| Names, paths and patterns a mode reads live in `extensions.json`, never in the Java | `@CLAUDE.md` invariant 10 |
| One owner per rule; others cite it | `@CLAUDE.md` invariant 2 |
| A rule that must always hold is a hook, not prose | `@CLAUDE.md` invariant 6 |
| A toggle is monotone, which is why it was made basename-exempt's sibling rather than blocked | `.claude/decisions/0060-lessons-learned-013-shipping-defects.md` |

## Propagation

| File | Change |
|---|---|
| `.claude/schemas/extensions.json` | `guard.frozen_statuses` gains `implemented-blocked`; new `guard.status_transitions` and `guard.checklist_toggle_statuses`, with the `$comment` explaining why a toggle no longer depends on the status |
| `.claude/hooks/ArchHook.java` | `isStatusClose` and `isChecklistToggle` read the two lists; `isSpecEditLegalOnDisk` (the `Stop` sweep) reads them too |
| `.claude/agents/java-spring-boot-developer.md` | the write order (checklist first, status last); the precedence for an unreachable case, closing to `implemented-blocked` with the blocker named; step 19 and the final-report block updated |
| `.claude/skills/new-feature/SKILL.md` | lifecycle table row; input row 3 lists the third value; § End of flow's expectation of `implemented` |
| `.claude/skills/use-case-design/SKILL.md` | the three readings of "approved or implemented" gain the third value |
| `CLAUDE.md` § Known pitfalls | the order, and the fact that a toggle now survives the close |

Goes to the generated project: **yes** — `ArchHook.java` and `extensions.json` whole through
`export`, the two skills and the agent through `export`'s include list.

**Restart warning:** none. No hook registration changes; `ArchHook.java` is read per invocation.

## Verification run before reporting it done

The state machine was exercised by hand against a sandbox, one `guard write` payload per
transition, plus the `Stop` sweep.

| Spec's status on disk | Edit | Result |
|---|---|---|
| `approved` | → `implemented` | exit 0 |
| `approved` | → `implemented-blocked` | exit 0 |
| `approved` | → `draft` | exit 2 — reopening stays a by-hand act, as the guard's own message says |
| `implemented` | → `approved` | exit 0 — the revert that run did not have |
| `implemented` | → `implemented-blocked` | exit 2 — no sideways move between closed states |
| `implemented` | `[ ]` → `[x]` | exit 0 — § 4's fix; this was the write that was impossible |
| `implemented` | a body line rewritten | exit 2 |
| `implemented-blocked` | `[ ]` → `[x]`, and → `approved` | exit 0 both |
| `implemented-blocked` | → `implemented` | exit 2 |
| `implemented-blocked` | `CHANGELOG.md` written in the folder | exit 0 |

`guard sweep` admits a spec committed `approved` and found on disk at `implemented-blocked`
with its boxes ticked — the executor's whole legal close in one turn, through the
`git show HEAD:` comparison.

**One asymmetry worth recording.** The sweep cannot see an `approved → draft` reopen: by the
time it runs, the file says `draft`, which is not in `frozen_statuses`, so the folder is no
longer frozen and there is nothing to compare. `guard write` blocks that same edit, because at
`PreToolUse` the disk still says `approved`. Reopening by hand is the documented path anyway —
this is the shape of the sweep being detection, not prevention, showing up in one more place.

`claude plugin validate .claude/skills` passes, `schema` passes, `doctor` reports
`Hooks ✅ 18 registration(s) across 4 event(s)` — unchanged, as intended: no registration moved.
