---
name: docker-architect
description: >
  Extends a project's docker-compose.yml and Dockerfile after project-bootstrap's base
  generation — adds the database or messaging service a modeled use case needs, keeps
  the compose-side image tag consistent with the one test-architect pins in
  TestcontainersConfiguration.java, and syncs with 20-persistencia.md / 40-testes.md.
  Use when the request involves adding a service to docker-compose, containerizing a
  new dependency, configuring Testcontainers at the compose level, or "docker-compose
  is missing the database" — also fires when persistence-architect or test-architect
  detect a service their spec needs isn't in docker-compose.yml yet.
argument-hint: "[path of the UC-NNN-<slug> folder, or empty for a manual service add]"
allowed-tools: Read, Write, Edit, Glob, Grep, Bash, AskUserQuestion
---

## Current compose state

!`test -f docker-compose.yml && grep -E "^\s{2}\S+:$" docker-compose.yml || echo "(no docker-compose.yml at project root — run project-bootstrap first)"`

## Target

$ARGUMENTS

---

# Docker Architect

Keeps `docker-compose.yml` and `Dockerfile` in sync with what the project actually
needs, **after** they're born. `project-bootstrap` writes the base pair — one `app`
service, nothing else — at project creation. Every service added afterward (Postgres,
MySQL, a broker) goes through this skill, so there's one owner instead of
`persistence-architect` and `test-architect` each editing the same YAML their own way.

**Entry rule: without `docker-compose.yml` at the project root, there's nothing to
extend.** If it's missing, stop and say to run `project-bootstrap` — this skill never
generates the base pair, only grows it.

## How it's invoked

Two paths, both real: `/docker-architect` by hand, when the user wants a service added
or checked; and chained by `persistence-architect` or `test-architect`, mid-procedure,
when their spec names a dependency `docker-compose.yml` doesn't have yet. That's why it
carries no `disable-model-invocation` — a skill the model can't see is a skill a sibling
skill can't call.

The guard against firing on an unborn project isn't the frontmatter: it's the entry rule
above.

## Why this is a skill and not a subagent

Form 1, motivated by the "Ambos" trigger axis: chained by sibling skills mid-procedure,
and invocable by hand. The closest rejected form was a subagent — it fails the § 5
counter-test in `claude-code-architect-designer`'s decision matrix on all three points:
the service choice is short, its shape fits in `templates/`, and the diff it produces is
a few YAML lines. No context to isolate, no tool to restrict, no model change justified.
Full record: `@.claude/decisions/0029-docker-architect-skill.md`.

## Boundary with neighboring pieces

| Piece | Owns | Doesn't touch |
|---|---|---|
| `project-bootstrap` | Base `Dockerfile` + `docker-compose.yml` (`app` service only), once, at generation | Any service added later |
| **this skill** | Every service added to `docker-compose.yml`/`Dockerfile` after the base pair exists | The engine choice itself — that's `20-persistencia.md`'s call, this skill reads it |
| `persistence-architect` | Engine choice, schema, datasource properties (`20-persistencia.md`) | `docker-compose.yml` directly — invokes this skill instead |
| `messaging-architect` | Broker choice, topic, consumer group (`25-mensageria.md`) | `docker-compose.yml` directly — invokes this skill instead |
| `test-architect` | The pinned image tag inside `TestcontainersConfiguration.java` (one line, Java side) | The compose-side service definition — invokes this skill instead, and both should agree on the same tag |

## Procedure

1. **Confirm the base pair exists.** `docker-compose.yml` and `Dockerfile` at the
   project root. Missing either → stop, name what's missing, point to
   `project-bootstrap`.

2. **Find out what's needed.**
   - If `$ARGUMENTS` names a `UC-NNN-<slug>` folder: read `20-persistencia.md` for the
     engine and version, `25-mensageria.md` for the broker and topic, and `40-testes.md`
     for whether `testcontainers` is active and which image tag `test-architect` already
     pinned in `TestcontainersConfiguration.java`
     (`grep -rn "DockerImageName.parse" src/test`).
   - If invoked manually with no folder: `AskUserQuestion` — engine (Postgres, MySQL,
     Kafka, other), version/tag, port, whether it needs an init script. Don't ask what a
     given spec already answers.

3. **Check what's already there.** `grep -A2 "^services:" docker-compose.yml` and the
   service names under it. A service already present gets left alone — this step never
   duplicates or silently overwrites a hand-edited block.

4. **Merge the service block.** From the matching `templates/<engine>-service.yml.example`,
   append under `services:` — indentation matched to the file's own, never reformatting
   what's already there. Use the **same image tag** `test-architect` pinned, when one
   exists, so the dev-time container and the integration-test container run identical
   software. If none is pinned yet, use the tag the engine's template ships with and say
   so in the report — `test-architect`'s setup mode is what should pick it up from here,
   not the other way around.

5. **Wire the app service's environment**, only the variables that change because of
   step 4 (`SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD` pointing at the new service's
   hostname and port from compose's internal network). Don't invent datasource
   properties beyond connectivity — sizing and the rest are
   `@.claude/rules/persistence.md`'s and `persistence-architect`'s call, not this
   skill's.

6. **Init script, only if step 2 found one is needed.** Write it under
   `docker/init/<service>/` and mount it read-only in the service's `volumes:`. Skip
   this entirely when the schema comes from a Flyway migration instead — one source of
   schema truth, not two.

7. **Report and stop.** Service added, image tag used (and whether it matches
   `test-architect`'s pin), files changed. Don't invoke anyone — a sibling skill that
   chained this one resumes on its own thread.

## Service catalog

| Engine | Template | When **not** |
|---|---|---|
| PostgreSQL | `templates/postgres-service.yml.example` | Engine chosen in `20-persistencia.md` isn't Postgres |
| MySQL | `templates/mysql-service.yml.example` | Engine chosen in `20-persistencia.md` isn't MySQL |
| Kafka | `templates/kafka-service.yml.example` | Broker chosen in `25-mensageria.md` isn't Kafka, or there is none |
| H2 | — no service | In-memory, runs inside the JVM; nothing to containerize |

Other engines and brokers (Oracle, RabbitMQ, SQS via LocalStack) follow the same shape as
the templates above: image, fixed dev port, named volume for data, healthcheck,
environment for user/password/database (or broker-equivalent). Write the block by hand
from that shape; don't wait for a template to exist before extending a project that needs
one today.

## Contract

**Reads** `docs/use-cases/UC-NNN-<slug>/20-persistencia.md`, `25-mensageria.md`, and
`40-testes.md` when a folder is given, `docker-compose.yml`, `Dockerfile`,
`TestcontainersConfiguration.java` (image tag only), `@.claude/rules/persistence.md`, and
`@.claude/rules/messaging.md`.

**Writes** `docker-compose.yml` (service blocks after the base pair), `Dockerfile`
(build-stage additions only, never the base image or base stages
`project-bootstrap` wrote), and `docker/init/**` when an init script is needed. No other
skill touches `docker-compose.yml` after the base generation — this is the single
owner.

**Does not** choose the database engine (`persistence-architect`'s call via
`20-persistencia.md`) or the broker (`messaging-architect`'s call via
`25-mensageria.md`), pin the Testcontainers image tag inside Java (`test-architect`'s one
line in `TestcontainersConfiguration.java`), or write the base
`docker-compose.yml`/`Dockerfile` (`project-bootstrap`, once, at generation). Doesn't
write business code, doesn't touch `.claude/rules/**`.
