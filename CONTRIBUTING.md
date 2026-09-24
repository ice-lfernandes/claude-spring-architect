# Contributing

Thanks for considering it. Blueprints and norms are the most useful contributions.

This repository is **not a Java application**: it has no `pom.xml`, nothing compiles,
there are no Maven tests. What gets edited here are instruction files, data, and one
hook. Read `CLAUDE.md` before the first PR — its § Invariants is what review checks
against, and § Known pitfalls covers the traps that fail silently.

## Setup

```bash
git clone https://github.com/ice-lfernandes/claude-spring-architect.git
cd claude-spring-architect
java .claude/hooks/ArchHook.java doctor
```

Dependencies: `java` (JDK 21+), `git`, `curl`. Nothing else — no Python, no shell
scripts, no `mvn` on `PATH` (the wrapper arrives inside the Initializr's `starter.tgz`).
A PR that adds a dependency outside that list fails `validate.yml`.

`claude` itself is only needed to exercise the skills, not to validate a change.

## Before you open a PR

```bash
java .claude/hooks/ArchHook.java doctor     # diagnoses the setup
java .claude/hooks/ArchHook.java schema     # validates frontmatter + .mcp.json (invariants 10, 11)
claude plugin validate .claude/skills       # malformed YAML only — see below
```

`claude plugin validate` **does not validate fields**. It accepts `metadata:`, accepts
camelCase, accepts an invented field, always printing `✔ Validation passed`. It never
looks at `.claude/agents/`, `.claude/rules/` or `.claude/settings.json`. What validates
fields is `ArchHook.java schema`.

`settings.json` and `.mcp.json` are read **only at session startup**. If your change
touches either, restart `claude` before claiming you tested it.

CI (`validate.yml`) runs the hook on Linux, macOS and Windows, then turns each invariant
into a check, then resolves every `import` in every `.java.example` against JARs from a
real `start.spring.io` request. Full map: [`docs/en/07-ci-validate.md`](docs/en/07-ci-validate.md).

## Deciding what you're writing

Everything here is one of five file types. Picking the wrong one is the most common
review finding, and there is a skill for it:

```
/claude-code-architect-designer
```

| You want to state | It's a |
|---|---|
| A rule about the code that is always true | norm in `.claude/rules/` |
| A procedure with steps | skill in `.claude/skills/` |
| The same procedure, but it must preserve context, restrict tools, or change model | agent in `.claude/agents/` |
| A rule that must hold even when the model forgets it | hook, or `permissions.deny` |
| Routing ("when X, read Y"), a pitfall, an invariant | section of `CLAUDE.md` |
| An architecture | blueprint in `.claude/blueprints/` — data, no prompt changes |

Norms are a leaf of the graph: a norm never mentions a skill, an agent or a command. If
it needs to, it's a procedure and belongs in a skill.

## Per-type rules

### Blueprint (new architecture)

- Own folder: `.claude/blueprints/<id>/<id>.yaml`. Start from
  `custom-template/custom.template.yaml`.
- Fulfills [`.claude/blueprints/_schema.md`](.claude/blueprints/_schema.md) and passes
  its 6-rule checklist. There is no external validator and nothing to install; the final
  arbiter is whether the generated POMs compile.
- Declares honest `trade_offs`. A blueprint with no trade-offs is poorly written.
- `architecture_paths` is required and goes **verbatim** into the generated
  `<project>/.claude/rules/architecture-ddd.md`. A glob copied from another blueprint
  leaves the norm silently unloaded.
- Changes no skill, agent or command. If you find you need to, the design is broken —
  fix the design, not the blueprint.

### Norm

- Own file under `.claude/rules/`, listed in
  [`00-index.md`](.claude/rules/00-index.md), never copied into a skill.
- Declare `paths` whenever the norm has an identifiable file territory — that is how it
  auto-loads without anyone remembering to read it.
- A glob by package must name a package some blueprint's `packages.map` declares, **and**
  the norm must appear in the derivation table of step 6.6 of
  `project-bootstrap/SKILL.md`. Both are CI checks, because the failure mode — a norm
  that never loads — is silent.
- No class, record, interface or enum declaration in the body. Boilerplate lives in
  `skills/<name>/templates/*.example`.
- Every norm carries a § How to verify. Prose nobody checks is decoration.

### Skill

- `.claude/skills/<name>/SKILL.md`. The folder name becomes the slash command, so it
  must not collide with a native one (`/doctor`, `/init`, `/context`, `/memory`, …) —
  shadowing produces no error, it runs the wrong command.
- Human-only invocation: `disable-model-invocation: true`. Never create
  `.claude/commands/`.
- Everything the model must obey goes in the **body**. No `metadata:` — it costs tokens
  on every invocation and enforces nothing. Ownership, `reads` and `handoff` live in the
  body's `## Contrato` section.
- Exemplars go in `templates/` with the `.example` suffix **last**
  (`Foo.java.example`), and every `import` in a `.java.example` must resolve against the
  current Initializr classpath.
- Restricting Bash: `allowed-tools: Bash(cmd:*)` filters each segment of a pipe
  separately. Write injections as a single command.

### Agent

- Exists only to preserve context, restrict tools, or change model. Otherwise it's a
  skill.

### Hook

- One file: `.claude/hooks/ArchHook.java`, single-file source launch, no build. Must run
  identically on Linux, macOS and Windows — CI proves it on all three.
- Exit 0 when a precondition is absent (this repo has no `./mvnw`; that's expected, not
  a failure).

### MCP server

- Declared for this meta-repo (`.mcp.json`), for the generated project
  (`project-bootstrap/templates/mcp.json.example` — written by
  `claude-code-architect-designer`, and absent until some server needs it; step 7.5
  copies it when it exists), or both. A server useful to both is written in both files on
  purpose.
- Secrets only as `${VAR}` / `${VAR:-default}`, `oauth`, or `headersHelper`. Never a
  token spelled out — `ArchHook.java schema` scans `headers` and `env`, because a leaked
  token in git history is irreversible.
- Never work around the one-time human approval of a project-scoped server with
  `enableAllProjectMcpServers`. That prompt is the trust boundary a cloned repo can't
  skip.

### Documentation

- `docs/` is português, `docs/en/` is English, numbered in parallel. Changing a
  behaviour means changing both sides.
- `.claude/decisions/` is not documentation and not a norm: it records what was decided
  on a date, not what holds today. No `paths`, not listed in `00-index.md`, not copied
  into generated projects. Supersede a record with a new one; don't rewrite the old one.

## Self-containment (invariant 9)

Whoever clones a generated project does not have `claude-spring-architect`. Everything
cited from inside a generated project must exist inside it. A new norm or skill here is
only complete once the step that copies it was updated too:

| What you added | Step in `project-bootstrap/SKILL.md` |
|---|---|
| Norm | 6.6 (plus the derived `paths:` row) |
| Development skill | 6.7 |
| `ArchHook.java`, `schemas/extensions.json` | 7 |
| MCP server | 7.5 (`templates/mcp.json.example`) |

Creation skills (`project-bootstrap`, `init-project`) and `blueprints/` are left out on
purpose: they only serve before the project exists.

## Versions

Never write a Java or Spring Boot version from memory, anywhere outside
`.claude/decisions/` and dated "Tested to compile" notes. Versions are resolved at
runtime via Spring Initializr; without network access, ask. CI greps for this.

## Git

- Branch off `main`: `<type>/<kebab-case-description>`, e.g.
  `feat/vertical-slice-blueprint`, `fix/otlp-collector-port`.
- Conventional Commits for the subject: `feat(persistence): …`, `fix(observability): …`,
  `docs: …`. Scope is the area, not the file.
- Commit messages, PR titles and descriptions, and issue text in English. The body of a
  commit explains why, not what the diff already shows.
- One concern per PR. A blueprint plus the norm it needs is one concern; a blueprint plus
  an unrelated docs rewrite is two.
- Fill in `.github/PULL_REQUEST_TEMPLATE.md` and let CI pass before asking for review.

## Reporting instead of contributing

Bugs, feature proposals and blueprint ideas have forms:
[new issue](https://github.com/ice-lfernandes/claude-spring-architect/issues/new/choose).
Questions go to
[Discussions](https://github.com/ice-lfernandes/claude-spring-architect/discussions).
Before proposing a redesign, check
[`.claude/decisions/`](.claude/decisions/README.md) — it may already record why the
current shape was chosen.

## License

By contributing you agree your contribution is licensed under the
[MIT License](LICENSE).
