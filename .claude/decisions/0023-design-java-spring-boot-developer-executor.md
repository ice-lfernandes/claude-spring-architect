# 0023 · Design of `java-spring-boot-developer` — pipeline executor

- **Date:** 2026-09-08
- **Scenario:** Detailed design of the executor subagent that reads the consolidated
  `UC-NNN-spec.md` (output of `/new-feature`) and implements compilable Java code end to
  end.
- **Decision:** **Form 3 — subagent.** Model `sonnet`, restricted tools (Read, Write,
  Bash), compilation blocklist, 4 intermediate feedbacks, prompt in `/new-feature` to
  invoke it.
- **Status:** approved by Lucas Fernandes, on 2026-09-08

---

## Why this is Form 3 (subagent)

Three reasons, all apply:

1. **Preserve context** — the complete spec.md replaces the interview. The model doesn't
   discover or ask; it executes what's already been decided. The conversation already
   happened in `/new-feature` (axis 1, symptom of the previous skill).
2. **Restrict tools** — writes only under `src/` (domain, persistence, REST, tests);
   reads only spec.md, templates, rules. No access outside the project.
3. **Different model** — `sonnet` to generate 19 steps of compilable code in one pass.
   Haiku doesn't have the capacity. Haiku is designated for triage; this is generation.

None of the three reasons is "delegate work." Preserved context is the driver.

---

## Interview

| Axis | Answer | Implication |
|---|---|---|
| 1 — Symptom | Specs consolidated, implementation missing | Executes, no interview |
| 2 — Trigger | `/new-feature` delegates, or the user invokes | Subagent, not skill |
| 3 — Frequency | Every complete feature | On demand |
| 4 — Territory | `src/**`, `src/test/**` | Restricted write |
| 5 — Nature | 19 code steps (not an interview) | Procedure → skill or agent |
| 6 — Isolation | Restricted tools, sonnet model | **Form 3 confirmed** |
| 7 — Enforceability | Can fail (incomplete spec) | Not a hook |
| 8 — Destination | Both (repo + generated projects) | Step 6.8 copies agents (D20 gap) |
| 9 — Integration | Reads spec.md, invoked by `/new-feature` or manually | Clear contract |
| 10 — Cost | Days (wrong code = long debugging) | High weight on the score |

---

## Refinements incorporated

| # | Aspect | Decision | Rationale |
|---|---|---|---|
| 1 | Input validation | Aborts if spec.md is incomplete | Executor, not designer — doesn't fill gaps |
| 2 | Failure mode | Blocklist — aborts on compilation error | User fixes the spec, retries. No automatic rollback |
| 3 | Isolation | Direct in `src/` (no worktree) | Trusts Sonnet; worktree overhead doesn't pay off |
| 4 | Tools | Restrict to: `Read, Write, Bash` | No network, safe. No access outside the project |
| 5 | Feedback | 4 intermediate ones (domain+persist+rest+test) | Per-block visibility, status + files + what's missing |
| 6 | Invocation | Prompt in `/new-feature` ("Implement now?") | User controls it. Manual or automatic afterward |

---

## Options evaluated

| # | Option | Form | Score | Verdict |
|---|---|---|---|---|
| 1 | Subagent (Form 3) with 4 feedbacks + blocklist | Form 3 | **9** | **Approved** |
| 2 | Auto-invocable skill (Form 1) | Form 1 | 4 | Rejected — context not isolated, skills have context limits |
| 3 | Manual skill (Form 2) | Form 2 | 5 | Rejected — doesn't execute code, only guides; the executor needs to write |
| 4 | Hook (enforcement form) | — | 2 | Rejected — not a guarantee, it's persuasion |

### Option 1 — Form 3 with sonnet (score 9)

**Motivator:** axes 6 (isolation), 1 (context), 2 (trigger).

**Pros:**
- Preserves context (complete spec, no interview).
- Restricted tools (only `src/`, safe).
- Sonnet model (capacity for 19 steps of code).
- Clear blocklist (compilation fails → aborts, user fixes the spec).
- 4 intermediate feedbacks (visibility + confidence).
- Prompt in `/new-feature` (user control).

**Cons:** Step 6.8 is missing (the agent won't be copied to the generated project without
it). Recorded in D20's gap; not a con of this decision.

**Rubric (0-10):**

| # | Criterion | Loses? |
|---|---|---|
| 1 | Form fit | ✅ Perfect (3 reasons) → 1.43 |
| 2 | Invariant compliance | ✅ No tension → 1.43 |
| 3 | Context cost | ✅ Isolated → 1.43 |
| 4 | Enforcement | ✅ Compilation is the arbiter → 1.43 |
| 5 | Maintenance | ✅ Simple, no rollback → 1.43 |
| 6 | Precedent | ✅ Unique, new pattern → 1.43 |
| 7 | Propagation | ⚠️ Step 6.8 missing (D20 gap) → 0.57 |

**Total:** 6.43 → rounds to 6.5 (gap 6.8 costs a point).

---

## Refined operation

### Entry guardrail

```
Spec.md validation:
1. File exists (readable)
2. 5 blocks present (§ 1-5)
3. 19-item checklist (or aborts)
4. Valid project (pom.xml, src/, .claude/)

On failure: aborts with a clear message
```

### 4 intermediate feedbacks

*Superseded (0119):* each block's feedback became its line in the final report. A background
agent's report is delivered once, and an executor that spent that delivery on Block 1 progress
lost its final report. See @.claude/decisions/0119-audit-ignores-subagent-handback-executor-single-report.md.

After each block, structured feedback:
```
✅ Block N: [Name] complete (steps X-Y)
- Main artifact: [description]
- Files: N classes, 0 errors
- Next: Block N+1 (steps ...)
```

### Failure mode

**Incomplete spec:**
```
❌ Spec missing § 3 (Persistence)
Run `/new-feature UC-NNN` again.
```

**Compilation breaks:**
```
❌ Block 1 failed (step 3)
[Error]: undefined method 'validate()'

Fix spec.md § 2. Retry the executor.
```

**Tests fail:**
```
❌ Block 4 failed (step 17)
OrderControllerTest#testIdempotency FAILED

Fix spec.md § 4. Retry the executor.
```

No rollback. The user fixes the spec (source of truth).

### Invocation

```
/new-feature UC-001-order
  → consolidates UC-001-spec.md ✅
  → "Implement now? [y/n]"
    > y
  → invokes java-spring-boot-developer
  → (4 feedbacks + summary)
  → "Green build. Next: git push"
```

---

## Gap: step 6.8

The agent exists, but bootstrap doesn't copy it into the generated project.

**Whoever writes step 6.8** (probably whoever works on project-bootstrap later):
- Copy `.claude/agents/java-spring-boot-developer.md` to the project
- Without this, `/new-feature` copied there would delegate to an agent that doesn't
  exist — invariant 9 broken

**Recorded in:** D20 § Gap. Postponed because the agent didn't exist. Now that it does,
step 6.8 is **blocking for invariant 9**.

---

## References

| Statement | Source |
|---|---|
| 3 reasons for an agent: context, tools, model | `references/decision-matrix.md` § 5 |
| Agent only for one of the 3 reasons | `@CLAUDE.md` invariant 6 |
| Specs via skills, code via executor | D15 |
| Executor `model: sonnet`, destination both, blocked until specs exist | D20 |
| `/new-feature` orchestrator written | D22 |
| Generated project is autonomous | `@CLAUDE.md` invariant 9 |
| Step 6.7 copies skills, 6.8 doesn't exist | `project-bootstrap/SKILL.md` § 6 |
| A compilation blocklist is safe | Testing rule `@.claude/rules/testing.md` — `./mvnw verify` gate |

---

## Propagation

| File | Change | Status |
|---|---|---|
| `.claude/agents/java-spring-boot-developer.md` | New file | ✅ Written |
| `.claude/decisions/0023-design-java-spring-boot-developer-executor.md` | New record | ✅ Written |
| `@CLAUDE.md` § Invariants | N/A (the agent is legitimate by the 3 reasons) | — |
| `CONTEXT.md` § 2 decisions | Row D23 | **TODO** |
| `CONTEXT.md` § 4 inventory | `.claude/agents/java-spring-boot-developer.md`: from ❌ to ✅, D23 | **TODO** |

---

## Next steps (out of scope for this decision)

1. Test the agent against 2+ real specs in a generated project (validate the flow).
2. Write `project-bootstrap`'s step 6.8 (copy agents) — blocking for invariant 9.
3. Update `CONTEXT.md` § 2 + § 4 (decisions + inventory).
4. Once the executor is ready in a generated project: test `/new-feature UC-002` →
   complete implementation.
