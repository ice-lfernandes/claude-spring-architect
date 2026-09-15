# Claude Spring Architect

[![CI](https://github.com/ice-lfernandes/claude-spring-architect/actions/workflows/validate.yml/badge.svg)](https://github.com/ice-lfernandes/claude-spring-architect/actions/workflows/validate.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-orange.svg)](#dependencies)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-GA%20latest-6DB33F.svg?logo=spring)](https://start.spring.io)
[![Built with Claude Code](https://img.shields.io/badge/Built%20with-Claude%20Code-D97757.svg)](https://claude.com/claude-code)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](#contributing)

**A `.claude/` layer that generates Spring Boot projects already prepared for
AI-assisted development — architecture picked from a declarative blueprint, boundaries
enforced by a hook instead of by prose, and every rule written exactly once.**

Copy `.claude/` into an empty repository, run `/init-project`, and you get a compiling
Spring Boot project whose architecture the tooling can actually enforce — plus the
skills to keep building features inside it, with no dependency back on this repository.

---

## Motivation

### The problem

Anyone using code assistants on Java projects knows the pattern: you start with a
30-line `CLAUDE.md`, six months later it's 400, the model ignores half of it, and the
same rules show up duplicated across three different skills — which have since
diverged. The AI "forgets" the architecture because nobody can tell it, cheaply and
reliably, what is actually true in this project.

Worse, none of it is enforced. An instruction file is a *request*. The model follows it
until context gets long, and when it doesn't, nothing fails — the violation lands in a
green build and surfaces three weeks later.

And every new project starts from zero: the same conventions written again, the same
structural decisions made again, nothing learned from the previous project surviving.

### The solution

Treat the AI files as production code: **explicit architecture, dependencies in one
direction, one source of truth per rule, and automatic enforcement.**

Three consequences, which are the whole design:

1. **Architectures are data.** Hexagonal, Clean, Onion, Layered, Vertical Slice,
   Modular Monolith — each one is a YAML file. Adding your own touches no prompt.
2. **What must always hold is a hook, not a paragraph.** `ArchHook.java` blocks a
   forbidden import at the moment of the edit, with exit 2. The model's memory is not
   part of the control loop.
3. **This repository applies its own rules to itself, under CI.** Its invariants are
   not documented good intentions; `validate.yml` fails the build when one breaks.

---

## What makes this different

Most Claude/Cursor template repos are a curated pile of prompts. The difference here is
that the instruction layer has an architecture, and that architecture is verified.

| | Typical AI template repo | This repo |
|---|---|---|
| **Adding an architecture** | Edit the prompts that mention it | Drop a YAML in `blueprints/`. CI fails if the diff touched `skills/` or `agents/` |
| **Rule enforcement** | Prose the model may ignore | `ArchHook.java check` blocks the edit (exit 2) from data the blueprint generated |
| **Duplicated rules** | Inevitable; nobody notices the drift | One owning file per norm; a CI check greps for known drift terms |
| **Context cost** | Everything in `CLAUDE.md`, loaded every turn | `CLAUDE.md` is an index; norms auto-load via `paths` only when matching files are touched |
| **Frontmatter** | Invented fields that silently do nothing | `schemas/extensions.json` is the single owner; `ArchHook.java schema` fails loud on an unknown field |
| **Code exemplars** | Snippets pasted in prose, rotting quietly | `*.java.example`; CI resolves every import against JARs from a real `start.spring.io` request |
| **Versions** | `Spring Boot 3.2.1` hardcoded, stale on day 30 | Resolved at runtime via Spring Initializr; CI fails on a version written as fact |
| **Cost visibility** | None | Every orchestrator run leaves a trail: tokens, spend, duration, failures — read back with `/audit-usage` |
| **The generated project** | Depends on the template repo forever | Self-contained: norms, skills, agents and the hook are copied in. CI guards the claim |
| **Dependencies** | Python/Node scripts, template engines | JDK, git, curl. One Java file for all hooks, on Linux/macOS/Windows |
| **Secrets in config** | Reviewed by eye | `.mcp.json` scanned by the hook; only `${VAR}`, `oauth` or `headersHelper` pass |

The honest version: this is heavier than a prompt pile. It pays off when more than one
person edits the instruction layer, or when it must survive six months.

---

## Main features

- **7 architecture blueprints**, plus a template for your own — Hexagonal, Clean
  (multi- and single-module), Onion, Layered, Vertical Slice, Modular Monolith.
- **Boundaries derived from the blueprint** — `forbidden_imports` is declared once and
  feeds both each module's generated `CLAUDE.md` (so the model *knows*) and
  `ArchHook.java check` (so the build *enforces*). They cannot diverge.
- **A 6-mode hook in a single Java file** — `check`, `format`, `tests`, `schema`,
  `audit`, `doctor`. No shell, no `.ps1` twin, same behavior on three OSes.
- **A feature pipeline** — `/new-feature` chains use case → domain → persistence →
  (messaging) → REST → tests, each step's output contract being the next one's input
  contract, then hands a consolidated spec to an executor agent.
- **12 norms loaded on demand** — DDD boundaries, naming, code quality, errors, REST,
  Lombok, value objects, persistence, testing, observability, logging, messaging.
- **Execution audit trail** — per-run report and a cross-run ledger of spend per skill.
- **Versions resolved at runtime** — nothing about Spring Boot or Java is written from
  memory, anywhere.
- **Git offer behind two confirmations** — after a green build, `git-publish` offers
  commit and `gh repo create`+push. Nothing runs unattended.

### Dependencies

`java` (JDK 21+) · `git` · `curl`. Nothing else — no Python, no Node, no `mvn` on PATH
(the wrapper arrives inside the Initializr's `starter.tgz`).

---

## Getting Started

### 1 · Install the AI layer — **before** opening Claude

```bash
mkdir my-api && cd my-api
git init && git commit --allow-empty -m "chore: initial repository"

cp -r /path/to/claude-spring-architect/.claude .     # copy the AI layer
java --version && git --version && curl --version | head -1

git add .claude && git commit -m "chore: setup Claude Code"
claude                                                # only now open the session
```

Order matters, for three concrete reasons:

- **`settings.json` is read at session startup.** Copy it after opening `claude` and
  the hooks stay inactive until you restart — the most common mistake, because
  everything *looks* fine.
- **`git commit --allow-empty` first**, because `ArchHook tests` uses `git diff HEAD`.
  With no `HEAD`, the hook exits silently and runs zero tests.
- **Commit `.claude/` before generating**, so the next `git diff HEAD` shows only
  generated code.

### 2 · Generate the project

```
/arch-doctor                     # is enforcement actually active on this machine?
/init-project --build maven      # interview → blueprint → generate → verify
```

Don't copy a `CLAUDE.md` into the project: it is **generated** by the bootstrap, with
resolved versions and the real module list. The `CLAUDE.md` at the root of *this*
repository describes the meta-repo and does not serve a Spring project.

### Silent failures worth knowing

| Symptom | Cause | Check |
|---|---|---|
| Hooks never fire | `settings.json` copied with the session already open | Restart: `/exit`, then `claude -c` |
| Hooks fail on any OS | `java` not on PATH | `/arch-doctor` |
| Forbidden import doesn't block | `.claude/forbidden-imports.txt` not generated yet | `/arch-doctor` reports the active rule count |
| Tests never run on `Stop` | No `HEAD`, or no Maven wrapper | `/arch-doctor` |

The hooks exit 0 when they cannot verify something. That is safe, but it gives false
greens — which is exactly what `/arch-doctor` exists to expose.

Full step-by-step, including what `/init-project` runs internally:
[`docs/en/02-init-project.md`](docs/en/02-init-project.md).

---

## Orchestration skills you invoke

Five commands are human-invoked only (`disable-model-invocation: true`) — generating a
project or running the feature pipeline has too large a side effect to fire from
inference over a conversation.

| Command | Does | Where it runs |
|---|---|---|
| `/init-project` | Interviews, validates the blueprint, generates the project, installs hooks, verifies the build, offers `git-publish` | This repo |
| `/new-feature UC-NNN-<slug>` | Runs the 5-skill design pipeline, consolidates a spec, hands it to the executor agent | Generated project |
| `/arch-doctor` | Diagnoses the setup on this machine: hooks, wrapper, `HEAD`, active rules | Both |
| `/audit-usage` | Reads the execution trail back: spend per skill, duration, failure rate, which report to open | Generated project |
| `/claude-code-architect-designer` | Designs a new extension of `.claude/` itself — and decides *which of the six forms* it should be | This repo |

```
/new-feature UC-NNN-<slug>
   use-case-design → domain-modeling → persistence-architect
      → messaging-architect (conditional) → rest-api-architect → test-architect
   → consolidated UC-NNN-spec.md
   → agent: java-spring-boot-developer (isolated context)
   → on success: offers git-publish, behind two confirmations
```

Each step receives the structured output of the previous one. Free-prose handoff
degrades by the third hop; that is why one step's `output-contract` *is* the next one's
`input-contract`.

The other skills — `domain-modeling`, `persistence-architect`, `rest-api-architect`,
`test-architect`, `docker-architect`, `messaging-architect`, `java-patterns`,
`git-publish` — trigger from their `description` when the request matches. You don't
type them.

Pipeline detail: [`docs/en/03-new-feature.md`](docs/en/03-new-feature.md).

---

## Blueprints

| ID | Layout | Choose if | Cost |
|---|---|---|---|
| `layered` | single-module | CRUD; familiar controller/service/repository vocabulary | ArchUnit is the only boundary — the compiler enforces nothing |
| `clean-architecture-single-module` | single-module | Clean vocabulary without module ceremony | Same: boundaries live in the test, not the build graph |
| `clean-architecture-multi-module` | multi-module | Long-lived system, medium/large team | More ceremony; many mappings |
| `hexagonal` | multi-module | Multiple entry/exit channels | More modules than layered |
| `onion` | multi-module | Palermo's terminology (Application Core, Gateway); persistence and presentation as independent peers | Domain module carries model + services + every Gateway |
| `vertical-slice` | single-module | Loosely coupled features, fast delivery | Duplication between slices |
| `modular-monolith` | single-module | Multiple bounded contexts (Spring Modulith) | A second verifier (`ApplicationModules.verify()`) on top of ArchUnit |
| `custom` | your choice | You already have an in-house pattern | You describe and validate it |

Don't default to the fanciest one. Over-engineering is paid every day; under-engineering
is paid once, at refactor time.

**Adding one is the test that proves the design.** Copy the template, edit the YAML —
and touch nothing else:

```bash
cp .claude/blueprints/custom-template/custom.template.yaml .claude/blueprints/my-style/my-style.yaml
git status --porcelain .claude/skills .claude/agents    # must come back empty — CI checks this
```

The contract (7 mandatory blocks, 5 validation rules) is in
[`blueprints/_schema.md`](.claude/blueprints/_schema.md); a walkthrough in
[`docs/en/05-blueprints.md`](docs/en/05-blueprints.md).

---

## Governance, compliance and evolution

This is the part that is hard to copy from a prompt pile, and the reason the repo
exists in this shape: **how the instruction layer stays correct as it grows.**

### The dependency graph is real

```
hooks/ + settings.json        infra / enforcement — deterministic
        ↓ verifies
skills/                       procedure + exemplars
        ↓ invokes                     ↑ delegates via context: fork
agents/                       isolated execution
        ↓ cites, never copies
rules/ + blueprints/ + .mcp.json      norms and data     ← LEAF

decisions/                    history — nobody reads it at runtime
```

Eleven invariants derive from it — `rules/` is a leaf, each norm has one owning file,
code boilerplate never lives in a norm, an agent exists only to preserve context /
restrict tools / change model, a generated project is self-contained, no literal secret
in a versioned file. Full list: [`CLAUDE.md`](CLAUDE.md) § Invariants.

### The invariants are CI checks, not good intentions

`validate.yml` runs on every push and PR, and turns each prose invariant into a
deterministic check:

- **Cross-platform hooks** — `ArchHook doctor` plus a `BoundaryTest` that injects a
  forbidden import and requires exit 2, on `ubuntu`, `macos` and `windows`.
- **Frontmatter schema** — `ArchHook.java schema` validates every skill, agent, rule
  and `.mcp.json` against `schemas/extensions.json`, the single owner of the recognized
  field list, and scans `headers`/`env` for literal secrets.
- **Architecture of the `.claude/` itself** — no rule mentions a skill or agent, no
  code declaration inside `rules/`, no new `commands/`, a new blueprint leaves prompts
  untouched, every norm's `paths` matches a package some blueprint declares.
- **Exemplars actually compile** — every `import` in a `*.java.example` resolves
  against JARs fetched from a real `start.spring.io` request.
- **No version written from memory** — no `Spring Boot X.Y` / `Java NN` stated as fact
  outside `decisions/`.
- **No dependency outside the Java ecosystem** — no `pip`, `npm`, `node` or `python`
  anywhere in `.claude/`.

Known gaps are listed, not hidden: self-containment (invariant 9) is still verified by
hand, and `claude plugin validate` isn't on the runner. Full job table:
[`docs/en/07-ci-validate.md`](docs/en/07-ci-validate.md).

### Every run is auditable

In a generated project, `ArchHook.java audit` records the lifecycle of each orchestrator
run — prompt, tool calls, permissions granted, failures, compaction, tokens and spend —
into `.claude/audit-usage/`, as one Markdown report per run plus an append-only ledger.
`/audit-usage` reads it back and answers what a single report can't: what has this
project cost so far, per skill, across runs.

Two deliberate properties: the trail switches on by the mere **presence** of the
directory (turning it on is the user's decision, not a side effect), and the observers
— `/audit-usage`, `/arch-doctor` — are excluded from the trail, so watching the system
doesn't pollute what it measures. It is **off in this meta-repo on purpose**: it is a
feature of the generated project, and copying the hook entries here would start
auditing the design of the tool instead of its use.

### Every decision has a record, and every run produces lessons

- **`decisions/`** — one ADR per shaped piece: why a skill is a skill and not an agent,
  why hooks are Java, why `metadata:` left the frontmatter. It records what was decided
  *on that date*, is superseded rather than rewritten, and never enters the runtime
  graph. Index: [`decisions/README.md`](.claude/decisions/README.md).
- **`lessons-learned/`** — real end-to-end runs are written up as gap lists with a fix
  at the source, and those fixes become decisions and CI checks. The evolution loop is:
  run it for real → record the gap → fix the generator, not the output → guard it in CI.

### Contributing gate

A PR that adds a blueprint passes the 5-rule checklist and declares honest
`trade_offs`. A PR that adds a norm gives it its own file, lists it in `00-index.md`,
and copies it nowhere. No PR pins a Java or Spring Boot version, and none adds a
dependency outside JDK / git / curl — `validate.yml` fails if it does.

---

## Example projects

| Project | Blueprint | What it shows |
|---|---|---|
| [demo-clean-arch-single-module](https://github.com/ice-lfernandes/demo-clean-arch-single-module) | `clean-architecture-single-module` | A full `/init-project` run: generated structure, copied norms and skills, installed hooks, green build and first commit |

Generated projects carry their own `.claude/` and do not depend on this repository.

---

## Documentation index

| Document | Content |
|---|---|
| [`docs/en/00-overview.md`](docs/en/00-overview.md) | Full `.claude/` architecture diagram, legend by piece type |
| [`docs/en/01-file-types.md`](docs/en/01-file-types.md) | Runtime mechanics of skill, rule, agent, `CLAUDE.md`, hook — and every recognized frontmatter field |
| [`docs/en/02-init-project.md`](docs/en/02-init-project.md) | `/init-project` end to end: who calls whom, output contract |
| [`docs/en/03-new-feature.md`](docs/en/03-new-feature.md) | `/new-feature`: the 5-skill pipeline plus executor |
| [`docs/en/04-arch-doctor.md`](docs/en/04-arch-doctor.md) | `/arch-doctor`: what it diagnoses, how to read the output |
| [`docs/en/05-blueprints.md`](docs/en/05-blueprints.md) | `_schema.md` contract and how to write a custom blueprint |
| [`docs/en/06-claude-code-architect-designer.md`](docs/en/06-claude-code-architect-designer.md) | Designing a new extension: 5 phases, decision matrix, anti-patterns |
| [`docs/en/07-ci-validate.md`](docs/en/07-ci-validate.md) | `validate.yml` job by job, and what isn't covered yet |
| [`CLAUDE.md`](CLAUDE.md) | The invariants, the routing table, the known pitfalls |
| [`claude-help.md`](claude-help.md) | Reference for how each Claude Code mechanism works |
| [`roadmap.md`](roadmap.md) | Delivery phases and a checklist of every feature, done or not |

Portuguese versions of the `docs/` pages live alongside them in
[`docs/`](docs/README.md).

---

## Contributing

Blueprints and norms are the most useful contributions. Read the gate under
[§ Governance](#contributing-gate) before opening a PR, and run
`claude plugin validate .claude/skills` plus `java .claude/hooks/ArchHook.java schema`
locally — the first catches malformed YAML, the second catches everything the runtime
would silently ignore.

## License

MIT.
