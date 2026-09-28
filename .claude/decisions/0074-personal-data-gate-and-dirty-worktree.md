# 0074 · Personal data in transit becomes a gate, and the entry guardrail reads the worktree instead of the index

- **Date:** 2026-09-28
- **Scenario:** lessons-learned-014 §§ 8 and 11. § 8: `KycVerificationRequestedPayload.securityNumber`
  carried a CPF in clear to an external KYC service over Kafka, and **nothing in the pipeline
  could stop on it** — the only mention of personal data is in the final report, which is
  detection after the spec is approved, after the code is written and after the commit; it
  surfaced there only because the executor volunteered it. § 11: the entry guardrail's
  pre-existing-work check runs `git diff --cached --stat`, so a modified
  `.claude/audit-usage/history.jsonl` and an untracked report from a previous run were invisible
  to it and were swept into the run's commit by `git add -A`, while the guardrail reported a
  clean start.
- **Decision:** No new piece. A `Personal data` block in the two partials that design a payload
  leaving the process, gated at consolidation; and `git status --porcelain` in place of
  `git diff --cached`, with the list itself travelling to `git-publish`.
- **State:** approved by Lucas Fernandes, on 2026-09-28.

## § 8 — the structural finding, not the field

`lessons-learned-002 § 7` already recorded that masking stops at logs. This is different and
worse: **there is no point in the pipeline where the question can stop anything.**
`messaging-architect` designs the payload and has no step that cross-checks its field names
against the catalog; `/new-feature`'s final report asks the model to *report* every field
crossing in clear, which is a summary, not a gate.

`@.claude/rules/security.md` § In transit already requires the decision — *the exception is the
record, not the absence of one* — and no partial recorded it. A norm whose observance nothing
asks for is observed when someone happens to remember.

The check itself is cheap and already written: `security.md` § How to verify grep 1, read
against the derivation in `@.claude/rules/logging.md` § Masking candidates. **That derivation is
not re-stated in the new blocks** — one owner, invariant 2. It matters that it is the *name
list* and not intuition: the field that leaked was called `securityNumber`.

## § 11 — the index is the half that was already known

`git diff --cached` sees staged work. An unstaged edit and an untracked file are invisible to
it, and `git add -A` commits both. The run's own case was harmless — `git-publish` commits the
audit trail with the run on purpose — and the shape is not: a half-finished edit from before the
run rides along while the guardrail reports a clean start.

Two changes, and the second is the one with teeth: read `git status --porcelain`, and carry
**the list** to `git-publish`, not just the fact. `git-publish` can then diff what the guardrail
saw against what is dirty now — what the guardrail saw is exactly what the run did not produce.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | A CPF published in clear across a team boundary with no recorded decision; two pre-existing changes committed with a feature | 9 (create nothing) |
| — § 8 form | A block in the partial plus a gate at consolidation, the shape `Declared dependencies` and `Deferred` already use | A check step with no block (persuasion in the same place that already had the information and did not record it); a hook (grep finds shape, not meaning — an `internalRef` holding a CPF matches nothing) |
| — § 8 scope | `messaging-architect` and `rest-api-architect` — the two boundaries where a payload leaves the process | messaging alone, which leaves a response body unowned |
| — § 11 | `git status --porcelain`, full list travelling | Keeping the index as the gate and mentioning unstaged work in passing |
| 8 — destination | Both | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `Personal data` block in both partials + consolidation gate; porcelain + list handover | 8 | **Approved** |
| 2 | Check step in each skill, no block, no gate | 5 | Rejected — the run already had the information at that point and recorded nothing |
| 3 | A hook cross-checking the consolidated spec's field names | 4 | Rejected — grep finds the shape and not the meaning; it would pass every renamed field and fail every `document` that is a ticket number |
| 4 | Create nothing | 2 | Rejected — the norm exists, the observance does not |

**Why the block and not the hook, stated once:** the thing being gated is a *decision*, and a
decision cannot be derived from a field name. What the grep produces is a hit list to check
against the design, which is exactly what `security.md` § How to verify says of itself — *the
human question is the one that caught nothing automatically*. The gate's content is that the
question was asked and answered, not that a pattern matched.

## References

| Claim | Source |
|---|---|
| The decision is recorded where the payload is published, with receiver and reason | `@.claude/rules/security.md` § In transit · § Admitted exception |
| Which fields are candidates is derived, not remembered | `@.claude/rules/logging.md` § Masking candidates |
| Grep finds the shape, not the meaning | `@.claude/rules/security.md` § How to verify |
| A gate belongs where every run passes | `.claude/decisions/0068-dependency-list-gate-at-consolidation.md` · `0070-deferred-work-needs-an-owner.md` |
| `IGNORED` already travels from the guardrail to `git-publish` | `.claude/skills/new-feature/SKILL.md` § Entry guardrail, third case |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/messaging-architect/SKILL.md` | step 8 gains § 8 `Personal data` and renumbers `Deferred` to § 9; the block table gains the row; nine blocks |
| `.claude/skills/messaging-architect/templates/messaging-spec.md.example` | `## 8 · Personal data` with a worked row; `Deferred` becomes `## 9` |
| `.claude/skills/rest-api-architect/SKILL.md` | a § *Personal data leaving in a response body* before the block table, saying `@MaskSensitiveData` is not the answer to it; the block row; seven blocks |
| `.claude/skills/rest-api-architect/templates/rest-spec.md.example` | `## 6 · Personal data` with a worked `reduced` row |
| `.claude/skills/new-feature/SKILL.md` | the consolidation gate; block counts in steps 3 and 4; the final report says it now repeats what the partials recorded; the guardrail's fourth case reads `git status --porcelain` and hands over the list |
| `.claude/skills/git-publish/SKILL.md` | `PRE_EXISTING_INDEX` becomes `PRE_EXISTING_WORK`, read from porcelain; the state row and the handover paragraph |

Renumbering note: `0070` describes `Deferred` as `25-mensageria.md` § 8. It is § 9 from here on
— `Personal data` took § 8, so the payload's own question sits next to the payload rather than
after the deferrals. This record supersedes that detail; `0070` is not rewritten.

Goes to the generated project: **yes** — all four skills are in `export.skills.include`.

**Restart warning:** none.

## Verification

`claude plugin validate .claude/skills` and `java .claude/hooks/ArchHook.java schema` pass.
Nothing here executes. Two block-count drifts were corrected in passing, both the same kind
`0070` found in `messaging-architect`: `rest-api-architect` said "Five blocks" over a table of
six rows, and `/new-feature`'s step 3 said "five blocks" for it as well.
