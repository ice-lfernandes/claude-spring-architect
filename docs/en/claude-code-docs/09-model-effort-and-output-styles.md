# 09 — Model, effort and output style

Official pages covered:

| Page | Link |
|--------|------|
| Model configuration | <https://code.claude.com/docs/en/model-config> |
| Speed up responses with fast mode | <https://code.claude.com/docs/en/fast-mode> |
| Escalate hard decisions with the advisor tool | <https://code.claude.com/docs/en/advisor> |
| Output styles | <https://code.claude.com/docs/en/output-styles> |
| How Claude Code uses prompt caching | <https://code.claude.com/docs/en/prompt-caching> |

---

## Model aliases

| Alias | Behavior |
|-------|----------|
| `default` | Clears any override and returns to the account default |
| `best` | The `fable` alias model when available; otherwise the same as `opus` |
| `fable` | The provider's Fable model, for harder and longer tasks |
| `sonnet` | Latest Sonnet, for day-to-day coding |
| `opus` | Latest Opus, for complex reasoning |
| `haiku` | Fast and cheap, for simple tasks |
| `sonnet[1m]` / `opus[1m]` | 1-million-token context window |
| `opusplan` | Opus during plan mode, Sonnet during execution |

Aliases point to the recommended version **per provider** and change over time (on the
Anthropic API, `opus` → Opus 5.5 and `sonnet` → Sonnet 5 in this documentation's snapshot).
To pin, use the full name (`claude-opus-5-5`).

`ANTHROPIC_BASE_URL` changes **where** requests go, not which model answers.

Configuration: `/model` (saves as default; `s` applies to the session only), `--model`, the
`model` key in settings, `ANTHROPIC_MODEL`. Each model has its own prompt cache — switching
models rereads the whole conversation with no cache.

Other features: `availableModels` (organizational allowlist), `modelPicker`,
`modelOverrides`, `fallbackModel` chains and automatic fallback on overload, custom model
IDs, and auto-compaction window tuning (`/autocompact`, `--autocompact`).

## Effort level

Controls adaptive reasoning: how much the model thinks before acting.

| Model | Levels |
|--------|--------|
| Fable 5.1 and Fable 5 | `low`, `medium`, `high`, `xhigh`, `max` |
| Opus 5.5, Opus 5, Sonnet 5, Opus 4.8, Opus 4.7 | `low`, `medium`, `high`, `xhigh`, `max` |
| Opus 4.6, Sonnet 4.6 | `low`, `medium`, `high`, `max` |

If you ask for an unsupported level, Claude Code falls back to the highest supported level
below it. Resolution order: explicit choice (`CLAUDE_CODE_EFFORT_LEVEL`, `--effort`,
`/effort`) → your settings (`modelSettings`/`effortLevel`) → the model default (`high` for
most; `medium` on Opus 5.5; `xhigh` on Opus 4.7).

In `/effort` and `/model`: `Enter` saves as the model default, `s` applies to the session
only. `max` lasts only for the session, unless it comes from the environment variable.

**Ultracode** is not a model level: it is a setting that sends `xhigh` and adds Claude
Code's own behavior. Turn it on with `/effort ultracode`, `--effort ultracode`,
`"ultracode": true`, or the `/model` slider.

## Extended thinking

The reasoning the model emits before answering. On models with adaptive reasoning, effort
level is the main control.

| Control | How |
|----------|------|
| Toggle in the session | `Option+T` (macOS) / `Alt+T` (Windows, Linux) |
| Global default | `/config` → thinking mode (`alwaysThinkingEnabled`) |
| Turn off via env | `MAX_THINKING_TOKENS=0` |

Thinking cannot be turned off on Opus 5.5 or on the Fable models. Output is collapsed by
default; `Ctrl+O` shows the reasoning in gray italics.

## Fast mode and advisor

- **Fast mode** (`/fast`, `--fast` depending on availability) speeds up Claude Opus output
  without switching to a smaller model. It invalidates the prompt cache when turned on.
- **Advisor tool** (`/advisor <model|off>`, `--advisor`) consults a second model on hard
  decisions.

## Output styles

An output style defines the response **role, tone and format** for the whole session. It is
instruction, not guarantee.

| Style | What changes | Use when |
|--------|------------|-----------|
| Default | No extra instruction; standard engineering prompt | General case |
| Proactive | Starts working right away and takes routine decisions | You want fewer questions about trivia |
| Concise | Answers lead with the result, no preamble or recap | Default answers are too long |
| Explanatory | Adds `Insight` blocks explaining the choices | Learning the codebase |
| Learning | `Insight` + leaves `TODO(human)` snippets for you to write | You want to practice while the task moves |

Notes:

- Proactive does **not** change the permission mode: permission prompts still follow the
  mode.
- Concise keeps full text where safety demands it: error reports, failing test output,
  security warnings and destructive confirmations.
- Switching style takes effect from the next message.

How to switch: `/output-style <style>`, `/config` → **Output style**, the VS Code extension
menu, or the `outputStyle` key in a settings file (**case-sensitive**: `Proactive`,
`Concise`, `Explanatory`, `Learning`). In `~/.claude/settings.json` it becomes the global
default.

A custom style is a Markdown file with metadata frontmatter and the instructions right
below it, kept in `output-styles/` (also distributable by plugin).

### Output style vs. other features

| You need | Use |
|---------|-----|
| What Claude should **know** about the project | `CLAUDE.md` |
| **How** it answers (tone, length, format, role) | Output style |
| Something that happens **always** (format, block) | Hook |
| Knowledge or procedure on demand | Skill |

`CLAUDE.md` and output style combine: the former stays loaded whatever the style, and
Claude follows both as instructions — neither is enforcement.
