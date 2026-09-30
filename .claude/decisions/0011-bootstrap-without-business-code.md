# 0011 · Bootstrap stops writing business code: it delivers structure, configuration and enforcement, and nothing else

- **Date:** 2026-09-08
- **Scenario:** "creating a project with /init-project loads feature examples that end up
  overloading the first project creation, with example code that can conflict with the
  future specs and code that the future /new-feature skill will orchestrate. Evaluate the
  idea: blueprints and project bootstrap don't generate Java code, only module
  organization, packages and general configuration files"
- **Decision:** **Option 1** — no new piece. Rewrite of `project-bootstrap/SKILL.md`
  (step 4.5 drops, step 4.7 now materializes packages and configuration), 12 exemplars
  deleted, 5 moved to `domain-modeling/templates/`, coverage gate transferred to
  `test-architect`'s setup mode.
- **Status:** approved by Lucas Fernandes, on 2026-09-08

## What motivated the record

Three conditions from § 3.5, and one would have sufficed: four options scored ≥ 5; the
approved option redistributes ownership across four skills; and axis 8 answers
**both** — the `DomainException` family changes owner to `domain-modeling`, which is
copied to the generated project in step 6.7.

On top of that, the symptom that started all this isn't hypothetical: running
`/init-project --blueprint clean-architecture-single-module --artifact demo-app` today
produced the two defects this scenario predicted.

## Observed symptom (axis 1)

| # | Defect | Where it showed up |
|---|---|---|
| 1 | Exemplars contradicting each other | `_shared/UserExample.java.example` (owner of the `domain.model` role) declares `id` + `name`; `flyway/V1__create_users_table.sql.example`, `openapi/OpenApiAnnotationsExample.java.example` and `testcontainers/UserRepositoryJpaAdapterIntegrationTestExample.java.example` still assume `email`. The agent followed the role's owner and generated `users(id, name)` — the repo's own migration and test disagree with the entity |
| 2 | Coverage gate red at birth | Step 4.7 emits 11 public classes with no tests at all, and `pom.parent.xml.example` carries `testing.md`'s gate (80% lines / 70% branches) in the `verify` phase. The agent had to write 6 ad hoc test classes (20 tests) for the build to close green. No step in `SKILL.md` says to write them |
| 3 | Duplicated ownership | `templates/features/` has 12 Java and SQL exemplars for a made-up `User` aggregate. The layer skills are already the owners of the same form: `domain-modeling` 7 exemplars, `rest-api-architect` 6, `persistence-architect` 3, `test-architect` 6. Invariant 2 broken — and defect 1 is exactly the divergence invariant 2 predicts |

Defect 3 is the cause of the other two. 1 and 2 are what it cost on this run.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | Concrete and reproduced above, not preemption | Rules out anti-pattern 9, which blocked D-0010 |
| 5 — Nature | Procedure: which steps bootstrap runs and what it emits | Forms 4 and 5 — no new declarative fact to write |
| 7 — Enforceability | **Prose in `SKILL.md` and the output contract.** Not a hook | Closes the "out of scope" option; assumed to be persuasion |
| 8 — Destination | **Both.** `project-bootstrap` stays in this repo, but `domain-modeling` receives the exceptions and goes to the project in step 6.7 | Requires saving this record |
| 9 — Integration | Four skills touched: `project-bootstrap` loses, `domain-modeling` and `test-architect` gain, `persistence-architect` and `rest-api-architect` are confirmed owners of what they already had | Rules out the "just delete" option without reassigning |
| — Cut | **All business Java, exceptions included.** Steps 4.5 and 4.7 disappear | Eliminates the "only 4.7 goes" variant |
| — Exemplars | **Delete.** Don't move: owners already exist | Eliminates option 3 below |
| — Non-Java | **Actuator YAML stays, migration SQL goes** | The actuator is general configuration; `V1__create_users_table.sql` is business schema |
| — Empty packages | **`package-info.java` per role** | Eliminates `.gitkeep` and "don't create the directory" |
| — Coverage | **The gate only kicks in with the first feature**, turned on by `test-architect`'s setup mode | Eliminates "exclude the startup class" and "check first" |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Rewrite `project-bootstrap`: Java goes, `package-info.java` files come in; exceptions move to `domain-modeling`, coverage gate to `test-architect`'s setup mode | 9 | **Approved** |
| 2 | Relocate the 12 exemplars into the layer skills instead of deleting them | 6 | Rejected — creates a second exemplar per role next to the one that already exists; invariant 2 again, under another name |
| 3 | Keep step 4.7 behind a `--with-examples` flag | 4 | Rejected — the ownership duplication stays intact, just becomes optional. Invariant 2 violated caps the score at ≤ 4 |
| 4 | Don't change the design: fix only `email` vs `id`+`name` and add a step that generates tests for the 11 classes | 5 | Rejected — closes defects 1 and 2 and leaves 3, the cause. Diverges again on the next exemplar touched |
| 5 | New skill just to install the error infrastructure at startup | 3 | Rejected — sixth piece with no symptom of its own; anti-pattern 9 |

### Option 1 — rewrite `project-bootstrap` (score 9)

**Motivator:** axes 1 and 9. The symptom is real and the cause is duplicated ownership,
not a badly written exemplar.

**Pros:**

- Closes all three defects at once, and 3 is the only one that prevents recurrence.
- Restores invariant 2 without writing a new rule: every role has one owner again.
- Aligns bootstrap with the D-0002 and D-0010 pipeline. The five layer skills emit spec,
  the executor emits code. A bootstrap that already emits a made-up `User` aggregate puts
  competing code in the path of the first real spec — exactly the material D-0010's gate
  requires to unlock the executor.
- Cuts 12 files and two steps from `SKILL.md`. It's the only option that reduces surface
  area.
- The output contract becomes honest: today it announces "Examples generated: rest,
  persistence-jpa, openapi, flyway, actuator, testcontainers" and delivers classes the
  user is going to delete.

**Cons:**

- The freshly generated project no longer compiles any example code. Whoever wanted to
  see a controller's shape only gets it inside the skill that emits it — the "open and
  read" immediacy is lost. Mitigation: the root `CLAUDE.md` routes to the skills, and
  that's where the form is owned.
- The coverage gate leaves bootstrap and now depends on someone running
  `/test-architect` in setup mode. Between generation and that run, `verify` checks no
  coverage at all. It's the same window ArchUnit already has today, declared in the
  output contract — not a new window, the second one with the same shape.
- Enforcement is still persuasion (axis 7): nothing stops the model from writing Java
  during a bootstrap. § Contract stops listing `src/main/java/**`, and that's it.

**Points cut on the rubric:** criterion 4 (enforcement) — the guarantee existed
(`permissions.deny` or an `ArchHook` subcommand) and isn't used, by axis 7's explicit
decision.

### Option 2 — relocate the exemplars (score 6)

**Motivator:** axis 9, reading "written work isn't thrown away."

Violates no invariant by itself, and is what would be done if the layer skills had no
exemplars. They do. `UserEntityExample` next to `persistence-architect`'s entity
exemplar is two exemplars for the same role, and "which one is correct?" reopens on the
first update. Loses for that, and for not reducing surface area.

Sole survivor of this option, already absorbed by Option 1:
`features/actuator/application-actuator.yml.example`. It's configuration, has no owner
elsewhere, and the step that merges it into `application.yml` stays.

### Option 3 — `--with-examples` flag (score 4)

Treats the symptom as excess volume. It isn't: it's an ownership conflict. With the flag
off, the 12 exemplars still sit on disk diverging from the layer skills' own, and the
first person to read them doesn't know which one rules. Invariant 2 violated caps at
≤ 4.

### Option 4 — fix and add the test step (score 5)

The honest minimal-maintenance option, and the one that'd be done with little time.
Closes defects 1 and 2 with small work. Fails by leaving the cause standing: two owners
for the same role diverge again, and the new test step would add to this `SKILL.md` the
obligation to write more Java — in the direction opposite the pipeline's.

### Option 5 — new skill for the error infrastructure (score 3)

No symptom of its own. `domain-modeling` already owns the domain's form, already reads
`error-handling.md`, and already goes to the generated project. One piece too many.

## What changes, concretely

### `project-bootstrap/SKILL.md`

| Step | Today | After |
|---|---|---|
| 4.5 | Generates `DomainException` and the 4 typed ones | **Gone.** Moves to `domain-modeling` |
| 4.7 | Generates 11 Java classes + 1 SQL from `templates/features/` | **Gone.** Replaced by a step that writes one `package-info.java` per role from `packages.map`, and merges `application-actuator.yml.example` when the `actuator` feature is active |
| 4.6 | Checkstyle | Unchanged. Remains the only automatic check bootstrap installs |
| 8 | Verification, including the boundary test and the `lombok.config` one | Stays. The boundary test now needs a class: uses the `@SpringBootApplication` one, which the Initializr generates anyway |
| Output contract | "Examples generated: …" line | Becomes "Business code: none — by design" plus the coverage-gate line, in the same shape the ArchUnit line already has |
| § Contract | `owns` includes `src/main/java/**` | Removed from the list. Keeps `src/main/java/**/package-info.java` |

Two consequences in step 8 not anticipated in the draft:

- **The boundary test needed a class from `domain`, and there's no longer one.** It now
  writes a temporary probe `ArchHookProbe.java` with the forbidden import, confirms the
  block, and deletes it. It's the only piece of business code the procedure ever writes,
  and it only exists for the duration of the test.
- **Zero `*IT` discovered becomes the expected result** in `verify`, unlike before, when
  step 4.7 generated the Testcontainers IT. What's now confirmed is that
  `maven-failsafe-plugin` stayed in the POM, with `grep -c`.

And a fix to the output contract that was left over: the "Next steps" used to say to run
`/new-feature`, which doesn't exist (D-0010). Now they say to run `/use-case-design`,
which is the pipeline's real entry point today.

### Files deleted

The whole of `templates/features/`, **except**
`actuator/application-actuator.yml.example`:

```
_shared/UserExample.java.example                       → domain-modeling/templates/Aggregate.java.example
rest/UserControllerExample.java.example                → rest-api-architect
rest/CreateUserUseCaseExample.java.example             → domain-modeling/templates/UseCasePort.java.example
rest/CreateUserRequestExample.java.example             → rest-api-architect
rest/UserResponseExample.java.example                  → rest-api-architect
persistence-jpa/UserEntityExample.java.example         → persistence-architect
persistence-jpa/UserJpaRepositoryExample.java.example  → persistence-architect
persistence-jpa/UserRepositoryJpaAdapterExample.java.example → persistence-architect
persistence-jpa/UserMapperExample.java.example         → persistence-architect
persistence-jpa/UserRepositoryPortExample.java.example → domain-modeling/templates/UseCasePort.java.example
openapi/OpenApiAnnotationsExample.java.example         → rest-api-architect (annotated controller)
flyway/V1__create_users_table.sql.example              → persistence-architect/templates/V1__create_table.sql.example
testcontainers/UserRepositoryJpaAdapterIntegrationTestExample.java.example → test-architect/templates/PersistenceIT.java.example
```

Right column = the owner that already exists. None of these files leaves without a
replacement.

### Files moved

The five exception exemplars move from `project-bootstrap/templates/` to
`domain-modeling/templates/`, verbatim:
`DomainException`, `NotFoundException`, `ValidationException`,
`BusinessRuleViolationException`, `ConflictException`.

**Fix made in Phase 4, and it matters:** `domain-modeling` has its own output
rule — *it doesn't write code*, it emits `10-domain.md`. Handing it the *emission* of
the exceptions would break that rule and its § Contract. What it receives is **the form
and the status**, not the writing:

| Receives | Doesn't receive |
|---|---|
| The five `.example` files in `templates/` | Writing the `.java` classes |
| Step 5 of the procedure: search for `DomainException` with `Grep` and mark **NEW** or **REUSE** in the partial, once per project | Any write path under `src/**` |

Whoever writes stays the executor, as in D15. **Consequence to state:** while
`java-spring-boot-developer` doesn't exist (D-0010's gate), the generated project stays
without exception classes until someone writes them by hand from the exemplars. It's the
price accepted by answering "all business Java goes."

### Coverage gate

`pom.parent.xml.example` generates JaCoCo without `haltOnFailure` (report yes, gate no).
`test-architect`'s setup mode — which already writes to the POM to install ArchUnit —
turns the gate on with `testing.md`'s numbers. The two enforcements that depend on code
existing now enter through the same door, at the same moment.

## The open question — resolved

Flyway active with no migration at all. Confirmed in the jar's metadata, which
`demo-app` resolved, not from memory:

```
spring-boot-flyway-4.1.1.jar → META-INF/spring-configuration-metadata.json
  "name": "spring.flyway.fail-on-missing-locations"
  "defaultValue": false
```

An absent or empty location doesn't break startup. Step 4.7 creates `db/migration/`
empty, without `.gitkeep` and without `V1__`.

## References

| Statement | Source |
|---|---|
| Every rule and every form has one owner file; written in two places, they diverge | `@CLAUDE.md` invariant 2 |
| Boilerplate lives in `templates/` inside the skill that emits it | `@CLAUDE.md` invariant 3 |
| Architectures are data: adding a blueprint edits no skill | `@CLAUDE.md` invariant 7 · `@.claude/blueprints/_schema.md` |
| The generated project is autonomous; a new rule or skill is only complete once the step that copies it is updated | `@CLAUDE.md` invariant 9 |
| Two pieces writing to the same paths is an ownership bug | `references/decision-matrix.md` § 7, anti-pattern 5 |
| An invariant violation caps the score at ≤ 4 | `references/decision-matrix.md` § 8 |
| Layer skills emit spec; code comes from the executor | `@.claude/decisions/0003-skill-domain-modeling.md` · `@.claude/decisions/0010-pipeline-executor-and-orchestrator.md` |
| The executor's gate is two complete specs produced in a generated project | `@.claude/decisions/0010-pipeline-executor-and-orchestrator.md` § The gate |
| `test-architect` already writes to the POM in setup mode | `@.claude/skills/test-architect/SKILL.md` § Procedure — setup mode |
| Bootstrap already states it doesn't generate ArchUnit because there are no classes to apply it to | `@.claude/skills/project-bootstrap/SKILL.md` § 4.6, last paragraph |
| The coverage gate lives in `pom.parent.xml.example`, with `COVEREDRATIO` and `limits` | `@.claude/skills/project-bootstrap/templates/pom.parent.xml.example` lines 175-213 |
| The coverage rule is 80% lines / 70% branches | `@.claude/rules/testing.md` · `@.claude/rules/00-index.md` |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/project-bootstrap/SKILL.md` | Step 4.5 gone; 4.7 replaced; preconditions, output contract and § Contract rewritten |
| `.claude/skills/project-bootstrap/templates/features/**` | Deleted, except `actuator/application-actuator.yml.example` |
| `.claude/skills/project-bootstrap/templates/{DomainException,NotFoundException,ValidationException,BusinessRuleViolationException,ConflictException}.java.example` | Moved to `domain-modeling/templates/` |
| `.claude/skills/project-bootstrap/templates/pom.parent.xml.example` | JaCoCo without `haltOnFailure` |
| `.claude/skills/domain-modeling/SKILL.md` | Step 5 marks the exception family NEW/REUSE; blocks table, exemplars section and § Contract updated |
| `.claude/skills/test-architect/SKILL.md` | Setup mode gains points 8 and 9: turns on JaCoCo's `check` execution. Mode title, modes table, declared-exception section and § Contract updated |
| `.claude/skills/project-bootstrap/templates/pom.parent.xml.example` | JaCoCo with `prepare-agent` and `report`; `check` execution removed, with the reason in a comment |
| `.claude/skills/project-bootstrap/SKILL.md` § 6.7 | The `domain-modeling` line now says twelve exemplars, not seven |
| `.claude/agents/project-initializer.md` § Interview | Active feature = dependency + configuration, not code; don't ask about coverage, same as no longer asking about ArchUnit |
| `CLAUDE.md` | No change — no new piece to route |
| `.claude/rules/**` | No change — no rule's content changes. `testing.md` still fixes 80%/70%; who turns on the gate changes, and that's procedure, not rule (invariant 1) |
| `.claude/blueprints/**` | No change — `features` still controls Initializr dependencies and configuration. Verified by `grep`: no YAML cited `templates/features/` |

Goes to the generated project: **partly**. `project-bootstrap` doesn't go (step 6.7, it's
a creation skill). `domain-modeling` and `test-architect` go, and carry what they
gained — hence axis 8 is "both" and this record exists.
