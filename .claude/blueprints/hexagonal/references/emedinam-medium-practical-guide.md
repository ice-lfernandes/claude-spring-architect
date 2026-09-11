# Implementing Hexagonal Architecture in Java — A Practical Guide (Medium, @emedinam)

Source: https://medium.com/@emedinam/implementing-hexagonal-architecture-in-java-a-practical-guide-for-clean-domain-centric-design-37c7f8ca3e80

Used as a reference for `.claude/blueprints/hexagonal/hexagonal.yaml` (this folder).

## Points used in the blueprint

- Primary/driving vs secondary/driven split for both ports and adapters — same
  distinction this blueprint already encodes as `adapter-in-*` (driving: REST,
  messaging inbound) vs `adapter-out-*` (driven: persistence), each with its own
  `feature` flag and its own module.
- "Ports should not depend on adapters; adapters depend on ports" — same shape as
  `dependency_rules.forbidden`, where `adapters/*` may never appear as a `from` with
  another adapter as `to` ("Adapters do not talk to each other — they communicate
  through the application").
- Ports designed in domain language, not technical/implementation terms — matches
  `<Verb><Noun>UseCase` / `<Noun><Action>Port` in the naming convention: both name the
  business operation or the resource, never the technology behind it.
- Domain objects annotation-free; DTOs isolate the domain from API concerns; mapper
  libraries (MapStruct) considered for complex conversions — consistent with the
  `application`/`domain` `forbidden_imports` and with the manual-mapping rule already
  cited from `baeldung-ddd-spring.md` (this folder).
- Dependency injection used specifically to swap real adapters for test doubles in
  unit tests — the reason `.claude/rules/testing.md`'s pyramid keeps domain/use-case
  tests at the unit level, doubling only the ports.

## Blueprint's divergence from the article

Same naming divergence as `baeldung-ddd-spring.md` (this folder): the article names
primary ports `ProductService`/`OrderService` and secondary adapters
`JpaProductRepository`/`JpaProductRepositoryAdapter`, with no dedicated port suffix.
This blueprint keeps `UseCase` on the input port and `Port` on the output port for the
same reason recorded there — the generated Spring Data interface would otherwise
naturally claim the exact same bare name as the port.
