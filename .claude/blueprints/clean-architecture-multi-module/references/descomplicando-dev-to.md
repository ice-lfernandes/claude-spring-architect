# Descomplicando Clean Architecture com Spring Boot (translated: Demystifying Clean Architecture with Spring Boot)

Source: https://dev.to/gervasio/descomplicando-arquitetura-limpa-clean-architecture-com-spring-boot-3528

Used as a reference for `.claude/blueprints/clean-architecture-multi-module/clean-architecture-multi-module.yaml`
(this folder).

## Points used in the blueprint

- Maven modules, not packages — real isolation between layers via `forbidden_imports`
  per module, not naming convention.
- 4 code layers plus 1 coverage-aggregation layer: `core → usecase → application → infra`.
  `core` depends only on JUnit; `usecase` only on `core`; `application` on `core` + `usecase`;
  `infra` on everything, plus Spring/JPA/Security.
- Use cases are an interface in `usecase` (`CreateUser`, `FindByEmail`) with a `*Impl`
  implementation in `application` (`CreateUserImpl`).
- Output ports called **Gateway**, not **Port**: `CreateUserGateway` (interface in
  `usecase`/`application`), `CreateUserGatewayImpl` (implementation in `infra`).
- Entity validation in the constructor/setters, throwing `DomainException` — not in the DTO.
- `ConflictException` for duplicate data, `DomainException` for business rule violations.
- Use case beans centralized in a `*Config` class in the `infra` module
  (inversion of control without `@Component` scattered across the domain).

## Not used (out of scope for this blueprint)

- A dedicated `coverage` module — this blueprint does not generate a coverage-aggregation
  module; that's left to the generated project's discretion.
