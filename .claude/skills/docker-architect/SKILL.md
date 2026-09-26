---
name: docker-architect
description: >
  Extends a project's docker-compose.yml and Dockerfile after project-bootstrap's base
  generation — adds the database or messaging service a modeled use case needs, keeps
  the compose-side image tag consistent with the one test-architect pins in
  TestcontainersConfiguration.java, and syncs with 20-persistencia.md / 40-testes.md.
  Also owns the observability backend behind the OTLP collector: Jaeger, or Grafana +
  Tempo + Prometheus. Use when the request involves adding a service to docker-compose,
  containerizing a new dependency, configuring Testcontainers at the compose level,
  "docker-compose is missing the database", or wanting a dashboard, a trace UI, or
  somewhere to actually look at spans and metrics instead of the collector's `debug`
  stdout — also fires when persistence-architect or test-architect detect a service
  their spec needs isn't in docker-compose.yml yet.
argument-hint: "[path of the UC-NNN-<slug> folder, or empty for a manual service add]"
allowed-tools: Read, Write, Edit, Glob, Grep, Bash, AskUserQuestion
---

## Current compose state

!`R="${CLAUDE_PROJECT_DIR:-.}"; if test ! -d "$R"; then echo "(could not look: project root '$R' is not a readable directory — this is not an answer about docker-compose.yml)"; elif test -f "$R/docker-compose.yml"; then grep -E "^\s{2}\S+:$" "$R/docker-compose.yml" || echo "(docker-compose.yml exists but declares no service at two-space indent)"; else echo "(no docker-compose.yml at project root — run project-bootstrap first)"; fi`

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

The `## Current compose state` block above answers one of three things, and only the
second one arms that rule:

| Output | Meaning | What to do |
|---|---|---|
| A list of service names | The file exists | Proceed |
| `(no docker-compose.yml at project root …)` | The project root was readable and the file is not there | Entry rule applies: stop |
| `(could not look: project root … is not a readable directory …)` | The root itself couldn't be read — **this is not an answer about `docker-compose.yml`** | Don't apply the entry rule. Read the file at the path the user gave, or ask for the project root; run `/arch-doctor`, which reports `CLAUDE_PROJECT_DIR NOT set` |
| `(docker-compose.yml exists but declares no service …)` | The file is there and malformed or empty | Entry rule doesn't apply — the base pair exists. Read the file before editing |

## How it's invoked

Three paths, all real: `/docker-architect` by hand, when the user wants a service added
or checked; chained by `persistence-architect`, `messaging-architect`, or
`test-architect`, mid-procedure, when their spec names a dependency
`docker-compose.yml` doesn't have yet; and chained by `project-bootstrap` itself,
right after it writes the base pair in its own step 4.10, once per blueprint feature
that's already active and needs a container (`persistence-jpa` → Postgres,
`observability` → the OTLP collector) — see `project-bootstrap/SKILL.md` step 4.10.
That's why it carries no `disable-model-invocation` — a skill the model can't see is a
skill a sibling skill can't call.

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
| `project-bootstrap` | Base `Dockerfile` + `docker-compose.yml` (`app` service), once, at generation. Also *decides when* to call this skill for a feature already active in the blueprint (`persistence-jpa`, `observability`) — never writes the service block itself | Any service a use case adds later; the shape of any service block, ever |
| **this skill** | Every service block in `docker-compose.yml`/`Dockerfile` — the ones `project-bootstrap` calls it for at generation, and the ones added later by hand or chained from a UC-driven skill | The engine choice itself — that's `20-persistencia.md`'s call, this skill reads it |
| `persistence-architect` | Engine choice, schema, datasource properties (`20-persistencia.md`) | `docker-compose.yml` directly — invokes this skill instead |
| `messaging-architect` | Broker choice, topic, consumer group (`25-mensageria.md`) | `docker-compose.yml` directly — invokes this skill instead |
| `test-architect` | The pinned image tag inside `TestcontainersConfiguration.java` (one line, Java side) | The compose-side service definition — invokes this skill instead, and both should agree on the same tag |

**The OTLP signal contract has two owners, one per half**, and that is what made
lessons-learned-008 possible. `project-bootstrap`'s
`templates/features/observability/application-observability.yml.example` decides **which
signals the application exports** (`management.otlp.*`); this skill's
`templates/otel-collector-config.yml.example` decides **which signals the collector
accepts** (`service.pipelines`). The OTLP receiver registers no route for a signal that
has no pipeline, so a mismatch is a `404` on every publish cycle, not a startup error.
Adding or removing a signal on either side is a change to both files, in the same commit.
No rule states this — `rules/` is a leaf and cannot name either skill (invariant 1), so
this table is where it lives.

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
   - If called from `project-bootstrap` at generation time: the feature already decided
     it — `persistence-jpa` means Postgres (the engine `application.yml.example`'s
     `datasource.url` already assumes), `observability` means the OTLP collector. No
     engine question to ask, and **no backend question either**: generation stays
     non-interactive, and `project-bootstrap`'s own final report is what tells the user
     the collector exports to `debug` and how to add a UI later. Go straight to step 3.
   - If invoked manually with no folder: `AskUserQuestion` — engine (Postgres, MySQL,
     Kafka, other), version/tag, port, whether it needs an init script. Don't ask what a
     given spec already answers.

2.5 **Observability backend — ask only when it is actually open.** Conditions, all three:
   invoked manually (not chained), `otel-collector` already in `docker-compose.yml`, and
   no backend service there yet. Otherwise skip this step without mentioning it.

   `AskUserQuestion` with three options, and state what each costs:

   | Answer | What gets merged |
   |---|---|
   | Jaeger | `templates/jaeger-service.yml.example`. One container, traces only, UI on `${JAEGER_UI_PORT:-16686}`. The `metrics` pipeline stays on `debug` |
   | Grafana + Tempo + Prometheus | `templates/grafana-stack-service.yml.example` + its three init scripts. Three containers, both signals, UI on `${GRAFANA_PORT:-3000}` |
   | Keep `debug` | Nothing merged. Say plainly that this means no UI — the collector dumps to its own stdout — and that the question can be re-asked any time by running this skill again |

   Don't recommend by guessing the project's future: Jaeger when only traces are asked
   for, the Grafana stack when the request names metrics or a dashboard. A backend
   already present in `docker-compose.yml` is never replaced by this step — removing one
   is a hand edit the user asks for explicitly.

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
   hostname and port from compose's internal network). For the OTLP collector that is
   **both** endpoint variables, one per signal — `OTLP_ENDPOINT=http://otel-collector:4318/v1/traces`
   and `OTLP_METRICS_ENDPOINT=http://otel-collector:4318/v1/metrics`, matching the two
   placeholders the observability fragment declares. Wiring only the tracing one leaves
   metrics pointed at the app container's own `localhost`, which is silent and wrong.
   Don't invent datasource properties beyond connectivity — sizing and the rest are
   `@.claude/rules/persistence.md`'s and `persistence-architect`'s call, not this
   skill's.

   The backend services from step 2.5 need no variable on the `app` service: the
   application talks only to the collector, and the collector reaches the backend through
   the exporter delta in step 6.

6. **Init script.** Write it under `docker/init/<service>/` and mount it read-only in
   the service's `volumes:`, from the matching `templates/<name>-config.<ext>.example`
   when one exists. For a database service this is conditional on step 2 finding one is
   needed, and skipped entirely when the schema comes from a Flyway migration instead —
   one source of schema truth, not two. For the OTel collector it isn't conditional:
   the collector has no built-in default pipeline and refuses to start without
   `templates/otel-collector-config.yml.example` mounted, so this step always runs for
   that service.

   Same for the backends from step 2.5 — Tempo, Prometheus, and Grafana each refuse to
   start, or start useless, without theirs:

   | Service | Init script | From |
   |---|---|---|
   | `tempo` | `docker/init/tempo/tempo-config.yml` | `templates/tempo-config.yml.example` |
   | `prometheus` | `docker/init/prometheus/prometheus-config.yml` | `templates/prometheus-config.yml.example` |
   | `grafana` | `docker/init/grafana/datasources.yml` | `templates/grafana-datasources.yml.example` |
   | `jaeger` | — none | Jaeger v2 ships a working default |

6.5 **Apply the collector's exporter delta**, and only when step 2.5 merged a backend.
   Edit `docker/init/otel-collector/otel-collector-config.yml` — never a template, never
   a second copy of it — with the `exporters:` entries and the pipeline exporter lists
   written in the header of the backend template just used. Two rules: the `metrics`
   pipeline keeps `debug` when the backend is Jaeger (Jaeger stores no metrics), and an
   exporter is added to `exporters:` rather than replacing what is there. Then note in
   the report that the collector needs `docker compose up -d --force-recreate
   otel-collector` to pick the file up — a mounted config is read once, at start.

7. **Report and stop.** Service added, image tag used (and whether it matches
   `test-architect`'s pin), files changed, and — when step 2.5 merged a backend — the UI
   URL with the variable that moves it (`http://localhost:16686`, `JAEGER_UI_PORT`).

   End the report with the one command that verifies the result, and say what it
   catches: `java .claude/hooks/ArchHook.java compose` — every service actually
   `running` rather than `created`, and no container from another project holding a host
   port this one publishes. `docker compose up -d` exits 0 in both of those failures.
   Don't run it here: nothing has been started yet at this point, and a report about
   containers that do not exist is noise.

   Don't invoke anyone — a sibling skill that chained this one resumes on its own thread.

## Service catalog

| Engine | Template | When **not** |
|---|---|---|
| PostgreSQL | `templates/postgres-service.yml.example` | Engine chosen in `20-persistencia.md` isn't Postgres — or, at bootstrap time, when `persistence-jpa` isn't active in the blueprint |
| MySQL | `templates/mysql-service.yml.example` | Engine chosen in `20-persistencia.md` isn't MySQL |
| Kafka | `templates/kafka-service.yml.example` | Broker chosen in `25-mensageria.md` isn't Kafka, or there is none |
| OpenTelemetry Collector | `templates/otel-collector-service.yml.example` + init script `templates/otel-collector-config.yml.example` | `observability` isn't active in the blueprint. No engine choice for the collector itself — one vendor-neutral ingest point, always the same shape. Where it *exports* to is a real choice, and it's the next two rows |
| Jaeger | `templates/jaeger-service.yml.example` | No `otel-collector` in the file yet, a backend is already there, or the project wants metrics too — Jaeger stores traces only |
| Grafana + Tempo + Prometheus | `templates/grafana-stack-service.yml.example` + three init scripts (`tempo-config`, `prometheus-config`, `grafana-datasources`) | No `otel-collector` yet, a backend is already there, or three containers is too much for what the project needs — Jaeger is the one-container answer |
| H2 | — no service | In-memory, runs inside the JVM; nothing to containerize |

The collector's default exporter is `debug`, which writes to its own stdout and **is not
a dashboard**. That default is deliberate — this skill doesn't pick an observability
vendor on its own — but it is a starting point, not the end of the road: step 2.5 exists
so the gap gets named out loud instead of waiting for someone to notice that "the traces
work" and "I can see the traces" are different sentences.

Other engines and brokers (Oracle, RabbitMQ, SQS via LocalStack) follow the same shape as
the templates above: image, fixed dev port, named volume for data, healthcheck,
environment for user/password/database (or broker-equivalent). Write the block by hand
from that shape; don't wait for a template to exist before extending a project that needs
one today.

## Contract

**Reads** `docs/use-cases/UC-NNN-<slug>/20-persistencia.md`, `25-mensageria.md`, and
`40-testes.md` when a folder is given; the active blueprint's `features:` (`persistence-jpa`,
`observability`) when called from `project-bootstrap` at generation time; plus
`docker-compose.yml`, `Dockerfile`, `TestcontainersConfiguration.java` (image tag only),
`@.claude/rules/persistence.md`, and `@.claude/rules/messaging.md`.

**Writes** `docker-compose.yml` (every service block, including the ones added at
generation time for an already-active feature), `Dockerfile` (build-stage additions
only, never the base image or base stages `project-bootstrap` wrote), and `docker/init/**`
when an init script is needed. No other skill writes a service block into
`docker-compose.yml` — not even `project-bootstrap`, which only decides *when* to call
this skill — this is the single owner.

**Does not** choose the database engine (`persistence-architect`'s call via
`20-persistencia.md`) or the broker (`messaging-architect`'s call via
`25-mensageria.md`), pin the Testcontainers image tag inside Java (`test-architect`'s one
line in `TestcontainersConfiguration.java`), or write the base
`docker-compose.yml`/`Dockerfile` (`project-bootstrap`, once, at generation). Doesn't
write business code, doesn't touch `.claude/rules/**`.
