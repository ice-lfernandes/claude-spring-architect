# 0127 · The generated CI's IT check reads the aggregate counts and fails on any skipped IT

- **Date:** 2026-10-06
- **Scenario:** Issue #100, triaged at `b0397dd`: the Maven CI template fails "integration
  tests actually ran" on the zero-test failsafe report that `ForwardedHeadersIT`'s enclosing
  `@Nested` class writes, though its nested classes ran tests.
- **Decision:** Option 1. The "integration tests actually ran" step of `project-bootstrap/templates/ci.yml.example` and `ci-gradle.yml.example` sums the build tool's aggregate counts and fails on any skipped IT, or when none ran.
- **State:** approved by the maintainer, on 2026-10-06

## Reproduced on disk

The confirmed rows of the `issue-verifier` table, checked at `b0397dd`. No commit since touches
a cited file.

| # | Fact | Evidence |
|---|---|---|
| 1 | The Maven step fails on any `*IT.txt` that starts with `Tests run: 0` | `project-bootstrap/templates/ci.yml.example:58` |
| 2 | `ForwardedHeadersIT` holds every test in two `@Nested` classes; the enclosing class has none | `transport-security-setup/templates/ForwardedHeadersIT.java.example:39-82` |
| 4 | The rule asks for failure only when the count of executed ITs is zero | `rules/testing.md:143-146` |
| 5 | Every Maven project that runs `transport-security-setup` with topology A or C′ gets that IT | `transport-security-setup/SKILL.md:130` |
| 6 | Present since `803af10` (v0.17.3), unchanged at v0.17.4 | `git tag --contains 803af10` |

Claim 3 (failsafe writes a separate `<Name>$<Nested>.txt` per nested class) was unproven
statically. The experiment below refutes half of it. The issue's proposed fix is not an input.

### Experiment — what the build tools actually write

Fresh Initializr projects (web, actuator; Java 21; failsafe 3.5.6 from the Boot parent; Maven
3.9.16; Gradle 9.7.1), with the `integrationTest` task from `build.gradle.parent.example` and
the failsafe block from `pom.parent.xml.example`. Three ITs: the real `ForwardedHeadersIT`
template, `GuardedIT` (two tests under `@EnabledIf` returning `false`, the shape of
`test-architect/templates/PersistenceIT.java.example`), and `PlainIT` (one test).

| Report | Maven (failsafe) | Gradle |
|---|---|---|
| `@Nested` enclosing class | `ForwardedHeadersIT.txt`: `Tests run: 0`. Its `TEST-…ForwardedHeadersIT.xml`: `tests="3"`. No `$Nested.txt` file at all | No report for the enclosing class. `TEST-…ForwardedHeadersIT$FromTrustedProxy.xml` `tests="2"`, `…$FromAnyoneElse.xml` `tests="1"` |
| Docker guard false | `GuardedIT.txt`: `Tests run: 2, … Skipped: 2` | `tests="2" skipped="2"` |
| Aggregate | `failsafe-summary.xml`: `completed=6`, `skipped=2` (one per module) | none; sum the per-class XML |

Three defects follow, two of which the issue did not name:

1. **False red, Maven, always** — the enclosing `.txt` reads `Tests run: 0` (the issue).
2. **False red, Gradle, when nested ITs are the only ITs** — the `*IT.xml` report probe finds
   nothing, since every file ends in `$<Nested>.xml`, and the step says "no integrationTest
   report".
3. **False green, both, always** — a class skipped by the Docker guard reports
   `Skipped: N`, never `Tests run: 0`. The step never caught the skip it exists for.
   Decision 0011's premise, carried by lessons-learned-002 § 2, never held on failsafe 3.x
   or Gradle.

## Interview

The triaged table and the experiment answered the axes that need no user. These are the
axes that remained.

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 2 — trigger | A step of the generated project's CI, after `verify` | 1, 2, 3, 6: no model decision and no Claude Code runtime event is involved |
| 7 — mandatoriness | Build gate, must hold | 4, 5: persuasion |
| 8 — destination | Generated project only | — |
| 9 — integration | `project-bootstrap` owns both CI templates; `rules/testing.md` owns the norm; `arch-adopt` carries `migrations` | — |
| 17 — CI coverage | New path-filtered test in `templates.yml` (maintainer's choice) | — |
| Detection strategy | Fail on any skipped IT (maintainer's choice, after the trade-offs) | Options 2 and 3 below |
| Existing projects | A `migrations` entry (maintainer's choice) | — |

None of the eight forms is new here. The fix is an edit to two templates that the generated
project runs as its own CI, plus the norm they enforce. The decision is how the check counts.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Aggregate counts, fail on `skipped > 0` or `executed == 0` | 8 | **Approved** |
| 2 | CI-aware Docker guard in the IT template, aggregate `executed > 0` | 5 | Rejected — enforcement by convention; an old or hand-written guard skips silently |
| 3 | Aggregate counts, fail on `executed == 0` only | 3 | Rejected — keeps the false green; a Docker-free IT keeps the count above zero |
| 4 | Create nothing | 1 | Rejected — every topology A or C′ Maven project stays red |

### Option 1 — aggregate counts, any skip fails (score 8)

**Motivator:** axis 7. The rule says the guard must never turn into a silent skip in CI. A
skip is the only thing the reports record when it happens.

**Shape.** Both templates stop reading per-class reports by name.
- Maven: sums `completed` and `skipped` over every
  `*/target/failsafe-reports/failsafe-summary.xml`, so each module of a multi-module build
  counts.
- Gradle: sums `tests` and `skipped` over every
  `*/build/test-results/integrationTest/*.xml`, whatever the class name ends with.

The source-presence gate of lessons-learned-002 § 2 stays. Fails when no aggregate exists,
when `executed = total − skipped` is zero, or when `skipped > 0`. Each failure prints its own
reason. `rules/testing.md` § Database in integration tests changes to "fails if any
integration test was skipped, or none ran".

**Pros:**
- Catches the guard whether or not a Docker-free IT ran beside it.
- Edits CI files only: no Java in the project changes, and the migration touches one file.
- Catches every silent skip (`assume*`, `@Disabled`), which is what the rule forbids.

**Cons:**
- An `@Disabled` or `assume*` IT turns CI red; disabling an IT means removing it.
- Reads report formats (`failsafe-summary.xml`, Gradle JUnit XML). Both are stable, and the
  test below watches them.

**Points cut in the rubric:** 6 (novel: no step in the repo parses an aggregate today), 9 (a
blocking judgment: a deliberate `@Disabled` IT gets stuck).

**CI:** a new `.claude/.ci/CiItCheckTest.java` and a job in `.github/workflows/templates.yml`.
The test reads the step's `run:` block out of each template. It builds fresh Initializr
Maven and Gradle projects with `ForwardedHeadersIT` alone, the case that broke both tools,
and runs the block under bash: it must pass. It adds the guarded IT, rebuilds and runs again: the block must fail by
name. Both template paths and the test go into both trigger lists.

### Option 2 — CI-aware guard in the template (score 5)

`dockerAvailable()` in `test-architect/templates/PersistenceIT.java.example` returns `true`
when `CI` is set, so Testcontainers fails loudly. The step keeps only aggregate
`executed > 0`.
- Pro: the failure points at the IT; `@Disabled` stays allowed.
- Con: enforcement rests on every IT copying the new guard, and a hand-written or older guard
  skips silently with nothing to catch it. That is persuasion where a gate was available
  (invariant 6).
- Con: `CI` is unset on Jenkins, among others.
- Con: the migration rewrites every IT, not one file.
- Cut: 4, 5, 7.

### Option 3 — aggregate, `executed == 0` only (score 3)

The current rule read literally, through the aggregates. It fixes defects 1 and 2 and keeps
defect 3. In a topology A or C′ project, `ForwardedHeadersIT` needs no Docker and always
runs, so the count never reaches zero and the check is dead. Cut: 1, 4, 5, 6, 7, 9.

### Option 4 — create nothing (score 1)

Every Maven project with topology A or C′ stays red, and every project stays blind to the
guard.

## References

| Claim | Source |
|---|---|
| The step under change | `project-bootstrap/templates/ci.yml.example:44-62`, `ci-gradle.yml.example:44-62` |
| The norm it enforces | `rules/testing.md` § Database in integration tests |
| The source-presence gate stays | `lessons-learned/lessons-learned-002.md` § 2, `decisions/0011` |
| A generated-project CI step is right for a claim about the project's code | `claude-code-architect-designer/references/ci-coverage.md` § Axis 8 = "both" |
| Network or Maven builds go in the path-filtered workflow | `decisions/0099`, `.github/workflows/templates.yml` |
| Existing projects learn of a convention change through `migrations` | `decisions/0104`, `schemas/extensions.json` › `migrations` |
| The guard shape and the build wiring used in the experiment | `test-architect/templates/PersistenceIT.java.example`, `project-bootstrap/templates/pom.parent.xml.example:194-205`, `build.gradle.parent.example:133-145` |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/project-bootstrap/templates/ci.yml.example` | The step sums `completed` and `skipped` over every module's `failsafe-summary.xml`, fails on any skipped IT and names the skipped classes, and fails when none ran. It never reads a per-class `.txt` |
| `.claude/skills/project-bootstrap/templates/ci-gradle.yml.example` | The same shape, summing `tests` and `skipped` over every XML under `integrationTest/`, whatever the class name ends with |
| `.claude/rules/testing.md` | § Database in integration tests: any skipped IT, or none run, fails the pipeline. The count comes from the aggregate, never from one report per class |
| `.claude/schemas/extensions.json` | The `migrations` entry `ci-it-check-reads-aggregates`, for every blueprint |
| `.claude/.ci/CiItCheckTest.java` | New test (step 9) |
| `.github/workflows/templates.yml` | Job `ci-it-check`, with its five paths added to both trigger lists |
| `docs/pt-br/07-ci-validate.md`, `docs/en/07-ci-validate.md` | The `ci-it-check` row, the local command, and "every job" in place of "both jobs" |

Goes to the generated project: **yes.** It gets the CI template through `project-bootstrap`
and the rule and the `migrations` entry through `export`. `/arch-adopt` does not write
`.github/workflows/`, so an existing project gets the change through the migration prompt.

`decisions/0011` and `lessons-learned-002` § 2 are left as written. The source-presence gate
they introduced stays. This record corrects their premise that a guard skip reports zero tests.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `templates` · `ci-it-check` › `@Nested ITs pass, a Docker-guard skip fails, on Maven and Gradle` | The step is read out of each template and run under `bash --noprofile --norc -eo pipefail` on a fresh Initializr project per build tool, wired with the IT block of the parent build template. With `ForwardedHeadersIT` alone, it passes and reports 3 ITs. With a `GuardedIT` added, it fails and names `GuardedIT` | Green on the tree: 4 of 4 cases, 62 s. Red against the templates at `b0397dd`: 4 of 4 cases failed by name. Maven was red on `@Nested` alone (`An IT ran with 0 tests`) and failed the guard case without naming `GuardedIT`. Gradle was red on `@Nested` alone (`no integrationTest report`) and **green** on the guarded build |
| `validate` · `design` › `frontmatter schema` | The new `migrations` entry carries the required fields and names existing blueprints | Green |
