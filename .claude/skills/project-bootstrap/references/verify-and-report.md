# `project-bootstrap` — verify and report (steps 8 to 8.6)

Read at step 8 of `SKILL.md`, after the build-tool reference's § 8 — this file holds what
both tools share. `templates/…` paths are relative to the skill folder
(`${CLAUDE_SKILL_DIR}`). The Output contract the report renders stays in `SKILL.md`.

## 8 · Verify — both tools

Boundary test, mandatory before reporting success: temporarily write
`<domain-module>/src/main/java/<domain-package>/ArchHookProbe.java` with an
`import org.springframework.stereotype.Component;`, confirm the hook blocks the write,
and delete the file if one was left. If it doesn't block, `forbidden-imports.txt` is
wrong. The probe is the only business class this procedure ever writes, and it exists
only for the duration of the test.

`lombok.config` test, for the same reason and the same way: temporarily put a
`@Setter` with a field on the `@SpringBootApplication` class — the only one that
exists —, run `./mvnw -q test-compile` (Maven) or `./gradlew -q --no-daemon
compileTestJava` (Gradle), confirm the compilation stops with `Use of @Setter is
flagged according to lombok configuration`, and remove it. Compiling green means the
file isn't at the root, or `flagUsage` didn't make it in — the `lombok.md` rule would
run with no enforcement at all.

If any test depends on Testcontainers (Spring context with a real DB), guard it with a
condition that checks the Docker daemon (e.g. `@EnabledIf("dockerAvailable")`). Without
that, `./mvnw test`/`./gradlew test` goes red on any machine without local Docker — a
false negative that says nothing about the code. The test actually runs in CI, where
the runner has a daemon.

If the build fails, **fix it before reporting**. A bootstrap that delivers a red build
isn't finished.

**Autonomy test**, also mandatory: nothing in `<project>/` can cite a path that only
exists in Nerviz.

```bash
ls .claude/rules/ .claude/skills/ .claude/agents/ .claude/hooks/ArchHook.java .claude/schemas/extensions.json
grep -rn "blueprints/" .claude/ CLAUDE.md */CLAUDE.md   # must return nothing
grep -rn "decisions/" .claude/ CLAUDE.md */CLAUDE.md    # must return nothing — § 6.6 cuts these
java .claude/hooks/ArchHook.java schema </dev/null      # must exit 0, and without warning
```

The `schema` check above fails two different ways: exits 2 if some copy ended up with
invalid frontmatter, and exits 0 **with a warning** if `extensions.json` didn't make it
into the project. Read the output, not just the exit code.

Every skill the root `CLAUDE.md` routes to must have `SKILL.md` in the project. Every
agent the root `CLAUDE.md` delegates to must have `<name>.md` in `.claude/agents/`. A
dead path here is exactly the failure § 6.6 exists to prevent, and the `export` mode
already reported any citation that survived its cut — this is the second reading of the
same question, from the project's side.

## 8.5 · Generate the project README

Runs **after** Verify, on purpose: everything this step writes — the build status, the
boundary and Lombok probe results — only exists once step 8 has finished. A README
written earlier would either lie about the build or need a second pass to patch it.
Without this step the generated project ships with no README at all: `HELP.md` is the
Initializr's own boilerplate, and `CLAUDE.md` targets an AI reader, not a human seeing
the repository for the first time — `@.claude/lessons-learned/lessons-learned-004.md`
Gap 2.

Write two files at `<project>/`, from `templates/README.md.example` (English, the
default) and `templates/README.pt-br.md.example` (Portuguese) — same shape rule as every
other exemplar in this skill: read it, understand it, write the equivalent, don't
mechanically substitute. Same andaime rule as step 4/6 (`scaffold.md` § 4): the top comment explaining the
file is an exemplar is cut; a citation to a rule or invariant stays.

Every `{{...}}` placeholder resolves from data this procedure already computed —
**nothing here is invented**:

- `{{projectName}}`, `{{groupId}}`, `{{artifactId}}`, `{{targetDirectory}}` — step 3's
  inputs.
- `{{initCommand}}` — the literal `/init-project` invocation that started this run,
  arguments included, exactly as the user or the command line gave it. This is the
  README's only source for "how was this made" — the generated project has no other
  record of it, since git history starts at `git-publish`'s first commit, after
  generation.
- `{{blueprint.id}}`, `{{blueprint.oneLineDescription}}` — step 1/2, the `name` and a
  one-line cut of the blueprint YAML's own description. Don't paraphrase past what the
  YAML says.
- `{{javaVersion}}`, `{{springBootVersion}}`, `{{buildTool}}` — step 3's resolved
  versions, the same ones already in `SKILL.md` § Output contract. Never re-resolve, never
  restate from memory.
- `{{featuresList}}` — the active features from step 3/4.7, one bullet each.
- `{{boundedContext}}` — step 3's input, defaulting to `{{artifactId}}` when the caller
  gave none. It is the first segment of every topic name
  (`@.claude/rules/messaging.md` § Topics and serialization) and nothing else in the
  project declares it, so it is written into the root `CLAUDE.md` even when messaging is
  not an active feature: the case that adds Kafka later reads the line instead of choosing
  a prefix inside one use case.
- `{{skillsList}}`, `{{agentsList}}` — one bullet per entry actually written into the
  project (`ls .claude/skills/`, `ls .claude/agents/` — read the disk, not the `export`
  manifest: the manifest lists what *can* travel, not what a given blueprint's feature
  set produced), each with a one-line "Why" taken from that skill's or agent's own
  `description`, not invented here.
- `{{outputContractBlock}}` — the exact block this step's successor (`SKILL.md` § Output contract)
  renders, pasted verbatim. The README and the report given to the user are the same
  text; this is not a second, independently-written summary that can drift from the
  first.
- `{{nextStepsBlock}}` — the same "Next steps" list as the Output contract, verbatim.

Both files are self-contained: reading only `README.md` (or only `README.pt-br.md`)
answers "what is this, how was it made, what can I do with it" with no need to open
`CLAUDE.md` or ask the meta-repo. The cross-link at the top of each is the only coupling
between the two.

## 8.6 · Write the audit genesis record

Runs **after** the README (8.5), for the same reason: the Output contract block it
embeds only exists once step 8 has finished. Fixes a different gap than 8.5 —
`@.claude/lessons-learned/lessons-learned-004.md` Gap 1: `.claude/audit-usage/` (§ 6.6,
`ensure_dirs`) exists in the freshly generated project, but the hook that fills it
(`ArchHook.java audit`) has never run there, because that hook only fires from a live
session rooted at the *generated* project, and the run that creates the project happens
from a session rooted at the *meta-repo* instead. Without this step the trail is
structurally empty on day one — not a missing report, a run nobody could have recorded.

Write `<project>/.claude/audit-usage/GENESIS.md` from
`templates/GENESIS.md.example`, same read-it-understand-it-write-the-equivalent rule as
every other exemplar here. `{{initCommand}}`, `{{blueprint.id}}`,
`{{blueprint.oneLineDescription}}`, `{{groupId}}`, `{{artifactId}}`, `{{buildTool}}`, and
`{{outputContractBlock}}` resolve exactly as documented for the same placeholders in
step 8.5 — same values, second destination. `{{startIso}}` is this run's own start
timestamp (interview's first question, step 1); `{{endIso}}` is now, at the moment this
step runs; `{{buildStatus}}` is step 8's own `PASSED`/`FAILED` verdict, restated, never
re-derived.

**Never overclaim fidelity.** This file names itself a reconstruction, not a hook
report, and stays that way — no invented per-tool-call timeline, no cost, no ranked
stages, none of the fields `ArchHook.java audit`'s own reports carry. A later
`/audit-usage` reader must be able to tell this entry apart from every report that
follows it in the same directory. Written once; a second `/init-project` run never
overwrites it (idempotence — § Preconditions already stops before this step if the
project exists).

