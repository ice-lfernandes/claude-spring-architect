# Vertical Slice Architecture (Baeldung)

Source: https://www.baeldung.com/java-vertical-slice-architecture

Used as a reference for `.claude/blueprints/vertical-slice/vertical-slice.yaml` (this
folder).

## Points used in the blueprint

- **"Components are arranged into domain-specific packages that span multiple
  layers"** instead of one package per technical layer — the blueprint's
  `features.<feature>.<usecase>` package, replacing `layered`'s flat
  `controller`/`service`/`repository` siblings.
- **"Our package names reflect technical layers without conveying the true purpose of
  the project"** — cited almost verbatim in the blueprint's `description` as the
  contrast with `layered`.
- **Package-private visibility to keep the slice boundary real**: "Dividing the
  project into vertical slices allows us to use the default, package-private access
  modifiers for most of the classes. This ensures that unexpected dependencies don't
  cross the domain boundary." The blueprint doesn't mandate package-private (Java
  visibility isn't part of any blueprint field here), but this is the underlying reason
  `dependency_rules` treats sibling-slice isolation as a boundary worth stating even
  though it can't be a static ArchUnit `forbidden` entry yet (see the comment above
  `dependency_rules` in the yaml).
- **Cross-slice communication via application events, not direct service calls**:
  "When an article is created, `CreateArticleUseCase` publishes an
  `ArticleCreatedEvent`, allowing unrelated slices like recommendations to react via
  `@EventListener`" — this is the blueprint's `<Noun><PastTenseEvent>Listener` naming
  entry and the "ONLY sanctioned way one slice reacts to another" line in the
  dependency comment.
- **Minimal shared abstractions, each slice with its own representation**: "a 'user'
  becomes a 'reader,' 'author,' or 'topic follower' depending on context" — the basis
  for the blueprint's trade-off that a slice's `Response` is never reused by another
  slice even when fields overlap.

## Blueprint's divergence from the article

The article's slices are named after DDD bounded contexts (`author`, `reader`,
`recommendation`) — one package per *domain*, containing several use cases each. This
blueprint's `features.<feature>.<usecase>` nests one level deeper: one package per
*use case*, grouped under a feature. Reason: `rules/architecture-ddd.md` § Application
already states "one inbound abstraction per use case; fat interfaces are a smell" for
every blueprint in this repo (see `layered.yaml`'s `<Verb><Noun>Service` for the same
reasoning) — a package shared by every use case in a domain would let two unrelated
handlers hide behind one class again, the exact smell that rule forbids.
