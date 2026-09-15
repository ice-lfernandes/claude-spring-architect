# claude-spring-architect

Meta-repository: generates Spring Boot projects already prepared for AI-assisted
development. **It is not a Java application** — it has no `pom.xml`, does not compile,
has no Maven tests. What gets edited here are instruction files, data, and a hook.

Purpose, features and status: `@README.md`.

## Dependencies

`java` (JDK 21+) · `git` · `curl`. Nothing else. Zero Python, zero shell, zero `mvn` on
PATH — the wrapper comes in the Initializr's `starter.tgz`.

## Commands

| Action | Command |
|---|---|
| Diagnose the setup on this machine | `/arch-doctor` |
| Create a project from a blueprint | `/init-project` |
| Design a new extension of this `.claude/` | `/claude-code-architect-designer` |
| Validate frontmatter of skills and agents | `claude plugin validate .claude/skills` |
| Run the hook by hand | `java .claude/hooks/ArchHook.java doctor` |
| Render the execution trail of a run by hand | `java .claude/hooks/ArchHook.java audit flush` |
| Validate frontmatter of all extension files, and `.mcp.json` | `java .claude/hooks/ArchHook.java schema` |
| List and inspect this repo's MCP servers | `claude mcp list` · `/mcp` |

## Architecture of the AI files

Clean Architecture applied to `.claude/` itself. Dependencies point in one direction:

```
hooks/ + settings.json      infra/enforcement — deterministic
        ↓ verifies
skills/                     procedure + exemplars
        ↓ invokes                    ↑ delegates via context: fork
agents/                     isolated execution
        ↓ cites, never copies
rules/ + blueprints/ + .mcp.json   norms and data   ← LEAF

decisions/                  history — nobody reads it at runtime, outside the graph
```

## Invariants (non-negotiable)

1. **`rules/` is a leaf.** A norm never mentions a skill, an agent, or a command. If it
   needs to, it's a procedure and belongs in a skill.
2. **Each norm has a single owning file.** Others cite it by path
   (`@.claude/rules/naming.md`). A norm written in two places has diverged — that's a bug.
3. **Code boilerplate lives in `templates/` inside the skill that emits it**, with the
   `.example` suffix. Never inside a norm, never pasted into the body of a `SKILL.md`.
4. **No new `commands/` are created.** Slash commands were merged into skills: write
   `skills/<name>/SKILL.md` and control invocation with `disable-model-invocation`.
5. **An agent exists only for one of three reasons:** preserving context, restricting
   tools, or changing model. If none applies, it's a skill.
6. **If a rule must always hold, it's a hook or `permissions.deny`** — not prose in
   markdown.
7. **Architectures are data.** Adding a blueprint must never require editing a skill, an
   agent, or a command.
8. **Java and Spring Boot versions are never written from memory.** They are resolved at
   runtime via Spring Initializr; without network access, ask.
9. **The generated project is self-contained.** Whoever clones it does not have
   `claude-spring-architect`. Everything cited from inside the project must exist inside the
   project: norms (step 6.6), development skills (6.7), `ArchHook.java` and
   `schemas/extensions.json` (7). Creation skills (`project-bootstrap`, `init-project`)
   and `blueprints/` are left out on purpose — they only serve before the project exists.
   A new norm or skill here is only complete once the step that copies it has also been
   updated. **An MCP server has the same obligation, per server, not per file:** it is
   declared for this meta-repo (`.mcp.json`), for the generated project
   (`project-bootstrap/templates/mcp.json.example`, copied in step 7.5), or both — and
   the file it's written into **is** that declaration. A server useful to both is
   written in both files on purpose; that is not a duplication bug, see invariant 2.
10. **Recognized frontmatter fields, and `.mcp.json`'s server fields, are data with a
    single owner.** The list lives in `.claude/schemas/extensions.json`, `ArchHook.java
    schema` is what reads it, and any other file that displays it is derived and must
    match it exactly. A corollary of 2 and 7, written separately because its failure
    mode is silent: the runtime ignores an unknown field without any error, and `claude
    plugin validate` lets it through too — and does not look at `.mcp.json` at all.
11. **No literal secret in a versioned file.** `.mcp.json` (this repo's or the copy
    inside a generated project) carries only `${VAR}` / `${VAR:-default}` expansion,
    `oauth`, or `headersHelper` — never a token, key, or password spelled out. Verified
    by `ArchHook.java schema`'s secret scan over `headers`/`env`. A leaked token in git
    history is irreversible; that is why this is a hook and not a review checklist.

## Routing — when X, read Y

| If the task involves | Go to |
|---|---|
| Creating a project from scratch | skill `project-bootstrap` |
| Creating a skill, agent, norm, or `CLAUDE.md` section — and deciding which of the five | skill `claude-code-architect-designer` |
| Designing a use case before implementing it; knowing whether a request is one or several | skill `use-case-design` |
| Aggregate, value object, invariant, ports of an already-designed use case | skill `domain-modeling` |
| Table, JPA mapping, migration, index, slow query, datasource properties | skill `persistence-architect` |
| REST adapter, controller, DTO, status, OpenAPI | skill `rest-api-architect` |
| Tests, coverage, installing ArchUnit | skill `test-architect` |
| Orchestrating a full feature (use case → domain → persistence → REST → tests) | skill `new-feature` |
| Creating a git repo, committing, or pushing the project just generated or just implemented | skill `git-publish` — chained automatically after `/init-project` and after `java-spring-boot-developer` succeeds; behind two confirmations |
| Docker, docker-compose, adding a service (DB, broker) to a project, Testcontainers image consistency at the compose level | skill `docker-architect` |
| Kafka producer/consumer, publishing or consuming a domain event over a broker, topic/partition/DLQ | skill `messaging-architect` |
| Design pattern, growing `if`/`switch` chain | skill `java-patterns` |
| Connecting to an external system (Jira, database, GitHub, Figma), a server exposing `mcp__*` tools, `.mcp.json` | skill `claude-code-architect-designer` |
| Auditing what a `/command` run in a **generated project** cost and which skills it chained | `ArchHook.java audit` — hook, not skill; wired only into `project-bootstrap/templates/settings.json.example`, step 7 parts 4-5 |
| Reading that trail back — spend per skill across runs, which report to open | skill `audit-usage` — runs in the **generated project**, where the trail exists; here it reports the trail is off. The only `disable-model-invocation` skill the hook doesn't record, via `audit.exclude_skills` in `@.claude/schemas/extensions.json` |
| Which norm covers what | `@.claude/rules/00-index.md` |
| Which frontmatter fields are valid in each file type | `@.claude/skills/claude-code-architect-designer/references/frontmatter-fields.md` |
| Why a skill, norm, or agent exists in the form it's in | `@.claude/decisions/README.md` |
| Contract every blueprint fulfills | `@.claude/blueprints/_schema.md` |
| Frontmatter fields the runtime recognizes | `@claude-help.md` |
| How each piece of Claude Code works | `@claude-help.md` |

## Known pitfalls

- **A skill cannot have the name of a native slash command.** The folder name becomes
  the command, and `/doctor`, `/init`, `/context`, `/memory` already exist in the
  runtime. That's why this repo's diagnostic skill is called `arch-doctor`. Shadowing a
  native command doesn't produce an error — it runs the wrong command.
- **`.claude/settings.json` is only read at session startup.** Editing hooks mid-session
  has no effect — `claude` must be restarted.
- **The runtime silently ignores unknown frontmatter.** An invented field is decoration,
  not behavior. List of native fields in `@claude-help.md`.
- **Everything the model must obey lives in the body of the file**, never in
  frontmatter. `metadata.*` was removed from skills and agents: ownership, `reads`,
  `handoff`, and contracts live in the `## Contrato` section of the body. Do not put
  `metadata:` back into a `SKILL.md` — it costs tokens on every invocation and enforces
  nothing.
- **The `CLAUDE.md` at the root of a generated project is not this file.** It is
  produced from
  `.claude/skills/project-bootstrap/templates/root.CLAUDE.md.example`.
- **`claude plugin validate` does not validate fields.** It accepts `metadata:`, accepts
  camelCase in a skill, and accepts an invented field, always with `✔ Validation
  passed`. It does not look at `.claude/agents/`, `.claude/rules/`, or
  `.claude/settings.json`. It catches malformed YAML, nothing else. What validates
  fields is `java .claude/hooks/ArchHook.java schema`.
- **`.claude/decisions/` is neither a norm nor living documentation.** It records what
  was decided on the date, not what holds true today. It has no `paths`, does not enter
  `00-index.md`, and does not go into the generated project. It is superseded by a new
  record; the old one is not rewritten.
- **This repository does not run `./mvnw`.** `ArchHook` exits 0 when it doesn't find
  the wrapper; here that's expected, not a failure.
- **`allowed-tools` with `Bash(command:*)` checks each segment of the pipe separately.**
  An injection `` !`a | b | c` `` in the body needs a rule for `a`, `b`, and `c`; miss
  one and the whole command is blocked before it runs. Write injections as a single
  command (`ls .claude/skills`, not `find … | sed | sort`). This only affects skills
  that restrict Bash: `allowed-tools: Bash` without a filter lets the whole pipeline
  through.
- **The `audit` mode is off in this repository, on purpose.** It switches itself on by
  the presence of `.claude/audit-usage/`, and this meta-repo doesn't create the
  directory: the trail is a feature of the *generated* project, wired in
  `project-bootstrap/templates/settings.json.example`, not in this repo's
  `settings.json`. Copying those hook entries here would start auditing the design of
  the tool instead of its use. What it records and why it isn't a skill:
  `@.claude/decisions/0035-auditoria-execucao-hook.md`. The consequence for
  `/audit-usage` is that running it **here** correctly reports the trail is off — its
  territory is the generated project, and it is exercised there, not in this repo.
- **`/audit-usage` is the one manual skill the trail doesn't record.** It's listed in
  `audit.exclude_skills` in `@.claude/schemas/extensions.json`, so reading the trail
  doesn't append a report about reading the trail. Invoking it still *closes* the run in
  progress — which is also the only way, inside a live session, to get a report that
  isn't stamped `⏳ em andamento`. Why:
  `@.claude/decisions/0036-skill-audit-usage.md`.
- **`.mcp.json` is only read at session startup**, same as `settings.json`. Adding or
  editing a server mid-session has no effect until `claude` is restarted.
- **A project-scoped server in `.mcp.json` needs one-time human approval** the first
  time it loads (`claude mcp list` shows pending ones). That prompt is the trust
  boundary a cloned repository can't skip — never work around it with
  `enableAllProjectMcpServers`.
- **Server precedence is `local > project > user`, silently.** A personal server with
  the same name as the team's `.mcp.json` one shadows it — no warning either way.
- **An unset `${VAR}` in `.mcp.json` doesn't fail generation.** The server loads with
  the literal `${VAR}` text and fails to connect at runtime instead. `claude mcp list`
  surfaces the missing-variable warning; frontmatter schema validation does not.
