# 0072 · The near-miss answers with the real slug, and the error rule bans side effects rather than output

- **Date:** 2026-09-28
- **Scenario:** lessons-learned-014 § 12 — `/new-feature UC-003-spec` answered
  `use case not found; to create one, describe the feature` while
  `docs/use-cases/UC-003-initiate-kyc-verification/` sat on disk. The model then printed the
  fixed error **and** a survey **and** two suggested commands, against the rule *"After it: no
  skill call, no write, no question."*
- **Decision:** No new piece. An error row for the near miss, and the error rule rewritten to
  forbid side effects rather than output.
- **State:** approved by Lucas Fernandes, on 2026-09-28.

## Two findings that belong together

**The message was literally true and practically wrong.** No folder is named `UC-003-spec`, so
"not found" is accurate about what was typed. The argument was the basename of the spec file
*inside* an existing folder — the case exists, the person named it the way the file is named.
An answer that is true about the string and useless about the intent is the shape worth fixing.

**The rule was violated because it was slightly wrong, not because it was forgotten.** The extra
output was the survey — a read — and it was what the user actually needed. A rule that is broken
every time it gets in the way is a rule to correct: "no output" was never the property worth
protecting; "no side effect" is. The pipeline writes `src/` two rows above, and *that* is what
must not happen after an error.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | A true, useless error over an existing case; a rule broken in the direction of being helpful | 9 (create nothing) |
| — scope | Any argument carrying a `UC-NNN` that resolves to exactly one folder — `UC-003`, `UC-003-spec`, `UC-003-spec.md`, a wrong slug with the right number | An `EXACT`-only row, which leaves the most common near miss (typing just the number) in "ambiguous" |
| — action | Report the real slug and status, plus the exact command, and stop | Resolving the argument and carrying on: the argument was wrong, two rows above write `src/`, and the skill's own next paragraph forbids inferring intent |
| — error rule | "No side effect" — reads stay allowed | Keeping "no output", which the next run would break again for the same reason |
| 8 — destination | Both | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Near-miss error row, any `UC-NNN` resolving to one folder + "no side effect" | 8 | **Approved** |
| 2 | The same row, but it resolves the argument and proceeds | 5 | Rejected — it interprets the input, which the paragraph under the table forbids, and the rows it would fall into write `src/` and chain `git-publish` |
| 3 | `EXACT`-shaped arguments only | 6 | Rejected — narrower for no gain; `/new-feature UC-003` is the commonest near miss and stays "ambiguous" |
| 4 | Create nothing | 3 | Rejected — the message stays true and useless, and the rule stays one a helpful run will break again |

**Shape of row 5.** A number is extracted from the argument and matched against
`docs/use-cases/UC-NNN-*/`. Exactly one hit → report its real slug and `status:`, plus the
command for it. Zero or several → the old "not found", now row 6. Several folders sharing a
number is a different defect (a recycled `UC-NNN`) that the survey's own `HEAD`-vs-disk check
already reports.

## References

| Claim | Source |
|---|---|
| The model doesn't interpret the input | `.claude/skills/new-feature/SKILL.md` § Entry guardrail |
| Rows 3 and 4 act on `src/` and chain `git-publish` | same file, § End of flow |
| A `UC-NNN` is never reused, so one number normally means one folder | `@.claude/rules/naming.md` § Use case identifier |
| A recycled number is already detected by the survey | `.claude/skills/new-feature/SKILL.md` § Entry guardrail, disk check |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/new-feature/SKILL.md` | the resolution command in the classification block; row 5 and the renumbering to 9 rows; the paragraph explaining why it reports rather than acts; the error rule rewritten to "no side effect", with the row-5 message shape |
| `docs/03-new-feature.md`, `docs/en/03-new-feature.md` | the same two changes in the reproduced table and the error sentence |

Goes to the generated project: **yes**, with the skill.

**Restart warning:** none.

## Verification

`claude plugin validate .claude/skills` and `java .claude/hooks/ArchHook.java schema` pass. The
nine rows stay mutually exclusive: rows 2–4 require `FOLDER`, row 5 requires its absence plus
exactly one resolution, row 6 the absence of both, and rows 7–9 are unchanged.
