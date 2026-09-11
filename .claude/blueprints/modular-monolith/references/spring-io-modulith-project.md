# Spring Modulith (project page)

Source: https://spring.io/projects/spring-modulith

Used as a reference for `.claude/blueprints/modular-monolith/modular-monolith.yaml`
(this folder).

## Points used in the blueprint

- "Business modules as direct sub-packages of the application's main package" — the
  same convention `baeldung-spring-modulith.md` and
  `spring-io-introducing-modulith.md` (this folder) cite; three independent sources
  agreeing on the same package shape is why this blueprint treats it as non-negotiable
  rather than one option among several.
- "Guides developers toward organizing applications around domain-driven module
  structures" — the project page's own framing lines up with this blueprint's choice
  to nest full DDD layering inside each module's `internal` package (§ divergence in
  `baeldung-spring-modulith.md`), rather than treating Modulith's module boundary and
  this repo's domain purity as competing concerns.
- Runtime observability "on the application module level" and integration testing via
  `@ApplicationModuleTests` — confirms this is a framework meant to stay useful past
  the structural-verification stage this blueprint wires up at bootstrap
  (`ModularityTests.java`); the observability half is a later, per-feature concern, not
  something `project-bootstrap` needs to configure.

## Blueprint's divergence from the article

None — this is the project's own summary page, cited for the same core convention the
other two Spring Modulith sources confirm independently.
