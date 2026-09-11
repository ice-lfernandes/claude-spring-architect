# Vertical Slice Architecture (milanjovanovic.tech)

Source: https://milanjovanovic.tech/blog/vertical-slice-architecture

Used as a reference for `.claude/blueprints/vertical-slice/vertical-slice.yaml` (this
folder). Written for .NET/MediatR; the blueprint keeps the folder shape and the REPR
naming, not the mediator framework — see divergence below.

## Points used in the blueprint

- **"All the files for a single use case live in one folder. This minimizes coupling
  between unrelated features and maximizes coupling inside a single feature."** — the
  blueprint's core organizing principle, quoted in spirit in `description` and
  `when_to_choose`.
- **Folder layout, adapted from the article's `Features/Activities/GetActivity/`
  example**: `GetActivityQuery.cs`, `GetActivityQueryHandler.cs`,
  `GetActivityEndpoint.cs`, `ActivityResponse.cs` all in one folder — this is exactly
  the blueprint's `features.<feature>.<usecase>` package containing
  `<Verb><Noun>Command`/`Query`, `<Verb><Noun>Handler`, `<Verb><Noun>Endpoint`,
  `<Noun>Response`. The blueprint's naming convention comment names each role after
  this exact set.
- **REPR pattern (Request-Endpoint-Response) naming** — directly the source of the
  blueprint's `Endpoint`/`Response` suffixes.
- **"New features only add code, you're not changing shared code and worrying about
  side effects."** — quoted near-verbatim in `when_to_choose`, the blueprint's second
  bullet.
- **A slice can grow too large**: "Because much of the business logic sits inside a
  single use case, a slice can grow until it does too much. You need to spot code
  smells and refactor by pushing logic down into the domain model." — this is exactly
  why the blueprint keeps a shared `domain` kernel with a real aggregate
  (`rules/architecture-ddd.md` § Domain, "invariants guaranteed by the aggregate,
  never by the caller") instead of letting the Handler hold all the logic itself, the
  temptation the article warns about.

## Blueprint's divergence from the article

The article's `Handler` classes are dispatched through **MediatR**, a mediator
library that gives every handler a pipeline for free (validation, logging,
transactions wrapped around the call automatically). This repo has no Java equivalent
in its dependency list (`ai-spring-setup` has zero framework opinions beyond Spring
Boot itself — see root `CLAUDE.md` § Dependencies), and `rules/architecture-ddd.md` §
Composition already requires constructor injection with no service locator for every
blueprint. So this blueprint's `Endpoint` calls its `Handler` directly, a plain
Spring-managed bean — no mediator, no pipeline behaviors. This is called out
explicitly as the first trade-off in `vertical-slice.yaml`: "None of the free
cross-cutting behavior a mediator pipeline gives... each slice wires its own."
