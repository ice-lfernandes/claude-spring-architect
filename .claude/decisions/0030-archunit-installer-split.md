# 0030 · Split `archunit-installer` out of `test-architect`'s setup mode

- **Date:** 2026-09-11
- **Scenario:** Claude Code's own `/usage` panel showed `/test-architect` responsible
  for 28% of the last-24h token usage on this machine — the single heaviest skill in
  the repo (`claude-code-architect-designer` next, at 5%; every other pipeline skill at
  1%). The same panel names the mechanism directly: "48% of your usage was at >150k
  context — longer sessions are more expensive even when cached" and "heavy skills can
  be scoped down or run with a cheaper model via skill frontmatter."
- **Decision:** **Form 3 — new agent** `.claude/agents/archunit-installer.md`. It takes
  over `test-architect`'s setup-mode procedure verbatim; `test-architect` itself shrinks
  to invoking it and reporting the summary.
- **Status:** approved by Lucas Fernandes, on 2026-09-11

---

## Motivation

`test-architect` has two modes sharing one file. Design mode is bounded and needs to
stay inline — its interview reads live conversation state a subagent can't see
(`@.claude/decisions/0008-testing-rule-and-design.md`). Setup mode has the opposite
shape: zero interview, purely mechanical — resolve a version from Maven Central,
translate exemplar packages, write one file, edit the POM twice, run `./mvnw` up to
three times (`test`, then `clean verify`, then re-run after any fix), iterate until
green. Run inline, every `curl` response and every Maven build log — routinely the
longest single output in a session — becomes permanent context: paid once to produce,
then paid again on every later turn for the rest of the session. That compounding is
exactly what the usage panel's >150k-context warning describes, and setup mode is a
repeat offender because a real project run usually needs more than one attempt before
the ArchUnit rules and the coverage gate both go green.

Design mode was never the problem: it's bounded by its own interview cap (4 questions
per `AskUserQuestion` call) and writes one small partial. The 28% traces to setup mode's
Bash-heavy iteration.

## Why this is Form 3 (agent)

Invariant 6 requires only one of three reasons; this decision rests on one:

1. **Preserves context** — setup mode asks nobody anything. All ten of its steps are
   determined by what's already on disk (POM, blueprint packages, exemplar shape) and
   what Maven Central and `./mvnw` report. Nothing here needs the conversation, so
   nothing is lost by not seeing it — the same test that ruled *design* mode out of
   Form 3 rules setup mode *into* it.
2. Tool restriction and model change don't independently apply — the agent still needs
   `Read, Write, Edit, Bash` (same set `test-architect` already had) and the same
   `sonnet` model. Reason 1 alone is sufficient per invariant 6.

## Current structure (before)

```
test-architect/SKILL.md
├── design mode  — inline: read partials, survey, interview, write 40-testes.md
└── setup mode   — inline: curl Maven Central, write ArchitectureTest.java,
                   translate packages, edit POM (ArchUnit + JaCoCo check),
                   ./mvnw test, ./mvnw clean verify, pin Testcontainers tag,
                   flag docker-compose.yml mismatch
```

One file, one context. Every setup-mode run's Bash output stayed in the same
conversation as every design-mode run that came before or after it.

## New structure (after)

```
test-architect/SKILL.md                    (design mode: unchanged)
  └── setup mode → Agent(archunit-installer, project root)
                 → report the returned summary
                 → if summary flags a Testcontainers/compose mismatch,
                   invoke docker-architect (owner of docker-compose.yml)

archunit-installer.md                      (new agent, Form 3)
  └── owns the ten mechanical steps, verbatim from the old setup mode:
      resolve version → declare dependency → write ArchitectureTest.java →
      translate packages → direction rules → naming/code-quality rules →
      ./mvnw test → wire JaCoCo check → ./mvnw clean verify → pin image tag
  └── returns one structured summary, not raw curl/mvnw output
```

`test-architect`'s `allowed-tools` gained `Agent`. Everything else about design mode —
entry rule, interview axes, exemplar table, `40-testes.md` shape — is untouched.

## What doesn't change

- Coverage-gate **ownership** stays with `test-architect`, per
  `@.claude/decisions/0011-bootstrap-without-business-code.md` — only who physically
  writes the `check` execution moves to the delegated agent.
- `docker-compose.yml` ownership stays with `docker-architect`; the new agent still
  never touches that file, it only reports a mismatch upward.
- Design mode's procedure, interview cap, and output shape: identical.

## Alternatives considered

1. **`context: fork` on the whole `SKILL.md`.** Rejected — it would fork design mode
   too, and design mode's interview needs live conversation state a fork doesn't carry
   (same reasoning as D8). Forking the whole file breaks the mode that must stay inline
   to fix the mode that shouldn't be.
2. **Reuse `java-spring-boot-developer` for setup mode.** Rejected — its contract is a
   complete `UC-NNN-spec.md` driving feature code; setup mode takes a project root and
   produces whole-project verification infrastructure. Forcing one executor to cover
   both blurs single ownership (invariant 2) and its own guardrail would reject the
   input it was never designed for.
3. **Keep it inline, just trim Bash output (`| tail`, `-q` everywhere).** Rejected —
   already mostly `-q`; the residual cost isn't verbosity, it's structural: every
   inline run's output is permanent context regardless of how short each line is, and
   setup mode runs `./mvnw` multiple times per attempt. Trimming shrinks the bill;
   isolating stops it.
4. **Do nothing.** Rejected by the user — this decision is the response to the 28%
   finding.

## Propagation

| File | Change | Status |
|---|---|---|
| `.claude/agents/archunit-installer.md` | New file — owns setup mode's procedure | ✅ Written |
| `.claude/skills/test-architect/SKILL.md` | Setup-mode section replaced with delegation; `allowed-tools` gains `Agent`; mode table, "why not a subagent" section, `## What the partial contains`, and `## Contract` updated to reflect the split | ✅ Written |
| `.claude/skills/project-bootstrap/SKILL.md` | § 6.7 `test-architect` row notes the delegation; § 6.8 gains an `archunit-installer` row (✅ copied — same reason `java-spring-boot-developer` is, invariant 9) | ✅ Written |
| `.claude/decisions/0030-archunit-installer-split.md` | New record | ✅ Written |
| `CONTEXT.md` § 2 decisions, § 4 inventory | Row D30 + inventory entries | **TODO** |

## References

| Statement | Source |
|---|---|
| Agent only for one of three reasons | `@CLAUDE.md` invariant 6 |
| Design mode can't fork — interview needs live conversation | `@.claude/decisions/0008-testing-rule-and-design.md` |
| Coverage gate owned by `test-architect` since setup mode's introduction | `@.claude/decisions/0011-bootstrap-without-business-code.md` |
| Same pattern applied once before, for the same reason (context) | `@.claude/decisions/0023-design-java-spring-boot-developer-executor.md` |
| Generated project must stay self-contained — new agent needs step 6.8 propagation | `@CLAUDE.md` invariant 9 |
| `context: fork` / delegating to an agent is the native way to isolate a skill's context | `@.claude/skills/claude-code-architect-designer/references/frontmatter-fields.md` |

## Next steps (out of scope for this decision)

1. Update `CONTEXT.md` § 2 (decision D30) and § 4 (inventory rows for
   `archunit-installer.md` and the updated `test-architect/SKILL.md`).
2. Run setup mode once against a real generated project to confirm the delegation
   round-trips correctly (agent invoked, summary read back, `docker-architect` invoked
   on a real tag mismatch).
