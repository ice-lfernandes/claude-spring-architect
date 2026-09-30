# 0024 · Remediation of UC-001's 18 gaps — three batches, one new rule and one CI job

- **Date:** 2026-09-09
- **Scenario:** «I ran the java-spring-boot-developer agent on the demo-app project; in
  the end it created things correctly but had some deviations from the final
  implementation that deserve fixing here in the meta-repo so it doesn't happen again» —
  from `.claude/lessons-learned/lessons-learned-001.md`
- **Decision:** Option 1 — three batches in dependency order. One new piece
  (`.claude/rules/observability.md`, Form 4); everything else is edits to exemplars,
  rules, skills and the executor agent, plus a proposed CI job.
- **Status:** approved by Lucas Fernandes, on 2026-09-09

## Context

The lessons-learned file records 18 gaps found while implementing
`UC-001-criacao-pessoas` in a project generated with the
`clean-architecture-single-module` blueprint, against Spring Boot 4.1.1 / Java 21 /
Testcontainers 2.0.5.

Four claims from the document were checked against this repository before proposing
anything:

| Gap | Verification | Result |
|---|---|---|
| 8 | `.claude/rules/api-rest.md` frontmatter | Confirmed: only `**/adapter/in/rest/**`. The single-module blueprint declares `infrastructure.rest` in `packages.map`; the hexagonal one declares `adapter.in.rest`. The glob never matches the first one. |
| 8 | `project-bootstrap/SKILL.md` step 6.6 | The step states in writing that the glob «matches the `adapter.in.rest` package, which every blueprint with `feature: rest` declares in `packages.map`». The claim is false for 2 of the 4 blueprints. |
| 3 | `project-bootstrap/references/dependency-catalog.md` | `validation` exists in the catalog (`| validation | validation |`), but doesn't appear in the `features:` list of any blueprint. It never enters the Initializr's `-d dependencies=`. |
| 1 · 12 | `project-bootstrap/templates/checkstyle.xml.example` | Confirmed: `ParameterNumber` (line 43), `MagicNumber` (65), `ConstantName` (105). |
| 10 | `.claude/rules/naming.md:45-47` | Confirmed: two test-name conventions in different files, and one citation to `CONTEXT.md`, a file that doesn't exist in any blueprint. |
| 15 · 18 | `.claude/agents/java-spring-boot-developer.md:63,105` | Confirmed: the guardrail requires `src/domain/`; blocks compile with `./mvnw -pl domain test-compile`. Neither exists in the single-module blueprint. |

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | 18 real gaps, with build evidence, not preemption | Eliminates "create nothing" as a global option |
| 4 — Territory | The rules' `paths` is itself the bug: it assumes a package layout only half the blueprints have | Fixes the structural correction in step 6.6, not in the rules |
| 5 — Nature | A mix: declarative contradictions (rules, checkstyle) and procedure (pipeline, executor) | Rules out one single piece — forces a split by form |
| 7 — Enforceability | «Templates compile against the blueprint's pom» has to always hold | CI job, not prose — **out of scope for this skill** |
| 8 — Destination | Both: `api-rest.md`, `testing.md`, `naming.md`, `persistence.md` and the new `observability.md` are copied by step 6.6 | Requires this record (Phase 3.5) and touching `project-bootstrap` |
| 9 — Integration | Real ownership collision: `naming.md` and `testing.md` are both owners of test naming | Gap 10 is an invariant-2 bug, not a style question |
| 10 — Cost of error | Gap 2 fails silently (409 becomes 500 in production); gap 18 cost ~2h of wall-clock time | Raises the score of any option that handles blockers first |

### Forks resolved in the interview

| Gap | User's decision |
|---|---|
| 1 · 12 | Relax `checkstyle.xml.example` — `log` exemption in `ConstantName`, static factories exempt from `ParameterNumber`. Templates untouched. |
| 6 | Blueprint adds `com.fasterxml.uuid:java-uuid-generator`; `persistence.md` cites `Generators.timeBasedEpochGenerator()`. |
| 8 · 15 | Rule `paths` with territory derived from the active blueprint's `packages.map`, in step 6.6 — same treatment `architecture-ddd.md` already gets. |
| 7 | `observability` feature in the blueprint **and** new `observability.md` rule, owner of the `traceId`'s origin. Template gains two variants. |
| 14 | `testing.md` now admits `@EnabledIf` locally and requires Docker in CI. The generated code stays as is. |
| 18 | Executor rewritten: incremental writing per checklist step, with resume; guardrail and compilation command derived from the blueprint. |
| 16 · 17 | All four: consolidation resolves divergences, `use-case-design` stops fixing transport, post-consolidation ArchUnit stage, engine-agnostic guardrail across the five pipeline skills. |
| Root cause | CI job that generates a reference project and compiles each `*.java.example` against it. |

## The 18 gaps classified by form and owner

| Gap | Form | Owner file | Within this skill's contract? |
|---|---|---|---|
| 1 · 12 | Exemplar | `project-bootstrap/templates/checkstyle.xml.example` | Yes |
| 2 | Exemplar + Rule | `persistence-architect/templates/RepositoryAdapter.java.example`, `rules/persistence.md` | Yes |
| 3 | **Blueprint** | `blueprints/*/*.yaml` (`features.validation`) | **No** — invariant 7, this skill's contract |
| 4 · 9 | Exemplar | `test-architect/templates/{PersistenceIT,ControllerTest}.java.example` | Yes |
| 5 | Exemplar | `rest-api-architect/templates/ApiExceptionHandler.java.example` | Yes |
| 6 | **Blueprint** + Rule | `blueprints/*/*.yaml`, `rules/persistence.md`, `dependency-catalog.md` | Partial |
| 7 | **New rule** + Blueprint | `rules/observability.md`, `blueprints/*/*.yaml` | Partial |
| 8 | Skill | `project-bootstrap/SKILL.md` step 6.6 | Yes |
| 10 | Rule | `rules/naming.md` (removes), `rules/testing.md` (owner) | Yes |
| 11 | Exemplar | `persistence-architect/templates/JpaEntity.java.example` | Yes |
| 13 | CI | `.github/workflows/validate.yml` | Enforcement — proposes, doesn't execute |
| 14 | Rule | `rules/testing.md` | Yes |
| 15 | Skill ×5 | the five pipeline skills | Yes |
| 16 | Skill | `new-feature/SKILL.md`, `use-case-design/SKILL.md` | Yes |
| 17 | Skill | `new-feature/SKILL.md` | Yes |
| 18 | Agent | `agents/java-spring-boot-developer.md` | Yes |
| root | CI | `.github/workflows/validate.yml` | Enforcement — proposes, doesn't execute |

**Contract restriction.** This skill doesn't write `.claude/blueprints/**` (invariant 7:
architectures are data). Gaps 3, 6 and 7 each have a part that falls outside it: adding
`validation`, `uuid-v7` and `observability` to the blueprints' `features:` list. That
part is data editing and goes in its own commit, with `dependency-catalog.md` (which
**is** `skills/**` and therefore within the contract) updated in the same step.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Three batches in dependency order | 9 | **Approved** |
| 2 | Single remediation batch | 6 | Rejected — a `git revert` would undo unrelated fixes |
| 3 | Only the four blockers | 5 | Rejected — leaves gaps 8 and 17's dead enforcement intact |
| 4 | Only the CI job, let CI catch the rest | 3 | Rejected — closes no gap by itself, and doesn't catch gap 2 |
| 5 | Create nothing | 0 | Rejected — six claims verified and confirmed against the repo |

### Option 1 — Three batches in dependency order (score 9)

**Motivator:** axis 5 (mixed nature) and axis 10 (cost of error concentrated in the
blockers).

**Batch A — internal contradictions in generated material.** Gaps 1, 2, 4, 5, 9, 10, 11,
12, 14. All exemplar or rule edits, no dependency on anything. It's the batch that erases
gap 2, the only one that fails silently.

**Batch B — territory and enforcement.** Gaps 8, 15, 3, 6, 7 + CI job. Step 6.6 starts
deriving `paths` from `packages.map`; `observability.md` is born; blueprints gain
`validation`, `uuid-v7` and `observability`; the CI job now proves batch A and prevents
its recurrence.

**Batch C — pipeline and executor.** Gaps 16, 17, 18. Executor rewrite with incremental
writing, post-consolidation ArchUnit stage, divergence resolution at consolidation,
`use-case-design` stops fixing transport.

**Pros:**
- Batch A is independent and delivers value on its own; if the session gets
  interrupted, gap 2 is already closed.
- Batch B only makes sense after A: the CI job exists to *prove* A got it right. Wiring
  the CI before fixing the templates gives a red build with no new information.
- One decision record, three commits — the audit trail stays legible.
- Each batch has its own check (`validate` green, new job green, `/new-feature` running
  to completion).

**Cons:**
- Three passes over `project-bootstrap/SKILL.md` (batches A and B both touch it) —
  conflict risk if parallelized. Mitigation: sequential, not parallel.
- Tensions invariant 7 in batch B: the blueprints have to change. Not a violation — the
  change is adding features to existing data, without touching any prompt, which is
  exactly what the `new blueprint doesn't touch prompts` job in `validate.yml` already
  proves.

**Points cut on the rubric:** −1 because coordinating batches A and B over the same file
requires sequencing discipline that nothing in the repository enforces.

**Visual:**

```
Batch A · contradictions             Batch B · territory                Batch C · pipeline
─────────────────────                ────────────────────               ─────────────────
checkstyle.xml.example  ─┐           project-bootstrap 6.6 ─┐           new-feature/SKILL.md
RepositoryAdapter …     ─┤           rules/observability.md ─┤          use-case-design/SKILL.md
PersistenceIT …         ─┤           blueprints/*.yaml      ─┤          java-spring-boot-developer.md
ControllerTest …        ─┼─ proves ─► .github/…/validate.yml  │         + 5 engine-agnostic guardrails
ApiExceptionHandler …   ─┤           (reference-project job) │
JpaEntity …             ─┤                                   │
rules/{naming,testing,  ─┘                                   └─ catches regressions from A
       persistence}.md
```

### Option 2 — Single batch (score 6)

All 18 in one commit. Faster on the clock, but mixes one-line edits
(`UNPROCESSABLE_CONTENT`) with a 285-line agent rewrite in the same diff. Review
impossible, and a regression in the executor forces reverting the correct template fixes
too.

**Points cut:** −4 on reversibility (rubric § 8): a `git revert` undoes unrelated
fixes.

### Option 3 — Only the four blockers (score 5)

Gaps 1, 2, 3, 4. Closes what blocks the build, leaves the other fourteen. Defensible if
the goal is to unblock now, but gaps 8 and 17 are *dead enforcement* — they block
nothing today and so never rise in priority on their own. Leaving them means choosing
they stay dead.

**Points cut:** −5 for leaving the root cause intact; the list grows again on the next
Boot version bump.

### Option 4 — Only the CI job (score 3)

Wire the reference-project job and let it report the gaps. Elegant in theory: CI turns
red and forces the fix. In practice delivers a red build with 8 simultaneous failures and
none of them fixed, and gap 2 (silent failure, not caught by compilation) still passes.

**Points cut:** −7 for closing no gap on its own.

### Option 5 — Create nothing (score 0)

Rejected with evidence: six of the document's claims were checked against this
repository's files and confirmed. They aren't preemption.

## References

| Statement | Source |
|---|---|
| `rules/` is a leaf — the new `observability.md` rule can't mention any skill or agent | `@CLAUDE.md` invariant 1; job `rules is a leaf of the graph` in `.github/workflows/validate.yml` |
| A test name needs one single owner file — `testing.md` | `@CLAUDE.md` invariant 2; `@.claude/rules/00-index.md` § header |
| Boilerplate stays in `templates/*.example`, never inside a rule | `@CLAUDE.md` invariant 3; job `norm contains no code boilerplate` |
| Adding features to blueprints must never require editing prompts | `@CLAUDE.md` invariant 7; job `new blueprint doesn't touch prompts` |
| New rules and skills are only complete once the step that copies them is updated | `@CLAUDE.md` invariant 9; `project-bootstrap/SKILL.md` steps 6.6/6.7 |
| Java and Spring Boot versions are never written from memory | `@CLAUDE.md` invariant 8 — motivates the CI job instead of pinning versions in templates |
| Deriving `paths` from the blueprint has precedent | `project-bootstrap/SKILL.md` step 6.6, treatment of `architecture-ddd.md` |
| Enforcement that must always hold is a hook or CI, not prose | `@CLAUDE.md` invariant 6; `claude-code-architect-designer/SKILL.md` § Out of scope |
| This skill doesn't write `.claude/blueprints/**` | `claude-code-architect-designer/SKILL.md` § Contract |

## Propagation

### Batch A — internal contradictions

| File | Change | Gap |
|---|---|---|
| `project-bootstrap/templates/checkstyle.xml.example` | `ConstantName` admits `log`; `SuppressionXpathSingleFilter` exempts static factories from `ParameterNumber` | 1 · 12 |
| `persistence-architect/templates/RepositoryAdapter.java.example` | `saveAndFlush` + `catch (DataIntegrityViolationException)` alongside optimistic locking | 2 |
| `rules/persistence.md` § Boundary | Two new lines: no framework exception leaves the adapter; translating a constraint requires a flush inside the adapter | 2 |
| `test-architect/SKILL.md` step 4 | A unique business key requires an integration test that asserts the `errorCode` | 2 |
| `test-architect/templates/PersistenceIT.java.example` | `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`; `org.testcontainers.postgresql.PostgreSQLContainer` without a generic; `@EnabledIf`; duplicate test asserts `ConflictException` + `errorCode` | 4 · 9 · 2 · 14 |
| `test-architect/templates/ControllerTest.java.example` | `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` | 4 |
| `rest-api-architect/templates/ApiExceptionHandler.java.example` | `HttpStatus.UNPROCESSABLE_CONTENT` | 5 |
| `persistence-architect/templates/JpaEntity.java.example` | `@FieldDefaults(level = PRIVATE)` with `lombok.experimental.FieldDefaults`; modifiers removed from fields | 11 |
| `rules/naming.md` § Tests | Stops fixing test names; cites `testing.md`. Removes the reference to `CONTEXT.md`, a nonexistent file | 10 |
| `rules/testing.md` § Test data | Gains `<Aggregate>Fixtures`, moved from `naming.md` | 10 |
| `rules/testing.md` § Database in ITs | `@EnabledIf` admitted locally; in CI, Docker is mandatory and zero ITs run is a failure | 14 |
| `project-bootstrap/templates/ci.yml.example` | New step that fails if no IT ran, or if an IT ran with 0 tests | 14 |

### Batch B — territory and enforcement

| File | Change | Gap |
|---|---|---|
| `rules/observability.md` | **New file** (Form 4). Owner of the correlation identifier's origin: tracing bridge when it exists, generated locally and declared in a comment when it doesn't | 7 |
| `rules/00-index.md` | `observability.md` moves from planned to written; new paragraph about rules whose `paths` gets rewritten during generation | 7 · 8 |
| `project-bootstrap/SKILL.md` step 6.6 | § Rules with territory: the `paths` of `api-rest.md`, `persistence.md`, `value-objects.md` and `observability.md` now derives from `packages.map`, with the four derivation rules and a check that the glob matches | 8 |
| `project-bootstrap/SKILL.md` § Contract | `persistence.md`, `testing.md` and `observability.md` join the mandatory reading list | 7 |
| `project-bootstrap/references/dependency-catalog.md` | `validation` becomes mandatory with `rest`; new features `observability` and `uuid-v7`; JDK 25 warning; warning to confirm the four `id`-less features in the Initializr with `dependency:tree` | 3 · 6 · 7 |
| `blueprints/*.yaml` (4 files) | `validation`, `uuid-v7` and `observability` added to `features` | 3 · 6 · 7 |
| `rules/persistence.md` § Identity | UUID v7 comes from `Generators.timeBasedEpochGenerator()`, never hand-written code; id generated in the application layer | 6 |
| `rest-api-architect/templates/ApiExceptionHandler.java.example` | Two variants, chosen by the `feature: observability`; `traceId` computed once and written to both the log and the body | 7 |
| `rest-api-architect/SKILL.md`, `use-case-design/SKILL.md`, `java-patterns/SKILL.md` | Stop naming `adapter/in/rest` literally; go through `packages.map` or `@RestController` instead | 15 |
| `.github/workflows/validate.yml` | Job `exemplar-imports` (resolves each import against the Initializr's JARs, plus a denylist of renamed symbols) and two new checks in the `design` job (rule glob matches a real package; a rule with territory is in the derivation table) | 4 · 5 · 8 · 9 |

**Not built, and why.** The job that *compiles* each `*.java.example` against the
reference project wasn't written. The exemplars don't form a compilable unit: they use
two different package roots (`com.example.demo` and `com.exemplo.minhaapi`), several
declare multiple classes per file in `// --- <File>.java` blocks, and no set closes over
the classes it references. Making them compilable is an exemplar rewrite, not a CI job,
and is a separate decision. What was built — resolving each `import` against the real
JARs — catches gaps 4, 5 and 9 without that rewrite, and that's where the value was.

### Batch C — pipeline and executor

| File | Change | Gap |
|---|---|---|
| `new-feature/SKILL.md` § Guardrail | Stops checking literal `src/domain/` and `adapter/` paths; discovers the domain package with `find`, and explains why the literal path guards nothing | 15 |
| `new-feature/SKILL.md` § Consolidation | New step 1: resolves divergences via a precedence table before consolidating, and the spec carries one single value per fact. New step 3: detects that the project now has business classes without ArchUnit and proposes `test-architect` in setup mode | 16 · 17 |
| `new-feature/SKILL.md` § Operational note | Replaces § Gap, obsolete since step 6.8: long background work dies with the machine's sleep; offers `caffeinate -i` or the main thread | 18 |
| `new-feature/templates/feature-spec.md.example` | New `## Divergences resolved` section at the end, marked as an audit trail — the blocks above already carry the winner | 16 |
| `use-case-design/SKILL.md` § Doesn't decide | Path, verb and HTTP status now explicitly belong to `rest-api-architect` | 16 |
| `use-case-design/templates/use-case-spec.md.example` | Trigger without a path; the **Exits** table now lists situations, not statuses | 16 |
| `agents/java-spring-boot-developer.md` | § Discover the layout (destination map derived from `find`, compilation command per multi- or single-module); § Execution rule (write early, one step at a time) and § Resume; block 2, 3 and 4 validations gain `saveAndFlush`, the `ApiExceptionHandler` variant and `errorCode` assertion; missing dependency for execution instead of editing `pom.xml` | 15 · 18 · 2 · 3 · 7 |

Goes to the generated project: **yes** — `observability.md` and the changed rules
(`api-rest.md`, `testing.md`, `naming.md`, `persistence.md`) are copied by step 6.6,
which needs to gain the new rule's line. The exemplars and the creation skills stay out,
being generation-time material.
