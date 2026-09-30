# 0097 · Remediation of lessons-learned-017 — the offline half of Sonar in the build loop

- **Date:** 2026-09-30
- **Scenario:** `@.claude/lessons-learned/lessons-learned-017.md` §§ 9 and 8 — 42 Sonar issues
  shipped through a green `./mvnw verify`; the user already chose, in `0096`'s interview, the
  offline option only: no Sonar step in the developer loop.
- **Decision:** Option 1, with the test execution bound to `verify`. Spotless `check` bound to
  `verify` in `pom.parent.xml.example` (Gradle already had it in `check`);
  `IllegalIdentifierName` with an **explicit** `format` in `checkstyle.xml.example`; new
  `checkstyle-test.xml.example` with only that module, read by a second Maven execution
  (`checkstyle-test`, `verify`, `src/test`, own `cacheFile`) and by Gradle's `checkstyleTest`;
  `rules/naming.md` § Restricted identifiers owns the norm; `spotless:apply` /
  `spotlessApply` before step 8's first build; the CI `formatting` step removed from both
  templates.
- **State:** approved by Lucas Fernandes, on 2026-09-30

## Reproduced on disk before classifying

Checked against `9a3c7af`. The lessons' premise holds for Maven only; three facts change the
mechanism `0096` recorded.

| Claim | On disk |
|---|---|
| `spotless:check` is not bound to `verify` | **Maven: confirmed** — `pom.parent.xml.example` declares the plugin with no `<execution>`. **Gradle: false** — the Spotless Gradle plugin wires `spotlessCheck` into `check`, which `build` runs |
| Nothing checks formatting before `main` | **CI does**: `ci.yml.example:64` and `ci-gradle.yml.example:64` run a `formatting` step. The gap is the **local** loop the executor and `git-publish` run — a project whose first push never happened has no CI signal at all |
| Checkstyle never reads `src/test` | **Maven: confirmed** (`<includeTestSourceDirectory>false`). **Gradle: the opposite** — `checkstyleTest` runs the full config, size and magic-number rules included, over `src/test` |
| Add `UnusedImports` to Checkstyle | Redundant with `spotless:check` (`<removeUnusedImports/>` fails the check on an unused import, main and test). `project-bootstrap/references/scaffold.md:58` already decides imports belong to Spotless. And harmful at `validate`: the `check` hook runs `test-compile` on every Java edit and exits 2 on failure, in parallel with `format` — an import added one edit before its use would block |
| Bind `spotless:check` to `verify` | Correct phase, and for the same reason: at `validate` the `check` hook would race `format`'s `spotless:apply` on every edit |
| — | Consequence nobody listed: with `spotless:check` in `verify`, `project-bootstrap` step 8's first `./mvnw clean verify` fails on Initializr's tab-indented files unless `spotless:apply` runs first. Gradle's `./gradlew build` already has this latent failure |

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | 7 of 42 issues (S1128 ×2, S6213 ×5) are catchable offline; S1128 and every format drift already fail CI, never the local loop | create nothing |
| 7 — mandatoriness | Build gate — the generated project's own build, not a hook here | Forms 7 and 8 in this repo: the guarantee is Maven/Gradle's |
| 8 — destination | Generated project only — `project-bootstrap` templates and references | — |
| 9 — integration | `check` hook runs `test-compile` (includes `validate`) and blocks; `format` runs `spotless:apply` in parallel | anything transient-sensitive at `validate` |
| — imports | Spotless only, not Checkstyle | `UnusedImports` |
| — Gradle `checkstyleTest` | Align to the light config | leave the full config on tests |
| — CI `formatting` step | Remove from both CI templates | keep a step that can never be first to fail |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Spotless `check` bound to `verify` (Maven); `IllegalIdentifierName` in `checkstyle.xml`; new `checkstyle-test.xml` with only that module, run by a second Maven execution over `src/test` and by Gradle's `checkstyleTest`; `spotless:apply` before step 8's first build; CI `formatting` step removed | 8 | **Approved** — test execution at `verify` |
| 2 | The lessons' list verbatim: `UnusedImports` + `IllegalIdentifierName` in Checkstyle, second execution over `src/test`, `spotless:check` in `verify`; Gradle and CI untouched | 5 | Rejected — imports checked twice, `check` hook blocks mid-edit |
| 3 | create nothing — CI already runs `spotless:check` | 3 | Rejected — CI does not catch S6213, and an unpushed project has none |

### Option 1 (score 8)

**Motivator:** axis 1 — the local loop is where the 42 issues went unseen, and 7 of them are
catchable with no server.

**Pros:** each check has one owner — imports and formatting are Spotless's, restricted
identifiers are Checkstyle's; test code gets exactly the rules that fit it; Maven and Gradle
agree on what a test must satisfy; bootstrap's first build stops depending on Initializr's
indentation.

**Cons:** a second Checkstyle config file to keep in the bootstrap write list. The test
execution runs at `verify`, so a restricted identifier in `src/test` is found only after the
test run, not at edit time (see § Measured before approval). Projects generated before this change keep their build until
someone edits it: `arch-adopt` writes no build file.

**Points cut in the rubric:** maintenance (−1, a second config file); propagation (−0.5,
already-generated projects are not reached).

### Option 2 (score 5)

Imports checked twice, once at `validate` where the `check` hook blocks mid-edit; Gradle
keeps holding tests to size rules Maven does not apply. **Cut:** form fit, maintenance,
cost of always running.

### Option 3 (score 3)

CI covers formatting but not S6213, and a project that was never pushed has no CI. **Cut:**
form fit, enforcement.

## Measured before approval

The user asked what the test execution costs per edit at `validate`. Measured on a copy of
`demo-clean-arch-single-module` (112 main, 49 test files), with the `check` hook's own command
`./mvnw -q -o test-compile`, three runs each:

| Case | Today | With `checkstyle-test` at `validate` | Delta |
|---|---|---|---|
| Edit in `src/main` (Checkstyle cache warm) | 1.90 s | 2.14 s | +0.24 s |
| Edit in one `src/test` file | 3.21 s | 3.44 s | +0.23 s |
| No cache (first run, after `clean`) | — | 2.41 s | +0.5 s once |

The cost is Maven's per-execution overhead, not the rule. The user chose `verify`: zero cost
per edit, a violation found at the executor's final `verify`. Acceptable because a restricted
identifier is never a transient mid-edit state.

The same copy confirmed Spotless's reach: `spotless:check` reported about **20** unformatted files (one of them touched by the
measurement itself),
`OutboxEventStoreIT` among them with the two unused imports (S1128) the lessons list — the
format hook had missed every one.

## References

| Claim | Source |
|---|---|
| `check` hook runs `test-compile` and exits 2 | `.claude/hooks/ArchHook.java:138-143` |
| `format` runs `spotless:apply`, failure ignored | `.claude/hooks/ArchHook.java:154` |
| Imports are Spotless's, not Checkstyle's | `skills/project-bootstrap/references/scaffold.md:58` |
| Gradle Spotless wires `spotlessCheck` into `check` | Spotless Gradle plugin, `enforceCheck` default `true` |
| Checkstyle's `IllegalIdentifierName` default rejects `record` | **False for the pinned 14.1.0** — its default `format` is `^(?!var$\|\S*\$)\S+$` (read from `checkstyle-14.1.0.jar`'s metadata). Run over the demo's `src/test` it reported 0 violations; with the explicit `format` it reported the 4 `record` variables. The lessons' claim came from older docs |
| The mechanism was chosen before this run | `0096` § Scope of this run |

## Propagation

| File | Change |
|---|---|
| `skills/project-bootstrap/templates/pom.parent.xml.example` | Spotless `spotless-check` execution at `verify`; Checkstyle `checkstyle-test` execution at `verify` over `${project.build.testSourceDirectory}`, `includeResources` false, own `cacheFile` |
| `skills/project-bootstrap/templates/build.gradle.parent.example` | `checkstyleTest` reads `checkstyle-test.xml`; comment that `spotlessCheck` is already in `check` |
| `skills/project-bootstrap/templates/checkstyle.xml.example` | `IllegalIdentifierName` with explicit `format` |
| `skills/project-bootstrap/templates/checkstyle-test.xml.example` | **New** — light rule set for `src/test` |
| `skills/project-bootstrap/templates/ci.yml.example`, `ci-gradle.yml.example` | `formatting` step removed |
| `skills/project-bootstrap/references/build-maven.md`, `build-gradle.md` | § 4.6 step 3 writes `checkstyle-test.xml`; § 8 runs `spotless:apply` / `spotlessApply` first |
| `skills/project-bootstrap/SKILL.md` | Step 4.6, template list, report lines, written-files list |
| `rules/naming.md` | New § Restricted identifiers — owner of the norm |
| `rules/code-quality.md` | § How to verify: test config and Spotless |
| `docs/pt-br/02-init-project.md`, `docs/en/02-init-project.md` | Step 4.6 row and sample report |

Goes to the generated project: **yes, through `project-bootstrap`** — a newly generated
project only. `export` copies rules and development skills, not build files; `arch-adopt`
writes no build file, so a project generated before this change keeps its build until it is
edited by hand.
