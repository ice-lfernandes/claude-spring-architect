# Indexed Claude Code documentation

Portuguese version: [`docs/pt-br/claude-code-docs/`](../../pt-br/claude-code-docs/README.md).

A study index of the official Claude Code documentation, organized by the topics of the
[official Overview](https://code.claude.com/docs/en/overview). Each page in this directory
summarizes a block of topics with the rules, norms, best practices and inner workings, and
points to the official reference link of each page.

- **Source:** <https://code.claude.com/docs/en/overview> and the full index at
  <https://code.claude.com/docs/llms.txt> (209+ pages).
- **Snapshot:** 2026-09-27. The product changes weekly (see
  [What's new](https://code.claude.com/docs/en/whats-new/index)); confirm version details
  before publishing content based on these summaries.
- **Convention:** any official URL can be read as plain Markdown by appending `.md`
  (example: `https://code.claude.com/docs/en/skills.md`).

## Index by topic

| # | Page | Covers |
|---|------|--------|
| 00 | [Getting started and how it works](00-getting-started-and-how-it-works.md) | Overview, Quickstart, agentic loop, surfaces, `.claude/`, context window, prompt caching |
| 01 | [Rules: CLAUDE.md, rules and memory](01-rules-claude-md-and-memory.md) | `CLAUDE.md`, `.claude/rules/`, `AGENTS.md`, auto memory, `/memory` |
| 02 | [Skills](02-skills.md) | `SKILL.md`, frontmatter, invocation, arguments, `context: fork`, bundled skills |
| 03 | [Subagents and parallel work](03-subagents-and-parallelism.md) | Subagents, agent view, agent teams, dynamic workflows, worktrees, cross-session messages |
| 04 | [Hooks](04-hooks.md) | Lifecycle events, exit codes, decision JSON, matchers, hook types |
| 05 | [MCP](05-mcp.md) | Transports, scopes, tool search, authentication, security |
| 06 | [Plugins and marketplaces](06-plugins-and-marketplaces.md) | Plugin structure, installation, scopes, marketplaces, governance |
| 07 | [Settings, permissions and security](07-settings-permissions-and-security.md) | Settings files, precedence, permission rules, permission modes, sandboxing |
| 08 | [CLI, commands and tools](08-cli-commands-and-tools.md) | `claude` CLI, `/` commands, tool catalog, interactive mode, sessions, checkpoints |
| 09 | [Model, effort and output styles](09-model-effort-and-output-styles.md) | Model selection, effort, fast mode, extended thinking, output styles |
| 10 | [Best practices and workflows](10-best-practices-and-workflows.md) | Official best practices, common workflows, monorepos, prompt library |
| 11 | [Platforms and integrations](11-platforms-and-integrations.md) | Terminal, VS Code, JetBrains, Desktop, Web, Slack, Chrome, CI/CD |
| 12 | [Automation, headless and scheduling](12-automation-headless-and-scheduling.md) | `claude -p`, routines, `/loop`, `/goal`, channels, deep links |
| 13 | [Administration and enterprise](13-administration-and-enterprise.md) | Managed settings, deployment, gateways, costs, monitoring, data |
| 14 | [Agent SDK](14-agent-sdk.md) | TypeScript/Python SDK, agent loop, custom tools, permissions, deployment |
| 15 | [Troubleshooting and glossary](15-troubleshooting-and-glossary.md) | Errors, config diagnostics, glossary of official terms |

## The five points that hold up everything else

1. **Agentic loop:** the model repeats *gather context → take action → verify results* using
   tools, until the task ends or you interrupt. Documentation:
   [How Claude Code works](https://code.claude.com/docs/en/how-claude-code-works).
2. **The context window is the scarce resource:** everything you add (CLAUDE.md, skills, MCP,
   command output) competes for space, and quality drops as the window fills.
   Documentation: [Explore the context window](https://code.claude.com/docs/en/context-window).
3. **Instruction ≠ enforcement:** `CLAUDE.md`, rules, skills and output styles are *context*
   the model interprets. Only permissions, hooks and sandbox are enforced by the client,
   regardless of what the model decides. Documentation:
   [Extend Claude Code](https://code.claude.com/docs/en/features-overview).
4. **Configuration layers have a defined precedence:** managed > CLI > project local >
   project > user, with lists that add up instead of replacing. Documentation:
   [Settings files and precedence](https://code.claude.com/docs/en/settings).
5. **Verification closes the loop:** give Claude a test, build or script that produces
   pass/fail, otherwise "looks done" is the only signal available. Documentation:
   [Best practices](https://code.claude.com/docs/en/best-practices).

## How to choose the right primitive

| You want | Use | Deterministic? |
|-----------|-----|-----------------|
| Context that holds in every session | `CLAUDE.md` | No (heuristic) |
| Instruction that only applies to certain files | `.claude/rules/*.md` with `paths:` | No |
| Reusable procedure, loaded on demand | Skill (`SKILL.md`) | No |
| Heavy work without polluting the main context | Subagent | No |
| Connection to an external system | MCP server | No (but the tool is real) |
| Something that must happen **always** | Hook | Yes |
| Block a tool, command or path | Permission rule / sandbox | Yes |
| Package all of this for reuse | Plugin | — |

Table source: [Extend Claude Code](https://code.claude.com/docs/en/features-overview#match-features-to-your-goal).
