# Lessons learned 021 — compose services that already exist, or run with other parameters than the application expects

Date: 2026-10-06. Scope: the first local run of `banking-app` after the `/init-project` run
recorded in `lessons-learned-020.md` (blueprint `clean-architecture-single-module`, JPA +
Flyway, observability, SonarQube local). The application did not start from the IDE. The
cause was not in the user's code: the generated `application.yml` and the generated
`docker-compose.yml` disagree on database name, user and password, and nothing checks that
they agree. The same session then hit the second half of the problem, services of another
project already holding this project's host ports. This file records what broke and why. It
decides nothing. The design belongs to `/claude-code-architect-designer`.

Sources: the session in `banking-app`, `docker ps`, `docker exec … env`, the generated files,
and this repository's templates at commit `907b1c8`.

---

## § 1 · What happened

`BankingAppApplication`, run from IntelliJ with no environment variables, died at startup.
The compose `postgres` service was up and healthy on `localhost:5432` (started by IntelliJ's
compose run configuration, `postgres` only).

The two sides of the connection, as generated:

| | Database | User | Password |
|---|---|---|---|
| `postgres` container (`docker exec … env`) | `appdb` | `app` | `app` |
| `application.yml` defaults (host run) | `bankingapp` | `bankingapp` | empty |

With an empty password the PostgreSQL driver refuses SCRAM before it sends credentials. With a
password it would get `password authentication failed for user "bankingapp"`, and then
`database "bankingapp" does not exist`. Either way Hikari and Flyway cannot open a connection,
and the context fails. The exact stack trace was not captured. The cause was confirmed by the
fix: with the defaults aligned and `DB_PASSWORD=app` set, the application started in 2.2 s and
`/actuator/health` answered `UP`.

**The run in the container works.** The `app` service sets `SPRING_DATASOURCE_URL`,
`DB_USERNAME` and `DB_PASSWORD` from the same `POSTGRES_*` variables the `postgres` service
uses. So `docker compose up` is green and the defect only shows on the host run, which is the
run `CLAUDE.md` lists as "Run locally" (`./mvnw spring-boot:run`).

## § 2 · Root cause: two templates, two sets of defaults, no link between them

| Template | Database | User | Password | Variable names |
|---|---|---|---|---|
| `project-bootstrap/templates/application.yml.example` | `minhaapi` (instantiated as the artifact name) | `minhaapi` | `${DB_PASSWORD:}` | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
| `docker-architect/templates/postgres-service.yml.example` | `${POSTGRES_DB:-appdb}` | `${POSTGRES_USER:-app}` | `${POSTGRES_PASSWORD:-app}` | `POSTGRES_*` |
| `persistence-architect/templates/application-persistence.yml.example` | `${DB_URL}` | `${DB_USER}` | `${DB_PASSWORD}` | `DB_URL`, **`DB_USER`** |
| `scaffold.md` and `docker-architect/SKILL.md` step 5 (`app` wiring) | — | — | — | **`SPRING_DATASOURCE_URL`**/`_USERNAME`/`_PASSWORD` |

Four places name the same three values, with three naming conventions for the variables and
two sets of defaults. Each template is consistent with itself. Nobody owns the pair.

- The bootstrap defaults are derived from the artifact; the compose defaults are fixed
  literals. They can only match by accident.
- `persistence-architect` uses `DB_USER`, while bootstrap and the `app` service use
  `DB_USERNAME`. A project that takes the persistence fragment gets a placeholder the compose
  file never sets.
- The `app` service is wired with `SPRING_DATASOURCE_URL`, which overrides `${DB_URL:…}` only
  through relaxed binding. It works, but the project has two names for one setting, and a
  reader of `application.yml` cannot see where the container's value comes from.

## § 3 · The compose gate reports healthy while the host run cannot log in

After the fix, `ArchHook.java compose` reported:

```
✅ 3/3 service(s) running, no port collision, image tags match, published ports advertised
   to the host, placeholders hold on host and container
```

Before the fix it would have reported the same thing: the host and port did not change. Question 5
(`placeholderIssues`) checks that a `${VAR:default}` reaches a **host and port** the service
publishes. `jdbc:postgresql://localhost:5432/bankingapp` passes, because `5432` is published.
The **path** (database name) and the sibling properties (`username`, `password`) are not
compared with the service's `POSTGRES_DB` / `POSTGRES_USER`. So "placeholders hold on host
and container" is true for the socket and false for the login.

This is the same shape as the OTLP defect that motivated question 5 (decision 0110): a value
that holds inside the compose network and not on the host, reported green.

## § 4 · The compose template carries a password default, against `rules/secrets.md`

`postgres-service.yml.example` ships `POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-app}`, and the
generated `app` service repeats it as `DB_PASSWORD: ${POSTGRES_PASSWORD:-app}`.
`rules/secrets.md` says a secret is never in "a compose file" and that "a placeholder for a
secret has no default value, or an empty one". A dev-only password is still a default in a
versioned file.

This matters for § 1: the compose side has a password because of this default, and the
`application.yml` side has none because it follows the rule. The rule and the template pull
the two halves apart. Whatever the fix is, the rule and the compose template must say the
same thing. Either the rule names a local-only exception, or the compose file reads the
password from an untracked `.env` that `.gitignore` covers and that bootstrap creates.

## § 5 · Services of another project already running on the same host ports

At the end of the session the `Stop` hook reported:

```
host port 4318 is held by `demo-clean-arch-single-module-otel-collector-1`, a container of ANOTHER project
host port 9000 is held by `demo-clean-arch-single-module-sonarqube-1`, a container of ANOTHER project
```

Both projects come from the same blueprint, so both declare the same fixed host ports. While
the other project's containers ran:

- `banking-app`'s `otel-collector` and `sonarqube` could not bind, and stayed down.
- The application, run on the host, sent every span and metric to **the other project's
  collector**, with that project's config. Nothing failed, so nothing showed it.
- The gate saw it only because the `Stop` hook ran. Starting services from IntelliJ's compose
  run configuration bypasses any check.

The agent asked, the user chose to stop the two foreign containers, and the agent ran `docker stop` on them. The gate then went green.

The same mechanism is behind `lessons-learned-020.md` § 2: an IT without
`@Import(TestcontainersConfiguration.class)` fell back to `localhost:5432`, reached a
`postgres` left running by another project, and failed SCRAM. For a database it is worse than
for the collector:

- **Port held by another project's database** — the host run connects to a database that
  is not this project's. Different name, user and password give an authentication error that
  looks exactly like § 1. If they happen to match (two projects generated with the same
  compose defaults, `appdb`/`app`/`app`), the application **connects and runs Flyway against
  the other project's schema**. That case is silent.
- The gate's question 2 catches it only for a **container** publishing the port. A database
  installed on the host (Homebrew, Postgres.app) on 5432 is not a container, and the gate
  does not see it.

## § 6 · Not observed here, same family: parameters changed after the first start

The official `postgres` image reads `POSTGRES_DB`, `POSTGRES_USER` and `POSTGRES_PASSWORD`
**only when it initializes an empty data directory**. The generated compose mounts a named
volume (`postgres-data`). If a developer fixes § 1 by changing the compose environment instead
of `application.yml`, `docker compose up` reuses the old volume. The container keeps the old
database and user, the environment shows the new ones, and `docker exec … env` (the check used
in § 1) lies. Only `docker compose down -v`, which deletes the data, applies the new values.
Nothing in the generated `CLAUDE.md`, README or `docker-architect` says this.

This was not reproduced in this session. It is recorded because it is the obvious next step
after the § 1 fix, and its failure looks the same as § 1.

## § 7 · Fix applied in `banking-app` (local, not upstream)

- `application.yml`: `DB_URL` default `jdbc:postgresql://localhost:5432/appdb`, `DB_USERNAME`
  default `app`, `DB_PASSWORD` with no default.
- `docker-compose.yml`: `app` sets `DB_URL` instead of `SPRING_DATASOURCE_URL`.
- `DB_PASSWORD=app` goes in the IDE run configuration (`.idea` is ignored).
- `./mvnw verify` green (5 tests). The ITs are not affected: `@ServiceConnection` replaces the
  datasource.

Not verified: with `DB_PASSWORD` unset, Spring Boot binding may pass the literal
`${DB_PASSWORD}` as the password instead of failing on the unresolved placeholder. The error
would be an authentication failure, not a message naming the variable.

## Constraints any fix must respect

- **Invariant 6.** "The application's datasource defaults match the compose service" is a
  check, so it belongs in `ArchHook.java compose` (question 5), not in a paragraph asking the
  agent to keep them aligned.
- **Invariant 10.** Which properties pair with which service variables
  (`spring.datasource.url` path ↔ `POSTGRES_DB`, `spring.datasource.username` ↔
  `POSTGRES_USER`) is data, not code. The same goes for any other engine added later (MySQL's
  `MYSQL_DATABASE`, Mongo's `MONGO_INITDB_*`).
- **`rules/secrets.md` is the single source for passwords.** The fix for § 4 changes either
  the rule or the template, never only one of them.
- **Never compare password values in a report.** A check may say "the defaults disagree" for
  database and user. For the password, it may only say whether each side has one.
- **Never stop another project's containers automatically.** The gate names the container and
  suggests the command. The user decides, as happened here.

## Open questions for `/claude-code-architect-designer`

1. Who owns the datasource defaults: `docker-architect` (the service defines them and the
   application follows), or `project-bootstrap` (the artifact name defines them and the
   compose block is instantiated from it)? One owner, one set of literals.
2. One variable convention for the whole chain: `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`
   everywhere, including the `app` service and `persistence-architect`'s fragment
   (`DB_USER`)?
3. Should question 5 also compare the JDBC path and `username` default with the target
   service's `POSTGRES_DB` / `POSTGRES_USER`, and report when the application's password
   placeholder is empty but the service requires one?
4. Should generated projects publish **variable** host ports for every service
   (`"${POSTGRES_PORT:-5432}:5432"`, as `otel-collector` and `sonarqube` already do), so two
   projects from the same blueprint can run side by side instead of fighting for the port?
   And should their default database names differ by artifact, so a wrong connection fails
   loudly instead of reaching another project's schema?
5. Should the gate warn about a **non-container** process listening on a port the compose
   file publishes (`lsof`/`ss`), the Homebrew Postgres case in § 5?
6. Where does the volume caveat in § 6 live: a comment in `postgres-service.yml.example`, a
   line in the generated `CLAUDE.md` "Known pitfalls", or a gate check that compares
   `POSTGRES_USER` with the roles that actually exist in the running container?
