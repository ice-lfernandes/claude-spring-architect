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

!`find docs/use-cases -mindepth 1 -maxdepth 1 -type d -name 'UC-*' 2>/dev/null | sort`

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
| **this skill** | After the domain partial | `20-persistencia.md`, migration SQL inside it |
| `rest-api-architect` | Before this one | `30-rest.md` — transport, plus the schema requirements it creates |
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

2. **Survey what already exists.** Look for entities, repositories, and migrations in
   the project. A table that already exists gets altered; it isn't recreated. The
   mother spec's REUSE state overrides intuition. Same check for `idempotency_keys`: it's
   shared by the whole application, not per use case — if a prior pass already created
   it, this one reuses it and doesn't touch it again.

   ```bash
   ls db/migration/ src/main/resources/db/migration/ 2>/dev/null
   grep -rln "@Entity" --include='*.java' src/ 2>/dev/null
   grep -rl "idempotency_keys" db/migration/ src/main/resources/db/migration/ 2>/dev/null
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

The exemplars in `templates/` are **reference for form**, not files to copy. It's the
executor agent that reads them when generating code.

## Contract

**Reads** `docs/use-cases/UC-NNN-<slug>/00-caso-de-uso.md` and `10-dominio.md`
(mandatory — stops without the second), `@.claude/rules/persistence.md`,
`@.claude/rules/architecture-ddd.md` (Adapters and Composition sections),
`@.claude/rules/naming.md`, `@.claude/rules/error-handling.md`,
`@.claude/rules/lombok.md`, `@.claude/rules/value-objects.md`,
`@.claude/rules/api-rest.md` § Idempotency (only when step 4a applies), and the active
blueprint's `packages.map`. Also reads `30-rest.md` for an HTTP-triggered case —
mandatory then, its schema requirements are this pass's input.

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

**Does not collide with `domain-modeling`**: that one declares the output port, this one
says how it's served. The port's signature belongs to the other; if it needs to change,
report the divergence instead of rewriting it.
