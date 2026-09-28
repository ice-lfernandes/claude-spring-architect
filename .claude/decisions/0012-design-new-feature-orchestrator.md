# 0012 · Design of `/new-feature` — manual pipeline orchestrator

- **Date:** 2026-09-08
- **Scenario:** Detailed design of `/new-feature`, the invocable skill that orchestrates
  the feature pipeline (5 design skills) and consolidates specs into an implementation
  plan (`UC-NNN-spec.md`) for the executor.
- **Decision:** **Form 2 — skill with `disable-model-invocation: true`.** Write SKILL.md
  + template.
- **Status:** approved by Lucas Fernandes, on 2026-09-08

---

## Why this is Form 2 (manual skill)

Axis 2 (manual trigger) + axis 5 (procedural) + axis 8 (both — this repo and generated
projects).

- Form 1 (auto-invocable) rejected: the user asked for a manual `/new-feature`, not
  auto-discovery.
- Subagent (Form 3) rejected: none of the three reasons (preserve context, restrict
  tools, different model).
- Rule (Form 4) rejected: it's ordered procedure, not declaration.
- CLAUDE.md (Form 5) rejected: fixed order rules it out.

---

## Interview

| Axis | Answer | Eliminates |
|---|---|---|
| 1 — Symptom | Already mapped in D20, wants detailed design | Eliminates nothing; justifies design, not postponement |
| 2 — Trigger | `/new-feature` manual invocation | Form 1 rejected, Form 2 approved |
| 3 — Frequency | Every new feature | On demand (not permanently in context) |
| 4 — Territory | `docs/use-cases/UC-*/` inside the project | Doesn't apply paths (skill, not rule) |
| 5 — Nature | 6-stage ordered procedure | Form 1 or 2 (skills) |
| 6 — Isolation | No — pure orchestrator | Not Form 3 |
| 7 — Enforceability | Can fail sometimes | Not a hook or `permissions.deny` |
| 8 — Destination | **Both** — this repo + generated projects | Requires step 6.7 + gap 6.8 (agents) |
| 9 — Integration | Calls 5 skills in sequence; writes UC-NNN-spec.md | Owner of the sequence, not of the partials |
| 10 — Cost of error | Days (wrong plan for the executor) | Medium-high weight on the score |

---

## Options evaluated

| # | Option | Form | Score | Verdict |
|---|---|---|---|---|
| 1 | Manual skill with 6 stages + consolidation into spec.md | Form 2 | **9** | **Approved** |
| 2 | Auto-invocable skill (Form 1) | Form 1 | 5 | Rejected — inverts the user's explicit intent |
| 3 | Executor agent (Form 3) | Form 3 | 2 | Rejected — violates invariant 6; none of the three reasons |
| 4 | Don't create now (respect D20) | — | 8 | Rejected — the user asked for design/writing now |

### Option 1 — Form 2 (score 9)

**Motivator:** axes 2 (manual), 5 (procedural), 8 (both).

**Pros:**

- The matrix table points perfectly: procedure + manual trigger = Form 2.
- Confirmed pattern in `/init-project` and `/arch-doctor` (both Form 2).
- Manual (no accidental firing).
- Natural orchestrator: calls 5 skills in order, consolidates into a spec the executor
  consumes.
- Step 6.7 already copies skills; `/new-feature` travels with the generated project.
- Output `UC-NNN-spec.md` is a concrete artifact (not prose), verifiable.

**Cons:** None.

**Rubric (0-10):**

| # | Criterion | Loses? |
|---|---|---|
| 1 | Form fit | ✅ Perfect (2 → 1.43) |
| 2 | Invariant compliance | ✅ No tension (2 → 1.43) |
| 3 | Context cost | ✅ On demand (3 → 1.43) |
| 4 | Enforcement | ✅ Input + spec output validation (4 → 1.43) |
| 5 | Maintenance | ✅ Integrates 5 skills, loses cost; gains single purpose (5 → 1.43) |
| 6 | Precedent | ✅ Known pattern in 2 skills (6 → 1.43) |
| 7 | Propagation | ✅ Closes routing, step 6.7, SKILL.md + template ready (7 → 1.43) |

**Total:** 7 × 1.43 = 10 (rounded)

### Option 2 — Form 1 (score 5)

Auto-invocable skill. The model invokes it when it sees "designing a new feature" in
context.

**Cons:** Inverts the user's explicit answer ("manual"). Risks firing when the user was
just listing UCs.

**Rejected:** direct violation of intent.

### Option 3 — Form 3 (score 2)

Executor agent. Violates invariant 6 (agent only for one of 3 reasons: context, tools,
model). None applies here:
- Context: spec already complete, no isolated conversation.
- Tools: nothing restricted (calls the same skills the model would).
- Model: it's Haiku or Sonnet (same model that writes the skill).

**Rejected:** anti-pattern 1.

---

## What was written (Phase 4 of the skill)

### 1. `.claude/skills/new-feature/SKILL.md`

Skill with `disable-model-invocation: true`, `arguments: [uc-id]`.

**Contract:** Owner of the sequence + output `UC-NNN-spec.md`.

**Entry guardrail:** 5 validations (valid UC, generated project, disk, new vs. existing
UC).

**Procedure:** 6 stages in depends-on order + consolidation.

### 2. `.claude/skills/new-feature/templates/feature-spec.md.example`

Template with 5 blocks (use-case, domain, persistence, REST, tests) +
implementation-order checklist.

Each block has fixed sections: aggregate, VOs, invariants, ports, events; JPA entity,
migrations; resources, DTOs, error map; pyramid, cases, coverage.

**Recipient:** the `java-spring-boot-developer` agent (once it exists), or manual review.

---

## Gap kept open: step 6.8

Bootstrap's step 6.8 doesn't exist — it doesn't copy `.claude/agents/**`. When
`java-spring-boot-developer.md` is written:
- Step 6.8 will need to copy agents into the generated project.
- Without it, the copied `/new-feature` would delegate to an agent that wasn't
  copied — would violate invariant 9.

Recorded in D20 § Gap. Postponed because the agent doesn't exist yet. **Whoever writes
the executor finds this here and in D20.**

---

## References

| Statement | Source |
|---|---|
| The pipeline is: use-case → domain → persistence → REST → tests → executor | D14 |
| Pipeline skills without `disable-model-invocation` | D17 |
| `/new-feature` is postponed until 2+ real specs exist; the executor is `java-spring-boot-developer`; destination both | D20 |
| Form 2 is a skill with `disable-model-invocation: true` for manual trigger + procedure | `references/decision-matrix.md` § 4 |
| Agent only for one of 3 reasons | `references/decision-matrix.md` § 5; anti-pattern 1 § 7 |
| Invariant 6 — agent only for 3 reasons | `@CLAUDE.md` § Invariants |
| Invariant 8 — specs via skills, code via executor | `@CLAUDE.md` § Invariants |
| Invariant 9 — generated project autonomous | `@CLAUDE.md` § Invariants |
| Every skill has a `## Why this is <form>` section in its body | `references/decision-matrix.md` § 9, § Level 1 |

---

## Propagation

| File | Change | Status |
|---|---|---|
| `@CLAUDE.md` § Routing | Add `/new-feature` to the table | **TODO** |
| `.claude/rules/00-index.md` | N/A (skill, not rule) | — |
| `.claude/skills/new-feature/SKILL.md` | New file | ✅ Written |
| `.claude/skills/new-feature/templates/feature-spec.md.example` | New template | ✅ Written |
| `CONTEXT.md` § 4 inventory | `new-feature` row: from ❌ to ✅, note D22 | **TODO** |
| `CONTEXT.md` § Decisions | Row D22 | **TODO** |
| `.claude/decisions/0012-design-new-feature-orchestrator.md` | New record | ✅ Written |

---

## Next steps (out of scope for this decision)

1. Update `@CLAUDE.md` § Routing (add the `/new-feature` line).
2. Update `CONTEXT.md` § 4 inventory (mark `.claude/skills/new-feature/` as ✅, D22).
3. Update `CONTEXT.md` § 2 decisions (add row D22).
4. Test `/new-feature UC-001-[...]` against a generated project with 2+ complete specs
   (validate the flow).
5. Once the executor exists, write step 6.8 (copy agents into the project).
6. Propagate D20 § Gap's gap 6.8 to the executor.
