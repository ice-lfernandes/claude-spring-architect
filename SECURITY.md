# Security policy

## Reporting a vulnerability

Do not open a public issue for a vulnerability, and do not file it with `/report-issue`
from a generated project: both publish it.

Report it privately, in one of two ways:

- **GitHub** (preferred): [Report a vulnerability](https://github.com/nerviz-ai/nerviz/security/advisories/new)
  on the Security tab of this repository.
- **Email**: `security@nerviz.dev`.

Include the version you found it in (the tag, or the `ref` in a project's
`.claude/.arch-provenance.json`), the steps that reproduce it, and what an attacker gains.
Do not send a real token, key or password, even one already revoked.

Nerviz is maintained by one person. A report is acknowledged within 7 days, and the fix,
the advisory and the credit are agreed with you before anything is published. Say so if
you prefer not to be credited.

## Supported versions

Only the latest tag is supported. A fix ships as a new tag, and a project picks it up with
`/arch-adopt`, which also prints the `migrations` note when the fix changes a convention
already-generated code relies on.

## What is in scope

Nerviz runs on a developer's machine with that developer's permissions: the hooks run
`.claude/hooks/ArchHook.jar` on Claude Code events, and `/arch-adopt` downloads and writes
`.claude/`. In scope is anything that turns that into harm:

- Code execution, or a write outside the project, triggered by content Nerviz reads: a
  blueprint, a spec, `extensions.json`, an `.mcp.json`, an issue body read by
  `/triage-issue`, the archive `/arch-adopt` downloads.
- A secret leaving the machine or landing in a versioned file despite the secret scan of
  `ArchHook.java schema`.
- The update channel: anything that lets `/arch-adopt` install content that is not this
  repository's.
- The GitHub Actions workflows of this repository and of
  [`nerviz-ai/marketplace`](https://github.com/nerviz-ai/marketplace).
- A template or norm that makes generated code insecure by default, for example an
  endpoint left open by the `security-architect` templates.

## What is not

- **Claude Code itself.** Report it to Anthropic:
  [responsible disclosure policy](https://www.anthropic.com/responsible-disclosure-policy).
- **Spring, the JDK, and other upstream dependencies.** Report them upstream.
- **A bypass of `guard` that needs the person at the keyboard to cooperate.** `guard` keeps
  the model inside a skill's territory; it is not a sandbox against the user who runs it.
  Open a regular issue for that.
- **Code a project wrote after generation**, unless a Nerviz template or norm produced the
  flaw.
