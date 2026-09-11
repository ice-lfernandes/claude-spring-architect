# Spring Boot Project Layered Architecture (Medium, @armwaseemkss3147)

Source: https://medium.com/@armwaseemkss3147/spring-boot-project-layered-architecture-7ffa78bd9e6a

Used as a reference for `.claude/blueprints/layered/layered.yaml` (this folder).

## Points used in the blueprint

- Three-layer stack — Controller (presentation), Service (business logic), Repository
  (data access) — is the blueprint's `controller` / `service` / `repository` packages
  and its `dependency_rules.forbidden` chain (`controller → service → repository`,
  never the other direction).
- `@ControllerAdvice` for global exception handling, DTOs and validation living at the
  controller boundary — matches `controller.rest` in `packages.map` and the
  `validation: true` feature requirement already shared with every other blueprint.
- Transaction management (`@Transactional`) belongs to the service layer, never the
  repository or the controller — matches `rules/architecture-ddd.md` § Application:
  "The transaction opens and closes here. Never in the adapter, never in the domain."
- Separate `DTO`, `Entity`, `Config` groupings, distinct from the three main layers —
  matches this blueprint's flat `dto`, `entity`, `config` packages, kept as siblings
  rather than nested inside one adapter package (unlike `hexagonal`/
  `clean-architecture-single-module`, which fold them into a shared adapter/
  infrastructure parent).

## Blueprint's divergence from the article

The article's `@Service`-annotated business layer is one class per resource
(`ItemService` with create/read/update/delete methods) — the article never mentions a
separate domain model either; the entity doubles as the object every layer passes
around. This blueprint can't follow either point:

1. **One service class per use case, not per resource.**
   `rules/architecture-ddd.md` § Application states a general principle that holds for
   every blueprint, hexagonal or not: "One inbound abstraction per use case; fat
   interfaces are a smell." A single `ItemService` with four CRUD methods is exactly
   the fat interface that rule rejects. This blueprint's naming convention
   (`<Verb><Noun>Service`, e.g. `CreateItemService`) keeps the article's familiar
   `Service` suffix while splitting by operation, not by resource.
2. **A domain package still exists, framework-free.** `rules/architecture-ddd.md` §
   Domain ("zero framework... doesn't change with the blueprint") applies here exactly
   as it does in `hexagonal`. The article merges the JPA entity and the object passed
   between layers; this blueprint keeps `domain.model`/`domain.event`/`domain.exception`
   separate from `entity`, with `mapper` doing the conversion — the one addition this
   blueprint makes over the raw tutorial, non-negotiable per the rule.
