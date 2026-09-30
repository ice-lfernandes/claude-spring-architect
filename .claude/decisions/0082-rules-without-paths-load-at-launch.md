# 0082 · Every rule declares `paths`, none loads at launch, and no rule loads on `ArchHook.java`

- **Date:** 2026-09-29
- **Scenario:** lessons-learned-015 topic E — "Rules sem `paths` carregam em toda sessão"; widened at review: the five `**/*.java` rules load whenever `.claude/hooks/ArchHook.java` is read, and `security.md`'s name is broader than its content
- **Decision:** option 1 — every rule declares `paths` (Java as `**/src/**/*.java`), `security.md` renamed `personal-data.md`, and `export.retired` deletes the old name from the target
- **State:** approved by Lucas Fernandes, 2026-09-29
- **Goes to the generated project:** yes — rules travel whole through `export.rules`; `derived_paths` rewrites their globs per blueprint; `ArchHook.java` and `extensions.json` travel whole

## Interview

Answered from the runtime docs, this session's own context, and the source; no axis needed a
question.

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | **Observed**, not anticipated: a session in this repo that touched no `.java` opened with `00-index.md`, `architecture-ddd.md` **and** `security.md` in context — three rules, not the two the lesson names (`security.md` postdates it). ~15 KB, ~3.7k tokens, every session. In a generated project: `00-index.md` + `security.md` (~2.7k tokens) — `architecture-ddd.md` already gets `paths` there | Create nothing |
| 1b — Doc says the opposite | `00-index.md`'s frontmatter ("never auto-loaded"), its § How a rule enters context item 2, `security.md`'s "No `paths`" paragraph, `frontmatter-fields.md:125` ("only enters context through explicit citation"), and `templates/rule.md.example:46` all claim a rule without `paths` loads only when cited. The runtime: "Sem frontmatter `paths`, a rule carrega no lançamento" (`claude-code-docs/01-rules-claude-md-e-memoria.md:103`) | — |
| 1c — Over-match (raised at review) | `code-quality`, `error-handling`, `logging`, `lombok`, `naming` declare `**/*.java`. `.claude/hooks/ArchHook.java` matches it, so every read of the hook source — here, where it is the only Java there is, and in every generated project, which carries the same file — pulls five norms about Spring application code into context. None of them applies to the hook | — |
| 1d — Name (raised at review) | `security.md` covers **personal data at rest and in transit** — columns, `jsonb` payload columns, retention, message bodies, cross-team receivers. It explicitly **excludes logs** (owner: `logging.md` § Masking candidates) and announces secrets and authn/authz as future sections that were never written. The name promises a scope the file does not hold | — |
| 5 — Nature | Frontmatter data per rule + entries in `derived_paths`/`derived_paths_exempt_globs` | New pieces of any kind |
| 8 — Destination | Both | — |
| 9 — Integration | `derivePaths` (`ArchHook.java:1579`) **replaces** an existing `paths:` block, so a master-file example is overwritten at export. `checkRuleTerritories` (`:2566`) fails a rule with a non-exempt glob missing from `derived_paths`. Every piece that decides a payload already cites `security.md` by path (15 files outside `decisions/` and lessons). **`export`/`arch-adopt` never delete a file from the target** — no retire mechanism exists | Moving the index (option 3) |
| 10 — Cost of error | A rule given a glob that never matches is unloaded in silence — the failure `derived_paths` exists for. A renamed rule whose old copy stays in an adopted project is a norm in two places there (invariant 2), the old one loading at launch | Rename without retiring the old file |

## Glob choice

`**/src/**/*.java` instead of `**/*.java`:

| Path | `**/*.java` | `**/src/**/*.java` |
|---|---|---|
| `src/main/java/…/Order.java` (single module) | ✅ | ✅ |
| `domain/src/main/java/…/Order.java` (multi-module) | ✅ | ✅ |
| `src/test/java/…/OrderTest.java` | ✅ | ✅ |
| `.claude/hooks/ArchHook.java` | ✅ — the over-match | ❌ |

Architecture-independent (no package name), so it goes into `derived_paths_exempt_globs`
beside `**/src/test/**` — that list's own definition — and replaces `**/*.java` there once no
rule declares it.

For the personal-data rule: `**/src/**/*.java` + `**/db/migration/**`. It covers a domain
event's fields — the observed leak started in a domain event — which the per-boundary
derived glob of the first draft (`rest`, `persistence`, `messaging`) missed. Both globs are
exempt, so no `derived_paths` entry is needed. The cost: it loads on every Java edit, like
the other five; that is the price of the field name not saying what the value is
(`securityNumber` held a CPF).

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Everything in option 2, **plus** rename `security.md` → `personal-data.md`, and an `export.retired` list that `export` deletes from the target | 8 | **approved** |
| 2 | `paths` on all three launch-loaded rules (index → `.claude/rules/**`; `architecture-ddd.md` → example rewritten by `from_blueprint`; `security.md` → `**/src/**/*.java` + `**/db/migration/**`); the five `**/*.java` rules → `**/src/**/*.java`; fix the five false statements. No rename | 7 | not chosen — keeps a name broader than its content |
| 3 | The lesson's suggestion: move the index out of `rules/`, rewrite 46 citations in 24 files; the rest as option 2 | 4 | rejected |
| 4 | Only fix the docs; accept the launch load | 3 | rejected |
| 5 | Create nothing | 2 | rejected |

### Option 1 (score 8)

**Motivator:** axes 1, 1c, 1d.

**Mechanism.**

1. Everything in option 2.
2. `git mv .claude/rules/security.md .claude/rules/personal-data.md`; title and scope
   paragraph lose the "secrets and authn/authz announced for this file" sentence. Those two
   move to the planned table of `00-index.md` as **separate** future files
   (`secrets.md`, `authorization.md`), each with its own territory when a case demands it.
3. Rewrite every citation (`rules/security.md` → `rules/personal-data.md`): 15 files —
   the design skills, `new-feature`, the three spec templates, `java-spring-boot-developer`,
   `00-index.md`, `api-rest.md`, docs. `decisions/` and `lessons-learned/` are history and
   keep the old name.
4. `extensions.json`, `export`: `"retired": [".claude/rules/security.md"]` with a
   `$comment` citing this record. `ArchHook.java export`: for each entry, delete it from
   `<dest>` when present and name it in the report (`--dry-run` names it without deleting).
   `arch-adopt` already refuses a dirty worktree, so the deletion is one `git checkout` from
   undone. `schema`: an entry in `retired` that still exists in **this** repo fails by name
   — a retired file that is also shipped is a contradiction.
5. `build` → jar rebuilt; `schema </dev/null` exits 0; probe:
   `export <tmp> --blueprint <id> --dry-run` over a target holding a `security.md` lists it
   as retired.

**Pros:** the name says what the file holds; adopted projects converge instead of carrying
two copies; `retired` is reusable for the next rename, which is the failure mode option 3
was rejected for.

**Cons:** 15 citation edits; a Java change to a mode (`export`) — Form 7c edit — for a
single file today.

**Points cut:** Java for one entry (−1); citation churn (−1).

### Option 2 (score 7)

**Motivator:** axes 1, 1b, 1c.

1. `00-index.md` — `paths: [".claude/rules/**"]`, added to `derived_paths_exempt_globs`.
2. `architecture-ddd.md` — an example `paths` (the layered blueprint's
   `architecture_paths`); the existing `{ "from_blueprint": "architecture_paths" }` rewrites
   it at export. In this repo it never matches, which is the point.
3. `security.md` — `paths: ["**/src/**/*.java", "**/db/migration/**"]`.
4. `code-quality`, `error-handling`, `logging`, `lombok`, `naming` — `**/*.java` →
   `**/src/**/*.java`. `derived_paths_exempt_globs`: add `**/src/**/*.java`,
   `.claude/rules/**`; drop `**/*.java`.
5. Rewrite the five false statements (axis 1b) and the `**/*.java` precedent line in
   `frontmatter-fields.md:129`.
6. `schema </dev/null` exits 0; `export <tmp> --blueprint <id> --dry-run` shows the derived
   `paths` on `architecture-ddd.md` and the literal ones everywhere else.

**Pros:** zero launch cost; no rule on the hook source; no Java; no stale file anywhere.

**Cons:** keeps a name broader than its content (axis 1d).

**Points cut:** the misleading name stays (−1); the index loads on any rule read, including
a `paths` auto-load if the runtime counts it (−1, unverified, harmless).

### Option 3 (score 4)

Same effect for the index, bought with 46 citation edits, and without a `retired` mechanism
the old `rules/00-index.md` stays in every adopted project, loading at launch. If option 1
is approved, `retired` removes that objection — but the index move still buys nothing that
`paths` does not.

### Option 4 (score 3)

Honest docs, same cost every session, and the hook read keeps pulling five norms.

## Found while implementing

- **A CRLF checkout skipped every rule's frontmatter silently.** With `core.autocrlf`, the
  rules arrived as `\r\n`, and the LF-only frontmatter parse in `exportRules` and
  `checkRuleTerritories` found no `paths` block: `export` wrote nothing derived and
  `schema` checked nothing, both exit 0. Both now normalize `\r\n` to `\n` on read; the
  rules were rewritten LF.
- **An optional `derived_paths` entry with no match stripped `paths`.** Before this record
  that was harmless ("citable"); after it, a stripped `messaging.md` would load at launch
  in every project without a broker. `derivePaths` now keeps the master example globs,
  which name a package the blueprint doesn't have and so match nothing.
- **`retired` deletes only under the target's `.claude/`.** An entry resolving elsewhere
  exits 2 before anything is written; `--dry-run` only lists; a path also shipped by the
  same export (`out`, `binary_copy`) is never deleted; and `schema` fails when a retired
  path still exists in this repo — it would be shipped and deleted at once.

## References

| Claim | Source |
|---|---|
| A rule without `paths` loads at launch; with `paths`, when a matching file is read | `.claude/claude-code-docs/01-rules-claude-md-e-memoria.md:102`–`:105` |
| Three rules loaded in a session that touched no `.java` | This session's opening context, 2026-09-29 |
| `derivePaths` replaces the master file's `paths` block | `ArchHook.java:1579`, `replacePathsBlock` |
| A non-exempt glob outside `derived_paths` fails `schema` | `ArchHook.java:2566` `checkRuleTerritories`; `extensions.json:444` |
| `security.md` excludes logs; secrets/authn announced, unwritten | `.claude/rules/security.md` § Scope of this version |
| `export`/`arch-adopt` never delete in the target | no delete path in `ArchHook.java` `export`; `.claude/skills/arch-adopt/SKILL.md` |
| One owner per norm; lists as data | `@CLAUDE.md` invariants 2, 10 |

## Propagation

| File | Option 2 | Option 1 adds |
|---|---|---|
| `.claude/rules/00-index.md` | `paths`; frontmatter comment; § How a rule enters context; paragraphs on `security.md`/`architecture-ddd.md` | table row renamed; `secrets.md`, `authorization.md` in the planned table |
| `.claude/rules/architecture-ddd.md` | example `paths` | — |
| `.claude/rules/security.md` | `paths`; "No `paths`" paragraph | renamed `personal-data.md`; scope paragraph |
| `.claude/rules/{code-quality,error-handling,logging,lombok,naming}.md` | `**/src/**/*.java` | — |
| `.claude/schemas/extensions.json` | exempt globs | `export.retired` |
| `.claude/hooks/ArchHook.java` + jar | — | `export` deletes `retired`; `schema` checks it |
| 15 citing files | — | `rules/personal-data.md` |
| `frontmatter-fields.md` | § Rule: loads at launch without `paths`; precedent glob | — |
| `templates/rule.md.example` | exemplar line | — |
| `@CLAUDE.md` § Known pitfalls | one bullet: a rule without `paths` loads every session; `**/*.java` matches the hook source | `retired` in the `export` command row |

Written by the main thread, not delegated.
