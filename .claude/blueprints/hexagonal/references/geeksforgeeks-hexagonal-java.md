# Hexagonal Architecture in Java (GeeksforGeeks)

Source: https://www.geeksforgeeks.org/java/hexagonal-architecture-in-java/

Used as a reference for `.claude/blueprints/hexagonal/hexagonal.yaml` (this folder).

## Points used in the blueprint

- Inbound port exposes core logic to the outside world; outbound port lets the core
  talk to persistence — same in/out split this blueprint's `application.port.in` /
  `application.port.out` packages already encode.
- Driving adapters (REST controllers) vs driven adapters (repository
  implementations) — matches `adapter-in-rest`/`adapter-in-messaging` vs
  `adapter-out-persistence` modules, each depending on `[application, domain]` and
  never on each other.
- Core logic fully isolated, communication only through ports and adapters, layers
  independently replaceable — matches `dependency_rules.direction: inward` and the
  `domain`/`application` `forbidden_imports`.

## Blueprint's divergence from the article

Same naming divergence already recorded in `baeldung-ddd-spring.md` (this folder): the
article's inbound port (`CakeService`) and outbound port (`CakeRepository`) carry no
suffix distinguishing them from a Spring-managed bean or a Spring Data interface named
the same way. This blueprint keeps `<Verb><Noun>UseCase` and `<Noun><Action>Port`
specifically to avoid that collision — see the fuller rationale there.
