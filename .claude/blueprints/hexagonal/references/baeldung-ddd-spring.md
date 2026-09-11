# Hexagonal Architecture and DDD with Spring (Baeldung)

Source: https://www.baeldung.com/hexagonal-architecture-ddd-spring

Used as a reference for `.claude/blueprints/hexagonal/hexagonal.yaml` (this folder).

## Points used in the blueprint

- Three-region split — domain at the center, application as the boundary the outside
  world calls through, infrastructure at the edge — matches this blueprint's
  `domain` / `application` / `adapters/*` / `bootstrap` modules and the `inward`
  `dependency_rules.direction`.
- Domain has zero dependency on application or infrastructure; infrastructure
  implements domain-owned interfaces (dependency inversion) — matches
  `modules[domain].forbidden_imports` (`org.springframework..`,
  `jakarta.persistence..`, `jakarta.validation..`, `com.fasterxml.jackson..`) and the
  `forbidden: from: domain, to: [application, adapters/*, bootstrap]` rule.
- Request/response objects kept distinct from the domain entity, converted at the
  boundary (`OrderEntity.toOrder()` in the article) — matches
  `.claude/rules/architecture-ddd.md` § Adapters: "Explicit mapping: no domain entity
  serialized outward, no JPA entity entering the domain."
- Domain service implementations are plain objects, not Spring beans discovered by
  component scan — consistent with this blueprint's `application` module forbidding
  `org.springframework.web..` and never listing `org.springframework..` as an allowed
  import in `domain`. The article's specific technique (wire the domain service by
  hand in a `@Configuration` class, restrict `@ComponentScan`/`@EnableMongoRepositories`
  to the infrastructure package) is a code-generation decision, not blueprint data —
  it belongs to `domain-modeling`/`persistence-architect`'s procedure, not to this
  YAML.

## Blueprint's divergence from the article

The article names the output port bare — `OrderRepository`, interface, placed in the
domain — with `MongoDbOrderRepository` / `CassandraDbOrderRepository` as the
infrastructure-side implementations. This blueprint's convention (see
`hexagonal.yaml`'s naming-convention comment) instead suffixes the port itself:

```
Input port:  <Verb><Noun>UseCase   — e.g. ConfirmOrderUseCase
Output port: <Noun><Action>Port    — e.g. OrderRepositoryPort
```

Kept on purpose, two reasons:

1. **Avoids the Spring Data collision.** The natural name for the generated
   `interface ... extends JpaRepository<...>` adapter is exactly `OrderRepository`. If
   the port used that same bare name, the two are indistinguishable by name alone —
   whoever writes the adapter needs a different name anyway, so the blueprint gives
   the *port* the distinct name instead of leaving the *adapter* to invent one ad hoc.
2. **Same call already made for `clean-architecture-multi-module`.** That blueprint
   resolved an equivalent no-suffix-vs-suffix tension in favor of the explicit
   `Gateway` suffix — see
   `.claude/blueprints/clean-architecture-multi-module/references/porto-seguro-medium.md`
   § divergence. Keeping `hexagonal` consistent with that precedent, not re-deciding
   it per blueprint.
