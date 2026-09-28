# 0027 · Full example `00-caso-de-uso.md` specs, not a one-liner catalog

- **Date:** 2026-09-09
- **Scenario:** clarification of 0026 — wanted complete example use-case specs
  (`use-case-design/templates/use-case-spec.md.example` shape), not a one-line-per-row
  catalog, to test `/new-feature` and `java-spring-boot-developer`.
- **Decision:** `.claude/skills/use-case-design/examples/UC-1NN-<slug>/00-caso-de-uso.md`
  (12 files) + `examples/README.md`
- **State:** approved by Lucas Fernandes, on 2026-09-09

## Supersedes

`@.claude/decisions/0026-example-use-case-catalog.md`. That record's approved artifact
(`java-patterns/references/example-use-cases.md`, one line per scenario) was deleted —
it doesn't survive as a second, weaker index once the full specs exist. 0026 is left
unedited per this directory's own rule ("superseded by a new record; the old one is not
rewritten") — read it only for the rejected-options history, not for what's on disk.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| clarification | The referenced artifact is `use-case-design/templates/use-case-spec.md.example`, not `java-patterns`'s catalog table | Eliminates any option that isn't a full parent-spec document |
| 8 — destination (re-asked) | "meta-repo as examples and documentation" — explicitly not the generated project this time | Eliminates propagation via step 6.7; forces an explicit exclusion, since "entire directory" would otherwise sweep a same-named subfolder in |
| scope | All 12 scenarios from 0026's catalog, each promoted to a full spec | Reuses the symptom design already done; no new scenario invention needed |
| cleanup | Remove the old one-liner file | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `use-case-design/examples/UC-1NN-<slug>/00-caso-de-uso.md` | 8 | **Approved** |
| 2 | `docs/use-cases/UC-1NN-<slug>/00-caso-de-uso.md` at this repo's root | 2 | Rejected — this repo has no `docs/use-cases/` convention; it isn't a Spring project (`@CLAUDE.md`), and the path only means something inside a generated project |

### Option 1 — `use-case-design/examples/` (score 8)

**Motivator:** single owner — `use-case-design` already owns the `00-caso-de-uso.md`
shape (`templates/use-case-spec.md.example`, § Procedure step 7); a fixture of that
same artifact belongs beside it, not inside `java-patterns` (which only owns which
pattern fits which symptom, not the use-case spec format).

**Pros:** mirrors the exact target layout (`UC-NNN-slug/00-caso-de-uso.md`), so a fixture
is a straight `cp -r` into any generated project's `docs/use-cases/`; none of the 12
specs name a pattern, consistent with this skill's own out-of-scope table ("which
pattern" belongs to `java-patterns`); each spec stands alone per the template's own
rule ("while partials don't exist yet, `00-caso-de-uso.md` stands on its own").

**Cons:** step 6.7's "copy the entire directory" wording had to be tightened to an
explicit `SKILL.md` + `templates/` + `references/` list, since a fourth subfolder now
exists that must **not** travel — done in this change, `project-bootstrap/SKILL.md`
§ 6.7.

**Points cut in the rubric:** context cost — twelve files add real weight to this
skill's directory, though none of it loads unless a human opens `examples/` on
purpose; the runtime never reads it during a normal `use-case-design` invocation.

## References

| Claim | Source |
|---|---|
| This repo has no `docs/use-cases/`, isn't a Spring project | `@CLAUDE.md` header |
| `use-case-design` owns the `00-caso-de-uso.md` shape | `.claude/skills/use-case-design/SKILL.md` § Procedure step 7, § Contract |
| "Which design pattern to apply" is `java-patterns`'s decision, not this skill's | `.claude/skills/use-case-design/SKILL.md` § Out of scope |
| Step 6.7 previously copied "the entire directory" | `.claude/skills/project-bootstrap/SKILL.md` § 6.7, pre-edit |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/java-patterns/references/example-use-cases.md` | Deleted — superseded by the full specs |
| `.claude/skills/java-patterns/SKILL.md` | Reverted the `## Contract` § Reads line added by 0026 |
| `.claude/skills/use-case-design/examples/UC-101-*/00-caso-de-uso.md` … `UC-112-*` | Created — 12 full parent specs |
| `.claude/skills/use-case-design/examples/README.md` | Created — index, usage steps, pattern-per-UC annotation |
| `.claude/skills/use-case-design/SKILL.md` | `## Contract`: "Also carries `examples/`" note |
| `.claude/skills/project-bootstrap/SKILL.md` | § 6.7: "entire directory" narrowed to an explicit file list, with `examples/` named as the exclusion |

Goes to the generated project: **no, by design** — this is meta-repo test material for
this repository's own pipeline, not project content; excluded explicitly in step 6.7.
