# `claude-spring-architect` workflow

Versão em português: [`docs/`](../README.md).

This folder documents how the pieces of this repository's `.claude/` collaborate to
run the main commands — `/init-project`, `/new-feature`, `/arch-doctor`, and
`/audit-usage` — and what this project does that similar repositories don't. A
first-time reader should start at [09-differentiators.md](09-differentiators.md).

`claude-spring-architect` is not an application — it's a meta-repository. What it produces are
**instruction files** (skills, rules, agents, hooks) that, together, generate Spring
Boot projects already prepared for AI-assisted development. Understanding how these
files call each other is the prerequisite for editing any one of them without breaking
the others.

## Recommended reading order

| Document | Content |
|---|---|
| [00-overview.md](00-overview.md) | General diagram of the `.claude/` architecture, with a legend by piece type |
| [01-file-types.md](01-file-types.md) | How each type works — skill, rule, agent, `CLAUDE.md`, hook — according to the Claude Code runtime |
| [02-init-project.md](02-init-project.md) | Full `/init-project` workflow: who calls whom, invocation example, final report |
| [03-new-feature.md](03-new-feature.md) | Full `/new-feature` workflow: 5-skill pipeline + executor, example, final report |
| [04-arch-doctor.md](04-arch-doctor.md) | `/arch-doctor` workflow: what it diagnoses and how to read the output |
| [05-blueprints.md](05-blueprints.md) | `_schema.md` contract, what each blueprint declares, how to create a custom blueprint |
| [06-claude-code-architect-designer.md](06-claude-code-architect-designer.md) | `/claude-code-architect-designer` workflow: the 5 phases, decision matrix, frontmatter fields, full example |
| [07-ci-validate.md](07-ci-validate.md) | `validate.yml`: why it exists, what each job/step verifies, what's still missing |
| [08-audit-usage.md](08-audit-usage.md) | Audit trail: the `audit` hook (one report per run, tokens and cost per piece), the `guard` hook (design never writes `src/`, approved specs frozen), the `/audit-usage` skill |
| [09-differentiators.md](09-differentiators.md) | What this repository does that templates, skills packs, and agent bundles on GitHub don't — comparison table, sources, and what is not a differentiator |

## How this repository is organized (quick reference)

```
.claude/
├── hooks/ + settings.json   enforcement infra — deterministic
├── skills/                  procedure + exemplars
├── agents/                  isolated execution
├── rules/ + blueprints/     norms and data (leaves — call nobody)
└── decisions/               decision history — outside the runtime graph
```

This is the same dependency graph described in `CLAUDE.md` § Architecture of the AI
files, applied to the `.claude/` folder itself as Clean Architecture. The documents in
this folder detail how that graph behaves across the three commands above.

## What this documentation is not

It is not a copy of the rules — every `SKILL.md`, `rule`, and `agent` remains the
single source (`CLAUDE.md`, invariant 2). What's here is the **reading across
files**: the call sequence, why each piece exists in the shape it's in, and concrete
execution examples. Wherever this document cites a Claude Code runtime behavior
(frontmatter, hooks, subagents), the source is `claude-help.md`, at the root of this
repository.
