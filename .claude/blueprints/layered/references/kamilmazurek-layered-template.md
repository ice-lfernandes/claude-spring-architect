# Layered Architecture Template (kamilmazurek.pl)

Source: https://kamilmazurek.pl/layered-architecture-template

Used as a reference for `.claude/blueprints/layered/layered.yaml` (this folder).

## Points used in the blueprint

- **"Each layer communicates only with the one directly below it."** This is the
  defining rule of this blueprint's dependency set — the
  `from: controller, to: [repository]` forbidden entry exists specifically to catch a
  controller skipping the service and calling the repository directly, which neither
  `hexagonal` nor `clean-architecture-single-module` needs to state this way (their
  boundary is port-vs-adapter, not adjacency).
- API / Service / Repository / Database, four regions with `api`, `service`,
  `persistence` directories — matches this blueprint's `controller`, `service`,
  `repository` packages (renamed `persistence` → `repository` to name the artifact,
  not the concern, consistent with `<Noun>Repository` already being the class suffix).
- Repository layer combines a `JpaRepository` interface directly — no separate
  interface-plus-implementation pair — matches this blueprint's one-file
  `ItemRepository extends JpaRepository<ItemEntity, UUID>`, the concrete difference
  from `hexagonal`'s `OrderRepositoryPort` + `OrderRepositoryAdapter` pair called out
  in `layered.yaml`'s description.
- Mapping between layers via a dedicated mapper (`ModelMapper` in the article; this
  blueprint's generated code uses an explicit mapper class instead — see
  `rules/architecture-ddd.md` § Adapters, "explicit mapping" over a reflection-based
  library) — same principle, different mechanism.
- Single-module Maven project, `mvnw`, Testcontainers-adjacent tooling (H2 for
  dev/test in the article) — matches `build.layout: single-module` and this
  blueprint's `testcontainers: true` (this repo's own convention, real containers over
  H2 — see `rules/testing.md` § database engine).

## Blueprint's divergence from the article

Same divergence recorded in `armwaseem-medium.md` (this folder): the article's
`ItemService` is one class per resource, and there's no domain layer distinct from
`ItemEntity`. This blueprint splits by use case (`<Verb><Noun>Service`) and keeps a
framework-free `domain` package, per `rules/architecture-ddd.md` § Application and §
Domain — see the fuller rationale there.

One narrower divergence specific to this article: it uses `ModelMapper` for
entity/domain/DTO conversion ("maps data between layers smoothly"). This blueprint's
generated mappers are explicit, hand-written classes, not a reflection-based library —
`rules/architecture-ddd.md` § Adapters requires "explicit mapping," and a
reflection-based mapper hides exactly the field-by-field correctness a domain
boundary depends on.
