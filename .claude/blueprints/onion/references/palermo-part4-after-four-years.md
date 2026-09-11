# Onion Architecture, Part 4: After Four Years (Jeffrey Palermo)

Source: https://jeffreypalermo.com/2013/08/onion-architecture-part-4-after-four-years/

Used as a reference for `.claude/blueprints/onion/onion.yaml` (this folder).

## Points used in the blueprint

- "Onion architecture works just fine without the likes of StructureMap or Castle
  Windsor" — no IoC container is required by the pattern itself. Confirms this
  blueprint doesn't need to declare a DI-framework feature beyond what Spring Boot
  already provides; wiring the `Gateway`→`Adapter` binding is ordinary Spring
  `@Configuration`, nothing onion-specific.
- "Works well with and without DDD patterns. It works well with CQRS, forms over data,
  and DDD" — used in `when_to_choose` to frame this blueprint as a module-shape choice,
  not a DDD-or-nothing commitment; teams doing lighter, non-DDD business logic can
  still use it.
- "Merely an architectural pattern where the core object model is represented in a way
  that does not accept dependencies on less stable code" — the single sentence this
  blueprint's `description` is built around: the shape matters, the ceremony around it
  (IoC container, DDD tactical patterns) doesn't.

## Blueprint's divergence from the article

None — this retrospective mostly corrects misconceptions about the *original* two
posts rather than changing the pattern; nothing here contradicts what Part 1–3 already
gave the blueprint.
