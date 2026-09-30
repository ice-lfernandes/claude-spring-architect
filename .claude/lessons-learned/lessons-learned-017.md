# Lessons learned 005 — first SonarQube analysis of the generated project

Run date: 2026-09-30. Scope: the first `./mvnw -B verify sonar:sonar` against the local
`sonarqube:26.9.0.129388-community` container that `sonarqube-setup` wired in, after UC-001,
UC-002 and UC-003 were implemented. The build was green: every unit test and all 26 `*IT` tests
passed. Checkstyle and ArchUnit passed. SonarQube then **failed the quality gate** (`Sonar way`).

Dashboard: `http://localhost:9000/dashboard?id=com.example%3Ademo-clean-arch-single-module`

This file lists every open issue and every duplicated block. The main point is to trace each one
to the **meta-repository** piece that produced it: a template, a rule, the Checkstyle config, or
a gap where the model decided alone. Every fix lands in `claude-spring-architect`, not here.

---

## Snapshot

| Measure | Value |
|---|---|
| Quality gate | **ERROR**. Failing condition: `new_violations` 14 > 0 |
| `new_coverage` | 96.4 % (threshold 80 %), OK |
| `new_duplicated_lines_density` | 0.0 % (threshold 3 %), OK |
| Coverage (overall) | 94.2 % |
| Lines of code | 2,690 |
| Bugs | 3. Reliability rating **C** |
| Vulnerabilities / Security hotspots | 0 / 0 |
| Code smells | 39. Maintainability rating A |
| Duplicated lines | 0.7 %. One block, 14 lines, in two files |
| Open issues | **42**: 9 in `src/main`, 33 in `src/test` |

Issues by origin:

| Origin in the meta-repo | Issues | Sections |
|---|---|---|
| Template copied verbatim | 5 (+ the duplication) | § 1, § 2, § 3, § 4 |
| Model's false belief about Checkstyle, or a template comment | 2 | § 5 |
| Test template teaches the pattern | 17 | § 6 |
| No rule and no template: the model decided alone | 18 | § 7, § 8 |

Nothing that `verify` runs could catch any of the 42 issues. That is § 9, and it is the
structural lesson.

---

## § 1 · `JpaEntity.java.example` duplicates the `Persistable` boilerplate in every entity

**What happened.** SonarQube reports the project's only duplicated block, 14 lines, in two files:

- `infrastructure/persistence/account/AccountEntity.java:58-71`
- `infrastructure/persistence/customer/CustomerEntity.java:59-72`

The block is `getId()`, `isNew()`, and `markNotNew()` under `@PostLoad`/`@PostPersist`. The same
shape also exists in `OutboxEventStore` and `IdempotencyKeyStore`. Sonar does not flag those,
because the surrounding lines differ enough to break the match.

**Why.** `rules/persistence.md` § Identity and keys (decision 0039) requires every entity with
an assigned `@Id` to implement `Persistable<T>` with a transient `isNew` flag. The rule is
correct. But the only implementation of it is inline, in three templates:

- `skills/persistence-architect/templates/JpaEntity.java.example:127-138`
- `skills/persistence-architect/templates/OutboxEventStore.java.example:174`
- `skills/persistence-architect/templates/IdempotencyKeyStore.java.example:225`

Every new aggregate therefore adds one more copy of the same 14 lines.

**Fix.** Ship a `@MappedSuperclass` base in the persistence template set, for example
`AssignedIdEntity<ID> implements Persistable<ID>`. It holds the transient flag and both
callbacks. Entities extend it and implement only `getId()`. Update the
`java-spring-boot-developer.md:340` clause and the ArchUnit guard from decision 0039 to accept
"extends `AssignedIdEntity`" as satisfying the rule.

Rejected alternative: `sonar.cpd.exclusions=**/*Entity.java`. That hides the signal instead of
removing the copy.

---

## § 2 · `IdempotentExecution.java.example` ships two Sonar smells

| Rule | Location in project | Message |
|---|---|---|
| `java:S6878` | `application/shared/IdempotentExecution.java:33` | Use the record pattern instead of this pattern match variable. |
| `java:S2326` | `application/shared/IdempotentOutcome.java:7` | R is not used in the interface. |

**Why.** Both lines come verbatim from
`skills/persistence-architect/templates/IdempotentExecution.java.example`:

- Line 66: `if (idempotencyKeys.claim(request) instanceof IdempotencyClaim.Replay replay)`,
  followed by `replay.response()`.
- Line 34: `public sealed interface IdempotentOutcome<R>`. `R` appears only in the permitted
  records, not in the interface body.

**Fix.**

- S6878: change the template to a record pattern,
  `instanceof IdempotencyClaim.Replay(StoredResponse response)`. Java 21 is the floor, so this is
  free.
- S2326: this is a false positive for a sealed generic sum type. `R` exists so that
  `Executed<R>` and `Replayed<R>` share a type. Keep the shape, and add a one-line
  `// NOSONAR java:S2326 — type parameter binds the permitted records` in the template. Or ship a
  `sonar.issue.ignore.multicriteria` entry for this file through `sonarqube-setup` (§ 10).

---

## § 3 · `IdempotencyKeyInterceptor` and `IdempotencyAspect` templates ship two Sonar smells by design

| Rule | Severity | Location in project | Message |
|---|---|---|---|
| `java:S3516` | **BLOCKER** | `infrastructure/rest/idempotent/IdempotencyKeyInterceptor.java:30` | Refactor this method to not always return the same value. |
| `java:S112` | MAJOR | `infrastructure/rest/idempotent/IdempotencyAspect.java:53` | Replace generic exceptions with specific library exceptions or a custom exception. |

**Why.**

- `skills/rest-api-architect/templates/IdempotencyKeyInterceptor.java.example:61`:
  `preHandle` rejects by **throwing** `MissingIdempotencyKeyException`, so that
  `@RestControllerAdvice` renders the error body. Both branches therefore `return true`. That
  design is deliberate and correct. Returning `false` would need the interceptor to write the
  response itself and bypass the error map.
- `skills/rest-api-architect/templates/IdempotencyAspect.java.example:239`: `aroundIdempotent`
  declares `throws Throwable`, because `ProceedingJoinPoint#proceed()` declares it. The
  Checkstyle template already suppresses `IllegalThrows` for exactly this method
  (`checkstyle.xml.example:109-112`). Sonar has no equivalent suppression.

The framework reasoned about this exception for Checkstyle, but not for Sonar. A BLOCKER on
the first analysis of every project that uses `Idempotency-Key` is a bad first impression.

**Fix.** Mirror each Checkstyle suppression as a Sonar suppression in the same pass:

- S3516: restructure `preHandle` so it does not look invariant. For example, extract
  `requireKey(request)` as a `void` method that throws, then `return true` once. Sonar no longer
  sees two identical returns. Otherwise add `// NOSONAR java:S3516` with the reason from the
  template's comment.
- S112: add `// NOSONAR java:S112 — proceed() declares Throwable`, next to the Checkstyle
  suppression comment. Or add a multicriteria ignore scoped to `**/IdempotencyAspect.java`.

---

## § 4 · `LoggingCommonsMethods.java.example` ships a Sonar **bug**

| Rule | Type | Location in project | Message |
|---|---|---|---|
| `java:S6218` | BUG, reliability MEDIUM | `commons/logging/LoggingCommonsMethods.java:69` | Override equals, hashCode and toString to consider array's content in the method |

**Why.** Verbatim from `skills/new-feature/templates/commons/LoggingCommonsMethods.java.example:67`:
`private record Call(String method, String type, Object[] args)`. A record with an array
component gets identity-based `equals`/`hashCode` and a `toString` that prints `[Ljava.lang.Object;@…`.
Today it is harmless, because `Call` is never compared or printed. But it is one of the three
bugs that pull reliability to **C**. Every project that runs `commons-logging-installer`
inherits it.

**Fix.** In the template, change the component to `List<Object> args`
(`List.of(joinPoint.getArgs())` rejects nulls, so use `Arrays.asList`). Or keep the array and
override `toString` with `Arrays.toString(args)`.

---

## § 5 · Checkstyle folklore produces code that Sonar then flags

Two issues exist because the model **believed** Checkstyle required something:

| Rule | Location in project | Checkstyle module the model cited |
|---|---|---|
| `java:S108` | `infrastructure/messaging/outbox/DomainEventOutboxWriter.java:34` | `MissingSwitchDefault` (`checkstyle.xml.example:94`) |
| `java:S125` | `infrastructure/scheduling/JobRunRecorder.java:34` | `IllegalCatch`, indirectly |

**S108: empty `default -> { }` on a sealed switch.** `DomainEventOutboxWriter.append` switches
over the sealed `DomainEvent` with pattern labels. The model added `default -> { }` with this
comment: *"Present only because Checkstyle's MissingSwitchDefault requires the clause
syntactically."* Sonar flags the empty block.

**The claim is false.** It was checked on a scratch copy of this project, with the project's own
`config/checkstyle/checkstyle.xml` (`MissingSwitchDefault` active at line 92) and Checkstyle
14.1.0. With the `default` clause removed, `./mvnw checkstyle:check` reports
`You have 0 Checkstyle violations.` `MissingSwitchDefault` does not validate switch statements
that use pattern labels.

The unnecessary `default` also does harm. It **removes** the compiler's exhaustiveness check. When
a second event is added to `permits`, the switch silently does nothing for it, instead of failing
compilation. The model gave up the language feature that makes the sealed hierarchy safe, to
satisfy a rule that did not apply.

**S125: prose read as commented-out code.** `skills/jobs-architect/templates/ScheduledJob.java.example:88`
carries a block comment that explains why there is no `catch (RuntimeException)`. Sonar's
heuristic reads `catch (RuntimeException)` inside the comment as dead code.

**Fix.**

- Add one line to the rules (e.g. `rules/` for Java style, or the developer agent): *"A switch
  over a sealed type has no `default`: exhaustiveness is the compiler's job. `MissingSwitchDefault`
  does not apply to pattern-label switches."* Without the line, the next model repeats the same
  belief.
- Consider an ArchUnit or hook check that flags `default -> { }` next to a `case` with a
  type pattern.
- `ScheduledJob.java.example:88`: reword the comment so it does not contain a code fragment. For
  example: *"No catch of the base runtime exception here: Checkstyle's IllegalCatch refuses it."*
  Apply the same wording to `IdempotentExecution.java.example:82`, which has the same fragment
  inside a `//` comment. Sonar has not flagged that one yet.

---

## § 6 · Test templates teach `assertThatThrownBy` with more than one call in the lambda (17 issues)

| Rule | Count | Locations in project |
|---|---|---|
| `java:S5778` | 17 | `CreateAccountUseCaseTest:62,89` · `RegisterCustomerUseCaseTest:73` · `CustomerRegisteredTest:49` · `CustomerTest:36,55` · `MoneyTest:24` · `AccountRepositoryJpaAdapterIT:101` · `CustomerRepositoryJpaAdapterIT:73` · `IdempotencyKeyStoreTest:73,88,119,133,158,180` · `IdempotencyKeyInterceptorTest:47,59` |

**Why.** The templates show the pattern, and the model copies it everywhere:

- `skills/test-architect/templates/UseCaseTest.java.example:61`:
  `assertThatThrownBy(() -> service.handle(command("ana@exemplo.com")))`
- `skills/test-architect/templates/PersistenceIT.java.example:89`:
  `assertThatThrownBy(() -> adapter.save(TestFixtures.userWithEmail("ana@exemplo.com")))`

The builder call inside the lambda can throw too. If it does, the test passes for the wrong
reason. For example, `CustomerId.of(UUID.randomUUID())` inside
`new CustomerRegistered(...)` at `CustomerRegisteredTest:49`.

This is the largest single group: 17 of 42 issues, about 40 % of the total.

**Fix.** In both templates, build the argument in the arrange block and keep one call in the
lambda:

```java
var command = command("ana@exemplo.com");

assertThatThrownBy(() -> service.handle(command))
```

Add one line to the testing rule: *"The lambda passed to `assertThatThrownBy` makes exactly
one call: the call under test."*

---

## § 7 · Test smells with no rule and no template: the model decided alone (13 issues)

`grep` finds none of these patterns in the meta-repo. They come from the model's own habits
while it wrote tests that `test-architect` specified only in prose.

| Rule | Type | Location in project | What is wrong |
|---|---|---|---|
| `java:S1751` | **BUG** | `infrastructure/messaging/outbox/OutboxRelayIT.java:137` | `for (record : records) { return record; }`: a loop that runs at most once, hand-rolled polling |
| `java:S5841` | **BUG** | `DemoCleanArchSingleModuleApplicationTests.java:59` | `allSatisfy(...)` on a collection that can be empty. With no `ScheduledTaskHolder` bean the test passes vacuously |
| `java:S1186` ×4 | smell, CRITICAL | `commons/logging/LogExecutionAspectTest.java:19,22` · `HttpMethodLogExecutionAspectTest.java:19,22` | Empty annotated fixture methods (`void defaultOptions() {}`) with no comment |
| `java:S5976` | smell | `commons/logging/LoggingCommonsMethodsTest.java:28` | Four copies of one test that differ only in `LoggingOptions(bool, bool)`: should be `@ParameterizedTest` |
| `java:S5853` ×2 | smell | `infrastructure/rest/dto/RegisterCustomerRequestTest.java:17` · `RegisterCustomerResponseTest.java:18` | Two `assertThat(masked)` statements instead of one chain |
| `java:S5838` | smell | `domain/model/CustomerIdTest.java:19` | `assertThat(x.toString()).isEqualTo(…)` instead of `hasToString(…)` |
| `java:S8924` | smell | `infrastructure/persistence/idempotency/IdempotencyKeyStoreTest.java:169` | `org.mockito.Mockito.verify(...)` fully qualified instead of statically imported |
| `java:S1128` ×2 | smell | `infrastructure/persistence/outbox/OutboxEventStoreIT.java:9,13` | Unused imports `java.time.Duration`, `java.util.List` |

The two bugs matter most. Both are tests that can pass without testing anything:

- **`OutboxRelayIT`** polls Kafka by hand, with a deadline loop and `System.currentTimeMillis()`.
  Nothing in `test-architect` names the tool for "wait until a record arrives". The model
  improvised. `KafkaTestUtils.getSingleRecord(consumer, topic, Duration)` or Awaitility does the
  same thing in one line and never trips S1751.
- **`noScheduledTaskRunsUnderTestProfile`** asserts "every holder has no tasks". If Spring
  registers no holder at all, the test passes. That is exactly the misconfiguration the test
  should catch.

**Fix.**

- `test-architect`: add a Kafka consumer template, or one paragraph, that prescribes
  `KafkaTestUtils.getSingleRecord` or Awaitility for "a record arrives". Add one rule line:
  *"`allSatisfy`, `allMatch` and `noneMatch` are preceded by `isNotEmpty()` unless empty is a
  valid pass."*
- `commons-logging-installer`: ship the two aspect tests as templates next to the aspects in
  `skills/new-feature/templates/commons/`, with a comment in each empty fixture method and a
  `@ParameterizedTest` over the four `LoggingOptions` combinations.
- Unused imports: see § 9. `spotless` has `<removeUnusedImports/>`, but nothing runs it on
  `verify`.

---

## § 8 · `record` used as a variable name (5 issues)

| Rule | Location in project |
|---|---|
| `java:S6213` | `infrastructure/messaging/outbox/KafkaOutboxEventSender.java:46` |
| `java:S6213` | `infrastructure/messaging/outbox/OutboxRelayIT.java:123,136` |
| `java:S6213` | `infrastructure/persistence/outbox/OutboxEventStoreIT.java:85,212` |

**Why.** The model names a `ProducerRecord`, a `ConsumerRecord` or an `OutboxEventRecord`
variable `record`. `record` is a restricted identifier since Java 16. No template does this:
`grep -F 'var record'` and `grep -F '<String, String> record'` find nothing in the meta-repo.
It is the model's default name for "a record of something", and nothing objects.

**Fix.** Add Checkstyle's `IllegalIdentifierName` to `checkstyle.xml.example`. Its default
format already rejects `record`, `yield`, `var`, `permits` and `sealed`. The build then fails on
the first occurrence, before Sonar ever sees it.

This catches only 1 of the 5. The Checkstyle plugin runs with
`<includeTestSourceDirectory>false</includeTestSourceDirectory>`, so the 4 occurrences in
`src/test` would still pass. See § 9.

---

## § 9 · Nothing in the build loop runs Sonar's rule set

**What happened.** The developer agent's gate is `./mvnw verify`: compile, Checkstyle,
unit tests, `*IT`, ArchUnit and JaCoCo. That gate was green. All 42 issues shipped through
three `/new-feature` runs and three `git-publish` commits, and nobody saw them. The first person
to see them was the user, by hand, after the fact.

**Why.** `sonarqube-setup` wires the scanner, but deliberately binds it to no phase
(`pom.xml` comment: *"`verify` never reaches a server on its own"*). That is correct for CI.
But nothing replaces it locally. Checkstyle covers structure and size; it does not cover
Sonar's Java rules. The Sonar gate on **new code** counts test code too, and 33 of 42 issues are
in `src/test`. Checkstyle never reads `src/test`: the plugin sets
`<includeTestSourceDirectory>false</includeTestSourceDirectory>`.

Spotless has `<removeUnusedImports/>`, but its `check` goal is not bound to `verify` either.
The format hook runs `spotless:apply` on each `Write`/`Edit`. `OutboxEventStoreIT` still
reached `main` with two unused imports, so that hook did not reach this file.

**Fix (pick one or combine).**

1. **Close the loop where it is cheap.** Bind `spotless:check` to `verify`. Add to Checkstyle the
   modules that overlap Sonar's findings in this report: `UnusedImports` and
   `IllegalIdentifierName`. Run a second, lighter Checkstyle execution over `src/test` with only
   those modules, because the main one excludes tests and the size and complexity rules do not
   fit test code. These run offline, on every build.
2. **Add a Sonar step to the developer agent when a server is declared.** When
   `sonar.host.url` answers and `SONAR_TOKEN` (or anonymous analysis) is available, the agent
   runs `./mvnw -B sonar:sonar -Dsonar.qualitygate.wait=true` after `verify`, and treats a failed
   gate like a failed test: fix, then rerun. When no server answers, it reports "Sonar: skipped —
   no server" in its summary, so the gap is visible.
3. **Or use SonarLint's rule set offline.** Run the SonarLint CLI or a `sonar-java` rules
   runner in `verify`. This needs no server, but it adds a heavier dependency.

Option 1 alone would have prevented 7 of the 42 issues (§ 7 S1128 ×2, § 8 ×5), with the test
execution included. Option 2 prevents all of them.

---

## § 10 · `sonarqube-setup`: anonymous analysis on a local container needs three settings, and the skill names none

**What happened.** The first run had no `SONAR_TOKEN`. The user chose anonymous analysis. It
took three round trips to get there, each one discovered by a failure:

1. `Force user authentication` is **on** by default in SonarQube 26. `api/authentication/validate`
   returned `{"valid":false}`.
2. With it off, the scanner failed:
   `You're not authorized to analyze this project or the project doesn't exist on SonarQube and you're not authorized to create it.`
   The group `Anyone` needs **Execute Analysis**.
3. The project did not exist yet. `Anyone` also needs **Create Projects**.

Each failure after step 1 came **after** a full `verify`: several minutes of tests, to learn one
missing checkbox.

**Why.** `skills/sonarqube-setup/SKILL.md:69-70` offers *Anonymous* as an option with one line:
*"the server allows analysis without a token. Rare outside a local container; say so."* The
local container is the exact case where the user picks it. But the summary at `:139-142` lists
only the token path.

**Fix.**

- When *Anonymous* is chosen for the local container, print the three settings in order:
  `Administration → Configuration → Security → Force user authentication` off, then
  `Administration → Security → Global Permissions → Anyone`: **Execute Analysis** and **Create
  Projects**. Say that this is local-only.
- Add a pre-flight line to the summary that checks access **before** the scan:
  `curl -s <host>/api/authentication/validate` must return `{"valid":true}`, or `SONAR_TOKEN`
  must be set. This moves the failure from the end of `verify` to the start.
- Print the run command as `./mvnw -B verify sonar:sonar` for the first run, and
  `./mvnw -B sonar:sonar` for reruns on an unchanged `target/`. A rerun after a settings change
  does not need the tests again.
- Ship the suppressions from § 2 and § 3 as `sonar.issue.ignore.multicriteria` in
  `pom-sonar.xml.example`, if the `// NOSONAR` route is rejected.

---

## Appendix A · All 42 open issues

`M/` = `src/main/java/com/example/democleanarchsinglemodule/`,
`T/` = `src/test/java/com/example/democleanarchsinglemodule/`. "New" = counted by the gate's
`new_violations`.

| # | Rule | Type | Severity | Location | New | § |
|---|---|---|---|---|---|---|
| 1 | `java:S6878` | smell | MAJOR | `M/application/shared/IdempotentExecution.java:33` | | 2 |
| 2 | `java:S2326` | smell | MAJOR | `M/application/shared/IdempotentOutcome.java:7` | | 2 |
| 3 | `java:S6218` | **bug** | MAJOR | `M/commons/logging/LoggingCommonsMethods.java:69` | | 4 |
| 4 | `java:S108` | smell | MAJOR | `M/infrastructure/messaging/outbox/DomainEventOutboxWriter.java:34` | ✓ | 5 |
| 5 | `java:S6213` | smell | MAJOR | `M/infrastructure/messaging/outbox/KafkaOutboxEventSender.java:46` | ✓ | 8 |
| 6 | `java:S112` | smell | MAJOR | `M/infrastructure/rest/idempotent/IdempotencyAspect.java:53` | | 3 |
| 7 | `java:S3516` | smell | **BLOCKER** | `M/infrastructure/rest/idempotent/IdempotencyKeyInterceptor.java:30` | | 3 |
| 8 | `java:S125` | smell | MAJOR | `M/infrastructure/scheduling/JobRunRecorder.java:34` | ✓ | 5 |
| 9 | `java:S5841` | **bug** | MINOR | `T/DemoCleanArchSingleModuleApplicationTests.java:59` | ✓ | 7 |
| 10 | `java:S5778` | smell | MAJOR | `T/application/usecase/CreateAccountUseCaseTest.java:62` | | 6 |
| 11 | `java:S5778` | smell | MAJOR | `T/application/usecase/CreateAccountUseCaseTest.java:89` | | 6 |
| 12 | `java:S5778` | smell | MAJOR | `T/application/usecase/RegisterCustomerUseCaseTest.java:73` | | 6 |
| 13 | `java:S1186` | smell | CRITICAL | `T/commons/logging/HttpMethodLogExecutionAspectTest.java:19` | | 7 |
| 14 | `java:S1186` | smell | CRITICAL | `T/commons/logging/HttpMethodLogExecutionAspectTest.java:22` | | 7 |
| 15 | `java:S1186` | smell | CRITICAL | `T/commons/logging/LogExecutionAspectTest.java:19` | | 7 |
| 16 | `java:S1186` | smell | CRITICAL | `T/commons/logging/LogExecutionAspectTest.java:22` | | 7 |
| 17 | `java:S5976` | smell | MAJOR | `T/commons/logging/LoggingCommonsMethodsTest.java:28` | | 7 |
| 18 | `java:S5778` | smell | MAJOR | `T/domain/event/CustomerRegisteredTest.java:49` | ✓ | 6 |
| 19 | `java:S5838` | smell | MINOR | `T/domain/model/CustomerIdTest.java:19` | | 7 |
| 20 | `java:S5778` | smell | MAJOR | `T/domain/model/CustomerTest.java:36` | | 6 |
| 21 | `java:S5778` | smell | MAJOR | `T/domain/model/CustomerTest.java:55` | | 6 |
| 22 | `java:S5778` | smell | MAJOR | `T/domain/model/MoneyTest.java:24` | | 6 |
| 23 | `java:S6213` | smell | MAJOR | `T/infrastructure/messaging/outbox/OutboxRelayIT.java:123` | ✓ | 8 |
| 24 | `java:S6213` | smell | MAJOR | `T/infrastructure/messaging/outbox/OutboxRelayIT.java:136` | ✓ | 8 |
| 25 | `java:S1751` | **bug** | MAJOR | `T/infrastructure/messaging/outbox/OutboxRelayIT.java:137` | ✓ | 7 |
| 26 | `java:S5778` | smell | MAJOR | `T/infrastructure/persistence/account/AccountRepositoryJpaAdapterIT.java:101` | | 6 |
| 27 | `java:S5778` | smell | MAJOR | `T/infrastructure/persistence/customer/CustomerRepositoryJpaAdapterIT.java:73` | | 6 |
| 28 | `java:S5778` | smell | MAJOR | `T/infrastructure/persistence/idempotency/IdempotencyKeyStoreTest.java:73` | | 6 |
| 29 | `java:S5778` | smell | MAJOR | `T/infrastructure/persistence/idempotency/IdempotencyKeyStoreTest.java:88` | | 6 |
| 30 | `java:S5778` | smell | MAJOR | `T/infrastructure/persistence/idempotency/IdempotencyKeyStoreTest.java:119` | | 6 |
| 31 | `java:S5778` | smell | MAJOR | `T/infrastructure/persistence/idempotency/IdempotencyKeyStoreTest.java:133` | | 6 |
| 32 | `java:S5778` | smell | MAJOR | `T/infrastructure/persistence/idempotency/IdempotencyKeyStoreTest.java:158` | | 6 |
| 33 | `java:S8924` | smell | MINOR | `T/infrastructure/persistence/idempotency/IdempotencyKeyStoreTest.java:169` | | 7 |
| 34 | `java:S5778` | smell | MAJOR | `T/infrastructure/persistence/idempotency/IdempotencyKeyStoreTest.java:180` | | 6 |
| 35 | `java:S1128` | smell | MINOR | `T/infrastructure/persistence/outbox/OutboxEventStoreIT.java:9` | ✓ | 7 |
| 36 | `java:S1128` | smell | MINOR | `T/infrastructure/persistence/outbox/OutboxEventStoreIT.java:13` | ✓ | 7 |
| 37 | `java:S6213` | smell | MAJOR | `T/infrastructure/persistence/outbox/OutboxEventStoreIT.java:85` | ✓ | 8 |
| 38 | `java:S6213` | smell | MAJOR | `T/infrastructure/persistence/outbox/OutboxEventStoreIT.java:212` | ✓ | 8 |
| 39 | `java:S5853` | smell | MINOR | `T/infrastructure/rest/dto/RegisterCustomerRequestTest.java:17` | ✓ | 7 |
| 40 | `java:S5853` | smell | MINOR | `T/infrastructure/rest/dto/RegisterCustomerResponseTest.java:18` | ✓ | 7 |
| 41 | `java:S5778` | smell | MAJOR | `T/infrastructure/rest/idempotent/IdempotencyKeyInterceptorTest.java:47` | | 6 |
| 42 | `java:S5778` | smell | MAJOR | `T/infrastructure/rest/idempotent/IdempotencyKeyInterceptorTest.java:59` | | 6 |

## Appendix B · Duplications

| Block | File A | File B | Lines | § |
|---|---|---|---|---|
| 1 | `M/infrastructure/persistence/account/AccountEntity.java:58` | `M/infrastructure/persistence/customer/CustomerEntity.java:59` | 14 | 1 |

## Appendix C · Meta-repo change list

| Meta-repo path | Change | § |
|---|---|---|
| `skills/persistence-architect/templates/JpaEntity.java.example` (+ `OutboxEventStore`, `IdempotencyKeyStore`) | Extract `Persistable` boilerplate into a `@MappedSuperclass` | 1 |
| `agents/java-spring-boot-developer.md:340`, ArchUnit guard from decision 0039 | Accept the base class as satisfying the `Persistable` rule | 1 |
| `skills/persistence-architect/templates/IdempotentExecution.java.example:34,66` | Record pattern; suppress S2326 with a reason | 2 |
| `skills/rest-api-architect/templates/IdempotencyKeyInterceptor.java.example:61` | Restructure `preHandle` or suppress S3516 with a reason | 3 |
| `skills/rest-api-architect/templates/IdempotencyAspect.java.example:239` | Suppress S112 next to the existing Checkstyle suppression | 3 |
| `skills/new-feature/templates/commons/LoggingCommonsMethods.java.example:67` | `Call.args` as `List<Object>`, or override `toString` | 4 |
| `rules/` (Java style) or `agents/java-spring-boot-developer.md` | "No `default` on a sealed pattern switch; `MissingSwitchDefault` does not apply" | 5 |
| `skills/jobs-architect/templates/ScheduledJob.java.example:88`, `IdempotentExecution.java.example:82` | Reword comments that contain code fragments | 5 |
| `skills/test-architect/templates/UseCaseTest.java.example:61`, `PersistenceIT.java.example:89` | One call per `assertThatThrownBy` lambda; add a rule line | 6 |
| `skills/test-architect` | Kafka "record arrives" template; `isNotEmpty()` before `allSatisfy` | 7 |
| `skills/new-feature/templates/commons/` | Ship the aspect tests as templates | 7 |
| `skills/project-bootstrap/templates/checkstyle.xml.example` + pom template | Add `IllegalIdentifierName`, `UnusedImports`; a second execution over `src/test` | 8, 9 |
| `skills/project-bootstrap/templates/pom.parent.xml.example` | Bind `spotless:check` to `verify` | 9 |
| `agents/java-spring-boot-developer.md` | Run the Sonar gate when a server is declared, or report "skipped" | 9 |
| `skills/sonarqube-setup/SKILL.md:69-70,139-142` | Anonymous-access steps, pre-flight access check, rerun command | 10 |
