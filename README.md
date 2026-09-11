# Claude Spring Architect

[![CI](https://github.com/ice-lfernandes/claude-spring-architect/actions/workflows/validate.yml/badge.svg)](https://github.com/ice-lfernandes/claude-spring-architect/actions/workflows/validate.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](#contributing)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-orange.svg)](#dependencies)
[![Built with Claude Code](https://img.shields.io/badge/Built%20with-Claude%20Code-D97757.svg)](https://claude.com/claude-code)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-GA%20latest-6DB33F.svg?logo=spring)](https://start.spring.io)
[![Spring Framework](https://img.shields.io/badge/Spring-Framework-6DB33F.svg?logo=spring)](https://spring.io/projects/spring-framework)
[![GitHub stars](https://img.shields.io/github/stars/ice-lfernandes/claude-spring-architect.svg?style=social)](https://github.com/ice-lfernandes/claude-spring-architect/stargazers)
[![GitHub forks](https://img.shields.io/github/forks/ice-lfernandes/claude-spring-architect.svg?style=social)](https://github.com/ice-lfernandes/claude-spring-architect/network/members)
[![GitHub issues](https://img.shields.io/github/issues/ice-lfernandes/claude-spring-architect.svg)](https://github.com/ice-lfernandes/claude-spring-architect/issues)
[![Last commit](https://img.shields.io/github/last-commit/ice-lfernandes/claude-spring-architect.svg)](https://github.com/ice-lfernandes/claude-spring-architect/commits)

**A template for bootstrapping Spring Boot projects already prepared for AI-assisted
development — with a selectable architecture, boundaries verified by the build, and
zero instruction duplication.**

---

## The problem

Anyone using code assistants on Java projects knows the pattern: you start with a
30-line `CLAUDE.md`, six months later it's 400, the model ignores half of it, and the
same rules show up duplicated across three different skills — which have since
diverged. The AI "forgets" the architecture because nobody can tell it, cheaply and
reliably, what's actually true in this project.

At the same time, every new project starts from zero: the same conventions written
again, the same structural decisions made again, and nothing learned from the previous
project survives.

## The idea

Treat the AI files as production code: **explicit architecture, dependencies in one
direction, a single source of truth per rule, and automatic enforcement**.

The result is a self-contained `.claude/` that can be copied into any repository and
that:

- Generates the structure of a complete Spring Boot project from a **declarative
  blueprint** — Hexagonal, Clean, Onion, Layered, Vertical Slice, Modular Monolith, or
  your own.
- Keeps context cheap: the model loads a norm **when** it needs it, not on every turn.
- Prevents architecture violations at the moment they happen, through hooks, without
  relying on the model remembering the rule.
- Allows adding a new architecture **without editing a single prompt**.

## Purpose

| Goal | How it's achieved |
|---|---|
| No unusual dependencies | JDK, Maven, git and curl. Zero Python, zero template engines, zero shell |
| Genuinely cross-platform | Hooks in a single Java file in exec form — Linux, macOS and Windows, no variants |
| Cheap context | `CLAUDE.md` under 200 lines; norms loaded on-demand via `paths` |
| Zero duplication | Each norm has one owning file; every other file cites it by path |
| Architecture respected | Hooks block forbidden imports derived from the blueprint |
| Replicable | Self-contained in `.claude/` — nothing depends on global skills in `~/.claude` |
| Extensible | Architectures are data (YAML), not prompts |

---

## Guiding principle

The same Clean Architecture we apply to Java code applies to the AI files.
Dependencies flow in one direction and there are no cycles:

```
                    ┌──────────────────────────────────────┐
   INFRA/ENFORCE    │  hooks/  ·  settings.json            │  deterministic
                    └──────────────┬───────────────────────┘
                                   │ verifies
                    ┌──────────────▼───────────────────────┐
   ADAPTERS         │  agents/  (isolated execution)       │  entry points
                    └──────────────┬───────────────────────┘
                                   │ invokes
                    ┌──────────────▼───────────────────────┐
   APPLICATION      │  skills/  (procedure + exemplars)    │  the "how"  ·  /name
                    └──────────────┬───────────────────────┘
                                   │ cites (never copies)
                    ┌──────────────▼───────────────────────┐
   DOMAIN           │  rules/   (norms)  ·  blueprints/    │  the "what / what not"
                    └──────────────────────────────────────┘
                              ↑ LEAF — references no one
```

Seven derived rules:

1. **`rules/` is a leaf.** A norm never mentions a skill, agent, or command. If it
   needs to, it's a procedure and belongs in a skill.
2. **Each norm has a single owning file.** Others cite it by path
   (`@.claude/rules/naming.md`). A rule written in two places will diverge — that's a
   bug.
3. **`CLAUDE.md` is an index, not a manual.** Only invariants and routing; target
   under 200 lines.
4. **Skills own their exemplars.** Code boilerplate lives in `templates/` inside the
   skill that emits it, with the `.example` suffix — never inside a norm, never pasted
   into the body of a `SKILL.md`. The norm states what must be true; the exemplar gives
   the shape.
5. **No new `commands/` are created.** Slash commands were merged into skills: a
   `skills/<name>/SKILL.md` with `disable-model-invocation: true` gives the same
   `/name` and gains a support folder, `paths`, and `context: fork`.
6. **An agent exists only for one of three reasons:** preserving context, restricting
   tools, or changing model. If none applies, it's a skill.
7. **Hooks verify, they don't teach.** What's validated by command doesn't go into
   prose. If the violation is a build, security, or compliance bug, it belongs in a
   hook or `permissions.deny` — not in `CLAUDE.md`.

---

## Features

- **Architecture blueprints** — 7 out of the box, extensible via YAML file.
- **Three levels of instruction** — global constitution, per-module context, detailed
  on-demand norms.
- **Hook-based enforcement** — formatting, incremental compilation, and forbidden
  imports on post-edit; tests of affected modules on stop.
- **Boundaries derived from the blueprint** — each module's `CLAUDE.md` is
  *generated*, not hand-written, so it never diverges from what the build enforces.
- **Versions resolved at runtime** — no Spring Boot version hardcoded in the template.
- **Skills by capability** — domain, use cases, REST, persistence, messaging, tests;
  each triggers by description, without explicit invocation.
- **Packaged pipeline** — `/new-feature` runs the full chain end-to-end.

---

## Structure

```
claude-spring-architect/
├── CLAUDE.md                      # facts about this meta-repo (not the generated project's)
├── CONTEXT.md                     # continuity log: what's done, what's left, what can't break
└── .claude/
    ├── settings.json              # hooks + permissions (versioned)
    ├── settings.local.json        # (gitignored) personal overrides
    ├── rules/                     # norms, on-demand (leaves of the graph)
    │   ├── 00-index.md            #   map of norm → file → paths → who verifies
    │   ├── architecture-ddd.md    #   master content: copied into the generated project
    │   ├── naming.md
    │   ├── code-quality.md
    │   ├── error-handling.md
    │   ├── api-rest.md
    │   ├── lombok.md
    │   ├── value-objects.md
    │   ├── persistence.md
    │   ├── testing.md
    │   ├── observability.md
    │   └── logging.md
    ├── blueprints/                # architectures as data
    │   ├── _schema.md             #   contract every blueprint fulfills
    │   ├── hexagonal/hexagonal.yaml
    │   ├── clean-architecture-multi-module/clean-architecture-multi-module.yaml
    │   ├── clean-architecture-single-module/clean-architecture-single-module.yaml
    │   ├── layered/layered.yaml
    │   ├── modular-monolith/modular-monolith.yaml
    │   ├── onion/onion.yaml
    │   ├── custom-template/custom.template.yaml
    │   └── vertical-slice/vertical-slice.yaml
    ├── skills/                    # procedure + exemplars
    │   ├── init-project/SKILL.md              #   /init-project — stays in this repo
    │   ├── arch-doctor/SKILL.md                #   /arch-doctor  — copied into the generated project
    │   ├── claude-code-architect-designer/     #   designs this .claude/ itself — stays in this repo
    │   ├── project-bootstrap/                  #   stays in this repo: builds, doesn't maintain
    │   │   ├── SKILL.md
    │   │   ├── references/        #   reading material for the model
    │   │   └── templates/         #   real, compilable exemplars (*.example)
    │   ├── use-case-design/       #   pipeline 1/5 — copied into the generated project
    │   ├── domain-modeling/       #   pipeline 2/5 — copied
    │   ├── persistence-architect/ #   pipeline 3/5 — copied
    │   ├── rest-api-architect/    #   pipeline 4/5 — copied
    │   ├── test-architect/        #   pipeline 5/5 — copied; owns the ArchUnit exemplar
    │   ├── new-feature/           #   orchestrates the 5 above — copied
    │   ├── java-patterns/         #   preloaded into the executor agent — copied
    │   ├── docker-architect/      #   extends docker-compose/Dockerfile after bootstrap — copied
    │   └── messaging-architect/   #   optional pipeline step — Kafka producer/consumer — copied
    ├── agents/                    # isolated context
    │   ├── project-initializer.md         #   drives /init-project
    │   ├── java-spring-boot-developer.md  #   /new-feature's executor — copied
    │   └── archunit-installer.md          #   test-architect's setup mode — copied
    ├── decisions/                 # history of why each piece is shaped as it is (not a norm, not copied)
    └── hooks/
        └── ArchHook.java          # enforcement, one file, four modes
```

There is no `commands/`: slash commands live as skills with
`disable-model-invocation: true`. `forbidden-imports.txt` is not here — it is
generated inside the project during `/init-project`.

**The generated project is self-contained.** Whoever clones it does not need this
repository: `/init-project` copies over the norms (`rules/*.md`), the development
skills (`arch-doctor`, `use-case-design`, `domain-modeling`, `persistence-architect`,
`rest-api-architect`, `java-patterns`, `test-architect`, `new-feature`,
`docker-architect`, `messaging-architect`), the executor agents (`java-spring-boot-developer.md`,
`archunit-installer.md`), and `ArchHook.java`. Only `project-bootstrap`, `init-project`,
`claude-code-architect-designer`, `blueprints/`, and `decisions/` are left out — they
serve before the project exists, or record decisions about this meta-repo, not the
generated one.

### Where to write what

| You're writing | Goes to | Why |
|---|---|---|
| "Never use `@Autowired` on a field" | `rules/naming.md` | It's a norm — single source |
| "How to create a REST adapter, step by step" | `skills/rest-api-architect/SKILL.md` | It's a procedure |
| A reference `Controller.java` | `skills/rest-api-architect/templates/` | It's the exemplar of the skill that applies it |
| The body of `DomainException` | `skills/domain-modeling/templates/DomainException.java.example` | Code never lives in a norm: it would load into context on every `.java` file touched |
| "The domain doesn't import Spring" | `rules/architecture-ddd.md` + `blueprints/*.yaml` | Norm + verifiable data |
| "Run spotless after editing" | `hooks/ArchHook.java` + `settings.json` | Verifiable by command |
| "Never read `application-prod.yml`" | `settings.json` → `permissions.deny` | Compliance isn't requested, it's enforced |
| "This project uses Maven and Java 21" | `CLAUDE.md` | Always true, always relevant |
| A ritual you trigger by hand (`/release`) | `skills/release/SKILL.md` + `disable-model-invocation: true` | No new `commands/` are created |

---

## Getting Started

Two sequences, and order matters in both, for concrete reasons.

### A · Install the AI layer (once, **before** opening Claude)

```bash
# 1. directory + git with an existing HEAD
mkdir my-api && cd my-api
git init
git commit --allow-empty -m "chore: initial repository"

# 2. install .claude/ BEFORE starting the session
unzip -q ~/Downloads/claude-spring-architect-scaffold.zip -d /tmp/ais
cp -r /tmp/ais/claude-spring-architect/.claude .

# 3. confirm the tools (nothing to install)
java --version && git --version && curl --version | head -1

# 4. commit the AI layer, separate from the code
git add .claude
git commit -m "chore: setup Claude Code (hexagonal blueprint)"

# 5. only now open the session
claude
```

No `chmod`: the hooks are not shell scripts. On Windows the commands are the same,
with `mvnw.cmd` in place of `./mvnw` once the project is generated.

Inside the session, `/arch-doctor` tells you whether enforcement is actually active on
this machine.

Then, **inside** the session:

```
/init-project --build maven
```

Don't copy a `CLAUDE.md`: the project's is **generated** in step 6 of the bootstrap,
from `.claude/skills/project-bootstrap/templates/root.CLAUDE.md.example`, already with
resolved versions and the real list of modules. The `CLAUDE.md` at the root of this
repository describes the meta-repo itself and doesn't serve a Spring project.

Three reasons for this exact order:

- **`.claude/settings.json` is read at session startup.** Copying it after opening
  `claude` means the hooks stay inactive until you restart — the most common mistake,
  because everything *looks* like it's working.
- **`git commit --allow-empty` before anything else**, because `ArchHook.java tests`
  uses `git diff HEAD`. In a repo with no commits, `HEAD` doesn't exist and the hook
  exits silently without running a single test.
- **Committing `.claude/` before generating the project** makes the following
  `git diff HEAD` show only generated code, not the AI layer. Clean diff for review.

### B · Internal generation order (what `/init-project` runs)

To run by hand, or to understand what the agent does in step 4:

```bash
# 1. base + GA versions, nothing fixed in this repository — already brings mvnw/mvnw.cmd
curl -sS https://start.spring.io/starter.tgz \
  -d type=maven-project -d groupId=com.example -d artifactId=my-api \
  -d dependencies=web,data-jpa,actuator | tar -xzf - -C .

./mvnw -v                       # 2. confirms the extracted wrapper works
                                # 3. parent pom (pom packaging + <modules>)
                                # 4. module poms (topological order from depends_on)
                                # 5. .claude/forbidden-imports.txt + <module>/CLAUDE.md
                                # 6. .gitignore (already came from Initializr) + CI

./mvnw -q clean test-compile              # validates the architectural boundaries
./mvnw -q test                            # smoke
./mvnw -q -pl bootstrap spring-boot:run   # does the context come up?

git add . && git commit -m "feat: initial structure (hexagonal)"
```

Don't run `mvn -N wrapper:wrapper`: the Initializr's `starter.tgz` already brings
`mvnw`, `mvnw.cmd`, and `.mvn/wrapper/` ready to go — regenerating it is redundant and
requires `mvn` on the PATH, which this template doesn't require (see D7).
`ArchHook.java` in `check`/`tests` mode tests whether `./mvnw` exists and, if not,
warns on stderr and exits without blocking — so confirming the wrapper **before** the
POMs avoids green reports that actually didn't run anything.

### Silent failures to know about

| Symptom | Cause | Verification |
|---|---|---|
| Hooks never fire | `settings.json` copied with the session already open | Restart: `/exit` and `claude -c` |
| Hooks fail on any OS | `java` not on PATH | `/arch-doctor` |
| Forbidden import doesn't block | `.claude/forbidden-imports.txt` doesn't exist (it's generated during init) | `/arch-doctor` shows the number of active rules |
| Tests never run on `Stop` | No `HEAD` or no Maven wrapper | `/arch-doctor` |

The hooks are written defensively: they exit with 0 when they can't verify something.
That's safe, but it gives false greens. To harden them, swap the guard `exit 0` for
`exit 2` with a message — noisier, but it never lies about the state of the code.

---

## Flows

### `/init-project` — creating the project

```
/init-project
     │
     ▼  agent: project-initializer (isolated context, model: opus)
     │
     ├─ 1. INTERVIEW — blueprint · coordinates · build · features
     ├─ 2. VALIDATES blueprint — 5-rule checklist            [fails fast]
     ├─ 3. BASE via Spring Initializr (curl)                 [GA versions, nothing hardcoded]
     ├─ 4. RESTRUCTURES into modules per the blueprint       [exemplars give the shape]
     ├─ 5. GENERATES forbidden-imports.txt + <module>/CLAUDE.md
     ├─ 6. INSTALLS hooks
     ├─ 7. VERIFIES build + smoke + tested boundary block
     └─ 8. REPORT in the fixed output contract
```

### `/new-feature` — end-to-end feature

```
/new-feature UC-NNN-<slug>
     │
     ▼  skill: new-feature (orchestrates, no agent involved in this part)
     │
     use-case-design → domain-modeling → persistence-architect
        → messaging-architect (conditional) → rest-api-architect → test-architect
     │
     ▼  consolidates into UC-NNN-spec.md
     │
     ▼  agent: java-spring-boot-developer (executor, invoked manually at the end)
```

Each step receives the structured output of the previous one. Free-prose handoff
degrades by the third hop; that's why one step's `output-contract` is literally the
next one's `input-contract`.

### Edit cycle (what happens on every `Write`/`Edit`)

```
Write/Edit on *.java
     │
     ├─ ArchHook format   spotless on the touched module        (never blocks)
     └─ ArchHook check    forbidden imports + compilation        (blocks: exit 2)
                          └─ reads .claude/forbidden-imports.txt

end of task (Stop)
     └─ ArchHook tests    tests of the changed modules            (blocks: exit 2)
                          └─ respects stop_hook_active, doesn't loop
```

---

## Blueprints

| ID | Layout | Choose if | Cost |
|---|---|---|---|
| `layered` | single-module | CRUD, familiar controller/service/repository vocabulary | Same single-module cost as `clean-architecture-single-module` — ArchUnit is the only boundary, not the compiler |
| `clean-multimodule` | multi-module | Long-lived system, medium/large team | More ceremony; many mappings |
| `hexagonal` | multi-module | Multiple entry/exit channels | More modules than layered |
| `onion` | multi-module | Palermo's own terminology (Application Core, Gateway), persistence/presentation as independent peers | Domain module carries model + services + every Gateway — bigger compilation unit than hexagonal's or clean architecture's |
| `vertical-slice` | single-module | Loosely coupled features, fast delivery | Duplication between slices |
| `modular-monolith` | single-module | Multiple bounded contexts (Spring Modulith) | A second verification tool (`ApplicationModules.verify()`) on top of ArchUnit; module names are feature data, not pre-created at bootstrap |
| `custom` | your choice | You already have an in-house pattern | You have to describe and validate it |

Don't default to the fanciest architecture. The cost of over-engineering is paid every
day; the cost of under-engineering is paid once, at refactor time.

### Adding a blueprint

This is the test that proves the design is right: **adding an architecture must never
require touching any skill, agent, or command.**

```bash
cp .claude/blueprints/custom-template/custom.template.yaml .claude/blueprints/my-style.yaml
$EDITOR .claude/blueprints/my-style.yaml
```

Validation is the 5-rule checklist from `_schema.md`, run by the agent in step 2 of
the bootstrap. There's no external validator or anything to install; the build is the
final arbiter.

The YAML declares seven mandatory blocks (full contract in `blueprints/_schema.md`):

```yaml
id: my-style
name: "My architecture"
description: >
  One sentence about what characterizes it.
when_to_choose: ["Objective criterion 1"]
trade_offs:     ["Honest cost 1"]     # no trade-offs = poorly written blueprint

build:
  layout: multi-module                  # multi-module | single-module
  tool: maven                           # maven | gradle

modules:
  - id: core
    path: core
    depends_on: []
    forbidden_imports: ["org.springframework.."]   # generates local CLAUDE.md + hook
  - id: app
    path: app
    depends_on: [core]
    contains_main: true                 # exactly one module

packages:
  base: "{{groupId}}.{{artifactName}}"
  map: { core: "core", app: "app" }

dependency_rules:
  direction: inward
  forbidden:
    - from: core
      to: [app]
      reason: "The core doesn't know who uses it"

templates: { parent_pom: ..., module_pom: ..., module_docs: ... }
features:  { rest: true, persistence-jpa: false, ... }
```

Validation rules applied: `depends_on` graph is acyclic · exactly one
`contains_main` · every referenced `feature` exists · every template exists on disk.

The field that does the heavy lifting is `forbidden_imports`: it feeds both the
module's `CLAUDE.md` (so the model *knows*) and `ArchHook.java check` (so the build
*enforces* it). One declaration, two effects, with no chance of diverging.

---

## Metadata

The Claude Code runtime **silently ignores** any field it doesn't recognize — no
error, no warning. That's why the distinction between a native field and a convention
is operational, not cosmetic: an invented field never changes behavior.

### Skill (`skills/<name>/SKILL.md`) — native fields

| Field | Effect |
|---|---|
| `name` | Identifier; allows invocation with `/name` |
| `description` | **Sole trigger for automatic invocation.** Always in context |
| `allowed-tools` / `disallowed-tools` | Pre-approves or denies tools during the skill |
| `model` | `opus` \| `sonnet` \| `haiku` \| full id — overrides the session's |
| `context: fork` | Runs the skill in an isolated subagent |
| `agent` | Which subagent to use, with `context: fork` |
| `background` | Runs in background (requires `context: fork`) |
| `effort` | `low` \| `medium` \| `high` \| `xhigh` \| `max` |
| `maxTurns` | Turn limit |
| `skills` | Other skills to load inside this one |
| `hooks` | Hooks that become active from invocation onward |
| `disable-model-invocation` | Only the human can invoke |
| `user-invocable` | Only the model can invoke |

If the `description` doesn't contain the words you'd actually write in the request,
the skill never fires. "Persistence specialist" is useless; "use when mentioning
repository, JPA, `@Entity`, migration, Flyway, query, index" works.

The body of `SKILL.md` only enters context when the skill is invoked. The
`description` is there always — that's the permanent cost of every installed skill.

### Subagent (`agents/<name>.md`) — native fields

`name` and `description` are required. Optional: `tools`, `disallowedTools`, `model`
(also accepts `inherit`), `permissionMode`, `maxTurns`, `skills` (preloaded whole at
startup), `mcpServers`, `hooks`, `memory`, `background`, `effort`,
`isolation: worktree`, `color`, `initialPrompt`.

Model resolution: invocation parameter → frontmatter → `CLAUDE_CODE_SUBAGENT_MODEL` →
main session's model.

### Rule (`rules/<name>.md`) — native field `paths`

```yaml
---
paths:
  - "domain/**/*.java"
  - "application/**/*.java"
---
```

This is the mechanism that makes progressive disclosure work without relying on the
model remembering to read the norm: the rule **auto-loads** when files matching the
globs are touched, and stays out of context the rest of the time. A rule without
`paths` is still valid, but must be cited explicitly.

### CLAUDE.md

No frontmatter — it's plain markdown. Supports imports with `@path` (up to 4 levels
deep); recommended target under 200 lines. HTML comments are stripped from context.

### Slash commands

`.claude/commands/*.md` still works for compatibility, but has been **merged with
skills**: `/name` and `.claude/skills/name/SKILL.md` are equivalent, with the same
frontmatter. This repository has no `commands/` — `/init-project` and `/arch-doctor`
are skills with `disable-model-invocation: true`. Don't create new `commands/`: the
skill form gains a support folder, `paths`, and `context: fork`.

### Convention (ignored by the runtime)

`metadata.*` — `scope`, `owns`, `reads`, `handoff`, `version`, `input-contract`,
`output-contract` — doesn't exist natively and doesn't change behavior.

That's why it **left the frontmatter of skills and agents**. Ownership, dependencies,
handoff, and contracts now live in a `## Contrato` section in the body of each
`SKILL.md`/`agents/*.md`. The frontmatter field cost tokens on every invocation
without enforcing anything; in the body, the same information is de facto
instruction.

`enforced-by` and `status` survive in `rules/*.md`, alongside the native `paths`, as
review annotation.

Everything the model must obey goes in the file's **body**, never in the frontmatter.

## Anti-patterns

| Anti-pattern | Symptom | Fix |
|---|---|---|
| Bloated `CLAUDE.md` | >150 lines; the model ignores the end | Move to `rules/`, leave an index |
| Duplicated rule | Same norm in `rules/` and a `SKILL.md` | The skill cites it by path |
| Skill that never fires | You type the request and nothing happens | `description` missing the literal terms |
| Loose agent chain | The 3rd agent reinvents the 1st one's work | Identical structured contracts |
| Orphan templates | Global folder nobody reads | Move inside the skill |
| Fixed versions | Issue on day 30 of a public template | Resolve at runtime |
| Slow hook | Someone comments out the hook | `-o -q -pl <module> -am` |
| Hardcoded architecture in the agent | Adding "Onion" requires editing prompts | Declarative blueprint |

---

## Status and roadmap

Moved to [`roadmap.md`](roadmap.md): delivery phases plus a checklist of every major
feature and pattern, marked done or not.

**The skill declares preconditions and stops if a tool or exemplar is missing**,
instead of improvising. An agent missing a piece doesn't fail outright — it
approximates; and a silent approximation in a bootstrap only shows up three weeks
later, when two projects from the same blueprint end up with different POMs.

## Design sanity checks

Before publishing, three checks:

```bash
# 1. New architecture without touching prompts
cp .claude/blueprints/custom-template/custom.template.yaml .claude/blueprints/test.yaml
git status --porcelain .claude/skills .claude/agents .claude/commands   # must come back empty

# 2. Single rule — each norm in one file only
grep -rl "constructor injection" .claude/ | wc -l                       # must be 1

# 3. Clean machine — no global skills
env -u HOME claude   # /init-project must give a green build
```

## Cross-platform

The hooks are **a single Java file** (`.claude/hooks/ArchHook.java`) executed in
single-file source mode, invoked in **exec form**:

```json
{ "type": "command", "command": "java",
  "args": ["${CLAUDE_PROJECT_DIR}/.claude/hooks/ArchHook.java", "check"] }
```

Why this, and not a `.sh` with a `.ps1` twin:

- **No shell is invoked.** Exec form passes the arguments directly, which the
  documentation specifically recommends to avoid quoting and path issues on Windows.
  It no longer matters whether the machine has bash, Git Bash, WSL, or none of those.
- **One implementation, not two.** Keeping `.sh` and `.ps1` in parallel would violate
  the rule this repository applies to everything else — each norm has a single owner.
  Two copies of the same logic diverge; it's just a matter of time.
- **The dependency already exists.** The target audience has a JDK by definition.
  Requiring Python, Node, or PowerShell would add an ecosystem; requiring Java adds
  nothing.

The file handles the real differences itself: it picks `mvnw` or `mvnw.cmd` depending
on the OS (the same problem the Maven wrapper already solves), normalizes path
separators, and forces UTF-8 on stderr because Windows consoles use cp1252 and would
break accented characters.

Assumed cost: ~1s of JVM startup per invocation, in single-file source mode. In a hook
that's already waiting on a `test-compile`, it's not noticeable.

Verification on this machine:

```
/arch-doctor
```

In CI, `validate.yml` runs `ArchHook doctor` and a `BoundaryTest` that injects a
forbidden import and requires exit 2 — on `ubuntu-latest`, `macos-latest`, and
`windows-latest`. Without the OS matrix, "cross-platform" would be a claim; with it,
it's a fact verified on every push.

## CI (`validate.yml`)

Motivation: the invariants in `CLAUDE.md` and the rules under `rules/` are prose, not
code — nothing stops an edit from violating one silently. `ArchHook.java check` only
guards the Java boundary of a *generated* project; it never runs against this
repository's own prompt graph. `validate.yml` is what turns each invariant into a
deterministic, repo-wide check that runs on every push and PR, instead of a claim that
holds only as long as whoever writes the next skill remembers it.

| Job / step | Verifies | Guards against |
|---|---|---|
| `hooks-cross-platform` | `ArchHook.java doctor` and `BoundaryTest` on `ubuntu-latest`, `macos-latest`, `windows-latest` | Decision D8 — "cross-platform" as a fact, not a claim |
| `frontmatter schema` | `java .claude/hooks/ArchHook.java schema` | Invariant 10 — `extensions.json` is the single owner of recognized frontmatter; an invented field or `metadata:` fails loud instead of being silently ignored at runtime |
| `skill name doesn't shadow a native slash command` | skill folder names against a denylist (`doctor`, `init`, `context`, `memory`, …) | A skill silently replacing a native command instead of erroring |
| `new blueprint doesn't touch prompts` | adding a blueprint leaves `.claude/skills` and `.claude/agents` untouched | Invariant 7 — architectures are data |
| `rules is a leaf of the graph` | no rule mentions "skill", "agent", "subagent" | Invariant 1 |
| `norm contains no code boilerplate` | no `class`/`record`/`interface`/`enum` declaration inside `rules/` | Invariant 3 |
| `no new commands/` | `.claude/commands/` doesn't exist | Invariant 4 |
| `exemplar imports have the .example suffix at the end` | every file under `skills/*/templates/` | Precondition globs that key off the suffix |
| `blueprint-declared templates exist on disk` | every `templates.<role>` in a blueprint resolves to a real file | A blueprint pointing at a template that was renamed or deleted |
| `each norm has a single owner` | a fixed list of known terms appears in at most one file under `rules/` | Invariant 2 — narrow by construction, see note below |
| `no dependencies outside the Java ecosystem` | no `pip install`, `npm install`, `node `, bare `python` in `.claude/` | Decision D7 |
| `every norm paths matches some blueprint` / `norm is in the bootstrap's derivation table` | a rule's `paths` glob names a package some blueprint declares, and step 6.6 of `project-bootstrap` knows to rewrite it | Gap 8 of `decisions/0024-lessons-learned-001-remediation.md` — a rule that silently never auto-loads |
| `decisions/ doesn't grow paths or enter 00-index.md` | no file in `decisions/` declares `paths:`; none is listed in `rules/00-index.md` | `decisions/` is history, not a rule — see Known pitfalls |
| `no hardcoded Spring/Java version outside decisions/` | no `Spring Boot X.Y` / `Java NN` written as fact in `rules/`, `skills/`, `blueprints/`, `CLAUDE.md` | Invariant 8 — versions are resolved via Spring Initializr, never written from memory. Excludes the `JDK 21+` minimum-requirement line and dated "Tested to compile" notes in exemplars, which record a past verification, not a version to use |
| `exemplar-imports` job | every `import` in a `.java.example` resolves against JARs from a real `start.spring.io` request, and none uses a name denylisted as deprecated | Gaps 4, 5, 9 of `decisions/0024-lessons-learned-001-remediation.md` — an exemplar that "compiles in the head of whoever wrote it" |

Note on "each norm has a single owner": the check tests a fixed list of literal
phrases (`Constructor injection`, `Zero framework`, `RuntimeException`), not a general
duplication detector. It catches regressions of terms already known to have drifted
once; a new rule added without a new phrase in that list isn't covered.

Not yet in `validate.yml`, known gaps:

- Invariant 9 (the generated project is self-contained) is only checked manually, via
  the command block at `project-bootstrap/SKILL.md` §8 ("Verify") — it requires
  generating a real project against the Initializr and hasn't been automated into CI
  because of that cost.
- `claude plugin validate .claude/skills` — the CLI isn't installed on the GitHub
  Actions runner, and adding it would pull in a dependency outside `java`/`git`/`curl`
  (see § Dependencies). Run it by hand before opening a PR; it's a cheap,
  complementary check to the `schema` step above and catches malformed YAML.

## Contributing

Blueprints and norms are the most useful contributions. Before opening a PR:

- A new blueprint passes the 5-rule checklist from `_schema.md` and declares honest
  `trade_offs`. There's no external validator — nothing to install.
- A new norm lives in its own file under `rules/`, is listed in `00-index.md`, and is
  not copied into any skill.
- No PR fixes Java or Spring Boot versions.
- No PR adds dependencies outside JDK, Maven, git, and curl — `validate.yml` fails if
  it does.

## License

MIT.
