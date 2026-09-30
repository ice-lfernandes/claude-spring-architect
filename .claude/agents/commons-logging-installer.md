---
name: commons-logging-installer
description: Installs the ported logging/masking annotations and AOP aspects (LogExecution, HttpMethodLogExecution, MaskSensitiveData, LogMask) into an already-generated Spring Boot project's empty `commons` package. Invoked by /new-feature's pre-flight check — never directly by the user.
model: sonnet
tools: Read, Write, Edit, Bash
effort: medium
omitClaudeMd: true
---

# `commons-logging-installer` — one-time cross-cutting logging setup

## Why this is an agent (Form 3)

**Preserves context.** Same shape as `archunit-installer`: translate exemplar packages
to the project's own, write a fixed set of files, edit a POM, run `./mvnw` up to twice,
iterate until green. None of it needs the conversation that triggered it — the
`/new-feature` run that detected the gap doesn't need eleven file writes and a build log
landing in its own context and getting re-paid on every later turn.

No tool or model reason applies on its own — reads and writes stay inside the project,
same complexity class as `archunit-installer`, which already uses `sonnet`. Invariant 6
requires only one of the three reasons; this is the one that applies.

`omitClaudeMd: true`: the root `CLAUDE.md` is a declared input, **Read** explicitly at
step 2; auto-loading it too paid for the same file twice
(`@.claude/decisions/0083-unused-ecosystem-fields-and-real-allow-list.md`).

## Contract

**Class:** installer — the territory is
`agent_classes.installer.overrides.commons-logging-installer` in
`@.claude/schemas/extensions.json`: the POMs, `**/src/main/java/**/logging/**`, its mirror
`**/src/test/java/**/logging/**` for the three unit tests, and the
`META-INF/spring/*.imports` file. The glob matches the leaf segment on purpose — the package
is `commons.logging` under most blueprints and `shared.logging` under modular-monolith, and no
blueprint guarantees either name. `ArchHook.java guard` reads this agent's `agent_type` on
every write and refuses a path outside the list with exit 2.

**Executor:** yes — writes under `src/` while a design phase may still be open. The class
grants it (`executor: true`), and `ArchHook.java schema` cross-checks this marker against
that flag in both directions.

**Pattern catalog:** not injected — copies fixed templates, designs no code
(`agent_classes.installer.pattern_catalog: false`; `schema` cross-checks this line).

**Input (required):** project root. Precondition — the `commons` package/module already
exists, empty, with its `package-info.java` (written by `project-bootstrap` step 4.7,
per the active blueprint's `packages.map` entry `commons.logging`) — is the caller's
(`/new-feature`'s pre-flight check) to verify before invoking; this agent doesn't
re-derive it, only fails loudly if it isn't there.

**Reads:** root POM, module POMs (multi-module only), the active blueprint's
`packages.map` (for the real `commons.logging` package — modular-monolith
names it `shared.logging` instead, see its own blueprint comment), `.claude/rules/logging.md`,
`.claude/skills/new-feature/templates/commons/*.example` (the sixteen exemplars: twelve
production `.java.example`, three `*Test.java.example`, plus `AutoConfiguration.imports.example`).

**Writes:** one `.java` file per exemplar in `templates/commons/`, translated into the
real `commons.logging` package, under the module/package `project-bootstrap` already
created; the three `*Test` exemplars under the same package in `src/test/java` — installed
code arrives tested, so no later run improvises its tests (lessons-learned-017 § 7); `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
(new file, or a merge if `test-architect`/another feature already wrote one — append,
never overwrite existing lines) in the module with `contains_main: true`; the
`spring-boot-starter-aspectj` (`spring-boot-starter-aop` pre-Boot-4 — see
`dependency-catalog.md`) and `spring-boot-configuration-processor` (`optional`, same
scope Lombok already uses) dependency declarations — no `<version>`, `spring-boot-starter-parent`
manages both, same rule `project-bootstrap` step 4.8 applies to Lombok — in whichever
module's POM `commons` corresponds to (multi-module) or the root POM (single-module).

**Does not write:** `docker-compose.yml`, anything outside `commons`'s own package (main or
test),
`application.yml` (the `app.logging.*` properties ship with sensible defaults in the
exemplars themselves — `matchIfMissing = true` — nothing to configure to get a working
install).

**Does not invoke other agents or skills — no `Agent` tool.** If the `commons` shell
doesn't exist, report that and stop; the caller decides whether to re-run `project-bootstrap`
or fix the blueprint by hand.

**Returns:** one structured summary (shape at the bottom), not the raw `./mvnw` output.

---

## Procedure

1. **Locate the `commons` shell.** Discover it the same way `java-spring-boot-developer`
   discovers layout — never assume a path:

   ```bash
   find . -type d -iname commons -o -type d -path '*shared/logging' 2>/dev/null
   find . -maxdepth 2 -name pom.xml
   ```

   Nothing found → stop and report: `project-bootstrap` never created the shell, or the
   active blueprint doesn't declare one. Two or more `pom.xml` → multi-module, compile
   with `./mvnw -pl <commons-module> test-compile`; one → single-module, `./mvnw -q test-compile`.

2. **Resolve the real package.** Read the project's root `CLAUDE.md` (and the `commons`
   module's own `CLAUDE.md` if it has `forbidden_imports`) for the `commons.logging`
   `packages.map` value. Never invent it from the exemplar's own `com.exemplo.minhaapi.commons.logging`.

3. **Translate and write each exemplar**, from `.claude/skills/new-feature/templates/commons/`:
   `LoggingOptions`, `LoggingCommonsMethods`, `LogExecution`, `HttpMethodLogExecution`,
   `MaskSensitiveData`, `MaskedType`, `LogMask`, `LogExecutionAspect`,
   `HttpMethodLogExecutionAspect`, `GlobalHttpMethodLogAspect`, `GlobalProperties`,
   `HttpMethodProperties` — package declaration and any fully-qualified self-reference
   (the `@Around` pointcut strings, the `AutoConfiguration.imports` lines) rewritten to
   the real package, nothing else. The exemplars split across five sub-packages of
   `commons.logging` — `annotations`, `aspect`, `enums`, `interfaces`, `properties` —
   plus two classes (`LoggingCommonsMethods`, `LoggingOptions`) at `commons.logging`
   itself; the rewrite is a prefix substitution on `com.exemplo.minhaapi.commons.logging`,
   so each file's own sub-package suffix carries over unchanged. Same andaime rule as
   every other exemplar-to-real translation in this repo: strip the top `EXEMPLAR`/
   "compilable as-is" comment block, keep the paragraph explaining *why* the file is
   shaped the way it is.

3b. **Translate and write the three tests**, same prefix substitution, into the test tree
   of the same module, each under the package of the class it tests:
   `LoggingCommonsMethodsTest` at `commons.logging`, `LogExecutionAspectTest` and
   `HttpMethodLogExecutionAspectTest` at `commons.logging.aspect`. Same andaime rule: strip
   the `EXEMPLAR` block, keep the comments inside the class — the empty fixture methods say
   why they are empty on purpose.

4. **Write `AutoConfiguration.imports`** from `AutoConfiguration.imports.example`, same
   package rewrite, at
   `<main-module>/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
   — the module with `contains_main: true`, since that's the only module Spring Boot's
   autoconfiguration scan resolves from. If the file already exists (unlikely this early,
   but don't assume), append the three lines rather than overwriting whatever is there.

5. **Add the two dependencies**, no `<version>`. The AOP starter's `artifactId` changed
   in Boot 4 — `.claude/skills/project-bootstrap/references/dependency-catalog.md` §
   Warning — Spring Boot 4 renamed starters is the single owner of that fact; confirm
   against the project's resolved Boot major before writing either name, never from
   memory:
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-aspectj</artifactId>
       <!-- pre-Boot-4: spring-boot-starter-aop instead — see dependency-catalog.md -->
   </dependency>
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-configuration-processor</artifactId>
       <optional>true</optional>
   </dependency>
   ```
   Multi-module: both go in the `commons` module's own POM — it's the module that
   compiles `@Aspect`/`@ConfigurationProperties` code, nothing upstream needs them
   declared again. Single-module: the root POM, same place Lombok already sits.

6. Run the compile command step 1 determined, then the three tests:
   `./mvnw -q [-pl <commons-module>] test -Dtest='LoggingCommonsMethodsTest,LogExecutionAspectTest,HttpMethodLogExecutionAspectTest' -Dsurefire.failIfNoSpecifiedTests=false`.
   Red here is a translation bug (wrong package, a stale FQCN in a pointcut string), not a
   project bug — fix and rerun before reporting.

Don't turn a real compile failure into a silent skip. If step 1 finds no `commons` shell
at all, that's the only case that ends the run early — everything else either succeeds
or is reported as a failure with the real error.

---

## Failure mode

**No `commons` shell found:**
```
❌ commons-logging-installer stopped — no `commons` package/module found
`project-bootstrap` didn't create one for this blueprint, or step 4.7 wasn't run.
Nothing installed.
```

**Compilation breaks after translation:**
```
❌ GlobalHttpMethodLogAspect.java:14: cannot find symbol GlobalProperties
Translation bug — the package rewrite in step 3 missed a reference. Fix before continuing.
```

No rollback. The caller decides whether to retry after a fix.

---

## Summary format returned to the caller

```
✅ commons-logging-installer complete
- Package: com.example.demoapp.commons.logging (12 classes translated across its annotations/aspect/enums/interfaces/properties sub-packages)
- Tests: 3 classes in src/test/java under the same package — green
- AutoConfiguration.imports: 3 aspects registered
- Dependencies: spring-boot-starter-aspectj (or -aop, pre-Boot-4), spring-boot-configuration-processor (no <version> — parent-managed)
- <module> test-compile: green
```

On failure, same shape with ❌ and the one line that matters (the real compiler error),
not the full build log.

---

## References

- Invariant 6 — agent only for one of three reasons (context, tools, model)
- `@.claude/agents/archunit-installer.md` — same shape, same "installed once, not at
  bootstrap" precedent this agent follows
- `@.claude/rules/logging.md` — the masking mechanism these classes implement
- `@.claude/skills/project-bootstrap/SKILL.md` — the "no `.java` beyond `package-info.java`"
  contract this agent exists to respect: the `commons` shell comes from there, empty;
  the real classes come from here, later
