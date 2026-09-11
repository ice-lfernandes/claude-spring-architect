# Onion Architecture Unleashed — A Spring Boot Microservices Odyssey (Medium, @akshay.dipta)

Source: https://medium.com/@akshay.dipta/onion-architecture-unleashed-a-spring-boot-microservices-odyssey-01293b56af67

Used as a reference for `.claude/blueprints/onion/onion.yaml` (this folder).

## Points used in the blueprint

- Domain Layer holding "entities, value objects, and business logic that represent the
  core domain," with a separate "Business/Service Layer... invoking methods on the
  core layer" — confirms the model-plus-services grouping this blueprint puts in one
  `domain` module, even though the article keeps them as two conceptual layers (see
  divergence below).
- `OrderMapper`/`orderMapperDto.mapToDto()` as the DTO↔domain conversion mechanism —
  matches the naming convention's `<Noun>Mapper`, and confirms mapping stays a
  dedicated class, not inline in the controller.
- Constructor-based dependency injection throughout (services receive repositories,
  controllers receive services and mappers) — matches
  `rules/architecture-ddd.md` § Composition, universal across every blueprint here.

## Blueprint's divergence from the article

Two points this blueprint does not follow:

1. **`@Service`-annotated domain services.** The article's Business/Service layer uses
   `@Service` directly on classes that operate on the core domain — this blueprint
   keeps the entire `domain` module (model *and* services) framework-free, per
   `rules/architecture-ddd.md` § Domain, which states "zero framework" without
   exception for services that happen to live beside the model. Wiring a domain
   service as a bean, if needed, is `app`'s job (`@Configuration`), not the service
   class's own annotation — same resolution already used for `hexagonal`'s domain
   services (see `@.claude/blueprints/hexagonal/references/baeldung-ddd-spring.md`).
2. **No `Facade`.** The article's controllers call services and mappers directly; this
   blueprint interposes a `Facade` between `Controller` and `Service` — see
   `jnavez-dev-to.md` for the source that shape actually comes from.
