# Designing Modular Monoliths with Spring Boot (Stackademic)

Source: https://blog.stackademic.com/designing-modular-monoliths-with-spring-boot-the-sweet-spot-between-one-big-blob-and-death-by-5b9c917dd2b2

Used as a reference for `.claude/blueprints/modular-monolith/modular-monolith.yaml`
(this folder).

## Access note

This article is member-gated; only the framing before the paywall was readable.
Recorded here honestly rather than left uncited, since it was one of the four sources
given for this blueprint.

## What the accessible portion contributed

- The framing itself, used near-verbatim in `when_to_choose`/`trade_offs`: a modular
  monolith as the middle ground between "one big blob" (no boundaries at all) and
  "death by [microservices]" (boundaries bought at the cost of 9 Docker containers for
  one feature) — this blueprint's own `description` and `when_to_choose` entries
  ("don't yet justify separate deployables, but do need a boundary stronger than
  'please don't import that package'") are this same framing, not the deep technical
  content, since the article cuts off before covering package structure or
  communication patterns.
- Confirms Spring Modulith by name as the tool of choice for building one — consistent
  with, but adding nothing beyond, the three other references in this folder, which
  carry the actual technical content this blueprint is built from.

## What it did not contribute

Package/module structure, boundary enforcement mechanics, inter-module communication,
and naming conventions all come from `baeldung-spring-modulith.md`,
`spring-io-introducing-modulith.md`, and `spring-io-modulith-project.md` (this folder)
— this source's technical claims couldn't be verified against the paywalled content
and aren't relied on here.
