# The Onion Architecture, Part 2 (Jeffrey Palermo)

Source: https://jeffreypalermo.com/2008/07/the-onion-architecture-part-2/

Used as a reference for `.claude/blueprints/onion/onion.yaml` (this folder).

## Points used in the blueprint

- "It cannot use `ConferenceRepository` directly. It must rely on something external
  passing in an instance of `IConferenceRepository`" — the exact shape of the `domain →
  gateway → adapter` split: `presentation`/`app` wire the concrete `Adapter` in, the
  `domain` module only ever sees the `Gateway` interface. Same dependency-inversion
  mechanics as `hexagonal`'s port/adapter pair, different names.
- Concrete implementations (`ConferenceRepository`) live outside the application core
  and implement interfaces defined at the center — confirms the `persistence` module
  depends on `domain` (for the `Gateway` interface), never the reverse, matching
  `dependency_rules.forbidden`'s `from: domain` entry.
- Constructor injection of interfaces, enabling test doubles in place of production
  adapters — same rationale `rules/testing.md`'s pyramid already gives for keeping
  domain-service tests at the unit level, doubling only the `Gateway`.

## Blueprint's divergence from the article

The article's example (`SpeakerController` receiving `IConferenceRepository` and
`IUserSession` directly) has the controller itself doing the work a `Facade` does in
this blueprint. Palermo's samples predate the Facade split `jnavez-dev-to.md`
documents; this blueprint follows the newer, more detailed source for that specific
class, not this one — see `jnavez-dev-to.md`'s own notes.
