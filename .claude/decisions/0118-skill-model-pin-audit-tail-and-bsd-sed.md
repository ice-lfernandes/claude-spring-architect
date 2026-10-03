# 0118 · The collateral findings of 0117 — a skill pin the runtime ignores, the audit's tail, and BSD `sed -i ''`

- **Date:** 2026-10-03
- **Scenario:** "trate os achados colaterais da decisao 0117-new-feature-cost-per-entry-scenario"
- **Decision:**
  - Finding 1: document the runtime behavior. Part 1 of `docs/{pt-br,en}/11-pitfalls.md`, `frontmatter-fields.md` and `claude-help.md`; no pin changes.
  - Finding 2: option A. A footnote in the audit report (`ArchHook.java` `audit`) and a paragraph in `docs/{pt-br,en}/08-audit-usage.md`.
  - Finding 3: Form 7c edit of `guard bash`. Empty operands are skipped, and a new `script_flags` field on the `sed` shape in `guard.bash_write_shapes` supplies the flags whose value is the script.
- **State:** approved by Lucas Fernandes, on 2026-10-03
- **Goes to the generated project:** yes, the `guard bash` fix and the audit footnote — `ArchHook.java`, the jar and `extensions.json` travel through `export`. The documentation of finding 1 stays here: `frontmatter-fields.md` belongs to `claude-code-architect-designer`, a creation skill in `export.skills.exclude`, and the pitfalls and audit pages are this repository's docs

## Reproduced on disk

Each finding of 0117 was checked against `HEAD` (`d1161b4`) and the demo transcripts before
any option was written. One half of finding 2 did not hold.

| Finding (0117) | Verification | Result |
|---|---|---|
| 1 · `git-publish`'s `model: sonnet` did not apply | `message.model` after each of 18 skill invocations in `demo-clean-arch-single-module`, Claude Code 2.1.280 | **Confirmed, and the cause is wider than `git-publish`.** Typed `/arch-doctor` (`model: sonnet`, `effort: high`) answered on `sonnet-5` in 6 of 6. Every skill the model invoked through the `Skill` tool answered on the caller's model: `git-publish` on `opus-5-5` 15 of 15, the design skills on `opus-5-5` because `/new-feature` (typed, `model: opus`) had already pinned it |
| 1 · Is it known upstream | `gh search issues` on `anthropics/claude-code`, 2026-10-03 | **Open bug.** [#98898](https://github.com/anthropics/claude-code/issues/98898), opened 2026-10-02, `has repro`, no maintainer reply yet: the override applies on `/name` and not through the `Skill` tool, on 2.1.287. It is the same bug as #79664, which a maintainer reproduced (`reproduced`, `regression`, 2.1.233) and the stale bot closed on 2026-09-23. A 2026-09-28 comment on #85658 shows the same split on 2.1.283 across CLI, desktop and every permission mode. #81618 (`effort:` alongside `model:` drops the model) does **not** reproduce here: `/arch-doctor` carries both and switched |
| 2a · Consolidation, approval and delegation land on `test-architect` | The UC-007 audit report itself: `test-architect` USD 1.91, `AskUserQuestion 3 · Agent 1` | **Confirmed.** `ArchHook.java` `audit`: a main-thread turn belongs to the last skill that started before it, and returns to the root only after an `Agent` call |
| 2b · Post-run work lands on `git-publish` | The audit reports, against the session transcripts | **Refuted.** 0117 took it from the analysis script written for 0117, which never reset on a new prompt; the audit closes the run. `git-publish`'s USD 0.29–0.54 in the reports is its own: 3–6 calls at a context of 250–370k |
| 3 · `guard bash` misreads `sed -i ''` | `guard bash` on the jar at `HEAD`, with `agent_type: java-spring-boot-developer` | **Confirmed.** `sed -i '' 's/a/b/g' src/test/java/A.java` → exit 2 `s/a/b/g is outside its territory`, and `sed -i '' -e 's/a/b/' …` likewise. `sed -i -e`, `sed -i.bak` and plain `sed -i` passed. Two real executor runs hit it (transcripts `agent-a81e7a2559ab96083`, `agent-ac35af142516b26f7`) |

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | All three observed in real runs; numbers above | Create nothing for 3 |
| 5 — Nature | 1 and 2 are facts a reader needs; 3 is a parser defect in an existing mode | A new skill, agent or rule |
| 7 — Mandatoriness | 3 is a guarantee that already exists and is too tight. 1 is runtime behavior this repository cannot change. 2 has no runtime signal to enforce against | New hooks for 1 and 2 |
| 8 — Destination | Both | — |
| 16 — Existing mode | `guard bash` (3), `audit` (2) | A new mode |
| Finding 1 (user) | **Document only.** The pins match the official docs, and a fix upstream makes them work again with no edit here | Changing `allowed_models` per class (0081) |
| Finding 2 (user) | **Option A**, a note | B, C |
| Finding 3 (user) | **Empty operand + script flags** | Empty operand only |

## Options evaluated

| # | Finding | Option | Score | Verdict |
|---|---|---|---|---|
| 1a | 1 | Document: runtime pitfall + `frontmatter-fields.md` + `claude-help.md` | 8 | **Approved** |
| 1b | 1 | Document + revisit pins (e.g. `ops` inherits the caller's model) | 5 | Rejected — changes 0081 over a bug that is open upstream and may be fixed |
| 2A | 2 | Footnote in the report + `08-audit-usage` paragraph | 8 | **Approved** |
| 2B | 2 | `audit.segment_closers`: globs whose main-thread write closes the nested skill's segment (`docs/use-cases/UC-*/UC-*-spec.md`) | 5 | Rejected — works only where a deliverable path exists; the reasoning before the write still lands on the skill; fails silently if consolidation writes another file first |
| 2C | 2 | Correct 0117 only | 3 | Rejected — a reader of `/audit-usage` without 0117 stays misled |
| 3a | 3 | Skip empty operands + `script_flags` data on the `sed` shape | 8 | **Approved** |
| 3b | 3 | Skip empty operands only | 7 | Rejected — leaves `sed -e x -e y file` refusing a file in territory |

### 1a — document the pin (score 8)

**Motivator:** axes 1 and 5. The behavior is upstream's to fix. What this repository owns is
four places that promise the switch.

**Consequences recorded:**
- `git-publish`'s `sonnet`/`low` is inert in its real use, because it is always chained.
  If upstream fixes the bug, the pin starts switching at the end of a long run. Switching
  model invalidates the cache (`@claude-help.md` § Prompt cache), which costs about
  USD 0.9 at 369k context against cents saved on output. Revisit that pin when #98898 closes.
- The design skills' `opus` holds through `/new-feature` only. Invoked directly in a Sonnet
  session, a design skill runs on Sonnet.

**Points cut:** 4 (it explains instead of enforcing; nothing here can enforce a runtime
behavior).

**CI:** nothing testable. The behavior is the runtime's, and the prose has no schema.

### 2A — the audit's tail (score 8)

**Motivator:** axis 1. The runtime emits no event when an inline skill ends, so no attribution
rule can be exact. A reader who knows the rule reads the row right.

**Pros:** one sentence in the report, one paragraph per doc, no behavior change.

**Cons:** the number stays mixed. Measuring consolidation as its own piece would need 2B.

**CI:** `validate · hooks-cross-platform › AuditRenderTest` renders the report with the new
footnote, green. Asserting the footnote's wording would pin prose, not behavior, so no case
was added.

### 3a — `sed -i ''` and script flags (score 8)

**Motivator:** axis 1. A guard that blocks a correct write in the executor's own territory
costs turns at ~290k context, and the model works around it.

**Mechanism.** In `bashWriteTargets`, two changes:
- An empty word is never an operand.
- A flag listed in the shape's `script_flags` consumes its value as the script (`-e x`,
  `--expression=x`). When one is present, a `tail` shape judges every operand.

The list lives in `extensions.json` (invariant 10). The real target is still judged.

**CI:** `validate · hooks-cross-platform › guard bash refuses force pushes and holds shell writes
to the phase`, with 6 new cases in `BashGuardTest`.

## References

| Claim | Source |
|---|---|
| Skill `model`/`effort` documented as applying for the rest of the turn | `@claude-help.md` skill fields table; official skills docs quoted in #98898 |
| The override applies only on typed `/name` | anthropics/claude-code#98898 (open), #79664 (reproduced, closed stale), #85658 comment of 2026-09-28; demo transcripts, 2.1.280 |
| Switching model invalidates the cache | `@claude-help.md` § Prompt cache |
| Audit attribution rule | `ArchHook.java` `audit`, the comment above `// ── tokens per piece ──` |
| `bash_write_shapes` is data, the parser is generic | `@.claude/decisions/0063-bash-write-enforcement.md`; invariant 10 |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` + `ArchHook.jar` | `bashWriteTargets`: skips empty words, reads `script_flags`, Javadoc. `audit` report footnote: the tail sentence |
| `.claude/schemas/extensions.json` | `guard.bash_write_shapes.commands[sed].script_flags`, and the block's `$comment` |
| `.claude/.ci/BashGuardTest.java` | 6 cases: `sed -i ''`, `-i '' -e`, two `-e`, `--expression=` allowed in territory; `-i ''` and `-i -e` refused outside it |
| `docs/{pt-br,en}/07-ci-validate.md` | `BashGuardTest` row: 23 → 29 cases |
| `docs/{pt-br,en}/11-pitfalls.md` | Part 1 § Skills: the pin applies only on `/name`. Part 2 § Classes and models: the same caveat |
| `.claude/skills/claude-code-architect-designer/references/frontmatter-fields.md` | `model` · `effort` row |
| `claude-help.md` | Skill fields table, `model` / `effort` row |
| `docs/{pt-br,en}/08-audit-usage.md` | Paragraph on the last chained skill carrying its caller's tail |
| `.claude/decisions/0117-new-feature-cost-per-entry-scenario.md` | Side findings point here; 2b corrected |

`docs/*/claude-code-docs/02-skills.md` is left as is: it mirrors the official docs, and the
discrepancy is upstream's.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · hooks-cross-platform › guard bash refuses force pushes and holds shell writes to the phase` | `sed -i ''` and script-flag spellings are judged by their file, not their script, in both directions | Green: 29/29. Red with `script_flags` removed from the data: `executor: two -e scripts — allowed` fails by name. Red with the empty-word skip removed from the Java: `executor: BSD sed -i '' on a file in its territory — allowed` fails by name |
| `validate · hooks-cross-platform › AuditRenderTest` | The report still renders with the new footnote | Green |
| `validate › build --verify` | The committed jar is the source's bytes | Green |

Findings 1 and 2 are prose about behavior outside this repository's control; nothing in them
is testable in CI.
