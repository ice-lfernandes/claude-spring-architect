---
name: arch-doctor
description: >
  Diagnoses the AI setup and architecture enforcement on this machine: active hooks,
  loaded boundary rules, Maven wrapper, `java` on PATH. Explicit invocation
  only.
disable-model-invocation: true
allowed-tools: Bash, Read
---

## Diagnosis

!`java "${CLAUDE_PROJECT_DIR:-.}/.claude/hooks/ArchHook.java" doctor 2>&1`

## AI files

!`find .claude -maxdepth 2 -type f | sort`

---

Interpret the result above and tell the user, in two or three sentences, whether the
setup is operational. If something is marked with ❌, give the concrete command that
fixes it — don't describe the problem in general terms.

Don't fix anything without the user asking.
