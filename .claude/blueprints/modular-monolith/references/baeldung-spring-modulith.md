# Spring Modulith (Baeldung)

Source: https://www.baeldung.com/spring-modulith

Used as a reference for `.claude/blueprints/modular-monolith/modular-monolith.yaml`
(this folder).

## Points used in the blueprint

- "An application module is a package located at the same level as the Spring Boot
  main class," organized as direct sub-packages of the application root, with a
  nested `internal` sub-package hiding implementation details — this is the entire
  reason `packages.map`'s roles for `shared` live under `shared.internal.*`, and why
  the naming-convention comment tells `domain-modeling` to nest every future module the
  same way.
- `ApplicationModules.of(Application.class).verify()`, built on ArchUnit, throwing
  `Violations` on an illegal cross-module reference — this is exactly the check
  `ModularityTests.java` (`templates/features/spring-modulith/ModularityTests.java.example`)
  runs, and exactly why `dependency_rules.forbidden` in this blueprint does **not**
  attempt to declare cross-module rules itself: there's no fixed `from`/`to` to write,
  and `ApplicationModules.verify()` already covers it, dynamically, for however many
  modules exist.
- `@ApplicationModuleListener` as the preferred way to consume another module's
  published event, over injecting its beans directly — matches the naming convention's
  "published domain event... other modules react to it" and, more broadly, the
  `dependency_rules.forbidden` entry that keeps adapters from calling each other
  directly, applied here at the module level instead of the layer level.
- `spring-modulith-bom` + `spring-modulith-api` + `spring-modulith-starter-test` as the
  Maven setup — matches the new `spring-modulith` row in
  `.claude/skills/project-bootstrap/references/dependency-catalog.md`. The BOM version
  this article names is already stale by the time anyone reads it; the catalog row
  says to resolve it from the live Initializr response instead, same discipline as
  every other version in this repo.

## Blueprint's divergence from the article

The article shows module-detection and boundary verification but doesn't go into what,
if anything, happens *inside* one module. This blueprint's answer — full
domain/application/adapter layering inside `internal`, using `hexagonal`'s exact
`UseCase`/`Port`/`Service` naming — was a deliberate choice, not something the article
argues for: `rules/architecture-ddd.md`'s Application/Domain sections are stated as
universal in this repo, and the alternative (a flat `internal` with no further
structure, closer to what Spring Modulith's own demos often show) was evaluated and
rejected precisely because it would need a documented exception to that universality —
see `@.claude/decisions/0031-modular-monolith-blueprint.md` for the full comparison.
