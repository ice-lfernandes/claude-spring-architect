---
# No `paths`: master content, copied to <project>/.claude/rules/ with `paths`
# derived from the blueprint's `architecture_paths`. Assumes no directory names.
status: active
---

# Architecture — Domain-Driven Design and dependency direction

Dependencies point inward. A module only imports what the active blueprint declares in
`depends_on`. The build is the source of truth, not discipline: if it compiles with the
declared boundaries, the architecture was respected.

The Domain (below) comes from DDD and doesn't change with the blueprint. The other
layers fix only the general principle — the exact name of the abstractions (port, use
case, service) is the active blueprint's decision, see `packages.map`.

## Domain

- Zero framework: no `org.springframework`, `jakarta.*`, `com.fasterxml.jackson`,
  `tools.jackson`. Both Jackson roots: Jackson 3 (Spring Boot 4) moved the core to
  `tools.jackson` and kept the annotations in `com.fasterxml.jackson.annotation`. A guard
  that lists only one root lets the other through
- Zero I/O: no network, files, database
- `Clock` injected, never `LocalDateTime.now()`
- Immutable value objects; validation in the constructor
- Invariants guaranteed by the aggregate, never by the caller
- Past-tense events: `OrderConfirmed`, not `ConfirmOrder`
- Exceptions: `@.claude/rules/error-handling.md`

## Application

- Depends on abstractions (interfaces), never on concrete implementations
- One inbound abstraction per use case; fat interfaces are a smell
- The transaction opens and closes here. Never in the adapter, never in the domain
- No HTTP, JPA, or messaging types in signatures
- Spring Data's `Pageable`, `Page`, and `Sort` are framework types too, same as
  `org.springframework.data.*`: an inbound or outbound port that paginates takes and
  returns a type the application layer owns, never these. Adapters may depend on them
  directly; the conversion happens at each adapter's own boundary, never inside the port

## Adapters

- Translate between the external model and the domain. **No business logic**
- Explicit mapping: no domain entity serialized outward, no JPA entity entering the
  domain
- An adapter only knows the abstractions it needs
- Spring configuration lives here and in bootstrap, not in the domain

## Composition

Constructor injection, always — never `@Autowired` on a field or setter, which hides
the signal of a class with too many responsibilities and prevents instantiating it in a
test without a Spring context. Dependency fields `private final`. The graph is assembled
in bootstrap: no `ApplicationContext.getBean`, no service locator.

## Admitted exception

A pure read query (report, listing with no business rule) may go from the inbound
adapter to the outbound one without crossing the domain, as long as it's recorded in an
ADR.

## How to verify

Depends on whether the layer is a build module or just a package — the active blueprint
decides that, this rule assumes neither.

```bash
# Layer = build module: if the inner module compiles in isolation, it doesn't depend on
# the outer ones. Replace `domain` with the id the blueprint gives the innermost module.
./mvnw -q -pl domain -am test-compile

# Layer = package within a single module: there's no module to isolate and the compiler
# imposes no boundary. What verifies it is the architecture test.
./mvnw -q test
```

In the second case the architecture test is the **only** enforcement outside the editor.
Without it the dependency direction is verified by nothing.
