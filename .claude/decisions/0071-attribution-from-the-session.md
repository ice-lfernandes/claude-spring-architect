# 0071 · `git-publish` takes its attribution from the session, and pins no model name

- **Date:** 2026-09-28
- **Scenario:** lessons-learned-014 § 10 — `git-publish` prescribed
  `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>` as a literal to append to every
  commit. The session that ran it was Opus 5, the model followed the session's attribution over
  the skill's literal — which was right and undocumented — and the audit trail recorded the
  executor run as two models on one feature.
- **Decision:** No new piece. The step says to append exactly what the session provides, and
  nothing when it provides nothing.
- **State:** approved by Lucas Fernandes, on 2026-09-28.

## Why a record for a one-paragraph edit

Because the reason generalises and the file travels. `git-publish` is copied into every
generated project, so a model name written in it goes stale in each of them, silently, with no
run ever comparing the literal against the session. This is `@CLAUDE.md` invariant 8's shape —
*versions are never written from memory, they are resolved at runtime* — applied to a fact that
looks like prose rather than like a version.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | A commit trailer naming a model that was not the one running | 9 (create nothing) |
| — fallback | When the session states no attribution, the commit carries none | A generic `Claude <noreply@anthropic.com>`, which invents an identity the session did not declare; keeping the literal as a "fallback example", which is exactly the shape that got copied |
| 8 — destination | Both — `git-publish` is in `export.skills.include` | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Session's trailer verbatim; none when it states none | 8 | **Approved** |
| 2 | Generic `Claude <noreply@anthropic.com>` fallback | 5 | Rejected — attributes a commit to an identity nobody declared |
| 3 | Keep the literal as a documented fallback example | 4 | Rejected — a literal in a fenced block reads as a value to copy, which is how this one arrived |
| 4 | Create nothing | 2 | Rejected — the literal is wrong in every session that is not Sonnet 5, and it ships to every generated project |

**Why a wrong attribution costs more than an absent one:** it is in the git history for good.
A missing trailer is noticed and added; a wrong one is believed.

## References

| Claim | Source |
|---|---|
| Versions and model facts are never written from memory | `@CLAUDE.md` invariant 8 |
| `git-publish` travels into every generated project | `export.skills.include` |
| The session states its own attribution lines | the running session's own instructions |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/git-publish/SKILL.md` | step 4 of gate 1: the trailer comes from the session, none means none, and no model name is written from memory |

Goes to the generated project: **yes**, with the skill. No `export` change.

**Restart warning:** none.

## Verification

`claude plugin validate .claude/skills` and `java .claude/hooks/ArchHook.java schema` pass. A
sweep for a pinned model name across `.claude/skills`, `.claude/agents`, `.claude/schemas` and
`CLAUDE.md` returns only `project-bootstrap/templates/audit-pricing.json.example`, where
`claude-opus-5` and `claude-sonnet-5` are keys of a pricing table — data about models, not a
claim about which one is running.
