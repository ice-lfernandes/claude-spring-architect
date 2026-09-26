---
name: rest-api-architect
description: >
  Designs the inbound REST adapter for an already-modeled use case — endpoints, DTOs,
  status codes, error map, pagination, idempotency, and OpenAPI — into the `30-rest.md`
  partial. Use when the request involves exposing a use case via HTTP, designing an
  endpoint or controller, deciding status and error body, paginating a collection,
  applying `Idempotency-Key`, or fixing the OpenAPI contract. Piece of the
  `/new-feature` pipeline: requires `10-dominio.md` in the given folder and stops
  without it.
argument-hint: "[path to the UC-NNN-<slug> folder]"
allowed-tools: Read, Write, Glob, Grep, Bash, AskUserQuestion
---

## Available specs

!`find "${CLAUDE_PROJECT_DIR:-.}/docs/use-cases" -mindepth 1 -maxdepth 1 -type d -name 'UC-*' 2>/dev/null | sort`

Empty above → none yet, run `/use-case-design` first. (`find`, not an `ls` glob: under zsh an unmatched glob
aborts the command before any fallback runs.)

## Target

$ARGUMENTS

---

# REST API Architect

Designs **how the use case is exposed over HTTP**: what endpoints exist, what DTOs go
in and out, what status each result returns, what error body arises from each domain
exception, how the collection is paginated, and what OpenAPI has to document. What
`domain-modeling` left as an inbound port, this skill gives transport shape to.

**Entry rule: without `10-dominio.md`, there's nothing to expose.** This skill reads
`docs/use-cases/UC-NNN-<slug>/00-caso-de-uso.md` and `10-dominio.md` and treats them as
a contract. Without the domain partial, it stops and tells the caller to run
`/domain-modeling` — designing endpoints before the ports and exceptions exist produces
a contract that describes the screen, not the business.

**Exit rule: writes no code.** It emits `30-rest.md`. The controller, the DTOs, the
mapper, and the `ApiExceptionHandler` come from the executor agent, which reads the
partial and the exemplars in `templates/`. Inherits D15 —
`@.claude/decisions/0003-skill-domain-modeling.md` — and the inconsistency P7 flagged is
closed in `@.claude/decisions/0006-rest-api-architect-design.md`.

**Rule rule: rules don't live here.** Verb semantics, status table, `ProblemDetail`,
`traceId`, pagination, idempotency, and what must be annotated in OpenAPI are
`@.claude/rules/api-rest.md`. This skill applies them and cites them; it doesn't
reproduce them.

## How it's invoked

Two ways, and both matter: `/rest-api-architect` by hand, or chained by `/new-feature`
once that orchestrator exists. That's why it does **not** carry
`disable-model-invocation` — that field hides the skill from the model, and a skill the
model can't see is a skill the orchestrator can't call.

The guard against out-of-order firing isn't the frontmatter: it's the **entry rule**
above. Without the prior partial the skill stops and says what needs to run first.
Recorded in `@.claude/decisions/0007-pipeline-skills-invocation.md`.

## Why this isn't a subagent

It's a procedure whose step 3 goes back to asking the user what no prior spec fixes —
what endpoints the client actually needs, what collection grows without bound, what
`POST` touches money or an external system. A subagent doesn't see the conversation.

## Boundary with neighboring skills

The split is by **moment and by artifact**:

| Piece | Acts when | Produces |
|---|---|---|
| `use-case-design` | Before the domain exists | `00-caso-de-uso.md` — boundary and canonical names |
| `domain-modeling` | After the parent spec | `10-dominio.md` — aggregate, invariants, ports |
| **this skill** | After the domain partial | `30-rest.md` — including the schema requirements transport creates |
| `persistence-architect` | After this one | `20-persistencia.md` — schema, reading this partial's schema requirements |
| `test-architect` | After all of them | `40-testes.md` — pyramid, slices, data |

Doesn't collide with `test-architect`: this skill fixes the HTTP contract's **cases**
(status, `errorCode`, body shape); that one owns the **strategy** for testing the whole
use case. Different artifacts, different owners.

Doesn't collide with `java-patterns`, which refactors code that already exists from an
observed symptom. If the controller already exists and the problem is a growing `if`
chain, this isn't the right skill.

## Procedure

1. **Read the specs.** `00-caso-de-uso.md` and `10-dominio.md` from the folder in
   `$ARGUMENTS`. Without the second, stop. Extract: canonical names, inbound ports and
   signatures, the exception table with `errorCode`, and the rows of the component
   table marked `Detailed by: rest-api-architect`.

2. **Survey what already exists.** A resource may already have a controller, and the
   use case may just be a new endpoint. The REUSE state from the parent spec rules over
   intuition.

   ```bash
   grep -rln "@RestController" --include='*.java' src/ 2>/dev/null
   ```

   The inbound adapter's package isn't the same across every blueprint (`adapter/in/rest`
   in one, `infrastructure/rest` in another): discover it via `@RestController` or via
   `packages.map`, never from a path written from memory. An `ls` against a path this
   project doesn't have returns empty and passes for "no controller yet".

3. **Interview — only what the specs don't fix.** `AskUserQuestion`, at most 4
   questions per call. Don't re-ask what `00-caso-de-uso.md` or `10-dominio.md` already
   answered.

   | Axis | Decides |
   |---|---|
   | What operations the client needs beyond the use case's own | Whether there's a read `GET`, and how many endpoints come out of this |
   | Does the collection grow without bound | Whether it's paginated — and it is, unless proven otherwise |
   | Does the `POST` create a resource, move money, or touch an external system | Whether `Idempotency-Key` is required |
   | Is there concurrent writing on the same resource | Whether `ETag` + `If-Match` come in (412 and 428) |
   | Is the resource's existence sensitive information | 404 instead of 403, for the whole resource |
   | Is the client internal or public | Weight of `operationId` stability and the cost of a `/v2` |

4. **Fix the endpoints.** One per line: method, path with explicit `/api/v1`, stable
   `operationId`, the inbound port it serves, success status. Resources and verbs per
   `@.claude/rules/api-rest.md`; class names per `@.claude/rules/naming.md`. An
   endpoint with no inbound port behind it doesn't get written — it's a guessed
   contract. OpenAPI documentation for each operation (`@Operation`, every
   `@ApiResponse`, `@Parameter` with examples, request body example) is extracted into
   its own composed annotation, suffix `OpenApiDocs` — one per operation — shape in
   `templates/OpenApiDocs.java.example`. `@Tag` and the composed annotations go on a
   `<Resource>Api` contract interface (`templates/Api.java.example`) **alone** — no
   Spring mapping or binding annotation there. Those go on the controller that
   implements it (`templates/Controller.java.example`): `@PostMapping`/`@GetMapping`,
   `@Valid`, `@RequestBody`, `@PathVariable`, and any Spring Data Web resolution
   annotation. The split is by kind — documentation on the interface, everything that
   makes the endpoint run on the implementation — not by which file has room.

5. **Fix the DTOs.** Input and output, field by field. DTO validation is **shape**
   only; business rule stays in the aggregate and comes out as 422. No domain type in
   the public signature, no aggregate serialized. Manual, static mapper
   (`templates/RestMapper.java.example`).

   **Apply masking to the fields `10-dominio.md` flagged sensitive.** Any DTO field
   that mirrors a field `domain-modeling` listed as a masking candidate gets
   `@MaskSensitiveData(maskedType = MaskedType.<X>)` (`@.claude/rules/logging.md` §
   Masking mechanism), and the DTO class implements `LogMask`. This isn't optional
   because it "looks fine without it": `GlobalHttpMethodLogAspect` logs every request
   and response DTO by default (opt-out, not opt-in) — a DTO that doesn't implement
   `LogMask` logs the field raw the moment the endpoint runs.

6. **Fix the error map.** Each exception from `10-dominio.md` to its status and its
   `errorCode`, plus the structural errors that don't come from the domain (bean
   validation, unreadable body), and the 500 with `traceId`. This table is the source
   of block 5 — an exception that doesn't appear here won't have a test.

   **Cross-check shape validation against domain invariants before listing both.** For
   each row that comes from a domain invariant (not idempotency, not a structural
   error), check whether step 5 already put a bean-validation annotation on the same
   DTO field. If it did, the bean-validation 400 fires first and the domain's
   `errorCode` is unreachable through this endpoint — mark the row accordingly (e.g.
   "unreachable via this endpoint: intercepted by `@NotBlank` on `<field>`") instead of
   listing it as if it were a scenario a contract test can actually hit. Don't write the
   two as parallel, equally-reachable cases — a test written against the domain row
   alone will assert a 422 that never happens.

7. **Fix pagination and idempotency.** Offset by default; cursor only when volume or
   mutation of the set demands it. If the `POST` requires `Idempotency-Key`, declare
   both halves: the structural one stays in the adapter
   (`templates/IdempotencyKeyInterceptor.java.example`), and the key's table **is
   handed off to `persistence-architect`** — name it in the partial, don't model it
   here. Shape reference for that other half:
   `@.claude/skills/persistence-architect/templates/IdempotencyKeyTable.sql.example`,
   `.../IdempotencyKeyStore.java.example`, and `.../IdempotentExecution.java.example`.
   The transaction shape is fixed by `@.claude/rules/api-rest.md` § Idempotency — don't
   reopen it per use case: the controller calls `IdempotentExecution`, the command
   carries no key, and `10-dominio.md`'s use case signature doesn't change for it.

   **Every `Idempotency-Key` endpoint, including the first one in the project, uses
   `@Idempotent` + `IdempotencyAspect`** (`templates/IdempotencyAspect.java.example`,
   `templates/IdempotencyKeyInterceptor.java.example`'s annotation-based variant,
   `Controller.java.example`): it reuses the same `IdempotentExecution`/`IdempotencyKeyPort`
   — no second port, no second vocabulary — and needs zero code in the controller beyond
   the annotation. When `idempotency_keys` is **NEW** in the project (first time this
   folder decided it's needed), this same pass names `IdempotencyAspect` in the
   dependencies list alongside the table — there is no separate manual path to fall back
   to for a lone first endpoint. (Decision `.claude/decisions/0044-idempotent-first-endpoint.md`:
   the by-hand call this replaced cost ~30 lines of controller-side response
   (de)serialization per endpoint, code the aspect already generalizes, for a cost —
   reflection-based response-type lookup and a body-index argument — that's paid once
   per project regardless of endpoint count.)

   **Write the schema requirements down.** Block 4 closes with a `Schema requirements`
   list: every table or column this transport needs that the domain didn't model — the
   idempotency key table, first of all — or "none". `persistence-architect` runs after
   this skill and reads that list in its first pass; a requirement left in prose here is a
   second persistence pass later.
8. **Fix the dependencies.** springdoc, tracing bridge, and validation. **Read `pom.xml`
   first**: a dependency already declared there keeps its version, and nothing is
   resolved. Never a web search for a version. springdoc's
   version **isn't managed by the Spring Boot BOM**: when it's absent, resolve it at runtime and confirm
   compatibility with the project's Boot major —
   `references/best-practices-links.md` § Resolving the springdoc version. No network,
   ask. Never from memory (`@CLAUDE.md`, invariant 8). You don't edit `pom.xml`: you
   declare, the executor applies.

9. **Write the partial.** `docs/use-cases/UC-NNN-<slug>/30-rest.md`, from
   `templates/rest-spec.md.example`. Five blocks, all mandatory.

10. **Report and stop.** File path, divergences from `10-dominio.md`, what was handed
    off to another owner (idempotency table), and what's left for the folder to be
    complete (`40-testes.md`). Don't invoke anyone.

## What the partial contains

Five blocks. A block with no content is written as "none" — deleting it hides a
question nobody asked.

| Block | Fixes | Shape exemplar |
|---|---|---|
| Endpoints | Method, path, `operationId`, port, success status | `Controller.java.example` implementing `Api.java.example` |
| OpenAPI docs | `@Tag` + `@Operation`, `@ApiResponse` per status, `@Parameter` with examples, request body example — one composed `...OpenApiDocs` annotation per operation, declared on the contract interface alone (no mapping/binding annotation there — those are on the controller) | `Api.java.example` · `OpenApiDocs.java.example` |
| DTOs | Input and output, fields, shape validation, translation, `@MaskSensitiveData` on sensitive fields | `Dtos.java.example` · `RestMapper.java.example` |
| Error map | Exception → status → `errorCode`; `violations` and `traceId` | `ApiExceptionHandler.java.example` · `error-responses.json.example` |
| Pagination, idempotency and dependencies | Mode and limits, both halves of the key, artifacts to add | `PageResponse.java.example` · `page-response.json.example` · `PageCriteria.java.example` (the port's own pagination type — `Pageable` never crosses it) · `IdempotencyKeyInterceptor.java.example` · `IdempotencyAspect.java.example` (every `@Idempotent` endpoint, first one included) |
| Contract test cases | Status, `errorCode`, and body shape per scenario | `@.claude/skills/test-architect/templates/ControllerTest.java.example` |

The exemplars in `templates/` are a **shape reference**, not files to copy. It's the
executor agent that reads them when generating code.

The contract test exemplar does **not** live here: test-code shape has a single owner,
and it's `test-architect`. This skill fixes the cases; the shape of the class that
verifies them is at
`@.claude/skills/test-architect/templates/ControllerTest.java.example`.

## Contract

**Reads** `docs/use-cases/UC-NNN-<slug>/00-caso-de-uso.md` and `10-dominio.md`
(mandatory — stops without the second), `@.claude/rules/api-rest.md`,
`@.claude/rules/architecture-ddd.md` (Adapters and Composition sections),
`@.claude/rules/naming.md`, `@.claude/rules/error-handling.md`,
`@.claude/rules/code-quality.md`, `@.claude/rules/logging.md` (masking mechanism for
DTO fields `10-dominio.md` flagged sensitive), and the active blueprint's
`packages.map`. Also reads
`20-persistencia.md` when it exists (a resumed or hand-run case) — the table's expected
volume decides offset or cursor. In `/new-feature` this skill runs first, so the volume
comes from its own interview ("does the collection grow without bound") and is written in
block 4, where `persistence-architect` reads it instead of asking again.

**Writes** `docs/use-cases/UC-NNN-<slug>/30-rest.md`. Only that file.

**Writes no Java code.** The controller, the DTOs, the mapper, the interceptor, the
`ApiExceptionHandler`, and the contract tests come from the executor agent. It doesn't
edit `pom.xml` nor `.github/workflows/**` — the `openapi.json` diff job belongs to
`project-bootstrap`.

**Doesn't decide** the use case boundary (`00-caso-de-uso.md`), the domain model
(`10-dominio.md`), the schema or the idempotency key table (`20-persistencia.md`), nor
the test strategy (`40-testes.md`). Doesn't touch `.claude/rules/**`.

**Doesn't collide with `domain-modeling`**: that one declares the inbound port, this
one says over which HTTP it's called. The port's signature belongs to the other one; if
it needs to change, report the divergence instead of rewriting it.
