# 0083 · The allow list names commands that exist; `omitClaudeMd` on the installers; no `maxTurns`, no agent `memory`

- **Date:** 2026-09-29
- **Scenario:** lessons-learned-015 topic N — "Recursos do ecossistema não usados": `omitClaudeMd` on the installers, `maxTurns` on the four agents, `memory: project` on `java-spring-boot-developer`, `/fewer-permission-prompts`, and an allow list that reflects real commands
- **Decision:** option 1 — allow lists name real read-only `ArchHook.java` commands; `omitClaudeMd: true` on both installers; `maxTurns` and `memory` not written
- **State:** approved by Lucas Fernandes, 2026-09-29
- **Goes to the generated project:** yes — `project-bootstrap/templates/settings.json.example` and the two installer agents travel

## Interview

Answered from the runtime docs, the agents' bodies and the two settings files; no axis
needed a question.

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | **Allow list: observed.** This repo's `permissions.allow` pre-approves `./mvnw`, `mvnw.cmd`, `./gradlew` — none exists here (`@CLAUDE.md` § Known pitfalls: "This repository does not run `./mvnw`") — and nothing for `ArchHook.java`, so every manual run of the documented commands (`doctor`, `schema`, `compose`, `build --verify`) prompts. **`maxTurns`: not observed** — no run of any of the four agents has looped. **`memory`: not observed** — no case where a feature lost a project fact the spec or a rule should have carried | `maxTurns`, `memory` (mirror of invariant 6: a guard against a failure nobody observed) |
| 1b — Duplicate load | Both installers **Read** the project's root `CLAUDE.md` as a declared input (`archunit-installer.md:48`, `commons-logging-installer.md:46`, `:90`) to resolve the `commons` package. As custom subagents they also get it auto-loaded (`claude-code-docs/03-subagents-e-paralelismo.md:59`). The file enters their context twice | — |
| 5 — Nature | Frontmatter field per agent; JSON lines in two settings files | New pieces of any kind |
| 7 — Mandatoriness | A permission line is Form 8 — this record is mandatory | — |
| 8 — Destination | Both: the template's allow keeps `./mvnw` (the wrapper exists there) and gains the same read-only `ArchHook.java` modes; the installers travel via `export.agents.include` | — |
| 9 — Integration | `memory: project` makes the agent write `.claude/agent-memory/<name>/`, outside `java-spring-boot-developer`'s `agent_classes` `write_allow` — `guard` would block it. It would also be a second, unreviewed owner of project norms beside `rules/` (invariant 2). `maxTurns` truncates silently: an executor cut mid-feature leaves half-written code and a spec still `approved`; an installer cut mid-POM leaves a build that does not parse | `memory`, `maxTurns` |
| 10 — Cost of error | A blanket `Bash(java .claude/hooks/ArchHook.java:*)` pre-approves `export <dest>` (writes and **deletes** under `<dest>/.claude/`, `export.retired`) and `build` (rewrites the committed jar). Only read-only modes go in | The blanket prefix |
| 11 — CLI | `/fewer-permission-prompts` is a built-in the user runs; it writes no file of this repo | Nothing to write for it |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Allow list fixed in both files (read-only modes only) **plus** `omitClaudeMd: true` on the two installers; `maxTurns` and `memory` rejected here with reasons | 8 | **approved** |
| 2 | Allow list fix only | 7 | not chosen — leaves the duplicate load |
| 3 | The lesson as written: all five items, blanket `ArchHook.java` prefix | 3 | rejected |
| 4 | Create nothing | 3 | rejected |

### Option 1 (score 8)

**Motivator:** axes 1 (allow list), 1b (duplicate load), 10 (read-only scope).

**Mechanism.**

1. `.claude/settings.json` `permissions.allow`: drop `Bash(./mvnw:*)`, `Bash(mvnw.cmd:*)`,
   `Bash(./gradlew:*)`; add
   - `Bash(java .claude/hooks/ArchHook.java doctor)`
   - `Bash(java .claude/hooks/ArchHook.java schema)`
   - `Bash(java .claude/hooks/ArchHook.java compose)`
   - `Bash(java .claude/hooks/ArchHook.java compose gate)`
   - `Bash(java .claude/hooks/ArchHook.java build --verify)`

   Exact commands, not `:*` prefixes: `build:*` would also admit `build` (rewrites the jar),
   and no read-only mode takes a free argument. `guard`, `context`, `audit` are read from
   stdin by the hooks themselves and are not run by hand often enough to earn a line.
2. `project-bootstrap/templates/settings.json.example` `permissions.allow`: keep
   `./mvnw`/`mvnw.cmd`/`./gradlew` (the wrapper exists there), add the same five lines plus
   `Bash(java .claude/hooks/ArchHook.java audit summary)` — `audit-usage`'s aggregation,
   live only in the generated project.
3. `archunit-installer.md`, `commons-logging-installer.md`: `omitClaudeMd: true`. Procedure
   step that resolves the `commons` package says **Read** the root `CLAUDE.md` explicitly
   (`commons-logging-installer` already does at step 2; `archunit-installer` step 6a says
   "the root `CLAUDE.md` names it" and gains the verb). One line in each `## Why this is an
   agent` section.
4. `extensions.json`: nothing — `omitClaudeMd` is already in the agent field list.
5. `schema </dev/null` exits 0.

**Pros:** the allow list tells the truth; the documented commands stop prompting; nothing
that writes or deletes is pre-approved; each installer spawn stops paying for the root
`CLAUDE.md` twice.

**Cons:** the `omitClaudeMd` gain is small — the installers run once per project. Five
literal lines per file drift if a mode is renamed (`schema` does not check allow entries
against the dispatch).

**Points cut:** small gain for the field (−1); literal lines unchecked (−1).

### Option 2 (score 7)

Same as option 1 without step 3. Leaves the duplicate load; nothing else lost.

### Option 3 (score 3)

- `maxTurns` on four agents against a loop never observed (invariant 6 mirror), and its
  failure is a silent half-write.
- `memory: project` blocked by the agent's own territory, and a second owner of norms
  (invariant 2) that never travels reviewed.
- The blanket prefix pre-approves a mode that deletes files.

### Option 4 (score 3)

Keeps three dead allow lines and a prompt on every documented manual command.

## Not written, and why

| Item | Why |
|---|---|
| `maxTurns` | No observed loop; truncation is silent and leaves partial writes. Revisit with an `audit` trail showing a runaway |
| `memory: project` on `java-spring-boot-developer` | Writes outside its `write_allow`; competes with `rules/` as owner of project facts; unreviewed, so not self-contained in the invariant-9 sense. A recurring project fact belongs in the project's `CLAUDE.md` or a rule |
| `/fewer-permission-prompts` | A user command, run in the generated project after real use; nothing to write here |

## References

| Claim | Source |
|---|---|
| Custom subagents load `CLAUDE.md` unless `omitClaudeMd: true` | `.claude/claude-code-docs/03-subagents-e-paralelismo.md:58`–`:59` |
| `maxTurns`, `memory`, `omitClaudeMd` are valid agent fields | same, `:99`–`:100`; `.claude/schemas/extensions.json` agent field list |
| Installers Read the root `CLAUDE.md` | `archunit-installer.md:48`, `:127`; `commons-logging-installer.md:46`, `:90` |
| Current allow lists | `.claude/settings.json`; `project-bootstrap/templates/settings.json.example` |
| `export` deletes under the target | `.claude/decisions/0082-rules-without-paths-load-at-launch.md` § Found while implementing |
| No guard for an unobserved failure; one owner per norm | `@CLAUDE.md` invariants 6, 2 |

## Propagation

| File | Option 1 | Option 2 |
|---|---|---|
| `.claude/settings.json` | allow lines | same |
| `project-bootstrap/templates/settings.json.example` | allow lines | same |
| `.claude/agents/archunit-installer.md` | `omitClaudeMd`; explicit Read; Why line | — |
| `.claude/agents/commons-logging-installer.md` | `omitClaudeMd`; Why line | — |

Written by the main thread, not delegated (Form 8).
