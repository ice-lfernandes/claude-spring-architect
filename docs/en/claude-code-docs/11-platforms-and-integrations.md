# 11 — Platforms and integrations

Official pages covered:

| Page | Link |
|--------|------|
| Platforms and integrations | <https://code.claude.com/docs/en/platforms> |
| Use Claude Code in VS Code | <https://code.claude.com/docs/en/vs-code> |
| JetBrains IDEs | <https://code.claude.com/docs/en/jetbrains> |
| Get started with the desktop app | <https://code.claude.com/docs/en/desktop-quickstart> |
| Desktop application | <https://code.claude.com/docs/en/desktop> |
| Claude Desktop on Linux (beta) | <https://code.claude.com/docs/en/desktop-linux> |
| Claude Code Desktop in WSL | <https://code.claude.com/docs/en/desktop-wsl> |
| Schedule recurring tasks in Desktop | <https://code.claude.com/docs/en/desktop-scheduled-tasks> |
| Test iOS apps in the simulator | <https://code.claude.com/docs/en/desktop-ios-simulator> |
| Get started with Claude Code in the cloud | <https://code.claude.com/docs/en/web-quickstart> |
| Use Claude Code in the cloud | <https://code.claude.com/docs/en/claude-code-on-the-web> |
| Continue local sessions with Remote Control | <https://code.claude.com/docs/en/remote-control> |
| Claude Code on mobile | <https://code.claude.com/docs/en/mobile> |
| Let Claude coordinate ongoing work with Projects | <https://code.claude.com/docs/en/claude-projects> |
| Use Claude Code with Chrome | <https://code.claude.com/docs/en/chrome> |
| Let Claude use your computer from the CLI | <https://code.claude.com/docs/en/computer-use> |
| Claude Code in Slack | <https://code.claude.com/docs/en/slack> |
| Claude Tag | <https://code.claude.com/docs/en/claude-tag> |
| Code Review | <https://code.claude.com/docs/en/code-review> |
| Find bugs with ultrareview | <https://code.claude.com/docs/en/ultrareview> |
| Claude Code GitHub Actions | <https://code.claude.com/docs/en/github-actions> |
| GitHub Actions with cloud providers | <https://code.claude.com/docs/en/github-actions-cloud-providers> |
| GitHub Enterprise Server | <https://code.claude.com/docs/en/github-enterprise-server> |
| Claude Code GitLab CI/CD | <https://code.claude.com/docs/en/gitlab-ci-cd> |
| Catch security issues as Claude writes code | <https://code.claude.com/docs/en/security-guidance> |
| Scan your codebase for vulnerabilities | <https://code.claude.com/docs/en/claude-security> |
| Configure cloud environments | <https://code.claude.com/docs/en/cloud-environments> |

---

## General rule

All surfaces share the same engine: the repository's `CLAUDE.md`, settings and MCP servers
apply everywhere. What changes is **where the code runs** and **how you interact**.

## Surfaces

| Surface | Characteristic |
|------------|----------------|
| **Terminal (CLI)** | Complete; the basis of everything. Supports third-party providers |
| **VS Code** | Inline diffs, @-mentions, plan review, conversation history in the editor |
| **JetBrains** | IntelliJ, PyCharm, WebStorm etc.; diff viewing and selection sharing. Requires the CLI installed |
| **Desktop (macOS/Windows/Linux beta)** | Parallel sessions with git isolation, panel layout, integrated terminal and editor, side chats, computer use, local scheduled tasks, iOS simulator |
| **Web (claude.ai/code)** | No local setup; long tasks, repos you do not have locally, parallel sessions |
| **Mobile (iOS/Android)** | Start, monitor and steer tasks from your phone |

Moving between surfaces:

- `claude --cloud` creates a cloud session; `claude --teleport` pulls a cloud session into
  the local terminal (requires a claude.ai subscription).
- `/desktop` continues the current session in the desktop app (macOS and Windows x64).
- **Remote Control** keeps execution and files local, but you control it from the browser or
  phone.
- **Dispatch** creates a desktop session from a message sent from the phone.

## Cloud

- **Web quickstart:** connect a GitHub repository, submit the task, review the PR.
- **Cloud environments:** define what loads in a cloud session. Critical rule: cloud
  sessions do **not** read `~/.claude/settings.json`, `.claude/settings.local.json`,
  personal skills in `~/.claude/skills/`, or plugins enabled only in your local settings.
  They read the committed `.claude/settings.json` (in a single-repository session) and
  server-managed settings.
- **Projects:** one continuous conversation where Claude coordinates parallel sessions
  (threads) that share repositories, instructions and memory.
- **Routines:** run in the cloud on a schedule, by API call, or reacting to GitHub events.
- **Ultrareview:** deep multi-agent review in a cloud sandbox (`/code-review ultra`;
  `/ultrareview` is a deprecated alias). It is user-triggered and billed; it needs a git
  repository.

## Browser and computer

- **Chrome:** test web apps, debug with console logs, automate form filling and extract
  data (`--chrome` / `/chrome`).
- **Computer use (macOS):** Claude opens apps, clicks, types and sees the screen — to test
  native apps, debug visual problems and automate GUIs.

## Chat

- **Claude Code in Slack:** delegates tasks from the workspace. Anthropic is retiring this
  version for Team and Enterprise workspaces in favor of **Claude Tag**; it remains the
  setup path on Pro.
- **Claude Tag:** brings Claude into Slack channels (`/install-slack-app`).

## Code review and CI/CD

| Tool | Use |
|-----------|-----|
| `/code-review` (`/review`) | Reviews the local diff in an isolated subagent; accepts PR, branch or path, `--fix`, `--comment` |
| Code Review (GitHub) | Automatic PR review with multi-agent analysis of the codebase |
| `security-guidance` (plugin) | Claude reviews its own changes for vulnerabilities and fixes them in the same session |
| Claude Security (plugin) | Scans the codebase and turns findings into patches for review |
| `/security-review` | Analyzes the current branch's changes |
| GitHub Actions | Responds to `@claude` mentions, automates tasks, turns issues into PRs |
| GitLab CI/CD | Equivalent integration on GitLab |

GitHub Actions can run through Amazon Bedrock, Google Cloud Agent Platform or Microsoft
Foundry instead of the Claude API, and there is a documented path for GitHub Enterprise
Server (cloud sessions, code review and plugin marketplaces).

## Dev containers

`devcontainer` is documented for isolated environments that are consistent across the team
(<https://code.claude.com/docs/en/devcontainer>).
