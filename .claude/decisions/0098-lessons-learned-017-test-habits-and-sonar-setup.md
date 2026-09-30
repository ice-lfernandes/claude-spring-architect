# 0098 · Remediation of lessons-learned-017 — test habits, sealed switches, and the Sonar setup

- **Date:** 2026-09-30
- **Scenario:** `@.claude/lessons-learned/lessons-learned-017.md` §§ 5a, 6 (rule line), 7 and
  10, plus the S2326/S112 suppression `0096` deferred — the sections `0096` and `0097` left
  open. Same branch and PR (#46).
- **Decision:** Options A, B, C, D, E and F. Persuasion and exemplars only — no hook, no
  Checkstyle check for 5a. One agent's territory widens (`commons-logging-installer` gains
  `src/test/java/**/logging/**`), and the tests partial becomes the executor's fourth
  source of a declared dependency.
- **State:** approved by Lucas Fernandes, on 2026-09-30

## Reproduced on disk before classifying

Checked against `aaff942`.

| § | Claim | On disk |
|---|---|---|
| 5a | No rule says a sealed switch has no `default` | Confirmed. No template carries one either: the two `default ->` in templates (`domain-modeling/templates/ValueObjectCatalog.java.example:137`, `gof-design-patterns/templates/Flyweight.java.example:48`) switch on a `String`, where `default` is right |
| 5a | "Consider an ArchUnit or hook check" | Not viable. ArchUnit reads bytecode, where the `default` label is gone. A Checkstyle `MatchXpath` sees a pattern switch with a `default`, but not whether the selector is sealed — and on an open selector the `default` is required for the switch to compile. Every such check has false positives by construction |
| 7 | Nothing names the tool for "wait until a record arrives" | Confirmed: no template, rule or skill mentions `KafkaTestUtils`, Awaitility or a Kafka `*IT`. The demo has neither `spring-kafka-test` nor `awaitility`; both are managed by the Spring Boot BOM |
| 7 | `commons-logging-installer` should ship the aspect tests | The installer writes **no** test today: its territory is `src/main/**/logging/**`. The executor wrote the tests the lessons flag — to cover the installed classes, with no exemplar |
| 7 | — | A gap the lessons did not name: `40-testes.md` has no dependency block. `java-spring-boot-developer.md:106` lets the executor write `pom.xml` only for a dependency the messaging, jobs or persistence partial declares — Awaitility (or the demo's `testcontainers-kafka`) has no legal entry point |
| 10 | Anonymous access is one line; the summary names only the token path | Confirmed at `sonarqube-setup/SKILL.md:69-70` and `:139-142` |

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | S108 ×1, S5778 ×17 (templates fixed in `0096`, rule line open), 13 test smells with no rule or exemplar, three round trips to a first anonymous scan | create nothing |
| 5 — nature | Facts about test and Java code → rule lines; exemplar code → templates; setup steps → the skill that runs them | new skill, new agent |
| 7 — mandatoriness | Persuasion: no mechanical check for 5a is sound; the test smells are Sonar's to catch | Forms 7 and 8 |
| 8 — destination | Both — rules, development skills and the installer travel via `export` | — |
| 9 — integration | The installer gains a test territory; the executor gains the test partial as a dependency source | — |
| — § 5a | One line in `rules/code-quality.md` | Checkstyle `MatchXpath` |
| — § 6/§ 7 | All four: rule lines in `testing.md`, Awaitility, aspect test templates, AssertJ idioms | — |
| — suppression | 23.1: multicriteria in the Sonar build templates | 23.2, 23.3, accept on the server |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| A | § 5a: `rules/code-quality.md` — a switch over a sealed type has no `default`; `MissingSwitchDefault` does not apply to pattern switches | 8 | **Approved** |
| A′ | A + Checkstyle `MatchXpath` on a pattern switch with `default` | 4 | Rejected — false positives on open selectors, by construction |
| B | `rules/testing.md`: one call inside an `assertThatThrownBy` lambda; `isNotEmpty()` before `allSatisfy`/`allMatch`/`noneMatch`; no hand-rolled polling — Awaitility; AssertJ idioms (one chain per subject, the dedicated assertion over `isEqualTo` on a derived value, static imports) | 8 | **Approved** |
| C | Awaitility's entry point: a `## 5 · Test dependencies` block in `test-spec.md.example`, `test-architect` step naming it, and the executor's `pom.xml` clause listing the tests partial | 8 | **Approved** |
| D | Three test templates in `new-feature/templates/commons/` (`LogExecutionAspectTest`, `HttpMethodLogExecutionAspectTest`, `LoggingCommonsMethodsTest` with `@ParameterizedTest`), written by `commons-logging-installer`; its territory gains `src/test/java/**/logging/**` | 7 | **Approved** |
| E | `sonarqube-setup`: the three anonymous-access settings for the local container; a pre-flight access check before the scan; the rerun command | 9 | **Approved** |
| F | 23.1 from `0096`: `sonar.issue.ignore.multicriteria` for S2326 and S112 in `pom-sonar.xml.example` and `build-gradle-sonar.example`; each Java template's comment points at it | 7 | **Approved** |

### A — rule line for sealed switches (score 8)

**Motivator:** axis 1 — the model added `default -> { }` believing Checkstyle required it, and
lost the compiler's exhaustiveness check. **Cut:** enforcement (−1, persuasion, no sound check
exists).

### A′ — plus `MatchXpath` (score 4)

Blocks a correct `default` on an open selector. **Cut:** cost of always running (a judgment
call that will sometimes be wrong blocks the build), form fit, trust.

### B — `testing.md` lines (score 8)

**Motivator:** axis 1 — S5778 ×17, and the two test **bugs** (S5841, S1751) are tests that can
pass without testing. **Cons:** four more lines in a rule loaded on every test file.
**Cut:** enforcement (−1).

### C — Awaitility's entry point (score 8)

Without it, B names a library the executor is not allowed to add. **Cons:** touches three
files for one dependency row; also legalizes the demo's `testcontainers-kafka`, which arrived
with no declaring block. **Cut:** maintenance (−1).

### D — aspect test templates (score 7)

**Motivator:** axis 1 — S1186 ×4 and S5976 in tests the executor improvised for installed
code. **Pros:** installed classes arrive tested; nobody reinvents the fixture. **Cons:** the
installer's territory widens to `src/test`; three more templates to keep in sync with the
aspects. **Cut:** maintenance (−1), trust surface (−1, wider territory).

### E — `sonarqube-setup` (score 9)

**Motivator:** axis 1 — three failures, each found after a full `verify`. **Cut:** none
material.

### F — multicriteria (score 7)

Recorded in full in `0096` § Option 23.1. **Cut:** maintenance (−1), propagation (−1,
projects already configured miss it).

## References

| Claim | Source |
|---|---|
| A rule never names a skill, agent or template | `@CLAUDE.md` invariant 1 |
| The executor writes `pom.xml` only for a declared dependency | `agents/java-spring-boot-developer.md:106-116` |
| Installer territory is per-agent data | `agent_classes.installer.overrides` in `@.claude/schemas/extensions.json` |
| Awaitility and `spring-kafka-test` versions are Boot-managed | Spring Boot dependency management; invariant 8 forbids writing a version |
| S2326/S112 options | `0096` § Options 23.1–23.3 |

## Propagation

| File | Change | Option |
|---|---|---|
| `rules/code-quality.md` | Sealed switch has no `default`; `MissingSwitchDefault` does not apply to pattern switches | A |
| `rules/testing.md` | One call inside the `assertThatThrownBy` lambda; new § Assertions (`isNotEmpty()` before `allSatisfy`, one chain per subject, dedicated assertions, static imports, `@ParameterizedTest`, commented empty fixture methods); new § Asynchronous effects (Awaitility) | B |
| `skills/test-architect/templates/test-spec.md.example` | New `## 5 · Test dependencies` block | C |
| `skills/test-architect/SKILL.md` | Step 6 names test dependencies; five blocks; block table row | C |
| `skills/new-feature/SKILL.md` | Step 6 validates five blocks; consolidation requires `40-testes.md` § 5 | C |
| `skills/new-feature/templates/feature-spec.md.example`, `feature-spec-short.md.example` | Test dependencies row | C |
| `agents/java-spring-boot-developer.md` | `pom.xml` clause accepts the tests partial's § 5 | C |
| `skills/new-feature/templates/commons/LoggingCommonsMethodsTest.java.example` | **New** — `@ParameterizedTest` over the four `LoggingOptions` | D |
| `skills/new-feature/templates/commons/LogExecutionAspectTest.java.example`, `HttpMethodLogExecutionAspectTest.java.example` | **New** — fixture methods commented, one parameterized test | D |
| `agents/commons-logging-installer.md` | Reads sixteen exemplars; writes the three tests (step 3b); runs them (step 6); summary line | D |
| `schemas/extensions.json` | `agent_classes.installer.overrides.commons-logging-installer.write_allow` gains `src/test/java/**/logging/**` (rooted and `**/`) | D |
| `docs/pt-br/01-tipos-de-arquivo.md`, `docs/en/01-file-types.md` | Installer territory row: main and test | D |
| `skills/sonarqube-setup/SKILL.md` | Anonymous option warns; report gains the access check, first-run/rerun commands, and the three anonymous settings; step 3 explains the multicriteria entries | E, F |
| `skills/sonarqube-setup/templates/pom-sonar.xml.example`, `build-gradle-sonar.example` | `sonar.issue.ignore.multicriteria` for S2326 (`IdempotentOutcome.java`) and S112 (`IdempotencyAspect.java`) | F |
| `skills/persistence-architect/templates/IdempotentExecution.java.example`, `skills/rest-api-architect/templates/IdempotencyAspect.java.example` | Comment pointing at the suppression | F |

Verified: the three test templates, translated into a copy of `demo-clean-arch-single-module`,
compile and pass — 10 tests green.

Goes to the generated project: **yes** — rules, the development skills and both agents via
`export`; the Sonar entries through `sonarqube-setup` on its next run. A project where
`commons-logging-installer` already ran keeps its hand-written tests.
