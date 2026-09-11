# MCP — fields the runtime recognizes

Reference for the `claude-code-architect-designer` skill. Loads only when it is invoked,
same discipline as `frontmatter-fields.md`.

Source: `@claude-help.md` § 9. Primary source:
<https://code.claude.com/docs/en/mcp>, <https://code.claude.com/docs/en/settings-reference>,
<https://code.claude.com/docs/en/managed-mcp>. Where they diverge, the official docs win
— and this file gets corrected.

## Who owns this list

**The owner is `.claude/schemas/extensions.json`'s `mcp` block**, because that's what
`ArchHook.java schema` reads and what it blocks on. A field added only here doesn't
become accepted — it still returns exit 2. Same relationship this skill's
`frontmatter-fields.md` has to the `types` block of the same file.

When adding a field: JSON first, then the table. Confirm with
`java .claude/hooks/ArchHook.java schema`.

Design and rationale: `@.claude/decisions/0033-mcp-in-architect-designer.md`.

---

## `.mcp.json` — top level

| Field | For what |
|---|---|
| `mcpServers` | The only recognized top-level key. An object of `<server-name>: <server-config>` |

## Per-server fields

| Field | Required when | For what |
|---|---|---|
| `type` | Always | `stdio` \| `http` \| `sse` \| `ws`. Missing or unrecognized → schema error |
| `command` | `type: stdio` | Executable to run |
| `args` | Optional, any type | Arguments passed to `command`, or extra flags for other transports |
| `url` | `type: http` \| `sse` \| `ws` | Endpoint the transport connects to |
| `headers` | Optional | Static HTTP headers — where a credential goes, expanded, never literal |
| `env` | Optional | Environment variables passed to a `stdio` process — same expansion rule |
| `timeout` | Optional | Milliseconds. Also settable per-session via `MCP_TIMEOUT` / `MCP_TOOL_TIMEOUT` |
| `oauth` | Optional | `clientId`, `callbackPort`, `authServerMetadataUrl`, `scopes` — OAuth 2.0 instead of a static header |
| `headersHelper` | Optional | Path to a script whose stdout (JSON) supplies headers at connect time — for Kerberos, SSO, or any credential that can't sit still in a file. Requires workspace trust for a project/local-scope server |
| `alwaysLoad` | Optional | `true` keeps the server's tools loaded even under tool search |

`command`/`args`/`env` only apply to `stdio`; `url` only to `http`/`sse`/`ws`. A `stdio`
entry with `url` (or vice versa) is a field the wrong transport doesn't use — flagged the
same way a camelCase field in a skill's frontmatter is.

## Server name

`^[a-z][a-z0-9_-]*$`. Reserved and rejected by the runtime: `workspace`, `computer-use`.
A name outside the pattern, or reserved, fails `ArchHook.java schema` before it ever
reaches `claude mcp list`.

## Credential rule — invariant 11

No literal secret in `headers` or `env`. Allowed:

- `${VAR}` / `${VAR:-default}` expansion, anywhere in the value — `"Bearer ${TOKEN}"` is
  the documented good pattern
- `oauth`, when the server supports it
- `headersHelper`, for a credential that must be computed at connect time

`ArchHook.java schema`'s secret scan: a value in `headers`/`env` with no `${` in it, whose
key name looks credential-shaped (`Authorization`, `token`, `api_key`, `secret`,
`password` — case-insensitive) or whose value starts with a known token prefix (`Bearer
`, `ghp_`, `github_pat_`, `sk-`, `xoxb-`, `AKIA`), is rejected. A value containing `${`
anywhere is never flagged, even with a sensitive key name — that's the difference
between `"Bearer ${TOKEN}"` (safe) and `"Bearer ghp_abc123"` (rejected).

## Tool and prompt naming

```
mcp__<server>__<tool>          e.g. mcp__github__create_pr
mcp__plugin_<plugin>_<server>__<tool>    for a plugin-bundled server
```

Use this pattern in `permissions.allow/ask/deny`, in an agent's `disallowedTools`, and in
`allowed-tools`/`disallowed-tools` in a skill's frontmatter — all four already accept
`mcp__*`, no schema change needed there.

MCP resources: `@server:resource-name`. MCP prompts: `/mcp__server__prompt-name`.

## Scopes — where a server is declared, and what that means for this repo

| Scope | File | Shared | This skill's stance |
|---|---|---|---|
| `local` | `~/.claude.json` (per-project entry) | No | Not written by this skill — personal/experimental, `claude mcp add` without `--scope project` |
| `project` | `.mcp.json` at the repo root | Yes, via git | **Form 6a.** This skill's default write target |
| `user` | `~/.claude.json` (global) | No, applies to every project | Not written by this skill — out of any single repo's scope |

Precedence: `local > project > user`, silently — a personal server with the same name as
the team's shadows it, no warning either way.

## `settings.json` keys for MCP (project-level approval, not server definition)

| Key | For what |
|---|---|
| `enabledMcpjsonServers` | Array — pre-approves specific `.mcp.json` servers by name |
| `disabledMcpjsonServers` | Array — rejects specific `.mcp.json` servers by name |
| `enableAllProjectMcpServers` | Boolean — approves every `.mcp.json` server with no prompt. **This skill never proposes setting this to `true`** — it removes the one human trust checkpoint a cloned repository can't skip on its own |
| `allowedMcpServers` / `deniedMcpServers` | Organization-level allow/deny list, not this repo's concern unless it becomes one |

These are recognized in `.claude/schemas/extensions.json`'s `settings.allowed` list —
same file, same validation pass as the hook-entry checks already there.

## Cost and output limits

- Tool search defers full schemas; only tool **names** load at startup. `/context all`
  shows the actual per-server cost.
- `MAX_MCP_OUTPUT_TOKENS` (env, default 25000) caps a single MCP tool's output.
  `MCP_TIMEOUT` / `MCP_TOOL_TIMEOUT` (env, milliseconds) bound connection and per-call
  time, on top of any per-server `timeout` in `.mcp.json`.

## Diagnosis

| Symptom | Likely cause |
|---|---|
| Server declared but the model never uses it | Needs a skill teaching the tools, or the description in the server's own tool metadata is too abstract — this is the "MCP + skill combine" case in `@claude-help.md` § 9 |
| Server doesn't load / connection fails | Missing or unexpanded `${VAR}` — check `claude mcp list` for the warning; `ArchHook.java schema` does not detect this, only literal secrets |
| Edited `.mcp.json` mid-session, nothing changed | `.mcp.json` is only read at startup, same as `settings.json` — restart `claude` |
| A teammate's server never shows up for them | It's `local` scope (`~/.claude.json`), not `project` (`.mcp.json`) — not shared via git |
| First run of a cloned project asks to approve a server | Expected — the workspace-trust prompt for a `project`-scope server. Approve it, or run `/mcp`. Never suppress with `enableAllProjectMcpServers` |
