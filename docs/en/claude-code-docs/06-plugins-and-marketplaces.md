# 06 — Plugins and marketplaces

Official pages covered:

| Page | Link |
|--------|------|
| Plugins overview | <https://code.claude.com/docs/en/plugins/overview> |
| Install and manage plugins | <https://code.claude.com/docs/en/plugins/install> |
| Anthropic's marketplaces | <https://code.claude.com/docs/en/plugins/anthropic-marketplaces> |
| Code intelligence plugins | <https://code.claude.com/docs/en/plugins/code-intelligence> |
| Plugin security and trust | <https://code.claude.com/docs/en/plugins/security> |
| Create a Claude Code plugin | <https://code.claude.com/docs/en/plugins/create> |
| Add components to a plugin | <https://code.claude.com/docs/en/plugins/components> |
| Plugin dependencies | <https://code.claude.com/docs/en/plugins/dependencies> |
| Test plugins with evals | <https://code.claude.com/docs/en/plugin-evals> |
| Publish and distribute a plugin | <https://code.claude.com/docs/en/plugins/publish> |
| Measure plugin cost and usage | <https://code.claude.com/docs/en/plugins/measure> |
| Create a marketplace | <https://code.claude.com/docs/en/plugins/create-marketplace> |
| Host and maintain a marketplace | <https://code.claude.com/docs/en/plugins/host-marketplace> |
| Manage plugins for your organization | <https://code.claude.com/docs/en/plugins/org> |
| Plugin loading reference | <https://code.claude.com/docs/en/plugins/loading> |
| Plugin manifest reference | <https://code.claude.com/docs/en/plugins/manifest-reference> |
| Marketplace reference | <https://code.claude.com/docs/en/plugins/marketplace-reference> |
| Plugin commands reference | <https://code.claude.com/docs/en/plugins/cli-reference> |
| Troubleshoot plugins | <https://code.claude.com/docs/en/plugins/troubleshooting> |

---

## What a plugin is

A directory of components that Claude Code installs and loads as a unit, usually with a
manifest at `.claude-plugin/plugin.json` (name required; version, description and metadata
optional). Possible components:

- **Skills** (`skills/<name>/SKILL.md`) — namespaced as `/plugin:skill`
- **Agents** (`agents/*.md`) — delegable subagents
- **Hooks** (`hooks/hooks.json`) — commands on lifecycle events
- **MCP servers** — servers connected while the plugin is active

## When you need a plugin

Skills, subagents, hooks and MCP servers work on their own. A plugin makes sense when:

- you want **several** of those components packaged as one unit;
- you want to install a setup someone else built, with one command and updates through a
  marketplace;
- you need the same setup across several repositories.

## What an enabled plugin adds to every session

- **Context and usage:** the name and description of every skill/agent/command Claude can
  invoke on its own enter the context on every request.
- **Processes:** the plugin's MCP servers run alongside each session and its hooks fire.
- **Permissions:** whatever the plugin runs, it runs **as you**.

Where to measure: before installing, the **Marketplaces** tab in `/plugin` shows a *Context
cost* estimate (official-marketplace plugins); afterwards, see
[Measure what a plugin costs](https://code.claude.com/docs/en/plugins/measure) and the **Not
used recently** group in the **Installed** tab. To stop without uninstalling: `/plugin` or
`claude plugin disable`.

## Marketplaces

A marketplace is a repository or directory with `.claude-plugin/marketplace.json` listing
plugins and where to fetch each from — it is a catalog, not a hosted store.

Claude Code adds Anthropic's official marketplace on the first interactive terminal session,
unless managed policy says otherwise.

Three tiers, and official/community names are only accepted for marketplaces coming from
`github.com/anthropics/` repositories:

- **Official** — official names, including `claude-plugins-official`
- **Community** — community names, such as `claude-community`
- **Third-party** — everything else, including your colleague's or your company's

Regardless of tier, an installed plugin can execute code with your privileges. Read
[Plugin security and trust](https://code.claude.com/docs/en/plugins/security) first.

```
/plugin                                   # opens the manager
/plugin marketplace add anthropics/claude-plugins-official
/plugin install mcp-server-dev@claude-plugins-official
/plugin uninstall <plugin>@<marketplace>
/reload-plugins [--force]                 # applies changes without restarting
```

## Installation scopes

- **User** — enabled for you in every project on the machine
- **Project** — enabled for everyone on the repository, via the committed
  `.claude/settings.json` (each person still installs on their own machine)
- **Local** — only for you, only in this repository

The terminal, local desktop app sessions and the VS Code extension share the same user
settings. **Cloud sessions do not load plugins from your local settings.**

## Three layers for a plugin to work

1. **Settings** — marketplaces added and plugins enabled
2. **Disk** — `~/.claude/plugins/` holds what was downloaded
3. **Session** — plugins load at startup or via `/reload-plugins`

`/plugin` shows which stage a plugin stopped at; the loading reference details the rules for
each layer.

## Organizational governance

Through managed settings, an organization can: allow or block marketplaces, force plugin
installation, turn off session-only loading and restrict customization to plugins
(`strictPluginOnlyCustomization`). See
[Manage plugins for your organization](https://code.claude.com/docs/en/plugins/org).

## Development

- During development you do not need a marketplace: load the plugin locally.
- A skill directory with `.claude-plugin/plugin.json` loads as the plugin
  `<name>@skills-dir`, and can then package agents, hooks and MCP servers. In a project's
  `.claude/skills/`, it requires accepting the workspace trust dialog.
- Plugin subagents **ignore** `hooks`, `mcpServers` and `permissionMode`.
- `claude plugin eval` runs eval suites to test the plugin (JSON/report, sandbox, CI);
  `/skill-doctor` shows the context cost and trigger rate of skills.
- Useful variables inside a plugin: `${CLAUDE_PLUGIN_ROOT}` (installation directory) and
  `${CLAUDE_PLUGIN_DATA}` (persistent directory that survives updates).
