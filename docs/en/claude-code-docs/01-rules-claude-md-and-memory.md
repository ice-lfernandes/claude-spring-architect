# 01 — Rules: CLAUDE.md, `.claude/rules/` and memory

Official pages covered:

| Page | Link |
|--------|------|
| How Claude remembers your project | <https://code.claude.com/docs/en/memory> |
| Extend Claude Code (comparisons) | <https://code.claude.com/docs/en/features-overview> |
| Best practices — writing an effective CLAUDE.md | <https://code.claude.com/docs/en/best-practices> |
| Monorepos and large repositories | <https://code.claude.com/docs/en/large-codebases> |
| Debug your configuration | <https://code.claude.com/docs/en/debug-your-config> |

---

## Two memory mechanisms

| | `CLAUDE.md` | Auto memory |
|---|---|---|
| Who writes it | You | Claude |
| Content | Instructions and rules | Learnings and patterns |
| Scope | Project, user or organization | Per repository, shared across worktrees |
| Loaded | Every session | Every session (200 lines or 25 KB of `MEMORY.md`) |

Core rule: **both are context, not enforced configuration**. To block an action regardless
of what the model decides, use a `PreToolUse` hook.

## Where to put `CLAUDE.md`

Load order, from the broadest scope to the most specific:

| Scope | Location | Shared with |
|--------|-------|-------------------|
| Managed policy | macOS `/Library/Application Support/ClaudeCode/CLAUDE.md`; Linux/WSL `/etc/claude-code/CLAUDE.md`; Windows `C:\Program Files\ClaudeCode\CLAUDE.md` | Everyone in the organization |
| User | `~/.claude/CLAUDE.md` | Only you, all projects |
| Project | `./CLAUDE.md` or `./.claude/CLAUDE.md` | The team, via version control |
| Local | `./CLAUDE.local.md` (gitignored) | Only you, in this project |

### How they load

- `CLAUDE.md` and `CLAUDE.local.md` from the current directory **and every directory above
  it** are loaded at launch. They are all concatenated (none overrides another), from the
  filesystem root down to the working directory — so the one closest to where you launched
  is read last. Within each directory, `CLAUDE.local.md` comes after `CLAUDE.md`.
- Files in subdirectories below the working directory load on demand, when Claude reads
  files in that folder.
- Block-level HTML comments (`<!-- note -->`) are stripped before injection into the
  context — useful for maintenance notes at no token cost.
- `--add-dir` does **not** load the added folder's `CLAUDE.md`, unless
  `CLAUDE_CODE_ADDITIONAL_DIRECTORIES_CLAUDE_MD=1`.

## Norms for writing an effective `CLAUDE.md`

- **Size:** aim for under 200 lines. Larger files consume context and reduce adherence.
  Files above 4 MiB are ignored entirely.
- **Structure:** headers and bullets; organized sections work better than paragraphs.
- **Specificity:** "Use 2-space indentation", not "format nicely"; "Run `npm test` before
  committing", not "test your changes".
- **Consistency:** contradictory rules make the model pick one arbitrarily. Periodically
  review `CLAUDE.md`, the nested ones and `.claude/rules/`.
- **Pruning test:** for each line, ask "would removing this make Claude make mistakes?". If
  not, cut it. A bloated `CLAUDE.md` makes the model ignore the rules that matter.
- **Emphasis:** if an instruction is always ignored, mark only that one with "IMPORTANT".
  Emphasizing many lines cancels the effect.
- Treat the file like code: commit it, review it when something goes wrong, prune it and
  test it.

### Include vs. exclude

| Include | Exclude |
|---------|---------|
| Bash commands Claude can't guess | What it discovers by reading the code |
| Style that differs from the language default | Standard language conventions |
| Test instructions and preferred runner | Detailed API documentation (link it) |
| Repository etiquette (branch, PR) | Information that changes frequently |
| The project's architectural decisions | Long explanations and tutorials |
| Environment quirks (env vars) | File-by-file descriptions |
| Gotchas and non-obvious behavior | Truisms like "write clean code" |

## Imports with `@path`

- Syntax `@path/file`; relative paths resolve against the file that imports.
- Recursive, maximum depth of 4 hops.
- Imports inside code spans/blocks are ignored: `` `@README` `` is literal.
- Imports improve organization, they do **not** reduce context: the imported file is loaded
  at launch.
- An external import (outside the working directory) in a project file opens an approval
  dialog the first time. User-scope files (`~/.claude/...`) are trusted without a dialog,
  except in Cowork sessions on desktop.

## `.claude/rules/`

Instructions split by topic, optionally tied to paths:

```
.claude/
├── CLAUDE.md
└── rules/
    ├── code-style.md
    ├── testing.md
    └── security.md
```

- `.md` files are discovered recursively (`rules/frontend/react.md` works).
- Without a `paths` frontmatter, the rule loads at launch, with the same priority as
  `.claude/CLAUDE.md`.
- With `paths`, it loads only when Claude reads a file matching the glob:

```markdown
---
paths:
  - "src/api/**/*.ts"
  - "tests/**/*.test.ts"
---

# API Development Rules
- Every endpoint validates input
- Standard error response format
```

- `paths` is the **only** field Claude Code reads from a rule; others are ignored without
  error. Invalid YAML makes the rule load as if it had no `paths` (`claude --debug` shows the
  parse error).
- Brace expansion (`{ts,tsx}`) has a budget of 1,000 expanded patterns and 4 MiB per rule.
- `[` starts a bracket expression; for a literal, escape it: `photos \[2024/**`.
- Symlinks are supported (including circular ones, handled safely). A symlink pointing
  outside the working directory follows the external import rule. Network paths (UNC
  `\\server\share`, `/net`, `/Network`) are not followed.
- User rules in `~/.claude/rules/` apply to all projects and load before the project ones;
  neither overrides the other, so keep them consistent.

## `AGENTS.md`

Since v2.1.277 Claude Code reads `AGENTS.md` directly:

| The repository has | Claude reads |
|-----------------|-----------|
| `AGENTS.md`, no `CLAUDE.md`/`CLAUDE.local.md` on the path | `AGENTS.md` |
| Both | Only the `CLAUDE.md` files |
| A `CLAUDE.md` that imports `AGENTS.md` | `CLAUDE.md`, with the import expanded |

To change it, `/config` → **Project instructions**: `claude-md-or-agents-md` (default),
`claude-md-and-agents-md`, `claude-md`, `managed-only`. Also configurable via the built-in
`agents-md@builtin` plugin's `pluginConfigs` (ignored in project/local settings).

Differences: `InstructionsLoaded` hooks do not fire for an `AGENTS.md` read through the
configuration; `--add-dir` does not load `AGENTS.md`; `AGENTS.local.md`,
`AGENTS.override.md` and `.agents/` are never read.

To share one file across tools, prefer `@AGENTS.md` inside a `CLAUDE.md` over a symlink (on
Windows, a committed symlink becomes a one-line text file without `core.symlinks`).

## Auto memory

Claude saves four kinds of note, marked in the frontmatter (`type`):

- `user` — your role, expertise, working preferences
- `feedback` — corrections you gave and approaches confirmed
- `project` — ongoing work, deadlines, decisions not derivable from the code
- `reference` — where to find information outside the project

It **skips** what can be derived from the codebase (architecture, paths, fixes) and what
`CLAUDE.md` already says.

- Location: `~/.claude/projects/<project>/memory/`, with `MEMORY.md` (index) and one file per
  topic. Derived from the git repository, so all worktrees share it.
- Only the first 200 lines (or 25 KB) of `MEMORY.md` enter the context; topic files are read
  on demand.
- Configurable: `autoMemoryEnabled: false`, `autoMemoryDirectory`, env
  `CLAUDE_CODE_DISABLE_AUTO_MEMORY=1`, toggle in `/memory`.
- It is local to the machine; it is not synced across machines or to cloud sessions.
- Excluded from the retention sweep (`cleanupPeriodDays`) that deletes old transcripts.
- Subagents can have their own memory through the `memory` frontmatter field.

## Scale: organization and monorepo

- A managed `CLAUDE.md` cannot be excluded by individual settings; it can also be embedded
  in the `claudeMd` key of `managed-settings.json`.
- Use managed **settings** for technical enforcement (tool deny, sandbox, env, login) and
  managed **CLAUDE.md** for behavioral guidance.
- `claudeMdExcludes` (globs against absolute paths) skips other teams' `CLAUDE.md` in
  monorepos; arrays add up across layers.

## Troubleshooting

- **"Claude doesn't follow my CLAUDE.md":** the content enters as a user message after the
  system prompt — there is no guarantee of compliance. Check `/context` → **Memory files**,
  make the instruction more specific, eliminate conflicts. If it must always happen, make it
  a hook.
- **Instruction lost after `/compact`:** the root `CLAUDE.md` is re-read from disk and
  re-injected. Nested ones and rules with `paths:` come back when Claude reads a matching
  file. What only existed in the conversation is lost.
- **File too large:** warning at startup and in `/status`; `/doctor` proposes cuts for a
  committed `CLAUDE.md` (removes what is derivable from the code and keeps gotchas and
  conventions).
- The `InstructionsLoaded` hook is useful to log which files loaded, when and why.
