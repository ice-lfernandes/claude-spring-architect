# 0136 · `sonarqube-setup` derives `sonar.coverage.exclusions` from the project's JaCoCo excludes

- **Date:** 2026-10-08
- **Scenario:** Issue #116, triaged at `5e2a79c`: sonarqube-setup writes no
  sonar.coverage.exclusions, so Sonar measures coverage over the startup and configuration
  classes that rules/testing.md § Coverage and the JaCoCo excludes in project-bootstrap's build
  templates leave out.
- **Decision:** Option 1 — Form 1, an edit of `.claude/skills/sonarqube-setup/SKILL.md` step 3
  and its report, comment-only placeholders in its two templates, and the
  `sonar-coverage-exclusions` entry in `migrations.entries` of `.claude/schemas/extensions.json`
- **State:** approved by the maintainer, on 2026-10-08

## Reproduced on disk

From the `issue-verifier` verdict of `/triage-issue 116` (layer `static`, `HEAD` = `5e2a79c` =
`v0.22.0`). Only the confirmed rows; the issue's proposed fix is not an input.

| Claim | Evidence |
|---|---|
| The norm excludes the startup class and the configuration classes from coverage, nothing else | `.claude/rules/testing.md:157-158` |
| The root POM's `jacoco-maven-plugin` carries four `<exclude>`s at plugin level, so they also apply to `report` | `project-bootstrap/templates/pom.parent.xml.example:222-235` |
| Gradle's twin filters `jacocoTestReport.classDirectories`, the task that writes the XML | `project-bootstrap/templates/build.gradle.parent.example:120-127` |
| The generated root `CLAUDE.md` restates the scope by what the class is | `project-bootstrap/templates/root.CLAUDE.md.example:115-118` |
| `sonarqube-setup` step 3 says "Coverage needs no property" — a sentence about report paths, silent on scope | `sonarqube-setup/SKILL.md:105-107` |
| Neither Sonar template carries `sonar.coverage.exclusions`; the property appears nowhere under `.claude/` | `pom-sonar.xml.example`, `build-gradle-sonar.example` |
| `sonarqube-setup` travels into generated projects | `extensions.json` `export.skills.include` |

Claim #7 of the triage (Sonar counts a source file absent from `jacoco.xml` as uncovered) was
unproven statically; the maintainer has observed it on a generated project (axis 1 below).
Claim #8 (the new-code coverage gate fails because of it) stays unproven and is not an input.
Decision `0091` chose no `sonar.coverage.jacoco.xmlReportPaths` and never weighed scope;
`0062` moved the `*Config`/`*Configuration` excludes into JaCoCo without a Sonar side. Neither
decided against this.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — concrete symptom | Observed by the maintainer: Sonar reports `*Config`/`*Application` classes as uncovered on a generated project, while the build's JaCoCo gate leaves them out | Create nothing |
| 5 — nature | A step of an existing procedure (`sonarqube-setup` step 3), not a new fact: the scope already has an owner (`testing.md`) and a build-level expression (the JaCoCo excludes) | Forms 4, 5 — a rule would be a second owner of the scope |
| 7 — mandatoriness | A missed property costs a wrong coverage number on a dashboard, not a broken build; the build's JaCoCo gate is the guarantee and it is already right | Forms 7, 8 |
| 8 — destination | Both: the skill travels (`export.skills.include`) | — (forces this record) |
| 9 — integration, source of the list | Derived at run time from the JaCoCo excludes the project's root build file already carries, `.class` → `.java`. No literal pattern list in `.claude/` beside the two `project-bootstrap` templates | Option 3 |
| 9 — integration, projects already configured | `sonarqube-setup` configures once and never re-runs on a project with the scanner, so existing projects get a `migrations` entry: `arch-adopt` prints its note and prompt on update, never runs it | A rewrite by `arch-adopt` (outside its territory) |
| 17 — CI | Answered below per option | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Step 3 of `sonarqube-setup` derives the list; templates comment only; report shows it; one `migrations` entry | 9 | **Approved** |
| 2 | Option 1 + a `doctor` line in the generated project comparing the two scopes | 6 | Rejected — guards a drift nobody observed (anti-pattern 16), Java over two build dialects |
| 3 | Literal pattern list in both Sonar templates + a `design` step asserting it equals the JaCoCo templates | 4 | Rejected — two more copies of the scope (invariant 2), blind to a project's own excludes |

### Option 1 — derive in step 3, migration for existing projects (score 9)

**Motivator:** axes 1, 5 and 9.

- `sonarqube-setup/SKILL.md` step 3: replace "Coverage needs no property" with two facts. Report
  paths need no property (unchanged reason). Scope does: read the JaCoCo excludes of the root
  build file (`<configuration><excludes>` of `jacoco-maven-plugin`; the `exclude:` list of
  `jacocoTestReport.classDirectories`), map each `.class` suffix to `.java` and keep directory
  patterns as they are, and write them comma-separated as `sonar.coverage.exclusions`. No
  JaCoCo excludes found → write no property and say so in the report. Coverage exclusion only,
  never `sonar.exclusions`: the classes still get issues analysed.
- Report: one `Coverage` line naming the derived patterns and the file they came from.
- Both Sonar templates: a placeholder line with a comment that the value is derived from the
  JaCoCo block in step 3, never spelled out in the template.
- `extensions.json` `migrations.entries`: `sonar-coverage-exclusions`, all seven blueprints.
  The prompt stops when there is no scanner, or when `sonar.coverage.exclusions` is already
  set; otherwise it derives the same way, shows the line and waits for a go-ahead.

**Pros:** one owner for the scope stays true — the norm says it, the build file expresses it,
Sonar reads the build file's expression at setup. A project that edited its JaCoCo excludes gets
its own list. No Java, no new piece.

**Cons:** persuasion: a `sonnet`/`effort: low` skill does the mapping, and nothing checks it
afterwards. A later edit to the JaCoCo excludes does not reach Sonar — no drift has been
observed, and invariant 6's mirror applies.

**Points cut in the rubric:** none counted; criterion 4 (enforcement) is arguable, kept because
no guarantee is owed to a dashboard number (axis 7).

**CI:** `validate · design › frontmatter schema` already proves the skill's body sections and
class, and the `migrations` entry's `required_fields`, `id_pattern` and that `blueprints` name
existing blueprints. The derivation itself is a model step over a project's build file —
nothing testable offline in this repository.

### Option 2 — Option 1 plus a `doctor` scope line (score 6)

A Form 7c extension of `doctor` in the generated project: when the root build file has the
scanner, compare `sonar.coverage.exclusions` with the JaCoCo excludes and warn on a gap. Would
also reach existing projects without a migration. Cut: anti-pattern 16 — no drift after setup
has been observed, only the missing property, which Option 1 already closes; Java that parses
two build-file dialects (criterion 5); a new `<Mode>Test.java` in `hooks-cross-platform`.
**CI:** a new `.claude/.ci/DoctorSonarScopeTest.java` in `hooks-cross-platform`.

### Option 3 — literal list in the templates (score 4)

The four `.java` patterns spelled in `pom-sonar.xml.example` and `build-gradle-sonar.example`,
with a `design` step asserting they match the JaCoCo templates. Rejected by axis 9: a third
and fourth copy of the scope (invariant 2 strained — the cap), blind to a project's own JaCoCo
edits, and the CI step would exist only to guard the duplication it creates.
**CI:** a new `grep` step in `validate · design`.

## References

| Claim | Source |
|---|---|
| A norm written in two places has diverged | `@CLAUDE.md` invariant 2 |
| A convention change that leaves existing projects behind is a `migrations` entry, printed by `arch-adopt`, never run | `@.claude/decisions/0104-use-case-subpackage-per-aggregate.md`; `extensions.json` `migrations.$comment` |
| `arch-adopt` cannot write the build file | `extensions.json` `skill_classes` (`arch-adopt` territory) |
| `sonarqube-setup` may write the root build file | `extensions.json` `skill_classes.build.overrides.sonarqube-setup.write_allow` |
| `sonarqube-setup` never re-runs on a configured project | `sonarqube-setup/SKILL.md` § Entry rule |
| A guarantee is bought against an observed failure | `@CLAUDE.md` invariant 6 (mirror); decision matrix anti-pattern 16 |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/sonarqube-setup/SKILL.md` | Step 3: report path still needs no property; scope derived into `sonar.coverage.exclusions` from the root build file's JaCoCo excludes, `.class` → `.java`, never `sonar.exclusions`, none written when there are no excludes. Report gains a `Coverage` line. Contract § Reads names the JaCoCo excludes |
| `.claude/skills/sonarqube-setup/templates/pom-sonar.xml.example` | `<sonar.coverage.exclusions>` placeholder, comment says derived in step 3 |
| `.claude/skills/sonarqube-setup/templates/build-gradle-sonar.example` | `sonar.coverage.exclusions` property placeholder, same comment |
| `.claude/schemas/extensions.json` | `migrations.entries` `sonar-coverage-exclusions`, all seven blueprints |

No routing row, class, or `export` change: the skill already exists, is in `skill_classes.build`
and in `export.skills.include`; its territory already holds the root build file.

Goes to the generated project: **yes** — the skill travels whole through `export`, and the
`migrations` entry travels with `extensions.json`; `arch-adopt` prints it on the next update.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · design › frontmatter schema` | The skill's frontmatter, class and required sections; the `migrations` entry's `required_fields`, `id_pattern` and existing `blueprints` | green on the tree · red with the id changed to `Sonar_Coverage`: `migrations.entries[13] \`Sonar_Coverage\` — id does not match …`, exit 2 |

The derivation itself is a model step over a project's build file at setup time; nothing in
this repository runs it offline. The Sonar templates are fragments merged into a build file,
read by no CI job — the placeholder is not compiled or parsed anywhere.
