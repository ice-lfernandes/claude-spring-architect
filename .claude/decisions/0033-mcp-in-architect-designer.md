# 0033 · Extend `claude-code-architect-designer` to cover MCP servers as Form 6

- **Date:** 2026-09-11
- **Scenario:** MCP servers were cited across the repo (`decision-matrix.md`'s "out of
  scope" row, `extensions.json`'s `mcpServers` frontmatter field, `roadmap.md`'s GitHub
  MCP idea) with no owner deciding when one should exist, what it may contain, or how it
  reaches a generated project. Extend the skill's responsibility to design, validate,
  and write MCP configuration, keeping every existing invariant extensive to MCP.
- **Decision:** Form 6 (new) — MCP server, split 6a (`.mcp.json`, shared) and 6b
  (`mcpServers:` in one agent's frontmatter). The skill writes it, not just proposes it.
- **State:** approved by Lucas Fernandes, on 2026-09-11

## Interview

Four explicit questions were put to the user (not the skill's own 13-axis interview —
this decision is about the skill's own scope, one level up).

| Axis | Answer | Forms it eliminated |
|---|---|---|
| Does the skill write `.mcp.json`, or only propose it (like a hook)? | Writes it (option A) | Option B ("propose and stop, like a hook") |
| Does a designed MCP server always live in only one place? | No — per server, it can be meta-repo only, the generated-project template only, or both | A single `.mcp.json` file covering both meta-repo and generated project |
| Does `ArchHook.java schema` validate `.mcp.json` now, or later? | Now | Deferring enforcement, shipping Form 6a as persuasion-only |
| Rubric: fold MCP's extra risk into existing criteria, or add one? | Add an 8th criterion | Doubling the weight of criteria 3/4 to cover MCP's context and trust cost |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Form 6 (6a `.mcp.json` + 6b `mcpServers:`), skill writes, validated by `ArchHook.java`, 8th rubric criterion | 9 | **Approved** |
| 2 | MCP stays fully out of scope, same as a hook — skill only proposes | 6 | Rejected — leaves the roadmap's GitHub MCP idea and the schema's existing `mcpServers` field permanently orphaned; the skill already writes agents (which carry `mcpServers`), so refusing to write `.mcp.json` is an arbitrary line, not a principled one |
| 3 | Single `.mcp.json`, no per-server destination — always copied into every generated project regardless of whether a server makes sense there | 3 | Rejected — reintroduces the exact anti-pattern `@.claude/decisions/0011-bootstrap-without-business-code.md` closed for Java classes: invented content with no real use case behind it |

### Option 1 — Form 6, skill writes (score 9)

**Motivator:** the schema already accepted `mcpServers` in agent frontmatter with no
owner deciding when it's appropriate; the roadmap already proposed a GitHub MCP server
with no skill to design it.

**Pros:** closes an existing governance gap instead of opening a new one; reuses every
existing invariant (2, 9, 10) by extension rather than inventing MCP-specific rules
outside `rules/`; keeps `rules/` a leaf (invariant 1) by putting all MCP procedure in the
skill, not in a new `rules/mcp.md`.

**Cons:** grows the skill's write surface into a new file type (JSON, not markdown) —
mitigated by treating it exactly like `settings.json`'s existing JSON validation path in
`ArchHook.java`, not inventing new enforcement machinery. Strains no invariant.

**Points cut in the rubric:** none — this is the option the rubric was written around.

### Option 2 — MCP stays out of scope (score 6)

**Motivator:** minimizes the skill's write surface; matches how hooks are handled
(propose, don't execute).

**Cons:** the hook analogy doesn't hold — a hook is infrastructure with its own test
suite (`ArchHook.java`'s existing modes); an MCP server is closer in shape to an agent or
a rule, both of which this skill already writes after approval. Leaves two existing
citations (schema field, roadmap item) with no path to resolution through this skill.

### Option 3 — Single always-copied `.mcp.json` (score 3)

**Motivator:** simpler propagation table — one row instead of a per-server destination
decision.

**Cons:** violates the same principle invariant `0011` already established for Java
code — a generated project shouldn't carry speculative content with no real use case.
Caps at ≤ 4 in the rubric on criterion 2 (compliance with the invariants) alone.

## References

| Claim | Source |
|---|---|
| `.mcp.json` scopes, precedence, transports, `oauth`, `headersHelper`, approval settings | `@claude-help.md` § 9 · <https://code.claude.com/docs/en/mcp> · <https://code.claude.com/docs/en/settings-reference> |
| CLI is the cheaper alternative to a new MCP server | `@claude-help.md` § 9, "Cheaper alternative: CLI" |
| `mcpServers` already a recognized agent frontmatter field, no owner deciding its use | `.claude/schemas/extensions.json` (pre-existing `agent.allowed` list) |
| No speculative content in a generated project | `@.claude/decisions/0011-bootstrap-without-business-code.md` |
| Frontmatter fields are data with a single owner (extended here to `.mcp.json`'s server fields) | `@CLAUDE.md` invariant 10 |

## Propagation

| File | Change |
|---|---|
| `.claude/schemas/extensions.json` | New `mcp` block (`match`, `root_allowed`, `server_allowed`, `required_by_type`, `name_pattern`, `reserved_names`, `secret_scan`); `settings.allowed` list added |
| `.claude/hooks/ArchHook.java` | `sweep()`, `checkOne()` route `.mcp.json`; new `checkMcp()`, `looksLikeSecret()`, `matchesAny()`; `checkSettings()` validates top-level keys; `doctor()` reports MCP server count/validity |
| `.claude/settings.json` | `Write(.mcp.json)` / `Edit(.mcp.json)` triggers for the `schema` hook |
| `CLAUDE.md` | Invariant 11 (no literal secret); invariant 9 extended (per-server destination); invariant 10 extended; routing table row; architecture diagram; 5 new pitfalls |
| `.claude/skills/claude-code-architect-designer/SKILL.md` | Form 6a/6b in the forms table; interview axes 11-13; Phase 2 veto count; Phase 3.5 save condition; Phase 4 steps (generate, secret rule, "why this is", propagate, delegate); § Out of scope; § Contract |
| `.claude/skills/claude-code-architect-designer/references/decision-matrix.md` | § 2.1 (MCP sub-table); § 1 diagram; § 5 corollary; anti-patterns 11-14; 8-criterion rubric; § 9 axis count |
| `.claude/skills/claude-code-architect-designer/references/mcp-fields.md` | New file — derived field reference, same relationship `frontmatter-fields.md` has to `extensions.json` |
| `.claude/skills/claude-code-architect-designer/templates/mcp.json.example` | New file — shape reference for Form 6a |
| `.claude/skills/claude-code-architect-designer/templates/mcp-setup.md.example` | New file — companion env-var doc template |
| `claude-help.md` § 9 | `oauth`, `headersHelper`, `alwaysLoad`, approval settings, `reset-project-choices` |
| `.claude/skills/project-bootstrap/SKILL.md` | New conditional step 7.5 (copies `.mcp.json` + `MCP-SETUP.md` only if a server was designed for the generated project); output contract line; `## Skill contract` writes list |
| `roadmap.md` | GitHub MCP item now names an owner and the CLI-first check |

Goes to the generated project: **per-server, decided at design time (axis 13)** — not
this decision's own file, and not the designer skill itself (it's a creation skill, same
exclusion as `project-bootstrap`/`init-project`). A server designed with axis 13 = "both"
or "generated project" reaches the project through `project-bootstrap` step 7.5; this
decision record does not.
