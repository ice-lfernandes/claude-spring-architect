# 0059 · Agent classes as data: body structure validated by `schema`, write territory enforced by `guard` through `agent_type`

- **Date:** 2026-09-27
- **Scenario:** "today the agents have little enforcement and standardization between them
  in terms of structure (at least nothing deterministic) — I want a schema for every agent
  in the project so that any creation or edit, even a manual one, is validated by a hook";
  plus "help me group my current agents into a classification and define a standard
  structure for each type".
- **Decision:** Form 7c — an `agent_classes` block in `@.claude/schemas/extensions.json`,
  read by two existing modes of `.claude/hooks/ArchHook.java` (`schema` for body, sections and
  frontmatter; `guard` for territory, keyed on `agent_type`). No new hook registration and no
  new mode. `guard.executor_agents` is deleted: `executor: true` on a class is the single owner
  of who may write.
- **State:** approved by Lucas Fernandes, on 2026-09-27 — Option 1, with no amendment.

## Interview

| Axis | Answer | Forms/options it eliminated |
|---|---|---|
| 1 — symptom | Two observed, both verifiable on disk. **Structure:** the four agent files share no shape — `project-initializer` has no H1 title, calls its procedure `## Steps` while the other three call it `## Procedure`, and has none of `## Failure mode`, `## Summary format`, `## References`; the `## Why` heading is spelled two different ways; `effort` is declared by three agents and absent from the fourth. `claude plugin validate` does not read `.claude/agents/` at all, and `schema` checks only their frontmatter field names. **Territory:** every agent already writes a prose `**Writes:**` / `**Does not write:**` contract — `archunit-installer` promises not to touch `docker-compose.yml`, `commons-logging-installer` promises to stay inside `commons`'s own package — and the guard grants all four an unconditional bypass, so none of those promises is enforced by anything | "create nothing" |
| 2 — trigger | A file is written by a subagent (`Write`/`Edit` with `agent_type` set), and an agent file is edited — including by hand, with no agent running | Forms 1-5: none of them run at the moment of the write |
| 5 — nature | Declarative facts (which class an agent is, what it may write, which sections its body carries, which frontmatter fields it must declare) consumed by two procedures that already exist | Form 3; a new skill |
| 7 — mandatoriness | Cannot be allowed to fail. The prose contracts are the same kind of promise that failed for the skills in lessons-learned-012 § 4, and an agent's bypass is wider than any skill's territory | Everything above the line in decision-matrix § 1 |
| 8 — destination | **Both.** Three of the four agents travel (`export.agents.include`); `project-initializer` does not (`export.agents.exclude`), exactly like the creation skills, so "listed but absent" is an error only in the source repository | Meta-repo-only, generated-project-only |
| 9 — integration | Collides with one existing list: `guard.executor_agents`, which today answers "may this agent write anything at all" | An `agent_classes` block that leaves that list in place — see Option 2 |
| 14 — lifecycle event | `PreToolUse` on `Write`/`Edit`/`MultiEdit` for territory; the same edit-time registration `schema` already has for structure. **Both registrations already exist** and need no new entry in either `settings.json` | A new hook registration; `SubagentStop` bookkeeping |
| 15 — reaction | Block, exit 2, naming the class and its `write_allow` | Form 8 — `permissions.deny` is static and cannot know which subagent is running |
| 16 — existing mode | Both checks land on modes that already walk these exact files: `schema` already validates `.claude/agents/*.md` frontmatter and already cross-checks `guard.executor_agents` against the `**Executor:** yes` marker; `guard` already reads `agent_type` on every write | A new `agents` mode — anti-pattern 17 |

**User's answers on the four design forks** (asked before any option was written):

| Fork | Answer |
|---|---|
| Scope of a class | Body shape **and** write territory, for every class |
| Number of classes | Three: `driver` · `executor` · `installer` |
| Frontmatter the hook must require | `model` and `tools` mandatory; `permissionMode: bypassPermissions` forbidden; `effort` mandatory for `executor` and `installer` |
| Owner of "who may execute" | Derived from `agent_classes` — `guard.executor_agents` stops being a hand-written list |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `agent_classes` in `extensions.json`, territory keyed by `agent_type` | 9 | **Approved** |
| 2 | `agent_classes` for body shape only, `guard.executor_agents` untouched | 6 | Rejected — standardizes the prose promise instead of enforcing it, and leaves two owners of who executes |
| 3 | One merged `classes` map covering skills and agents together | 5 | Rejected — the two file types disagree on frontmatter case, match glob, required fields and how territory is keyed |
| 4 | New `agents` mode in `ArchHook.java` | 3 | Rejected — anti-pattern 17: `schema` already walks these files and `guard` already reads `agent_type` |

### Option 1 — `agent_classes` in `.claude/schemas/extensions.json` (score 9)

**Motivator:** axes 1, 7 and 16 together, plus all four user answers. The structure half is
the same gap `skill_classes` closed for skills in 0058; the territory half is the same
prose-that-failed shape, one layer lower.

Three classes, each with `required_sections`, `required_fields`, `write_allow`, and an
`executor` flag:

| Class | Agents | What makes it one class | `write_allow` |
|---|---|---|---|
| `driver` | `project-initializer` | Interviews the user and delegates the procedure to a skill; writes the whole tree of a project that does not exist yet | `**` |
| `executor` | `java-spring-boot-developer` | Implements an approved spec; the spec replaces the interview | `src/**`, `**/src/**`, plus the one `docs/use-cases/UC-*/UC-*-spec.md` line it closes |
| `installer` | `archunit-installer`, `commons-logging-installer` | One-shot mechanical setup, no interview, returns a structured summary | Per-agent `overrides` — the two territories are known, fixed, and an order of magnitude narrower than `src/**` |

The mechanism for territory is the part that makes this cheap: **`agent_type` is already
present in the `PreToolUse` payload of every write a subagent makes** — that is what the
current bypass reads. So an agent's territory needs no phase file, no `SubagentStop`
bookkeeping, and is immune to the two-hook ordering race of lessons-learned-006 § 1 by
construction: there is nothing to order, the payload names the agent on every single write.

`guard.executor_agents` is deleted. The same fact is `agent_classes.<class>.executor: true`,
and the existing both-directions cross-check against the `**Executor:** yes` marker is
retargeted at it — invariant 2 (single owner) and invariant 10 (lists live in
`extensions.json`, once).

**Pros:**

- Zero new hook registrations and zero new modes. Both `schema` and `guard` already run on
  exactly these files and already read exactly these payload fields.
- Symmetric with `skill_classes`: same `class_marker`, same prefix-matched
  `required_sections`, same `overrides.<name>.write_allow` escape hatch, same
  both-directions coverage check. One concept to learn, not two.
- Turns four prose `**Does not write:**` contracts into enforcement without rewriting any
  of them — the prose becomes the documentation of a rule that now holds.
- Narrows the widest trust surface in the repo. Today any of the four agents may write any
  path in the project; after this, `archunit-installer` writing `docker-compose.yml` is an
  exit 2 instead of a lesson learned.
- Fixes the residual race of lessons-learned-006 § 1 for the classed agents rather than
  documenting it: territory no longer depends on which of two hook processes wrote the
  state file last.

**Cons:**

- Replaces an unconditional bypass with an allowlist, in the path that generates whole
  projects. A territory written too narrowly stops a generation with exit 2 mid-run. This
  is the real cost, and it is paid once per agent, in the data.
- `installer` needs `overrides` immediately — its two agents share a shape but not a
  territory. The class defaults to the intersection and each agent widens it; the same
  pattern `build` already uses for four skills in `skill_classes`.
- `commons-logging-installer`'s package is blueprint-dependent (`commons.logging` here,
  `shared.logging` in modular-monolith), so its glob has to match on the leaf segment
  (`**/src/main/java/**/logging/**`) rather than on a package name no blueprint guarantees.
- Behavior change beyond the new data: an `Agent` call to an executor no longer *deletes*
  the skill phase. It no longer needs to — the agent's writes are judged by `agent_type`,
  and deleting the phase silently unrestricted the main thread for the rest of the turn.

**Points cut in the rubric:** criterion 9 (cost of always running) — the guard now blocks on
data that has to be right, in the one flow where being wrong stops a whole generation.
Everything else scores full: the form matches § 2.2 row 4 (the check needs to read the
file), no invariant is strained, the precedent is 0058 written three days ago, and
propagation is closed by the same `export` block that already carries `skill_classes`.

### Option 2 — `agent_classes` for body shape only (score 6)

**Motivator:** axis 1's structure half alone. `schema` gains the body and frontmatter
checks; `guard` is not touched, and the four agents keep their unconditional bypass.

**Pros:** no possibility of blocking a legitimate generation; a much smaller change to the
Java; the standardization the scenario opens with is fully delivered.

**Cons:** leaves axis 7's answer unanswered — the prose `**Does not write:**` contracts stay
prose, which invariant 6 calls the thing to fix, not to standardize. Two owners of "who may
execute" survive (`guard.executor_agents` and the new block), which is the divergence
invariant 2 exists to prevent. The user's first answer ruled this out explicitly.

**Points cut:** criterion 4 (enforcement — relies on persuasion where a guarantee was
available) and criterion 7 (incomplete propagation — `guard.executor_agents` left behind).

### Option 3 — one merged `classes` map for skills and agents (score 5)

**Motivator:** axis 9, read as "one concept beats two".

**Pros:** a single block to read; no chance of the two definitions drifting in shape.

**Cons:** the two file types disagree on almost everything the block would have to hold —
frontmatter case (`kebab` for skills, `camel` for agents), `match` glob, which fields are
required, and how territory is keyed (an open phase for skills, `agent_type` for agents). A
merged map needs a discriminator field on every entry, which is two blocks with extra
steps. It also makes every future skill-class edit a diff that touches agent behavior.

**Points cut:** criteria 1 (form fit), 5 (maintenance cost), 6 (no precedent).

### Option 4 — a new `agents` mode in `ArchHook.java` (score 3)

**Motivator:** none from the interview; it is the shape the request suggests if axis 16 is
never asked.

**Cons:** anti-pattern 17, directly. `schema` already validates these files and `guard`
already reads `agent_type`; a third mode would need its own registration in two
`settings.json` files, would duplicate the frontmatter parser, and would be the second
place a reader has to look to learn what an agent file must contain.

**Points cut:** criteria 1, 5, 7, and 9.

## References

| Claim | Source |
|---|---|
| An agent exists only for one of three reasons, and `tools`/`model` are two of them | `@CLAUDE.md` invariant 5 · `references/decision-matrix.md` § 5 |
| A rule that must always hold is a hook, not prose | `@CLAUDE.md` invariant 6 |
| Lists of names read by the hook live in `extensions.json`, never in the Java | `@CLAUDE.md` invariant 10 |
| A norm has a single owning file; two copies have diverged | `@CLAUDE.md` invariant 2 |
| `agent_type` is present in the `PreToolUse` payload of a subagent's write | `.claude/hooks/ArchHook.java`, `guardWrite` — the current executor bypass reads exactly this field |
| Territory as data, prefix-matched sections, `overrides.<name>.write_allow`, both-directions coverage | `@.claude/decisions/0058-skill-classes-territory-schema.md` |
| Reusing an existing mode beats writing a new one | `references/decision-matrix.md` § 7 anti-pattern 17 · § 2.2 row 4 |
| A blocking hook must state the way out | `references/decision-matrix.md` § 7 anti-pattern 18 |
| The two-hook ordering race that a state file cannot fix | `@.claude/decisions/0055-lessons-learned-011-remediation.md` · lessons-learned-006 § 1, quoted in `guard`'s own comment |
| `project-initializer` does not travel into a generated project | `export.agents.exclude` in `@.claude/schemas/extensions.json` |
| Frontmatter fields the runtime actually recognizes for an agent | `types.agent.allowed` in `@.claude/schemas/extensions.json` · `references/frontmatter-fields.md` |

## Propagation

| File | Change |
|---|---|
| `.claude/schemas/extensions.json` | New `agent_classes` block: three classes, `universal_sections`/`universal_fields`/`forbidden_values`/`require_h1`, per-class `required_sections`/`required_fields`/`write_allow`/`executor`, and `overrides` for the two installers. `guard.executor_agents` removed; `guard`'s `$comment` rewritten |
| `.claude/hooks/ArchHook.java` | New `agentClassOf`, `agentWriteAllowOf`, `agentNameOf`, `checkAgentBody`, `bodyH1`, `checkAgentClasses`. `checkExecutorAgents` deleted — its both-directions marker cross-check moved into `checkAgentClasses`, now keyed on `executor: true`. `checkOne` calls `checkAgentBody` on `agent_classes.match`. `guardWrite`: an `agent_type` with a class is judged by that agent's `write_allow` and the phase is not read; an unclassed one falls back. `guardCall`: the `Agent`/`Task` branch deleted — a call no longer closes the phase |
| `.claude/agents/project-initializer.md` | H1 title added; `## Steps` renamed `## Procedure`; `**Class:** driver`; `**Executor:** yes` rewritten to cite the class flag instead of the deleted list |
| `.claude/agents/java-spring-boot-developer.md` | `**Class:** executor` + the same rewrite |
| `.claude/agents/archunit-installer.md` | `**Class:** installer`, naming its override and stating that the prose `**Does not write:**` list is now that data restated |
| `.claude/agents/commons-logging-installer.md` | `**Class:** installer`, including why the glob matches the leaf segment (`commons.logging` vs `shared.logging`) |
| `.claude/skills/new-feature/SKILL.md` | The pre-flight rule "never fire the `Skill` and the `Agent` call in the same turn" retired, with the reason it no longer applies |
| `CLAUDE.md` | Invariant 9 gains the agent clause; routing table gains the `agent_classes` row; a new known pitfall on `agent_type` beating the caller's phase |
| `.claude/skills/claude-code-architect-designer/SKILL.md` | Propagation table, Agent row |
| `.claude/skills/claude-code-architect-designer/references/decision-matrix.md` | § 5 gains the two mandatory frontmatter fields; § 7 gains anti-pattern 20; criterion 7 of the rubric names both class blocks |
| `.claude/.ci/AgentTerritoryTest.java` | New — 18 guard cases over the real `extensions.json`, no Docker |
| `.claude/.ci/SkillTerritoryTest.java` | Two cases rewritten (an `Agent` call no longer closes the phase), one added (a plain prompt does) |
| `.claude/.ci/InjectionPathTest.java` | `stubExecutorAgents` replaced by `stubAgents`, which generates each stub from the class's own required sections, fields and `executor` flag |
| `.github/workflows/validate.yml` | New step in `hooks-cross-platform`, next to the skill one |
| `docs/01-tipos-de-arquivo.md` · `docs/en/01-file-types.md` | The executor paragraph replaced by the class table and the `agent_type` rule |
| `docs/03-new-feature.md` · `docs/en/03-new-feature.md` | Pre-flight paragraph: the two gaps may now fire in one turn |
| `docs/07-ci-validate.md` · `docs/en/07-ci-validate.md` | Graph node, job table row, case counts, local command list |
| `docs/08-audit-usage.md` · `docs/en/08-audit-usage.md` | `guard` section: two data blocks, the rewritten phase table, and the pitfall marked retired with the general lesson kept |

Goes to the generated project: **yes** — `agent_classes` rides inside
`schemas/extensions.json`, which `export` copies whole, and the three `export.agents.include`
agents carry their `**Class:**` markers with them. `project-initializer` stays behind, so its
class is validated here only.
