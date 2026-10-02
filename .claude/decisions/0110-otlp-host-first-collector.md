# 0110 · The OTLP collector is host-first, like every other service the app reaches, and `compose` checks that each placeholder holds both on the host and in the container

- **Date:** 2026-10-02
- **Scenario:** Issue #66, triaged at `634e2b2`: the OTLP default endpoints point to
  `localhost:4318`, but the collector has published no host port since 0046. So host runs
  (`./mvnw spring-boot:run`) fail every export. Projects generated before 0046 have no
  `OTLP_METRICS_ENDPOINT` on `app`, and no migration adds it. `ArchHook.java compose`
  checks neither case.
- **Decision:** Option 1 — the host-first collector in `docker-architect`'s template, the
  `otlp-host-run` migration, and question 5 of `ArchHook.java compose`, with both (a) and (b)
  blocking in the gate
- **State:** approved by Lucas Fernandes, on 2026-10-02. It partly supersedes 0046 (host
  port 4318); a note there links back here

## Reproduced on disk

The `issue-verifier` table from `/triage-issue 66`, confirmed rows only, checked at `634e2b2`
(= `v0.13.5`; no commits since).

| # | Claim | Evidence |
|---|---|---|
| 1 | The fragment defaults both endpoints to host `localhost:4318` | `project-bootstrap/templates/features/observability/application-observability.yml.example:22,25` |
| 2 | The collector publishes no port, on purpose | `docker-architect/templates/otel-collector-service.yml.example:10-23` |
| 3 | Nothing in the generated project tells the developer to write the override | The only mention is the YAML comment of row 2. `root.CLAUDE.md.example:24` says only `{{runCmd}}`, which resolves to `./mvnw spring-boot:run` |
| 5 | `docker-architect` step 5 requires both OTLP variables on `app` | `docker-architect/SKILL.md:208-212`; `project-bootstrap/references/scaffold.md:243-249` |
| 6 | `migrations` exists to carry convention changes to code that is already generated | `extensions.json` `migrations.$comment` |
| 7 | Four migration ids exist, and none is for 0046 | `migrations.entries`: no entry mentions OTLP, 4318 or otel |
| 8 | `arch-adopt` does not touch `docker-compose.yml` | `docker-compose*.yml` is only in `docker-architect`'s `write_allow` |
| 10 | `compose` asks four questions, and neither case is one of them | `ArchHook.java:740-755`; the `compose` block of `extensions.json` |
| 11 | 0046 weighed only host-side curl, not the app running on the host | `0046-…md:36` ("OTLP ingest is machine-to-machine inside the network"), `:76-77`. The same change added the `localhost` metrics default (`:146`) |
| 12 | Defect 2 affects projects generated before 0046 | 0046 (`6832231`) is already in `v0.1.0`, so only projects from before `v0.1.0` are affected |

Not inputs: claim 4 (refuted, the row is in this repository's `CLAUDE.md`, not in the
generated template), claims 9 and 13 (unproven: the reporter's own compose file, and the
runtime `ConnectException`), and the issue's proposed fix.

Found while designing, not in the issue:

- The collector is the only service the app reaches that publishes nothing. The
  `postgres`, `mysql` and `kafka` service templates all publish a host port, and
  `messaging-architect` makes the Kafka default host-first on purpose (0060,
  lessons-learned-013 § 11).
- The collision 0046 removed the port to avoid is now detected. `compose` question 2 (a
  container from another project holds a port this compose file publishes) and question 1
  (a service in `Created`) came with 0046. 0064 put them behind an exit code on `Stop`.
  Postgres, MySQL and Kafka have lived with the same collision risk under that detection
  since then.
- `jaeger-service.yml.example:11,19` and `grafana-stack-service.yml.example:13` repeat
  0046's "4318 is not published" reasoning.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — concrete symptom | Confirmed rows 1, 2, 10: every host run fails both exports. Rows 7, 8, 12: projects from before `v0.1.0` send container metrics to themselves | Create nothing (anti-pattern 9 does not apply) |
| 2 — trigger | No new trigger. Each item lands on a piece that already owns its territory: `docker-architect` (collector block, step 5), `project-bootstrap` (the fragment), `extensions.json` (`migrations`), `ArchHook.java compose` | 1, 2, 3, 4, 5, 6 as new pieces |
| Host run (user's call) | **Host-first, like Kafka.** The collector publishes `${OTLP_HTTP_PORT:-4318}:4318`. The fragment default stays `localhost:4318`, and the compose `app` service overrides it with `otel-collector:4318` | Export off on the host; keep 0046 and document an override |
| 7 — mandatoriness | **A guarantee.** `compose` gets a fifth question that reads only files, and `compose gate` blocks on it like on question 4. Twice now, a host/container mismatch was promised in prose and shipped (lessons-learned-013 § 11, 014 § 13) | Prose only |
| 8 — destination | **Both.** The templates, `ArchHook.java` and `extensions.json` all travel through `export` | — (makes this record mandatory) |
| Migration (user's call) | **One entry for both gaps.** It adds `OTLP_METRICS_ENDPOINT` to `app` when missing and publishes the collector port. The prompt checks first and stops when the project already has both | Two entries |
| 16 — existing mode | `compose` already owns `docker-compose.yml` and already reads `app` environments (`composeServiceEnv`) and published ports (`composeHostPorts`) | A new mode (anti-pattern 17): this is a fifth question inside `compose`, not a sixth mode |
| 17 — CI | `ComposeGateTest` already proves the gate's exit code for question 4 with no Docker. Question 5 gets cases there | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Host-first collector + migration `otlp-host-run` + `compose` question 5 (data in `extensions.json`) | 8 | **Approved** |
| 2 | Option 1 without question 5 | 6 | Viable fallback. The next drift of the same shape is silent again |
| 3 | Export off on the host: `enabled` toggles default `false`, and compose `app` sets them `true` | 4 | Rejected. The host run exports nothing, silently. Property names change across Boot majors (invariant 8 risk) |
| 4 | Keep 0046 and add an override instruction to `root.CLAUDE.md.example` | 3 | Rejected. Persuasion where a guarantee exists. The default stays broken |

### Option 1 — host-first collector, migration, `compose` question 5 (score 8)

**Motivator:** axis 1 (symptom confirmed on disk), axis 7 (guarantee), host-run answer.

**What changes:**

- `docker-architect/templates/otel-collector-service.yml.example` gets
  `ports: - "${OTLP_HTTP_PORT:-4318}:4318"`. The header comment changes: the port is
  published for the host run, it is variable so a second project can move it, and a
  foreign holder is reported by `compose` questions 1 and 2 and blocked by the gate. The
  same change goes into the "not published" lines of `jaeger-service.yml.example` and
  `grafana-stack-service.yml.example`.
- `project-bootstrap/templates/features/observability/application-observability.yml.example`:
  the comment says the defaults are host-first (they reach the published port) and that
  the compose `app` service overrides both. The values stay the same.
- `docker-architect/SKILL.md` step 5: the defaults are host-first, the `app` overrides are
  container-first, and `compose` checks that the pair agrees.
- `extensions.json` `compose`: new data for question 5. `app_config_globs` lists the
  application config files read for `${VAR:default}` placeholders. `app_service_key`
  (`build`) is how the app's own service is recognized. `host_addresses` is reused.
- `ArchHook.java compose` question 5, file-only, computed before the first `docker` call
  like questions 3 and 4:
  - **(a)** The `app` service sets `VAR` to `<scheme>://<svc>:<port>` or `<svc>:<port>`,
    where `<svc>` is another compose service. The placeholder default must then be a host
    address on a port that `<svc>` publishes. Otherwise a host run cannot reach `<svc>`
    (the collector since 0046).
  - **(b)** A placeholder defaults to a host address on a port that a non-app service
    publishes, and the `app` service does not set `VAR`. Inside the container,
    `localhost` is the app itself (pre-0046 projects and `OTLP_METRICS_ENDPOINT`).
- `extensions.json` `migrations`: entry `otlp-host-run` for all seven blueprints. Its
  prompt checks for both pieces first, and stops when they are already there.
- `ComposeGateTest`: cases for (a) and (b) in both directions.

**Pros:** It reuses the precedent the repository already chose for every other service:
host-first default, container override, and a check that the two agree (0060). It closes
both defects in the owners, not in one project. Question 5 also catches the next service
that ships the same mismatch, which is how Kafka shipped twice.

**Cons:** It reverses half of 0046. A second project running a collector at the same time
now needs `OTLP_HTTP_PORT`. The gate refuses until then, and the block message names the
variable. It touches eight files and the Java.

**Points cut in the rubric:** criterion 9 (the gate blocks on a heuristic: a placeholder
that points at `localhost` on purpose, on a port a compose service publishes, is blocked
by (b)). The scope is narrowed to ports some **other** compose service publishes, which
excludes the app's own URL. Criterion 5 (eight files plus the Java for one contract).

**CI:** `validate` · `hooks-cross-platform` › `compose gate blocks a published service no
host client can reach` (`ComposeGateTest`) gets six cases: (a) blocked and (b) blocked,
each with its fixed variant quiet, plus two false-positive guards. The step name stays: case (a) is still a service no host client can reach.
`design` › `compose service templates merge and parse` already parses the changed
collector template. `design` › `frontmatter schema` already validates the new
`migrations` entry (`MigrationsSchemaTest` covers its shape).

### Option 2 — Option 1 without question 5 (score 6)

The templates and the migration only. It closes #66 and nothing after it. This is the
shape that let Kafka's mismatch ship once in prose and once with the check unrun
(0060, 0064). Cut: criterion 4 (persuasion where a guarantee is available) and
criterion 7 (no CI answer for the pair). **CI:** the same `design` steps. Nothing proves the pair agrees.

### Option 3 — export off on the host (score 4)

Two `enabled` properties default to `false`, and compose `app` sets them `true`. The host
run exports nothing and logs nothing, so the developer sees no spans and no reason. The
property names belong to Spring Boot and move between majors, and writing them from
memory strains invariant 8. 0046's fragment comment exists because "leaving the line out
does not turn metrics off". **CI:** nothing proves that the host gets nothing on purpose.

### Option 4 — keep 0046 and document the override (score 3)

A line in `root.CLAUDE.md.example` telling the developer to write
`docker-compose.override.yml`. The default stays broken. This is prose where a guarantee
is available (anti-pattern 2), and it does nothing for pre-0046 projects. **CI:** nothing
testable; it is prose.

## References

| Claim | Source |
|---|---|
| Host-first default plus a published external port is the repository's answer for a service the host run reaches | `messaging-architect/SKILL.md` step 7; `0060-lessons-learned-013-shipping-defects.md`; `kafka-service.yml.example:27-35` |
| A foreign holder of a published port is detected | `ArchHook.java:740-745` (questions 1, 2); `0064-compose-gate-on-stop.md` |
| A list a mode reads lives in `extensions.json` | `@CLAUDE.md` invariant 10 |
| A migration is the answer for every convention change that leaves existing code behind | `0104-use-case-subpackage-per-aggregate.md`; `0109-migration-for-0097-build-checks.md` |
| A guarantee is bought against an observed failure | `@CLAUDE.md` invariant 6; decision matrix § 2.2, last row |
| A file-only `compose` question survives a daemon that is down | `ArchHook.java` `composeReport`, `tagIssues` computed before `docker compose ps` |

Found while writing, and closed in the same change: the generated `application.yml`
declares `${DB_URL:jdbc:postgresql://localhost:5432/…}`, while `docker-architect` step 5
sets `SPRING_DATASOURCE_URL` on `app`. Spring's relaxed binding replaces the whole
`spring.datasource.url` property, so the placeholder is overridden even though `DB_URL`
is never set. Without that rule, case (b) would have blocked every generated project that
uses Postgres. Question 5 therefore records the dotted property each placeholder sits under,
and it counts `app` setting the variable's name, its env form, or the property's env form.
A test case guards it.

## Propagation

| File | Change |
|---|---|
| `.claude/skills/docker-architect/templates/otel-collector-service.yml.example` | `ports: - "${OTLP_HTTP_PORT:-4318}:4318"`. The header now explains both runs, the LL-008 collision that is now detected, and the way out |
| `.claude/skills/docker-architect/templates/jaeger-service.yml.example` | Dropped "the collector's 4318, which this repo stopped publishing"; Jaeger's own OTLP ports stay unpublished |
| `.claude/skills/docker-architect/templates/grafana-stack-service.yml.example` | Dropped "the collector's own 4318 is not published either" |
| `.claude/skills/project-bootstrap/templates/features/observability/application-observability.yml.example` | Comment: the defaults are host-first, `app` overrides both, `compose` checks the pair. Values unchanged |
| `.claude/skills/docker-architect/SKILL.md` | Step 5: every pair is host-first by default and container-first in `app`. Step 7: the report names the fourth file-only failure |
| `.claude/hooks/ArchHook.java` | `compose` question 5: `placeholderIssues`, `placeholdersIn` (with the YAML property path), `hostPorts`, `envForm`, `composeServiceKeys`. `projectFiles` extracted from `testImagePins` and shared. The section header and the summary line name the fifth question |
| `.claude/hooks/ArchHook.jar` | Rebuilt under JDK 21; `build --verify` green |
| `.claude/schemas/extensions.json` | `compose.app_config_globs`, `compose.app_service_key`, and the `$comment`. `migrations` entry `otlp-host-run`, for all seven blueprints, with a note and a prompt that checks first and stops when nothing is missing |
| `.claude/.ci/ComposeGateTest.java` | Six cases: (a) blocked and fixed, (b) blocked and fixed, relaxed binding quiet, Kafka host-first quiet. `expect` takes an `application.yml` and a needle |
| `.github/workflows/validate.yml` | Comment on the `compose gate` step names question 5 (0110). Step name unchanged: case (a) is still "a service no host client can reach" |
| `CLAUDE.md` | § Commands `compose` row, and the routing row for container and port failures |
| `README.md` | Hook mode table and the `.ci/` tree line |
| `docs/{pt-br,en}/01-*`, `04-arch-doctor.md`, `07-ci-validate.md`, `09-*` | `compose` description, and the `ComposeGateTest` row and diagram node |
| `docs/{pt-br,en}/11-pitfalls.md` | § Compose: a placeholder holds in two places, and the gate blocks an older project after `/arch-adopt` until the migration prompt runs |
| `.claude/decisions/0046-…md` | Note linking here for the host port decision |

Goes to the generated project: **yes.** `export` writes the templates, `ArchHook.java` and
`ArchHook.jar`, and `schemas/extensions.json` (with `migrations`). An exported
`clean-architecture-single-module` passes its own `schema`. Nothing under `.claude/.ci/`
travels.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · hooks-cross-platform › compose gate blocks a published service no host client can reach` | Question 5 blocks (exit 2) on a collector with no published port and on an `app` without `OTLP_METRICS_ENDPOINT`. It stays quiet on the fixed variants, on `SPRING_DATASOURCE_URL` overriding `${DB_URL:…}`, and on Kafka's host-first default. No Docker needed | Green on the tree, 10/10. Red with `placeholderIssues` removed from `composeReport`: the two blocked cases fail by name (`expected exit 2, got 0`). Red with relaxed binding by property removed: the datasource case fails by name (line present) |
| `validate · hooks-cross-platform › committed ArchHook.jar is what the source compiles to` | The jar is the source | `build --verify` green after the red runs were reverted |
| `validate · design › compose service templates merge and parse` | The changed collector template still merges into the base compose | Run locally: 8 templates merge and parse |
| `validate · design › frontmatter schema` | The `migrations` entry and the `compose` block load; `MigrationsSchemaTest` covers the entry's shape | `schema` exit 0; `MigrationsSchemaTest` green |
| `validate · hooks-cross-platform › hook reports a compose image tag that disagrees with src/test` | `testImagePins`, refactored onto `projectFiles`, still reads `src/test` | Green |
