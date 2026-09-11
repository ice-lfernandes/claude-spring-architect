# Blueprint references

Shared folder only for material that is **cross-cutting** across several architectures:
comparisons between blueprints, general selection criteria. Free-form `.md` files.

Material specific to **one** architecture (an article about clean architecture, about
hexagonal, etc.) lives inside the blueprint's own folder:
`.claude/blueprints/<id>/references/`. E.g.:
`.claude/blueprints/clean-architecture-multi-module/references/`.

It is not automatically loaded by any skill or agent — it's for human reference only.
If a blueprint needs to cite a reference, it cites the path in the YAML comment, never
copies the content into the blueprint.

Not to be confused with `.claude/skills/project-bootstrap/references/`, which holds
`blueprint-selection.md` and `dependency-catalog.md` — those are operational and read by
the skill at runtime. This folder here is just supporting archive, with no read contract.
