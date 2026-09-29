# 14 — Agent SDK

Official pages covered:

| Page | Link |
|--------|------|
| Agent SDK overview | <https://code.claude.com/docs/en/agent-sdk/overview> |
| Quickstart | <https://code.claude.com/docs/en/agent-sdk/quickstart> |
| Migrate to Claude Agent SDK | <https://code.claude.com/docs/en/agent-sdk/migration-guide> |
| Troubleshoot the Agent SDK | <https://code.claude.com/docs/en/agent-sdk/troubleshooting> |
| Configure your agent | <https://code.claude.com/docs/en/agent-sdk/configuration> |
| Examples | <https://code.claude.com/docs/en/agent-sdk/examples> |
| How the agent loop works | <https://code.claude.com/docs/en/agent-sdk/agent-loop> |
| Use Claude Code features in the SDK | <https://code.claude.com/docs/en/agent-sdk/claude-code-features> |
| Work with sessions | <https://code.claude.com/docs/en/agent-sdk/sessions> |
| Persist sessions to external storage | <https://code.claude.com/docs/en/agent-sdk/session-storage> |
| Streaming input | <https://code.claude.com/docs/en/agent-sdk/streaming-vs-single-mode> |
| Handle approvals and user input | <https://code.claude.com/docs/en/agent-sdk/user-input> |
| Stream responses in real-time | <https://code.claude.com/docs/en/agent-sdk/streaming-output> |
| Get structured output from agents | <https://code.claude.com/docs/en/agent-sdk/structured-outputs> |
| Give Claude custom tools | <https://code.claude.com/docs/en/agent-sdk/custom-tools> |
| Connect to external tools with MCP | <https://code.claude.com/docs/en/agent-sdk/mcp> |
| Scale to many tools with tool search | <https://code.claude.com/docs/en/agent-sdk/tool-search> |
| Subagents in the SDK | <https://code.claude.com/docs/en/agent-sdk/subagents> |
| Modifying system prompts | <https://code.claude.com/docs/en/agent-sdk/modifying-system-prompts> |
| Extend agents with skills | <https://code.claude.com/docs/en/agent-sdk/skills> |
| Plugins in the SDK | <https://code.claude.com/docs/en/agent-sdk/plugins> |
| Configure permissions | <https://code.claude.com/docs/en/agent-sdk/permissions> |
| Intercept behavior with hooks | <https://code.claude.com/docs/en/agent-sdk/hooks> |
| Rewind file changes with checkpointing | <https://code.claude.com/docs/en/agent-sdk/file-checkpointing> |
| Track cost and usage | <https://code.claude.com/docs/en/agent-sdk/cost-tracking> |
| Observability with OpenTelemetry | <https://code.claude.com/docs/en/agent-sdk/observability> |
| Track todos | <https://code.claude.com/docs/en/agent-sdk/todo-tracking> |
| Hosting the Agent SDK | <https://code.claude.com/docs/en/agent-sdk/hosting> |
| Securely deploying AI agents | <https://code.claude.com/docs/en/agent-sdk/secure-deployment> |
| Reference TypeScript | <https://code.claude.com/docs/en/agent-sdk/typescript> |
| Reference Python | <https://code.claude.com/docs/en/agent-sdk/python> |

---

## What it is

The Agent SDK exposes Claude Code **as a library**, to build production agents with the same
tools, harness and capabilities — with full control over orchestration, tool access and
permissions. Available in TypeScript and Python.

Architecture: the SDK runs the Claude Code CLI as a subprocess. This explains much of the
troubleshooting (CLI that does not start, process that exits, result without structured
output).

## Core concepts

- **Agent loop** — same cycle of messages, tool execution and context management as the CLI.
- **Sessions** — persist history; `continue`, `resume` and `fork` to pick up earlier runs.
  Transcripts can be mirrored to your own storage (object store, key-value, database) so
  another host can resume the session.
- **Claude Code features in the SDK** — project instructions (`CLAUDE.md`), skills, hooks and
  plugins load in the SDK; `settingSources` (equivalent to `--setting-sources`) controls
  which filesystem settings sources are included.

Defaults that differ from the interactive CLI: fork mode **off**, built-in agents can be
disabled with `CLAUDE_AGENT_SDK_DISABLE_BUILTIN_AGENTS=1`, and `PermissionRequest` only
exists when the host provides the `canUseTool` callback.

## Input and output

- **Streaming input** vs. single mode: two input modes, with distinct use cases.
- **Streaming output**: text and tool calls in real time.
- **Structured outputs**: JSON validated by JSON Schema, Zod or Pydantic after tool use
  (equivalent to the CLI's `--json-schema`).
- **User input and approvals**: how to surface Claude's approval requests and questions to
  the end user and return the decisions to the SDK.

## Extending with tools

- **Custom tools** via an *in-process* MCP server: your functions and APIs become agent
  tools.
- **External MCP**: transports, authentication, error handling.
- **Tool search**: scales to thousands of tools by loading only the needed ones.
- **Subagents in the SDK**: isolate context, run in parallel and apply specialized
  instructions; definable programmatically (equivalent to the CLI's `--agents`).

## Customizing behavior

- **System prompt**: choose between the `claude_code` preset and your own prompt; customize
  via `CLAUDE.md`, output styles, `append`, or full replacement.
- **Skills**: control which skills the agent may invoke, dispatch commands by name and write
  skills that sessions discover.
- **Plugins**: load plugins to add skills, agents, hooks and MCP servers.

## Control and observability

- **Permissions**: permission modes, hooks and declarative allow/deny rules — the same
  semantics documented in [07](07-settings-permissions-and-security.md).
- **Hooks**: intercept behavior at the same lifecycle points.
- **File checkpointing**: tracks file changes and restores earlier states.
- **Cost tracking**: token usage, cost estimate and prompt caching configuration.
- **OpenTelemetry**: traces, metrics and events to the observability backend.
- **Todo tracking**: render Claude's progress in your application from the structured tool
  calls.

## Deploy

- **Hosting**: subprocess architecture, session persistence, scaling, observability and
  multi-tenant isolation on Docker, Kubernetes and serverless.
- **Secure deployment**: isolation, credential management and network controls.

## Migration

The old "Claude Code" SDKs (TypeScript and Python) were replaced by the **Claude Agent SDK**
— there is a dedicated migration guide. The TypeScript SDK's V2 session API was **removed**;
the corresponding page exists only as a historical reference.
