# 05 — MCP (Model Context Protocol)

Official pages covered:

| Page | Link |
|--------|------|
| Connect to MCP servers (quickstart) | <https://code.claude.com/docs/en/mcp-quickstart> |
| Connect Claude Code to tools via MCP (reference) | <https://code.claude.com/docs/en/mcp> |
| Control MCP server access for your organization | <https://code.claude.com/docs/en/managed-mcp> |
| Connect to external tools with MCP (SDK) | <https://code.claude.com/docs/en/agent-sdk/mcp> |
| Push events into a running session with channels | <https://code.claude.com/docs/en/channels> |

---

## MCP's role in the ecosystem

MCP is an open standard for connecting Claude to external tools and data: databases, issue
trackers, browsers, internal APIs. Connect a server when you catch yourself copying data
from another tool into the chat.

Contrast with a skill: **MCP provides the connection and the tools; the skill teaches how to
use them well.** The two combine (MCP connects to the database, a skill documents the schema
and the query patterns).

> Verify that you trust each server before connecting. Servers that fetch external content
> expose you to prompt injection.

## Installing servers

```bash
# Remote HTTP (recommended)
claude mcp add --transport http notion https://mcp.notion.com/mcp
claude mcp add --transport http secure-api https://api.example.com/mcp --header "Authorization: Bearer token"

# SSE (deprecated; HTTP falls back to SSE on its own from v2.1.265+)
claude mcp add --transport sse asana https://mcp.asana.com/sse

# Local stdio — everything after -- goes to the server
claude mcp add --env AIRTABLE_API_KEY=KEY --transport stdio airtable -- npx -y airtable-mcp-server

# WebSocket (JSON only)
claude mcp add-json events '{"type":"ws","url":"wss://mcp.example.com/socket","headers":{"Authorization":"Bearer T"}}'
```

Configuration notes:

- In JSON, `type` accepts `streamable-http` as an alias of `http`.
- An entry with `url` and **no** `type` is an error: Claude Code reads it as stdio and skips
  the server.
- `"type": "sdk"` can only be registered by an SDK host application.
- stdio servers receive `CLAUDE_PROJECT_DIR` in their environment. To limit directory
  access, implement `roots/list` (Claude Code answers with the launch directory plus the
  added ones and sends `notifications/roots/list_changed`).
- To adapt instructions written for another client: URL → `--transport http`; `npx`/`uvx`
  command → stdio with `--`; `mcpServers` block → `claude mcp add-json` with the object
  inside the wrapper.

Management: `claude mcp list`, `claude mcp get <name>`, `claude mcp remove <name>`, and
`/mcp` inside the session. Removing a remote server also deletes its stored OAuth tokens.

## Scopes and precedence

| Scope | Loads in | Shared | Stored in |
|--------|-----------|---------------|-------------|
| Local (default) | Only the current project | No | `~/.claude.json` |
| Project | Only the current project | Yes, via git | `.mcp.json` at the root |
| User | All your projects | No | `~/.claude.json` |

Precedence (highest to lowest): managed (`managedMcpServers`) > local > project > user >
plugin servers > claude.ai connectors. Duplicates across scopes are matched by **name**;
plugins and connectors, by **endpoint**.

Servers from `.mcp.json` require interactive approval before use. In `claude -p`, in the
Agent SDK and in cloud sessions there is no prompt: they load without approval. To restrict:
`disabledMcpjsonServers`, `--setting-sources` without `project`, or `--strict-mcp-config`
(uses only what comes from `--mcp-config`).

Approvals committed to the repository (`enableAllProjectMcpServers`,
`enabledMcpjsonServers`) only apply after you trust the workspace.

## Status and diagnostics

`claude mcp list` shows `✔ Connected`, `! Needs authentication`, `✘ Failed to connect`,
`⏸ Pending approval`, `✘ Rejected` or `⊘ Disabled for this project`. WebSocket servers do
not appear in that list — use `claude mcp get` or `/mcp`.

Configuration warnings Claude Code emits:

- **Hidden whitespace** in `command`, `url`, `args`, `env` or `headers` (typical of a token
  pasted with a newline). It does not trim — you have to edit.
- **Same name in more than one scope** with different endpoints (OAuth logins are per
  endpoint).
- **Reserved names**: `workspace`, `claude-in-chrome`, `computer-use`, `Claude Preview`,
  `Claude Browser`.
- **Missing environment variable**: `${VAR}` with no value and no `:-default` loads
  literally.

The discovery cache (`MCP_DISCOVERY_CACHE=1`) allows a `cached ... connects on first use`
status, connecting only on the first tool call.

## Environment variables in `.mcp.json`

`${VAR}` and `${VAR:-default}` are expanded, so you can commit the configuration without
secrets:

```json
{
  "mcpServers": {
    "notion": {
      "command": "npx",
      "args": ["-y", "@notionhq/notion-mcp-server"],
      "env": { "NOTION_TOKEN": "${NOTION_TOKEN}" }
    }
  }
}
```

Gotcha: an undefined variable does **not** fail parsing — the server loads with the literal
`${VAR}` text and fails only on connection (with a warning in `claude mcp list` and `/mcp`).

## Tool search (context)

By default, MCP tool definitions are **deferred**: only tool names and the server's
instructions load at session start; full schemas come in on demand. That keeps the cost of
adding servers almost flat.

- Requires a model that supports `tool_reference` (Sonnet 4.5, Haiku 4.5, Opus 4.5 and
  later).
- Turned off automatically when `ANTHROPIC_BASE_URL` points to a non-first-party host.
- Controlled by `ENABLE_TOOL_SEARCH`; `CLAUDE_CODE_DISABLE_EXPERIMENTAL_BETAS` keeps it off.
- Tool descriptions and server instructions are truncated at 2,048 characters
  (`CLAUDE_CODE_MAX_MCP_DESCRIPTION_LENGTH` adjusts it). Put the essentials at the start.

For server authors: **server instructions** matter more with tool search — explain the
category of tasks, when to look for its tools and the main capabilities.

## Permissions and tool names

MCP tools are named `mcp__<server>__<tool>` (e.g. `mcp__github__search_repositories`).
Servers coming from a plugin use a scoped segment: `mcp__plugin_my-plugin_db__query`.

- Allow: `mcp__puppeteer__*` or `mcp__github__get_*` — the server segment cannot have a
  glob. Unanchored globs (`"*"`, `"mcp__*"`) in allow are ignored with a warning.
- Deny/ask: `mcp__*` blocks every MCP tool.
- `mcp__` rules with parentheses in settings files are skipped (use `--disallowedTools` to
  match a parameter on an MCP tool).
- Tools marked `requiresUserInteraction` always ask for approval, even with an `allow` hook.

## Authentication

OAuth for remote servers (`/mcp` drives the flow; `claude mcp add` accepts static headers).
Available features: fixed callback port, pre-configured OAuth credentials, metadata
discovery override, scope restriction and `headersHelper` to generate headers dynamically at
connection time.

## Other features

- **Channels**: an MCP server can push messages into the session (Telegram, Discord,
  webhooks), making Claude react to external events.
- **MCP resources**: `ListMcpResourcesTool` / `ReadMcpResourceTool`, and reference by `@`.
- **Elicitation**: the server asks for user input during a tool call (`Elicitation` and
  `ElicitationResult` hook events).
- **Claude Code as an MCP server**: exposes its capabilities to other clients.
- **claude.ai connectors**: enter the session with organization controls
  (`disableClaudeAiConnectors` is honored as `true` from any scope).
- **Output limits**: large outputs are truncated with a warning; the limit can be raised per
  tool.
- **Automatic backgrounding** of long tool calls.
