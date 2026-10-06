# 0124 · One `DB_*` convention from `.env` to `application.yml`, and `compose` checks that the application logs in to the service it reaches

- **Date:** 2026-10-06
- **Scenario:** `.claude/lessons-learned/lessons-learned-021.md`: the first host run of
  `banking-app` died at startup. The generated `application.yml` defaults to database and
  user `bankingapp` with an empty password. The generated `postgres` service runs with
  `appdb`/`app`/`app`. `ArchHook.java compose` reported "placeholders hold on host and
  container", because question 5 compares only host and port.
- **Decision:** Option 1, amended: the login half reports and never blocks. Form 7c change
  to the existing `compose` mode (`ArchHook.java` `datasourceLoginWarnings`, a `warnings` list on
  `ComposeReport`), with data in `.claude/schemas/extensions.json`
  (`compose.datasource_pairs`, a `migrations` entry, `docker-architect`'s `write_allow`). Plus
  the template changes for one `DB_*` convention, the `.env` and the variable host ports
- **State:** approved by Lucas Fernandes, on 2026-10-06 — "aprovo a opção 1 com essa mudança",
  after "bloquear migracao apenas por isso me parece ser muito restritivo. O usuario pode ter o
  docker compose em outro lugar fora do projeto"

## Reproduced on disk

Checked at `907b1c8`, the commit the lessons-learned file cites. No commit since touches the
cited files.

| # | Claim (lessons-learned-021) | Evidence |
|---|---|---|
| 1 | Bootstrap defaults are derived from the artifact, and the password default is empty | `project-bootstrap/templates/application.yml.example:12-14` (`minhaapi`, `${DB_PASSWORD:}`) |
| 2 | Compose defaults are fixed literals, and the password has a default | `docker-architect/templates/postgres-service.yml.example:8-10` (`appdb`, `app`, `app`). The MySQL template does the same, plus `MYSQL_ROOT_PASSWORD:-root` (`mysql-service.yml.example:8-11`) |
| 3 | `persistence-architect` uses `DB_USER`, while bootstrap uses `DB_USERNAME` | `persistence-architect/templates/application-persistence.yml.example:13` |
| 4 | The `app` service is wired with `SPRING_DATASOURCE_*` | `docker-architect/SKILL.md` step 5; `project-bootstrap/references/scaffold.md` § 4.10 step 3 |
| 5 | Question 5 compares host and port only | `ArchHook.java` `placeholderIssues`: `hostPorts()` drops the JDBC path, and `username`/`password` are never read |
| 6 | `rules/secrets.md` forbids a secret default in a compose file, and already allows an untracked `.env` | `rules/secrets.md` § Where a secret may live, bullets 1, 3 and 6 |
| 7 | The Initializr `.gitignore` does not cover `.env` | `start.spring.io/starter.tgz` on 2026-10-06: no `.env` line |
| 8 | `otel-collector`, `sonarqube`, `jaeger`, `grafana` and `edge` already publish a variable host port; `postgres`, `mysql` and `kafka` do not | `docker-architect/templates/*-service.yml.example` `ports:` |

Not reproduced, recorded only: § 6 (an old volume keeps the old credentials), and the
note in § 7 that an unresolved `${DB_PASSWORD}` may reach the driver as a literal.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | Observed: host run fails to log in; the gate reports green. § 5 observed for containers of another project | Create nothing |
| 7 — Mandatoriness | "Defaults match the service" is a check (invariant 6), and the lessons-learned names it as a constraint | 1, 2, 4, 5 as the only answer |
| 8 — Destination | Both: the templates, `ArchHook.java` and `extensions.json` travel | — (propagation grows) |
| 9 — Owner of the literals | **Option A:** one `DB_*` convention from end to end. `project-bootstrap` writes the artifact-derived literal; `docker-architect` owns the shape | Two conventions (`POSTGRES_*` and `DB_*`); fixed literals in the compose file |
| 9 — Password | Untracked `.env`, created by bootstrap, plus a versioned `.env.example` and a `.gitignore` line. The compose file reads `${DB_PASSWORD:?…}`; `application.yml` imports `optional:file:.env[.properties]`. `rules/secrets.md` stays as it is | A local-only exception in the rule |
| 9 — Ports | Variable host port on every service the application reaches (`${DB_PORT:-5432}`, Kafka's external port). Database name = artifact, so a connection to another project's database fails loudly | Fixed ports |
| 14 — Event | `Stop`, already registered for `compose gate` (0064) | New registration |
| 15 — Reaction | **Report only.** The login half is a warning in `compose`, `doctor` and `/arch-doctor`. It fails none of them and never reaches the `Stop` gate. The user's reason: "bloquear apenas por isso me parece ser muito restritivo. O usuario pode ter o docker compose em outro lugar fora do projeto." With a compose file outside the project the check is already silent: `composeReport()` reads only the root. The false positive that stays is a project that declares `postgres` on the port its datasource uses, while the developer connects to another database there on purpose. A non-blocking `systemMessage` on every `Stop` was rejected as noise in that case. Block plus a project opt-out (the 0111 shape) was rejected as a new piece whose first contact is still a block. The host-and-port half of question 5 (0110) keeps blocking | Exit 2 for the login half |
| 16 — Existing mode | `compose` question 5 covers host and port; it gets the login half. No new mode | A second mode |
| — New checks | None beyond the login comparison. A process on the host holding a port, and an old volume (§ 6), stay unchecked. § 6 becomes a comment in the service template and a pitfall in the generated root `CLAUDE.md` | Bind probe; `docker exec` against the volume |
| — Existing projects | A `migrations` entry, so `/arch-adopt` prints the prompt | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Aligned templates + question 5 reports a login mismatch as a warning + `migrations` entry | 8 | **Approved** |
| 2 | Aligned templates only, no check | 4 | Rejected — invariant 6: the alignment drifts on the first hand edit or manual `/docker-architect`, and the gate keeps saying green |
| 3 | A new `ArchHook.java datasource` mode with its own `Stop` registration | 3 | Rejected — anti-pattern 17, and two definitions of "healthy" against 0064 |
| 4 | Create nothing | 0 | Rejected — the failure was observed, and the gate reports it as healthy |

### Option 1 — templates, question 5 as a warning, migration (score 8)

**Motivator:** axes 1 and 7. The failure was observed, and a check that reports healthy
over it is the 0110 shape again.

**What changes:**

- **One convention.** `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, plus `DB_NAME` and `DB_PORT`
  for the service side. `application.yml.example` defaults:
  `jdbc:postgresql://localhost:${DB_PORT:5432}/${DB_NAME:<artifact>}`, `${DB_USERNAME:<artifact>}`,
  `${DB_PASSWORD}` with no default. The `postgres` service:
  `POSTGRES_DB: ${DB_NAME:-<artifact>}`, `POSTGRES_USER: ${DB_USERNAME:-<artifact>}`,
  `POSTGRES_PASSWORD: ${DB_PASSWORD:?set DB_PASSWORD in .env}`, `"${DB_PORT:-5432}:5432"`,
  healthcheck on `$${POSTGRES_USER}`/`$${POSTGRES_DB}` (the container's own env). MySQL is
  the same shape, `MYSQL_ROOT_PASSWORD` included. `app` sets `DB_URL` (compose hostname) and
  `DB_USERNAME`/`DB_PASSWORD` by the same names, never `SPRING_DATASOURCE_*`.
  `persistence-architect`'s fragment moves from `DB_USER` to `DB_USERNAME`.
- **Owner.** `docker-architect` owns the shape and writes `{{DB_NAME}}`. The caller fills it:
  `project-bootstrap` with the artifact; a manual `/docker-architect` reads
  `spring.application.name`.
- **`.env`.** `.env` with a random `DB_PASSWORD`, a versioned `.env.example` with the same
  keys and empty values, and an `.env` line in `.gitignore` and `.dockerignore` (the
  `Dockerfile`'s build stage runs `COPY . .`). Written in Phase 4 by `docker-architect` step
  4.5, not by `project-bootstrap` as first drafted: a database service added by hand later
  needs the same files, and the piece that adds the service is their single owner.
  `project-bootstrap` reaches them through the same step 4.10 call that merges the service.
  `application.yml` imports
  `optional:file:.env[.properties]`, so the IDE run and the compose file read the same
  file. In multi-module, the run's working directory is the module: the import also names
  `../.env`.
- **Kafka.** `"${KAFKA_EXTERNAL_PORT:-29092}:29092"`, advertised as
  `EXTERNAL://localhost:${KAFKA_EXTERNAL_PORT:-29092}`, and the messaging default follows it.
- **Question 5, login half.** New data `compose.datasource_pairs` in `extensions.json`, one
  entry per engine image: the service env key for database, user and password, and the
  application property each pairs with (database ↔ path of the JDBC URL). When a datasource
  URL reaches a service of that image, the gate compares the database and user defaults
  on both sides. Each `${VAR:default}` / `${VAR:-default}` resolves to its default. For the
  password it reports only presence: the service requires one, and the application has
  neither a value nor a placeholder. **It never prints a password value.** These lines are
  **warnings**: `ComposeReport` gets a `warnings` list next to `detail`, `ok` ignores it, and
  `compose`, `doctor` and `/arch-doctor` print it with ⚠️. `composeGate` reads only `ok` and
  `detail`, so it does not block on a warning and does not print one. The generated
  templates are aligned by construction, so the warning catches only drift: a hand edit, or
  a service added to another project's naming.
- **§ 6.** A comment in `postgres-service.yml.example` and `mysql-service.yml.example`, plus a
  pitfall in `root.CLAUDE.md.example`: changing `DB_NAME`/`DB_USERNAME` after the first
  start needs `docker compose down -v`.
- **Migration.** A `datasource-env-convention` entry: note + prompt to rename the
  variables, create `.env`/`.env.example`, add the `.gitignore` line, and align the
  defaults. Nothing blocks these projects. `/arch-adopt` prints the prompt once, and
  `/arch-doctor` keeps the warning visible until the defaults agree.

**Pros:** closes §§ 1-5 at their source and keeps them closed. `rules/secrets.md` does not
change, because the template now does what the rule already said. Data in `extensions.json`
(invariant 10); MySQL or Mongo is a new entry, not Java. Uses the `Stop` registration that
already exists.

**Cons:** many files (about 15). A project without `.env` fails `docker compose up` with
the `:?` message. That is intended, but it is a new step for whoever clones the project
(README + `.env.example`). The `.env`/properties import is new in the generated project.
Residual risk, not verified: with no `.env`, Spring may pass `${DB_PASSWORD}` literally,
so the error is an authentication failure, not a message naming the variable.

**Points cut in the rubric:** criterion 5 (maintenance) loses half: the change touches many
files. Criterion 4 (enforcement) loses one: the login half only reports. A guarantee was
available, and it was declined because the false positive above would block a legitimate
setup. Criterion 9 is full: nothing new blocks. Score 8.

**CI:** `validate · hooks-cross-platform` › `compose gate blocks a published service no host
client can reach` (`ComposeGateTest.java`) gets new cases. The `compose` report shows a
warning for a database mismatch, a user mismatch, and a service with a password next to an
application with none. Its output contains no password value. `compose gate` exits 0 on each
of those three. The fixed variant and the case with the same variable and equal defaults on
both sides show no warning. `validate · design` › `compose service templates merge
and parse` must export `DB_PASSWORD` before `docker compose config`, or `:?` fails it. That
proves the `:?` is there too. `frontmatter schema` covers the `migrations` entry
(`MigrationsSchemaTest`). `ComposeTagTest` already covers the variable port parsing.

### Option 2 — templates only (score 4)

Same template changes, no gate change. Capped by invariant 6: the lessons-learned names the
alignment as a check. After a hand edit or a manual `/docker-architect` on a project with
another name, the gate says green over a login that fails. That is the defect that started
this record.

### Option 3 — new `datasource` mode (score 3)

A second `Stop` hook for a check that `compose` question 5 already half-does. Anti-pattern
17. 0064 keeps `composeReport()` as the single definition of healthy; a second mode adds a
second definition, and a second JVM on every `Stop`.

## References

| Claim | Source |
|---|---|
| A rule that must always hold is a hook | `@CLAUDE.md` invariant 6 |
| Lists the Java reads are data | `@CLAUDE.md` invariant 10; `compose` block of `extensions.json` |
| `composeReport()` is the single definition of healthy; the gate runs on `Stop` | `.claude/decisions/0064-compose-gate-on-stop.md` |
| Question 5 and the host-first pair | `.claude/decisions/0110-otlp-host-first-collector.md` |
| Untracked `.env` is the allowed place for a local secret | `.claude/rules/secrets.md` § Where a secret may live |
| Convention changes reach existing projects through `migrations` | `extensions.json` `migrations.$comment`; `.claude/decisions/0104-use-case-subpackage-per-aggregate.md` |
| A generated project carries `ArchHook.java` and `extensions.json` whole | `@CLAUDE.md` invariant 9; `export` mode |
| Compose `${VAR:?msg}` fails interpolation when VAR is unset | Compose specification, interpolation |
| `spring.config.import` accepts an extension hint for a file with no extension (`[.properties]`) | Spring Boot reference, "Importing Extensionless Files" |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` + `ArchHook.jar` | `ComposeReport.warnings`, ignored by `ok` and by `compose gate`, printed by `compose` and `doctor`. `datasourceLoginWarnings` and its helpers (`loginCompare`, `appSet`, `springDefault`, `composeDefaults`, `yamlScalars`). The advertised-address check reads compose defaults before it splits `host:port`, so `localhost:${KAFKA_EXTERNAL_PORT:-29092}` is not reported as unreachable |
| `.claude/schemas/extensions.json` | `compose.datasource_pairs` (postgres, mysql). The `datasource-env-convention` migration. `docker-architect`'s `write_allow` gets `.env`, `.env.example`, `.gitignore` and `.dockerignore` |
| `docker-architect/templates/postgres-service.yml.example`, `mysql-service.yml.example` | `DB_*` variables, `{{DB_NAME}}`, `${DB_PASSWORD:?…}` with no default, `${DB_PORT:-…}` host port, a healthcheck on the container's own env, the volume caveat (§ 6) |
| `docker-architect/templates/kafka-service.yml.example` | `${KAFKA_PORT:-9092}`, `${KAFKA_EXTERNAL_PORT:-29092}`, the advertised EXTERNAL address on the same variable |
| `docker-architect/templates/env.example.example` | New: the `.env.example` shape |
| `docker-architect/SKILL.md` | Step 4.5 (`{{DB_NAME}}`, `.env`, `.env.example`, `.gitignore`/`.dockerignore`). Step 5 wires `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`, never `SPRING_DATASOURCE_*`. Step 7 names the warning. Contract territory |
| `project-bootstrap/templates/application.yml.example` | `spring.config.import` of `.env`; `DB_PORT`/`DB_NAME` in the URL default; `minha_api` defaults; `${DB_PASSWORD}` with no default |
| `project-bootstrap/templates/root.CLAUDE.md.example` | Two pitfalls under `persistence-jpa`: `.env`, and the volume that applies credentials once |
| `project-bootstrap/references/scaffold.md` § 4.10 | Applies `docker-architect` step 4.5. Wires `DB_*` |
| `project-bootstrap/references/build-maven.md`, `build-gradle.md` | Multi-module `.env` import from `../.env` |
| `project-bootstrap/SKILL.md` | Writes list (`.env`, `.env.example`, the ignore lines), the final report's `Local secrets` line |
| `persistence-architect/templates/application-persistence.yml.example` | `DB_USER` becomes `DB_USERNAME`; same defaults as bootstrap |
| `messaging-architect/templates/application-kafka.yml.example`, `messaging-spec.md.example` | `bootstrap-servers` default `localhost:${KAFKA_EXTERNAL_PORT:29092}` |
| `CLAUDE.md` | The `compose` row of § Commands names the warning |
| `docs/pt-br/11-pitfalls.md`, `docs/en/11-pitfalls.md` | Two Compose entries: the login half, and the volume caveat |
| `docs/pt-br/07-ci-validate.md`, `docs/en/07-ci-validate.md` | `ComposeGateTest` and `compose service templates merge and parse` rows; `SkillTerritoryTest` 39 cases become 45 |
| `.claude/.ci/ComposeGateTest.java`, `.claude/.ci/SkillTerritoryTest.java`, `.github/workflows/validate.yml` | CI, below |

Not changed: `rules/secrets.md`. It already allows an untracked `.env` and forbids the default
the templates carried; the templates now follow it. No hook registration changed: the gate on
`Stop` is the one from 0064, and it does not read warnings.

Goes to the generated project: **yes**. `export` copies `ArchHook.java`, the jar,
`extensions.json` and the skills' `templates/` whole. Projects generated before this change
get the `datasource-env-convention` prompt from `/arch-adopt`, and `/arch-doctor` shows the
warning until they align.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · hooks-cross-platform` › `compose gate blocks a published service no host client can reach` (`ComposeGateTest.java`) | The lessons-learned-021 shape gets three warnings in `compose` (database, user, missing password). The password literal is absent from the output. The container side wired to the service is quiet, and the gate never prints `login:`. The aligned `DB_*` shape is quiet on both halves of question 5. An `app` pointing at another database is warned. Kafka on variable host ports, advertised through the same variable, is quiet | Green on the tree, 19 cases. Red with three defects injected and the jar rebuilt (gate printing warnings, advertised check without compose defaults, password check off): 3 cases failed by name |
| `validate · hooks-cross-platform` › `guard keeps each skill inside its class's territory` (`SkillTerritoryTest.java`) | `docker-architect` may write `.env`, `.env.example`, `.gitignore` and `.dockerignore`, and not `application.yml` | Green, 45 cases. Red with `.env` taken out of `write_allow`: `.env — docker-architect's, step 4.5 — expected exit 0, got 2` |
| `validate · design` › `compose service templates merge and parse` | Every service template merges and parses with `{{DB_NAME}}` filled and the passwords exported. The `:?` is really there | Green locally with `docker compose config -q`. Red with `DB_PASSWORD` unset: `required variable DB_PASSWORD is missing a value: set DB_PASSWORD in .env` |
| `validate · hooks-cross-platform` › `schema blocks a migrations entry no project would see` + `design` › `frontmatter schema` | The new `migrations` entry and `datasource_pairs` pass `schema`, in this repository and in an exported project | `schema` exit 0 here and inside `export --blueprint clean-architecture-single-module` |
| `validate · hooks-cross-platform` › `committed ArchHook.jar is what the source compiles to` | The jar is the source | `build --verify` green after the restore |

Not covered by CI: a real `spring.config.import` of `.env` at startup, and the Spring behavior
when `.env` is missing (§ 7 of the lessons-learned: an unresolved `${DB_PASSWORD}` may reach
the driver as a literal). Both need a Maven build of a generated project. They are the first
thing the next `/init-project` run on `persistence-jpa` shows.
