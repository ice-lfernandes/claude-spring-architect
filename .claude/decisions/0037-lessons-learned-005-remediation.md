# 0037 · Remediation of the `/new-feature` run in lessons-learned-005 — four batches, no new piece

- **Date:** 2026-09-16
- **Scenario:** "crie uma nova feature branch para execucao dessa skill" over
  `.claude/lessons-learned/lessons.learned-005.md` — one real `/new-feature` run for
  "CRUD via REST de customer (id, name)": 5 use cases in one run, 46 min, ~USD 15,
  30 spec files for a 2-field aggregate, migrations written to `src/`, commit and push
  with no confirmation.
- **Decision:** Option 1 — four batches in dependency order, no new skill, agent, or rule.
  One new hook mode (`ArchHook.java guard`), three new exemplars.
- **State:** approved by Lucas Fernandes, on 2026-09-16

## Context

Claims from the document checked against this repository before proposing anything:

| Lesson | Verification | Result |
|---|---|---|
| 3 | `new-feature/SKILL.md` frontmatter + § Entry guardrail | Confirmed: `argument-hint: "[UC-NNN-slug]"`; guardrail item 4 asks the user for name + slug — a second owner of what `use-case-design/SKILL.md:102-106` also decides. No closed input table. |
| 4 | `persistence-architect/SKILL.md:131` | Confirmed: step 5 "Write the migration. `db/migration/V<N>__…`" — the skill writes the file itself. `new-feature` § Contract doesn't list migrations among its outputs. |
| 4 · 9 | Migration name | Confirmed divergence: `rules/persistence.md:96` owns `V<N>__<verb>_<object>.sql`; `agents/java-spring-boot-developer.md:51` and `new-feature/templates/feature-spec.md.example:75,314` use `V001__`. |
| 5 | `new-feature/SKILL.md` Consolidation step 4 | Confirmed: `git-publish` is invoked only "on success" of the executor. The no-executor path has no instruction at all. |
| 5 | `project-bootstrap/templates/settings.json.example:190-207` | Confirmed: no `Skill(...)` pre-authorization for the pipeline, no `ask` entry for `git push`. |
| 6.1 | Pipeline order | Confirmed: persistence (step 3) runs before REST (step 5), yet `api-rest.md` is what generates the idempotency table requirement. |
| 6.2 | `use-case-design/SKILL.md:68` | Confirmed: the prohibition on path/verb/status exists as prose; `SKILL.md:90` still lists "Idempotency" among the interview questions. |
| 6.3 | `use-case-design/SKILL.md:89,103` | Confirmed: the skill names the exception. |
| 7 | `--include=*.java` | Confirmed unquoted in 5 files: `test-architect`, `rest-api-architect`, `new-feature`, `messaging-architect` (×2), `persistence-architect`. |
| 7 | `` !`ls -1d docs/use-cases/UC-*` `` | Confirmed glob in 6 dynamic injections: `domain-modeling`, `test-architect`, `rest-api-architect`, `use-case-design`, `messaging-architect`, `persistence-architect`. |
| 9 | `UC-NNN` and `NNNN` | Confirmed: `use-case-design/SKILL.md:106`. |
| 9 | Use case naming | Confirmed: `rules/naming.md:25` fixes `<Verb><Noun>Service`, as do `use-case-design` and `domain-modeling` templates; both clean-architecture blueprints declare "Use case (concrete, no interface): `<Verb><Noun>UseCase`". Only `hexagonal` matches the rule. |

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | Measured run with cost, duration, file count, and git actions — not anticipation | "create nothing" as a global option |
| 5 — Nature | Mixed: pipeline procedure (skills, executor), one norm conflict (naming), and enforcement (hooks, permissions, audit) | a single piece |
| 6 — Isolation | Consolidation on a smaller model: **not now** — consolidation by reference already cuts the duplicated output, a new agent without a post-fix measurement is anti-pattern 9 | Form 3 |
| 7 — Mandatoriness | "Design never writes `src/`" and "approved spec is immutable" must always hold; the run proved prose is bypassed | prose-only for those two → hook |
| 8 — Destination | Both: every touched pipeline skill, the executor, `ArchHook.java` and `settings.json.example` travel via steps 6.7 and 7 | requires this record |
| 9 — Integration | Real ownership collisions: number/slug (orchestrator × `use-case-design`), migration name (rule × executor × template), use case name (rule × blueprint) | invariant-2 bugs, not style |

### Forks resolved in the interview

| Fork | User's decision |
|---|---|
| Slicing | Batches in dependency order (precedent `0024`) |
| REST-generated schema requirement | REST runs **before** persistence; `persistence-architect` reads `30-rest.md` |
| Enforcement in this branch | All four: `src/**` block during design, approved-spec immutability, audit fixes, permissions in the settings template |
| Use case naming owner | The active blueprint. `naming.md` cites it instead of fixing `Service` |
| Trivial case | Single spec, no partials, verifiable criterion |
| End of flow without executor | Explicit step: offer `git-publish` for `docs/use-cases/UC-NNN-*` |
| Consolidation subagent | Not now |
| Audit inside a worktree | Everything written to the main checkout (report, history, `pricing.json`) |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Four batches in dependency order, no new piece | 8 | **Approved** |
| 2 | Batches A–C only; enforcement (D) in its own branch | 6 | Rejected — leaves the two broken boundaries as prose |
| 3 | Only the boundaries (lessons 3, 4, 5) | 5 | Rejected — keeps cost drivers and naming conflict |
| 4 | Single batch | 5 | Rejected — a revert mixes prose and hook |
| 5 | Consolidation agent on `model: sonnet` | 3 | Rejected in interview — anti-pattern 9 |
| 6 | Orchestrator extracts infra requirements from `api-rest.md` | 4 | Rejected in interview — cites a rule's content in a skill, invariant 2 |
| 7 | Create nothing | 0 | Rejected — twelve claims verified and confirmed |

### Option 1 — Four batches, no new piece (score 8)

**Motivator:** axis 5 (mixed nature) and axis 7 (the two boundaries the run broke must
hold regardless of the model).

**Batch A — `/new-feature` contract and boundaries** (lessons 2, 3, 4, 5).
- `new-feature/SKILL.md`: closed 7-row input table decided by command (`grep -E`,
  `test -d`, `grep '^status:'`), fixed error format that ends the turn, no
  interpretation of input; one use case per run; open-case lock; lifecycle
  `draft → approved → implemented` with an explicit approval question at the end;
  approved spec is read-only and changes go into `## Impact on approved use cases` of the
  new spec; mandatory executor offer, only for `approved`; explicit no-executor end step
  that invokes `git-publish` on the docs; explicit ban on `git commit`/`git push`;
  "never writes under `src/`"; worktree decided in the entry guardrail; `/clear`
  recommended between runs; `argument-hint` updated; manual-only documented.
- `new-feature/templates/feature-spec.md.example`: `status:` field, impact section,
  `V<N>__` instead of `V001__`.
- `use-case-design/SKILL.md`: sole owner of number and slug; split presented once in
  dependency order, only the chosen one designed, the rest appended to
  `docs/use-cases/BACKLOG.md` without reserving numbers; approved specs read as contract.
  New exemplar `use-case-design/templates/backlog.md.example`.
- `persistence-architect/SKILL.md` + `templates/persistence-spec.md.example`: migration SQL
  goes as a code block in `20-persistencia.md` with target path; never writes `src/`.
- `agents/java-spring-boot-developer.md`: explicit step materializing the migration from
  the partial; refuses a spec not `approved`; writes `status: implemented` on green build;
  migration name cited from `rules/persistence.md`.
- `git-publish/SKILL.md`: accepts a docs-only invocation from `/new-feature`.

**Batch B — decisions in the right place** (lessons 6, 9).
- `new-feature/SKILL.md`: order becomes use case → domain → REST → persistence →
  messaging → tests; depends-on list and precedence table follow.
- `rest-api-architect` emits a schema-requirements block in `30-rest.md`;
  `persistence-architect` requires and reads it.
- `use-case-design/SKILL.md`: pre-`AskUserQuestion` checklist (HTTP code, verb, path,
  idempotency, exception name → don't ask) with a negative example; records the business
  fact ("does repeating the request duplicate?") and marks the technical decision as
  delegated; describes the error situation, doesn't name the exception; decides only what
  the current case needs; `UC-NNN` vs `NNNN` fixed.
- `rules/naming.md`: use case row cites the active blueprint's convention.
  `use-case-design` (`SKILL.md`, `references/scope-boundary.md`,
  `templates/use-case-spec.md.example`) and `domain-modeling`
  (`templates/domain-spec.md.example`, `templates/UseCasePort.java.example`) stop
  fixing `Service`.

**Batch C — cost and tool failures** (lessons 1, 7).
- The six pipeline skills: report only at the end, no progress narration; parallel writes;
  dependency versions come from the build file, no web search.
- `new-feature`: consolidation by reference (final decisions + resolved divergences,
  details cited by partial path); trivial short path (no new table, no new exception, no
  user question → single spec).
- Quoted globs in the 5 `--include` sites; `find docs/use-cases -maxdepth 1 -name 'UC-*'`
  in the 6 injections.
- `project-bootstrap/templates/root.CLAUDE.md.example` known pitfalls: one-option
  `AskUserQuestion` is not a question.

**Batch D — enforcement** (lessons 2, 4, 5, 8). Outside this skill's contract (it doesn't
write `hooks/**`), approved into the branch by the user; own commit.
- `ArchHook.java`: `PreToolUse` guard denying `Write|Edit` under `src/**` while a design
  skill run is open, and denying edits to a `UC-*-spec.md` whose `status:` is `approved`
  (except the executor's `approved → implemented` transition).
- `ArchHook.java audit`: run closes on the first user prompt that isn't a question answer;
  node duration ends at its last event; `AskUserQuestion` wait measured apart; manual vs
  automatic compaction distinguished; inside a worktree, everything written to the main
  checkout (`git rev-parse --git-common-dir`).
- `project-bootstrap/templates/settings.json.example`: wire the guard; pre-authorize the
  pipeline skills; `Bash(git push:*)` in `ask`; no broad `git *`.
- `.claude/schemas/extensions.json` only if the guard needs data (list of design skills).

**Pros:**
- A closes the two boundaries that caused irreversible effects (push, `src/`) first.
- B depends on A's single-case lifecycle (exception promotion goes to the impact section).
- C is independent edits, low risk.
- D proves A: the hook makes the prose boundary a guarantee. Separate commit keeps
  `git revert` of infra independent of prose.

**Cons:**
- `new-feature/SKILL.md` is touched by A, B and C — sequential only.
- D is infra change outside this skill's contract and needs its own manual test; the
  "open design skill run" state must exist without depending on `.claude/audit-usage/`
  being present.
- Order change in B supersedes part of D14 (`0012`); a new record, not an edit of the old.

**Points cut in the rubric:** −1 maintenance (three passes over the same file);
−1 precedent (a `PreToolUse` guard keyed on skill-run state is new in `ArchHook`).

### Option 2 — A–C here, D in its own branch (score 6)

Same prose fixes. Leaves the two boundaries the run actually broke as persuasion until
the next branch — criterion 4 (enforcement) loses two points. Viable only if D's manual
test can't fit this branch.

### Option 3 — Only boundaries (score 5)

Closes lessons 3–5. Leaves the cost drivers and the naming contradiction; the next run
still asks the user an HTTP status that gets overwritten.

### Option 4 — Single batch (score 5)

One commit mixing prose and hook; a `git revert` of a faulty hook undoes the contract.

## References

| Claim | Source |
|---|---|
| A rule that must always hold is a hook, not prose | `@CLAUDE.md` invariant 6 · decision matrix § 2 first row |
| Number/slug, migration name, use case name each have one owner | `@CLAUDE.md` invariant 2 |
| Architectures are data — naming belongs to the blueprint | `@CLAUDE.md` invariant 7 · `blueprints/clean-architecture-single-module/*.yaml:61` |
| Pipeline skills stay model-invocable; the guard is in the body | decision matrix § 4 · `0007-pipeline-skills-invocation.md` |
| Batching remediation in dependency order | `0024-lessons-learned-001-remediation.md` |
| Audit capture is a hook; changing its windows is infra | `0035-auditoria-execucao-hook.md` |
| Allowed-tools with Bash filters checks each pipe segment | `@CLAUDE.md` Known pitfalls |
| Skills and hook travel to the generated project | `@CLAUDE.md` invariant 9 · `project-bootstrap/SKILL.md` steps 6.7, 7 |

## Implementation notes

Decisions taken while writing, within the approved option:

- **Impact section is per partial.** Promoting an exception is `domain-modeling`'s call and
  a new column is `persistence-architect`'s; a single section in `00-caso-de-uso.md` would
  have given `use-case-design` a decision it doesn't own. Every partial carries the section;
  consolidation collects them.
- **Use case vocabulary travels as rendered text.** Blueprints don't go to the generated
  project (invariant 9), so step 6.6 writes the blueprint's naming comment into `naming.md`
  § Architecture vocabulary.
- **A defect in an approved spec** is reopened by the user setting `status: draft` by hand —
  the guard blocks tools, not people.
- **Guard phase state** lives in the OS temp dir, one file per session, so it can't be
  committed. Plain-text answers to a design skill end the phase: accepted gap, documented in
  the hook.
- **`extensions.json` `$comment` of `guard`** cites no decision record: the file travels to
  the generated project.
- **Not done:** the meta-repo consistency check for divergent naming patterns (lesson 9's
  "periodic verification"); a consolidation subagent on a smaller model (rejected in
  interview, revisit with audit data).

## Propagation

| File | Change |
|---|---|
| `.claude/skills/new-feature/SKILL.md` | A: closed input table, worktree at entry, lifecycle, end of flow with git-publish · B: REST before persistence, precedence rows · C: execution discipline, short path, consolidation by reference, quoted glob |
| `.claude/skills/new-feature/templates/feature-spec.md.example` | Rewritten by reference; `status:`; impact section; checklist order matches executor |
| `.claude/skills/new-feature/templates/feature-spec-short.md.example` | New — short path |
| `.claude/skills/use-case-design/SKILL.md` | A: number/slug owner, split to backlog, approved specs read-only · B: pre-question checklist, negative example, no exception names, blueprint vocabulary · C: `find` injection |
| `.claude/skills/use-case-design/templates/backlog.md.example` | New |
| `.claude/skills/use-case-design/templates/use-case-spec.md.example` | Situations instead of statuses and exceptions; impact section |
| `.claude/skills/use-case-design/references/scope-boundary.md` | Canonical names via blueprint; statuses in input accepted, not asked |
| `.claude/skills/use-case-design/examples/README.md` | Resume wording |
| `.claude/skills/domain-modeling/SKILL.md`, `templates/domain-spec.md.example`, `templates/UseCasePort.java.example` | Exception names owned here; promotion via impact section; vocabulary note |
| `.claude/skills/persistence-architect/SKILL.md`, `templates/persistence-spec.md.example` | SQL in the partial, never `src/`; reads `30-rest.md` schema requirements |
| `.claude/skills/rest-api-architect/SKILL.md`, `templates/rest-spec.md.example` | Schema requirements list; `pom.xml` before any version |
| `.claude/skills/{messaging,test}-architect/…` | Impact section in templates; quoted globs; `find` injections |
| `.claude/skills/git-publish/SKILL.md` | Path-scoped, docs-only invocation |
| `.claude/agents/java-spring-boot-developer.md` | Requires `approved`; materializes migrations; reads cited partials; `n/a` steps; closes `implemented`; `Edit` tool |
| `.claude/rules/naming.md` | Use case naming → § Architecture vocabulary |
| `.claude/hooks/ArchHook.java` | New `guard` mode; audit windows, wait, compaction trigger, worktree dir |
| `.claude/schemas/extensions.json` | New `guard` block |
| `.claude/skills/project-bootstrap/SKILL.md` | 6.6 naming vocabulary render; 6.7 template lists; 7 triggers, permissions, guard |
| `.claude/skills/project-bootstrap/templates/settings.json.example` | Guard and ask/answer triggers, `Agent` matcher, skill allows, `git push` ask |
| `.claude/skills/project-bootstrap/templates/root.CLAUDE.md.example` | Routing row; Known pitfalls |
| `CLAUDE.md`, `README.md`, `docs/03-new-feature.md`, `docs/en/03-new-feature.md`, `docs/00-visao-geral.md`, `docs/en/00-overview.md` | Manual-only, new order, new flow |

Goes to the generated project: **yes** — the pipeline skills via step 6.7, `naming.md` via
6.6, `ArchHook.java`, `extensions.json`, and `settings.json` via step 7. This record stays
here.
