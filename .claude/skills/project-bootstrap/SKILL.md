---
name: project-bootstrap
description: >
  Creates the complete initial structure of a Spring Boot project from a declarative
  architecture blueprint. Use when the user asks to create a project from scratch,
  start a new project, set up a project, scaffolding, "new Spring project",
  bootstrap, generate a module structure, create a multi-module Maven or Gradle
  project, build a hexagonal architecture, clean architecture, onion, layered, vertical
  slice or modular monolith, or when the /init-project command is run.
allowed-tools: Read, Write, Edit, Bash, Glob
---

# Project Bootstrap

Turns an empty directory into a Spring Boot project with declared architectural
boundaries, active hooks, and a green build.

**Does not write business code.** No example, no seed, no demo aggregate. It emits
module structure, packages, POMs, configuration, enforcement, rules, and skills — and
stops there. Classes come from the `/new-feature` pipeline, from a real spec. A `User`
aggregate invented by the bootstrap competes with that spec and diverges from the
layer skills' exemplars, which own the shape. Record:
`@.claude/decisions/0011-bootstrap-without-business-code.md`.

The only Java in the freshly generated project is what the Initializr brings — the
`@SpringBootApplication` class and the context test — plus one `package-info.java` per
role in `packages.map` (step 4.7).

## Dependencies

`java` (JDK 21+) · `git` · `curl`. **Nothing else.** `mvn`/`gradle` on the PATH are not
required — the Initializr's `starter.tgz` already brings the Maven wrapper.

No Python, no template engines, no bash. The hooks are a single Java file run in
single-file source mode, which makes them identical on Linux, macOS, and Windows — the
same dependency the target audience already has mandatorily.

## When NOT to use

`pom.xml` or `build.gradle` already exists at the root. This skill **does not migrate**
existing projects nor overwrite structure. In that case: stop, report, suggest
`/new-feature` instead.

## Preconditions

```bash
java --version && git --version && curl --version | head -1
ls ${CLAUDE_SKILL_DIR}/templates/*.example
```

Check that the exemplars the procedure cites are there —
`pom.parent.xml.example` and `pom.module.xml.example` (step 4),
`checkstyle.xml.example` (4.6), `lombok.config.example` (4.8), `Application.java.example`
and `application.yml.example` (step 3), `features/actuator/application-actuator.yml.example`
(4.7), `Dockerfile.example` and `docker-compose.yml.example` (4.10),
`root.CLAUDE.md.example` and `module.CLAUDE.md.example` (step 6),
`settings.json.example` (step 7) and `ci.yml.example` (step 8) — plus whatever the
active blueprint's `templates:` declares. Don't count files against a fixed number: the
folder grows, the number falls behind, and the precondition starts failing for nothing.
If a cited exemplar is missing, or a tool is missing: **stop and report**, naming what's
missing. Don't improvise a root POM or invent Spring Boot versions from what you think
you know — the version you have in memory is stale by construction.

Naming convention: the `.example` suffix always comes **last**
(`pom.parent.xml.example`, `DomainException.java.example`). An exemplar with the suffix
in the middle escapes the glob above and the precondition turns green without the file
existing.

## Procedure

### 1 · Select the blueprint

List `.claude/blueprints/*/*.yaml` dynamically (never a fixed list in the prompt) —
each architecture lives at `.claude/blueprints/<id>/<id>.yaml`; folders without a
`.yaml` (architecture not yet written) don't appear in the list. If the user hasn't
indicated which one, show `references/blueprint-selection.md` with each one's
`when_to_choose` and `trade_offs`, and ask. **Never assume** the architecture: it's the
most expensive decision to reverse in the whole project.

### 2 · Validate the blueprint

Read the YAML and check, one by one:

| # | Rule | If it fails |
|---|---|---|
| 1 | `id`, `name`, `build`, `modules`, `packages`, `dependency_rules`, `features`, `architecture_paths` all exist | Stop and name the missing field |
| 2 | The `depends_on` graph is acyclic | Stop and show the cycle |
| 3 | Exactly **one** module with `contains_main: true` | Stop and list the candidates |
| 4 | Every `feature` referenced by a module exists in `features` | Stop and name the feature |
| 5 | Every `templates.<role>` points to an existing file | Stop and name the path |
| 6 | `architecture_paths` is not an empty list | Stop — without this, step 6.6 has nothing to write |

Don't invent defaults for `dependency_rules` — it's the backbone of the enforcement.

### 3 · Generate the base with Spring Initializr

```bash
curl -sS https://start.spring.io/starter.tgz \
  -d type=maven-project \
  -d language=java \
  -d groupId=<groupId> \
  -d artifactId=<artifactId> \
  -d name=<name> \
  -d packageName=<packageBase> \
  -d javaVersion=21 \
  -d dependencies=<list derived from the features> \
  | tar -xzf - -C .
```

`javaVersion=21` is not a pin from memory — it's this skill's own precondition (JDK
21+, see § Dependencies) made explicit. Without it the Initializr falls back to its own
default, which has been observed to be an older LTS than what this skill requires;
letting that happen means the generated `pom.xml` and `dependency-catalog.md`'s
reasoning about the target JDK silently disagree.

The Initializr **is** the version oracle for everything else: it returns the current
Spring Boot GA, with nothing pinned in this repository. Confirm what came back:

```bash
grep -m1 '<version>' pom.xml && grep -m1 'java.version' pom.xml
```

If the network isn't available, **stop and ask** the user for the versions. Never write
them from memory. If you pin `bootVersion` explicitly, don't use the `.RELEASE`
suffix — see the note in `references/dependency-catalog.md`.

Feature → Initializr dependency mapping in `references/dependency-catalog.md`.

### 4 · Restructure according to the blueprint

If `build.layout: single-module`, the result of step 3 already works as the structure:
just create the packages from `packages.map`, there's no per-layer POM to write. The
Initializr's `pom.xml` still needs the `<build>` blocks from
`templates/pom.parent.xml.example` — Spotless, Checkstyle, failsafe, and JaCoCo —
because none of them come from the Initializr. Copy them into that `pom.xml` and move
on to step 4.6. Steps 4.6 through 4.8 apply to both layouts.

If `multi-module`:

1. Convert the root `pom.xml` into a parent (`<packaging>pom</packaging>` +
   `<modules>`), using `templates/pom.parent.xml.example` as the shape reference. The
   `<spotless.version>` property is there as a placeholder: resolve it on Maven
   Central, same rule as step 3, never from memory.

   ```bash
   curl -sS 'https://repo1.maven.org/maven2/com/diffplug/spotless/spotless-maven-plugin/maven-metadata.xml' \
     | grep -o '<release>[^<]*</release>'
   ```

   No network: **ask**, don't invent. A made-up number that doesn't exist in the
   repository breaks the build on the first `./mvnw`.

   The version oracle is `maven-metadata.xml` from `repo1.maven.org` — the repository
   itself. Don't use `search.maven.org`'s `solrsearch`: it's a separate index, returns
   versions that lag behind the repository (observed: Spotless 2.44.5 in the index,
   3.10.2 in the repository) and responds intermittently. The same holds for every
   version resolved in this skill.
2. Create a `pom.xml` per module from `templates/pom.module.xml.example`,
   with the dependencies that module's `depends_on` authorizes — **and only those**.
3. Move the `@SpringBootApplication` class to the module with `contains_main: true`.
4. Move `application.yml` into that module's `resources`.

Mandatory order: parent → modules → main class → configuration → docs → CI.
A swapped order leaves the build broken halfway through generation.

The files in `templates/` are **real, compilable exemplars**, not molds to be
mechanically substituted. Read them, understand the shape, and write the equivalent for
this project. A `domain` POM doesn't carry `spring-boot-starter-web` even if the
exemplar shows it in another module.

<a id="andaime"></a>**The exemplar's scaffolding doesn't go into the project.** This
applies to every step that copies from `templates/` — this one, 4.6, 4.7, 4.8, and 4.10. The
top comment block mixes two things:

| Stays | Goes |
|---|---|
| Why the file is shaped this way (e.g. "these two versions are distinct on purpose") | The word `EXEMPLAR` and anything describing the file as a template |
| Citations to rules (`.claude/rules/...`) | "Compilable as-is", "not pseudo-code" |
| Business rule or invariant the reader needs | "role `X` from `packages.map`", generation instructions, references to other `.example` files |

A comment that describes the template instead of the code is exactly what
`@.claude/rules/code-quality.md` forbids — in the generated project there's no template
to refer to.

### 4.5 · Domain exception family — not here

The five exemplars (`DomainException` and the four typed ones) live in
`.claude/skills/domain-modeling/templates/`. `domain-modeling` owns the shape of the
domain, and the `10-dominio.md` partial names the exceptions for each invariant; the
code comes from the executor, with the first feature.

The bootstrap doesn't write them because it has no invariant to tie them to: five
exception classes in a project with no domain are dead code the user either deletes or,
worse, keeps by mistake. `@.claude/rules/error-handling.md` is still copied in step
6.6, and it's the one that sets the taxonomy — in particular the prohibition on
throwing generic `RuntimeException`/`Exception`/`IllegalArgumentException` in any
module.

`ApiExceptionHandler` isn't from here either — transport-specific, an exemplar of
`rest-api-architect` (see `@.claude/rules/api-rest.md`).

### 4.6 · Generate the Checkstyle config

The limits from `@.claude/rules/code-quality.md` — method length, parameter count,
cyclomatic complexity, nesting, magic numbers — stay prose without Checkstyle. It's the
only automatic check the bootstrap installs.

1. Resolve the versions on Maven Central, same rule as step 3:

   ```bash
   curl -sS 'https://repo1.maven.org/maven2/org/apache/maven/plugins/maven-checkstyle-plugin/maven-metadata.xml' \
     | grep -o '<release>[^<]*</release>'
   curl -sS 'https://repo1.maven.org/maven2/com/puppycrawl/tools/checkstyle/maven-metadata.xml' \
     | grep -o '<release>[^<]*</release>'
   ```

   They go into `<checkstyle.plugin.version>` and `<checkstyle.version>` in the root
   POM. They're two versions distinct on purpose: the plugin's and the tool it runs —
   without the second, the plugin runs an old Checkstyle that doesn't know half the
   checks.

   **JaCoCo's version is resolved here too, not skipped.** Unlike failsafe,
   `spring-boot-starter-parent` does **not** manage `org.jacoco:jacoco-maven-plugin`
   (confirmed empty against the parent's `dependencyManagement`) — leaving it unpinned
   resolves whatever's newest at build time, non-reproducibly:

   ```bash
   curl -sS 'https://repo1.maven.org/maven2/org/jacoco/jacoco-maven-plugin/maven-metadata.xml' \
     | grep -o '<release>[^<]*</release>'
   ```

   Goes into `<jacoco.version>` in the root POM.
2. Write `<project>/config/checkstyle/checkstyle.xml` with the shape of
   `templates/checkstyle.xml.example`. Fixed path — it's what the root POM's
   `configLocation` points to, via `${maven.multiModuleProjectDirectory}`. Don't swap
   it for a relative path: it resolves at the root and fails in every submodule.
3. **Don't change the exemplar's numbers.** Each one mirrors a limit from
   `code-quality.md`; changing one side just makes the rule and the build disagree. If
   the limit has to change, change it in both files, in the same pass.
4. Don't add formatting checks (imports, braces, spacing): Spotless handles that,
   already configured in the root POM. A duplicate check breaks the build over
   something the formatter would have fixed on its own.

Runs at `validate`, before compiling — one violation stops the build immediately. If
the freshly generated project already fails here, the error is in the translated
exemplar or in a class coming from the Initializr, not in the limit: fix it before
continuing.

**Architecture tests (ArchUnit) are not generated here.** The bootstrap delivers a
project with no business code, and a `classes().that()...should()` rule over zero
classes fails vacuously — the build would be born red for having nothing to check.
Writing them is the job of the `test-architect` skill, once classes exist for them to
apply to. The bootstrap only records this in the output contract. The blueprint's
`archunit` feature is still valid data: it's the `test-architect` skill that reads it,
not this step.

**The coverage gate goes out the same door, for the same reason.** The
`pom.parent.xml.example` brings JaCoCo with `prepare-agent` and `report`, and
**without** the `check` execution: report yes, gate no. The limits from
`@.claude/rules/testing.md` (80% lines / 70% branches) only make sense over code that
exists, and it's `test-architect`'s setup mode that wires them — in the same pass that
installs ArchUnit. Don't add the `check` execution yourself: a project with no business
classes either passes the gate vacuously, which proves nothing, or breaks it, which is
a false negative.

### 4.7 · Materialize the packages and feature configuration

The step that replaced example generation. Two parts: the packages come to exist on
disk, and the features that have configuration receive it.

**a) One `package-info.java` per role in `packages.map`.**

An empty directory doesn't survive git, and a `packages.map` that only exists in the
YAML isn't a boundary at all: `ArchHook check` matches by path prefix, and a path that
doesn't exist never matches. For each role declared in `packages.map`, write
`<module>/src/main/java/<package>/package-info.java` with one sentence — the role, and
what that layer may import, derived from the module's `depends_on` and
`forbidden_imports`:

```java
/**
 * Domain. Aggregates, value objects, and invariants.
 *
 * <p>No framework: no {@code org.springframework}, {@code jakarta.*}, or
 * {@code com.fasterxml.jackson}. See {@code .claude/rules/architecture-ddd.md}.
 */
package com.example.demoapp.domain;
```

One sentence for the role, and the boundary line only when the module declares
`forbidden_imports`. Don't write more: `package-info.java` isn't the place to reproduce
a rule — cite it by path, invariant 2.

**b) Configuration for active features.**

| Feature | What this step does |
|---|---|
| `actuator` | Merges `templates/features/actuator/application-actuator.yml.example` into the `application.yml` of the module with `contains_main: true` (the same file from step 6, not a new one) |
| `flyway` | Creates `src/main/resources/db/migration/` in the module with the `infrastructure.persistence` role, **empty**. No `.gitkeep` and no `V1__`: `spring.flyway.fail-on-missing-locations` defaults to `false` (verified in `spring-boot-flyway`'s metadata), so a missing or empty location doesn't break startup. The first migration comes from `persistence-architect`, with a real schema |
| `persistence-jpa`, `rest`, `openapi`, `testcontainers`, `archunit` | Nothing here. They're dependencies (step 3) and POM configuration (step 4). The code that uses them comes from the first feature |
| `spring-modulith` | Writes `<main-module>/src/test/java/**/ModularityTests.java`, from `templates/features/spring-modulith/ModularityTests.java.example`, adjusting only the package and the `@SpringBootApplication` class reference. Safe to write now, unlike ArchUnit — `ApplicationModules.of(...).verify()` passes meaningfully over zero modules; it isn't gated behind business code existing |

**No business classes.** No entity, no controller, no use case, no migration with a
table. Each one's shape has an owner — `domain-modeling`, `persistence-architect`,
`rest-api-architect`, `test-architect` — and writing it here creates a second exemplar
of the same role, which diverges from the first. That's what happened:
`@.claude/decisions/0011-bootstrap-without-business-code.md`.

### 4.8 · Generate the `lombok.config`

The root POM declares `org.projectlombok:lombok` as `optional`, inherited by all
modules. Without this file, `@Data` and `@Setter` compile — and
`@.claude/rules/lombok.md` stays prose, the same way `code-quality.md` stayed without
Checkstyle.

1. Write `<project>/lombok.config` with the shape of `templates/lombok.config.example`.
   **One only, at the root, next to the root `pom.xml`.** Lombok climbs the folder tree
   until `config.stopBubbling = true`, so the root covers every module. Copying the
   file into each module adds nothing and creates four places to diverge.
2. Don't remove lines from it. Each `flagUsage = ERROR` mirrors a rule's prohibition;
   removing one makes the rule and the build disagree, same as in step 4.6.
3. Don't add `lombok.fieldDefaults.defaultPrivate = true`. It would make implicit what
   the rule wants explicit — the `@FieldDefaults(level = AccessLevel.PRIVATE)`
   annotation at the top of the class is what's read in the file, and a global default
   hides it.

There's no version to resolve: `spring-boot-starter-parent` pins Lombok's. If you write
a `<version>` in the POM, it's the same mistake as step 3 — a number written from
memory.

### 4.9 · Generate the `logback-spring.xml`

Without this file the project still logs — Spring Boot's default logback config is
enough to run — but the default pattern has no `traceId` field, and
`@.claude/rules/logging.md`'s per-line contract stays prose nobody's config actually
produces.

1. Write `<module>/src/main/resources/logback-spring.xml` with the shape of
   `templates/logback-spring.xml.example`, where `<module>` is the module with
   `contains_main: true`. **Unlike `lombok.config` (step 4.8), this file cannot sit at
   the project root** — logback only resolves its config from the classpath root, and a
   root-level file is never on it. It has to be under `src/main/resources`, in the
   module that actually gets packaged, and named `logback-spring.xml` (not plain
   `logback.xml`) so Spring Boot's own initialization picks it up.
2. Don't change the pattern line. It's `logging.md`'s contract in executable form; a
   project that needs structured JSON output changes the encoder and the rule's pattern
   line in the same pass, not this file alone.

### 4.10 · Generate the base `Dockerfile` and `docker-compose.yml`

The base pair only — one `app` service, nothing else. Every service a use case needs
afterward (a database, a broker) is added later by the `docker-architect` skill, not
here: this step doesn't know yet what `persistence-architect` will decide, and
inventing a Postgres service with no aggregate to back it is the same mistake
`@.claude/decisions/0011-bootstrap-without-business-code.md` already closed for Java
classes.

1. Write `<project>/Dockerfile` with the shape of `templates/Dockerfile.example`,
   filling `{{JAVA_VERSION}}` with the version resolved in step 3 and
   `{{MAIN_MODULE_JAR_PATH}}` with the jar path of the module with `contains_main:
   true` — `target/<artifactId>-<version>.jar` in `single-module`, `<module-path>/
   target/<artifactId>-<version>.jar` in `multi-module`. In `multi-module`, replace
   `{{MODULE_POM_COPIES}}` with one `COPY <module>/pom.xml <module>/` line per module,
   so the dependency-resolution layer caches correctly; in `single-module`, delete that
   placeholder line — there's nothing to copy beyond the root `pom.xml` already copied
   above.
2. Write `<project>/docker-compose.yml` with the shape of
   `templates/docker-compose.yml.example`, verbatim — no substitution needed, it has no
   `{{...}}` placeholders.
3. Same rule as § andaime (step 4): the exemplar's top comment explaining *why* stays;
   anything describing the file as a template goes.

### 5 · Generate the boundary map

For each module with `forbidden_imports`, write one line per prefix in
`.claude/forbidden-imports.txt`, in the format `<module-path>|<prefix>`:

```
# Generated by /init-project — blueprint: <id> — do not edit by hand
domain|org.springframework.
domain|jakarta.persistence.
application|jakarta.persistence.
```

Don't write the literal string `blueprints/` into this header, or into any file this
step generates — step 8's autonomy test greps for exactly that string across the whole
project, and a header written that way fails the generator's own verification.

**This file is what gives `ArchHook.java check` its teeth.** Without it the hook warns
that enforcement is off, but blocks nothing. Generating it is not optional.

### 6 · Generate the root CLAUDE.md and the module ones

**Root** — write `<project>/CLAUDE.md` with the shape of
`templates/root.CLAUDE.md.example`, replacing the `{{...}}` placeholders with the
resolved data: name, versions from step 3, real build commands, and the list of
modules with their `depends_on`. Target under 200 lines: it's an index and invariants,
not a manual. No `rules/` rule is reproduced **inside** this file — only cited by path.
The rule files themselves are copied into the project in step 6.6.

**Per module** — for each module with non-empty `forbidden_imports`, write
`<module>/CLAUDE.md` with the shape of `templates/module.CLAUDE.md.example`, filled
with that module's data. The content **derives from the blueprint** — if you write it
by hand, it diverges from what the hook enforces.

### 6.5 · Generate CI

The Initializr doesn't generate CI. Write `.github/workflows/build.yml` with the shape
of `templates/ci.yml.example`, adjusted to the Java version resolved in step 3. Without
this, the `.github/workflows/*` declared in the skill's § Contract has no real
counterpart. The exemplar's comment "(see decisions/0011)" is the same dead reference as
§ 6.6/6.7/7 — cut it, the reasoning it points to has no counterpart in the project.

The `.gitignore` is **not** generated here: the Initializr already delivers a correct
one in step 3. Confirm it exists; if it's missing, write it then. It's not in `owns`
for that exact reason.

### 6.6 · Copy the rules into the project

The generated project lives on its own: nothing inside `<project>/` can depend on
`claude-spring-architect` existing on the machine of whoever clones the repository. The root
`CLAUDE.md` cites `.claude/rules/00-index.md`, and rules cite each other by path — if
the files aren't there, each citation is a silent dead end.

Copy into `<project>/.claude/rules/` **all** files from this repo's `.claude/rules/`:

| File | How to copy |
|---|---|
| `naming.md` | verbatim — the `**/*.java` glob doesn't depend on the blueprint |
| `error-handling.md` | verbatim — same |
| `code-quality.md` | verbatim — same |
| `lombok.md` | verbatim — same |
| `logging.md` | verbatim — the `**/*.java` glob doesn't depend on the blueprint |
| `testing.md` | verbatim — the `**/src/test/**` glob doesn't depend on the blueprint |
| `value-objects.md` | derived `paths:` — see § Rules with a territory |
| `api-rest.md` | derived `paths:` — see § Rules with a territory |
| `persistence.md` | derived `paths:` — see § Rules with a territory |
| `observability.md` | derived `paths:` — see § Rules with a territory |
| `messaging.md` | derived `paths:` — see § Rules with a territory |
| `architecture-ddd.md` | `paths:` derived from `architecture_paths` — see below |
| `00-index.md` | one paragraph rewritten — see below |

Verbatim means byte for byte, frontmatter included. Don't summarize, don't adapt to the
blueprint, don't cut sections: a hand-rewritten rule stops being the same rule and
diverges from the original on the next update. In the rules with derived `paths:`, the
only thing that changes is the frontmatter's `paths:` block — the body is still copied
byte for byte.

One more exception, for the same reason as `paths:`: cut any `@.claude/decisions/NNNN-
....md` citation found in the body — `value-objects.md`, `persistence.md`,
`observability.md`, and `testing.md` all carry one or more. `decisions/` never enters
the generated project (invariant 9, `@CLAUDE.md`) and, unlike a `blueprints/` citation
(§ 6.7), a decision record has no counterpart inside the project to rewrite it to.
Remove only the citing clause (e.g. a trailing "Record: `@.claude/decisions/...`." or a
parenthetical "(see `@.claude/decisions/...`)") — leave the rest of the sentence and the
rule's meaning intact. This is the same class of dead reference as `blueprints/`; § 6.7
and § 7 apply the identical cut wherever the citation resurfaces.

#### Rules with a territory — the `paths:` comes from `packages.map`, not from the original file

A rule with an identifiable territory auto-loads when someone touches files in that
territory. If the glob names a package this blueprint doesn't use, the rule **never
enters context** — and the failure mode is silent: nothing breaks, the rule simply
doesn't show up, and whoever edits a controller by hand goes without it.

The glob is never copied from the original file nor written from memory. It's derived
from the active blueprint's `packages.map`, with the same discipline `architecture_paths`
already gets:

| Rule | `packages.map` key | Glob to write |
|---|---|---|
| `api-rest.md` | the one ending in `.rest` (`adapter.in.rest`, `infrastructure.rest`, …) | `**/<value with `.` → `/`>/**` |
| `persistence.md` | the one ending in `.persistence` | `**/<value>/**` plus `**/db/migration/**`, which doesn't come from the blueprint |
| `value-objects.md` | `domain.model`, or `domain` if the blueprint doesn't declare a sub-package | `**/<value>/**/*.java` |
| `observability.md` | the one ending in `.rest` and the one ending in `.config` | one glob for each |
| `messaging.md` | the one ending in `.messaging` (`adapter.out.messaging`, `infrastructure.messaging`, …), inbound and outbound if the blueprint splits them | one glob for each; missing key → rule copied without `paths` (rule 3 below) |

Rules of the derivation:

1. **The value comes from `packages.map`, not the key.** The two coincide in every
   blueprint today, but it's the value that becomes a package on disk.
2. **Dot becomes slash.** `infrastructure.rest` → `infrastructure/rest`.
3. **Missing key, rule without `paths`.** If the blueprint has no messaging package, a
   rule about messaging is copied without `paths:` — not with an invented glob.
   Without `paths` the rule can still be cited; with a dead glob both paths are lost.
4. **No defensive union.** Write this blueprint's glob and no other. Listing the names
   of every layout was the patch that produced the divergence this step closes.

Check before moving on: for each rule with `paths`, at least one file or directory
created in step 4.7 matches at least one of the globs. A glob that matches nothing is
dead enforcement and is a failure of this step, not of the project.

**`architecture-ddd.md`** — write the `paths:` frontmatter using the active
blueprint's `architecture_paths` literally — don't invent, don't infer from
`modules[].path`. It's that `paths` that makes the rule auto-load when someone, in the
generated project, touches files in `domain`, `application`, `adapters`/
`infrastructure`, or `bootstrap` — whatever they're called for this blueprint.

```yaml
---
paths:
  - "domain/**/*.java"
  - "application/**/*.java"
  - "adapters/**/*.java"
  - "bootstrap/**/*.java"
---
```

(example for `hexagonal`; each blueprint declares its own list —
`clean-architecture-multi-module` has only 3 entries, domain/application/infrastructure,
and `clean-architecture-single-module`, being `layout: single-module`, uses package
globs instead of module folders)

**`00-index.md`** — the original explains that `architecture-ddd.md` has no `paths` of
its own because the globs come from the blueprint's `architecture_paths`, and cites
`@.claude/blueprints/_schema.md`. There's no `blueprints/` in the generated project:
replace that paragraph with a line saying the `paths` was fixed at generation time from
blueprint `<id>` and that changing it by hand disables the auto-loading. Everything
else — the map of who covers what, the loading mechanism, the table of planned rules,
the states — copy verbatim.

If you add a rule to this repo's `.claude/rules/`, add it to this table too and to the
`## Skill contract` list. A rule that exists here and doesn't reach the generated
project is exactly the failure this step closes.

### 6.7 · Copy the development skills into the project

Same reason as the previous step, applied to procedure: the root `CLAUDE.md` routes to
skills by name, and a name with no `SKILL.md` behind it makes the model search and fail
silently. Copy **only** `SKILL.md`, `templates/`, and `references/` from each
development skill into `<project>/.claude/skills/<name>/`. **Not** the entire directory
verbatim: `use-case-design/examples/` is meta-repo documentation — pipeline test
fixtures for this repository's own `/new-feature` and `java-spring-boot-developer` —
and stays out of the generated project.

| Skill | Goes to the project? | Why |
|---|---|---|
| `arch-doctor` | ✅ | Diagnoses **the project's** hooks and enforcement; the only place where running it makes sense. Prefixed name so it doesn't collide with Claude Code's native `/doctor` |
| `use-case-design` | ✅ | Designs the use case before implementing it. Only makes sense once the project exists; carries `templates/use-case-spec.md.example` and `references/scope-boundary.md` |
| `domain-modeling` | ✅ | Details domain and application for an already-designed use case. Carries `templates/domain-spec.md.example` and the twelve Java shape exemplars: seven model ones (VO, VO catalog, shared guards, aggregate, event, ports, command) and the five from the exception family, which moved here when the bootstrap stopped emitting code |
| `java-patterns` | ✅ | Design patterns during development |
| `persistence-architect` | ✅ | Designs the schema, mapping, and migrations for an already-modeled use case. Carries `templates/persistence-spec.md.example`, the three Java exemplars (entity, adapter with mapper, Spring Data interface), `V1__create_table.sql.example`, `application-persistence.yml.example`, and the two `references/` (SQL diagnosis and external links) |
| `rest-api-architect` | ✅ | Designs the endpoints, DTOs, error map, and OpenAPI contract for an already-modeled use case. Carries `templates/rest-spec.md.example`, the six Java shape exemplars (annotated controller, DTOs, mapper, `PageResponse`, `Idempotency-Key` interceptor, `ApiExceptionHandler`), the two JSON fixtures (`error-responses`, `page-response`), and `references/best-practices-links.md`. The contract test exemplar lives in `test-architect` |
| `test-architect` | ✅ | Two modes: design (the `40-testes.md` partial, per use case, inline) and setup (installs ArchUnit, once per project, delegated to the `archunit-installer` agent — see 6.8). Carries `templates/{test-spec.md,ArchitectureTest.java,TestFixtures.java,DomainTest.java,UseCaseTest.java,ControllerTest.java,PersistenceIT.java}.example` and `references/best-practices-links.md`. It's the sole owner of test-code shape — no other skill carries a test exemplar |
| `new-feature` | ✅ | Orchestrates the six skills above into a single `UC-NNN-spec.md`. Only makes sense once the project exists; carries `templates/feature-spec.md.example`. Its own `## Entry into generated projects` section already documents it travels here |
| `docker-architect` | ✅ | Extends `docker-compose.yml`/`Dockerfile` after the base pair exists, chained by `persistence-architect`/`test-architect`/`messaging-architect` or invoked by hand. Only makes sense once the project (and the base pair from 4.10) exists. Carries `templates/{postgres,mysql,kafka}-service.yml.example` |
| `messaging-architect` | ✅ | Designs the Kafka producer/consumer adapter, topic, delivery semantics, and retry/DLQ for an already-modeled domain event. Carries `templates/messaging-spec.md.example`, the two Java exemplars (producer adapter, consumer adapter), and `application-kafka.yml.example` |
| `project-bootstrap` | ❌ | Builds the project. Inside it there's nothing left for it to do, and it invites the model to re-generate on top of live code |
| `init-project` | ❌ | Same reason: it's the creation ritual, not the maintenance one |
| `claude-code-architect-designer` | ❌ | Designs **this** repository's own extensions — skills, agents, rules. Whoever clones an already-generated project has no extensions to design, and the invariants it applies are this repo's |

Three corrections during the copy, because the generated project has neither
`blueprints/` nor `decisions/`:

1. `java-patterns/SKILL.md`, `use-case-design/SKILL.md`, `domain-modeling/SKILL.md`,
   `rest-api-architect/SKILL.md`, and `messaging-architect/SKILL.md` say **Reads** `...
   and the active blueprint's packages.map`. Replace with: the project's packages,
   documented in the root `CLAUDE.md` and the module `CLAUDE.md` files. In
   `use-case-design`, the same correction applies in procedure step 5 and in the "Active
   blueprint" line of `templates/use-case-spec.md.example`.

   This list has gone stale before — silently, since a rule/skill citing a dead path
   breaks nothing at generation time, only for whoever reads it in the generated
   project. Before moving to step 6.8, confirm the list above is exhaustive:

   ```bash
   grep -rl "active blueprint's \`packages.map\`" \
     .claude/skills/{arch-doctor,use-case-design,domain-modeling,persistence-architect,java-patterns,rest-api-architect,test-architect,messaging-architect}/
   ```

   Every file this returns needs the same correction, whether or not it's named above.
2. Any other citation to `@.claude/blueprints/**` in a copied skill points to nothing.
   Rewrite it to the equivalent source inside the project, or cut the sentence. Don't
   leave the dead path there.
3. Every citation to `@.claude/decisions/NNNN-....md` in a copied skill's `SKILL.md`,
   `templates/`, or `references/` — same exception as § 6.6, same fix: cut the citing
   clause, don't rewrite it, since there's no equivalent inside the project. Known
   carriers today: `domain-modeling/SKILL.md`, `test-architect/SKILL.md`,
   `rest-api-architect/SKILL.md` and its two templates
   (`ApiExceptionHandler.java.example`, `IdempotencyKeyInterceptor.java.example`),
   `use-case-design/SKILL.md`, `java-patterns/SKILL.md`, `persistence-architect/SKILL.md`,
   `new-feature/SKILL.md`, `docker-architect/SKILL.md` (its `## Why this is a
   skill` section), and `messaging-architect/SKILL.md` (its `## Why this is a skill and
   not a subagent` section). Confirm the list is still exhaustive the same way as point 1:

   ```bash
   grep -rl "decisions/" \
     .claude/skills/{arch-doctor,use-case-design,domain-modeling,persistence-architect,java-patterns,rest-api-architect,test-architect,new-feature,docker-architect,messaging-architect}/
   ```

The rest of the content — including `disable-model-invocation` and `allowed-tools` —
copies verbatim. Skills the root `CLAUDE.md` might route to that don't exist yet stay
**out** of step 6's routing table, as that step already requires.

### 6.8 · Copy the development agents into the project

Same reason as steps 6.6 and 6.7: the project is self-contained. If the root
`CLAUDE.md` delegates a task to an agent by name, and the agent isn't copied, the
delegation is a silent dead end.

Copy the **entire file** of each development agent — `<name>.md` — into
`<project>/.claude/agents/<name>.md`.

| Agent | Goes to the project? | Why |
|---|---|---|
| `java-spring-boot-developer` | ✅ | Reads `UC-NNN-spec.md` specs generated in the project and implements code. Only makes sense once the project exists and has designed use cases. Name recorded in D20 |
| `archunit-installer` | ✅ | Invoked by `test-architect`'s setup mode, in the project, the same as in this repository. Without the copy, setup mode in the generated project delegates to an agent that doesn't exist. Name recorded in D30 |
| `project-initializer` | ❌ | Creation ritual; nothing for it to do inside an already-generated project. Whoever clones the project doesn't create new projects from it — it's used as a base, not as a template generator |

Verbatim — full frontmatter and content, no rewrites. Agents don't depend on
`blueprints/` (unlike some skills), so there are no citations to rewrite.

The rest of the content — `model`, `tools`, `disallowedTools`, `effort` — copies as-is.
Agents the root `CLAUDE.md` might delegate to that don't exist yet stay **out** of the
copy table.

---

### 7 · Install hooks

Three parts, and the first one is easy to forget:

1. Copy `.claude/hooks/ArchHook.java` (from this repo) to
   `<project>/.claude/hooks/ArchHook.java`, verbatim except for the `// .claude/decisions/
   0001-schema-frontmatter-extensions.md` comment near line 220 — same exception as
   § 6.6/6.7: cut that citation, `decisions/` has no counterpart in the project. That's
   what the `settings.json`'s `${CLAUDE_PROJECT_DIR}/.claude/hooks/ArchHook.java` points
   to — without the copy, the hooks fail to start on any machine that doesn't have this
   repository.
2. Copy `.claude/schemas/extensions.json` (from this repo) to
   `<project>/.claude/schemas/extensions.json`, verbatim except for the trailing
   "Design and rationale: .claude/decisions/0001-schema-frontmatter-extensions.md" clause
   in its `$comment`, cut for the same reason. `ArchHook`'s `schema` mode reads this
   file; without it, it switches off with a warning and nothing gets validated. Copying
   the hook and forgetting the schema delivers enforcement that looks on and isn't.
3. Merge `templates/settings.json.example` into `<project>/.claude/settings.json`,
   preserving what's already there. The template already brings the `schema` mode's
   three triggers (`PreToolUse`/Write, `PostToolUse`/Edit, and `Stop`).

There's no `chmod`: the hooks run in **exec form** (`command: java` + `args`), without
a shell and without an execute bit — that's what makes them identical on Linux, macOS,
and Windows.

### 8 · Verify

The `starter.tgz` from step 3 **already brings** `mvnw`, `mvnw.cmd`, and
`.mvn/wrapper/` — don't run `mvn -N wrapper:wrapper`. Confirm the wrapper exists and
works before anything else:

```bash
./mvnw -v                       # failure here = starter.tgz didn't extract the wrapper
./mvnw -q clean test-compile    # boundaries + Checkstyle (validate phase)
./mvnw -q test                  # Initializr smoke test — only the `*Test`s
./mvnw -q checkstyle:check      # Checkstyle only, to isolate violations
./mvnw clean verify             # includes the `*IT`s via failsafe
```

In `verify`, the freshly generated project **has no `*IT` at all** — it doesn't
generate business code, so it doesn't generate integration tests. Zero ITs discovered
is the expected result here, unlike what held when this step used to generate
examples. What you're confirming is that `maven-failsafe-plugin` made it into the
POM: without it, the first `*IT` that `test-architect` designs compiles and never runs.

```bash
grep -c maven-failsafe-plugin pom.xml    # must be ≥ 1
```

Boundary test, mandatory before reporting success: temporarily write
`<domain-module>/src/main/java/<domain-package>/ArchHookProbe.java` with an
`import org.springframework.stereotype.Component;`, confirm the hook blocks the write,
and delete the file if one was left. If it doesn't block, `forbidden-imports.txt` is
wrong. The probe is the only business class this procedure ever writes, and it exists
only for the duration of the test.

`lombok.config` test, for the same reason and the same way: temporarily put a
`@Setter` with a field on the `@SpringBootApplication` class — the only one that
exists —, run `./mvnw -q test-compile`, confirm the compilation stops with
`Use of @Setter is flagged according to lombok configuration`, and remove it. Compiling
green means the file isn't at the root, or `flagUsage` didn't make it in — the
`lombok.md` rule would run with no enforcement at all.

If any test depends on Testcontainers (Spring context with a real DB), guard it with a
condition that checks the Docker daemon (e.g. `@EnabledIf("dockerAvailable")`). Without
that, `./mvnw test` goes red on any machine without local Docker — a false negative
that says nothing about the code. The test actually runs in CI, where the runner has a
daemon.

If the build fails, **fix it before reporting**. A bootstrap that delivers a red build
isn't finished.

**Autonomy test**, also mandatory: nothing in `<project>/` can cite a path that only
exists in `claude-spring-architect`.

```bash
ls .claude/rules/ .claude/skills/ .claude/agents/ .claude/hooks/ArchHook.java .claude/schemas/extensions.json
grep -rn "blueprints/" .claude/ CLAUDE.md */CLAUDE.md   # must return nothing
grep -rn "decisions/" .claude/ CLAUDE.md */CLAUDE.md    # must return nothing — § 6.6/6.7/7 cut these
java .claude/hooks/ArchHook.java schema </dev/null      # must exit 0, and without warning
```

The `schema` check above fails two different ways: exits 2 if some copy ended up with
invalid frontmatter, and exits 0 **with a warning** if `extensions.json` didn't make it
into the project. Read the output, not just the exit code.

Every skill the root `CLAUDE.md` routes to must have `SKILL.md` in the project. Every
agent the root `CLAUDE.md` delegates to must have `<name>.md` in `.claude/agents/`. A
dead path here is exactly the failure steps 6.6, 6.7, 6.8, and 7 exist to prevent.

## Output contract

```
✅ Project <name> created — blueprint <id>

Modules:
  <path> → depends on <list>
  ...

Versions (resolved by the Initializr): Java <x> · Spring Boot <y> · <build tool>
Active features: <list>
Business code: none — by design. <n> package-info.java written
Boundaries: <n> rules in .claude/forbidden-imports.txt — blocking verified ✓
Checkstyle: config/checkstyle/checkstyle.xml — plugin <v> · tool <v>, validate phase
Lombok: lombok.config at the root — @Data and @Setter stop compilation
ArchUnit: to be installed — `test-architect` skill (see Next steps)
Coverage: JaCoCo generates a report; the 80%/70% gate comes in with `test-architect`
Self-contained: <n> rules + <n> skills + <n> agents + ArchHook.java + extensions.json copied — no dead paths ✓
Docker: base Dockerfile + docker-compose.yml (app service only) — extend with `docker-architect` when a feature needs one
Build: <PASSED | FAILED: reason>

Next steps:
  1. /use-case-design <first-use-case-name>
  2. Install the architecture tests (ArchUnit) and wire up the coverage gate with the
     `test-architect` skill as soon as business classes exist. Until then boundaries
     are guaranteed only by the hook (inside Claude Code)<, and by the POMs'
     `depends_on` (in the build) — only if `layout: multi-module`>.
```

The part between `<>` in line 2 only appears if the blueprint is `layout:
multi-module`. In `single-module` there's no per-layer POM and the compiler enforces no
boundary at all: outside Claude Code the project has no enforcement whatsoever until
ArchUnit is installed. Write that, in those words — the generic sentence promises a
build guarantee that doesn't exist.

The ArchUnit and coverage lines aren't optional: without them the user ends up thinking
`code-quality.md`, `architecture-ddd.md`, and `testing.md` already have full automatic
verification. They have part of it — Checkstyle and `lombok.config`. The other two come
in with `test-architect`, and until they do, `verify` checks neither dependency
direction nor coverage.

Neither is the business-code line: a user used to scaffolds expects to find an example
controller. Writing "none — by design" is what keeps them from looking for it and
concluding the generation failed.

## Applicable rules

All rules in `.claude/rules/` are master content: they guide the generation **and**
are copied into the project in step 6.6, so it lives without this repository.

Architecture: `@.claude/rules/architecture-ddd.md` — copied with `paths` coming from
the blueprint's `architecture_paths`; the rest go verbatim.
Naming: `@.claude/rules/naming.md`
Error handling: `@.claude/rules/error-handling.md` — doesn't guide any step since the
exception family moved to `domain-modeling` (see 4.5), but is copied.
Testing: `@.claude/rules/testing.md` — also doesn't guide: the coverage gate it sets is
wired by `test-architect` (see 4.6), not here.
Code quality: `@.claude/rules/code-quality.md` — SOLID and Clean Code limits; the
mechanical half becomes Checkstyle in step 4.6. The other half (ArchUnit) belongs to
the `test-architect` skill.
REST API: `@.claude/rules/api-rest.md` — doesn't guide any bootstrap step (no
`ApiExceptionHandler` is generated here, see 4.5), but is copied so the project is
complete.
Lombok: `@.claude/rules/lombok.md` — the mechanical half (forbidding `@Data` and
`@Setter`) becomes the root `lombok.config` in step 4.8.

Don't reproduce these rules here. If you need one that doesn't exist in `rules/`,
create the rule file first — don't write it inside this skill.

## Skill contract

**Reads before generating** — none of these auto-load at this point: the directory
doesn't have any `.java` files touched yet, so `naming.md` and `error-handling.md`'s
`paths` don't trigger. Read them explicitly before step 4:

- `.claude/blueprints/<id>/<id>.yaml` — the blueprint chosen in step 1
- `.claude/rules/00-index.md`
- `.claude/rules/architecture-ddd.md`
- `.claude/rules/naming.md`
- `.claude/rules/error-handling.md`
- `.claude/rules/code-quality.md`
- `.claude/rules/api-rest.md`
- `.claude/rules/lombok.md`
- `.claude/rules/value-objects.md`
- `.claude/rules/persistence.md`
- `.claude/rules/testing.md`
- `.claude/rules/observability.md`
- `.claude/rules/messaging.md`

All of them are read, not just the ones that guide the generation: step 6.6 copies
them into the project, and a partial copy isn't the rule.

**Writes** (paths relative to the **generated project**, not this repository) — no
other skill touches these files:

- `pom.xml` and `*/pom.xml`
- `CLAUDE.md` and `*/CLAUDE.md`
- `.claude/forbidden-imports.txt`
- `.claude/rules/*.md` — full copy of this repo's rules, see step 6.6
- `.claude/skills/{arch-doctor,use-case-design,domain-modeling,persistence-architect,java-patterns,rest-api-architect,test-architect,new-feature,docker-architect,messaging-architect}/**` — see step 6.7
- `Dockerfile` and `docker-compose.yml` — base pair only, step 4.10. Every service added
  afterward is `docker-architect`'s, not this skill's
- `.claude/hooks/ArchHook.java` — verbatim copy, see step 7
- `.claude/schemas/extensions.json` — verbatim copy, see step 7
- `.claude/settings.json` — merge, see step 7
- `config/checkstyle/checkstyle.xml`
- `lombok.config`
- `src/main/resources/application*.yml`
- `src/main/java/**/package-info.java` — one per role in `packages.map`, step 4.7. **No
  other `.java`**: business classes come from the `/new-feature` pipeline
- `.github/workflows/*`

`.gitignore` is deliberately left out — it comes from the Initializr (step 6.5).

**Hands off to** `use-case-design`, `domain-modeling`, `persistence-architect`,
`rest-api-architect`, and `test-architect`, which design the first feature. The
architecture tests and the coverage gate belong to `test-architect`'s setup mode —
this bootstrap deliberately doesn't generate them.

**Does not write business code, and this is the § of the contract that says so.** No
`.java` beyond the `package-info.java` files, no `.sql`, no file in `src/test/**`. If a
future step needs to emit a class, the right question is which layer skill owns that
shape — not how to add one more exemplar here.
