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
| Check every compose service is up, no foreign container holds its ports, and every published port is advertised at a host-resolvable address | `java .claude/hooks/ArchHook.java compose` |
| Check which paths a shell command would write, and whether the guard admits them | `echo '{"tool_input":{"command":"…"}}' \| java .claude/hooks/ArchHook.java guard bash` |
| Sweep what the current turn wrote against the open phase's territory and the frozen folders | `echo '{}' \| java .claude/hooks/ArchHook.java guard sweep` |
| The compose check as a gate — silent while healthy, exit 2 otherwise | `echo '{}' \| java .claude/hooks/ArchHook.java compose gate` |
| Render the execution trail of a run by hand | `java .claude/hooks/ArchHook.java audit flush` |
| Write a target project's `.claude/` from this one, transformed for a blueprint | `java .claude/hooks/ArchHook.java export <dest> --blueprint <id> [--dry-run]` |
| Validate frontmatter of all extension files, `.mcp.json`, every hook registration in `settings.json` and in `project-bootstrap`'s template, every `` !`…` `` injection's paths, and the `export` manifest against what is on disk | `java .claude/hooks/ArchHook.java schema` |
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

`claude-code-architect-designer` writes upward into `hooks/ + settings.json` after approval —
a design-time edge, like `project-bootstrap` writing `src/`; the runtime direction is unchanged.

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
   markdown. Owner: `claude-code-architect-designer`, forms 7 and 8, after approval and
   always with a record in `decisions/`. The mirror holds too — a hook against a failure
   nobody observed is a process per event confirming what was already true.
7. **Architectures are data.** Adding a blueprint must never require editing a skill, an
   agent, or a command.
8. **Java and Spring Boot versions are never written from memory.** They are resolved at
   runtime via Spring Initializr; without network access, ask.
9. **The generated project is self-contained.** Whoever clones it does not have
   `claude-spring-architect`. Everything cited from inside the project must exist inside the
   project: norms, development skills, agents, `ArchHook.java` and
   `schemas/extensions.json`. `ArchHook.java export` writes all of it (step 6.6 of
   `project-bootstrap`), and **what travels is data, in the `export` block of
   `@.claude/schemas/extensions.json`** — creation skills excluded on purpose, and the
   blueprint **catalog** too, with one exception the manifest names: the **active**
   blueprint travels (`export.blueprint_copy`), because the stamp records its id, an
   update has to resolve it, and a blueprint written during adoption exists nowhere
   else. A new norm, skill or agent is
   complete once that block lists it: every skill and agent on disk in `include` or
   `exclude`, a `derived_paths` entry for a rule with a package territory, and
   `ArchHook.java schema` failing by name on either gap.
   **An MCP server has the same obligation, per server, not per file:** it is declared
   for this meta-repo (`.mcp.json`), for the generated project
   (`project-bootstrap/templates/mcp.json.example`, copied by `export.optional_copy`),
   or both — and the file it's written into **is** that declaration. A server useful to both is
   written in both files on purpose; that is not a duplication bug, see invariant 2.
   **A hook, the same:** the registration is per file (`.claude/settings.json`,
   `project-bootstrap/templates/settings.json.example`); a mode inside `ArchHook.java`
   needs nothing, the `export` mode copies that file whole.
   **A skill has a second obligation on top of `export`:** a class in `skill_classes`,
   which is what gives it a body shape and a write territory. A skill in no class has no
   territory — `schema` fails by name on it, in this repo and inside a generated project.
   **An agent has the same second obligation**, in `agent_classes`: its class fixes the
   sections its body carries, the frontmatter fields it must declare (`model` and `tools`
   always — two of the three reasons it is allowed to exist), whether it may write at all
   (`executor`), and the paths it may write. Territory there is keyed on `agent_type`, so it
   holds no matter which skill phase the caller left open.
10. **Recognized frontmatter fields, `.mcp.json`'s server fields, and `settings.json`'s
    hook events and entry fields are data with a single owner.** The list lives in
    `.claude/schemas/extensions.json`, `ArchHook.java schema` is what reads it, and any
    other file that displays it is derived and must match it exactly. A corollary of 2
    and 7, written separately because its failure mode is silent: the runtime ignores an
    unknown field without any error — an unknown *event* too, and a `matcher` on an event
    that never reads one — and `claude plugin validate` lets it all through, looking at
    neither `.mcp.json` nor `settings.json`. Binds the Java too: a mode reads its lists
    from `extensions.json`, never from a constant in the source.
11. **No literal secret in a versioned file.** `.mcp.json` (this repo's or the copy
    inside a generated project) carries only `${VAR}` / `${VAR:-default}` expansion,
    `oauth`, or `headersHelper` — never a token, key, or password spelled out. Verified
    by `ArchHook.java schema`'s secret scan over `headers`/`env`. A leaked token in git
    history is irreversible; that is why this is a hook and not a review checklist.

## Routing — when X, read Y

| If the task involves | Go to |
|---|---|
| Creating a project from scratch | skill `project-bootstrap` |
| Installing this `.claude/` into a project that was never generated here, or pulling a newer version into one that was | skill `arch-adopt` — manual only, runs **inside the target project**, refuses a dirty worktree, and writes through `ArchHook.java export`. It travels into the generated project, unlike the other creation skills: that is how a project updates itself once the plugin that delivered it is gone |
| Creating a skill, agent, norm, or `CLAUDE.md` section — and deciding which of the eight | skill `claude-code-architect-designer` |
| Creating a hook, a new `ArchHook.java` mode, or a `permissions.allow`/`deny` line — and deciding whether the answer is a guarantee at all | skill `claude-code-architect-designer`, forms 7 and 8. Writes it after approval, always with a record in `decisions/`, and warns that `settings.json` is read only at startup |
| Designing a use case before implementing it; knowing whether a request is one or several | skill `use-case-design` |
| Aggregate, value object, invariant, ports of an already-designed use case | skill `domain-modeling` |
| Table, JPA mapping, migration, index, slow query, datasource properties | skill `persistence-architect` |
| REST adapter, controller, DTO, status, OpenAPI | skill `rest-api-architect` |
| Tests, coverage, installing ArchUnit | skill `test-architect` |
| Orchestrating a full feature (use case → domain → REST → persistence → tests) | skill `new-feature` — manual only: the user types `/new-feature <description>`, the model can't invoke it. One use case per run |
| Implementing a spec that is already `approved`, in a later session | the same skill: `/new-feature UC-NNN-<slug>` over an approved spec goes straight to the executor delegation, skipping the design steps and consolidation. It is the only supported door to the pre-flight, the `CHANGELOG.md` writes, the four mandated findings and the `git-publish` chaining |
| Creating a git repo, committing, or pushing the project just generated or just implemented | skill `git-publish` — chained automatically after `/init-project` and after `java-spring-boot-developer` succeeds; behind two confirmations |
| Docker, docker-compose, adding a service (DB, broker) to a project, Testcontainers image consistency at the compose level, choosing an observability backend (Jaeger or Grafana+Tempo+Prometheus) behind the OTLP collector | skill `docker-architect` |
| A versioned file citing a `docs/use-cases/UC-NNN-slug/` folder that was deleted or renamed | `ArchHook.java doctor` — the `UC references` line, so `/arch-doctor` reports it. Reads `doctor.uc_references` from `@.claude/schemas/extensions.json`; silent in this meta-repo, which has no `docs/use-cases/` |
| A spec citing a backlog row — `BL-NN` — that `docs/use-cases/BACKLOG.md` does not have, in either its active or its retired table | `ArchHook.java doctor` — the `BL references` line, same shape and same data block (`doctor.bl_references`). `BL-NN` is the backlog's own identifier, assigned by `use-case-design` on append and never a `UC` number: it exists so an impact row can name the case that satisfies a precondition while that case is still backlog |
| A container that "started" but isn't answering, a port already allocated, OTLP traffic reaching the wrong collector, a compose `image:` tag that disagrees with the one `src/test` pins in `DockerImageName.parse`, a broker published to the host that no host client can reach | `ArchHook.java compose` — hook, not skill. Also folded into `doctor`, so `/arch-doctor` reports it. The tag comparison and the advertised-address check need no Docker: they read files |
| Kafka producer/consumer, publishing or consuming a domain event over a broker, topic/partition/DLQ | skill `messaging-architect` |
| Design pattern, growing `if`/`switch` chain | skill `java-patterns` |
| Connecting to an external system (Jira, database, GitHub, Figma), a server exposing `mcp__*` tools, `.mcp.json` | skill `claude-code-architect-designer` |
| Auditing what any skill or agent run in a **generated project** cost and what it chained — invoked by `/command` or by the model | `ArchHook.java audit` — hook, not skill; wired only into `project-bootstrap/templates/settings.json.example`, installed by step 6.6 |
| Reading that trail back — spend per skill and agent across runs, which report to open | skill `audit-usage` — aggregation by `ArchHook.java audit summary`, the skill renders; runs in the **generated project**, where the trail exists; here it reports the trail is off. An observer: the hook leaves it no report, via `audit.exclude_skills` in `@.claude/schemas/extensions.json` |
| Which norm covers what | `@.claude/rules/00-index.md` |
| Which class a skill is, what it may write, and what sections its body must carry | `skill_classes` in `@.claude/schemas/extensions.json` — `ArchHook.java schema` validates the body, `guard` enforces the territory |
| The same for an agent — plus which frontmatter fields it owes and whether it may write at all | `agent_classes` in `@.claude/schemas/extensions.json`. Three classes: `driver` (interviews and delegates, writes a tree that doesn't exist yet), `executor` (implements an approved spec), `installer` (one-shot setup, narrow fixed paths). `executor: true` is the single owner of who may write — the old `guard.executor_agents` list is gone |
| Which frontmatter fields are valid in each file type | `@.claude/skills/claude-code-architect-designer/references/frontmatter-fields.md` |
| Why a skill, norm, or agent exists in the form it's in | `@.claude/decisions/README.md` |
| Contract every blueprint fulfills | `@.claude/blueprints/_schema.md` |
| Frontmatter fields the runtime recognizes | `@claude-help.md` |
| How each piece of Claude Code works | `@claude-help.md` |
| A skill, hook, injection or MCP server that silently does nothing — and the runtime trap behind it | `@docs/11-pitfalls.md` |

## Known pitfalls

Only what is specific to **this** repository. The runtime's own silent traps — a skill named
after a native command, `$ARGUMENTS` interpolated in prose, `allowed-tools` checking each pipe
segment, an injection inheriting the shell's cwd, unknown frontmatter ignored,
`claude plugin validate` validating no field, a hook's four silent failures, which
`ArchHook.java` modes read stdin, `AskUserQuestion`'s 2-option floor and 4-question ceiling,
and the four `.mcp.json` ones — live in `@docs/11-pitfalls.md`, with `@claude-help.md` as their
source.

- **A skill writes only its class's territory, and `guard` blocks the rest with exit 2.**
  Deny by default; the message names the class and its `write_allow`, and the fix is the data
  (`skill_classes` in `@.claude/schemas/extensions.json`), never a retry. A design run is
  docs-only and cannot even *call* a `build`-class skill — `docker-architect` included: the
  missing compose service is recorded in the partial and materialized afterwards by
  `/docker-architect`. With no skill phase open, territory is unrestricted, which is why
  editing a file by hand is never blocked.
- **A subagent's write is judged by `agent_classes`, not by the caller's phase.** Every write
  carries its `agent_type`, and `guard` checks the path against the agent's own `write_allow`,
  so an open design phase neither widens nor narrows it. An agent no class lists falls back to
  the caller's phase — nothing else describes what it may write.
- **Enforcement is no longer tool-shaped, but it is still not filesystem-shaped.** Until
  lessons-learned-014 § 1 every hook matched on a tool name, so a heredoc, a `sed -i` or a
  `tee` passed through `guard`, `check`, `format`, `audit` and `schema` alike — the exact
  spellings a host instruction to prefer `Bash` over `Write`/`Edit` produces. `guard bash`
  (`PreToolUse`, matcher `Bash`) now reads the command for the write shapes in
  `guard.bash_write_shapes` and applies the territory and frozen-folder checks to every target
  it can read literally. **What it deliberately does not do:** a target holding `$`, a backtick
  or a glob, or landing outside the repository, is skipped without a word, and `check` and
  `format` stay off `Bash` entirely — ArchUnit and `spotless:apply` already re-check both,
  while a `PostToolUse` matcher there would pay a JVM on every `ls`. So the true statement is
  narrow: **inside Claude Code, through `Write`/`Edit`/`MultiEdit`/`NotebookEdit`, and through
  the shell spellings the shape list names.** Design:
  `@.claude/decisions/0063-bash-write-enforcement.md`.
- **`guard sweep` backs both write guards on `Stop`, and it is git-shaped.** It diffs the
  working tree against the baseline `guard prompt` takes at `UserPromptSubmit`, so a tree dirty
  before the turn is not reported, and it runs the same territory and frozen-folder checks over
  whatever changed — no matter which tool wrote it. Two limits worth knowing before reading it
  as total coverage: it is **detection, not prevention** (the write already happened), and a
  path git ignores never appears in `git status --porcelain`, so `.claude/decisions/` and
  `.claude/lessons-learned/` are never swept in this repository. A spec's `status:` close and
  its `[ ]` → `[x]` toggles are admitted by comparing against `git show HEAD:`, since the sweep
  has no `old_string` to read. Design: `@.claude/decisions/0065-guard-sweep-on-stop.md`.
- **`compose gate` runs the compose check unprompted, and it blocks.** `docker-architect` step 7
  already called `ArchHook.java compose` "not optional" and it had never been run against a
  project: a `kafka` block publishing 9092 while advertising only `kafka:9092` shipped on day
  one and was unreachable from every host client until a use case needed it. The gate is the
  same `composeReport()` — one definition of healthy, shared with `doctor` — silent while
  healthy, exit 2 with the failing lines otherwise. A service stopped on purpose blocks the stop
  too; that is a gate, not a bug. Design: `@.claude/decisions/0064-compose-gate-on-stop.md`.
- **An implemented use case's folder is frozen except for three writes:** the spec's `status:`
  line moved along `guard.status_transitions`, a checklist toggle in it, and
  `UC-NNN/CHANGELOG.md` — the write `/new-feature`'s consolidation *requires* for every change
  an impact row makes. Exempt basenames are data (`guard.frozen_exempt_basenames`), matched
  directly under the folder: `notes/CHANGELOG.md` is still frozen.
- **The spec has three closed states, and the checklist is ticked before the status closes.**
  `approved` closes to `implemented`, or to `implemented-blocked` when the run left an approved
  use case unreachable end to end — the code is on disk and green, and a `Satisfied by` the spec
  named is not there yet. Either reverts to `approved`, which is the exit a run that closed by
  mistake did not have. A `[ ]` → `[x]` toggle is admitted in **all three**
  (`guard.checklist_toggle_statuses`): it is monotone, and requiring `approved` once froze 23
  unticked boxes forever. The order is still the executor's contract, not the hook's: the status
  line is the last write it makes to the folder. Design:
  `@.claude/decisions/0066-spec-state-machine.md`.
- **A healthcheck that passes proves nothing about host reachability** — it runs inside the
  container, where `localhost` is the service. A service that publishes a port to the host
  while advertising only its compose-network name (`KAFKA_ADVERTISED_LISTENERS:
  PLAINTEXT://kafka:9092` next to `ports: "9092:9092"`) is reachable from no host client, and
  neither the healthcheck nor Testcontainers sees it — Testcontainers wires its own listeners.
  `ArchHook.java compose` reads the file for it (`compose.advertised_env_suffixes`); a service
  that advertises nothing claims nothing and is left alone.
- **`pom.xml` has exactly one writer inside a feature run: the executor, for a dependency the
  spec declares** (the messaging partial's § 7, or the persistence equivalent). The design
  skills are docs-only, `/new-feature` writes the spec, the two installers own only their own
  setup. Anything else in the file — a plugin, a property, a version bump — is reported, never
  written.
- **The outbox belongs to `persistence-architect`, all of it** — table, columns, claim query,
  and the `app.outbox.*` values that pace the claim. `messaging-architect` declares that the
  case needs one and which delivery guarantee the relay must honour, never a column name, and
  keeps the relay's broker side. A § 6 row naming columns is a divergence, and a column
  decision that would drop a declared guarantee stops the pipeline instead of being settled by
  precedence.
- **The bounded context is a project fact, not a per-use-case answer.** First segment of every
  topic name, asked with the coordinates in `/init-project` and written into the generated
  project's root `CLAUDE.md`. A prefix chosen inside one use case gives one system two
  namespaces.
- **`grep -A2 "^services:" docker-compose.yml` is not the list of services.** It reads two
  lines and stops, dropping services declared further down and reporting the children of
  `volumes:` as services. Every piece that needs that list — `docker-architect`'s injection
  and step 3, `messaging-architect` step 9, `persistence-architect` step 9 — uses the `awk`
  one-liner bounded to the `services:` block. A wrong portrait invites recreating a service
  that already exists.
- **The `CLAUDE.md` at the root of a generated project is not this file.** It comes from
  `.claude/skills/project-bootstrap/templates/root.CLAUDE.md.example`.
- **`.claude/decisions/` is neither a norm nor living documentation.** It records what was
  decided on the date, not what holds today: no `paths`, outside `00-index.md`, and it does
  not travel to the generated project. A new record supersedes the old one; the old one is not
  rewritten.
- **This repository does not run `./mvnw`.** `ArchHook` exits 0 when it finds no wrapper; here
  that is expected, not a failure.
- **The `audit` mode is off in this repository, on purpose.** It switches itself on by the
  presence of `.claude/audit-usage/`, which this meta-repo does not create: the trail is a
  feature of the *generated* project, wired in
  `project-bootstrap/templates/settings.json.example`. Copying those entries here would audit
  the design of the tool instead of its use — `@.claude/decisions/0035-auditoria-execucao-hook.md`.
  So `/audit-usage` run **here** correctly reports the trail is off.
- **The trail doesn't record its observers.** `/audit-usage` and `/arch-doctor` change nothing,
  so `audit.exclude_skills` leaves them no report of their own. Invoking one still *closes* the
  run in progress — the only way, inside a live session, to get a report not stamped
  `⏳ em andamento`. Why: `@.claude/decisions/0036-skill-audit-usage.md`.
