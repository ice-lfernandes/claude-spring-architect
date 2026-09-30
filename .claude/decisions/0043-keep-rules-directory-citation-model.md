# 0043 · Keep `rules/` as a separate leaf; citation stays the fix for subagent access

- **Date:** 2026-09-17
- **Scenario:** Reassess whether `rules/` still earns its place now that subagents don't
  auto-load `paths` — fold rule content into skills instead, or keep `rules/` and rely on
  explicit citation (already added to `java-spring-boot-developer.md` in PR #14).
- **Decision:** Option 1 — keep `rules/` as-is; citation-per-block stays the fix (already
  live in `.claude/agents/java-spring-boot-developer.md`, PR #14)
- **State:** approved by Lucas Fernandes, on 2026-09-17

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | Not a new bug: worry is *cost*, not correctness — "every session here loads all these rules that in theory it wouldn't need." | — |
| 9 — integration | Java-scoped rules (`naming.md`, `code-quality.md`, `lombok.md`, `error-handling.md`, `logging.md`) are each cited by 5 skills (`domain-modeling`, `persistence-architect`, `rest-api-architect`, `messaging-architect`, `test-architect`) **and** the executor agent. No single one of those six is a natural sole owner. | Folding into any one skill |
| 6 — architecture-ddd.md as precedent | User confirmed: keep `rules/` leaf shape as-is (second interview round, this session). | Splitting rules by enforcement class |
| Follow-up | User's real ask: could `logging.md`'s own suffix/prefix table (`*Controller`, `*RepositoryAdapter`, …) make the rule self-apply without per-block citation? | — answered under References: no runtime mechanism does this inside a subagent today |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Keep `rules/` as-is; citation-per-block (already merged, PR #14) is the standing pattern | 9 | **Approved** |
| 2 | Fold Java-scoped rule content into the citing skills, one becomes "owner" | 2 | Rejected — invariant 1/2 strain, doesn't fix the subagent gap either |
| 3 | Keep Option 1, and separately promote `logging.md`/`naming.md`/`value-objects.md`'s mechanically-checkable subset into ArchUnit | 7 | Not approved this round — logged for later |

### Option 1 — keep `rules/`, citation-per-block (score 9)

**Motivator:** axis 9. Java-scoped rules are cross-cutting content consumed by six
different pieces; a dedicated leaf avoids picking an arbitrary skill as owner and keeps
invariant 2 (single owning file, cited by path) intact everywhere, including inside the
executor agent, which now cites the exact rule per block (PR #14).

**Pros:**
- Zero duplication: one file, six citers.
- `paths` still does real work in the one place it can: a human (or the main session)
  editing a generated project's Java files directly, outside any pipeline.
- Matches the fact-check below — the "always loads" premise doesn't hold for this
  meta-repo's steady-state use.

**Cons:** subagent execution still needs the citation, forever — that's a runtime
limit (§ 5 of `references/decision-matrix.md`: a subagent gets its `skills:` preload and
its own body, nothing path-triggered), not something rearranging files fixes.

**Points cut in the rubric:** § 8 criterion 4 (Enforcement) — `logging.md`,
`naming.md`, `value-objects.md` stay persuasion (Review-verified only per
`@.claude/rules/00-index.md`), citation makes the agent *read* them, not obey them
mechanically.

### Option 2 — fold into skills (score 2)

**Motivator:** the cost worry, taken at face value.

**Pros:** none found — reading a rule's content costs the same whether the file lives
in `rules/` or inside a skill; the byte count doesn't change by moving it.

**Cons:** breaks invariant 2 the moment a second skill needs the same norm (either
duplicate it, or cite the first skill's copy — skill-to-skill coupling, which
`architecture-ddd.md`'s own layering argument (`skills/` → `agents/` → `rules/`,
one direction) exists to prevent). Doesn't touch the actual gap: a subagent doesn't
auto-load a skill's `paths` any more than a rule's — confirmed in
`references/decision-matrix.md` § 5, "what the subagent receives." Invariant strain
caps the score at ≤ 4 regardless of the rest.

### Option 3 — add ArchUnit for the mechanically-checkable subset (score 7)

**Motivator:** axis 7 (mandatoriness) — `logging.md`'s per-class-type table (Logger
field present, level per outcome) is checkable by class-name suffix; `00-index.md`
itself already flags these three rules as "Review only."

**Pros:** moves a slice from persuasion to guarantee — a missing `Logger` on a
`*Controller` fails the build even if an agent's citation gets skipped or a human
edits by hand.

**Cons:** separate piece of work (`test-architect`/`archunit-installer`), only covers
presence, not log-message quality; not something the user asked to build this round —
logged here so the next session doesn't re-run this interview from zero.

## References

| Claim | Source |
|---|---|
| Subagents receive their own system prompt, `CLAUDE.md`, `git status`, delegation message, and `skills:` preload — never a `paths`-triggered rule or skill | `references/decision-matrix.md` § 5, "What the subagent receives" |
| `paths` auto-load is glob-based over touched files, not a permanent context injection | `@.claude/rules/00-index.md` § "How a rule enters context" |
| 5 rules use `**/*.java` (naming, code-quality, error-handling, logging, lombok); 6 are package-scoped; `architecture-ddd.md` has none, cited always | frontmatter of each file in `.claude/rules/`, checked this session |
| This meta-repo has no `.java` files of its own — `**/*.java` rules don't fire in normal meta-repo sessions; they fired this session only because `CustomerController.java` from a sibling demo repo was `Read` for diagnosis | `@CLAUDE.md` opening line ("not a Java application"); observed in this session |
| A cross-cutting norm cited by 6 consumers has no natural single skill-owner | inventory: `domain-modeling`, `persistence-architect`, `rest-api-architect`, `messaging-architect`, `test-architect`, `java-spring-boot-developer.md` all cite the same Java-scoped rules |
| `logging.md`, `naming.md`, `value-objects.md` are "Review only," zero mechanical check today | `@.claude/rules/00-index.md`, "Verified by" column |

## Propagation

| File | Change |
|---|---|
| — | None. Option 1 needs no file change — `java-spring-boot-developer.md`'s per-block citation already shipped in PR #14. This record only closes the reassessment; nothing new to write. |

Option 3 stays logged, unapproved, for whenever it's picked up: touches
`test-architect`/`archunit-installer`, separate task.

Goes to the generated project: **no** — this record is about this meta-repo's own
design (invariant 9); `.claude/decisions/` never ships.

