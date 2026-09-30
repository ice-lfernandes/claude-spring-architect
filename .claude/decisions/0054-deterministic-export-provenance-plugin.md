# 0054 · Deterministic `export` mode, pulled by `arch-adopt` through a plugin

- **Date:** 2026-09-26
- **Scenario:** `context-plugin.md` records a brainstorm about packaging this `.claude/`
  as a Claude Code plugin, and closes with a caveat: the plugin solves *transport*, while
  the measured bottleneck is *transformation and provenance*. The request is to turn that
  initiative into a staged plan, one feature branch per stage, each with its own
  deliverable.
- **Decision:** Option 1 — Form 7c (`export` mode in `ArchHook.java`, its lists data in
  `.claude/schemas/extensions.json`) pulled by a Form 2 skill `arch-adopt`, delivered by
  a plugin that carries only that skill. Seven branches across two stages, below.
- **State:** approved by Lucas Fernandes, on 2026-09-26

## Interview

| Axis | Answer | Forms/options it eliminated |
|---|---|---|
| 1 — symptom | Every change to `.claude/` here is re-applied by hand into the sibling `demo-clean-arch-single-module`; a project that did not come from `/init-project` has no way in at all | "create nothing" |
| 2 — trigger | A human command inside the **target** project, which pulls from this repo | Forms 4, 5, 6; and the whole push direction |
| 5 — nature | The transformation is mechanical: verbatim copy + `paths` derived from `packages.map` + `decisions/` citations stripped | Form 1/2 as the *transformation* itself |
| 7 — mandatoriness | It runs on other people's machines, without the author present, possibly behind a paywall — the same input must produce the same tree | A skill in prose executing the transformation |
| 8 — destination | Both: `ArchHook.java` and `schemas/extensions.json` travel whole in step 7, and `arch-adopt` must travel too, so a project can update itself | — |
| 16 — existing mode | No mode of `ArchHook.java` copies or transforms `.claude/` | Form 7a alone |
| Who runs it | Nobody special. The author runs the same `/arch-adopt` on the example project that a stranger runs on theirs | Authorship gate; `permissions.deny`; a separate sync path; two mechanisms |
| Entry point | Plugin in a new marketplace repository, carrying **only** the skill | Payload embedded in the plugin (a copy of `.claude/` per release — the same drift, relocated) |
| Transport | HTTPS tarball at a ref, with an optional `Authorization` header from a `${VAR}` | `git clone` as the only path — it binds authorization to a git host, and a future licensing endpoint is not a git remote |
| Version | The latest release tag by default; `--ref` to pin; `main` only with an explicit `--ref main` | Always `main` |
| Update policy | Refuse a dirty worktree, then overwrite. `git diff` is the review, `git checkout` is the undo | Local-edit preservation (leaves stale norms behind in silence); per-file prompting |
| CI verification | `export` into a temp dir twice, require identical output, then run `schema` and `check` over the result | A versioned `examples/` tree; a cross-repo checkout with a PAT |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `ArchHook.java export` + manifest in `extensions.json`, pulled by an `arch-adopt` skill delivered as a plugin | 9 | **Proposed** |
| 2 | Same, but the transformation stays prose shared by `project-bootstrap` and `arch-adopt` | 5 | Rejected — it runs on the buyer's machine: a model interpreting it may produce a different tree per run, and the CI cannot execute a skill |
| 3 | Push model: an `export` the author alone runs into each target, plus a gate restricting it | 4 | Rejected — the gate is not enforceable against a shell, and it creates two mechanisms where one suffices. Dropped by the user |
| 4 | Form 1 of `context-plugin.md` — a pure plugin carrying the whole `.claude/` | 3 | Rejected — plugin layout has no `rules/`, plugin skills are namespaced and unaudited, and a generated project would depend on an external plugin (invariant 9) |

### Option 1 — `export` mode + `arch-adopt` + marketplace plugin (score 9)

**Motivator:** axis 7 — the transformation runs unattended on other people's machines;
and axis 1 — the manual re-application is the observed, repeated failure.

**Pros:** one implementation of the transformation, owned by code, with three consumers
(`project-bootstrap`, `arch-adopt` on a first install, `arch-adopt` on an update); the
copy lists leave prose and become data in `schemas/extensions.json` (invariants 7 and 10);
the pull direction means nobody needs write access to anybody's repository; the plugin
carries only the skill, so the norms have a single source of truth and no release-time
copy; the provenance stamp makes "how far behind am I" answerable for the first time.

**Cons:** `export` is the only mode that writes **outside** its own repository —
mitigated by a mandatory explicit destination, `--dry-run`, and the refusal on a dirty
worktree. A tarball fetch adds network to a path that had none; `--source <local path>`
covers the offline and unreleased-change cases. `arch-adopt` is a creation skill that
nevertheless **does** travel into the generated project, unlike `project-bootstrap` and
`init-project` — deliberate, and the reason is that the project must be able to update
itself without the plugin.

**Points cut in the rubric:** criterion 8 (trust surface) — a mode that writes to an
arbitrary path, plus a fetch over the network, is wider than anything else in the file.

### Option 2 — transformation in prose (score 5)

Cheapest, and it does give the transformation a single owner. But under axis 7 the
product is the update itself: each buyer pays tokens for a model to re-derive the same
tree, and two buyers may not get the same one. The CI cannot run a skill, so the only
determinism check available disappears with it.

### Option 3 — push with an authorship gate (score 4)

A gate stops the runtime, never a shell; `git config user.email` is forged in one line.
Distribution is controlled by repository visibility, and that is an access decision, not
a mechanism. Two mechanisms (author sync, stranger adopt) also means the author's path
stops being the one that gets exercised.

### Option 4 — pure plugin (score 3)

Recorded so nobody re-proposes it: the three blockers in `context-plugin.md` § Form 1 are
unchanged, and invariant 9 caps it.

## Stages and branches

### Stage 1 — the transformation, in this repository

| Branch | Deliverable |
|---|---|
| `feat/export-manifest` | `export` block in `.claude/schemas/extensions.json`: payload list, exclusions (`project-bootstrap`, `init-project`, `decisions/`, `blueprints/`), per-rule glob derivation from `packages.map`, body transforms (blueprint vocabulary into `naming.md`, the `00-index.md` paragraph, `decisions/` citations stripped). `ArchHook.java schema` validates it: every source path listed exists. No behavior change |
| `feat/archhook-export-mode` | `case "export"`: `export <dest> --blueprint <id> [--dry-run]`. Writes the transformed `.claude/` plus `.claude/.arch-provenance.json` (ref, sha, date, blueprint, manifest hash). Verified by exporting and diffing against `demo-clean-arch-single-module/.claude/` |
| `feat/ci-export-determinism` | CI job: export twice into temp dirs, require byte-identical output, then run `schema` and `check` over the result. This is the determinism proof — no example tree is versioned |
| `feat/bootstrap-invokes-export` | Steps 6.6/6.7/6.8/7 of `project-bootstrap` replaced by the call; the copy tables leave prose (single owner: the manifest). Verified by an `/init-project` run whose `.claude/` equals the mode's output |
| `feat/doctor-provenance` | ✅ The stamp carries a digest per exported file, and `doctor`'s Provenance line recomputes them: blueprint, ref and date when nothing changed, otherwise the files edited since — which is exactly what an update would overwrite. Three states: origin repository (writes stamps, carries none), no stamp, stamp. **"How far behind" is deliberately not here** — it cannot be answered without reaching the source, and `doctor` opens no network; `arch-adopt` answers it when it fetches |

### Stage 2 — delivery, pulled by the target project

| Branch | Deliverable |
|---|---|
| `feat/skill-arch-adopt` | ✅ Skill `arch-adopt` (Form 2). Resolves the ref (`git ls-remote --tags`, `--ref`, `--source <path>`), fetches the tarball with an optional `Authorization` header, refuses a dirty worktree or a non-git directory, detects install vs. update from the stamp, offers the closest blueprint by comparing `src/main/java` against each `packages.map` (or a `custom.template.yaml`, with its cost stated), invokes `export`, verifies with `schema` and `doctor`, and hands over the diff without committing. Travels into the project, so it is in `export.skills.include` |
| `claude-spring-architect-marketplace` (new repo) | 🟡 Staged for review, not published. Plugin named `spring-architect` inside marketplace `claude-spring-architect`, so the first install is `/spring-architect:arch-adopt` and the copy it writes into the project is plain `/arch-adopt`. `.claude-plugin/{marketplace,plugin}.json`, `skills/arch-adopt/SKILL.md` (the only vendored file), `.plugin-source.json` recording where that copy came from, `sync.sh` to refresh it, a CI job that fails when it has drifted, and a README. `claude plugin validate .` passes. **Open before publishing:** the source repository has no tag yet, so `latest-tag` falls back to `main`; and a private source needs a `SOURCE_READ_TOKEN` secret, without which CI skips the drift check rather than reporting a 404 as drift |

### Prepared, not built

Monetization. The transport is an indirection from the first commit: base URL and auth
variable name are data, so moving from GitHub to a licensing endpoint changes the
descriptor, not the code path. No literal credential anywhere — invariant 11, the same
rule `.mcp.json` already obeys. Authentication governs who downloads an update, never
what happens to the files afterwards.

## References

| Claim | Source |
|---|---|
| A new mode is a `case` in `ArchHook.java`, never a second hook file | Designer skill § Out of scope |
| Lists a mode reads live in `extensions.json`, not in the Java | `@CLAUDE.md` invariant 10 |
| Adding a blueprint must not require editing a skill | `@CLAUDE.md` invariant 7 |
| The generated project must not depend on this repo at runtime | `@CLAUDE.md` invariant 9 — the plugin is uninstallable after adoption, and `arch-adopt` travels into the project |
| Plugin layout has no `rules/`; plugin skills are namespaced; the audit trail ignores them | `context-plugin.md` § Form 1; `@claude-help.md` § 10 |
| A mode invoked by hand or by a skill, with no lifecycle event behind it, has precedent | `ArchHook.java compose`, `audit summary` |
| Fetching versioned content at runtime instead of vendoring it has precedent | Spring Initializr's `starter.tgz`, `@CLAUDE.md` invariant 8 |
| Only `${VAR}` / `${VAR:-default}` / `oauth` / `headersHelper`, never a literal secret | `@CLAUDE.md` invariant 11 |
| The transformation is already specified mechanically | `project-bootstrap/SKILL.md` §§ 6.6, 6.7, 6.8, 7 |

## Propagation

Stage 1 is done — `feat/export-manifest`, `feat/archhook-export-mode`,
`feat/ci-export-determinism`, `feat/bootstrap-invokes-export` and
`feat/doctor-provenance`. Stage 2 is the plan.

Two things the second branch settled that the plan had left open. **The demo project is
not an oracle:** exporting and diffing against `demo-clean-arch-single-module/.claude/`
returns 64 differing files, almost all of them drift accumulated since it was generated —
the very problem this initiative exists to close — so the check that means something is
idempotency (two exports byte-identical outside the stamp), which branch 3 automates.
**The stamp is the one non-reproducible file**, since it carries `exported_at`; every
comparison excludes it.

Branch 3 found the third: **`vertical-slice` names no `.rest` and no `.persistence`
package** — in that architecture both live inside each slice — so a derivation that only
reads `packages.map` had nothing to select and the export refused to run. The rule's
territory does exist there; it is one of the blueprint's own `architecture_paths`. Hence
`fallback_architecture_paths_containing` in `export.derived_paths`: when the package
selector finds nothing, the glob is picked from `architecture_paths` **by token**, never
as a union of all of them — a rule that also loads on domain edits is noise, and rule 4
of step 6.6 ("no defensive union") still holds. Found only because the CI job exports
every blueprint instead of the one in front of us.

Branch 4 removed two CI steps rather than repointing them. Both grepped the prose tables
that step 6.6/6.7/6.8 carried — a rule with a territory appearing in the derivation
table, and every rule, skill and agent having a row in the copy list — and both are now
checks inside `ArchHook.java schema`, which fires on every edit under `.claude/` and not
only in CI. The reverse direction was missing and is new: a rule whose `paths` names a
package and has no `derived_paths` entry is reported by name, the other half of gap 8 of
lessons-learned-001.

Branch 6 moved the transport descriptor out of `export` and into a top-level `source`
block. The exported `extensions.json` drops `export` (its lists describe this repository)
and keeps `source`, because a project that cannot read where it came from cannot update
itself — the skill reads `base_url`, `git_url` and `auth_env` from there and writes none
of them from memory. `git_url` is new and exists so `latest-tag` resolves through
`git ls-remote --tags`, with no API client and no JSON to parse. Note for stage 2: the
repository has **no tags yet**, so `latest-tag` currently falls back to `main`, and the
skill says so instead of pretending to pin.

| File | Change |
|---|---|
| `.claude/schemas/extensions.json` | ✅ A top-level `source` block (transport descriptor) **kept** in the exported copy while `export` is dropped — that is what lets a project update itself; `export` block: `copy`/`merge`/`optional_copy`, `ensure_dirs`, `gitignore_lines`, rules/skills/agents sets, `derived_paths`, `body_transforms` (branch 1). `arch-adopt` joins `skills.include` in branch 6 |
| `.claude/hooks/ArchHook.java` | ✅ `schema` cross-checks the manifest at sweep time: paths exist, `include`/`exclude` cover every skill and agent on disk, a rule with a package territory has a `derived_paths` entry, transport is https with a `{ref}` and no literal credential (branches 1 and 4). ✅ `export` mode: YAML reader for `packages.map`, `architecture_paths` and the vocabulary comment; `paths` derivation with a fallback to `architecture_paths`; citation cuts; the rewrites; the stamp with a digest per file (branches 2, 3 and 5). ✅ `doctor` Provenance line (branch 5) |
| `CLAUDE.md` | ✅ § Commands `schema` row; invariant 9 now names the `export` block as the copy list (branch 1). ✅ § Commands `export` row (branch 2) |
| `.claude/hooks/ArchHook.java` | `export` mode; `doctor` provenance section (branches 2 and 5) |
| `.claude/skills/project-bootstrap/templates/settings.json.example` | Nothing — the manifest is read by a mode, not registered as a hook |
| `.claude/skills/project-bootstrap/SKILL.md` | ✅ Steps 6.6/6.7/6.8/7/7.5 collapsed into § 6.6 (one command) plus § 7 (what the installed enforcement does); the copy tables, the derivation table and the correction lists are gone — the manifest owns them. 1314 → 1090 lines. `## Skill contract` rewritten (branch 4) |
| `.claude/skills/claude-code-architect-designer/**`, `.claude/agents/*.md`, `.claude/skills/{use-case-design,new-feature}/**`, `.github/PULL_REQUEST_TEMPLATE.md`, `.github/ISSUE_TEMPLATE/feature_request.yml` | ✅ Every reference to the deleted steps now names the `export` block (branch 4) |
| `.github/workflows/validate.yml` | ✅ `export-determinism` job: two exports per blueprint byte-identical outside the stamp, no surviving dead citation, the exported tree carries none of the meta-repo-only pieces and passes its own `schema` (branch 3). **Branch 4 must also rewrite the existing `norm with territory is in the bootstrap's derivation table` step** — it greps a prose table that branch 4 deletes; its new source is `export.derived_paths` |
| `.claude/skills/arch-doctor/SKILL.md` | ✅ `description` names provenance; the body says not to offer the update when files were edited locally (branch 5) |
| `.claude/skills/arch-adopt/SKILL.md` | New skill (branch 6) |
| `CLAUDE.md` | § Commands: `export` row; routing row for `arch-adopt`; § Known pitfalls: a stale `.claude/` is silent until `arch-doctor` reads the stamp (branches 2, 4, 5, 6) |
| `context-plugin.md` | Superseded by this record once approved |

Goes to the generated project: **the `export` mode, the manifest, and `arch-adopt`,
yes** — step 7 copies `ArchHook.java` and `schemas/extensions.json` whole, and the
manifest lists `arch-adopt` in the payload so a project can update itself.
**`project-bootstrap` and `init-project`, no** — creation skills, unchanged exclusion.
