---
name: persistence-architect
description: >
  Designs the persistence layer of an already-modeled use case — tables, aggregate
  mapping, migrations, queries, indexes, and datasource configuration — into the
  `20-persistencia.md` partial. Use when the request involves modeling the database,
  mapping an aggregate to JPA, writing or reviewing migrations, deciding indexes and
  keys, diagnosing N+1 or slow queries, or tuning the pool and datasource properties.
  Piece of the `/new-feature` pipeline: requires `10-dominio.md` in the given folder and
  stops without it.
argument-hint: "[path of the UC-NNN-<slug> folder]"
allowed-tools: Read, Write, Glob, Grep, Bash, AskUserQuestion
---

## Available specs

!`find "${CLAUDE_PROJECT_DIR:-.}/docs/use-cases" -mindepth 1 -maxdepth 1 -type d -name 'UC-*' 2>/dev/null | sort`

Empty above → none yet, run `/use-case-design` first. (`find`, not an `ls` glob: under zsh an unmatched glob
aborts the command before any fallback runs.)

## Target

$ARGUMENTS

---

# Persistence Architect

Designs **how the aggregate reaches disk and comes back**: what tables exist, what
columns and types, what indexes, what migration, what queries, and what the datasource
needs configured. What `domain-modeling` left as an output port, this skill gives body
to.

**Entry rule: without `10-dominio.md`, there's nothing to persist.** This skill reads
`docs/use-cases/UC-NNN-<slug>/00-caso-de-uso.md` and `10-dominio.md` and treats them as a
contract. Without the domain partial, it stops and tells you to run `/domain-modeling` —
designing tables before the aggregate has a boundary produces a schema that describes the
form, not the business.

**Two more partials stop it, each under its own condition**, for the same reason: both
carry schema requirements this pass has to honor, and a schema designed without them is a
schema that gets revised. An HTTP-triggered case with no `30-rest.md` → run
`/rest-api-architect` first. A case whose `10-dominio.md` Events block names external
(Kafka) delivery with no `25-mensageria.md` → run `/messaging-architect` first. Step 1
states what each list contains.

**Exit rule: it writes only under `docs/`.** It emits `20-persistencia.md`, and the
migration SQL goes **inside it** as a code block with its target file name and path —
never as a file under `src/`. The migration file and the Java classes come from the
executor agent, which reads the partial and the `templates/` exemplars. Inherits D15 —
`@.claude/decisions/0003-skill-domain-modeling.md`.

**Rule rule: the rules don't live here.** Lazy fetch, `ddl-auto: validate`, migration
immutability, `open-in-view: false`, and the rest are
`@.claude/rules/persistence.md`. This skill applies them and cites them; it doesn't
reproduce them.

## How it's invoked

Two paths, and both matter: `/persistence-architect` by hand, or chained by
`/new-feature` once that orchestrator exists. That's why it does **not** carry
`disable-model-invocation` — that field hides the skill from the model, and a skill the
model can't see is a skill the orchestrator can't call.

The guard against out-of-order firing isn't the frontmatter: it's the **entry rule**
above. Without the previous partial, the skill stops and says what needs to run first.
Recorded in `@.claude/decisions/0007-pipeline-skills-invocation.md`.

## Why this isn't a subagent

It's a procedure whose step 3 goes back to the user to ask what no earlier spec fixes —
which database engine, which queries the use case actually makes, what row volume is
expected. A subagent doesn't see the conversation. Decision recorded in
`@.claude/decisions/0005-persistence-rule-and-design.md`.

## Boundary with neighboring skills

The division is by **moment and artifact**, not technology:

| Piece | When it acts | What it produces |
|---|---|---|
| `use-case-design` | Before the domain exists | `00-caso-de-uso.md` — boundary and canonical names |
| `domain-modeling` | After the mother spec | `10-dominio.md` — aggregate, invariants, ports |
| `rest-api-architect` | Before this one | `30-rest.md` — transport, plus the schema requirements it creates (block 4) |
| `messaging-architect` | Before this one, only when an event leaves over a broker | `25-mensageria.md` — publication form, plus the schema requirements it creates (§ 6: outbox, dedupe table) |
| **this skill** | After both, so every requirement is on the table before the first column is named | `20-persistencia.md`, migration SQL inside it |
| `test-architect` | After all of them | `40-testes.md` |

If the aggregate has no written invariants yet, it isn't this skill. If the problem is a
slow query in code that already exists and there's no new use case, skip steps 1 and 2
and go straight to step 6 (diagnosis) — `references/sql-tuning.md`.

## Procedure

1. **Read the specs.** `00-caso-de-uso.md` and `10-dominio.md` from the folder in
   `$ARGUMENTS`. Without the second, stop. Extract: aggregate root, fields and types,
   value objects, declared output ports, and the component table with NEW/CHANGE/REUSE
   state. Also read `30-rest.md` when the use case has an HTTP trigger — in `/new-feature` it
   always exists by now, because REST runs first. Its block 4 `Schema requirements` list
   is input to this pass: the shared idempotency table (step 4a) and anything else there
   gets designed now, not in a second pass. An HTTP-triggered case whose `30-rest.md`
   doesn't exist yet → stop and tell the caller to run `/rest-api-architect` first.

   Also read `25-mensageria.md`, **mandatory on the same terms as `30-rest.md`**: when
   `10-dominio.md`'s Events block names external (Kafka) delivery and the messaging partial
   isn't in the folder → stop and tell the caller to run `/messaging-architect` first. In
   `/new-feature` it always exists by now, because messaging runs before this skill. Its
   § 6 `Schema requirements` list is input to this pass exactly as block 4 of `30-rest.md`
   is: the shared outbox table under publication Form B (step 4b), a dedupe table for a
   consumer, and anything else listed there gets designed now, not in a second pass.

   A domain partial whose Events block is "none" or in-process means no messaging partial
   exists and none is expected — read nothing, stop for nothing.

2. **Survey what already exists.** Look for entities, repositories, and migrations in
   the project. A table that already exists gets altered; it isn't recreated. The
   mother spec's REUSE state overrides intuition. Same check for `idempotency_keys`: it's
   shared by the whole application, not per use case — if a prior pass already created
   it, this one reuses it and doesn't touch it again. Same for `outbox_events`: one per
   project, whatever the number of events.

   ```bash
   ls db/migration/ src/main/resources/db/migration/ 2>/dev/null
   grep -rln "@Entity" --include='*.java' src/ 2>/dev/null
   grep -rl "idempotency_keys" db/migration/ src/main/resources/db/migration/ 2>/dev/null
   grep -rl "outbox_events" db/migration/ src/main/resources/db/migration/ 2>/dev/null
   ```

3. **Interview — only what the specs don't fix.** `AskUserQuestion`, at most 4 questions
   per call. Don't re-ask what `00-caso-de-uso.md`, `10-dominio.md`, or `30-rest.md` already answered —
   the collection's growth, above all.

   | Axis | Decides |
   |---|---|
   | Engine and version (Postgres, MySQL, Oracle, H2 only in tests) | Column types, migration syntax, whether `CONCURRENTLY` exists |
   | Expected table volume in 12 months | Whether the index is mandatory or premature, whether `SEQUENCE` beats `IDENTITY` |
   | Queries the use case actually makes, and which fields it filters by | Indexes, projections, what needs `JOIN FETCH` |
   | Concurrency on the same aggregate | Optimistic lock (`@Version`) or none |
   | Retention and deletion | Physical `DELETE` or a status column |
   | Table already exists in production | Mandatory expand/contract, destructive migration deferred |

4. **Design the schema.** One table per aggregate root; child entities of the same
   aggregate go into their own tables with a foreign key to the root. A simple value
   object becomes a column or `@Embeddable`, never its own table — if it needs its own
   table and its own identity, it wasn't a value object
   (`@.claude/rules/value-objects.md`). Fix in writing, column by column: name, type,
   nullability, default, `UNIQUE`, foreign key. A type that isn't Hibernate's default
   inference for the field (`char(n)`, `smallint`) names its `@JdbcTypeCode` in the
   same row, and an assigned id names `Persistable` — both per
   `@.claude/rules/persistence.md` § Mapping and § Identity and keys.

   **4a. Idempotency, when `30-rest.md` requires it and step 2 found no
   `idempotency_keys` table yet.** Not per-aggregate: one table, shared by every
   endpoint that needs `Idempotency-Key`, modeled once and reused afterward. Shape in
   `templates/IdempotencyKeyTable.sql.example` (schema) and
   `templates/IdempotencyKeyStore.java.example` (entity, Spring Data repository, and the
   adapter implementing the port), plus `templates/IdempotentExecution.java.example` (the
   application component that owns the two-transaction shape) — the transactional half
   of the mechanism whose structural half is `rest-api-architect`'s `IdempotencyKeyInterceptor.java.example`.
   Column set and TTL floor come from `@.claude/rules/api-rest.md` § Idempotency; don't
   redecide them here.

   **4b. Outbox, when `25-mensageria.md` fixes publication Form B and step 2 found no
   `outbox_events` table yet.** Exact mirror of 4a, and for the same reason: one table for
   the whole project, shared by every event, modeled once and reused afterward — never one
   outbox per aggregate or per event type. Shape in
   `templates/OutboxEventTable.sql.example` (schema, partial index, retry columns, and the
   prune as a commented `DELETE`) and `templates/OutboxEventStore.java.example` (entity,
   Spring Data repository, and the adapter implementing `OutboxRelayGateway`). The column
   set is the requirement row `25-mensageria.md` § 6 states; the criterion that made Form
   B apply is `@.claude/rules/messaging.md` § Publication timing, and it isn't re-litigated
   here — `messaging-architect` owns it.

   Two boundaries this step does **not** cross. `OutboxRelayGateway` and
   `PendingOutboxEvent` are declared in `messaging-architect`'s
   `templates/OutboxRelayPublisher.java.example`, which owns the relay consuming them: this
   step implements the port, it doesn't re-declare it. And the relay itself, the appender,
   and `app.outbox.*` are that skill's too — what lands in `20-persistencia.md` is the
   table, its mapping, and the adapter.

   **Retention is the one open value, and it gets asked.** The exemplar's `7 days` (matching
   `app.outbox.prune-after: P7D`) is a starting point, not a default to adopt in silence:
   put it to the user with `AskUserQuestion` — the default first, plus real alternatives —
   and write the chosen window and its reason into the partial's § 1. Never a single-option
   question: with nothing to choose between, decide and record instead of asking.

5. **Fix the migration in the partial.** Name per `@.claude/rules/persistence.md`
   § Migrations, with `<N>` following the highest one found in step 2, and the target
   path under the module the blueprint gives the persistence role. The SQL goes as a
   fenced `sql` code block in block 4 of `20-persistencia.md`, headed by that path. Shape in
   `templates/V1__create_table.sql.example`. One migration per logical change; never
   edit one already applied. **Don't create the `.sql` file** — anything under `src/`
   belongs to the executor, which materializes it from this block.

6. **Fix the queries and access plan.** For each output port in `10-dominio.md`: the
   query, the fetch strategy, the index that serves it, and whether it's paginated.
   Every collection read inside a loop is an N+1 and is resolved here, not in code
   review — symptom catalog and fixes in `references/sql-tuning.md`.

7. **Fix the configuration.** The datasource and JPA properties for this project, from
   `templates/application-persistence.yml.example`. The mandatory values are the rule
   (`@.claude/rules/persistence.md` § Configuration); what this skill decides is sizing
   — pool size, timeouts, `batch_size` — based on the volume answered in step 3.

8. **Write the partial.** `docs/use-cases/UC-NNN-<slug>/20-persistencia.md`, from
   `templates/persistence-spec.md.example`. Five blocks, all mandatory.

9. **Check the engine has a container.** `grep -A2 "^services:" docker-compose.yml`
   for the engine chosen in step 3. Missing (and the engine isn't H2) → invoke
   `docker-architect` with this UC's folder, so the dev-time container matches the
   schema just designed. Don't edit `docker-compose.yml` here — that skill is its
   single owner.

10. **Report and stop.** Path of the partial written, divergences from `10-dominio.md`,
    whether `docker-architect` ran, and what's missing for the folder to be complete
    (`30-rest.md`, `40-testes.md`). Don't invoke anyone else.

## What the partial contains

Five blocks. An empty block is written as "none" — deleting it hides a question nobody
asked.

| Block | Fixes | Form exemplar |
|---|---|---|
| Schema | Tables, columns, types, nullability, `UNIQUE`, foreign keys, indexes | `V1__create_table.sql.example` |
| Mapping | Aggregate → persistence entity, field by field; what's `@Embeddable`; value object translation; `@JdbcTypeCode` and `Persistable` where they apply | `JpaEntity.java.example` |
| Adapter and ports | Each port from `10-dominio.md`, the query serving it, the fetch strategy | `RepositoryAdapter.java.example` · `SpringDataRepository.java.example` |
| Migrations | New files, order, and the expand/contract pair when the table already exists | `V1__create_table.sql.example` |
| Configuration | Datasource and JPA properties, with the decided value and why | `application-persistence.yml.example` |
| Idempotency (only when `30-rest.md` requires `Idempotency-Key`) | The shared table, entity, repository, adapter, and application component — modeled once, reused by every later use case | `IdempotencyKeyTable.sql.example` · `IdempotencyKeyStore.java.example` · `IdempotentExecution.java.example` |
| Outbox (only when `25-mensageria.md` fixes publication Form B) | The shared `outbox_events` table, its mapping, the adapter implementing `OutboxRelayGateway`, and the retention chosen for the prune — modeled once, reused by every later event | `OutboxEventTable.sql.example` · `OutboxEventStore.java.example` |

The exemplars in `templates/` are **reference for form**, not files to copy. It's the
executor agent that reads them when generating code.

## Contract

**Reads** `docs/use-cases/UC-NNN-<slug>/00-caso-de-uso.md` and `10-dominio.md`
(mandatory — stops without the second), `@.claude/rules/persistence.md`,
`@.claude/rules/architecture-ddd.md` (Adapters and Composition sections),
`@.claude/rules/naming.md`, `@.claude/rules/error-handling.md`,
`@.claude/rules/lombok.md`, `@.claude/rules/value-objects.md`,
`@.claude/rules/api-rest.md` § Idempotency (only when step 4a applies),
`@.claude/rules/messaging.md` § Publication timing (only when step 4b applies), and the
active blueprint's `packages.map`. Also reads two partials whose schema requirements are
this pass's input, each mandatory under its own condition: `30-rest.md` for an
HTTP-triggered case (block 4), and `25-mensageria.md` whenever `10-dominio.md`'s Events
block names external delivery (§ 6 — the shared outbox table under Form B, a dedupe table
for a consumer). Missing either one where it's required stops this skill instead of
starting a design that a later pass would have to revise.

**Writes** `docs/use-cases/UC-NNN-<slug>/20-persistencia.md`. Nothing else — the
migration SQL lives inside it.

**Never writes under `src/`.** Not a migration, not a class, not a property file. The
executor materializes every file there from this partial.

**Does not write Java code.** The entities, adapters, and repositories come from the
executor agent.

**Does not edit `docker-compose.yml`.** When step 9 finds the chosen engine has no
container yet, it invokes `docker-architect` instead of writing the service block
itself — single owner, see that skill's Contract.

**Does not decide** the use case boundary (`00-caso-de-uso.md`), the domain model
(`10-dominio.md`), the transport (`30-rest.md`), or the tests (`40-testes.md`). Doesn't
touch `.claude/rules/**`.

**Does not collide with `messaging-architect`**: that one decides the publication form and
owns the relay, the appender, `app.outbox.*`, and the declaration of `OutboxRelayGateway` /
`PendingOutboxEvent`. This one owns the `outbox_events` table, its mapping, and the adapter
implementing that port. A form choice is never made here — an outbox requirement that
contradicts `25-mensageria.md` is a divergence to report.

**Does not collide with `domain-modeling`**: that one declares the output port, this one
says how it's served. The port's signature belongs to the other; if it needs to change,
report the divergence instead of rewriting it.
