# Simplifying Development with Vertical Slices (Medium, Symphony Solutions)

Source: https://medium.com/symphonyis/simplifying-development-with-vertical-slices-6af22b21d2d0

Used as a reference for `.claude/blueprints/vertical-slice/vertical-slice.yaml` (this
folder).

## Points used in the blueprint

- **A slice "encompasses all necessary components from the UI to the database"** —
  the reason the blueprint puts a slice-local `Port` + `JpaAdapter` inside the feature
  package instead of a shared persistence module every other blueprint in this repo
  gives its features (`persistence`/`repository`/`adapter.persistence`).
- **Slices communicate through Commands/Queries and a Shared Contract Model, not
  direct calls**: "There is still communication between features through Commands and
  Queries, where shared Contract Models need to be defined and shared between
  features (or copied)." The named "Shared Kernel" is the source of the blueprint's
  own shared `domain` package (aggregate + value objects), the one thing every slice
  is allowed to depend on.
- **Abstractions are not eliminated, just used differently**: "Using vertical slices
  will eliminate abstractions. That's not true; abstractions should still be used, but
  they are not mandatory and are used in a slightly different way." This licenses the
  blueprint's `Port` role staying an interface (an abstraction) even though it is
  slice-local rather than centralized — abstraction kept, its *scope* changed, not its
  existence.
- **"Repeated code across multiple slices may indicate a lack of shared
  components or libraries."** — quoted directly as the blueprint's second trade-off:
  near-duplicate ports/queries across slices touching the same aggregate, with no
  module boundary forcing a fix, only review.
- **Event-driven coupling between slices** ("the Ranking slice listens for events from
  the Game slice") — reinforces the `<Noun><PastTenseEvent>Listener` naming entry
  already drawn from the Baeldung article.

## Blueprint's divergence from the article

The article's `Game` slice uses full Event Sourcing (a stored event stream as the
system of record, not just an integration mechanism) and its `Ranking` slice is
described as fully event-driven end-to-end. This blueprint does not adopt Event
Sourcing as a default — it stays with a conventional aggregate persisted through a
`Port`/`JpaAdapter` pair (`persistence-jpa: true`, `flyway: true` in `features`),
using domain events only for the same narrow purpose the Baeldung article uses them
for: notifying an unrelated sibling slice, not sourcing state. Adding Event Sourcing
to a specific feature later is a `domain-modeling` decision for that use case, not a
default this blueprint's `features` map forces on every slice.
