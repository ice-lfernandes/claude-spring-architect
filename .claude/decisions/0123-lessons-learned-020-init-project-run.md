# 0123 · Remediation of lessons-learned-020 — one interview up front, a `check` that takes a path, GENESIS figures from the transcript

- **Date:** 2026-10-06
- **Scenario:** `@.claude/lessons-learned/lessons-learned-020.md` — one `/init-project` run took
  54 min 49 s, patched three shipped defects, recorded a start after its finish, reported a
  boundary probe that never ran as verified, and handed back to the main session twice because
  the agent could not ask its questions.
- **Decision:** A1 + B1 + C1 + D1 + E1. Form 7c inside existing modes of `.claude/hooks/ArchHook.java` (`check <path>`, the `audit genesis` sub-mode) with its data in `audit.genesis` of `.claude/schemas/extensions.json`; template fixes; the interview moved into `.claude/skills/init-project/SKILL.md`; `project-initializer` loses `AskUserQuestion` and the `git-publish` chain. No new registration, no new skill or agent.
- **State:** approved by Lucas Fernandes, on 2026-10-06

## Reproduced on disk before classifying

Checked against `72064a7`.

| Claim | On disk |
|---|---|
| `logback-spring.xml.example` puts the XML declaration after a comment | Confirmed — `<?xml` at line 23 after a 22-line `<!-- -->`; every other `*.xml.example` has it at line 1 |
| The transport ITs carry no `@Import` of the Testcontainers configuration | Confirmed — `ForwardedHeadersIT.java.example`, `TransportSecurityIT.java.example`; `TransportTemplatesTest` builds an Initializr project with `web,actuator` only, so no datasource is ever in its context |
| `check` ignores argv and blocks on an open stdin | Confirmed — `ArchHook.java` `main`: `case "check" … -> readAll(System.in)`, then `check(filePath(stdin))`. Reproduced in `banking-app`: `(sleep 4) \| … check <path>` exits 0 silently; the stdin JSON form exits 2 |
| `verify-and-report.md` § 8's probe cannot be blocked by a hook | Confirmed — the session runs rooted at this repository; the generated project's `settings.json` never loads during bootstrap |
| `ROOT` is `CLAUDE_PROJECT_DIR`, else cwd | Confirmed, `ArchHook.java:41`. The Bash tool leaves `CLAUDE_PROJECT_DIR` unset, so a probe run from `(cd <project> && …)` resolves modules against the project |
| `{{startIso}}` is never captured | Confirmed — § 8.6 defines it as "interview's first question, step 1", and no step records it. The interview no longer runs in the agent |
| GENESIS forbids cost on purpose | Confirmed — § 8.6 "no cost"; `GENESIS.md.example` header "no cost breakdown" (`lessons-learned-004` Gap 1) |
| A deterministic transcript reader and pricer exist | Confirmed — `usage(Path)` dedupes by `requestId`; `auditUsd(dir, Usage)` prices from `pricing.json` and returns `null` on any unpriced model; `subagentTranscripts(transcript)` maps `subagents/*.meta.json` to their `.jsonl` |
| A background subagent has a reduced tool set | `@claude-help.md` § Subagents › Limits — "A background subagent has a smaller tool set than a foreground one"; the agent reported no `AskUserQuestion`. Agent frontmatter can force background (`background: true`), never foreground |
| `transport-security-setup` and `sonarqube-setup` skip a question already answered in their context | Confirmed — both § 2: "When a caller already passed an answer in its one-line context, don't ask it again" |
| `project-initializer` and `/init-project` both chain `git-publish` | Confirmed — agent § When finished, `init-project` step 4 |
| `${CLAUDE_SESSION_ID}` is substituted in a skill body | `@claude-help.md` § Skills › substitutions |

## Interview

Axes 1, 3, 8 and 9 come from the lessons-learned itself; the user answered the rest.

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | Observed in one run, every item reproduced (table above) | create nothing, for every group |
| 2 — Trigger | `/init-project`, typed | 1 for the interview (it stays in the Form 2 skill) |
| 7 — Mandatoriness | The figures and timestamps in GENESIS must never be model-written (invariant 8 extended); the interview and the batching can fail | 1–5 for the figures; 7/8 for the interview and batching |
| 8 — Destination | Templates, GENESIS, `ArchHook.java` travel; `init-project`, `project-initializer` and `project-bootstrap` do not | — |
| Scope (user) | All four groups: templates §§ 1–2, probe/`check` § 3, GENESIS §§ 4–5, single interview § 6 | — |
| Cost coverage (user) | The whole run — main session from `/init-project` plus every `project-initializer` run | agent-only figure |
| `check` with argv (user) | Accept the argv path and skip stdin | fail-loud, doc-only |
| Efficiency (user) | Parallel `Write`/`Read` in one response only; README lists from `export` deferred | Bash heredoc batching, a new `export` output |
| 17 — CI for the transport fix (user) | Prose + template comment, no CI variant — the 2–4 min, network and Docker flakiness of a JPA variant in `templates.yml` were weighed and declined | — |

## Options evaluated

One option per group is proposed; the rejected ones are recorded so nobody re-proposes them.

| # | Group | Option | Score | Verdict |
|---|---|---|---|---|
| A1 | Templates | Fix both templates + `XmlTemplatesTest` in `design` + transport prose | 8 | **Approved** |
| A2 | Templates | Fix both templates, no CI | 6 | Rejected — anti-pattern 21 for an offline-testable defect |
| B1 | `check` | argv path accepted, stdin skipped; § 8 probe through the jar; `BoundaryTest` case | 9 | **Approved** |
| B2 | `check` | Exit 2 with usage when argv has a path and no payload | 7 | Rejected — keeps the hang and the JSON pipe |
| B3 | `check` | A `CLAUDE.md` command row only | 4 | Rejected — anti-pattern 2 |
| C1 | GENESIS | New `audit genesis` sub-mode, run by `/init-project` before `git-publish`; agent leaves placeholders | 8 | **Approved** |
| C2 | GENESIS | The agent runs the pricer on its own transcript at step 8.6 | 4 | Rejected — cannot see the main session or its own last turns |
| C3 | GENESIS | `date -u` injection in `/init-project` for Started, no cost | 3 | Rejected — shell dependency, no cost |
| D1 | Interview | Whole interview in `/init-project`; agent loses `AskUserQuestion` and the `git-publish` chain | 8 | **Approved** |
| D2 | Interview | Force the agent to the foreground | 2 | Rejected — not buildable |
| D3 | Interview | Keep relaying hand-backs | 3 | Rejected — the observed failure |
| E1 | Turns | Parallel `Write`/`Read` instruction in `project-bootstrap` and the agent | 7 | **Approved** |

### A1 — templates fixed, XML parse test (score 8)

**Motivator:** axis 1, two shipped defects that every bootstrap hits.

**Change:** `logback-spring.xml.example` — declaration to line 1, the exemplar comment after it.
`transport-security-setup` step 5 and both IT exemplars' top comments: when
`src/test/java/**/TestcontainersConfiguration.java` exists, put
`@Import(TestcontainersConfiguration.class)` on **each** nested `@SpringBootTest` class —
`@NestedTestConfiguration(OVERRIDE)` drops the enclosing class's import. The import is not
written into the exemplars' code: `TransportTemplatesTest` compiles them in a project with no
such class.

**Pros:** fixes the cause in both places; the XML defect gets a guard that costs milliseconds.

**Cons:** the transport half stays persuasion, by the user's decision on axis 17.

**Points cut:** 4 Enforcement — the `@Import` rule is prose.

**CI:** new `.claude/.ci/XmlTemplatesTest.java` — parses every `*.xml.example` under
`.claude/skills/` with the JDK's DOM parser, no network; step in `validate` › `design`. Transport:
nothing, because the user declined the JPA variant of `TransportTemplatesTest` (2–4 min,
start.spring.io and Docker Hub on the path) — recorded here so the gap is a choice, not an
oversight.

### A2 — templates fixed, no CI (score 6)

Same edits, no test. Anti-pattern 21 for the XML defect, which is testable offline in
milliseconds. Cut: 4 Enforcement, 7 Propagation (CI answer missing).

### B1 — `check <path>` (score 9)

**Motivator:** axis 1 — 7 min 48 s lost, and a ✓ that no hook produced.

**Change:** `main`'s stdin switch: `check` reads stdin only when argv carries no path; `check`
dispatch takes `args[1]` when present. § 8 probe becomes one executable command,
`(cd "<project>" && java -jar .claude/hooks/ArchHook.jar check <domain-module>/src/main/java/<domain-package>/ArchHookProbe.java)`,
exit 2 expected, the jar being the bytes the project's hooks run. `CLAUDE.md` § Commands gets
the row. Javadoc of `check` carries the three sentences (Form 7c).

**Pros:** the hang disappears for anyone typing it; the probe proves the real hook instead of a
replayed regex; no new mode.

**Cons:** two input shapes for one mode.

**Points cut:** none material; 6 Precedent half-point — no other mode takes a path in argv
besides `export`.

**CI:** case in `.claude/.ci/BoundaryTest.java` (`validate` › `hooks-cross-platform` ›
existing step): the argv form with stdin left open must exit 2 within a timeout. Red on the
current jar: it hangs until the timeout.

### B2 — fail loud (score 7)

Exit 2 and usage text when argv has a path. Ends the silent pass, keeps the hang when stdin is
open (it still reads first), and the probe still needs a JSON pipe. Cut: 5 Maintenance, 4
Enforcement half.

### B3 — document only (score 4)

The hang and the silent pass stay. Cut: 1, 4, 9 — anti-pattern 2.

### C1 — `ArchHook.java audit genesis <project> <session-id>` (score 8)

**Motivator:** axis 7 — the figures and timestamps must come from a deterministic reader
(invariants 6 and 8).

**Change:**
- New sub-mode of `audit`, no stdin. Finds `<session-id>.jsonl` under
  `${CLAUDE_CONFIG_DIR:-~/.claude}/projects/*/`. **Started** = timestamp of the last user entry
  carrying the `/init-project` command; **Finished** = now; **tokens** = main-transcript turns
  since Started plus every subagent transcript whose `toolUseId` appears after Started, deduped
  by `requestId` through the existing `usage()`; **cost** = `auditUsd()` with
  `<project>/.claude/audit-usage/pricing.json`, "not configured (`<model>`)" when unpriced —
  never a partial sum.
- Replaces four placeholders in `<project>/.claude/audit-usage/GENESIS.md`. Refuses, naming why,
  when the file is missing, the placeholders are already gone (idempotent), the transcript is
  not found, or Finished < Started.
- The command name, the GENESIS path and the placeholder names live in an `audit.genesis` block
  of `extensions.json` (invariant 10).
- `/init-project` runs it after the agent returns and before `git-publish`, passing
  `${CLAUDE_SESSION_ID}`; `allowed-tools` gains that one `Bash(java …audit genesis:*)` prefix.
- § 8.6 and `GENESIS.md.example`: the agent writes the four placeholders literally; the header
  says the figures are read from the session transcripts by `audit genesis`, priced from
  `pricing.json`, and that there is still no per-tool-call timeline. This reverses § 8.6's "no
  cost" — the premise (nothing measured the run) no longer holds.

**Pros:** one source for every number; the window covers what the user pays, up to the
`git-publish` question; reuses the audit pricer; `audit` stays off here — it reads
transcripts and writes only into the generated project.

**Cons:** a new sub-mode to maintain; the transcript layout is the runtime's, not ours —
a change there breaks it (it already breaks `audit render` the same way); cache writes priced
at one rate, as every audit report is.

**Points cut:** 5 Maintenance (new sub-mode, new data block), 6 Precedent half (first mode run
from a skill against another project's file outside `export`).

**CI:** new `.claude/.ci/GenesisTest.java` in `validate` › `hooks-cross-platform`: a throwaway
`CLAUDE_CONFIG_DIR` with a main transcript, one subagent transcript and its `.meta.json`, a
`pricing.json` and a GENESIS with placeholders. Cases: values filled and exact; second run
refused; missing transcript refused; unpriced model prints "not configured".

### C2 — the agent prices itself at step 8.6 (score 4)

The agent does not see the main session's transcript, does not know its own transcript path,
and runs before its own last turns and the main session's relay — the figure is short by
construction. Cut: 1, 4, 7; violates the user's "whole run" answer.

### C3 — `date -u` injection, no cost (score 3)

`date` is a shell dependency on Windows (`@CLAUDE.md` § Dependencies), and it drops the cost the
user asked for. Cut: 2 (dependencies), 1, 4.

### D1 — whole interview in `/init-project` (score 8)

**Motivator:** axis 2 + the observed hand-backs — the interview is the heart of the main
session, and a background agent cannot ask.

**Change:**
- `init-project` asks everything before delegating: blueprint (all ids with `when_to_choose` in
  the question text; up to four as options, the rest by id under Other), build, features,
  artifactId; groupId and bounded context; transport topology and its follow-up; Sonar server
  and authentication. Arguments already given are not asked.
- `project-initializer`: `AskUserQuestion` leaves `tools`; `## Interview` (required by the
  `driver` class) says the interview is owned by `/init-project` and lists the answers it must
  receive; a missing answer stops with `blocked: missing <field>`, never a guess and never a
  question. It passes the transport and Sonar answers in the chained skills' one-line context.
  "When finished → git-publish" is removed: `/init-project` step 4 is the single caller.
- `agent_classes.driver.$comment` updated ("receives the interview's answers"); class, territory
  and `executor` unchanged.

**Pros:** one agent run, no resume re-reading a 199k context, no 23-minute question; the user
answers once and can leave. Removing the tool turns "never ask" into a restriction, not a
request.

**Cons:** `/init-project` (sonnet, `effort: low`) carries a longer interview; the transport
follow-up depends on the topology answer, so up to three `AskUserQuestion` calls.

**Points cut:** 5 Maintenance half (interview text moves between two files), 4 Enforcement half
(asking everything up front is still prose).

**CI:** `validate` › `design` › `frontmatter schema` already checks the agent's `tools`, `model`,
class and required `## Interview`; `AgentTerritoryTest` is unaffected (territory unchanged).
Nothing new: whether the skill asks before delegating is model behaviour.

### D2 — foreground agent (score 2)

Agent frontmatter can force background, never foreground; the harness decides. Not buildable.

### D3 — keep relaying (score 3)

The observed failure: three runs, paraphrased questions, a mid-run gap. Cut: 1, 4, 5.

### E1 — parallel tool calls (score 7)

**Change:** `project-bootstrap` (preamble and the `package-info.java` step) and the agent's
Principles: independent `Write`s (all `package-info.java`) and `Read`s (a step's exemplars) go
in one response. A Bash heredoc loop was rejected: `guard bash` may not read paths inside a
loop, one bad heredoc corrupts many files, and it skips the `Write` hooks.

**Points cut:** 4 Enforcement (persuasion), 6 Precedent half.

**CI:** nothing testable — turn count is model behaviour; `schema` covers the edited files.

## References

| Claim | Source |
|---|---|
| A background subagent has fewer tools; agents can force background only | `@claude-help.md` § Subagents › Limits, and the `background` frontmatter row |
| `${CLAUDE_SESSION_ID}` substitution in skills | `@claude-help.md` § Skills, substitutions table |
| Guarantees are hooks; the model writes no number from memory | `@CLAUDE.md` invariants 6, 8 |
| A mode's lists come from `extensions.json` | `@CLAUDE.md` invariant 10 |
| Zero shell dependency | `@CLAUDE.md` § Dependencies |
| Verbatim templates get CI | `@.claude/decisions/0099-ci-tests-for-verbatim-templates.md`, `0122` |
| Audit pricing never sums partially | `@.claude/decisions/0101-lessons-learned-018-unpriced-model.md` |
| A test spawns the jar | `@.claude/decisions/0084-ci-covers-jar-and-post-0075-guards.md` |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` + `ArchHook.jar` | `check <path>` (`checkPath`), no stdin read when argv carries a path; `audit genesis` sub-mode (`auditGenesis`), no stdin read; dispatch comments. Jar rebuilt, `build --verify` green |
| `.claude/schemas/extensions.json` | `audit.genesis` block (file, command, placeholders) and its `$comment_genesis`; `agent_classes.driver.$comment` — receives the interview's answers |
| `.claude/skills/init-project/SKILL.md` | Whole interview before delegating (step 2), answers passed by name (3), `audit genesis` with `${CLAUDE_SESSION_ID}` before `git-publish` (5), sole `git-publish` caller (6); `allowed-tools` gains the `audit genesis` prefix; `## Session` substitution; Contract owns the interview |
| `.claude/agents/project-initializer.md` | `AskUserQuestion` out of `tools`; description; Principle 1 rewritten, Principle 6 (parallel calls); Input lists every answer; `## Interview` now states the owner and the `blocked: missing <field>` stop; no `git-publish`, GENESIS placeholders left |
| `.claude/skills/project-bootstrap/SKILL.md` | Procedure preamble: parallel `Read`/`Write`; step 1 never asks; interview references point at `/init-project`; `git-publish` caller |
| `.claude/skills/project-bootstrap/references/verify-and-report.md` | § 8 probe as one executable `check <path>` command through the jar; § 8.6 four placeholders left literal, fidelity rule revised |
| `.claude/skills/project-bootstrap/templates/GENESIS.md.example` | Header says where the four figures come from; `Tokens` and `Cost` rows |
| `.claude/skills/project-bootstrap/templates/logback-spring.xml.example` | XML declaration on line 1 |
| `.claude/skills/transport-security-setup/SKILL.md` | Step 5: `@Import(TestcontainersConfiguration.class)` on each `@SpringBootTest` class when the project has one |
| `.claude/skills/transport-security-setup/templates/ForwardedHeadersIT.java.example`, `TransportSecurityIT.java.example` | `DATASOURCE` paragraph in the EXEMPLAR header |
| `.claude/.ci/BoundaryTest.java` | Rewritten as four cases: payload, argv with stdin open, clean file, missing file — 30 s timeout per case |
| `.claude/.ci/GenesisTest.java` | New |
| `.claude/.ci/XmlTemplatesTest.java` | New |
| `.github/workflows/validate.yml` | `hooks-cross-platform`: BoundaryTest step renamed and commented, `audit genesis …` step; `design`: `XML exemplars are well-formed` step |
| `CLAUDE.md` | Two § Commands rows: `check <path>`, `audit genesis` |
| `docs/{en,pt-br}/07-ci-validate.md` | Diagram nodes H2, H15, D10; three table rows; fourteen tests; local command list |
| `docs/{en,pt-br}/02-init-project.md` | Sequence diagram (interview in the main session, `audit genesis`, `git-publish` from the skill); example text; step 8.6 row |
| `docs/{en,pt-br}/01-file-types.md` / `01-tipos-de-arquivo.md` | `project-initializer` tools row |
| `README.md` | `/init-project` diagram — written in a later turn, at the user's explicit request, after `guard sweep` refused the first edit made under this skill's open phase |

Goes to the generated project: **yes, through step 6.6's `export`** — `ArchHook.java`, the
jar and `extensions.json` travel whole, so `check <path>` exists there; the fixed templates land
there through the bootstrap. `init-project`, `project-initializer`, `project-bootstrap` and the
`.claude/.ci/` tests do not travel (`export.skills.exclude`, `export.agents.exclude`, and
`.ci/` is never copied). No step was added to `ci.yml.example`: nothing here is a claim about the
project's own code that `verify` does not already fail on.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · hooks-cross-platform › hook blocks forbidden import, from the payload and from argv` | `check` blocks from the payload and from `check <path>` with stdin left open; a clean file exits 0, a missing one 1 | Green on the tree · red on the pre-change jar: the three argv cases "hung for 30 s waiting on stdin" |
| `validate · hooks-cross-platform › audit genesis fills GENESIS from the transcripts, once` | Started is the `/init-project` message; turns before it and a tool result quoting the tag do not count; tokens and USD exact; a filled record and an unknown session refused; an unpriced model named | Green · red twice: with the `t.t() < start` filter removed (tokens and cost cases), and with the string-content check removed (Started, tokens, cost, unpriced cases) |
| `validate · design › XML exemplars are well-formed` | Every `*.xml.example` parses; fragments under a synthetic root | Green after the fix · red on the pre-fix `logback-spring.xml.example`: "The processing instruction target matching "[xX][mM][lL]" is not allowed", line 23 |
| — | The transport `@Import` rule | Nothing: the user declined the JPA variant of `TransportTemplatesTest` (axis 17). A regression shows up only in a real bootstrap |
| — | The interview in `/init-project`, parallel calls | Nothing testable: model behaviour. `frontmatter schema` covers the agent's `tools` and required `## Interview` |

Also run before committing: `schema` (here and inside a scratch `export` target), `claude plugin
validate .claude/skills`, `doctor` (19 registrations, unchanged), `AuditRenderTest`,
`AgentTerritoryTest`, `SkillTerritoryTest`, `TemplateCommentsTest`, `InjectionPathTest`,
`SubagentContextTest` — all green. `audit genesis` also ran against this session's real
transcript in a scratch copy: Started `2026-10-06T08:55:27Z`, the `/init-project` message, which
the first version missed by matching a later tool result — the case the test now holds.
