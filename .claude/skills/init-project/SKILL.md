---
name: init-project
description: >
  Initializes a Spring Boot project from scratch with a selectable architecture. Manual
  ritual — interview, validate the blueprint, generate the structure, install the hooks
  and verify the build. Explicit invocation only.
argument-hint: "[--blueprint <id>] [--build maven|gradle] [--groupId <groupId>] [--name <artifactId>] [--bounded-context <name>]"
disable-model-invocation: true
allowed-tools: Agent, Read, Write, Glob, AskUserQuestion, Bash(find:*), Bash(sort:*), Bash(ls:*), Bash(head:*)
model: sonnet
effort: low
---

## Available blueprints

!`find "${CLAUDE_PROJECT_DIR:-.}/.claude/blueprints" -mindepth 2 -maxdepth 2 -name '*.yaml' 2>/dev/null | sort`

## Next to this repository

!`ls -a "${CLAUDE_PROJECT_DIR:-.}/.." | head -30`

## Arguments

$ARGUMENTS

---

## Procedure

1. The project is generated next to this repository, at `../<artifactId>`
   (`project-bootstrap` § Where the project is born). If `--name` is given and the listing above
   shows that name, check it with `ls -a` on that directory. If it holds `pom.xml` or
   `build.gradle`, **do not initialize**: report what you found and suggest `/new-feature` from
   a session opened in that directory. Stop here. Without `--name`, the agent runs the same
   check after its interview.
2. Delegate to the `project-initializer` agent, passing the arguments above. The agent
   exists for two of the three legitimate reasons: generation produces verbose output that
   shouldn't fill this conversation, and it runs with a restricted set of tools.
3. Return the agent's report without rewriting it — the format is fixed in the
   § Output contract of `.claude/skills/project-bootstrap/SKILL.md`.
4. Chain `git-publish` once the report says the project was created, and only then, with
   `project: <absolute path of the generated project>` in its context.
5. End by telling the user to open a new session in the generated project
   (`cd ../<artifactId> && claude`). That session loads the project's own `settings.json` and
   hooks; this one keeps this repository's.

## Why this is a manually-invoked skill

Form 2: creating a project is a ritual a person starts (axis 2 — `/init-project`), and the
interview it leads to cannot be triggered by inference from a sentence. Form 1 was rejected
for exactly that: a skill that can scaffold a whole tree on the model's own judgment is a
directory rewritten by accident. Form 3 was rejected here and used one level down instead —
the isolation belongs to the generation, which is why `project-initializer` exists.

Runs on `sonnet` with `effort: low`: everything it does is delegated to `project-initializer`, which declares its own model; this skill parses the arguments and relays the report (`@.claude/decisions/0081-skill-model-required-per-class.md`).

## Contract

**Class:** orchestrator — the territory is `skill_classes.orchestrator`'s override for this
skill in `@.claude/schemas/extensions.json`, and it is **empty**: this skill writes no file
itself. Everything is written by `project-initializer`, which the guard bypasses as an
executor agent, and by `project-bootstrap` under it. `ArchHook.java guard` enforces it.

**Reads** the blueprint listing and the listing of this repository's parent directory injected
above, and the arguments.

**Delegates** to `project-initializer` — never generates a file inline, not even a single
`pom.xml`. A partial generation from this thread is the failure mode the agent exists to
prevent: the report would describe a tree nobody wrote in full.
