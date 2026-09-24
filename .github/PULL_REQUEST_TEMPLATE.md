<!--
Contribution guide: CONTRIBUTING.md. Invariants: CLAUDE.md § Invariants.
Delete the sections that don't apply to this PR — an untouched checkbox is read as "not done".
-->

## What changes

<!-- One paragraph. What the reader gets that they didn't have before. -->

## Why

<!-- The problem, not the diff. Link the issue with `Closes #NN` if there is one. -->

## Type of change

- [ ] Blueprint (a new architecture, or a fix to an existing one)
- [ ] Norm under `.claude/rules/`
- [ ] Skill under `.claude/skills/`
- [ ] Agent under `.claude/agents/`
- [ ] Hook (`.claude/hooks/ArchHook.java`) or `settings.json`
- [ ] MCP server (`.mcp.json` and/or `project-bootstrap/templates/mcp.json.example`)
- [ ] Documentation (`README.md`, `docs/`, `claude-help.md`)
- [ ] Decision record under `.claude/decisions/`

## Local checks

Run from the repository root. CI runs the same checks plus the cross-platform and
exemplar-classpath jobs.

- [ ] `java .claude/hooks/ArchHook.java doctor`
- [ ] `java .claude/hooks/ArchHook.java schema`
- [ ] `claude plugin validate .claude/skills` — catches malformed YAML only; `schema` is
      what validates fields
- [ ] I restarted `claude` after editing `settings.json` or `.mcp.json` — they are only
      read at session startup, so an unrestarted session proves nothing

## Invariants

From `CLAUDE.md` § Invariants. Tick what this PR upholds; explain below any that don't
apply.

- [ ] **1 · `rules/` is a leaf** — no norm mentions a skill, an agent or a command
- [ ] **2 · One owning file per norm** — others cite it by path, nothing is copied
- [ ] **3 · No boilerplate in a norm** — code lives in `skills/<name>/templates/*.example`
- [ ] **4 · No new `commands/`** — a slash command is a skill with
      `disable-model-invocation: true`
- [ ] **5 · An agent earns its existence** — it preserves context, restricts tools, or
      changes model; otherwise it is a skill
- [ ] **6 · A rule that must always hold is a hook or `permissions.deny`**, not prose
- [ ] **7 · Architectures are data** — no skill, agent or command changed to add a
      blueprint
- [ ] **8 · No Java or Spring Boot version written from memory** — versions are resolved
      via Spring Initializr at runtime
- [ ] **9 · The generated project is self-contained** — see the next section
- [ ] **10 · Frontmatter fields have a single owner** — `.claude/schemas/extensions.json`,
      and every file that displays the list matches it exactly
- [ ] **11 · No literal secret in a versioned file** — only `${VAR}`, `oauth` or
      `headersHelper`

## Self-containment (invariant 9)

A cloned generated project does not have this repository. If anything here must also
exist inside generated projects, the copy step was updated in the same PR:

- [ ] Not applicable — this only serves before the project exists
      (`project-bootstrap`, `init-project`, `blueprints/`)
- [ ] Norm — step 6.6 of `project-bootstrap/SKILL.md`, including the derived `paths:`
      row when the norm has a package territory
- [ ] Development skill — step 6.7
- [ ] `ArchHook.java` / `schemas/extensions.json` — step 7
- [ ] MCP server — `project-bootstrap/templates/mcp.json.example`, copied in step 7.5

## Dependencies

- [ ] No dependency added beyond JDK, git and curl — no `pip`, no `npm`, no `node`, no
      shell script required to run. `validate.yml` fails otherwise

## Notes for the reviewer

<!--
Anything that doesn't fit above: an invariant you deliberately broke and why, a decision
record this supersedes, a check that can't run on your machine.
-->
