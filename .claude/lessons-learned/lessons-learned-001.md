# Lessons learned — UC-001, first feature implemented end to end

Document intended for the **meta-repo** (the project generator, blueprint
`clean-architecture-single-module`), not for this project.

Record of the points where the executor had to **infer**, **fix**, or **contradict** the
generated material — rules, skill templates, `pom.xml` and configuration — to get
`UC-001-criacao-pessoas` implemented with a green `./mvnw clean verify`.

Each entry follows the same shape: what failed, the evidence, the cost, and what the
meta-repo should change in its generation so the next project doesn't repeat the error.

Execution context: Spring Boot 4.1.1, Spring Framework 7.0.9, Java 21, Testcontainers
2.0.5, Maven. Every claim below was verified against the real build, not inferred from
reading.

---

## Summary by severity

| # | Gap | Category | Severity |
|---|---|---|---|
| 1 | `ApiExceptionHandler` template breaks the Checkstyle the blueprint itself installs | Internal contradiction | **Blocking** |
| 2 | `RepositoryAdapter` template teaches a pattern that doesn't catch a `UNIQUE` violation | Silent defect | **Blocking** |
| 3 | `spring-boot-starter-validation` missing from the generated `pom.xml` | Missing dependency | **Blocking** |
| 4 | Test templates use Spring Boot 3 packages (`@DataJpaTest`, `@WebMvcTest`) | Outdated template | **Blocking** |
| 5 | `HttpStatus.UNPROCESSABLE_ENTITY` deprecated in Spring 7 | Outdated template | High |
| 6 | UUID v7 required by rule, with no support in JDK 21 nor a dependency in `pom.xml` | Rule without means of execution | High |
| 7 | `ApiExceptionHandler` template requires `Tracer` without the bridge in `pom.xml` | Missing dependency | High |
| 8 | `api-rest.md` never auto-loads in this blueprint (wrong `paths`) | Dead enforcement | High |
| 9 | `PersistenceIT` template uses the Testcontainers 1.x package; project has 2.x | Outdated template | High |
| 10 | `naming.md` and `testing.md` contradict each other on test method names | Contradiction between rules | Medium |
| 11 | `lombok.md` requires `@FieldDefaults` and no template shows the correct import | Rule without exemplar | Medium |
| 12 | `Aggregate` template violates the blueprint's Checkstyle `ParameterNumber` | Internal contradiction | Medium |
| 13 | Templates aren't pre-formatted in `palantirJavaFormat` | Build friction | Medium |
| 14 | `testing.md` contradicts the `@EnabledIf` the bootstrap itself generates | Internal contradiction | Medium |
| 15 | `/new-feature`'s guardrail checks paths this blueprint doesn't have | Misaligned skill | Medium |
| 16 | `00-caso-de-uso.md` ↔ `30-rest.md` divergence resolved only in prose | Pipeline | Medium |
| 17 | Coverage gate and ArchUnit have no step that turns them on | Incomplete pipeline | Medium |
| 18 | Background executor agent dies with the machine's sleep | Operational | High |

---

## 1. The `ApiExceptionHandler` template breaks the Checkstyle the blueprint installs

**Evidence.** `.claude/skills/rest-api-architect/templates/ApiExceptionHandler.java.example:43`
declares:

```java
private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
```

`config/checkstyle/checkstyle.xml` wires the `ConstantName` module, which requires
`^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$`. The build stopped at the `validate` phase:

```
[ERROR] ApiExceptionHandler.java:[30,33] (naming) ConstantName: Name 'log' must match pattern '^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$'.
[ERROR] Failed to execute goal maven-checkstyle-plugin:3.6.0:check (checkstyle-validate)
```

The template says of itself "Compilable as is — not pseudo-code." It is compilable, but
**doesn't pass the build of the project it ships with**.

**Cost.** One lost build iteration. Low in isolation, guaranteed in every generated
project — it's the SLF4J idiom used everywhere, and the template teaches it wrong.

**Meta-repo fix.** Pick one of the two and apply it everywhere:

- either the template switches to `private static final Logger LOG = ...`;
- or `checkstyle.xml` adds the conventional exemption to `ConstantName`:
  `<property name="format" value="^(log|[A-Z][A-Z0-9]*(_[A-Z0-9]+)*)$"/>`.

I recommend the second: lowercase `log` is the ecosystem's overwhelming convention and
will reappear in every piece of code anyone writes by hand.

---

## 2. The `RepositoryAdapter` template teaches a pattern that doesn't catch a `UNIQUE` violation

**This is the most serious gap in the batch, because it fails silently.**

**Evidence.** `.claude/skills/persistence-architect/templates/RepositoryAdapter.java.example:42-51`
shows `save` translating an infrastructure failure inside a `try/catch`:

```java
@Override
public User save(User user) {
    try {
        return mapper.toDomain(repository.save(mapper.toEntity(user)));
    } catch (OptimisticLockingFailureException cause) {
        throw new ConflictException("USER_CONCURRENT_UPDATE", ..., cause);
    }
}
```

The pattern works for optimistic locking, but **doesn't work for a constraint
violation**, which is exactly what this UC's `20-persistencia.md` requires of the
adapter:

> the race between two `INSERT`s with the same email is resolved by the
> `UNIQUE (email)` constraint: the second `INSERT` fails with a constraint violation, and
> the adapter translates it to `ConflictException` — never lets the driver/JPA exception
> cross the domain boundary.

`repository.save(...)` only registers the entity in the persistence context. The
`INSERT` reaches the database on flush, which by default happens **at transaction
commit** — opened in `CreatePersonService`, a layer above. By then the adapter's
`try/catch` has already gone out of scope. The `DataIntegrityViolationException` bubbles
up wrapped by the transaction manager, never becomes `ConflictException`, and the client
gets a 500 instead of a 409.

I had to use `saveAndFlush` to force the `INSERT` inside the `try`:

```java
PersonEntity saved = jpaRepository.saveAndFlush(toEntity(person));
```

The `PersonRepositoryAdapterIT.rejeitaEmailDuplicado` test confirms the translation
against a real Postgres, and the Hibernate log shows the constraint firing inside the
test:

```
WARN org.hibernate.orm.jdbc.error : ERROR: duplicate key value violates unique constraint "uq_pessoas_email"
```

**Cost.** High and treacherous. An executor following the template to the letter writes
an adapter that compiles, passes a unit test with a double, and only fails in
production — with the wrong status. If the integration test hadn't asserted the
`errorCode`, it would have passed too.

**Meta-repo fix.**

1. The `RepositoryAdapter.java.example` template now shows **both** translation cases,
   with a comment explaining the difference in flush timing:

```java
try {
    // saveAndFlush, not save: without the explicit flush the INSERT only reaches the
    // database at commit, outside this try/catch, and the constraint violation escapes
    // translation.
    return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(entity)));
} catch (DataIntegrityViolationException cause) {
    throw new ConflictException("<KEY>_ALREADY_EXISTS", "...", cause);
} catch (OptimisticLockingFailureException cause) {
    throw new ConflictException("<AGGREGATE>_CONCURRENT_UPDATE", "...", cause);
}
```

2. `persistence.md` § Boundary gains an explicit line: *"translating a constraint
   violation requires a flush inside the adapter (`saveAndFlush`); with `save` the
   exception is born at commit, outside the `try`."*

3. `test-architect` now requires that every adapter integration test with a unique
   business key assert the `errorCode`, not just the type — which was already written in
   `testing.md`, but wasn't tied to this concrete case.

---

## 3. `spring-boot-starter-validation` missing from the generated `pom.xml`

**Evidence.** `./mvnw dependency:tree` returned not a single line with `validation` —
neither `jakarta.validation-api`, nor `hibernate-validator`, in any scope. The generated
`pom.xml` has `webmvc`, `data-jpa`, `flyway`, `actuator`, `springdoc`, and no validation.

The `30-rest.md` partial § 4 anticipated the problem but left it open:

> `spring-boot-starter-validation` — To be confirmed in `pom.xml` by the executor;
> managed by the BOM if missing.

Without it, `@NotBlank`/`@Email`/`@Size` on the DTO don't compile, and the controller's
`@Valid` has no processor — two of `30-rest.md` § 5's five contract cases become
impossible.

**Cost.** Low in time, but it's an infrastructure decision made by the executor midway
through implementing a use case. Editing `pom.xml` shouldn't be the job of whoever's
implementing a use case.

**Meta-repo fix.** The blueprint that activates the `rest` feature adds
`spring-boot-starter-validation` to `pom.xml` at generation time. Bean validation on the
DTO is required by `api-rest.md` § Errors — the dependency isn't optional, it's a direct
consequence of a rule the same blueprint installs.

---

## 4. Test templates use Spring Boot 3 packages

**Evidence.** Boot 4.1 split the monolithic `spring-boot-test-autoconfigure` into
per-technology modules. The annotations changed package:

| Annotation | Template says | Boot 4.1.1 requires |
|---|---|---|
| `@DataJpaTest` | `org.springframework.boot.test.autoconfigure.orm.jpa` | `org.springframework.boot.data.jpa.test.autoconfigure` |
| `@WebMvcTest` | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |

Compilation error:

```
[ERROR] PersonRepositoryAdapterIT.java:[14,59] package org.springframework.boot.test.autoconfigure.orm.jpa does not exist
[ERROR] PersonControllerTest.java:[17,63] package org.springframework.boot.test.autoconfigure.web.servlet does not exist
```

Confirmed by inspecting the JARs under `~/.m2`: `spring-boot-data-jpa-test-4.1.1.jar`
contains `org/springframework/boot/data/jpa/test/autoconfigure/DataJpaTest.class`.

**Cost.** Two iterations, including digging through the JARs to find the new package —
not guessable, and the migration docs weren't at hand.

**Meta-repo fix.** Update `PersistenceIT.java.example` and `ControllerTest.java.example`.
More broadly: the templates need to be **pinned to a Boot version** and verified against
it. Concrete suggestion: a test in the meta-repo that compiles each `*.java.example`
against the blueprint's `pom.xml`. Templates that don't compile are the root cause of
half this list.

---

## 5. `HttpStatus.UNPROCESSABLE_ENTITY` is deprecated in Spring Framework 7

**Evidence.** `ApiExceptionHandler.java.example:64` uses `UNPROCESSABLE_ENTITY`. The
compiler warned:

```
The field HttpStatus.UNPROCESSABLE_ENTITY is deprecated since version 7.0
```

`javap` on `spring-web-7.0.9.jar` shows both fields: `UNPROCESSABLE_CONTENT` (the new
one) and `UNPROCESSABLE_ENTITY` (kept, deprecated). RFC 9110 renamed the status.

**Cost.** Low — a warning, not an error. But it's debt that propagates to every
generated project, and `api-rest.md` § Error body maps 422 to
`BusinessRuleViolationException`, so every generated `ApiExceptionHandler` is born with
the deprecated field.

**Meta-repo fix.** Switch to `UNPROCESSABLE_CONTENT` in the template. `api-rest.md` can
keep saying "422" — the number didn't change, only the constant's name.

---

## 6. UUID v7 required by rule, with no support in JDK 21 nor a dependency in `pom.xml`

**Evidence.** `persistence.md` § Identity and keys:

> UUID as primary key: time-ordered version (v7). UUIDv4 fragments the index

`10-dominio.md` and `UC-001-spec.md` inherit the requirement: *"`id` — `UUID` v7,
generated by `CreatePersonService`."* But:

- JDK 21's `java.util.UUID` generates **v4** (`UUID.randomUUID()`). There's no v7 API.
- `pom.xml` doesn't have `com.fasterxml.uuid:java-uuid-generator` or an equivalent.

In other words: the rule requires something the generated project has no way to do. I
had to hand-write `application/usecase/UuidV7Generator.java` — bit manipulation per
RFC 9562: 48 bits of timestamp, version nibble, variant bits.

Side effect: Checkstyle's `MagicNumber` forced naming **ten** constants
(`VERSION_BYTE_INDEX`, `VARIANT_NIBBLE_MASK`, `BITS_PER_BYTE`, …) for the generator to
pass `validate`. A pure infrastructure class, written by someone who was supposed to be
implementing a use case.

**Cost.** High. It's the only non-trivial piece of code in this UC that comes from no
spec at all, and it's a place where a bit error breaks no test — the `id` is still a
valid UUID, it just stops being sortable.

**Meta-repo fix.** One of the two, never leave it as is:

1. **Preferred** — the blueprint adds `com.fasterxml.uuid:java-uuid-generator` to
   `pom.xml` and `persistence.md` cites `Generators.timeBasedEpochGenerator()` as the
   form. A small, mature dependency, and the problem disappears.
2. Alternatively, bootstrap **generates** `UuidV7Generator` as an exemplar (alongside the
   domain exceptions), with its own test asserting the temporal ordering and the version
   nibble. A rule that requires v7 has to bring the means to produce it.

Note: Java 25 has UUID v7 in the JDK. When the blueprint moves to that LTS, this entry
closes on its own — but today the target is 21.

---

## 7. The `ApiExceptionHandler` template requires `Tracer` without the bridge in `pom.xml`

**Evidence.** The template injects `io.micrometer.tracing.Tracer` via constructor to fill
in the `traceId` for 5xx responses, and the comment itself warns:

> Requires the tracing bridge on the classpath; without it there's no `Tracer` bean and
> the context won't start.

`dependency:tree` confirms there's no `micrometer-tracing-bridge-*` in the generated
`pom.xml`. `30-rest.md` § 4 recorded the gap and passed it on to the executor:

> the 500's `traceId` depends on the project already having
> `micrometer-tracing-bridge-*` configured — outside this UC's scope, executor's
> verification.

I chose to generate the `traceId` with `UUID.randomUUID()`, recording the same value in
the log and in the body. Complies with the rule (`api-rest.md` § Errors — 500 family:
*"Every 5xx carries a correlation identifier in the body (`traceId`), the same one in the
log"*) without requiring infrastructure the project doesn't have. Documented the choice
in a Javadoc on the class.

**Cost.** Medium, and it's an architectural decision made by default. A `traceId` that
doesn't correlate with a distributed tracing system is considerably less useful than one
that does — but better than not starting at all.

**Meta-repo fix.** Decide this at generation time, not at implementation time:

- if the blueprint has `observability`, add the bridge to `pom.xml` and the template
  stays as is;
- if not, the template gains a variant without `Tracer`, with `UUID` as the `traceId`
  source and a comment saying it's a deliberate degraded mode.

The `observability.md` rule is listed in `00-index.md` as **planned, file not yet
written**. This is the decision it should own.

---

## 8. `api-rest.md` never auto-loads in this blueprint — the `paths` points to a layout that doesn't exist

**Evidence.** `.claude/rules/api-rest.md`'s frontmatter:

```yaml
paths:
  - "**/adapter/in/rest/**"
```

This blueprint (`clean-architecture-single-module`) puts the REST adapter in
`src/main/java/com/example/demoapp/infrastructure/rest/`. The glob never matches. The
rule that decides status, error-body shape, pagination and idempotency **never enters
context on its own** in this project.

Compare with `persistence.md`, which was fixed for both layouts:

```yaml
paths:
  - "**/adapter/out/persistence/**"
  - "**/infrastructure/persistence/**"
```

The fix was applied to `persistence.md` and forgotten in `api-rest.md`. `00-index.md`
warns that this matters:

> Declare `paths` whenever the rule has an identifiable file territory.

**Cost.** High and invisible. It didn't hurt in this UC because `/new-feature` went
through `rest-api-architect`, which cites the rule explicitly. But anyone editing a
controller by hand, outside the pipeline, doesn't receive `api-rest.md` — and it's the
densest rule in the repository.

**Meta-repo fix.** Two actions:

1. Immediate: add `"**/infrastructure/rest/**"` to `api-rest.md`'s `paths`.
2. Structural: rules' `paths` should be **generated from the active blueprint's
   `packages.map`**, as already happens with `architecture-ddd.md` — `00-index.md` says
   that one was "fixed at this project's generation, from the blueprint." The same
   treatment needs to apply to every rule with territory, or the divergence repeats with
   every new blueprint.

Worth a test in the meta-repo: for each rule with `paths`, assert that at least one file
in the generated project matches the glob. A glob that matches nothing is dead
enforcement.

---

## 9. `PersistenceIT` template uses the Testcontainers 1.x package

**Evidence.** `PersistenceIT.java.example:31` imports
`org.testcontainers.containers.PostgreSQLContainer`. `pom.xml` brings Testcontainers
**2.0.5**, where the class lives at `org.testcontainers.postgresql.PostgreSQLContainer` —
which is, in fact, what `TestcontainersConfiguration.java`, **generated by bootstrap
itself**, already uses:

```java
import org.testcontainers.postgresql.PostgreSQLContainer;
```

The template contradicts the code the same bootstrap writes, in the same project.

Additional detail: the template declares `PostgreSQLContainer<?>` with a generic; in
2.x the class is no longer generic.

**Cost.** Medium. Confusing to diagnose because there are two contradictory examples in
the same repository, one of them working.

**Meta-repo fix.** Update the template for Testcontainers 2.x and align it with the
generated `TestcontainersConfiguration`. Falls under the same remedy as gap 4: compile
the templates against the blueprint's `pom.xml` in CI.

---

## 10. `naming.md` and `testing.md` contradict each other on test method names

**Evidence.** `naming.md:47`:

> Method: `should<Outcome>_when<Condition>` — `shouldRejectOrder_whenStockIsZero()`.

`testing.md:68-69`:

> The method name describes the expected behavior, not the method called:
> `rejeitaEmailSemArroba`, not `testValidate`.

Two incompatible conventions — English with a `should` prefix and `_` separator, against
descriptive Portuguese. Every `test-architect` template and every UC-001 partial follows
the second. I followed the partials, which were the direct input.

`00-index.md` is explicit about why this is a bug:

> Each theme has **one** owner file. […] A rule written in two places diverges — treat it
> as a bug.

Test names are written in both places, and they diverged.

**Cost.** Low in this UC, guaranteed in future review — two people with the same rule
land on different names.

**Meta-repo fix.** `testing.md` is the owner of the "tests" theme. `naming.md` removes
line 47 and cites `@.claude/rules/testing.md` § Names and shape instead. The rule about
the **class** name (`<TestedClass>Test` / `<TestedClass>IT`) is also duplicated in both
files — same fix.

---

## 11. `lombok.md` requires `@FieldDefaults` and no template shows the correct import

**Evidence.** `lombok.md` § `@FieldDefaults` — mandatory when every field is private. No
persistence template uses it: `JpaEntity.java.example` declares `private UUID id;`
field by field, exactly what the rule says not to do.

`@FieldDefaults` lives in `lombok.experimental.FieldDefaults`, not in `lombok`. I wrote
the wrong import on the first try and the compiler caught it:

```
The import lombok.FieldDefaults cannot be resolved
```

The rule shows the annotation in use but never a complete file with imports — and it's
one of the few Lombok annotations not in the root package.

**Cost.** Low, one iteration. But it's exactly the kind of detail exemplars exist to
resolve.

**Meta-repo fix.** `JpaEntity.java.example` now uses `@FieldDefaults` with the full
import, complying with the rule that accompanies it. The alternative would be for
`lombok.md` to explicitly exempt JPA entities — but today it doesn't say that, and the
result is a template that violates the neighboring rule.

---

## 12. The `Aggregate` template violates the blueprint's Checkstyle `ParameterNumber`

**Evidence.** `Aggregate.java.example:47`:

```java
public static User register(Email email, String name, DepartmentId departmentId, Clock clock)
```

Four parameters. `checkstyle.xml` wires `ParameterNumber` with `max=3` and
`tokens=METHOD_DEF` — and `register` is a `METHOD_DEF`. Checkstyle's exemption covers
only injection constructors, not static factories.

The template says "Compilable as is." It compiles; it doesn't pass the `validate` of
the project it ships with.

**Cost.** None in this UC — `Person` has three fields and the factory wasn't needed with
four. Guaranteed cost on the first aggregate with four fields, which is the common case.

**Meta-repo fix.** Align the two. Either the template shows the factory taking a
parameter object / the command itself, or `checkstyle.xml` exempts static aggregate
factories. The first is more faithful to `code-quality.md`; the second is less
intrusive. Decide it in the meta-repo, not per project.

---

## 13. Templates aren't pre-formatted with `palantirJavaFormat`

**Evidence.** Code written following the templates' shape failed `spotless:check` in six
files — line breaks in `assertThatThrownBy`, indentation of `@Schema` annotations on
`record` parameters, text blocks. `./mvnw spotless:apply` fixed everything without
changing semantics.

CLAUDE.md lists Spotless as enforcement "on every `.java` Write/Edit" via hook, but the
hook didn't run in this session — the files were written and only the manual
`spotless:check` caught them.

**Cost.** Low, one `spotless:apply` pass. But it pollutes the diff and makes the
executor unsure whether it changed anything extra.

**Meta-repo fix.** Run `palantirJavaFormat` over every `*.java.example` in the meta-repo,
in CI. A pre-formatted template produces code that already passes `check` on the first
try, and the executor stops having to distinguish "my mistake" from "the template's
style."

Also worth confirming why the formatting hook didn't fire in this session — if the
enforcement CLAUDE.md announces isn't active, that's a gap of its own.

---

## 14. `testing.md` contradicts the `@EnabledIf` the bootstrap itself generates

**Evidence.** `testing.md` § Database in integration tests:

> Without Docker available, the integration test **doesn't run and fails**. It doesn't
> silently degrade to another database.

The `DemoAppApplicationTests.java` **generated by bootstrap** does exactly the opposite,
and justifies it in Javadoc:

```java
@EnabledIf("dockerDisponivel")
class DemoAppApplicationTests {
    static boolean dockerDisponivel() {
        return DockerClientFactory.instance().isDockerAvailable();
    }
}
```

> Without the guard, the test turns red on any machine without Docker — a false
> negative that says nothing about the code.

I followed the generated code (applied the same guard to `PersonRepositoryAdapterIT`),
for consistency with what was already in the project. But it's a choice between a rule
and an exemplar that contradict each other, made by the executor.

**Cost.** Medium. Both positions are defensible; the problem is the project asserting
both.

**Meta-repo fix.** Decide and write it in one place. Suggestion: keep `@EnabledIf` for
local work and require Docker in CI, with `testing.md` saying so explicitly —
*"an `@EnabledIf` guard is admitted locally; in CI, `verify` runs with Docker and the
absence of a container is a failure."* Without that sentence, the rule and the generated
code keep disagreeing.

Related: `naming.md:45` refers to a `CONTEXT.md`, phase 2, that doesn't exist in this
project. A pending reference from another generation context.

---

## 15. `/new-feature`'s guardrail checks paths this blueprint doesn't have

**Evidence.** `.claude/skills/new-feature/SKILL.md`, § Entry guardrail:

> **Project** — checks `pom.xml` (exists + parseable), `src/` (domain/, adapter/),
> `.claude/forbidden-imports.txt`

And, in § Contract: *"`pom.xml`, `src/domain/`, `.claude/forbidden-imports.txt` —
validates the generated project."*

This blueprint doesn't have `src/domain/` nor `adapter/`. It has
`src/main/java/com/example/demoapp/{domain,application,infrastructure}`. A literal check
would fail, or pass by accident.

**Cost.** Low in this run (I adapted the validation to the real structure), but it's a
guardrail that guards nothing.

**Meta-repo fix.** The skill should derive the paths from the active blueprint's
`packages.map`, or check in an engine-agnostic way (does `pom.xml` exist; does at least
one `domain` package exist; does `.claude/forbidden-imports.txt` exist). Applies to all
five pipeline skills, not just this one.

---

## 16. Divergence between pipeline partials resolved only in prose

**Evidence.** `00-caso-de-uso.md` fixed the trigger as `POST /v1/people` and mapped
validation errors to **422**. `30-rest.md` corrected both — `/api/v1/people` (required by
`api-rest.md` § Resources and URIs) and **400** (because 422 is reserved for
`BusinessRuleViolationException`) — and recorded the correction in prose:

> **Divergence from `00-caso-de-uso.md`:** […] Path corrected to `/api/v1/people` here;
> `00-caso-de-uso.md` isn't rewritten by this skill.

The consolidated `UC-001-spec.md` carries both versions: block 1 inherits the canonical
names from the mother spec, block 4 brings the corrected contract, and a note explains
which one wins.

It worked — the executor can read the note. But it depends on the executor **reading the
note**, and a consolidated spec that contradicts itself internally is fragile by
construction.

**Cost.** Low here, high once there are three or four divergences and the note sits two
hundred lines from where it matters.

**Meta-repo fix.** At consolidation, `/new-feature` should **resolve** the divergences,
not stack them: `UC-001-spec.md` presents the winning contract and moves the discarded
versions to a "Divergences resolved" section at the end. The executor reads one truth;
the audit trail stays available.

Useful complement: `use-case-design` shouldn't fix the HTTP path or status. Those are
`rest-api-architect`'s decision — the mother spec fixing transport is what generates the
divergence in the first place.

---

## 17. Coverage gate and ArchUnit remain uninstalled, with no step that turns them on

**Evidence.** CLAUDE.md § Enforcement installed:

> Architecture tests (ArchUnit) and the 80%/70% coverage gate **aren't installed yet**:
> they come with the `test-architect` skill, once business classes exist.

`40-tests.md` repeats the condition, and the comment on the JaCoCo `pom.xml` explains it
well: a `check` over an empty set proves nothing.

**Now business classes exist.** The condition is met. But `/new-feature`'s procedure has
no stage that triggers `test-architect`'s setup mode after the first feature — stage 5
invokes `test-architect` only in design mode, and consolidation doesn't mention the
matter.

This is the gap with the largest medium-term consequence: without ArchUnit, the
boundaries CLAUDE.md declares **have no enforcement outside the editor at all**.
`architecture-ddd.md` § How to verify says so itself:

> In the second case the architecture test is the **only** enforcement outside the
> editor. Without it, dependency direction is verified by nothing.

`.claude/forbidden-imports.txt` covers only Write/Edit within Claude Code. Any commit
made another way passes unverified.

**Cost.** High and growing. Every new feature increases the unverified surface.

**Meta-repo fix.** `/new-feature` gains a post-consolidation stage: if the project has no
ArchUnit and now has business classes, it proposes running `test-architect` in setup
mode. The condition is verifiable (`grep` for `ArchitectureTest`, a count of classes
outside `package-info`), so it needs no human judgment to be detected — only to be
authorized.

---

## 18. The background executor agent doesn't survive the machine's sleep

**Evidence.** `java-spring-boot-developer` was launched in the background three times.
All three failed with `Agent stalled: no progress for 600s (stream watchdog did not
recover)`, without writing **a single file**. All the transcripts end with
`[Request interrupted by user]`, with gaps of 15 to 40 minutes between the last
successful call and the interruption — coinciding with periods of machine inactivity.

Checked what wasn't the cause: `./mvnw -v` responds in seconds, Docker is running, no
file had been written across the three attempts. In all three, the agent died still in
the **reading** phase — spec, `pom.xml`, `dependency:tree` — before any `Write`.

I then implemented it directly in the main thread, which survives interruptions because
it's resumable.

**Cost.** The highest in the batch in real time: three lost runs, roughly two hours of
wall-clock time, zero progress on disk.

**Meta-repo fix.** Two fronts, and the second is the one that matters for the blueprint:

1. **Operational** — long background executor work needs an explicit warning that the
   machine's sleep kills it, or `caffeinate` during the run.
2. **Agent design** — `java-spring-boot-developer` front-loads reading: walks the whole
   spec, the `pom.xml`, the `dependency:tree` and the templates before writing a single
   line. An interruption during that phase loses everything. The agent's prompt should
   mandate **writing early and in checklist order**: domain exceptions first, then VOs,
   then the aggregate — each step persisted before reading the next. An interrupted run
   leaves reusable progress instead of nothing, and resuming can detect what already
   exists.

This is also what makes the `UC-NNN-spec.md`'s "Implementation order" checklist
valuable: it already defines natural checkpoints. The agent just doesn't use them.

---

## Underlying pattern

Twelve of the eighteen gaps share the same cause: **the templates and rules aren't
verified against the project the blueprint generates.** They compile in the head of
whoever wrote them, against a Spring Boot version that's already passed, in a package
layout this blueprint doesn't use.

A single change in the meta-repo fixes most of it:

> **A reference project generated in CI, where every `*.java.example` is compiled, every
> rule's `paths` is checked against the real file tree, and the full `verify` runs.**

That would catch, with no human intervention, gaps 1, 4, 5, 8, 9, 11, 12 and 13 — and
would have caught 2 too, if the reference project included an integration test with a
unique key, which is precisely what `40-tests.md` already says to write.

The rest — 3, 6, 7, 14, 16, 17, 18 — are decisions nobody made that fell on the executor
midway through implementation. All of them belong at generation time, and none of them
should be made by whoever is implementing a use case.

---

## Appendix: what went well

Worth recording, because it's also design information.

- **The spec chain worked.** The five partials were enough to implement without asking
  anything about the domain, and no business decision needed to be invented.
- **The 18-step checklist in `UC-001-spec.md` mapped 1:1 to the files.** Good shape —
  keep it.
- **Requiring the assertion of `errorCode`, not just the type,** caught itself: it's what
  makes gap 2's test able to detect the missing translation.
- **`forbidden-imports.txt` was correct** for this layout, and the `application` → no
  `org.springframework.web` separation let `@Service` and `@Transactional` through
  exactly as `architecture-ddd.md` intends.
- **The Surefire/Failsafe split (`*Test` vs `*IT`) worked as designed** — both `IT`s ran
  in `verify` and stayed out of `test`, with a real Postgres container and migrations in
  production order.
