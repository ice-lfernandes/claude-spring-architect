# 0091 · SonarQube analysis is configured by one skill, chained from project creation and from adoption

- **Date:** 2026-09-30
- **Scenario:** "quero adicionar uma nova skill para ser injetada no agent java-spring-boot-developer de boas praticas de codigos java baseado em regras do sonarqube" — revised mid-interview to: "na verdade nao há configuracao de sonarqube para avaliacao real. Ao invés de criar algo antecipado, vamos inserir a configuracao do sonarqube na skill que cria o projeto do zero e na atualizacao do arch-adopt, caso o projeto nao tenha. Durante a configuracao do sonarqube, pergunte se ja existe alguma url e/ou autenticacao, se nao existir, deve-se adicionar no docker-compose do projeto a imagem do sonar"
- **Decision:** option 1 — Form 1, `.claude/skills/sonarqube-setup/SKILL.md`, chained by `project-bootstrap` step 8.4 and `arch-adopt` step 7
- **State:** approved by Lucas Fernandes, 2026-09-30 — CI step only for an external server; local container on embedded H2
- **Goes to the generated project:** yes — `export.skills.include`

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | Seen in review of generated code: resources/streams, duplicated literals, complexity/style. No SonarQube runs anywhere, so nothing measures it | The original ask — a Sonar rule catalog injected at `SubagentStart` — dropped by the user as anticipation: a catalog with no analyzer behind it is persuasion nobody can check |
| 1b — Budget | The `SubagentStart` injection already renders 5 403 of its 9 000 `max_chars` (runtime cap 10 000); the whole Sonar way profile (~500 Java rules) cannot fit | Any variant of the injection covering "Sonar way inteiro" |
| 2 — Trigger | Chained at project creation (`project-bootstrap`) and at adoption/update (`arch-adopt`) when the project has no Sonar setup | Form 4/5 (not a fact), Form 7 (not a lifecycle event) |
| 5 — Nature | Sequence of steps with a question (server URL / auth exist?) and a branch (external server vs local container) | Forms 4 · 5 |
| 6 — Isolation | None of invariant 5's three reasons applies. Context: the output is one `curl` line and two or three edits — no `./mvnw`, so no build log to isolate (the reason `archunit-installer` and `commons-logging-installer` exist). Tools: the `guard` territory already restricts writes. Model: `sonnet`, same as the callers. The procedure also opens with the question that decides everything after it, while an installer receives a finished answer — the split `test-architect` makes between its inline design mode and its setup agent. Verifying with a build (`./mvnw verify sonar:sonar`) was offered and declined at approval; adding it later would bring the build log and, with it, a `sonarqube-installer` agent | Form 3 |
| 8 — Destination | Generated project only | Registration in this repo |
| 9 — Integration | `pom.xml`/`build.gradle` (bootstrap owns at generation; executor owns dependency lines in a feature round), `docker-compose.yml` (`docker-architect` owns every service block), `ci.yml` (bootstrap's template), `arch-adopt` territory is `.claude/**` + `.gitignore` and "writes nothing by itself" | Any option that writes a compose service outside `docker-architect`, or widens `arch-adopt` |
| 12 — Credential | Token is a secret: env var / CI secret only. Host URL is not a secret | Any literal token in `pom.xml`, `build.gradle`, `ci.yml` |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | New skill `sonarqube-setup` (Form 1, class `build`), chained by `project-bootstrap` and `arch-adopt`; compose service delegated to `docker-architect` with a new template | 8 | **Approved** |
| 2 | Procedure inside `project-bootstrap` (new step + reference), `arch-adopt` reads it from `$SRC` and writes pom/compose itself | 4 | Rejected — two owners of the procedure and of compose (invariant 2) |
| 3 | `sonarqube` as a blueprint `feature` with `templates/features/sonarqube/` | 3 | Rejected — Sonar is not architecture; no update path |
| 4 | Create nothing — each project configures Sonar by hand | 2 | Rejected — requested explicitly; nothing measured |

### Option 1 — `.claude/skills/sonarqube-setup/SKILL.md` (score 8)

**Motivator:** axes 2 + 9 — two callers need the same procedure, and one of them
(`arch-adopt`) runs inside a project where `project-bootstrap` does not exist (it is in
`export.skills.exclude`). A single skill that travels is the only owner both can reach.

**Mechanism.**

1. **Entry rule — idempotent.** Build file already declares the scanner plugin
   (`sonar-maven-plugin` / `org.sonarqube`) → report "already configured" and stop.
2. **Interview (one `AskUserQuestion`).** Existing SonarQube/SonarCloud server?
   - **Yes** → URL (written as the `sonar.host.url` property — not a secret), organization
     when SonarCloud, and the **name** of the token variable (default `SONAR_TOKEN`) — never
     its value.
   - **No** → chain `docker-architect` to add a `sonarqube` service from a new template;
     host URL `http://localhost:9000`; the report says how to create the first token in
     the UI.
3. **Build.** Maven: `sonar-maven-plugin` in `pluginManagement` of the root POM, version
   resolved from `repo1.maven.org` `maven-metadata.xml` (same oracle as
   `project-bootstrap/references/build-maven.md`), properties `sonar.projectKey`
   (`groupId:artifactId`), `sonar.host.url`. No `sonar.coverage.jacoco.xmlReportPaths`:
   the scanner's default paths are where the build's JaCoCo already writes. Gradle:
   `org.sonarqube` plugin (Gradle Plugin Portal), same properties in a `sonar {}` block.
4. **CI.** Only when an external server exists: a step in `.github/workflows/build.yml`
   running the scanner with `SONAR_TOKEN` from `secrets`, skipped when the secret is absent
   (fork PRs). A local-container server is unreachable from CI, so no step.
5. **Territory** (`skill_classes.build.overrides.sonarqube-setup.write_allow`):
   `pom.xml`, `build.gradle`, `.github/workflows/**`. No compose path — that is
   `docker-architect`'s.

**Callers.**
- `project-bootstrap`: step 8.4, after Verify and before the README (8.5), so the
  README's report carries the `SonarQube:` line. Placed last (8.7) at first, to dodge the
  guard keeping only the caller's territory on a same-class chain; that rule was the bug
  decision 0092 fixed, and the step moved once the territories summed.
- `arch-adopt`: new step after 6 · Verify — detect the plugin in the build file; missing →
  offer `sonarqube-setup`. Install case: `.claude/skills/` did not exist at session start,
  so the report asks for `/reload-skills` first (`@claude-help.md` § Live detection).
  `arch-adopt`'s own territory stays `.claude/**` + `.gitignore`.

**Local server — embedded H2.** Asked at approval. No `SONAR_JDBC_URL` means the image
uses its built-in H2, so no second service. Evaluation-grade (no upgrade path; a new tag
starts empty) — right for local analysis, wrong for a shared server, which is the
external branch anyway. Tag `sonarqube:26.9.0.129388-community` confirmed on the Docker
Hub tags API; `curl` confirmed in the upstream Dockerfile for the healthcheck.

**Pros:** one owner for Sonar config; compose stays with `docker-architect`; `arch-adopt`
unchanged in what it writes; travels into the project, so a project generated before this
can adopt it; versions resolved, image tag verified — nothing from memory.

**Cons:** a third writer of `pom.xml` outside feature rounds (alongside bootstrap and the
installers) — needs a pitfalls line. Form 1 means the description loads every session in
the generated project (a few hundred tokens). A chained skill can be skipped by the model
— persuasion, accepted: nothing observed yet justifies a hook.

**Points cut in the rubric:** criterion 3 (description always loaded); criterion 6
(first skill `arch-adopt` chains).

### Option 2 — procedure inside `project-bootstrap` (score 4)

`arch-adopt` would have to read a `project-bootstrap` reference from `$SRC` and write
`pom.xml` and `docker-compose.yml` itself: widens its territory, breaks "writes nothing by
itself", and writes a compose service outside `docker-architect` — invariant 2 (two owners
of the same procedure, two owners of compose). Capped at 4.

### Option 3 — blueprint feature `sonarqube` (score 3)

`features` decides dependencies per architecture; Sonar is orthogonal to architecture, and
a feature cannot carry the "server exists?" question. `arch-adopt` does not re-run feature
generation, so the update path is uncovered.

### Option 4 — create nothing (score 2)

The user asked for it explicitly, and without an analyzer the quality concerns seen in
review stay unmeasured.

## References

| Claim | Source |
|---|---|
| `SubagentStart` `additionalContext` cap 10 000, current injection 5 403 chars | `.claude/decisions/0077-pattern-catalog-injected-at-subagent-start.md`; `echo '{"agent_type":"java-spring-boot-developer"}' \| java -jar .claude/hooks/ArchHook.jar context subagent \| wc -c` |
| A new `skills/` directory needs `/reload-skills`; an edited one is picked up live | `@claude-help.md` § Skills — Live detection |
| A skill the model can't see can't be chained by a sibling | `.claude/skills/docker-architect/SKILL.md` § How it's invoked |
| `docker-architect` owns every compose service block | same file, § Boundary with neighboring pieces |
| `arch-adopt` territory `.claude/**` + `.gitignore`; writes only through `export` | `.claude/skills/arch-adopt/SKILL.md` § Contract; `skill_classes.build.overrides` |
| `project-bootstrap` is not exported | `export.skills.exclude` in `.claude/schemas/extensions.json` |
| Plugin versions come from `maven-metadata.xml` | `.claude/skills/project-bootstrap/references/build-maven.md` § 4.6 |
| Image tag verified in the registry, never inferred | `.claude/skills/docker-architect/SKILL.md` step 4 |
| No literal secret in a versioned file | `@CLAUDE.md` invariant 11 (scoped to `.mcp.json`; the same reasoning applied here) |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/sonarqube-setup/SKILL.md` | new — Form 1, class `build`, `sonnet`/`low` |
| `.claude/skills/sonarqube-setup/templates/` | `pom-sonar.xml.example`, `build-gradle-sonar.example`, `ci-sonar-step.yml.example` |
| `.claude/skills/docker-architect/templates/sonarqube-service.yml.example` | new — community tag, embedded H2, healthcheck on `/api/system/status` |
| `.claude/skills/docker-architect/SKILL.md` | chained-from-`sonarqube-setup` path in step 2 and § How it's invoked; § Service catalog row |
| `.claude/skills/project-bootstrap/SKILL.md` | step 8.4; `SonarQube:` line and next step 3 in the output contract; `Chains` in § Contract; `Skill` in `allowed-tools` |
| `.claude/skills/arch-adopt/SKILL.md` | step 7 (detect scanner, chain, `/reload-skills` on install); report line; `Chains` in § Contract; `Skill` in `allowed-tools` |
| `.claude/schemas/extensions.json` | `skill_classes.build.skills` + `overrides.sonarqube-setup.write_allow` (`pom.xml`, `build.gradle`, `.github/workflows/**`); `export.skills.include` |
| `CLAUDE.md` | routing row |
| `docs/pt-br/11-pitfalls.md`, `docs/en/11-pitfalls.md` | ownership of the `sonar.*` lines outside a feature run |
| `docs/*/01-*.md`, `docs/*/02-init-project.md`, `docs/*/10-arch-adopt.md`, `README.md` | class table, step 8.4, arch-adopt's chained step, skill lists |

Goes to the generated project: **yes** — `export.skills.include`; `docker-architect`'s new
template travels with it. Written by the main thread, not delegated: axis 8 was
"generated project only", not "both".
