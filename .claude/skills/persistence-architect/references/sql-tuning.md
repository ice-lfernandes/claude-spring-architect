# Diagnosis and tuning — from symptom to fix

Reference for the `persistence-architect` skill. Not a rule: rules that always apply are
in `@.claude/rules/persistence.md`. This is what you do **once it already hurts**.

Mandatory order: **measure, fix, measure again**. Tuning without measuring before and
after is guessing with a maintenance cost.

## 1 · See what the application actually does

Before looking at the database's plan, look at the query count. Most JPA slowness isn't
one slow query — it's five hundred fast ones.

```properties
# Development only. `bind: TRACE` shows the bound values — never in production.
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.orm.jdbc.bind=TRACE
spring.jpa.properties.hibernate.generate_statistics=true
```

In an integration test, count the statements instead of reading them:

```java
var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
long before = stats.getPrepareStatementCount();
// ... exercise the use case
assertThat(stats.getPrepareStatementCount() - before).isEqualTo(2);
```

This assertion is the only defense that survives a refactor. An N+1 fixed by hand comes
back on the next change if nothing pins it down.

## 2 · Symptom catalog

| Symptom | Likely cause | Fix |
|---|---|---|
| One query, then N identical ones with `where id = ?` | Collection or `@ManyToOne` loaded inside a loop | `JOIN FETCH`, `@EntityGraph`, or `@BatchSize` on the association |
| Number of returned rows explodes | Two collection `JOIN FETCH`s in the same query | One collection per query. Two queries, or `@BatchSize` for the second |
| Slow only with pagination, and memory rises | `JOIN FETCH` of a collection with `Pageable` — the engine paginates in memory | Two phases: paginate the ids, then load the graph by `id in (...)`. `fail_on_pagination_over_collection_fetch: true` turns this into an error |
| `LazyInitializationException` | Session closed before access | Load what you need inside the transaction. **Never** with `open-in-view: true` |
| `UPDATE` on columns nobody changed | Entity marked `dirty` by reference comparison | Check the type mapping; avoid `@DynamicUpdate` as a first response — it treats the symptom, not the cause |
| Slow batch insert, one round trip per row | `GenerationType.IDENTITY`, or `batch_size: 0` | `SEQUENCE` with `allocationSize`; `batch_size` + `order_inserts` |
| Fast query in development, slow in production | Different volume and plan | `EXPLAIN ANALYZE` with production data. Stale statistics: `ANALYZE <table>` |
| Timeouts under load, low CPU | Pool exhausted, not the database | Check active connections vs `maximum-pool-size`; look for a long transaction holding a connection |
| Slow `DELETE` on a small table | Foreign key without an index on the referencing side | Index on the FK column |
| Index exists and isn't used | Function or cast on the column in `WHERE` (`lower(email) = ?`) | Functional index, or normalize the value before storing |

## 3 · Reading an execution plan

```sql
EXPLAIN (ANALYZE, BUFFERS) SELECT ...;
```

What to look for, in order of impact:

1. **`Seq Scan` on a large table** with a selective filter → missing index, or the index
   isn't usable by the predicate.
2. **Estimated `rows` far off from `actual rows`** → stale statistics. The plan was
   chosen with wrong information; run `ANALYZE`.
3. **`Nested Loop` with many iterations** → the engine expected few rows from the outer
   side. Same cause as point 2.
4. **`Sort` with `external merge Disk`** → insufficient `work_mem` for the sort.
5. **`Filter` with a high `Rows Removed by Filter`** → it's reading a lot to throw away
   almost all of it. Composite index with the columns in the right order.

In a composite index, column order is equality predicates first, range last.
`(department_id, created_at)` serves `department_id = ? AND created_at > ?`;
`(created_at, department_id)` does not.

## 4 · Indexes — when yes and when no

An index speeds up reads and costs on every write. Create one when:

- It serves an equality or range predicate of a query that actually exists
- It serves a foreign key
- It backs a business `UNIQUE`

Don't create one when: the table has a few thousand rows and isn't growing; the column
has very low cardinality and the filter isn't selective; the query is hypothetical. An
index without a query that uses it is pure write cost — and nobody deletes it afterward.

## 5 · Transactions and locks

- The transaction starts as late as possible and ends as early as possible. A call to
  an external service inside a transaction holds the connection for the duration of the
  network round trip
- Optimistic locking (`@Version`) by default. Pessimistic (`PESSIMISTIC_WRITE`) only
  when the conflict is frequent **and** the cost of retrying is high
- A deadlock almost always comes from two transactions touching the same rows in
  different orders. Order the accesses (by id, for example) and the deadlock disappears
- Isolation level: the database's default. Raising it to `SERIALIZABLE` without an
  identified anomaly trades a problem you don't have for contention you now do

## 6 · Testing against the real database

H2 in compatibility mode doesn't reproduce Postgres's plans, types, or constraints.
Testcontainers with the same engine version as production is what gives the test value.
Without that, the test proves the code compiles, not that the schema works.
