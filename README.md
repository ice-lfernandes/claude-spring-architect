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

**A generator of Spring Boot projects already prepared for AI-assisted development —
with a selectable architecture declared as data, boundaries enforced by hooks and by
the build, a spec-first feature pipeline, and a deterministic audit trail of what every
skill and agent run cost.**

Documentation: [`docs/en/`](docs/en/README.md) (English) · [`docs/pt-br/`](docs/pt-br/README.md)
(português). What sets this repository apart from similar ones, with the comparison:
[`docs/en/09-differentiators.md`](docs/en/09-differentiators.md).

Not affiliated with Anthropic. "Claude" in the name follows the ecosystem's naming
practice; this is not an official product.

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
- Records what every skill and agent run cost, chained, touched, and failed — as a
  hook, so the record exists even when the session dies halfway.

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
| Measurable | Every skill/agent invocation in a generated project leaves a report: tokens and cost per piece, chain, files touched, failures |
| Honest about itself | Eleven invariants of the `.claude/` design are gated in CI, not just written down |

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

## What makes it different

The closest projects on GitHub come in three shapes: a **static template** you clone
(one fixed architecture, a pinned Spring version, a `.claude/` inside — e.g.
`piomin/claude-ai-spring-boot`, `ryu-qqq/claude-spring-standards`), a **skills pack**
you copy into `.claude/skills/` (knowledge, no generation, no verification — e.g.
`rrezartprebreza/spring-boot-skills`), or an **agents + hooks bundle** (agents per role,
hooks that format or block dangerous commands — e.g.
`altmemy/claude-code-templates`). In all three the architecture is prose the model has
to remember, and nothing measures what a run cost.

This repository is a **generator with enforcement**:

| Capability | Here | Static template | Skills pack | Agents + hooks |
|---|---|---|---|---|
| Project generated via Spring Initializr, versions resolved live | ✅ | ❌ clone, pinned | ❌ | ❌ |
| Architecture selectable as data — 7 blueprints + custom, no prompt edited | ✅ | ❌ one, fixed | ❌ | ❌ |
| Boundary derived from the blueprint and blocked by a hook at write time | ✅ | ❌ | ❌ | ❌ generic hooks |
| Hook as one Java file, exec form, tested on Linux/macOS/Windows in CI | ✅ | ❌ | — | ❌ bash |
| Deterministic audit trail: cost per skill/agent, chain, files, failures | ✅ | ❌ | ❌ | ❌ |
| Spec-first pipeline; a design run can only write `docs/`, approved specs frozen by hook | ✅ | ❌ | ❌ | partial |
| The `.claude/`'s own design invariants gated in CI | ✅ | ❌ | ❌ | ❌ |
| Frontmatter schema that fails on a field the runtime would ignore silently | ✅ | ❌ | ❌ | ❌ |
| Every skill's body structure and write territory validated as data by a hook | ✅ | ❌ | ❌ | ❌ |
| Code exemplars compiled against a real Initializr classpath in CI | ✅ | ❌ | ❌ | ❌ |
| Generated project self-contained, generator not needed afterwards | ✅ | ✅ | — | ✅ |
| Project installs/updates its own `.claude/` from a manifest, with provenance | ✅ | ❌ merge by hand | partial | ❌ |

Full analysis, sources, and what is deliberately *not* a differentiator:
[`docs/en/09-differentiators.md`](docs/en/09-differentiators.md).

## Features

- **Architecture blueprints** — 7 out of the box (`hexagonal`, `clean-architecture-multi-module`,
  `clean-architecture-single-module`, `layered`, `onion`, `vertical-slice`,
  `modular-monolith`), extensible via a YAML file. Adding one never touches a prompt;
  CI proves it.
- **Generator, not template** — base from `start.spring.io` at run time, restructured
  per blueprint, zero business code, `README` + `GENESIS.md` written into the project.
- **Three levels of instruction** — global constitution (`CLAUDE.md` under 200 lines),
  per-module context, detailed norms loaded on demand via `paths` rewritten from the
  blueprint's `packages.map`.
- **Hook-based enforcement, one Java file, eleven modes** — every hook launches the
  committed, precompiled `ArchHook.jar` (CI checks it byte for byte against the source).
  Forbidden imports and incremental compile on post-edit, tests of changed modules on stop,
  schema validation (frontmatter, skill and agent bodies, `.mcp.json` with secret scan, hook
  registrations, the export manifest), the write-territory guard — on `Write`/`Edit`, on the
  shell write shapes it can read, and as an end-of-turn sweep of what git sees changed — force
  pushes refused in every spelling, a compose gate on stop, the design-pattern catalog handed
  to the executor at `SubagentStart`, audit trail, doctor, the deterministic `export` of a
  project's `.claude/`, and the reproducible `build` of the jar itself.
- **Every skill and agent has a class, and the class is data** — each skill sits in exactly
  one of six classes (`design`, `orchestrator`, `build`, `observer`, `meta`, `ops`) and each
  agent in one of three (`driver`, `executor`, `installer`) in `schemas/extensions.json`. The
  class declares the sections the body must carry, the paths it may write, and — for a skill
  — the `model` it may pin; for an agent, whether it may write at all. `schema` enforces the
  structure, `guard` the territory: a write outside it is `exit 2`, deny by default. A piece
  in no class fails validation by name.
- **Boundaries derived from the blueprint** — one `forbidden_imports` declaration feeds
  the module's `CLAUDE.md`, `.claude/forbidden-imports.txt`, and the POM graph; ArchUnit
  and a JaCoCo gate (80% lines / 70% branches) are installed by an agent when code
  exists.
- **Versions resolved at runtime** — no Spring Boot or Java version hardcoded; CI fails
  if one is written as a fact.
- **Spec-first pipeline** — `/new-feature` designs one use case per run across seven
  owner skills (messaging and jobs only when the case needs them), each deciding the design
  patterns of its own layer so the approved spec already shows the classes a pattern creates.
  It consolidates a `UC-NNN-spec.md` (`draft → approved → implemented`, or
  `implemented-blocked` when the case is not yet reachable end to end), asks approval, then
  hands the spec to a restricted executor agent.
- **Execution audit trail** — every skill/agent invocation in the generated project
  leaves a Markdown report and two ledgers; `/audit-usage` consolidates spend across
  runs. Prompts are redacted before landing in git.
- **Cross-cutting concerns solved once** — `Idempotency-Key` via AOP, logging with
  sensitive-data masking, OTLP collector with Jaeger or Grafana + Tempo + Prometheus,
  Kafka producer/consumer with retry/DLQ, compose port-collision diagnosis.
- **Git offer, behind two confirmations** — after a green bootstrap build or a
  successful feature implementation, `git-publish` offers to `git init`/commit and
  `gh repo create`+push. `git push` is always `ask`, and the guard refuses a force push in
  every spelling it can read (`-f`, `--force-with-lease`, `+ref`, `git -C … push`, `sh -c`).
- **Adoption and update of the AI layer** — `/arch-adopt` installs this `.claude/` into a
  project that was never generated here, or pulls a newer version into one that is behind.
  It refuses a dirty worktree, writes through the deterministic `export` mode (a data
  manifest, not a prose copy table), and stamps `.claude/.arch-provenance.json` so
  `/arch-doctor` can name every locally edited file *before* the next update overwrites it.
  The only creation skill that travels into the generated project — that is how a project
  updates itself once the plugin that delivered it is gone.

---

## Structure

```
claude-spring-architect/
├── CLAUDE.md                      # facts about this meta-repo (not the generated project's)
├── claude-help.md                 # how each piece of Claude Code works — the runtime reference the docs cite
├── roadmap.md                     # phases and feature checklist
├── docs/  ·  docs/en/             # how the pieces collaborate: pt-BR and English mirrors
├── .github/workflows/validate.yml # CI: OS matrix for the hook + design invariants + exemplar imports
└── .claude/
    ├── settings.json              # hooks + permissions (versioned)
    ├── settings.local.json        # (gitignored) personal overrides
    ├── schemas/extensions.json    # single owner of: recognized frontmatter, skill classes and their
    │                               #   write territory, .mcp.json fields, hook registrations,
    │                               #   audit/guard config, and the export manifest
    ├── rules/                     # norms, on-demand (leaves of the graph)
    │   ├── 00-index.md            #   map of norm → file → paths → who verifies
    │   ├── architecture-ddd.md    #   paths come from the blueprint at generation time
    │   ├── naming.md · code-quality.md · error-handling.md · api-rest.md
    │   ├── lombok.md · value-objects.md · persistence.md · testing.md
    │   └── observability.md · logging.md · messaging.md · scheduling.md
    ├── blueprints/                # architectures as data
    │   ├── _schema.md             #   contract every blueprint fulfills
    │   ├── README.md              #   pros, cons, when to choose each
    │   ├── hexagonal/hexagonal.yaml
    │   ├── clean-architecture-multi-module/clean-architecture-multi-module.yaml
    │   ├── clean-architecture-single-module/clean-architecture-single-module.yaml
    │   ├── layered/layered.yaml
    │   ├── modular-monolith/modular-monolith.yaml
    │   ├── onion/onion.yaml
    │   ├── vertical-slice/vertical-slice.yaml
    │   └── custom-template/custom.template.yaml
    ├── skills/                    # procedure + exemplars
    │   ├── init-project/SKILL.md              #   /init-project — stays in this repo
    │   ├── project-bootstrap/                  #   stays in this repo: builds, doesn't maintain
    │   │   ├── SKILL.md
    │   │   ├── references/        #   blueprint selection, dependency catalog
    │   │   └── templates/         #   real, compilable exemplars (*.example): POMs, CLAUDE.md, settings.json, CI, Docker…
    │   ├── claude-code-architect-designer/     #   decides skill vs agent vs rule vs MCP — stays in this repo
    │   ├── arch-adopt/            #   /arch-adopt — installs/updates this .claude/ in a project — copied
    │   ├── arch-doctor/           #   /arch-doctor — copied into the generated project
    │   ├── use-case-design/       #   pipeline 1 — copied
    │   ├── domain-modeling/       #   pipeline 2 — copied
    │   ├── rest-api-architect/    #   pipeline 3 — copied
    │   ├── messaging-architect/   #   pipeline 3b, conditional — Kafka producer/consumer — copied
    │   ├── jobs-architect/        #   pipeline 3c, conditional — scheduling tech, cadence, outbox relay's own schedule — copied
    │   ├── persistence-architect/ #   pipeline 4 — copied
    │   ├── test-architect/        #   pipeline 5 — copied; owns the ArchUnit exemplar
    │   ├── new-feature/           #   /new-feature — orchestrates the seven above — copied
    │   ├── gof-design-patterns/   #   pattern catalog: read at design time by the pipeline skills,
    │   │                          #   injected into the executor at SubagentStart,
    │   │                          #   /gof-design-patterns on existing code — copied
    │   ├── docker-architect/      #   services, OTLP collector, Jaeger / Grafana stack — copied
    │   ├── git-publish/           #   git init/commit + gh create/push, two confirmation gates — copied
    │   ├── sonarqube-setup/       #   scanner + server URL, CI step for an external server, local container otherwise — copied
    │   ├── sonar-lessons/         #   /sonar-lessons — Sonar findings traced to .claude/, issue filed here — copied
    │   ├── report-issue/          #   /report-issue — the one door from a project to this repo's issues — copied
    │   ├── triage-issue/          #   /triage-issue <N> — every claim checked at HEAD by issue-verifier — stays in this repo
    │   └── audit-usage/           #   /audit-usage — reads the execution trail — copied
    ├── agents/                    # isolated context
    │   ├── project-initializer.md         #   drives /init-project — stays in this repo
    │   ├── issue-verifier.md              #   /triage-issue's read-only checker, opus — stays in this repo
    │   ├── java-spring-boot-developer.md  #   /new-feature's executor — copied
    │   ├── archunit-installer.md          #   test-architect's setup mode — copied
    │   └── commons-logging-installer.md   #   logging/masking aspects, /new-feature's pre-flight — copied
    ├── hooks/
    │   ├── ArchHook.java          # enforcement, one file, eleven modes
    │   └── ArchHook.jar           # what every hook runs — committed, rebuilt by `build`
    ├── decisions/                 # why each piece has the form it has — history, not norms
    ├── lessons-learned/           # what real runs broke — history, not norms
    └── .ci/                       # CI tests, each one runs the jar
        ├── BoundaryTest.java        # a forbidden import, requires exit 2
        ├── InjectionPathTest.java   # a cwd-relative `!`…``, requires exit 2
        ├── ComposeTagTest.java      # compose image tag vs. the one src/test pins
        ├── ComposeGateTest.java     # a published service no host client can reach blocks Stop
        ├── SkillTerritoryTest.java  # a skill writing outside its class's territory, requires exit 2
        ├── AgentTerritoryTest.java  # the same for each agent class
        ├── BashGuardTest.java       # force pushes and shell writes outside the phase
        ├── SweepTest.java           # the end-of-turn sweep names this turn's writes only
        ├── SubagentContextTest.java # the pattern catalog reaches pattern_catalog agents only
        └── AuditRenderTest.java     # the audit report renders, redacts, skips observers
```

There is no `commands/`: slash commands live as skills with
`disable-model-invocation: true`. `forbidden-imports.txt` is not here — it is
generated inside the project during `/init-project`. The decision records
(`.claude/decisions/`) and the lessons learned from real runs
(`.claude/lessons-learned/`) are versioned, but they are history, not norms: no `paths`,
outside `rules/00-index.md`, never copied into a generated project, and `CLAUDE.md` cites
them only as the "why" behind a piece.

**The generated project is self-contained.** Whoever clones it does not need this
repository: `/init-project` copies over the norms (`rules/*.md`), the development
skills (`arch-adopt`, `arch-doctor`, `use-case-design`, `domain-modeling`,
`rest-api-architect`, `persistence-architect`, `messaging-architect`, `jobs-architect`,
`test-architect`, `new-feature`, `gof-design-patterns`, `docker-architect`, `git-publish`,
`sonarqube-setup`, `sonar-lessons`, `report-issue`, `audit-usage`), the
executor agents (`java-spring-boot-developer`, `archunit-installer`,
`commons-logging-installer`), `ArchHook.java`, and `schemas/extensions.json` — and what
travels is **data**, the `export` block of `schemas/extensions.json`, checked against disk
by `ArchHook.java schema`. Only `project-bootstrap`, `init-project`,
`claude-code-architect-designer`, `triage-issue`, `project-initializer`, `issue-verifier`, and
the blueprint **catalog** are left out — they serve before the project exists, or only here; the
**active** blueprint does travel, because the next update has to resolve the id the provenance stamp records. `arch-adopt` is
the one creation skill that travels: it is how the project pulls a newer `.claude/`
afterwards. The project also
gets its own `README.md`, `README.pt-br.md`, and `.claude/audit-usage/GENESIS.md`
recording the run that created it.

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
| "This skill may only write under `docs/use-cases/`" | `schemas/extensions.json` → `skill_classes` | Territory is data one hook reads, not a promise in prose |
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
git clone --depth 1 https://github.com/ice-lfernandes/claude-spring-architect /tmp/csa
cp -r /tmp/csa/.claude .

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

**Already have a project?** Step 2 above is the manual route, and it is also the one you
never have to repeat: from inside the session, `/arch-adopt` installs this `.claude/` into a
project that was never generated here, and later pulls a newer version into it — refusing a
dirty worktree, writing through the deterministic `export` mode, and leaving the diff ready
for `git diff` / `git checkout`. It stamps `.claude/.arch-provenance.json`, so the next
update can tell you which files you edited by hand before it overwrites them. See
[`docs/en/10-arch-adopt.md`](docs/en/10-arch-adopt.md).

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
     ▼  agent: project-initializer (isolated context, model: sonnet)
     │
     ├─ 1. INTERVIEW — blueprint · coordinates · build · features
     ├─ 2. VALIDATES blueprint — 6-rule checklist            [fails fast]
     ├─ 3. BASE via Spring Initializr (curl)                 [GA versions, nothing hardcoded]
     ├─ 4. RESTRUCTURES into modules per the blueprint       [exemplars give the shape]
     ├─ 5. GENERATES forbidden-imports.txt + root and <module>/CLAUDE.md, CI, Checkstyle,
     │      lombok.config, logback, Dockerfile + docker-compose
     ├─ 6. COPIES rules, development skills, executor agents — the project is self-contained
     ├─ 7. INSTALLS hooks (ArchHook.java + .jar + extensions.json + settings.json), opens the audit trail
     ├─ 8. VERIFIES build + smoke + tested boundary block; writes README + GENESIS.md
     ├─ 9. REPORT in the fixed output contract
     └─ if the build is green: OFFERS git-publish (Skill tool) — two independent
        confirmations, never runs unattended
```

### `/new-feature` — end-to-end feature

```
/new-feature <feature description>     (or UC-NNN-slug to resume a draft, or empty to list)
     │
     ▼  skill: new-feature — closed input table; anything else is an error
     │
     use-case-design → domain-modeling → rest-api-architect
        → messaging-architect (conditional) → jobs-architect (conditional)
        → persistence-architect → test-architect
     │  one use case per run; a split goes to docs/use-cases/BACKLOG.md
     │  design skills write only their class's territory — docs/ only, never src/,
     │  never docker-compose.yml, never git; a build-class skill can't even be called
     │  each one decides the design patterns of its own layer, in its partial
     │
     ▼  consolidates into UC-NNN-spec.md (status: draft)
     │
     ▼  asks approval → status: approved (immutable from here — the guard hook freezes the folder)
     │
     ├─ implement now → pre-flight, once per project: ArchUnit (test-architect setup mode)
     │                   and logging/masking aspects (commons-logging-installer), each
     │                   behind a question, in separate turns
     │                → agent: java-spring-boot-developer, pattern catalog injected at start
     │                  → status: implemented (or implemented-blocked)
     │                → git-publish (feature commit)
     └─ not now       → git-publish (docs of the approved spec only)
                         both behind git-publish's two confirmations
```

Each step receives the structured output of the previous one. Free-prose handoff
degrades by the third hop; that's why one step's `output-contract` is literally the
next one's `input-contract`.

### Edit cycle (what happens on every write in a generated project)

```
prompt (UserPromptSubmit)
     └─ ArchHook guard prompt  baseline of the working tree for this turn's sweep

Write/Edit/MultiEdit
     │
     ├─ ArchHook guard    path outside the active skill's or agent's class territory → blocked
     │                    (PreToolUse, exit 2 — allowlist, deny by default)
     │                    spec folder approved/implemented → blocked
     │                    Skill(build-class) during a design run → blocked (guard call)
     ├─ ArchHook schema   frontmatter + body of .claude/**/*.md, .mcp.json    (PreToolUse, exit 2)
     ├─ ArchHook format   spotless on the touched module                      (never blocks)
     ├─ ArchHook check    forbidden imports + incremental compilation         (blocks: exit 2)
     │                    └─ reads .claude/forbidden-imports.txt
     └─ ArchHook audit    file touched → appended to the run's log            (never blocks)

Bash
     └─ ArchHook guard bash    force push → refused; a redirect, `sed -i`, `tee`… writing
                               outside the territory or into a frozen folder → blocked

subagent start (SubagentStart)
     └─ ArchHook context subagent   pattern catalog → agents that declare pattern_catalog

end of task (Stop)
     ├─ ArchHook guard sweep   what git sees changed this turn, re-checked     (blocks: exit 2)
     ├─ ArchHook compose gate  a service down, or published and unreachable    (blocks: exit 2)
     ├─ ArchHook audit flush   renders the run's report + ledger line         (never blocks)
     ├─ ArchHook schema
     └─ ArchHook tests    tests of the changed modules                        (blocks: exit 2)
                          └─ respects stop_hook_active, doesn't loop
```

In this meta-repo `schema`, `format`, `check`, `tests`, `guard` (all five sub-modes) and
`compose gate` are wired, plus `build` on every edit of `ArchHook.java` — `guard` because the
`meta` class has a territory here too (`.claude/**`, `CLAUDE.md`, `.mcp.json`, `docs/**`,
`.github/**`). `audit` stays off: there is no `.claude/audit-usage/`, and auditing the design
of the tool instead of its use is not the trail anyone wants. `context subagent` stays off
too: the executor never runs here.

### Audit trail — what every run cost

In a generated project, every invocation of a project skill or agent — typed as
`/command` or chained by the model — leaves `.claude/audit-usage/<timestamp>--<piece>.md`.
Excerpt of a real one, from a `/new-feature` run in the demo project:

```text
| ⏱️ Duration        | 2h55m35s  |   ⏸️ Waiting for the user | 2h44m33s |   ⚙️ Active duration | 11m01s |

/new-feature                                  ████████████████████ 11m01s   100%
├─ 📘 use-case-design                         ████░░░░░░░░░░░░░░░░ 2m17s    21%
├─ 📘 domain-modeling (UC-002)                ██░░░░░░░░░░░░░░░░░░ 1m15s    11%
├─ 📘 rest-api-architect (UC-002)             ███░░░░░░░░░░░░░░░░░ 1m43s    16%
├─ 📘 persistence-architect (UC-002)          ██░░░░░░░░░░░░░░░░░░ 1m06s    10%
├─ 📘 test-architect (UC-002)                 ████░░░░░░░░░░░░░░░░ 2m25s    22%
└─ 🤖 java-spring-boot-developer              ██░░░░░░░░░░░░░░░░░░ 1m12s    11%

| Piece                         | Own billable      |     | 🧮 billable (run)  | 530,831 |
| 📘 test-architect             |           242,813 |     | ♻️ cache read      | 6,849,774 |
| 🤖 java-spring-boot-developer |            88,517 |     | cache hit          | 100% |
```

Plus where the run spent (tool calls per piece, the costliest turns, the peak context,
the first line of each tool error, redacted), files touched, permissions requested, and
the rules that should have loaded. `/audit-usage` aggregates the ledgers across runs (spend per piece without
double counting, failure rate, which report to open). The trail is a hook, not a skill:
it survives the model forgetting and the session dying, and costs zero tokens to
produce. Prompts are redacted before landing in git; prices are `null` until you fill
`pricing.json`. Details: [`docs/en/08-audit-usage.md`](docs/en/08-audit-usage.md).

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

Validation is the 6-rule checklist from `_schema.md`, run by the agent in step 2 of
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
`contains_main` · every referenced `feature` exists · every template exists on disk ·
`single-module` has exactly one module · `architecture_paths` is non-empty.

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
handoff, and contracts now live in a `## Contract` section in the body of each
`SKILL.md`/`agents/*.md`. The frontmatter field cost tokens on every invocation
without enforcing anything; in the body, the same information is de facto
instruction.

`enforced-by` and `status` survive in `rules/*.md`, alongside the native `paths`, as
review annotation.

Everything the model must obey goes in the file's **body**, never in the frontmatter. The
skill's **class** follows the same rule and for the same reason: it is a `**Class:** <c>`
line in the `## Contract` section, cross-checked by `ArchHook.java schema` against
`schemas/extensions.json` — not a frontmatter field, which the runtime would ignore
without a word.

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
| Skill with no class | `schema` fails by its name; without a class it would have no write territory | Add it to a `skill_classes` class and state `**Class:** <c>` in the body |
| Territory promised in prose | A `## Contract` says "writes only under `docs/`" and a run writes `docker-compose.yml` | Territory is `write_allow` data the `guard` hook enforces |

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

The hooks are **a single Java file** (`.claude/hooks/ArchHook.java`), compiled into the
committed `.claude/hooks/ArchHook.jar` and invoked in **exec form**:

```json
{ "type": "command", "command": "java",
  "args": ["-jar", "${CLAUDE_PROJECT_DIR}/.claude/hooks/ArchHook.jar", "check"] }
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

The same file carries every mode, so there is one place to read and one to test:

| Mode | Event | Blocks? | What it does |
|---|---|---|---|
| `check` | `PostToolUse` Write\|Edit\|MultiEdit\|NotebookEdit, `if: Edit(**/*.java)` | yes | forbidden imports + incremental `test-compile` of the touched module |
| `format` | same | no | `spotless:apply` on the module |
| `tests` | `Stop` | yes | tests of the modules changed since `HEAD` |
| `schema` | `PreToolUse` / `PostToolUse` / `Stop` | yes | frontmatter **and body** of skills, agents, rules; `.mcp.json` with a secret scan; hook registrations; the export manifest — all against `schemas/extensions.json` |
| `guard` | `UserPromptSubmit` (`prompt`), `PreToolUse` (`write`, `bash`, `call`), `Stop` (`sweep`) | yes | every skill and agent writes only its class's territory (allowlist, deny by default) — through the file tools, through the shell write shapes it can read, and re-checked at the end of the turn against what git sees changed; a `build`-class skill is unreachable during a design run; approved specs are frozen; force pushes refused |
| `compose` | `Stop` (`gate`); manual; folded into `doctor` | `gate` only | every compose service `running`, no foreign container on this project's ports, every published port advertised at a host-resolvable address, no `image:` tag disagreeing with the one `src/test` pins |
| `context` | `SubagentStart` (generated project only) | no | hands the design-pattern catalog to each agent whose class declares `pattern_catalog` |
| `audit` | eleven lifecycle events | no | execution trail of every skill and agent (generated project only) |
| `doctor` | manual (`/arch-doctor`) | no | diagnoses the setup on this machine |
| `export` | manual (invoked by `/arch-adopt`) | no | writes a target project's `.claude/` from the `export` manifest, transformed for the active blueprint, plus the provenance stamp; deletes what `export.retired` names |
| `build` | `PostToolUse` on `ArchHook.java` (this repo only); manual | no | rebuilds `ArchHook.jar` under the JDK major `hook_build.javac_feature` pins; `--verify` compares byte for byte |

Every list the hook reads — recognized fields, skill and agent classes and their write
territory, audited skills' exclusions, redaction patterns, the export manifest — is data in
`schemas/extensions.json`, never a constant in the Java. A new skill is audited, its body
validated and its territory enforced without touching the hook: it is one entry in a class.

Cost: ~0.3 s of JVM startup per invocation from the jar, against ~3.3 s when every hook
recompiled the source (`.claude/decisions/0075-precompiled-hook-jar.md`). Editing
`ArchHook.java` changes nothing a hook runs until `build` rewrites the jar.

Verification on this machine:

```
/arch-doctor
```

In CI, `validate.yml` runs `ArchHook doctor`, `build --verify`, and the ten tests under
`.claude/.ci/` — every one spawning `java -jar .claude/hooks/ArchHook.jar`, the exact
command the registrations run — on `ubuntu-latest`, `macos-latest`, and `windows-latest`.
Without the OS matrix, "cross-platform" would be a claim; with it, it's a fact verified on
every push.

## CI (`validate.yml`)

Motivation: the invariants in `CLAUDE.md` and the rules under `rules/` are prose, not
code — nothing stops an edit from violating one silently. `ArchHook.java check` only
guards the Java boundary of a *generated* project; it never runs against this
repository's own prompt graph. `validate.yml` is what turns each invariant into a
deterministic, repo-wide check that runs on every push and PR, instead of a claim that
holds only as long as whoever writes the next skill remembers it.

| Job / step | Verifies | Guards against |
|---|---|---|
| `hooks-cross-platform` | `ArchHook.java doctor`, `build --verify`, and the ten `.claude/.ci/*Test.java` on `ubuntu-latest`, `macos-latest`, `windows-latest`, each running the jar | Decision D8 — "cross-platform" as a fact, not a claim |
| `committed ArchHook.jar is what the source compiles to` | `build --verify` recompiles under the pinned `javac` and compares byte for byte | Decision 0075 — an edit to the source without a rebuild changes nothing any hook runs |
| `guard keeps each skill inside its class's territory` · `… each agent …` | `SkillTerritoryTest.java` and `AgentTerritoryTest.java` drive the `guard` mode over the real `extensions.json`: inside and outside each `write_allow`, `agent_type` winning over the caller's phase, the refusal of a `build`-class `Skill` call during a design run | "Deny by default" rotting silently — it holds until one `write_allow` entry is widened by accident. The case that motivated it (a design run writing `docker-compose.yml`, a file no denylist named) is one of the cases |
| `guard bash …` · `guard sweep …` | `BashGuardTest.java`: every force-push spelling refused, a shell write outside the phase blocked; `SweepTest.java`: the end-of-turn sweep names this turn's writes and never pre-existing dirt | Decisions 0063, 0065, 0076 — enforcement that only matched tool names |
| `compose gate …` · `context subagent …` · `audit renders …` | `ComposeGateTest.java`, `SubagentContextTest.java`, `AuditRenderTest.java` | Decisions 0064, 0077, 0085 — a gate, an injection and a report that fail in silence |
| `hook reports a compose image tag that disagrees with src/test` | `ComposeTagTest.java` builds a throwaway project holding a `docker-compose.yml` and a `DockerImageName.parse(...)` under `src/test`, and requires `ArchHook.java compose` to report the divergence, expand `${VAR:-default}`, and stay quiet when the tags agree | The suite passing against an engine version nobody runs. Runs on all three OSes because that comparison reads two files and needs no Docker |
| `frontmatter schema` | `java .claude/hooks/ArchHook.java schema` | Invariant 10 — `extensions.json` is the single owner of recognized frontmatter; an invented field or `metadata:` fails loud instead of being silently ignored at runtime. Same mode also requires every `` !`command` `` injection to resolve paths from `${CLAUDE_PROJECT_DIR}` — a relative one reports a file as absent whenever the shell's cwd has drifted |
| `skill body matches its class` | the same `schema` mode: every `SKILL.md` on disk sits in exactly one `skill_classes` class, declares `**Class:** <c>` in its body, and carries the sections that class requires | Structure by review. Nine skills said `## Contract`, one said `## Skill contract`, two had none at all — and `claude plugin validate` printed `✔ Validation passed` over all of it |
| `skill name doesn't shadow a native slash command` | inside the `schema` step: skill folder names against a denylist (`doctor`, `init`, `context`, `memory`, …) | A skill silently replacing a native command instead of erroring |
| `new blueprint doesn't touch prompts` | adding a blueprint leaves `.claude/skills` and `.claude/agents` untouched | Invariant 7 — architectures are data |
| `rules is a leaf of the graph` | no rule mentions "skill", "agent", "subagent" | Invariant 1 |
| `norm contains no code boilerplate` | no `class`/`record`/`interface`/`enum` declaration inside `rules/` | Invariant 3 |
| `no new commands/` | `.claude/commands/` doesn't exist | Invariant 4 |
| `exemplar imports have the .example suffix at the end` | every file under `skills/*/templates/` | Precondition globs that key off the suffix |
| `blueprint-declared templates exist on disk` | every `templates.<role>` in a blueprint resolves to a real file | A blueprint pointing at a template that was renamed or deleted |
| `each norm has a single owner` | a fixed list of known terms appears in at most one file under `rules/` | Invariant 2 — narrow by construction, see note below |
| `no dependencies outside the Java ecosystem` | no `pip install`, `npm install`, `node `, bare `python` in `.claude/` | Decision D7 |
| `every norm paths matches some blueprint` / `norm is in the derivation table` | a rule's `paths` glob names a package some blueprint declares, and `export.derived_paths` knows to rewrite it | Gap 8 of `decisions/0024-lessons-learned-001-remediation.md` — a rule that silently never auto-loads |
| `the export manifest matches disk` | every file the `export` block names exists; every skill and agent is in `include` **or** `exclude`; every rule with a package territory has a `derived_paths` entry | Invariant 9 — that manifest **is** the copy list that makes the generated project self-contained. A new norm missing from it breaks nothing at generation time: it breaks for whoever clones the project later and follows a citation to a file that was never copied |
| `decisions/ doesn't grow paths or enter 00-index.md` | no file in `decisions/` declares `paths:`; none is listed in `rules/00-index.md` | `decisions/` is history, not a rule — see [`docs/en/11-pitfalls.md`](docs/en/11-pitfalls.md) |
| `no hardcoded Spring/Java version outside decisions/` | no `Spring Boot X.Y` / `Java NN` written as fact in `rules/`, `skills/`, `blueprints/`, `CLAUDE.md` | Invariant 8 — versions are resolved via Spring Initializr, never written from memory. Excludes the `JDK 21+` minimum-requirement line and dated "Tested to compile" notes in exemplars, which record a past verification, not a version to use |
| `export-determinism` job | two exports per blueprint produce identical trees; the tree carries nothing only this repo needs; the exported jar runs `schema` and `doctor` against its own tree; a re-export deletes what `export.retired` names | Invariant 9 — a generated project that differs between two runs, or cites a file it never received |
| `exemplar-imports` job | every `import` in a `.java.example` resolves against JARs from a real `start.spring.io` request, and none uses a name denylisted as deprecated | Gaps 4, 5, 9 of `decisions/0024-lessons-learned-001-remediation.md` — an exemplar that "compiles in the head of whoever wrote it" |

Note on "each norm has a single owner": the check tests a fixed list of literal
phrases (`Constructor injection`, `Zero framework`, `RuntimeException`), not a general
duplication detector. It catches regressions of terms already known to have drifted
once; a new rule added without a new phrase in that list isn't covered.

Beyond the invariants, the workflow declares `permissions: contents: read` (no job
writes), a `concurrency` group with `cancel-in-progress` (`push: [main]` and
`pull_request` fire on the same head), `timeout-minutes` on every job (the default is
6h), and every action pinned by commit SHA with its tag as a comment. The `design` and
`exemplar-imports` jobs set `defaults.run.shell: bash` for `pipefail`, which the default
`bash -e {0}` lacks — per job, since the OS matrix has a pwsh leg.

A second workflow, `release.yml`, requires every PR to tick exactly one bump level
(`major`, `minor`, `patch`) and, once merged into `main`, runs `schema` and `doctor` on the
merge commit before tagging it. Every step of both workflows, one by one:
[`docs/en/07-ci-validate.md`](docs/en/07-ci-validate.md).

Not yet in `validate.yml`, known gaps:

- Invariant 9 (the generated project is self-contained) is **half** covered: the copy-list
  step above proves the `export` manifest covers the disk, and `export-determinism` proves
  the exported `.claude/` validates on its own. That a generated project actually compiles
  and holds no dead path is still checked by hand, via the command block at `project-bootstrap/SKILL.md` §8 ("Verify") —
  it requires generating a real project against the Initializr and hasn't been automated
  into CI because of that cost.
- `claude plugin validate .claude/skills` — the CLI isn't installed on the GitHub
  Actions runner, and adding it would pull in a dependency outside `java`/`git`/`curl`
  (see § Dependencies). Run it by hand before opening a PR; it's a cheap,
  complementary check to the `schema` step above and catches malformed YAML.

## Documentation

| Read | To learn |
|---|---|
| [`docs/en/09-differentiators.md`](docs/en/09-differentiators.md) | What this repository does that similar ones don't, with sources and the honest non-differentiators |
| [`docs/en/00-overview.md`](docs/en/00-overview.md) | The full graph of skills, agents, rules, hooks, and who calls whom |
| [`docs/en/01-file-types.md`](docs/en/01-file-types.md) | How each piece behaves in the Claude Code runtime |
| [`docs/en/02-init-project.md`](docs/en/02-init-project.md) · [`03-new-feature.md`](docs/en/03-new-feature.md) · [`04-arch-doctor.md`](docs/en/04-arch-doctor.md) | The three main commands, step by step, with example output |
| [`docs/en/05-blueprints.md`](docs/en/05-blueprints.md) · [`.claude/blueprints/README.md`](.claude/blueprints/README.md) | The blueprint contract, and pros/cons of each architecture |
| [`docs/en/08-audit-usage.md`](docs/en/08-audit-usage.md) | The audit trail, the guard hook (skill classes and write territory), and `/audit-usage` |
| [`docs/en/10-arch-adopt.md`](docs/en/10-arch-adopt.md) | `/arch-adopt`: installing or updating this `.claude/` in an existing project, the `export` manifest, the provenance stamp |
| [`docs/en/06-claude-code-architect-designer.md`](docs/en/06-claude-code-architect-designer.md) | The eight forms an extension can take, the decision matrix, and how a new piece propagates |
| [`docs/en/07-ci-validate.md`](docs/en/07-ci-validate.md) | What CI verifies and what it still doesn't |
| [`docs/en/11-pitfalls.md`](docs/en/11-pitfalls.md) | Every silent trap — the runtime's (a skill, hook, injection or MCP server that does nothing without an error) and this repository's (write territories, frozen spec folders, who owns `pom.xml` and the outbox) |
| [`claude-help.md`](claude-help.md) | The Claude Code runtime reference every doc above cites |

Portuguese versions of every document live under [`docs/pt-br/`](docs/pt-br/README.md).

## Contributing

Blueprints and norms are the most useful contributions. Full guide, per file type:
[`CONTRIBUTING.md`](CONTRIBUTING.md). The short version — before opening a PR:

- A new blueprint passes the 6-rule checklist from `_schema.md` and declares honest
  `trade_offs`. There's no external validator — nothing to install.
- A new norm lives in its own file under `rules/`, is listed in `00-index.md`, and is
  not copied into any skill.
- No PR fixes Java or Spring Boot versions.
- No PR adds dependencies outside JDK, Maven, git, and curl — `validate.yml` fails if
  it does.
- `java .claude/hooks/ArchHook.java doctor` and `schema` both pass locally.

Bugs and proposals have forms:
[new issue](https://github.com/ice-lfernandes/claude-spring-architect/issues/new/choose).

## License

MIT.
