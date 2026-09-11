# Decision matrix — which extension for which scenario

Reference for the `claude-code-architect-designer` skill. Loads only when it is invoked.

Source: `@claude-help.md` (§ 2, 4, 5, 6, 7, 9, and 13) and the official documentation at
<https://code.claude.com/docs/en/overview>, <https://code.claude.com/docs/en/mcp>, and
<https://code.claude.com/docs/en/settings-reference>. Where they diverge, the official
docs win.

---

## 1. The dividing line

```
   ┌─ CLAUDE.md ──── fact, always in context             │
   ├─ rules/ ─────── fact, by territory (paths)          │  PERSUASION
   ├─ skills/ ────── procedure, on demand                 │  (the model can fail)
   ├─ agents/ ────── isolated execution                   │
   └─ .mcp.json ──── external tool, on demand             │
  ═════════════════════════════════════════════════════
   ┌─ permissions ── allow / ask / deny                   │  GUARANTEE
   └─ hooks ──────── lifecycle events                      │  (always executes)
```

Everything above the line is read by the model: it can be ignored, misinterpreted, or
lost in a `/compact` — including whether it calls an available MCP tool at all. Everything
below executes regardless of what the model decides. `.mcp.json` sits on the persuasion
side for that reason: connecting a server doesn't guarantee the model uses it well,
which is exactly why MCP and a skill combine (`@claude-help.md` § 9) instead of MCP
replacing one.

**Consequence for classification:** if breaking the rule is a compliance, security, or
build bug, the answer isn't among the six forms. It's a hook or `permissions.deny`, and
this skill proposes without writing.

---

## 2. Decision table

Read top to bottom. **The first row that matches decides** — the questions are ordered by
elimination power, not by frequency.

| Question | If yes |
|---|---|
| Must it happen always, without depending on the model's judgment? | **Hook** or `permissions.deny` — out of scope, propose and stop |
| Does it need to access an external system (Jira, database, S3) — go to § 2.1 first | **Form 6** — MCP server, unless § 2.1 eliminates it |
| Is it a declarative fact that holds in every session and across the whole repo? | **Form 5** — `CLAUDE.md` section |
| Is it a declarative fact that only holds for part of the files? | **Form 4** — rule in `rules/` with `paths` |
| Is it a multi-step procedure, or long, rarely-needed reference? | **Form 1 or 2** — skill |
| … and does the user want to trigger it by hand, or does it have a side effect? | **Form 2** — `disable-model-invocation: true` |
| … and should it fire on its own from the description, without the user asking? | **Form 1** |
| Does it need isolated context, restricted tools, or a different model? | **Form 3** — subagent, and even then see § 5 |

No row matches → the answer is **create nothing**.

### 2.1. Sub-table — which MCP form, and whether it's MCP at all

Read top to bottom, same rule: **the first row that matches decides**. Sits inside the
"external system" row above because CLI-first has to run before Form 6 is even
considered, not after.

| Question | If yes |
|---|---|
| Does a CLI already solve it (`gh`, `psql`, `aws`, `kubectl`, `sentry-cli`)? | **Create nothing** — `Bash` + a line in `permissions.allow`. `@claude-help.md` § 9 names this the cheaper alternative; it is the most context-efficient way to talk to an external service and the model already knows how to use it |
| Must the tool never be callable at all? | `permissions.deny` on `mcp__<server>__<tool>` — out of scope, propose and stop |
| Does only one agent need it? | **Form 6b** — `mcpServers` in that agent's frontmatter |
| Does the whole team need it, with no secret literal in the file? | **Form 6a** — `.mcp.json`, credential via `${VAR}`, `oauth`, or `headersHelper` |
| Is it personal or experimental, not for the team? | `claude mcp add --scope local` — this skill doesn't write it; it isn't versioned |
| Does the server already exist and the model just uses it wrong? | **Form 1** — a skill documenting how to use its tools well, not a new server |

No row matches → **create nothing**. A server with no observed call is dead weight from
the first session: its name alone costs context at startup (§ 3, "cheap triage" row
doesn't apply here — see § 7 anti-pattern 13).

---

---

## 3. Triggers — the signal that justifies creating

Not configured by anticipation. Each form has a recognizable signal; without the signal,
the piece is born speculative and dies from disuse.

| Observed signal | Form |
|---|---|
| The model gets the same convention wrong a second time | 5 (one line) or 4 (if it has territory) |
| `CLAUDE.md` went past ~200 lines | 4 — extract with `paths` |
| You pasted the same N-step procedure a third time | 1 or 2 |
| You write the same prompt to kick off a task | 2 |
| A task fills context with verbose output (search, logs, build) | 3 |
| A task shouldn't be able to write files | 3, with restricted `tools` |
| Cheap triage repeating many times | 3, with `model: haiku` |

---

## 4. Form 1 vs Form 2 — auto-invocable or manual

Same file, one line of difference. Mistakes happen in both directions:

| Symptom | Cause | Fix |
|---|---|---|
| The skill doesn't fire when it should | `description` too vague or abstract | Concrete use case first, trigger phrases after |
| The skill fires too much | Territory too broad | Add `paths`, or switch to `disable-model-invocation: true` |
| The skill fires on its own and causes damage | It has a side effect | `disable-model-invocation: true`, always |

This repo's practical rule, and the exception that bounds it:

- **Writes files in the user's project and is triggered by their decision → Form 2.**
  `arch-doctor`, `init-project`, `project-bootstrap`, `java-patterns`, and this skill.
- **Part of a pipeline another piece chains → Form 1**, even when writing files.
  `disable-model-invocation` hides the skill from the model, and what the model doesn't
  see the orchestrator doesn't call. The five pieces of `/new-feature` have been this way
  since D17 — `@.claude/decisions/0007-pipeline-skills-invocation.md`.

The guard for those five isn't the frontmatter: it's the entry rule in the body, which
makes them stop when the previous partial doesn't exist.

Only `description` stays permanently in context; the body loads on invocation. An
800-line guide costs ~30 tokens until it's needed — so a long body isn't an argument
against the skill. `description` + `when_to_use` are truncated to 1536 characters in the
listing.

---

## 5. Form 3 — the three reasons, and only those

An agent exists for one of these:

1. **Preserve context** — verbose exploration stays in the subagent, only the summary
   comes back.
2. **Restrict tools** — a reviewer with `tools: Read, Grep, Glob` can't write.
3. **Control cost or capability** — `model: haiku` for triage, `opus` where failure is
   expensive.

None applies → it's a skill. This is anti-pattern #1, and the most expensive one: one
more piece to maintain, no gain.

**Corollary — `mcpServers` in an agent's frontmatter is reason 2, never a fourth
reason.** Giving one agent its own MCP server is "restrict tools" applied to an external
connection instead of a built-in one: the rest of the session doesn't carry that
server's tool names in context, and no other agent can reach it. It doesn't unlock a new
category of agent — the same three-reason test above still gates whether the agent
should exist at all; `mcpServers` only answers *which* tools it gets once an agent is
already justified.

Counter-test before proposing Form 3 — if any answer is yes, it's a skill:

- Is the interview with the user the heart of the task? (the subagent doesn't see the
  conversation)
- Does the context it needs fit in a `references/` of the skill itself?
- Is the final output short anyway?

**What the subagent receives:** its own system prompt, the delegation message, all
`CLAUDE.md` files, a `git status` snapshot, and the skills preloaded in `skills:`.
**What it doesn't receive:** conversation history, skills already invoked, files already
read.

Sole precedent in this repo: `init-project` → `project-initializer`, and the agent's own
file opens with the section "Why this is an agent and not a skill". A new agent repeats
that section or isn't approved.

**Delegating the writing isn't a reason.** "The decision's already been made, now it's
just implementation" fails the counter-test above on all three points: the interview is
the heart of the task, the context fits in `templates/`, and the output is short. And the
premise is false — choosing the `description` decides whether the skill fires (§ 4), and
the `## Contract` decides ownership (invariant 2). That's design. What's left that's
mechanical is only propagation: table edits with file and line fixed in writing. Only
that part gets delegated, and only when the volume justifies it — axis 8 = "both". Never
without a saved decision record: the subagent doesn't see the conversation.

---

## 6. Form 4 vs Form 5 — rule or `CLAUDE.md`

| | Form 5 (`CLAUDE.md`) | Form 4 (`rules/`) |
|---|---|---|
| Cost | Every prompt, every session | Only when a `paths` matches |
| Scope | Whole repo | File territory |
| Survives `/compact` | Yes, re-injected | Reloads on touching the files |
| Size target | < 200 lines total | One theme per file |

Two loading paths for Form 4, and the first is preferable: **`paths`** (auto-loads,
doesn't depend on anyone remembering) and **explicit citation** by path, for
cross-cutting rules no glob captures. Declare `paths` whenever there's identifiable
territory.

Non-negotiable restrictions of this repo:

- **Invariant 1 — `rules/` is a leaf.** A rule never mentions a skill, an agent, or a
  command. If it needs to, it's a procedure: it belongs in a skill.
- **Invariant 2 — single owner.** The rule lives in one file; others cite it by path
  (`@.claude/rules/naming.md`). Written in two places, it diverges on the first update.
- A new rule enters `@.claude/rules/00-index.md` (leaves "planned", enters "written") and
  step 6.6 of `project-bootstrap` — otherwise the generated project cites a file that
  doesn't exist there.

What does **not** go into a rule or `CLAUDE.md`: architecture readable from the code, a
dependency list, directory layout, a multi-step procedure. What does: build and test
commands, conventions that diverge from the tool's default, known pitfalls, decisions and
their reasoning. Specific always beats vague.

---

## 7. Anti-patterns — reject in Phase 2

| # | Anti-pattern | Signal | Redirect |
|---|---|---|---|
| 1 | Agent where a skill would do | None of the three reasons in § 5 | Skill |
| 2 | Prose where it had to be a hook | "Always run X before finishing" repeated | Hook — out of scope |
| 3 | Obese `CLAUDE.md` | Past ~200 lines | Extract to `rules/` with `paths` |
| 4 | Rule that talks about skills | Breaks invariant 1 | Move the procedure to the skill |
| 5 | Duplicated rule | Same theme in two files | Single owner + citation |
| 6 | New `commands/` | Invariant 4 | Skill + `disable-model-invocation` |
| 7 | Invented frontmatter | Field the runtime ignores | `frontmatter-fields.md` |
| 8 | Code in the skill body | Boilerplate pasted into markdown | `templates/*.example` |
| 9 | Piece built on anticipation | No symptom in interview axis 1 | Create nothing |
| 10 | Creation skill copied into the generated project | Outside step 6.7 | Leave it out, and say so |
| 11 | MCP where a CLI already solves it | `gh`/`psql`/`aws`/etc. already installed | Create nothing + `permissions.allow` (§ 2.1) |
| 12 | Literal secret in `.mcp.json` | A token, key, or password spelled out in `headers`/`env` | `${VAR}`, `${VAR:-default}`, `oauth`, or `headersHelper` — invariant 11 |
| 13 | MCP server with no observed use | No symptom in interview axis 1; its name still costs context at every startup | Create nothing |
| 14 | Same server duplicated across scopes with no destination reason | Same name in `.mcp.json` and an agent's `mcpServers`, or in both this repo's `.mcp.json` and the project template, with no axis-13 "both" answer behind it | Single destination, or record the "both" answer in the decision |

---

## 8. Score rubric (0-10)

So the score isn't opinion. Eight criteria, equal weight, 1.25 points each. Round to the
nearest integer and show what cost points.

| # | Criterion | A point is lost when |
|---|---|---|
| 1 | **Form fit** | The § 2 table (or § 2.1 for MCP) points to another form |
| 2 | **Compliance with the invariants** | Strains one of the eleven; violating one caps the score at ≤ 4 |
| 3 | **Context cost** | Rarely-used knowledge stays always loaded — for Form 6, an MCP server's tool names load at every startup whether or not the session uses them |
| 4 | **Enforcement** | Relies on persuasion where a guarantee was available |
| 5 | **Maintenance cost** | Adds pieces or indirection without proportional gain |
| 6 | **Precedent in the repo** | No similar form already in use; novel design |
| 7 | **Complete propagation** | Doesn't close routing, `00-index`, steps 6.6/6.7/7.5, or the § 9 decision record |
| 8 | **Trust surface** | Grants a capability wider than the task needs — an MCP server that reads files and calls arbitrary APIs, an agent with `permissionMode: bypassPermissions`, a skill with unscoped `allowed-tools` where a narrower rule would do |

Violating an invariant caps the score at **≤ 4**, regardless of the rest. A score **≥ 8**
is a recommendation; **5 to 7** is viable with a written caveat; **≤ 4** appears only to
record why it was rejected.

---

## 9. Decision record

Phase 3 produces options, scores, rejected alternatives, and sources. That disappears
with the session, and without it the same thirteen-axis interview repeats itself six
months from now. Two levels, and the first is mandatory:

| Level | Where | When | Content |
|---|---|---|---|
| 1 | `## Why this is <form>` section in the created file | Always — except Form 5, which has no body | Form chosen, axis that motivated it, closest alternative and why. Three sentences |
| 2 | `.claude/decisions/NNNN-<slug>.md` | Only if there was a real choice | Interview, all options with score, rubric, references, propagation |

Level 2 is saved when at least one of these is true: two or more options scored ≥ 5; the
approved option strains an invariant; axis 8 = "both"; axis 13 (MCP destination) =
"both". None of these → level 1 is enough. A record for a trivial decision is ceremony,
not memory.

Level 1 travels with the file, including into the generated project. Level 2 stays in
this repository — it records decisions about this `.claude/`, invariant 9. It is not a
rule: no `paths`, no frontmatter, outside `00-index.md`. Full rules in
`@.claude/decisions/README.md`.

Restriction for Form 4: level 1 of a rule cannot name skills or agents — invariant 1,
`rules/` is a leaf. If the justification needs to name them, it lives only in level 2.
