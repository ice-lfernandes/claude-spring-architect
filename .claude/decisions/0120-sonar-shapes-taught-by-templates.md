# 0120 · Templates stop teaching the shapes Sonar flags; repeated literals become a Checkstyle check

- **Date:** 2026-10-04
- **Scenario:** Issue #86, triaged at `0185b62`: `.claude/` templates teach the shapes Sonar
  flags (repeated literals in `ApiExceptionHandler`, a literal path for `Location` in
  `Controller`, `Persistable` instead of `AssignedIdEntity`), and nothing in the build blocks
  them — no Checkstyle check for repeated strings, no parameterized adapter test template, no
  S110 handling, and build-side fixes don't reach existing projects.
- **Decision:** Option 1 without the `domain-modeling` mirror question — fixes at each
  template's owner, Checkstyle `MultipleStringLiterals` in `checkstyle.xml.example`, the
  `migrations` entry `multiple-string-literals`, a parameterized interceptor test exemplar, and
  a `component_tree` Browse probe in `sonar-lessons`
- **State:** approved by Lucas Fernandes, on 2026-10-04

## Reproduced on disk

The `issue-verifier` table from `/triage-issue 86`, confirmed rows only, checked at
`0185b62` (= `v0.14.10`; no commits since).

| # | Claim | Evidence |
|---|---|---|
| 1 | `ApiExceptionHandler` repeats `"Invalid request"` and `"violations"` twice each | `rest-api-architect/templates/ApiExceptionHandler.java.example:123,125,142,144` |
| 2 | No `MethodArgumentTypeMismatchException` handler | 0 matches. With the catch-all `@ExceptionHandler(Exception.class)` at `:171`, a malformed path variable answers 500, not 400 — found while designing |
| 3 | `Location` built as `URI.create("/api/v1/users/" + body.id())`, repeating the class-level `@RequestMapping` | `rest-api-architect/templates/Controller.java.example:57,84` |
| 4 | `api-rest.md` requires the header, not how it is built | `rules/api-rest.md:60` |
| 5 | `ControllerTest` asserts only that `Location` exists | `test-architect/templates/ControllerTest.java.example:97` |
| 7 | Migration `javadoc-only-comments` exists; `/arch-adopt` does not touch `checkstyle.xml` | `schemas/extensions.json` › `migrations` |
| 10 | `testing.md` requires `@ParameterizedTest` for cases differing only in input | `rules/testing.md:99-100` |
| 12 | No adapter or interceptor parameterized test template; `test-architect`'s design step never asks | 0 matches for "parameteri" in `test-architect/SKILL.md`, `test-spec.md.example`, `java-spring-boot-developer.md` |
| 13 | `error-handling.md` allows `DomainException → family → named subclass` (depth 6) | `rules/error-handling.md:29-30,37-40` |
| 14 | `sonarqube-setup` forbids local ignore entries; no S110 entry ships | `sonarqube-setup/SKILL.md:107`; `pom-sonar.xml.example:32-36` |
| 15 | `persistence-architect` ships `AssignedIdEntity` and tells the spec to name it | `persistence-architect/templates/JpaEntity.java.example:166`; `SKILL.md:168-169` |
| 16 | The executor still says the entity "implements `Persistable`" | `agents/java-spring-boot-developer.md:404` |
| 18 | `domain-modeling` never asks whether a new aggregate mirrors an existing one | `domain-modeling/SKILL.md:83-86,162,187` |
| 19 | `sonar-lessons` has no `component_tree` probe; `sonar-web-api.md` documents no `duplications_data` fallback | 0 matches each |
| 20 | `MultipleStringLiterals` is absent | 0 matches in `.claude/` |
| 21 | v0.14.10 touched none of these owners | `git diff v0.14.9..HEAD` |

Not inputs: claims 9 and 23 (the reporter's project and server data — gate numbers, counts,
403s, "4 copies"), claim 11 (refuted), claim 8 (partly true — the migration was shown once on
the update that brought it; whether it was is unproven), and the issue's proposed fix.

Found while designing, before proposing — a probe of `MultipleStringLiterals` on Checkstyle
14.3.0 (`allowedDuplicates=2`, `ignoreStringsRegexp='^".{0,4}"$'`) over the 94 shipped
`*.java.example` files, alone and inside a copy of `checkstyle.xml.example`:

- **Thresholds match S1192.** SonarJava's `StringLiteralDuplicatedCheck`:
  `DEFAULT_THRESHOLD = 3`, `DEFAULT_MINIMAL_LENGTH = 5` (content length, quotes excluded),
  literals inside annotations skipped, and test files skipped outright
  (`JavaFileTypeClassifier.isTestFile` → return). Checkstyle's `allowedDuplicates=2` flags the
  third occurrence; its default `ignoreOccurrenceContext=ANNOTATION` skips annotations. So the
  module belongs in `checkstyle.xml` (production) only, never in `checkstyle-test.xml`.
- **10 violations, all in test templates** (`ControllerTest`, `SecuredControllerTest`,
  `UseCaseTest`, `PersistenceIT`, `ArchitectureTest`, `LoggingCommonsMethodsTest`) — outside
  the production config's reach, and outside S1192's. Same 10 with the full production config:
  no suppression filter interferes.
- **33 production templates did not parse standalone** (`{{…}}` placeholders, multi-class
  `// --- file` blocks): unverified, not clean. Since the thresholds are S1192's, anything the
  module flags in them Sonar flags too — the module adds no false positive relative to the gate.
- `ApiExceptionHandler`'s two occurrences each sit under the threshold; the 400 handler row 2
  asks for makes them three.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| — scope | All four groups: template defects (rows 1-5, 15-16), S1192 enforcement (20), parameterized tests (10-12), `sonar-lessons` probe and the `domain-modeling` mirror question (18-19) | — |
| 7 — mandatoriness, S1192 | Enforce at build: Checkstyle module plus a `migrations` entry, the `0113` path. "No magic strings" (`code-quality.md:31`) is a norm broken again | persuasion only |
| 7 — S110 | Create nothing. The hierarchy is by design (`error-handling.md`), and no run here shows S110 firing | an ignore entry, a norm change |
| 8 — already-generated projects | Keep `0104`: a migration is shown once, the user decides. No re-show, no `doctor` line | a detection field in `migrations`, Form 7c |
| — `Location` | A `public static final String BASE_PATH` the class mapping and `URI.create` share. Relative URI, no proxy config. Weighed: `fromCurrentRequestUri()` gives an absolute URI and keeps a gateway prefix, but needs `server.forward-headers-strategy` behind any proxy (else the pod's host leaks), only fits `POST` on the collection, and a replay returns a stored absolute host | `ServletUriComponentsBuilder` |
| — `AssignedIdEntity` | The executor's line only; the ArchUnit guard stays `implement(Persistable)` | a guard change and its migration |
| — S5976 | A parameterized interceptor test exemplar plus a `test-architect` design-step line | design step only, template only |
| — mirror | Asked in the interview, then withdrawn at approval: the spec does not concern itself with whether the mapping of new attributes shares names with other entities or aggregates. `domain-modeling` is unchanged | a `domain-modeling` question |
| 16 — existing mode | No `ArchHook` mode; the project's build already runs Checkstyle at `validate` | Form 7c |
| 17 — CI | `templates` › `checkstyle-configs` gains fixtures; `schema` + `MigrationsSchemaTest` cover the entry; `exemplar-imports` covers new imports | a new workflow |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Template and norm fixes at each owner; `MultipleStringLiterals` in `checkstyle.xml.example` with fixtures; `migrations` entry `multiple-string-literals`; interceptor test exemplar and design-step line; `domain-modeling` question; `sonar-lessons` probe | 8 | **Approved**, without the `domain-modeling` question |
| 2 | Option 1 without the `migrations` entry | 6 | Rejected — new projects only, against `0104`/`0109`/`0113` |
| 3 | Template fixes only, no Checkstyle module | 4 | Rejected — the persuasion `0113` reversed |
| 4 | create nothing | 2 | Rejected — every confirmed row reproduces on the next generation |

### Option 1 — fixes at each owner + Checkstyle + migration (score 8)

**Motivator:** axis 7 — a norm broken again becomes enforcement (invariant 6), with `0113` as
the exact precedent: the build is where the project's mechanical rules live, and an existing
project gets a build change only through `migrations` (`0104`, `0109`).

**What changes, by owner:**

- `rest-api-architect/templates/ApiExceptionHandler.java.example` — one private
  `invalidRequest(request, violations)` builds every structural 400, so `"Invalid request"`
  and `"violations"` are written once (written as a helper rather than the two constants
  first proposed: three handlers built the same four lines); a
  `MethodArgumentTypeMismatchException` handler answering 400 with one violation named after
  the parameter — `api-rest.md` already listed an invalid path or query param as 400.
- `rest-api-architect/templates/Controller.java.example` — `public static final String
  BASE_PATH`, read by `@RequestMapping` and by `Location`.
- `test-architect/templates/ControllerTest.java.example` — asserts the exact `Location` value,
  built from `BASE_PATH`.
- `rules/api-rest.md` § status table — `Location` is relative, built from the same path
  constant the class mapping uses.
- `agents/java-spring-boot-developer.md:404` — an assigned-id entity extends the project's
  shared `AssignedIdEntity`, never a `Persistable` of its own.
- `project-bootstrap/templates/checkstyle.xml.example` — `MultipleStringLiterals`
  (`allowedDuplicates=2`, `ignoreStringsRegexp='^".{0,4}"$'`), header naming S1192's
  thresholds. Not in `checkstyle-test.xml`: S1192 skips test files.
- `rules/code-quality.md` § How to verify — the check and its numbers, paired with the
  config the way the other limits are.
- `schemas/extensions.json` — `migrations.entries[multiple-string-literals]`, all 7
  blueprints, module XML verbatim; stops when `checkstyle.xml` already has it.
- `test-architect/templates/IdempotencyKeyInterceptorTest.java.example` — one
  `@ParameterizedTest` over malformed keys (`@NullAndEmptySource` + `@ValueSource`), one
  accepted key, one unprotected route.
- `test-architect/SKILL.md` step 5 and the § What the partial contains table;
  `templates/test-spec.md.example` § 2 — a parameterized method listed once, with its
  inputs.
- ~~`domain-modeling/SKILL.md` step 2 — a mirror question~~ — withdrawn at approval.
- `sonar-lessons/SKILL.md` step 1 and `references/sonar-web-api.md` — a `component_tree`
  probe with `ps=1` before the run, so a token that cannot read it stops at preconditions
  instead of mid-extraction. **Not** a `duplications_data` fallback: no server here to verify
  the call against, and an unverified API call in a reference is worse than none.

**Pros:** each fix lands at the file that owns the shape, so the next generation stops
emitting it. The one rule with an executable form fails `./mvnw verify`, which the executor
runs before reporting — fixed in the same run, not a Sonar round later. Thresholds equal
S1192's, so the check never fails a build Sonar would pass on that rule.

**Cons:** the S1192 numbers live in the norm and in the config — the pairing
`code-quality.md` already declares for every other limit, held by the fixtures. The migration
prompt spells the XML verbatim and drifts from the template by design. 33 production
templates are unverified against the module standalone (see above). The `domain-modeling`
question guards duplication that stayed under the gate (1.03 % < 3 %) — persuasion against a
failure not observed here, chosen by the maintainer. S110 and the migration re-show stay
open on #86.

**Points cut in the rubric:** maintenance (−1: verbatim XML in the migration; seven owners
touched); enforcement (−0.5: the mirror question and the template fixes are persuasion);
invariant 2 strain (−0.5: thresholds paired across two files).

**CI:** `templates` · `checkstyle-configs` › `checkstyle.xml and checkstyle-test.xml against
fixtures` — `CheckstyleConfigTest.java` gains a bad fixture (a 5-character literal three
times, flagged as `MultipleStringLiterals` by the main config) and good fixtures that must
pass it (twice; a 4-character literal three times; three times inside annotations). Paths
already in both `templates.yml` filters. The migration entry: `validate` · `design` ›
`frontmatter schema` and `hooks-cross-platform` › `MigrationsSchemaTest`. New imports of
`ApiExceptionHandler` and the interceptor test: `validate` · `exemplar-imports`. Nothing
testable for the executor line, the `domain-modeling` question and the `sonar-lessons` probe:
prose a model reads, and no Sonar server in CI.

### Option 2 — Option 1 without the migration (score 6)

New projects only. `0113` and `0109` made a migration the answer for every build change
since `0104`. **Cut:** propagation (−2), form fit (−1).

### Option 3 — template fixes only (score 4)

What `0098` did for test smells and `0113` reversed. The literal in `ApiExceptionHandler` is
one instance; the next handler the executor writes repeats a different one. **Cut:**
enforcement (−3), invariant 6 strain (caps at 4).

### Option 4 — create nothing (score 2)

Every confirmed row stays reproduced on the next generation. **Cut:** form fit, enforcement,
propagation, symptom unaddressed.

## References

| Claim | Source |
|---|---|
| A norm broken again becomes enforcement | `@CLAUDE.md` invariant 6; decision matrix § 3 |
| Build checks for existing projects travel as a `migrations` entry, shown once | `0104` axis 8; `0109`; `0113` |
| `checkstyle.xml` runs at `validate` over production code; `checkstyle-test.xml` at `verify` over `src/test` | `rules/code-quality.md` § How to verify; `0097` |
| A Checkstyle config never runs here; only a fixture test proves it | `0097`, `0099`, `.ci/CheckstyleConfigTest.java` |
| S1192's threshold, minimum length, annotation and test-file exclusions | SonarJava `StringLiteralDuplicatedCheck.java` (master), probe above |
| `ignore` lines do not reach a project with a configured scanner | `0098` F; `arch-adopt/SKILL.md:182-184` |
| A rule never names a skill, agent or template | `@CLAUDE.md` invariant 1 |
| Relative `Location` is valid | RFC 9110 § 10.2.2 |

## Propagation

| File | Change |
|---|---|
| `skills/rest-api-architect/templates/ApiExceptionHandler.java.example` | `invalidRequest` helper for every structural 400; `handleTypeMismatch` for `MethodArgumentTypeMismatchException` |
| `skills/rest-api-architect/templates/Controller.java.example` | `public static final String BASE_PATH`, read by `@RequestMapping` and `Location` |
| `skills/test-architect/templates/ControllerTest.java.example` | every request path from `UserController.BASE_PATH`; `Location` asserted by value |
| `rules/api-rest.md` § Success statuses | `Location` is relative, built from the class mapping's path constant |
| `agents/java-spring-boot-developer.md` § persistence validation | an assigned-id entity extends the shared `AssignedIdEntity`, never a `Persistable` of its own |
| `skills/project-bootstrap/templates/checkstyle.xml.example` | `MultipleStringLiterals`, S1192's numbers, production only |
| `rules/code-quality.md` § How to verify | the check and its numbers in the Checkstyle line |
| `schemas/extensions.json` | `migrations.entries[multiple-string-literals]`, 7 blueprints, module XML verbatim |
| `skills/test-architect/templates/IdempotencyKeyInterceptorTest.java.example` | new — `@ParameterizedTest` over six missing or malformed keys, one accepted key, three unprotected routes |
| `skills/test-architect/SKILL.md` | step 5 groups cases that differ only in input; § What the partial contains cites the new exemplar |
| `skills/test-architect/templates/test-spec.md.example` | § 2 lists `rejectsInvalidAddress` once with its inputs, matching `DomainTest`; § 4 renamed with it |
| `skills/sonar-lessons/SKILL.md` | precondition 6, the Browse probe; the 403 failure line names the call |
| `skills/sonar-lessons/references/sonar-web-api.md` | § Preconditions row for the `component_tree` probe |
| `.ci/CheckstyleConfigTest.java` | two fixtures and three assertions |
| `docs/pt-br/07-ci-validate.md`, `docs/en/07-ci-validate.md` | `checkstyle-configs` row: the new fixtures and decision 0120 |

Not changed, on purpose: `checkstyle-test.xml.example` (S1192 skips test files); the ArchUnit
guard (interview); `error-handling.md` and the Sonar ignore lines (S110: create nothing);
`arch-adopt` step 9 (`0104` kept); `domain-modeling` (withdrawn at approval).

Goes to the generated project: **yes** — new projects through `project-bootstrap` step 4.6
and the exported skills, rules and agent; existing projects through the `migrations` entry,
which `/arch-adopt` prints on their next update and never runs.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `templates` · `checkstyle-configs` › `checkstyle.xml and checkstyle-test.xml against fixtures` | The module parses on the latest Checkstyle, flags a 5-character literal three times by id `MultipleStringLiterals` in the main config only, and passes twice, a 4-character literal and literals in annotations | green on 14.3.0 · red with `allowedDuplicates=1` (`AllowedLiterals` fails) · red without `ignoreStringsRegexp` (`AllowedLiterals` fails) · red with the module removed (`RepeatedLiteral` unflagged, by name) |
| `validate` · `design` › `frontmatter schema` | The migration entry's fields, id pattern and blueprint ids; the skills' and agent's frontmatter and sections | green (`schema` exit 0) |
| `validate` · `hooks-cross-platform` › `MigrationsSchemaTest` | `schema` blocks a malformed entry and accepts the shipped block | green |
| `validate` · `exemplar-imports` | `MethodArgumentTypeMismatchException`, `MockHttpServletRequest`, `NullAndEmptySource`, `CsvSource` resolve on the Initializr classpath | runs on the PR |

Both paths the fixture test reads are already in `templates.yml`'s `push` and
`pull_request` filters. Nothing testable for the executor line, the `test-architect` step and
the `sonar-lessons` probe: prose a model reads, and no Sonar server in CI.
