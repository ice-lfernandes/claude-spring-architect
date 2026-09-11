---
paths:
  - "**/adapter/out/persistence/**"
  - "**/infrastructure/persistence/**"
  - "**/db/migration/**"
status: active
---

# Persistence — mapping, migrations, and data access

The database schema is an adapter detail: versioned, explicit, and invisible to the
domain.

## Why this is a rule and not a `CLAUDE.md` section

It's a declarative fact with an identifiable territory — the globs in `paths` above —
and not a universal fact about the repository. It auto-loads when someone touches
persistence, and costs zero context in sessions that don't touch it. Decision recorded
in `@.claude/decisions/0005-persistence-rule-and-design.md`.

## Boundary

- The persistence entity (`@Entity`) lives in the outbound adapter. Never in the domain
- Zero `jakarta.persistence` outside the persistence adapter
- No persistence entity crosses the boundary, inward or outward: the domain ↔ entity
  mapping is explicit and lives in the adapter
- A framework repository interface (Spring Data) is an internal adapter detail. What the
  inner layers know is the outbound port they declare
- Zero `@Transactional` in the adapter. The transaction opens and closes in the
  application layer — `@.claude/rules/architecture-ddd.md`
- One repository per aggregate. A repository that returns pieces of two aggregates is a
  query, and a query has its own port
- No driver, JPA, or framework exception leaves the adapter: the adapter translates it
  into the taxonomy of `@.claude/rules/error-handling.md`
- Translating a constraint violation requires a **flush inside the adapter**
  (`saveAndFlush`). With `save`, the `INSERT` only reaches the database at the
  transaction's commit — outside the adapter's `try` — and the violation escapes
  translation: the client gets 500 where it should get 409. The translation covers both
  cases, constraint violation and optimistic-lock failure, because they arise at
  different moments
- An aggregate that doesn't carry `version` (`@.claude/rules/value-objects.md`'s
  counter-catalog) still needs the **row's real version** at update time. `save()` for
  an update must load-and-mutate the managed entity (`repository.findById`, then mutate
  the fetched instance), never construct a fresh detached one from the domain object via
  `mapper.toEntity`: the primitive `long` field defaults to `0`, so the first update
  succeeds and every one after it false-conflicts against the row's real version with
  `ObjectOptimisticLockingFailureException`, with zero real concurrency involved

## Mapping

- Explicit `fetch = FetchType.LAZY` on **every** `@ManyToOne` and `@OneToOne`: the
  default for these two is `EAGER` and produces silent N+1
- Zero `CascadeType.ALL` and `CascadeType.REMOVE` on an association that leaves the
  aggregate
- Bidirectional relation only when both directions are actually navigated
- The persistence entity has no business logic: fields, annotations, and access only
- `@Enumerated(EnumType.STRING)` always. `ORDINAL` is forbidden — reordering the enum
  corrupts already-stored rows
- Money in `BigDecimal` with explicit `precision` and `scale` on the column. `double`
  and `float` are forbidden for monetary values
- Instant in `Instant` or `OffsetDateTime`, stored in UTC, column with a timezone
  (`timestamptz` on Postgres). `LocalDateTime` only for a date-time that's naturally
  timezone-free
- `equals` and `hashCode` never over a database-generated id: while the entity is
  transient the id is `null` and the contract breaks in a `Set`
- Allowed Lombok annotations: `@.claude/rules/lombok.md`

## Identity and keys

- A technical primary key, with no business meaning. The business key gets its own
  `UNIQUE`
- `GenerationType.IDENTITY` turns off Hibernate's insert batching. Where volume
  justifies batching, use `SEQUENCE` with `allocationSize` equal to the sequence's
  increment
- UUID as primary key: time-ordered version (v7). UUIDv4 fragments the index
- **v7 comes from a library, never hand-written code.** On JDK 21 `UUID.randomUUID()`
  generates v4 and there's no v7 API; the form is
  `Generators.timeBasedEpochGenerator().generate()`
  (`com.fasterxml.uuid:java-uuid-generator`). Hand-implementing RFC 9562 is
  infrastructure code that needs auditing, and a bit-level bug doesn't break any test —
  the value is still a valid UUID, it just stops being sortable. From JDK 25 onward v7
  is in `java.util.UUID` itself and the library stops being necessary
- The id is generated where the aggregate is born — the application layer —, not by the
  database and not by the adapter. The aggregate never exists in a half-created state
  waiting for an id
- Every foreign key has an index. The engine doesn't create one on its own on the
  referencing side, in Postgres or MySQL

## Migrations

- Every schema change is a versioned migration. No manual change, in any environment
- `spring.jpa.hibernate.ddl-auto: validate` in every environment. `update`, `create`,
  and `create-drop` are forbidden, including in development
- An applied migration is immutable. Fix it with a new migration; editing the old one
  breaks the checksum and blocks startup
- Name `V<N>__<verb>_<object>.sql`, all in snake_case
- A destructive change (`DROP COLUMN`, `DROP TABLE`, `NOT NULL` on an existing column)
  ships in a release after the one that stopped using it. Expand first, contract after
- On a table in production: `CREATE INDEX CONCURRENTLY` (Postgres), outside a
  transaction and in its own migration

## Queries

- N+1 is a bug, not tuning. A collection loaded inside a loop is fixed with
  `JOIN FETCH`, `@EntityGraph`, `@BatchSize`, or a dedicated query
- At most one collection per `JOIN FETCH`. Two multiply rows into a cartesian product
- Pagination and collection `JOIN FETCH` don't combine: the engine paginates in memory
  and reads the whole table. Use two queries or `@BatchSize`
- Every listing is paginated. A query that returns an unbounded collection is a bug
- `LIMIT` without a deterministic `ORDER BY` returns an unstable result between runs
- Projection (`record` or interface) when the whole aggregate isn't needed
- Zero value concatenation in JPQL or SQL. Bound parameter, always
- A batch write query (`UPDATE`/`DELETE` in JPQL) bypasses the first-level cache: clear
  the persistence context afterward, or don't use it

## Configuration

- `spring.jpa.open-in-view: false`. Spring Boot's default is `true` and keeps the
  session open until the response is serialized, which hides N+1 and holds connections
- Pool size fixed explicitly. An implicit default value in production is a configuration
  bug, not a decision
- Connection acquisition, validation, and query timeouts defined. Without them a stuck
  query holds the connection until the pool runs out
- `spring.jpa.show-sql` stays `false`. SQL is observed via the Hibernate logger, with a
  level configurable per environment
- Zero credentials in a versioned `application.yml`. They come from the environment

## Admitted exception

The pure read query described in `@.claude/rules/architecture-ddd.md` may return a
projection directly from the adapter, without going through the domain, as long as it's
recorded in an ADR.

## How to verify

```bash
# No persistence type inside the domain. Zero lines is the expected result.
grep -rn "jakarta.persistence" --include=*.java . | grep -v "/persistence/"

# ddl-auto must be validate and open-in-view must be false, in every profile.
grep -rn "ddl-auto\|open-in-view" src/main/resources/

# The rest — boundaries and N+1 — is the architecture test and the integration tests.
./mvnw -q test
```
