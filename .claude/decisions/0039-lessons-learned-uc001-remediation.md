# 0039 · Remediation of the UC-001 executor run — existing owners fixed, plus ArchUnit guards

- **Date:** 2026-09-16
- **Scenario:** "promova o ajuste no meta-repo" over the demo project's `LESSONS-LEARNED.md`,
  written after `java-spring-boot-developer` took `UC-001-create-customer` to a green
  `./mvnw verify` — five gaps the meta-repo did not catch, all patched only in the
  generated project's code.
- **Decision:** Option 1 — fix the existing owners in four batches (observability,
  persistence, Jackson 3, idempotency) plus two ArchUnit guards. No new skill, agent, or
  rule; two new exemplars.
- **State:** approved by Lucas Fernandes, on 2026-09-16 — including the blueprint edits
  batch C needs, outside this skill's usual contract

## Context

Claims from the document checked against this repository before proposing anything:

| Lesson | Verification | Result |
|---|---|---|
| 1 | `project-bootstrap/references/dependency-catalog.md:17` | Confirmed: `observability` lists only `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`. Nothing registers the `Tracer` bean on Boot 4. |
| 1 | Why step 8 of `project-bootstrap` didn't catch it | No class injects `Tracer` until `rest-api-architect`'s `ApiExceptionHandler` exists, so a bare `contextLoads` stays green. It is also `@EnabledIf("dockerAvailable")`, skipped locally. A smoke check must **assert the bean**, not just start the context. |
| 2 | `persistence-architect/templates/JpaEntity.java.example:51,76` | Worse than reported: the exemplar itself has an assigned `UUID` id and `@Version long` (primitive). Spring Data treats a primitive version as absent and falls back to the id check, so `save()` → `merge()` here too. |
| 2 | `rules/persistence.md` § Identity and keys | Confirmed: no `Persistable` clause. |
| 3 | `persistence-architect/templates/IdempotencyKeyStore.java.example` | Origin of the bug is the exemplar: `@Lob` on `response_body` (`text` in `IdempotencyKeyTable.sql.example`), no `@JdbcTypeCode` for `char(64)` / `smallint`. The executor copied it. |
| 4 | `grep -rn tools.jackson .claude` | Zero hits. `architecture-ddd.md:19`, `test-architect/templates/ArchitectureTest.java.example:63`, three blueprints' forbidden prefixes, `project-bootstrap/SKILL.md:294` and the generated `forbidden-imports.txt` only bar `com.fasterxml.jackson`. A Jackson 3 import in the domain passes every guard silently. Jackson 3 keeps annotations in `com.fasterxml.jackson.annotation`, so both prefixes stay. |
| 5 | `IdempotencyKeyStore.java.example` header + `api-rest.md:172` | The exemplar already says "call from the application service, same transaction". But `begin()` catches the PK violation and then runs `findById` in that same transaction — on Postgres the transaction is already aborted. The single-transaction shape is unimplementable as written, not only a rule conflict. |

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | Five concrete failures in one real run (context startup, swapped idempotency statuses, schema validation, namespace detour, unilateral design deviation) | "create nothing" |
| 2 — Trigger | Touching persistence/REST/domain files; bootstrap generation | 2 (manual skill), 6 |
| 5 — Nature | Declarative facts (mapping, keys, idempotency shape) + exemplar corrections | New skill or agent |
| 7 — Mandatoriness | Persistable, `@Lob`, Jackson-in-domain are correctness bugs with no compiler signal | Pure prose → ArchUnit guard added in the generated project |
| 8 — Destination | Both — rules via 6.6, skills via 6.7, ArchUnit exemplar via `archunit-installer` | — |
| L5 shape | Claim commits in its own transaction; business effect + completion share the application transaction; orphan `IN_PROGRESS` answers 409 until the lease expires, accepted in writing; the use case receives the key | "three transactions", "single transaction via `ON CONFLICT`" |
| L2 scope | Every entity with an assigned `@Id` implements `Persistable<T>`, regardless of `@Version` | conditional rule |
| L1 smoke | Yes — bootstrap verifies the `Tracer` bean, not only the dependency | catalog-only fix |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Fix existing owners + ArchUnit guards in `ArchitectureTest.java.example` | 9 | **Approved** |
| 2 | Fix existing owners, prose only | 8 | Rejected — persuasion where a build guard was available |
| 3 | Option 1, but idempotency moves to a new `rules/idempotency.md` | 6 | Rejected — new cross-layer rule with no precedent |
| 4 | New `ArchHook.java` check mode for `@Lob` / `Persistable` | 4 | Rejected — out of this skill's scope; ArchUnit already runs in the generated build |
| 5 | create nothing | 1 | Rejected — every lesson is a reproduced failure or an unguarded bug |

### Option 1 — fix owners + ArchUnit guards (score 9)

**Motivator:** axis 7 — three of the gaps are correctness bugs with no compiler or test
signal; axis 8 — everything already travels to the generated project.

Batches, in dependency order:

- **A · Observability (L1).** `dependency-catalog.md` `observability` row adds the Boot
  glue starter, artifactId confirmed from the Initializr/Maven Central response, never
  memory (invariant 8). `project-bootstrap` step 8: `dependency:tree` check for it, and
  when `observability: true` the generated `*ApplicationTests` asserts the `Tracer` bean
  (template under `templates/features/observability/`).
- **B · Persistence (L2, L3).** `rules/persistence.md`: § Identity and keys — assigned
  `@Id` ⇒ `Persistable<T>` with a transient `isNew` flipped by `@PostLoad`/`@PostPersist`;
  § Mapping — a column type that isn't Hibernate's default inference for the Java type
  (`char(n)`, `smallint`, `text` vs `varchar`) carries `@JdbcTypeCode`; `@Lob` never maps
  a Postgres `text`. Fix `JpaEntity.java.example` and `IdempotencyKeyStore.java.example`.
  Executor block 2 validation line cites both.
- **C · Jackson 3 (L4).** `architecture-ddd.md` § Domain names both roots. Add
  `tools.jackson..` to `ArchitectureTest.java.example`, `project-bootstrap/SKILL.md`
  package-info and forbidden-imports generation, `module.CLAUDE.md.example`, and the
  blueprints that list `com.fasterxml.jackson..`.
- **D · Idempotency (L5).** `api-rest.md` § Idempotency replaces "same transaction" with
  the approved shape and names the availability gap. `IdempotencyKeyStore.java.example`
  splits `claim` (own `saveAndFlush`, no catch-then-query in a live transaction) from
  `complete` (joins the application transaction); lease reclaim for stale `IN_PROGRESS`.
  `domain-modeling`'s `UseCasePort.java.example` shows the command carrying a
  transport-agnostic idempotency context; `rest-api-architect` interceptor header and
  `rest-spec.md.example` follow.
- **ArchUnit guards** in `ArchitectureTest.java.example`: `@Entity` with `@Id` and no
  `@GeneratedValue` must implement `Persistable`; no `@Lob` on `String` fields; domain
  free of `tools.jackson..`.

**Pros:** every fix lands in the file that already owns the norm or exemplar (invariant
2); no new piece; the three silent bugs get build-time enforcement in the generated
project (invariant 6 spirit).

**Cons:** batch C edits `.claude/blueprints/**`, which this skill's contract excludes —
needs explicit approval as data maintenance (invariant 7: a data fix, not a skill
change). Batch D widens the use case signature for idempotent operations. Stored replay
body is serialized by the adapter; how it reaches `complete` is settled in the exemplar.

**Points cut in the rubric:** 5 (maintenance) — ~20 files touched in one change.

### Option 2 — prose only (score 8)

Same batches A–D, no ArchUnit rules. Cut: 4 (enforcement) — relies on the executor
reading `persistence.md` where a build guard was available; 5.

### Option 3 — new `rules/idempotency.md` (score 6)

Idempotency spans REST, application and persistence; a dedicated owner would stop
`api-rest.md` from dictating transaction shape. Cut: 3 (another always-routed rule), 5
(new file + `00-index.md` + step 6.6 row), 6 (the three existing layer rules already
split by territory — no precedent for a cross-layer rule).

### Option 4 — `ArchHook.java` check (score 4)

Hook-level grep for `@Lob`/`Persistable`. Out of scope for this skill (§ Out of scope);
duplicates what ArchUnit does inside `./mvnw verify` with type information a grep lacks.

## References

| Claim | Source |
|---|---|
| Fix lands in the owning file, never a second copy | `@CLAUDE.md` invariant 2 |
| Must-hold rules become enforcement, not prose | `@CLAUDE.md` invariant 6 |
| Blueprints are data; editing them needs no skill change | `@CLAUDE.md` invariant 7 · `@.claude/blueprints/_schema.md` |
| Versions and artifactIds resolved at runtime | `@CLAUDE.md` invariant 8 · `dependency-catalog.md` § Spring Boot 4 renamed starters |
| Rules, dev skills and ArchUnit exemplar reach the generated project | `@CLAUDE.md` invariant 9 · `project-bootstrap/SKILL.md` steps 6.6, 6.7 |
| Batching remediation, no new piece | `0024-lessons-learned-001-remediation.md` · `0037-lessons-learned-005-remediation.md` |
| Idempotency split structural/transactional | `0006-rest-api-architect-design.md` (P5(b)) |

## Implementation notes

Decisions taken while writing, within the approved option:

- **The use case doesn't receive the key.** The interview chose "use case receives the
  key"; writing it showed the use case would have to store the serialized HTTP response
  for replays. Re-asked on 2026-09-16 and approved: a shared application component,
  `IdempotentExecution`, takes the request, the effect (a use case call), and a recorder
  (result → stored response) from the adapter. Same two-transaction shape, use case port
  unchanged, command carries no key. `RestMapper`/`UseCaseTest` exemplars passed a key
  the `Command` exemplar never had — aligned.
- **Rollback releases the claim.** Not in the interview: without it, a business-rule
  rejection left the key `IN_PROGRESS` and every retry waited out the lease. `finally` +
  flag instead of `catch (RuntimeException)`, per `code-quality.md`'s generic-catch ban.
- **Lease reclaim uses `@Version`.** Table gains `claimed_at` and `version`; two
  reclaimers of one stale row are decided by optimistic locking, no native SQL.
- **Transaction guards in the store.** `claim`/`release` throw if a transaction is active;
  `complete` throws if none is. Turns a wrong call site into an immediate failure instead
  of a Postgres "transaction is aborted" far from the cause.
- **`allowEmptyShould(true)`** on both new ArchUnit rules: `no_lob_on_string` is empty in
  the expected state, and a project with only generated ids has nothing to check.
- **The `Tracer` smoke check** is a method added to the existing `*ApplicationTests`
  (reuses its context and Docker guard), plus a `dependency:tree` grep in step 8 that runs
  without Docker. A bare `contextLoads` could not catch lesson 1.
- **Single-module blueprints** declare no `forbidden_imports`; for them the Jackson 3
  guard is the ArchUnit rule plus `architecture-ddd.md`.
- **Not done:** `CustomerEntity`-style fixes in the demo project (its code is not this
  repo's); the stale `handle`/`create` naming mismatch between Controller and UseCasePort
  exemplars, which predates this change.

## Propagation

| File | Change |
|---|---|
| `.claude/rules/persistence.md` | § Mapping: `@JdbcTypeCode` for non-default types, no `@Lob` on Postgres `text` · § Identity and keys: assigned `@Id` ⇒ `Persistable` · § How to verify: `@Lob` grep |
| `.claude/rules/api-rest.md` | § Idempotency: two-transaction shape, release on rollback, lease reclaim, accepted crash window; new table row |
| `.claude/rules/architecture-ddd.md` | § Domain: both Jackson roots |
| `.claude/skills/persistence-architect/templates/JpaEntity.java.example` | `Persistable<UUID>` |
| `.claude/skills/persistence-architect/templates/IdempotencyKeyStore.java.example` | Rewritten: `claim`/`complete`/`release`, `Persistable`, `@JdbcTypeCode`, no `@Lob`, lease + `@Version`, transaction guards |
| `.claude/skills/persistence-architect/templates/IdempotencyKeyTable.sql.example` | `claimed_at`, `version`; column-type note |
| `.claude/skills/persistence-architect/templates/IdempotentExecution.java.example` | New — application component owning the transaction shape |
| `.claude/skills/persistence-architect/SKILL.md` | Step 4 column rows name `@JdbcTypeCode`/`Persistable`; 4a and exemplar table cite `IdempotentExecution` |
| `.claude/skills/rest-api-architect/SKILL.md` | Step 7: shape fixed by the rule, not reopened per use case |
| `.claude/skills/rest-api-architect/templates/Controller.java.example` | Calls `IdempotentExecution`; Jackson 3 import |
| `.claude/skills/rest-api-architect/templates/{IdempotencyKeyInterceptor.java,RestMapper.java,rest-spec.md}.example` | Transactional half re-described; command without key |
| `.claude/skills/test-architect/templates/ArchitectureTest.java.example` | `tools.jackson..` in domain purity; `assigned_ids_implement_persistable`, `no_lob_on_string` |
| `.claude/skills/test-architect/templates/{ControllerTest,UseCaseTest}.java.example` | `IdempotentExecution` pass-through double; command without key |
| `.claude/skills/project-bootstrap/references/dependency-catalog.md` | `observability` row: `spring-boot-starter-opentelemetry`; Boot 4 warnings: tracing, Jackson 3 |
| `.claude/skills/project-bootstrap/templates/features/observability/ApplicationTests-tracer.java.example` | New — `Tracer` bean assertion |
| `.claude/skills/project-bootstrap/SKILL.md` | Preconditions list; 4.7 observability row; package-info Jackson roots; step 8 `dependency:tree` check; 6.7 persistence-architect row |
| `.claude/skills/project-bootstrap/templates/module.CLAUDE.md.example` | `tools.jackson..` |
| `.claude/blueprints/{hexagonal,onion,clean-architecture-multi-module}/*.yaml` | `tools.jackson..` in domain `forbidden_imports` |
| `.claude/agents/java-spring-boot-developer.md` | Block 2 validation: `Persistable`, `@JdbcTypeCode`, `@Lob` · Block 3: idempotency wiring, starter check, Jackson 3 |
| `.claude/skills/new-feature/templates/feature-spec.md.example` | Checklist item 15 names `IdempotentExecution` |

Goes to the generated project: **yes** — rules via step 6.6, the four dev skills' templates
via 6.7 (whole folders), the agent via 6.8; bootstrap templates act at generation. This
record stays here (invariant 9).
