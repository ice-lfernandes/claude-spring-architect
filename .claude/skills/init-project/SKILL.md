---
name: init-project
description: >
  Initializes a Spring Boot project from scratch with a selectable architecture. Manual
  ritual — interview, validate the blueprint, generate the structure, install the hooks
  and verify the build. Explicit invocation only.
argument-hint: "[--blueprint <id>] [--build maven|gradle] [--groupId <groupId>] [--name <artifactId>]"
disable-model-invocation: true
allowed-tools: Agent, Read, Write, Bash, Glob, AskUserQuestion
---

## Available blueprints

!`find .claude/blueprints -mindepth 2 -maxdepth 2 -name '*.yaml' 2>/dev/null | xargs -n1 basename | sed 's/\.yaml$//'`

## Directory state

!`ls -a | head -30`

## Arguments

$ARGUMENTS

---

Delegates to the `project-initializer` agent. The agent exists for two of the three
legitimate reasons: generation produces verbose output that shouldn't fill this
conversation, and it runs with a restricted set of tools.

If the directory already contains `pom.xml` or `build.gradle`, **do not initialize**:
report what you found and suggest `/new-feature` instead.

Return the agent's report without rewriting it — the format is fixed in the
§ Output contract of `.claude/skills/project-bootstrap/SKILL.md`.
