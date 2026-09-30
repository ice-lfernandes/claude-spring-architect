# 0096 · Remediation of lessons-learned-017 — templates that ship Sonar findings

- **Date:** 2026-09-30
- **Scenario:** `@.claude/lessons-learned/lessons-learned-017.md` — the first SonarQube
  analysis of a generated project failed the `Sonar way` gate on 14 new violations, with 42
  open issues; the build (`./mvnw verify`) was green. The user scoped this run to the
  **templates** group: §§ 1, 2, 3, 4, the comment half of § 5, and the template half of § 6.
- **Decision:** Options 1.1, 2.1, 3.1, 4.1, 5b.1 and 6.1 — persuasion by exemplar only, each
  fix in the template that produced the finding: `AssignedIdEntity` `@MappedSuperclass` in
  `persistence-architect/templates/JpaEntity.java.example`, extended by the outbox and
  idempotency entities, with one clause in `rules/persistence.md` § Identity and keys; record
  pattern in `IdempotentExecution`; one-return `preHandle`; `List<Object>` in
  `LoggingCommonsMethods.Call`; two comments without a code fragment; arrange-before-act in
  two test templates. No hook, no new piece, no Java in `ArchHook.java`.
  **Option 23.1 (suppression of S2326 and S112) is not approved — deferred**, see below.
- **State:** approved by Lucas Fernandes, on 2026-09-30 — every option except 23.1

## Scope of this run

In: §§ **1, 2, 3, 4, 5b** (comments that contain a code fragment), **6** (the two templates).

Out — a later run takes them. One mechanism was **already chosen** by the user during this
interview:

| § | Deferred item | Mechanism already answered |
|---|---|---|
| 9 | Nothing in the build loop runs Sonar's rules | **Done in `0097`** (imports went to Spotless only, not Checkstyle). Chosen here: **option 1 only, offline:** `spotless:check` bound to `verify`; Checkstyle gains `UnusedImports` and `IllegalIdentifierName`; a second, lighter Checkstyle execution over `src/test`. **No** Sonar step in `java-spring-boot-developer` |
| 8 | `record` as a variable name | **Done in `0097`** — covered by § 9's answer (`IllegalIdentifierName`, plus the `src/test` execution) |
| 5a | No `default` on a switch over a sealed type | **Done in `0098`** |
| 6 | Rule line "one call per `assertThatThrownBy` lambda" in `rules/testing.md` | **Done in `0098`** |
| 7 | Kafka "record arrives" tool, `isNotEmpty()` before `allSatisfy`, aspect test templates for `commons` | **Done in `0098`** |
| 10 | Anonymous access steps, pre-flight access check, rerun command in `sonarqube-setup` | **Done in `0098`** |
| 2, 3 | `java:S2326` on `IdempotentOutcome<R>` and `java:S112` on `IdempotencyAspect#aroundIdempotent` — both accepted by design, both still reported | **Deferred by the user here; decided in `0098` — option 23.1 (F).** Options 23.1 (multicriteria in the Sonar build templates, 7), 23.2 (`@SuppressWarnings("java:Sxxx")`, 7) and 23.3 (`// NOSONAR`, 4) stay on record below |

## Reproduced on disk before classifying

Checked against `90bbd01`. Every claim holds; two need a correction, recorded so nobody
re-opens them.

| § | Claim | On disk |
|---|---|---|
| 1 | The ArchUnit guard from `0039` must be updated to accept the base class | **Not needed.** `test-architect/templates/ArchitectureTest.java.example:153` uses `.should().implement("org.springframework.data.domain.Persistable")`, and ArchUnit's `implement` is transitive — an entity that extends a `@MappedSuperclass` implementing `Persistable` passes. `HAVE_ASSIGNED_ID` reads `getAllFields()` of the `@Entity`, where `@Id` stays. `agents/java-spring-boot-developer.md:340` ("implements `Persistable`") stays true for the same reason |
| 1 | The duplication hurts the gate | It did **not** fail it: `new_duplicated_lines_density` 0.0 %. The fix is still taken — every aggregate adds 14 more copied lines |
| 2 | `IdempotentExecution.java.example:34,66` | Confirmed; the same fragment in a comment at `:82` (§ 5b) |
| 3 | `preHandle` returns `true` on both branches | Confirmed: two `return true` in `preHandle` (`:63`, `:71`). `IdempotencyAspect.java.example:239` `throws Throwable`, with the Checkstyle suppression at `checkstyle.xml.example:105-112` and no Sonar counterpart |
| 4 | `LoggingCommonsMethods.java.example:67` `Object[] args` | Confirmed. `call.args()` is only logged (`:45,54,57`); SLF4J renders an `Object[]` and a `List` the same way, `[a, b]` |
| 5b | `ScheduledJob.java.example:88`, `IdempotentExecution.java.example:82` | Confirmed; a sweep of every `.example` finds no third comment with a `` `catch (` `` fragment |
| 6 | `UseCaseTest.java.example:61`, `PersistenceIT.java.example:89` | Confirmed; `DomainTest.java.example:37` already makes one call. No other template has a nested call inside the lambda |

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | 42 Sonar issues on a green build; 5 issues + the only duplication copied verbatim from templates, 17 from a pattern two test templates teach | create nothing, for every section in scope |
| 5 — nature | Exemplar code the executor copies — neither a fact nor a procedure | new rule, new skill, new agent |
| 7 — mandatoriness | Persuasion by exemplar is enough here; the guarantee is § 9's offline gate, deferred | Forms 7 and 8 |
| 8 — destination | Both — every touched template lives in a skill listed in `export.skills.include` | — |
| 9 — integration | The executor writes `src/**`; `sonarqube-setup` owns the root build file's Sonar block | a suppression written by anyone but `sonarqube-setup` |
| — suppression shape | Chose multicriteria in the interview, then held 23.1 back at approval: to be evaluated later | — |
| — § 1 | `AssignedIdEntity` `@MappedSuperclass` | keep the inline copy |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1.1 | `AssignedIdEntity` block in `JpaEntity.java.example`; `OutboxEventStore` and `IdempotencyKeyStore` extend it; one clause in `rules/persistence.md` § Identity and keys | 8 | **Approved** |
| 1.2 | create nothing — keep the 14 lines inline | 4 | Rejected — the copy grows per aggregate |
| 2.1 | S6878: record pattern in `IdempotentExecution.java.example:66` | 9 | **Approved** |
| 23.1 | S2326 + S112: `sonar.issue.ignore.multicriteria` in `pom-sonar.xml.example` and `build-gradle-sonar.example`, each template's comment pointing at it | 7 | Deferred — decision later |
| 23.2 | `@SuppressWarnings("java:S2326")` / `("java:S112")` on the element | 7 | Deferred — fallback of 23.1 |
| 23.3 | `// NOSONAR` on the line | 4 | Rejected — suppresses every rule on the line |
| 3.1 | S3516: `preHandle` restructured to one `return true` | 9 | **Approved** |
| 4.1 | `Call.args` as `List<Object>` via `Arrays.asList` | 9 | **Approved** |
| 5b.1 | Reword the two comments without a code fragment | 9 | **Approved** |
| 6.1 | Arrange the argument before `assertThatThrownBy` in the two templates | 8 | **Approved** |

### Option 1.1 — `AssignedIdEntity` (score 8)

**Motivator:** axis 1, the only duplicated block Sonar reported, grown by one copy per
aggregate.

**Pros:** the `Persistable` mechanics exist once per project; entities implement only
`getId()`. The ArchUnit guard and the developer agent need no change (`implement` is
transitive). JPA applies `@PostLoad`/`@PostPersist` declared on a `@MappedSuperclass` to
every subclass.

**Cons:** a new cross-aggregate package, `…persistence.shared` — § Boundary says one
subpackage per aggregate; `outbox` and `idempotency` already set the precedent of
non-aggregate subpackages. The outbox and idempotency templates now depend on a class from
another template: the executor creates it when absent.

**Points cut in the rubric:** maintenance (−1: one more shared class every assigned-id
entity depends on); precedent (−0.5: first `@MappedSuperclass` in the template set).

### Option 1.2 — create nothing (score 4)

The gate passed; but the copy grows with every aggregate, and the rule it implements is
correct. **Cut:** form fit, maintenance. Rejected by the user in the interview.

### Option 2.1 — record pattern (score 9)

Java 21 is the floor; `IdempotencyClaim.Replay(StoredResponse response)` is a record.
**Cut:** none material.

### Option 23.1 — multicriteria in the Sonar build templates (score 7)

**Motivator:** axis 9 — `sonarqube-setup` is the only writer of the build file's Sonar block,
and a project without Sonar carries no suppression it does not need.

**Pros:** scoped by rule key and file; one place lists every accepted finding; nothing in the
Java code.

**Cons:** far from the code — each template's comment must point at it, or the next reader
"fixes" a finding that was accepted. Scoped to the file, not to the method: a second
`throws Throwable` in `IdempotencyAspect.java` would pass unseen, where Checkstyle's XPath
suppression is narrower. A project where `sonarqube-setup` already ran does not get the
entries until it is re-run. A renamed file silently drops out of the suppression — Sonar then
reports it, which is visible.

**Points cut in the rubric:** maintenance (−1, two places to keep in sync by comment);
propagation (−1, already-configured projects miss it).

### Option 23.2 — `@SuppressWarnings("java:Sxxx")` (score 7)

Scoped by rule and element, travels with the code, read by SonarLint too. **Cut:** a
Sonar-specific key inside every project's code, including those with no Sonar. Kept as the
fallback if 23.1's file scope proves too wide.

### Option 23.3 — `// NOSONAR` (score 4)

Suppresses **every** rule on the line, not the one named in the comment. **Cut:** trust
surface, enforcement.

### Option 3.1 — `preHandle` with one return (score 9)

`requiresKey` → a `void requireValidKey(request)` that throws, then a single `return true`.
S3516 only fires on two or more returns of the same value. The annotation-based variant
comment stays valid. **Cut:** none material.

### Option 4.1 — `List<Object> args` (score 9)

`Arrays.asList`, not `List.of`: arguments may be null. Log output unchanged. **Cut:** none.

### Option 5b.1 — reword the comments (score 9)

"No catch of the base runtime exception here: Checkstyle's IllegalCatch refuses it."
**Cut:** none.

### Option 6.1 — arrange before act (score 8)

The only lever for 17 of 42 issues is the exemplar the executor copies. **Cut:** enforcement
(−1: persuasion; the rule line is deferred).

## References

| Claim | Source |
|---|---|
| Boilerplate lives in `templates/` inside the skill that emits it | `@CLAUDE.md` invariant 3 |
| A norm has one owning file; the template cites it | `@CLAUDE.md` invariant 2 — `rules/persistence.md` § Identity and keys |
| A rule never names a skill or a template | `@CLAUDE.md` invariant 1 |
| The ArchUnit guard accepts a superclass | `skills/test-architect/templates/ArchitectureTest.java.example:150-155` |
| `sonarqube-setup` owns the root build file's Sonar block | `skill_classes.build.overrides.sonarqube-setup` in `@.claude/schemas/extensions.json`; `0091` |
| The Checkstyle counterpart of the S112 suppression | `skills/project-bootstrap/templates/checkstyle.xml.example:100-112` |
| Measure with the real analyzer before prompt content | memory `tool-before-prompt-catalog`; `0091` |

## Propagation

| File | Change |
|---|---|
| `skills/persistence-architect/templates/JpaEntity.java.example` | `UserEntity extends AssignedIdEntity<UUID>`; new last block `shared/AssignedIdEntity.java` (`@MappedSuperclass`, `public abstract`, `Persistable<T>`, the flag and both callbacks); header gains "WHY A BASE CLASS" |
| `skills/persistence-architect/templates/OutboxEventStore.java.example` | `OutboxEventEntity extends AssignedIdEntity<UUID>`; flag, `isNew()`, callbacks and their imports removed |
| `skills/persistence-architect/templates/IdempotencyKeyStore.java.example` | `IdempotencyKeyEntity extends AssignedIdEntity<UUID>`; same removal |
| `skills/persistence-architect/SKILL.md` | Step 4: an assigned id names `Persistable` through `AssignedIdEntity` |
| `rules/persistence.md` | § Identity and keys: flag and callbacks written once, in one `@MappedSuperclass` |
| `skills/persistence-architect/templates/IdempotentExecution.java.example` | Record pattern `Replay(StoredResponse response)` (S6878); comment at the `finally` without a code fragment (S125) |
| `skills/jobs-architect/templates/ScheduledJob.java.example` | `JobRunRecorder#run` comment without a code fragment (S125) |
| `skills/rest-api-architect/templates/IdempotencyKeyInterceptor.java.example` | `preHandle` with one `return true`; check extracted to `requireValidKey` (S3516); annotation-variant note names it |
| `skills/new-feature/templates/commons/LoggingCommonsMethods.java.example` | `Call.args` as `List<Object>` via `Arrays.asList` (S6218) |
| `skills/test-architect/templates/UseCaseTest.java.example` | Command built before `assertThatThrownBy` (S5778) |
| `skills/test-architect/templates/PersistenceIT.java.example` | Duplicate user built before `assertThatThrownBy` (S5778) |

Not touched, on purpose: `test-architect/templates/ArchitectureTest.java.example` and
`agents/java-spring-boot-developer.md:340` — `implement(Persistable)` is transitive, both
stay true as written.

Goes to the generated project: **yes, via `export`** — every touched file lives in a skill
listed in `export.skills.include`, or in `rules/`. No new file, so the manifest does not
change. A project generated before this change keeps its inline copies until `/arch-adopt`
pulls the new templates; its existing entities are not rewritten.
