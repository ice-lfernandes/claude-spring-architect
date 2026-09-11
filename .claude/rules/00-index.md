---
# No `paths`: this index is loaded by citation, never auto-loaded.
status: active
---

# Rule index

Each topic has **one** owning file. Whoever needs it cites the path
(`@.claude/rules/<file>.md`) and never reproduces the content. A rule written in two
places diverges — treat that as a bug.

This file contains no rules. It contains the map of who covers what, the loading
mechanism, and the list of what is still missing. Each rule's globs live in that file's
own `paths`; they are not repeated here.

## How a rule enters context

Two paths, and the first is preferable:

1. **Auto-loading via `paths`** — the frontmatter declares globs and the rule enters
   context on its own when matching files are touched. It doesn't depend on anyone
   remembering to read it.
2. **Explicit citation** — for cross-cutting rules that no glob captures
   (`git-workflow.md`, this index). Whoever needs it cites the path.

Declare `paths` whenever the rule has an identifiable file territory.

## Rules written

| File | Covers | Verified by |
|---|---|---|
| `architecture-ddd.md` | Universal DDD + layers, dependency direction, boundaries | `hooks/ArchHook.java check` |
| `naming.md` | Packages, classes, methods, tests, files | Review |
| `code-quality.md` | OCP, LSP, ISP + Clean Code limits (size, complexity, `null`, comments) | Checkstyle (`validate`) + ArchUnit in the generated project |
| `error-handling.md` | Domain exception taxonomy (transport-agnostic) | Contract tests |
| `api-rest.md` | Resources and versioning, verb semantics, success status, 4xx/5xx errors (Problem Details), pagination, idempotency, OpenAPI | OpenAPI + contract tests |
| `lombok.md` | Which Lombok annotations may be generated: `@Data` and `@Setter` forbidden, `@Getter` only with an immutable return, `@FieldDefaults` mandatory | `lombok.config` (`flagUsage = ERROR`, at compile time) + review for the `@Getter` condition |
| `value-objects.md` | When a domain field becomes a value object and when it stays a primitive: criteria, catalog (email, phone, CPF/CNPJ, document, money, percentage, id) and counter-catalog (`name`, `description`, `comments`) | Review |
| `persistence.md` | Adapter boundary, JPA mapping, identity and keys, versioned and immutable migrations, N+1 and pagination, datasource configuration | `grep` from § How to verify + integration tests |
| `testing.md` | Pyramid and levels, slices and context, doubles, names and shape, test data, database engine in integration tests, coverage gate (80% lines / 70% branches), architecture tests | `./mvnw verify` (failsafe + JaCoCo) + ArchUnit |
| `observability.md` | Vendor integration for tracing (correlation identifier origin) and metrics (cardinality, health endpoint, vendor annotations) | Contract tests + context startup |
| `logging.md` | `logback.xml` default pattern, log format and level semantics, sensitive data out of logs, per-class-type log content | Review |
| `messaging.md` | Kafka producer/consumer boundary, delivery semantics (at-least-once, idempotent consumer), topic naming and serialization, retry/DLQ, consumer configuration | `grep` from § How to verify + integration tests |

`architecture-ddd.md` is the only one without its own `paths`, by design: the globs come
from the active blueprint's `architecture_paths` and are written into the file that
bootstrap copies into the generated project. See `@.claude/blueprints/_schema.md`.

The rules whose territory is a package — `api-rest.md`, `persistence.md`,
`value-objects.md`, `observability.md` — have a `paths` here that serves as an example
and is **rewritten at generation time** from the active blueprint's `packages.map`. The
name of the entry-layer package changes from architecture to architecture
(`adapter/in/rest` in one, `infrastructure/rest` in another), and a glob copied verbatim
leaves the rule unloaded, silently. See `@.claude/blueprints/_schema.md`.

## Planned rules (file does not exist yet)

Do not cite these by path — there is nothing to load. If you need one, write it before
using it; don't improvise it inside another file.

| File | Will cover | Expected `paths` |
|---|---|---|
| `security.md` | Authn/authz, secrets, sensitive data, PII | — (no `paths`: always loaded) |
| `git-workflow.md` | Branches, commit messages, PRs | — (cited) |

## States

`status: active` applies now · `status: draft` is a proposal, do not apply ·
`status: deprecated` is kept for reading old code, do not use in new code.
