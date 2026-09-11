# Implementando Clean Architecture com Spring (translated: Implementing Clean Architecture with Spring) (Porto Seguro)

Source: https://medium.com/porto-seguro/implementando-clean-architecture-com-spring-4a8da4110bd5

Used as a reference for `.claude/blueprints/clean-architecture-multi-module/clean-architecture-multi-module.yaml`
(this folder).

## Points used in the blueprint

- Explicit dependency rule: the outermost layer depends on the innermost, never the
  other way around — same as the `inward` direction already used in the `hexagonal`
  blueprint.
- Three layer groupings: `entrypoint` (controller/request/response/mapper),
  `dataprovider` (technical implementations: repository, impl), `core`
  (domain + usecase + dataprovider interfaces).
- Suffix convention adopted in the blueprint:
  `UseCase` (interface) / `UseCaseImpl` (implementation),
  `Controller`, `Entity` (persistence), `Request`/`Response` (DTO), `Mapper`.
- `core` is forbidden from using any framework — not even to generate getters/setters.
- API DTOs always distinct from the domain entity — never serialize the entity
  directly.
- "Accepted reorganization": grouping `domain` + `usecase` into a single module is valid
  as long as the dependency rule (never inside-out) holds — that's what this blueprint
  does by folding `usecase` in as a sub-layer of `application` instead of a separate
  Maven module, unlike the dev.to article, which uses 4 modules.

## Blueprint's divergence from the article

This article uses `UseCase`/`UseCaseImpl` but a gateway/repository with **no** dedicated
suffix (`InsertCustomer`, not `InsertCustomerGateway`). The blueprint follows the other
article (`descomplicando-dev-to.md`, in this same folder) and keeps the `Gateway` suffix —
more explicit in distinguishing an output port from an input port.
