# 0081 · Every skill declares `model`, and its class says which models it may declare

- **Date:** 2026-09-29
- **Scenario:** follow-up to 0080 (lessons-learned-015 topic D) — a skill without `model` runs on whatever the session runs, silently; 0080 fixed four skills by hand, and nothing stops the next skill from shipping without the choice
- **Decision:** option 1 — `model` required on every skill, from its class's `allowed_models`; the per-skill values of the table below, unchanged
- **State:** approved by Lucas Fernandes, 2026-09-29
- **Goes to the generated project:** yes — `ArchHook.java` and `extensions.json` travel whole through `export`; the frontmatter of every exported skill travels with it

## Interview

Answered from 0080, the schema and the source; no axis needed a question.

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | No wrong output observed. What was observed is cost: Opus running `git init` and rendering reports, because an absent field is a silent default (the same failure shape as invariant 10) | Weight against a blocking check "for quality" |
| 5 — Nature | A field every skill must carry, and a set of values per class | Data in `skill_classes` |
| 7 — Mandatoriness | The user asked for it to hold, per class | Prose in `frontmatter-fields.md` alone |
| 8 — Destination | Both | Nothing to add to `export` |
| 9 — Integration | `agent_classes` already has `universal_fields: ["model","tools"]` + per-class `required_fields`, enforced at `ArchHook.java:2182`. `skill_classes` has neither. The skill check (`:1950`–`:2000`) already resolves the class and reads `required_sections` | Mirror the agent side, no new mode, no new registration: `schema` already runs on `SKILL.md` edits |
| 9b — Runtime | A skill's `model` holds **for the rest of the turn** (`claude-code-docs/02-skills.md`). `inherit` is documented for agents only; for skills it is unverified | No option relies on `model: inherit` in a skill |
| 10 — Cost of error | A wrong pin costs tokens or a weaker design, found by the reader; a wrong check blocks every edit to a `SKILL.md` with exit 2 | Error message must name the class and its allowed set |
| 16 — Existing mode | `schema` | 7c edit of an existing mode, not a new one |

## Suggested model per class

The value is per skill; the class fixes the **allowed set**. `effort` stays optional —
inherited unless the skill states it.

| Class | Skills | Nature | Suggested | Allowed set |
|---|---|---|---|---|
| `design` | `use-case-design`, `domain-modeling`, `persistence-architect`, `rest-api-architect`, `messaging-architect`, `test-architect` | Interview + the spec the executor implements verbatim. A wrong partial costs days, and it is the most read artifact downstream | `opus` | `opus` |
| `orchestrator` | `new-feature` | Consolidates partials, resolves divergences, chains the executor | `opus` | `opus`, `sonnet` |
| | `init-project` | Hands off to `project-initializer` (`sonnet`, 0078) and relays its report | `sonnet` + `low` | |
| `build` | `docker-architect` | Service design, one observed failure | `opus` (0080) | `opus`, `sonnet` |
| | `java-patterns` | Choosing a pattern against a growing chain is judgment | `opus` | |
| | `arch-adopt` | Merges into a foreign project, may write its blueprint — the only copy that will exist | `opus` | |
| | `project-bootstrap` | Template- and YAML-driven, runs inside `project-initializer` | `sonnet` + `high` | |
| `observer` | `arch-doctor`, `audit-usage` | Render a computed report | `sonnet` (0080) | `sonnet` |
| `ops` | `git-publish` | Fixed procedure behind two gates | `sonnet` (0080) | `sonnet` |
| `meta` | `claude-code-architect-designer` | Designs guarantees for every later session; meta-repo only | `opus` | `opus` |

`haiku` is in no set: the cheapest skills still explain a cause (`arch-doctor`) or read a
git state before a commit (`git-publish`), and a small model inventing a cause in a
diagnostic costs more than it saves (0080, option 3).

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `universal_fields: ["model"]` on `skill_classes` + `allowed_models` per class, values from the table above | 7 | **approved** |
| 2 | Option 1, but `design` exempt: no requirement, the six keep inheriting the session | 6 | not chosen — the largest class would be the one where absent and forgotten look alike |
| 3 | Field required, no allowed set | 5 | rejected — forces the field, not the choice |
| 4 | Create nothing; 0080's four stay the only pinned skills | 3 | rejected — the next skill ships with the silent default |

### Option 1 (score 7)

**Motivator:** axes 1 + 9. The absent field stops being a silent default, and the allowed
set turns "an observer runs on `sonnet`" from a convention into a check.

**Mechanism.**

1. `extensions.json`, `skill_classes`: `"universal_fields": ["model"]`, and in each class
   `"allowed_models": [...]` from the table. A `$comment` citing this record.
2. `ArchHook.java`, the skill-body check, after `required_sections`: read the frontmatter,
   require every `universal_fields` entry, and when the class has `allowed_models`, require
   `model` to be one of them. Same code shape as `:2180`–`:2188`; lists read from the data
   (invariant 10). Messages:
   `<rel> — class \`design\` requires the frontmatter field \`model\` (allowed: opus)` and
   `<rel> — \`model: sonnet\` is not allowed for class \`observer\` (allowed: sonnet)`.
3. The twelve unpinned skills get `model` (and `effort` where the table gives one), plus one
   line under their `## Why …` citing this record, like 0080.
4. `build` → rebuild `ArchHook.jar`; `schema </dev/null` must exit 0 afterwards; one probe:
   remove `model` from a copy's frontmatter and confirm `schema` names it.

**Pros:** every skill's model is a recorded choice; one owner for what a class may run on;
no new process, no new event — `schema` already runs.

**Cons:** `design` pinned to `opus` overrides a generated-project user who chose `sonnet`
for cost or quota, for the whole `/new-feature` turn — the biggest spend in that project.
A model family renamed upstream fails `schema` in every project until the data changes.

**Points cut:** no observed wrong output (−1, invariant 6 mirror); `design` takes the
session choice away from the generated project's user (−1); aliases coupled to the
runtime's vocabulary (−1).

### Option 2 (score 6)

Keeps the user's session choice where the spend is largest. Costs the uniformity the user
asked for: the class with the most skills is the one without the requirement, and an
absent field there is again indistinguishable from a forgotten one — unless `design`
declares `"model_exempt": true` with a `$comment`, which is a second mechanism for one
class.

### Option 3 (score 5)

The field is forced, the choice is not: `haiku` on `use-case-design` would pass. Half the
check for the same Java.

## Why a pinned model does not make these agents

Raised at approval: if the schema fixes a model per skill, isn't that invariant 5's third
reason — change model — and so an agent? Not by itself. The test is **scope**, not the
presence of a model:

- A skill's `model` holds from the moment it fires until the end of the turn. An agent's
  model holds for the agent and nothing else, and the agent starts cold — no conversation,
  only what the delegation passes.
- So the model alone justifies an agent only when it **must not leak** past the work. A pin
  that raises the model (`opus`) leaking into what follows costs tokens, never quality. A pin
  that **lowers** it on a skill fired mid-turn degrades whatever the turn does next — that is
  the smell.
- Before that, § 5's counter-test: would the work survive starting cold? An interview does
  not — it needs the conversation.

Per class:

| Class | Pin | Leak | Starts cold? | Verdict |
|---|---|---|---|---|
| `design` | raises (`opus`) | into `/new-feature`'s consolidation — wanted | no, it interviews | skill |
| `orchestrator` | `new-feature` raises; `init-project` lowers, but delegates at once and ends the turn with the relay | nothing after it | no, it holds the user's arguments | skill |
| `build` | `opus` raises; `project-bootstrap` on `sonnet` runs inside `project-initializer` (`sonnet`) | none past the agent | — | skill. A skill's `model` inside a subagent is not documented: there the pin is a declaration that agrees with the agent's, not a guarantee |
| `observer` | lowers (`sonnet`) | manual, invoked as the turn's own request | report needs no conversation, but is already cheap | skill |
| `ops` | lowers (`sonnet`) | `git-publish` is chained at the **end** of a flow; the borderline case — what follows is the push report | no, two gates with the user | skill, watched |
| `meta` | raises (`opus`) | wanted | no, it interviews | skill |

No skill in the table becomes an agent. `decision-matrix.md` § 5, reason 3, was refined to
say this: the model alone is a reason for an agent only when it must not leak past the work.

### Known risk, no hook

A lowering pin on a model-invocable skill fired mid-turn degrades the rest of the turn. No
case is observed — `git-publish` is chained last, `init-project` delegates and relays — so
no check is written for it: a hook against a failure nobody observed is the mirror of
invariant 6. If a case appears, the answer is § 5 reason 3 (move the work into an agent),
not a longer allowed set.

## References

| Claim | Source |
|---|---|
| A skill's `model` holds for the rest of the turn | `.claude/claude-code-docs/02-skills.md` |
| `inherit` documented for agents, not skills | `claude-help.md` agent-fields table; `frontmatter-fields.md:44` |
| Agent side already enforces required fields from data | `ArchHook.java` `:2180`–`:2188`; `agent_classes.universal_fields` |
| Lists come from `extensions.json`, never from the Java | `@CLAUDE.md` invariant 10 |
| A guarantee always has a record, never delegated | `@CLAUDE.md` invariant 6; designer Phase 3.5 |
| Four skills already pinned | `0080-procedural-skills-on-sonnet.md` |

## Propagation

| File | Change |
|---|---|
| `.claude/schemas/extensions.json` | `skill_classes.universal_fields`, `allowed_models` per class |
| `.claude/hooks/ArchHook.java` + `ArchHook.jar` | the check; jar rebuilt |
| `.claude/skills/{use-case-design,domain-modeling,persistence-architect,rest-api-architect,messaging-architect,test-architect,new-feature,init-project,java-patterns,arch-adopt,project-bootstrap,claude-code-architect-designer}/SKILL.md` | `model` (+ `effort`), one line citing this record |
| `.claude/skills/claude-code-architect-designer/references/decision-matrix.md` § 5 | reason 3: a skill's `model` leaks to the rest of the turn; the model alone justifies an agent only when it must not leak |
| `.claude/skills/claude-code-architect-designer/references/frontmatter-fields.md` | skill `model`: required, allowed set per class in `skill_classes` |
| `.claude/skills/claude-code-architect-designer/templates/SKILL*.example` | `model:` line, so a new skill starts compliant |
| `@CLAUDE.md` § Known pitfalls | one bullet: a skill without `model`, or with one outside its class's set, fails `schema` |

Written by the main thread, not delegated — Form 7c.
