# 0046 · Remediation of lessons-learned 008 and 009 — the OTLP observability contract stays inside `docker-architect`, no new extension is created

- **Date:** 2026-09-24
- **Scenario:** remediate `lessons-learned-008.md` (collector pipeline missing `metrics`; host port 4318 collided with an orphan container from a sibling generated project) and `lessons-learned-009.md` (no observability backend/UI exemplar exists; the collector comment promises Jaeger/Tempo/SaaS and ships none).
- **Decision:** Option 1 — no new extension form. Content changes inside `docker-architect` and `project-bootstrap`, five new `templates/*.example`, and a new `compose` mode in `ArchHook.java`.
- **State:** approved by the user, on 2026-09-24. The `ArchHook` change was approved in the same turn, explicitly and by name, which is why it was written here instead of only proposed — see § Note on scope below.

## Note on scope

`claude-code-architect-designer`'s contract says it does not write `.claude/hooks/**`,
and § Out of scope says a conclusion of "this must be a hook" is proposed and stopped on.
That held through Phase 3: the hook was presented as a proposal and nothing was written.
The user then approved Option 1 *and* asked for the hook in the same instruction. The
rule is about not smuggling infrastructure changes in behind a design decision, not about
refusing a direct request — so it was written, and it is called out here so the next
reader does not mistake it for the skill quietly widening its own contract.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — concrete symptom | Two reproduced failures in a real generated project: `OtlpMeterRegistry` 404 on `/v1/metrics` every publish cycle, and an 8-day-old `otel-collector` from a sibling project holding host port 4318 while this project's collector sat in `Created`. Plus one absence the user hit by asking: no dashboard exists, ever, in any generated project | None — the symptom is real, so anti-pattern 9 (building on anticipation) does not apply |
| 2 — trigger | Not a new trigger. Every item lands on a piece that already owns its territory: `docker-architect` owns every compose service block, `project-bootstrap` owns the `application.yml` feature fragment | 1, 2, 3, 4, 5, 6 — nothing new is invoked, so nothing new is created |
| 7 — mandatoriness (detection of the orphan container) | **ArchHook.** The "is every compose service actually `Up`, and is another project publishing my fixed port?" check must not depend on the model remembering to run it | 1 and 3 for that item — it is a hook, § Out of scope: propose and stop |
| 8 — destination | **Both.** `docker-architect` and its `templates/` are copied into the generated project by step 6.7; `project-bootstrap`'s observability fragment is written into every generated project by step 4.7 | None — but it is what makes this record mandatory (matrix § 9, level 2) |
| 9 — integration / ownership | `docker-architect` already owns the collector service block and its init script; `project-bootstrap` already owns the `application.yml` fragment. The LL-008.1 gap is that neither file names the other, so the contract between them has no stated owner | 4 — a rule cannot name either skill (invariant 1), so the cross-reference lives in `docker-architect`'s boundary table |

Decisions taken inside the interview, each closing an option rather than opening one:

| Question | Answer |
|---|---|
| Host port 4318 | Stop publishing it. The app reaches `otel-collector:4318` on the compose network; the host binding exists only to be collided with |
| Collector pipelines | `traces` + `metrics`. Not `logs` — nothing exports it today, and a pipeline nothing feeds is speculative |
| Backend exemplars | Jaeger **and** the Grafana + Tempo + Prometheus stack, as an engine branch, the same shape already used for Postgres/MySQL/Kafka |
| When the backend is chosen | `project-bootstrap` 4.10 does **not** ask. It generates `debug` as today and its final report states the gap and names `/docker-architect` as the way to close it |
| UI host ports | `${JAEGER_UI_PORT:-16686}`, `${GRAFANA_PORT:-3000}` — variable with a default. Deliberately asymmetric with 4318: a UI is opened by a human and needs a predictable number, OTLP ingest is machine-to-machine inside the network |
| Metrics export endpoint | Declared in the fragment (`management.otlp.metrics.export.url`) and wired by `docker-architect` step 5 as `OTLP_METRICS_ENDPOINT`, exactly as the tracing endpoint already is |
| OTLP contract norm | Fix both exemplars + one line in `docker-architect`'s boundary table. Not a new rule, not a new section in `observability.md` |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | create nothing — edit `docker-architect` + `project-bootstrap`, add 5 `templates/*.example`, hand the `Up`/port check to `ArchHook` | 9 | **Recommended** |
| 2 | minimal — the two LL-008 bug fixes only, and cut the comment that promises backends | 6 | Viable fallback, leaves LL-009 open |
| 3 | new rule `.claude/rules/otlp-contract.md` | 4 | Rejected — invariants 1 and 2 |
| 4 | new skill `observability-backend` (Form 1) | 3 | Rejected — invariant 2, `docker-architect` already owns every service block |

### Option 1 — create nothing (score 9)

**Motivator:** axis 2. No item in either lessons file needs a new invocation path. Every
one of them is a wrong or missing line inside a piece that already exists and already
owns that territory.

**Pros:**

- Zero context cost at startup: no new `description` in the listing, no new rule `paths`,
  no new server name.
- Keeps the single owner intact (invariant 2). `docker-architect` already owns every
  compose service block, including the collector's — LL-009's backend is one more branch
  of a catalog that already branches.
- The five new files are `templates/*.example`, which is exactly where invariant 3 puts
  boilerplate: inside the skill that emits it. They cost nothing until merged.
- LL-009's own wording accepts either outcome — ship the templates, or cut the comment.
  This ships them, so the comment stops pointing at a path that does not exist.
- The one item that must never depend on the model's judgment is handed to the one place
  that guarantees execution, instead of being written as prose someone hopes gets read.

**Cons:**

- Five new exemplars to keep current, and the Grafana branch alone is three containers
  with three config files. Real maintenance surface, taken on deliberately.
- The `Up`/port check is proposed, not delivered: until `ArchHook` grows that subcommand,
  LL-008.2's *detection* stays unsolved. Its *cause* is removed the same day, by dropping
  the host binding.
- Removing `ports: - "4318:4318"` is a breaking change for anyone who curls the collector
  from the host today. Intended, and stated in the template comment.

**Points cut in the rubric:** § 8 criterion 5 (maintenance cost) — five exemplars where
there was one. Criterion 4 (enforcement) is not cut: the part that needed a guarantee was
routed to a hook rather than written as prose.

### Option 2 — minimal, LL-008 only (score 6)

Fix the `metrics` pipeline, drop the host port, wire `OTLP_METRICS_ENDPOINT`, and delete
the comment lines that name Jaeger/Tempo/SaaS. Cheapest correct outcome, and LL-009
explicitly allows it ("either the comment goes, or the templates appear").

Rejected as the primary because it leaves the finding that motivated LL-009 intact: no
generated project ever gets an observability UI, and the next session rebuilds the same
ad-hoc vendor recommendation from scratch — with no reason for its answer to match this
one.

**Points cut:** criterion 7 (complete propagation) — one of the two lessons files stays
unremediated.

### Option 3 — new rule `otlp-contract.md` (score 4)

A rule stating that every OTLP signal the application exports has a matching pipeline
declared on the receiver.

Rejected on two invariants. Invariant 1: the useful version of this norm has to name
which piece owns which half of the contract, and `rules/` is a leaf — stripped of those
names it degrades into a sentence nobody can act on. Invariant 2: the territory overlaps
`observability.md`, which already owns vendor wiring, so the two would diverge on the
first edit. Violating an invariant caps the score at 4 (matrix § 8).

### Option 4 — new skill `observability-backend` (score 3)

A Form 1 skill owning the backend choice and writing the Jaeger or Grafana blocks.

Rejected: `docker-architect`'s contract says *no other skill writes a service block into
`docker-compose.yml`* — this would be a second owner of the same YAML, the exact failure
invariant 2 exists to prevent. It also fails matrix § 5's counter-test in the same way
`docker-architect` itself did (`decision 0029`): short choice, shape fits in `templates/`,
diff is a few YAML lines.

## References

| Claim | Source |
|---|---|
| A new piece is only justified by an observed symptom | matrix § 3 and anti-pattern 9 |
| A rule may not name a skill or an agent | `@CLAUDE.md` invariant 1 |
| One norm, one owning file | `@CLAUDE.md` invariant 2 |
| Boilerplate belongs in `templates/*.example` inside the emitting skill | `@CLAUDE.md` invariant 3 |
| A rule that must always hold is a hook, and this skill proposes without writing it | `@CLAUDE.md` invariant 6 · matrix § 2 row 1 · skill § Out of scope |
| Image tags are resolved against the registry, never written from memory | `@CLAUDE.md` invariant 8 · `otel-collector-service.yml.example` header comment |
| Whatever the generated project cites must exist inside it | `@CLAUDE.md` invariant 9 |
| `docker-architect` is the single owner of every compose service block | `docker-architect/SKILL.md` § Contract · § Boundary with neighboring pieces |
| The engine-branch shape (ask, then one template per option) is already the repo's pattern | `docker-architect/SKILL.md` § Service catalog — Postgres / MySQL / Kafka |
| `docker-architect` and its `templates/` reach the generated project | `project-bootstrap/SKILL.md` step 6.7 |
| Violating an invariant caps a score at 4 | matrix § 8 |
| A level-2 record is mandatory when axis 8 is "both" | matrix § 9 |
| Precedent for one record per lessons-learned remediation | `0024`, `0037`, `0039` |

## Propagation

| File | Change |
|---|---|
| `docker-architect/templates/otel-collector-config.yml.example` | Add the `metrics` pipeline next to `traces`, same `debug` exporter. Rewrite the header comment so the exporter-swap promise names the templates that now exist |
| `docker-architect/templates/otel-collector-service.yml.example` | Delete `ports: - "4318:4318"`. Comment stating the app reaches `otel-collector:4318` on the compose network, and that a host binding is what collided across sibling projects |
| `docker-architect/templates/jaeger-service.yml.example` | New — all-in-one, OTLP ingest, UI on `${JAEGER_UI_PORT:-16686}`. Ships the collector `exporters:`/`pipelines:` fragment that points the collector at it |
| `docker-architect/templates/grafana-stack-service.yml.example` | New — Tempo + Prometheus + Grafana, UI on `${GRAFANA_PORT:-3000}`. Same companion collector fragment |
| `docker-architect/templates/tempo-config.yml.example` | New — init script for Tempo, mounted read-only |
| `docker-architect/templates/prometheus-config.yml.example` | New — init script for Prometheus |
| `docker-architect/templates/grafana-datasources.yml.example` | New — Grafana datasource provisioning for Tempo and Prometheus |
| `docker-architect/SKILL.md` | Step 2: backend question on manual invocation when `observability` is active. Step 5: wire `OTLP_METRICS_ENDPOINT`. Step 6: the three new init scripts. § Service catalog: Jaeger and Grafana-stack rows, and the collector row stops calling the absence of a vendor final. § Boundary: the line naming who owns each half of the OTLP signal contract |
| `project-bootstrap/templates/features/observability/application-observability.yml.example` | Add `management.otlp.metrics.export.url: ${OTLP_METRICS_ENDPOINT:http://localhost:4318/v1/metrics}` |
| `project-bootstrap/SKILL.md` step 4.10 | Final report states the gap: collector exports to `debug`, there is no UI, `/docker-architect` adds one |
| `project-bootstrap/SKILL.md` step 6.7 | The `docker-architect` row lists `{postgres,mysql,kafka}` templates only — stale before this change, wrong after it |
| `.claude/hooks/ArchHook.java` | New `compose` mode, and a `runTimed` helper. Checks that every compose service is `running` rather than `created`/`exited`, and that no container from another project publishes a host port this project's compose declares. Folded into `doctor`, so `/arch-doctor` reports it too. Never blocks; each `docker` call is capped at `DOCKER_TIMEOUT` = 10s, because a diagnostic that hangs is worse than one that says "not checked" |
| `.claude/skills/arch-doctor/SKILL.md` | `description` names the compose check — the skill's body already renders whatever `doctor` prints, so nothing else changed |
| `.claude/skills/docker-architect/SKILL.md` `description` | Names the backend choice, so the skill fires on "dashboard", "trace UI", "Grafana", "Jaeger" |
| `CLAUDE.md` | Commands table: the `compose` invocation. Routing table: the `docker-architect` row now names the backend choice, plus a row sending a "port already allocated" / "container started but isn't answering" symptom to the hook |

Goes to the generated project: **yes** — `docker-architect` and its `templates/` via step
6.7, the observability fragment via step 4.7. No new copy step is needed; step 6.7's
template list is corrected rather than extended.
