# Lessons learned — UC-101-apply-order-discount

Generated project: `demo-application` (clean-architecture-single-module).
Meta-repo maintainers: gaps found while running `/new-feature` end-to-end through
`java-spring-boot-developer` for the first business feature in this project. This
project doesn't edit skills/rules directly (`.claude/skills/**`, `.claude/rules/**`
here are the generated copies) — fixes belong at the meta-repo source. Paths below are
meta-repo-relative (`.claude/skills/...`), same as this project's own copies.

Full context: `docs/use-cases/UC-101-apply-order-discount/` (all 5 partials +
consolidated `UC-101-spec.md`, with a "Resolved divergences" section for design-time
findings and an "Implementation divergence" note for the code-time one below).

---

## Gap 1 — `persistence-architect`'s `RepositoryAdapter.java.example` has no
UPDATE-with-no-domain-version variant

**Where:** `.claude/skills/persistence-architect/templates/RepositoryAdapter.java.example`

**What happened:** `10-dominio.md` for UC-101 deliberately excludes `version` from the
`Order` aggregate — `@.claude/rules/value-objects.md`'s counter-catalog lists `version`
as "no unit, no business operation," and optimistic locking is framed as a pure
persistence-layer concern. `20-persistencia.md` (written *before* any code existed)
confirmed this and specified `@Version` as entity-only. Neither partial caught the
consequence: `OrderRepositoryAdapter.save(Order order)` cannot build a fresh
`OrderEntity` from the domain object via `mapper.toEntity(order)` and `saveAndFlush`
it — a brand-new detached entity always carries `version = 0` (Java primitive
default), so on the *second* update the real row is at `version = 1`, the UPDATE's
`WHERE version = 0` matches nothing, and every update after the first false-conflicts
as `ObjectOptimisticLockingFailureException` even with zero real concurrency.

This was only discovered while writing `OrderRepositoryAdapterIT`, well into
implementation — not by `persistence-architect`'s own design pass, and not by
`domain-modeling`'s either.

**Why the exemplar didn't catch it:** `RepositoryAdapter.java.example`'s only
`save` example is `UserRepositoryAdapter.save`, and the mother-spec use case it
illustrates (`UC-001-create-user`) is a pure `INSERT` — the entity really is fresh
there, `version = 0` is correct on a first insert. The exemplar has never shown the
UPDATE case for an aggregate whose domain type doesn't carry `version`.

**Fix actually applied here** (`OrderRepositoryAdapter.java`, this project): `save()`
loads the managed entity first (`repository.findById(...)`, same first-level cache the
service's own `findById` already populated in the transaction), mutates only the
changed field on it via a package-private entity method (`OrderEntity.applyTotal(...)`
here), then `saveAndFlush`s the *managed* instance — Hibernate's dirty-checking then
carries the row's real current `version` into the `UPDATE`, and a genuine concurrent
write is still caught correctly (flush-time mismatch → `OptimisticLockingFailureException`
→ translated).

**Suggested fix at the source:**
1. `.claude/rules/persistence.md` § Boundary — add an explicit line: "An aggregate
   that doesn't carry `version` (`@.claude/rules/value-objects.md`'s counter-catalog)
   still needs the *row's* real version at update time. `save()` for an update must
   load-and-mutate the managed entity, never construct a fresh detached one from the
   domain object — the primitive `long` default (`0`) will false-conflict on every
   update after the first."
2. `RepositoryAdapter.java.example` — add a second `save` variant next to
   `UserRepositoryAdapter.save` (which stays as the pure-INSERT reference) showing the
   load-and-mutate pattern for an UPDATE-only port, with the same comment explaining
   why.
3. Optionally, `persistence-architect`'s own procedure (step 6, "Fix the queries and
   access plan") could flag this explicitly whenever step 1 finds `10-dominio.md`
   excluding `version` from the aggregate *and* the port has a `save` used for updates
   (not just creates) — i.e., catch it at design time, in `20-persistencia.md`, instead
   of leaving it for the executor to discover mid-implementation.

---

## Gap 2 — `test-architect`'s `ArchitectureTest.java.example` doesn't exclude
`package-info.java` from `classes()`-based naming rules

**Where:** `.claude/skills/test-architect/templates/ArchitectureTest.java.example`

**What happened:** This project's `project-bootstrap` generates a `package-info.java`
in every package (confirmed: `application/port`, `application/usecase`,
`infrastructure/persistence`, `domain/exception`, etc. all have one from the start).
`test-architect` setup mode's `use_case_implementations` rule —
`classes().that().resideInAPackage("..application.usecase..").should().haveSimpleNameEndingWith("Service")`
— matched `package-info` too (ArchUnit imports it as a class) and failed on the very
first run, purely because a package-documentation file isn't named `*Service`.

**Fix actually applied here** (`ArchitectureTest.java`, this project): added
`.and().haveSimpleNameNotEndingWith("package-info")` to the rule's `that()` clause.

**Suggested fix at the source:** since every generated project gets a
`package-info.java` per package by convention (`project-bootstrap`), any
`classes()`-based naming rule in the exemplar will hit this the same way, every time.
Bake `.and().haveSimpleNameNotEndingWith("package-info")` into every naming-style
`classes()` rule in `ArchitectureTest.java.example` (`use_case_implementations`,
`controllers_in_rest_adapter`, `jpa_entities_have_entity_suffix`, and any future one of
the same shape), with a one-line comment explaining why — so it's not rediscovered by
trial and error on every project's first `test-architect` setup run.

---

## Observation (not a code gap) — a hand-copied `00-caso-de-uso.md` example fixture
drifted from the real project

**Where:** `docs/use-cases/UC-101-apply-order-discount/00-caso-de-uso.md` (as found,
before correction), header said: *"Example spec — see
`.claude/skills/use-case-design/examples/README.md`. Written to exercise
`/new-feature` and `java-spring-boot-developer`."*

**What happened:** The file existed on disk before this session started (as
`use-case-spec.md`, wrong filename for the pipeline besides), using hexagonal-style
paths (`adapter/in/rest`, `application/port/in|out`, `application/service`) that don't
match this project's actual blueprint (`clean-architecture-single-module`:
`infrastructure.rest`, `infrastructure.persistence`, `application.usecase`,
`application.port`, confirmed against `CLAUDE.md` § Structure and
`.claude/forbidden-imports.txt`). It also marked `Order`/`OrderRepository`/
`OrderRepositoryAdapter` as `CHANGE`/`REUSE`, assuming an earlier order-creation use
case's code already existed — the project was in fact empty (`find src/main/java`
showed only `package-info.java` files).

`.claude/skills/use-case-design/examples/` doesn't exist in this generated project —
per that skill's own contract, `project-bootstrap` step 6.7 deliberately doesn't copy
it (it's meta-repo-only tooling for testing the pipeline itself). So this file was
manually placed into a real generated project from what looks like one of those
meta-repo example fixtures, without going through `use-case-design`'s own step 5
("Derive the real paths from this project's packages... never write a generic path
when the real one is knowable") or step 4 ("Read the code before proposing... every
component carries NEW/CHANGE/REUSE").

**This isn't a skill bug** — `domain-modeling`'s own step 2 ("Survey what already
exists... confirm or correct the mother spec's state") caught both divergences
correctly when invoked properly, exactly as designed. The bug is upstream of any
skill: a fixture meant for exercising the pipeline ended up seeded directly as if it
were a real spec, skipping the steps that would have caught the drift immediately.

**Suggested action at the source (optional, low priority):** if
`examples/README.md`'s fixtures are ever meant to double as "drop this into a fresh
project and run `/new-feature`" demos, they'd need a louder marker than a one-line
mention in the header — e.g., a fixture-only front-matter field
(`fixture: true, requires_reconciliation: true`) that `use-case-design`'s re-invoke
step could detect and react to differently (force step 4/5 instead of offering
"accept as-is"). Otherwise, no change needed: the pipeline already self-corrected.

---

## Suggestion — drop `final` from the 4 typed exceptions, keep `DomainException` abstract

**Where:** `.claude/rules/error-handling.md` § Shape of the base classes; exemplars
`.claude/skills/domain-modeling/templates/NotFoundException.java.example`,
`ValidationException.java.example`, `BusinessRuleViolationException.java.example`,
`ConflictException.java.example`.

**Current rule:** `DomainException` is abstract; the four typed ones
(`NotFoundException`, `ValidationException`, `BusinessRuleViolationException`,
`ConflictException`) are `final`, add no state, and are the only vocabulary — a new
scenario is a new `errorCode` string on one of the four, never a fifth class.

**What happened here:** UC-101's mother spec proposed
`domain/exception/OrderAlreadyPaidException.java` as its own class. The rule as
written forced collapsing it into `ConflictException("ORDER_ALREADY_PAID", ...)` — a
plain string, checked only by string equality in tests
(`.extracting("errorCode").isEqualTo("ORDER_ALREADY_PAID")`) and never by the type
system. Nothing stops a typo in the `errorCode` literal from compiling; nothing lets a
`catch (OrderAlreadyPaidException e)` exist even where the caller genuinely wants to
react to that one condition specifically instead of "some conflict, check the string."

**Suggested change:** remove `final` from the four typed classes — `DomainException`
stays `abstract`, the four typed ones stay the mandatory *family* vocabulary (still
exactly four families, still no fifth family invented ad hoc), but each family becomes
extensible: `OrderAlreadyPaidException extends ConflictException` is legal, carries no
new state beyond what `ConflictException` already has, and exists purely to give a
recurring, business-meaningful `errorCode` a real type instead of a string every
caller has to spell correctly. This mirrors the symptom-driven extension philosophy
`java-patterns` already applies elsewhere in this project (a pattern earns its class
once a symptom repeats) — a first occurrence stays the plain typed exception with a
string `errorCode`; a *repeated, named* business condition (like "order already paid",
likely to be checked explicitly by more than one caller) earns its own subclass.

**Governance this needs, if adopted** (so it doesn't quietly turn into "any exception
name goes"): the entry rule should stay narrow — a named subclass only for an
`errorCode` that (a) already repeats across ≥ 2 call sites, or (b) a caller needs to
`catch` specifically rather than branch on `errorCode()`. `domain-modeling`'s step 5
("Map each invariant to its exception") would need a line added: default to the plain
typed family + string `errorCode`; promote to a named subclass only under that
entry rule, exactly the same discipline `java-patterns`' own catalog already uses for
when a symptom earns a design pattern.

---

## Not a gap — Checkstyle magic-number catch in `Money.java`

Flagged during a routine `./mvnw -q test-compile`: a literal `10` (BigDecimal `divide`
scale) needed extracting into a named constant. Caught immediately, unambiguous
message, one-line fix. No exemplar or rule change indicated — this is Checkstyle doing
exactly what `@.claude/rules/code-quality.md` § Hard limits ("no magic numbers... named
constant") describes, working as intended.
