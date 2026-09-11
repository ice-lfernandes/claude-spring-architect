# The Onion Architecture, Part 1 (Jeffrey Palermo)

Source: https://jeffreypalermo.com/2008/07/the-onion-architecture-part-1/

Used as a reference for `.claude/blueprints/onion/onion.yaml` (this folder). The
original source of the pattern's name and core shape — every other file in this
folder is read against this one.

## Points used in the blueprint

- "All code can depend on layers more central, but code cannot depend on layers
  further out from the core" — `dependency_rules.direction: inward` and the
  `forbidden` entry keeping `domain` from reaching `persistence`/`presentation`/`app`.
- "The database is not the center. It is external." — the reason `persistence` is its
  own outer module here, not folded into `domain`, even though `domain.service` (the
  use cases) IS folded into `domain` — the model and its business logic are the
  center; the database is infrastructure, full stop.
- Domain Model at the center, "Application Core" as the ring holding the model plus
  repository/gateway interfaces — this is exactly why this blueprint has no separate
  `application` module: Palermo's own drawing shows one inner ring, not two.

## Blueprint's divergence from the article

None structural at this stage — Part 1 sets up the shape this blueprint follows
directly. The naming choice (`Gateway` over Palermo's `IConferenceRepository`-style
interface names) comes from the newer sources in this folder, not this one; see
`jnavez-dev-to.md`.
