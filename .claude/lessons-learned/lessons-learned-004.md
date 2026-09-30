# Lessons learned — `/init-project` run for demo-clean-arch-single-module

Document intended for the **meta-repo** (`claude-spring-architect`), not for the
generated project.

Generated project: `demo-clean-arch-single-module` (blueprint
`clean-architecture-single-module`, Maven, groupId `com.icelfernandes`). Gaps found
while running `/init-project --blueprint clean-architecture-single-module --build maven
--artifact demo-clean-arch-single-module` end to end via the `project-initializer`
agent, from a session rooted in this meta-repo.

---

## Gap 1 — the generated project's audit trail is empty on day one, structurally, not by omission

**Where:** `.claude/skills/project-bootstrap/SKILL.md` step 7 part 3-4 (writes
`templates/settings.json.example`'s ten `audit` hook triggers and
`.claude/audit-usage/pricing.json` into the project); `.claude/hooks/ArchHook.java`
`audit` mode; `@CLAUDE.md` known pitfall "The `audit` mode is off in this repository, on
purpose."

**What happened:** After `/init-project` finished — build green, first commit made —
`.claude/audit-usage/` in the generated project held only `pricing.json`, no run report.
Asked why: the hooks that write the trail (`audit prompt/call/perm/fail/agent/compact/
stopfail/close/flush`) are wired in the **generated project's own**
`.claude/settings.json`, and that file — like every `settings.json` — is only read at
the startup of a live Claude Code session rooted there (`@CLAUDE.md` known pitfall,
same mechanism). The entire `/init-project` run, including this one, executes inside a
session rooted at the **meta-repo**, which deliberately does not wire the audit hooks at
all (`@CLAUDE.md`: "this meta-repo doesn't create `.claude/audit-usage/`... copying
those hook entries here would start auditing the design of the tool instead of its
use"). So the one run best worth recording — the run that created the project — is the
one run structurally guaranteed to leave no trace in the mechanism designed to record
runs, because it happens before any session has ever opened with the generated project
as its root.

This isn't a bug in the trail's design for its intended case (`/new-feature`,
`java-spring-boot-developer`, and later runs, all executed *inside* the generated
project) — it's a blind spot specific to the one run that happens *before* the project
can host a session of its own.

**Suggested fix at the source (needs a decision record if adopted — it touches
`@CLAUDE.md`'s own reasoning about why this repo doesn't audit itself):**

The constraint that must hold either way: this meta-repo still must not turn on
self-auditing of its *own* development (`@CLAUDE.md`'s existing reasoning stays correct
for that case). What's being proposed is narrower — auditing one specific, delimited
action (a single `/init-project` run) on behalf of a project that doesn't exist yet to
host its own session.

1. `project-initializer` (or `project-bootstrap`'s own step 7) could keep a lightweight,
   local record of the run *as it executes* — not via the same hook mechanism (that
   requires a live session already rooted at the target, which is exactly what's
   missing here), but as data the agent already has: the resolved `initCommand`, the
   blueprint chosen, the interview answers, timestamps, and ultimately the Output
   contract block from step 8. All of this is already collected for the Output contract
   itself and, per `lessons-learned-004`'s Gap 2 fix below, for the new README.
2. On successful completion (build passed, first commit made), write that record as the
   **first** entry into `<project>/.claude/audit-usage/` — either a synthetic report
   shaped like `ArchHook.java audit`'s own Markdown format (so `/audit-usage`, run later
   inside the project, has one entry from birth instead of a starting silence), or a
   dedicated `GENESIS.md` if reusing the exact report shape turns out to overclaim
   fidelity the hook itself didn't produce (no per-tool-call granularity exists for a
   run that had no hook watching it — honesty about that gap matters more than shape
   consistency).
3. Whichever shape is chosen, it must be clearly distinguishable from a hook-produced
   report (a header line naming it as a meta-repo-side reconstruction, not a live trail)
   — conflating the two would let a later `/audit-usage` reader believe granular
   per-tool-call cost tracking existed for the bootstrap run when it didn't.

**Why this wasn't fixed directly in this pass:** unlike Gap 2, this isn't a
self-contained addition to one skill — it proposes writing into `.claude/audit-usage/`
from *outside* the hook mechanism that owns that directory everywhere else in this
repo's design, which is exactly the kind of tension `@CLAUDE.md` invariant 6 ("if a rule
must always hold, it's a hook... not prose in markdown") flags. Adopting it needs an
explicit call on whether a meta-repo-authored synthetic entry belongs in a directory
whose entire premise, elsewhere, is "the hook wrote this, nothing else did."

---

## Gap 2 — the generated project ships with no README.md

**Where:** `.claude/skills/project-bootstrap/SKILL.md` — no step wrote one. `HELP.md`
(Initializr boilerplate) and `CLAUDE.md` (written for an AI reader, routing and
invariants, explicitly capped under 200 lines) both exist, but neither answers "what is
this, how was it made, what can I do with it" for a human opening the repository for the
first time.

**What happened:** After `/init-project` finished for `demo-clean-arch-single-module`,
the project had no README at all. One was written by hand, after the fact, in the
generated project directly — outside the generator, from chat context that won't exist
the next time this blueprint runs.

**Fix actually applied at the source, this pass:**

1. New step **8.5 · Generate the project README** in
   `.claude/skills/project-bootstrap/SKILL.md`, placed *after* Verify (step 8) — the
   build status and the boundary/Lombok probe results it reports only exist once step 8
   has run; writing the README earlier would mean patching it afterward or reporting a
   status that isn't real yet.
2. Two new exemplars: `templates/README.md.example` (English, the default) and
   `templates/README.pt-br.md.example` (Portuguese, cross-linked, same content).
   Sections: origin (states plainly the project was generated from this meta-repo, with
   the literal `/init-project` command and resolved coordinates), blueprint chosen,
   stack and versions, a short explanation of the `.claude/` architecture the project
   inherited (hooks enforce / skills procedure / agents isolate / rules are the leaf —
   same shape as this meta-repo's own `@CLAUDE.md`, one level down) plus the actual
   lists of skills and agents copied into *this* project (not the full catalog this
   skill could copy — only what step 6.7/6.8 actually triggered), the Output contract
   block verbatim (same text the user already sees in the terminal — not a second,
   independently written summary that can drift from it), and the same Next steps list.
3. Every `{{...}}` placeholder resolves from data the procedure already computed by step
   8 — no invented content, same discipline as every other exemplar in this skill.
4. Preconditions, Output contract (`Docs:` line), and Skill contract's `Writes` list
   updated to mention the two new files.

**Why English default / Portuguese option:** matches this session's working language
split — the meta-repo's own documentation is English, the operator drives it in
Portuguese day to day.
