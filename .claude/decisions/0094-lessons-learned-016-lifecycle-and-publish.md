# 0094 · Remediation of lessons-learned-016 — the remaining sections

- **Date:** 2026-09-30
- **Scenario:** `@.claude/lessons-learned/lessons-learned-016.md`, the sections
  `.claude/decisions/0093-lessons-learned-016-fact-sources.md` left out (§§ 1, 4, 5, 6, 7, 8, 9,
  10, 12). The user asked for the unresolved ones; §§ 1 and 8 arrived with their mechanism
  already chosen in `0093`'s interview.
- **Decision:** Options 1.1, 6.1, 8.1, 9a.1 and 12.1. Prose in the skills that already own
  each fact, plus one change inside an existing hook mode: `audit answer` keeps each question and
  its answer, redacted, and the report renders them. No new mode, no new registration, no
  `permissions` line.
- **State:** approved by Lucas Fernandes, on 2026-09-30

## Scope of this run

| § | Answer | Piece |
|---|---|---|
| 1 | Remove the `Partial status` line (chosen in `0093`) | `use-case-design` template + 13 examples |
| 4 | **Not resolved, by the user's choice.** The reopen path exists (`status: draft` by hand, named in `guard`'s refusal message and in `0066`) | none |
| 5 | **Create nothing.** One-off legacy of projects older than `0069`; the inline migration worked | none |
| 6 | Rewrite `persistence-architect` step 4b; regulate downstream → upstream requirements in `/new-feature` consolidation | `persistence-architect`, `new-feature` |
| 7 | **Create nothing**, by the user's choice | none |
| 8 | `.claude/audit-usage/**` is always the run's own (chosen in `0093`) | `new-feature` guardrail, `git-publish` |
| 9a | "Everything dirty" → two commits, one push | `git-publish` |
| 9b | **Create nothing.** `sonarqube-setup` is chained by `project-bootstrap` (whose initializer publishes) and by `arch-adopt` (which never commits, by design); standalone, `/new-feature`'s guardrail caught the leftovers and asked | none |
| 10 | **Create nothing.** `guard bash` already reads `sed -i` (`0063`) and `guard sweep` backstops it; nothing was violated | none |
| 12 | `audit` records each question and its answer, redacted, and the report shows them | `ArchHook.java` `audit` mode |

## Reproduced on disk before classifying

| § | On disk (`a267e55` + `0093`) |
|---|---|
| 1 | `use-case-spec.md.example:5` lists `10`, `20`, `30`, `40`; 13 `examples/UC-1xx-*/00-caso-de-uso.md` copy it (one adds `25`, none `35`). No `SKILL.md` or agent writes it |
| 6 | `persistence-architect/SKILL.md:192` "the exemplar is its only source" vs `:218` lease option with `claimed_at`, a column the exemplar lacks, vs `:226-228` "cannot hold with the exemplar's columns … stop the pipeline and ask". The lessons' premise that `messaging.md` requires per-row backoff and multi-instance safety **does not hold**: `messaging.md:74-76` admits "a stated single-instance constraint", `:127` asks only bounded attempts. `new-feature/SKILL.md:485-486` carries a duplicated precedence row and `:483` a mis-indented one |
| 8 | `git-publish/SKILL.md:130` stages the trail always; `flush` writes at `Stop` (`ArchHook.java:3335`), `history.jsonl` on the next prompt — after the commit, by construction. `new-feature/SKILL.md:314-317` names the shape, exempts nothing |
| 9a | `git-publish/SKILL.md:101` two options, one message built from the caller's context only |
| 12 | `settings.json.example:55-60,187-192` wire `audit ask`/`answer`; `ArchHook.java:3324-3325` append timestamps only. The `PostToolUse` payload's `tool_response` carries `questions` and an `answers` map keyed by question text (checked in this session's transcript, `toolUseResult`) |

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | §6: the model read an exception as permission; §8: every run ends dirty; §9a: `docs(...)` commit carrying `pom.xml`; §12: the lessons file had to be rebuilt from the raw transcript | create nothing for those four |
| 7 — mandatoriness | §§ 1, 6, 8, 9a persuasion; §12 is inside an existing hook — the record has to survive the model forgetting | Form 7 for §12 only |
| 16 — existing mode | `audit` already runs on both events; only what it records changes | a new mode, a new registration |
| 8 — destination | Both: every file travels | — |
| 12 — data exposure | The trail is versioned. Free-text answers pass through `audit.redact` and the same 160-char cap the Rework table uses | recording raw text |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1.1 | Remove the line from template and examples | 9 | **Approved** |
| 1.2 | Give it an owner (each design skill flips its marker; consolidation refuses ❌) | 5 | Rejected |
| 6.1 | Step 4b: the exemplar is the **starting** column set; a column the chosen claim strategy or pacing needs is this step's to add, named in § 1 with the guarantee it serves; stop-and-ask narrowed to a declared guarantee **no** column can meet. Consolidation: a downstream partial adds a requirement upstream only as a listed divergence consolidation resolves. Plus the two broken precedence rows | 8 | **Approved** |
| 6.2 | `next_attempt_at` + lease column in `OutboxEventTable.sql.example`, claim and store exemplars (lessons' suggestion) | 5 | Rejected |
| 6.3 | Only the precedence line | 5 | Rejected |
| 8.1 | `.claude/audit-usage/**` excluded from pre-existing work in `/new-feature`'s guardrail and in `git-publish`'s `PRE_EXISTING_WORK` | 9 | **Approved** |
| 8.2 | Gitignore the trail | 4 | Rejected |
| 8.3 | `chore(audit):` commit on demand | 4 | Rejected |
| 9a.1 | Two commits, one push: the run's paths (+ trail) with the run's message, then the rest as `chore:` listing the files | 8 | **Approved** |
| 9a.2 | One commit, type `chore`, body splitting the two | 5 | Rejected |
| 12.1 | `audit answer` records header, question and answer per question, redacted and capped; report gains a `💬 Asked` section | 7 | **Approved** |
| 12.2 | 12.1 + `guard` writes its refusals into the trail | 5 | Rejected |
| 12.3 | create nothing — the transcript has it | 5 | Rejected |

**1.1** — one duplicated source of truth removed; `status:` and the files on disk answer the
question. **Cut:** none.

**6.1 — Motivator:** axis 1, both readings of step 4b were defensible. **Pros:** the lean
exemplar stays right for a single instance; the step owns its columns, as the precedence table
already says (`20-persistencia.md` wins on the outbox table). **Cons:** persuasion. **Cut:**
enforcement.
**6.2** — makes every project pay lease and backoff columns the rule does not require; edits
three exemplars that CI compiles. **Cut:** form fit, maintenance, trust in a false premise.

**8.1** — the trail is committed with the run on purpose (`export.gitignore_lines` comment);
the fix is to stop calling it someone else's work. **Cut:** none.
**8.2 / 8.3** — reverse a deliberate decision, or leave the tree dirty until someone remembers.

**9a.1 — Motivator:** axis 1. **Pros:** each commit's type matches its content; one gate, one
push. **Cons:** two commits to review. **Cut:** maintenance (half).

**12.1 — Motivator:** axis 1 and axis 16 — `audit` already fires on `PostToolUse
AskUserQuestion`; the change is what it keeps, not when it runs. **Pros:** the material a
lessons file needs survives compaction; no new registration, no new JVM start. **Cons:**
user text in a versioned file, bounded by `audit.redact` and the cap; Java in a hook every
generated project runs. **Cut:** trust surface, maintenance, cost of always running (none new,
but the record grows).
**12.2** — `guard` writing the audit's state couples two modes. **Cut:** maintenance, precedent.

## References

| Claim | Source |
|---|---|
| The trail is versioned and committed with its run | `extensions.json` `export.$comment_gitignore`; `git-publish/SKILL.md` step 2.3 |
| `audit` redacts before a versioned write | `ArchHook.java` `redact`, `extensions.json` `audit.redact` |
| Lists a mode reads come from `extensions.json` | `@CLAUDE.md` invariant 10 |
| The outbox table is `20-persistencia.md`'s | `new-feature/SKILL.md` consolidation precedence table; `.claude/decisions/0087-jobs-architect-skill.md` |
| Reopening an approved spec is by hand | `.claude/decisions/0066-spec-state-machine.md`; `ArchHook.java` frozen-folder message |
| `guard bash` reads `sed -i` | `.claude/decisions/0063-bash-write-enforcement.md`; `extensions.json` `guard.bash_write_shapes` |

## Found while writing

- The `answers` map is keyed by the question's text; `auditAnswer` falls back to
  `tool_input.questions` when the response carries no `questions`, and writes `—` for a question
  with no answer. Tested by hand in an exported project and pinned in
  `.claude/.ci/AuditRenderTest.java`.
- The raw text lands in `.claude/audit-usage/.state/*.ndjson`, which is gitignored; only the
  rendered, redacted table reaches the versioned report.

## Propagation

| File | Change |
|---|---|
| `.claude/skills/use-case-design/templates/use-case-spec.md.example` + 13 `examples/UC-1xx-*/00-caso-de-uso.md` | `> Partial status:` line removed |
| `.claude/skills/persistence-architect/SKILL.md` | step 4b: exemplar is the starting column set; a claim/pacing column is this step's to add; stop only when no column can meet the guarantee; a requirement on another partial is a divergence |
| `.claude/skills/new-feature/SKILL.md` | guardrail: `.claude/audit-usage/**` is never pre-existing work; consolidation: downstream → upstream requirement travels as a divergence; duplicated and mis-indented precedence rows fixed |
| `.claude/skills/git-publish/SKILL.md` | `PRE_EXISTING_WORK` ignores `.claude/audit-usage/**`; "everything dirty" = two commits, one push; report line for the second commit |
| `.claude/hooks/ArchHook.java` (+ jar) | `auditAnswer`, `clip` (shared with `firstLine`), `asked` event, `💬 Asked` section |
| `.claude/.ci/AuditRenderTest.java` | asserts the section, the redaction and the pipe escape |
| `docs/{pt-br,en}/08-audit-usage.md` | event table row and sample report |

Goes to the generated project: **yes** — skills, templates, examples stay where `export` already
sends them; `ArchHook.java` and the jar travel whole. `.claude/.ci/` does not travel.
