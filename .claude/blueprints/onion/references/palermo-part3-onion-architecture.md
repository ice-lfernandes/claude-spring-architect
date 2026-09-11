# The Onion Architecture, Part 3 (Jeffrey Palermo)

Source: https://jeffreypalermo.com/2008/08/the-onion-architecture-part-3/

Used as a reference for `.claude/blueprints/onion/onion.yaml` (this folder). This is
the post that most directly shaped `dependency_rules.forbidden`.

## Points used in the blueprint

- **The four tenets, verbatim:** "The application is built around an independent
  object model"; "Inner layers define interfaces. Outer layers implement interfaces";
  "Direction of coupling is toward the center"; "All application core code can be
  compiled and run separate from infrastructure." All four map directly onto this
  blueprint: tenet 1 → the merged `domain` module; tenet 2 →
  `domain.gateway`/`persistence` Adapter split; tenet 3 →
  `dependency_rules.direction: inward`; tenet 4 → `domain`'s `forbidden_imports`
  (compiles with zero Spring/JPA on the classpath).
- **"Any outer layer can directly call any inner layer,"** unlike traditional layered
  architecture where each layer only talks to the one directly beneath it. This is
  exactly why `dependency_rules.forbidden` has no `presentation → domain` or
  `persistence → domain` entry — both are legitimate, encouraged edges here, unlike in
  `layered`, whose whole `controller → repository` forbidden entry exists precisely
  because *that* blueprint enforces strict adjacency and this one doesn't.
- **"Infrastructure is code that is a commodity and does not give your application a
  competitive advantage"** — used near-verbatim in this blueprint's framing of why
  `persistence` and `presentation` are peers, not a hierarchy: neither is "more
  central" than the other, so `dependency_rules.forbidden`'s two lateral entries
  (`persistence → presentation` and the reverse) treat them as equally peripheral.

## Blueprint's divergence from the article

None — this post's four tenets are the blueprint's actual specification, not a source
to diverge from.
