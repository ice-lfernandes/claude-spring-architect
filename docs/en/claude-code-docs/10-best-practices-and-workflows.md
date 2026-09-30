# 10 — Best practices and workflows

Official pages covered:

| Page | Link |
|--------|------|
| Best practices for Claude Code | <https://code.claude.com/docs/en/best-practices> |
| Common workflows | <https://code.claude.com/docs/en/common-workflows> |
| Prompt library | <https://code.claude.com/docs/en/prompt-library> |
| Set up Claude Code in a monorepo or large codebase | <https://code.claude.com/docs/en/large-codebases> |
| Keep Claude working toward a goal | <https://code.claude.com/docs/en/goal> |
| Code Review | <https://code.claude.com/docs/en/code-review> |

---

## The constraint that explains almost every rule

> "Most best practices rest on one constraint: the context window fills up fast, and
> performance degrades as it fills."

A single debugging or exploration session can consume tens of thousands of tokens. When the
window is full, the model "forgets" older instructions and makes more mistakes.

## 1. Give Claude a way to verify its own work

Claude stops when the work **looks** done. Without an executable check, you become the
verification loop.

| Strategy | Before | After |
|------------|-------|--------|
| Verification criteria | "implement email validation" | "write `validateEmail`. Cases: `user@example.com` true, `invalid` false, `user@.com` false. Run the tests afterwards" |
| Visual verification | "improve the dashboard" | "[screenshot] implement this design, take a screenshot of the result, compare and list the differences" |
| Root cause, not symptom | "the build is broken" | "the build fails with [error]. Fix it and verify. Attack the root cause, do not suppress the error" |

Levels of rigor, lightest to strongest:

1. **In the prompt** — ask it to run the check and iterate in the same message.
2. **In the session** — `/goal <condition>`: a separate evaluator re-checks every turn.
3. **Deterministic gate** — a `Stop` hook that runs your script and blocks the end of the
   turn.
4. **Second opinion** — a verification subagent or dynamic workflow that tries to refute the
   result.

Ask for **evidence** (test output, command run, screenshot), not a claim of success.

## 2. Explore, plan, then code

1. **Explore** in plan mode (`Shift+Tab` until `⏸ plan mode on`, or
   `claude --permission-mode plan`): read files and answer without changing anything.
2. **Plan**: "which files change? what is the session flow? create a plan". `Ctrl+G` opens
   the plan in the editor.
3. **Implement**: approve the plan, let it code while verifying against the plan.
4. **Commit**: ask for a descriptive message and a PR.

Plan mode has overhead. Skip it when the scope is clear and the change is small — if you can
describe the diff in one sentence, do not plan.

## 3. Specific context in the prompt

| Strategy | Before | After |
|------------|-------|--------|
| Bound the task | "add tests for foo.py" | "write a test for foo.py covering the logged-out user case. Avoid mocks" |
| Point to the source | "why is this API weird?" | "look at ExecutionFactory's git history and summarize how the API got here" |
| Reference existing patterns | "add a calendar widget" | "see how the home widgets are built; HotDogWidget.php is a good example; follow the pattern" |
| Describe the symptom | "fix the login bug" | "login fails after session timeout; look at src/auth/, especially token refresh; write a test that reproduces it and then fix it" |

A vague prompt is useful when you are exploring ("what would you improve in this file?").

Ways to provide content: `@file`, pasting images, giving URLs (domain allowlist in
`/permissions`), `cat error.log | claude`, or letting Claude fetch on its own.

## 4. Set up the environment

- **`CLAUDE.md`** — `/init` generates it; refine with the test "would removing this make
  Claude err?".
- **Permissions** — pre-approve with `/permissions`, turn on the sandbox with `/sandbox`,
  use Manual mode when you want to approve everything.
- **CLIs** — the most context-efficient way to talk to external services (`gh`, `aws`,
  `gcloud`, `sentry-cli`). Trick: *"use `foo-cli --help` to learn the tool and then solve
  A, B, C"*.
- **MCP** — for what has no good CLI.
- **Hooks** — for what must always happen; ask Claude to write them.
- **Skills** — domain knowledge and on-demand workflows.
- **Subagents** — isolated work with restricted tools.
- **Plugins** — install code intelligence if you use a typed language.

## 5. Communicate like with a colleague

- Ask as you would ask a senior: "how does logging work?", "which edge cases does
  `CustomerOnboardingFlowImpl` handle?". It is an effective onboarding flow.
- For big features, **let Claude interview you**:

```text
I want to build [description]. Interview me in detail using the AskUserQuestion tool.
Ask about technical implementation, UI/UX, edge cases, concerns, and tradeoffs.
Keep interviewing until we've covered everything, then write a complete spec to SPEC.md.
```

Then open a fresh session to execute the spec. Good specs are self-contained: they name
files and interfaces, say what is out of scope, and end with an end-to-end verification.

## 6. Manage the session

- Correct early: `Esc` to interrupt, `Esc Esc`/`/rewind` to go back, "undo that", `/clear`
  between unrelated tasks.
- **After two failed corrections on the same point, `/clear` and rewrite the prompt** with
  what you learned. A clean session with a better prompt beats a long session with
  corrections.
- Delegate investigation to subagents to keep the research out of the main context.
- Use `/btw` for side questions that should not enter the history.
- `/compact <focus>`, or summarize from/up to a point through the rewind menu.
- Name sessions (`/rename`) and treat them like branches.

## 7. Automate and scale

- **Non-interactive:** `claude -p "prompt"`, with `--output-format json` or
  `stream-json --verbose` for parsing.
- **Parallel sessions:** worktrees, cross-session messaging, desktop app, cloud, agent view,
  agent teams.
- **Writer/Reviewer:** one session implements, another (fresh context) reviews — fresh
  context improves the review because there is no bias toward the code just written.
- **Fan-out:** `/batch <instruction>` splits into 5–30 subagents, each with its own
  worktree; or write your own loop:

```bash
for file in $(cat files.txt); do
  claude -p "Migrate $file from Python 2 to Python 3. Return OK or FAIL." \
    --allowedTools "Edit,Bash(git commit *)"
done
```

- **Auto mode for continuous execution:** `claude --permission-mode auto -p "fix all lint errors"`.
- **Adversarial review before calling it done:** `/code-review` (a fresh subagent over the
  diff), or your own prompt comparing the diff against `PLAN.md`. Tell it to report only
  correctness/requirement gaps — a reviewer asked to find problems always finds some, and
  chasing all of them leads to over-engineering.

## Common antipatterns

| Antipattern | Symptom | Fix |
|-----------|---------|----------|
| "Kitchen sink" session | Unrelated tasks in the same conversation | `/clear` between tasks |
| Correcting non-stop | Context polluted by failed attempts | After 2 corrections, `/clear` + better prompt |
| Bloated `CLAUDE.md` | Important rules get lost | Prune without mercy; turn what is verifiable into a hook |
| Trusting without verifying | Plausible implementation that misses edge cases | Always provide verification |
| Endless exploration | "investigate X" with no scope, hundreds of files read | Bound it or use subagents |

## Monorepos and large codebases

- **Where you start matters:** at the root, Claude has access to everything and loads only
  the root `CLAUDE.md` (subdirectory ones load on demand); in a subdirectory, access is to
  that subtree and the local `CLAUDE.md` **and those of all ancestors** load. Project
  settings are **not** inherited from parent directories the way `CLAUDE.md` files are.
- **`CLAUDE.md` layers:** root with what holds everywhere; one per package/area with local
  conventions. Each owner maintains theirs; review in PRs.
- **`CLAUDE.md` per directory vs. rule with `paths:`**: the former sits next to the code and
  loads when you start there; the latter sits centrally in `.claude/rules/` and loads when a
  file matches the glob.
- **Reduce what Claude reads:** block reading of generated and vendored code with deny
  rules, and install code intelligence to swap broad reads for symbol lookup.
- **Worktrees with partial checkout** and explicit access grants across packages.
- **Skills per directory** for each area's conventions, keeping them discoverable.

## `common-workflows` recipes

Understand a new codebase, fix bugs, refactor, work with tests, create pull requests, handle
documentation, work in non-code folders, work with images, reference files and directories,
run on a schedule, resume conversations, parallel sessions with worktrees, plan before
editing, delegate research to subagents, and pipe Claude into scripts.

## Develop intuition

The patterns are starting points, not dogma. Sometimes letting context accumulate is right
(you are deep in a single problem); sometimes skipping the plan is right (an exploratory
task); sometimes a vague prompt is exactly what you want.
