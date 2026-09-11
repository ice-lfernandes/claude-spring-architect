# Introducing Spring Modulith (spring.io blog, 2022-10-21)

Source: https://spring.io/blog/2022/10/21/introducing-spring-modulith

Used as a reference for `.claude/blueprints/modular-monolith/modular-monolith.yaml`
(this folder).

## Points used in the blueprint

- "An application module model can be tweaked to your liking, but let us stick with
  this default arrangement" — API package (module root) plus nested `internal`
  packages, "inaccessible to other modules." Same default this blueprint keeps rather
  than customizing: no reason to diverge from the framework's own default before a
  real project has ever pushed against it.
- `@ApplicationModule(allowedDependencies = …)` for an explicit, allow-listed exception
  to the isolation rule — relevant precedent for the `shared` module's `OPEN` type
  (see `baeldung-spring-modulith.md`, this folder): both are Spring Modulith's own
  escape hatches for "this dependency is intentional," not a gap in enforcement.
- `PublishedEvents` for testing that an integration test caused a specific event to be
  published, and the "transaction event publication log" for integrating modules
  through events inside a single transaction — the mechanism behind the published-event
  half of this blueprint's naming convention (`OrderPlaced`, module root, not
  `internal`).
- `@ApplicationModuleTest`, which "finds the module the test class is located in" and
  supports "flexible selection of a set of application modules to include" — the
  module-scoped test slice this blueprint expects `test-architect`'s design mode to
  reach for once a real bounded context exists, instead of a full `@SpringBootTest`
  that boots every module for a single feature's test.
- Documentation generation (`Documenter`, PlantUML/C4, Application Module Canvas) —
  the second `@Test` in `ModularityTests.java.example`
  (`templates/features/spring-modulith/`) calls this on every run, non-blocking, so the
  diagrams stay current without being a second gate alongside `verify()`.

## Blueprint's divergence from the article

None structural — this post is the source the `shared`/`internal` shape and the
`ModularityTests.java` exemplar follow most closely. The one addition this blueprint
makes beyond what the post describes is nesting full DDD layering inside `internal`
(see `baeldung-spring-modulith.md`'s divergence note, this folder, and
`@.claude/decisions/0031-modular-monolith-blueprint.md`).
