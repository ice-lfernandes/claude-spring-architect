# Make Your Microservices Tastier by Cooking Them with a Sweet Onion (dev.to, jnavez)

Source: https://dev.to/jnavez/make-your-microservices-tastier-by-cooking-them-with-a-sweet-onion-34n2

Used as a reference for `.claude/blueprints/onion/onion.yaml` (this folder). The most
detailed, most directly-followed source in this folder — its module names and class
roles map almost one to one onto this blueprint.

## Points used in the blueprint

- **"app, domain, persistence, presentation" as separate Maven modules** — this
  blueprint's `modules[]` uses these exact four ids. Closest match of any source
  fetched for any blueprint in this repo so far.
- **"The module dependencies lead to the domain module"** / **"External layers depend
  on the inner layers"** — `dependency_rules.direction: inward` and the `domain`
  module's empty `depends_on`.
- **Gateways / Adapters / Repositories, three-tier persistence:** "Gateways: Interfaces
  defining domain needs (e.g., `PersistenceGateway`)"; "Adapters: Service classes
  implementing Gateway interfaces (e.g., `PersistenceAdapter`)"; "Repositories:
  Traditional persistence interfaces used by Adapters" — this is the naming
  convention's `<Capability>Gateway` → `<Capability><Technology>Adapter` →
  `<Noun>Repository` chain, directly. One indirection more than `hexagonal`'s
  Port→Adapter pair, called out explicitly in this blueprint's `trade_offs`.
- **Facades:** "Service classes in presentation layer (e.g., `TaskManagerFacade`)...
  Mapping the DTO arguments into domain models (DTO → Model); Calling the domain
  service; Mapping and returning the result into a DTO (Model → DTO)" — the entire
  `Facade` entry in the naming convention, verbatim in spirit: `Controller` stays
  thin, `Facade` does the mapping, validation (`@Validated`/`@Valid`), and security
  (`@PreAuthorize`/`@Secured`).
- **"Application module: Spring Boot main class, integration tests, properties —
  orchestration only... 'No-code module'"** — this blueprint's `app` module, matching
  `hexagonal`'s `bootstrap` module in spirit and, like it, absent from `packages.map`
  (no business-shaped role to name — same precedent `hexagonal.yaml` already sets).

## Blueprint's divergence from the article

Only the same one already recorded in `akshay-dipta-medium.md`: this article doesn't
annotate domain services with `@Service` itself (it correctly keeps the domain module
free of the framework, unlike the Medium article), so there's no real divergence on
that point — this source and this blueprint agree. The one place this blueprint adds
something the article doesn't dwell on is the explicit one-service-per-use-case rule
(`rules/architecture-ddd.md` § Application) — the article's own example doesn't show
enough of `TaskManagerFacade`'s domain-service dependency to tell whether it follows
one-class-per-operation or one fat service per resource; this blueprint requires the
former regardless.
