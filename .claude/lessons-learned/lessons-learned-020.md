# Lessons learned 020 — `/init-project` for `banking-app`: three template defects, a wrong GENESIS timestamp, and 55 minutes of wall time

Date: 2026-10-06. Scope: one `/init-project` run from this repository, commit `72064a7`.
Blueprint `clean-architecture-single-module`, `com.nerviz:banking-app`, Maven, features REST +
OpenAPI, JPA + Flyway, Testcontainers + Actuator. Transport topology A (edge, no local Caddy),
SonarQube local with anonymous analysis. The build ended green, but only after the agent
patched three things this repository shipped. This file records what broke, the GENESIS
defects, and a timing and cost breakdown of the run. It decides nothing. The design belongs to
`/claude-code-architect-designer`.

Sources: the main session transcript and the `project-initializer` subagent transcript
(`~/.claude/projects/<repo>/<session>/subagents/agent-<id>.jsonl`), read with a script that
pairs each `tool_use` with its `tool_result` and adds up `usage` per `requestId`. Every
number below comes from those files, not from the agent's own report.

---

## § 1 · `logback-spring.xml.example` is invalid XML

`project-bootstrap/templates/logback-spring.xml.example` opens with a 22-line `<!-- … -->`
comment, and the `<?xml version="1.0" encoding="UTF-8"?>` declaration only comes at line 23.
An XML declaration must be the first thing in the document. The agent kept that order, so
Logback/Xerces rejected the file at context startup ("processing instruction target ... not
allowed"), and **every test that starts a Spring context failed**.

The agent moved the declaration to line 1 in the generated file. The template still has the
defect, so every bootstrap hits it unless the agent happens to fix it again.

Observed cost: one red `./mvnw test`, one Read, one Edit and one re-run, about 1 minute.

## § 2 · `transport-security-setup`'s IT exemplars ignore an active datasource

`transport-security-setup/templates/ForwardedHeadersIT.java.example` declares nested
`@SpringBootTest` classes under `@NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)`,
with no `@Import(TestcontainersConfiguration.class)`. With `persistence-jpa` and `flyway`
active, the context needs a datasource. Without the import, it fell back to
`localhost:5432`, reached a container left running by an unrelated project, and failed SCRAM
authentication. On a machine with nothing on 5432, it would fail with a connection refused
instead. Either way the build is red.

`OVERRIDE` stops nested classes from inheriting the enclosing class's configuration. So the
import is needed **on each nested class**, not once on the outer class. The agent added it to
both. `TransportSecurityIT.java.example` has the same shape and will fail the same way under
topology B or C.

Related, not confirmed: the generated `HstsHeaderFilter` has a class Javadoc that
`HstsHeaderFilter.java.example` does not have. The agent added it in two Edits right after a
failed `clean verify`, most likely to satisfy the project's Checkstyle Javadoc rule. Check
the exemplar against `checkstyle.xml.example` before trusting it.

Observed cost: three `clean verify` runs (one red on the filter, one red on the IT, one
green), five Edits, about 4 minutes.

## § 3 · The boundary probe never ran — the procedure gives no executable command

`project-bootstrap/references/verify-and-report.md` § 8 says: write `ArchHookProbe.java` with
a Spring import and "confirm the hook blocks the write". That cannot happen as written. The
probe is written into the **generated** project, but the session running the bootstrap is
rooted at **this** repository. The hooks that fire are this repository's, not the generated
project's `settings.json`. Nothing blocks the write.

The agent improvised `java .claude/hooks/ArchHook.java check <path>`. That form is wrong:
`check` ignores argv and reads the path from a JSON payload on stdin (`main` →
`readAll(System.in)` → `filePath(stdin)`). In the subagent's Bash, stdin is neither a TTY nor
closed, so the call blocked until the 120 s tool timeout. The agent then spent about 8
minutes on `ps`, `kill -9`, `java -version` and retries. It concluded "sandbox quirk" and
replayed the hook's regex by hand. Its report says "blocking verified ✓", but **no hook ever
ran against the probe**.

Reproduced on 2026-10-06 inside `banking-app` (probe written, then removed):

```
$ (sleep 4) | java .claude/hooks/ArchHook.java check src/main/java/.../domain/model/ArchHookProbe.java
exit=0 took=4s          # waited for stdin, then passed silently: no file_path in the payload
$ echo '{"tool_input":{"file_path":"<abs>/…/ArchHookProbe.java"}}' | java .claude/hooks/ArchHook.java check
❌ Architectural boundary violation — … org.springframework. (forbidden in module '…/domain')
exit=2
```

The boundary works. The test that should prove it is unexecutable, and the improvised form is
worse than a hang: with stdin closed, `check <path>` exits 0 and prints nothing, which reads as
a pass. The same trap applies to anyone checking by hand. `CLAUDE.md`'s command table shows
the stdin form for `guard bash` and `guard sweep` but has no row for `check`.

Observed cost: 7 min 48 s of wall time, plus a false ✓ in the report and in GENESIS.

## § 4 · GENESIS records a start after the finish

`banking-app/.claude/audit-usage/GENESIS.md`:

| | |
|---|---|
| Started | 2026-10-06T09:59:28Z |
| Finished | 2026-10-06T09:48:16Z |

The real window is 08:55:27Z (`/init-project` typed) to 09:48:16Z (GENESIS written).
**Finished is correct.** The agent ran `date -u` for it at 09:48:16.

**Started is local time with a literal `Z` appended.** At 09:45:03 the agent recovered the
start from the project directory's birth time:
`stat -f "%SB" -t "%Y-%m-%dT%H:%M:%SZ" <project>`. On macOS, `stat` formats in the local zone
(WEST, UTC+1 here), and the `Z` in the format string is just a character. The directory was
created at 08:59:28Z, which is 09:59:28 local, which became "09:59:28Z".

Root cause in the procedure: `verify-and-report.md` § 8.6 defines `{{startIso}}` as "this
run's own start timestamp (interview's first question, step 1)". But no step captures that
timestamp. By step 8.6, 50 minutes later, the agent has to reconstruct it. The definition is
also ambiguous now: the interview ran in the **main** session (§ 7.2), not in the agent, so
the agent never saw its first question. Nothing checks that Finished ≥ Started.

## § 5 · GENESIS carries no tokens or cost — by design, and that design is now the gap

The user asked for token count and estimated USD cost in GENESIS's header. § 8.6 forbids it
on purpose: "no invented per-tool-call timeline, no cost, no ranked stages". The reason was
that nothing measured the run, so any number would have been invented
(`lessons-learned-004` Gap 1).

That premise no longer holds. The run **is** measured: the subagent transcript has `usage`
on every assistant message, and `ArchHook.java audit` already knows how to read a transcript
and price it with `pricing.json`. The numbers below came from that file in under a second.
What is still true: the **model** must never compute or estimate the figure itself. It would
be a number written from memory, the failure invariant 8 exists to prevent. The figure has to
come from a deterministic reader of the transcript, priced from the generated project's own
`.claude/audit-usage/pricing.json`.

This run's figures, for the record (prices from `banking-app/.claude/audit-usage/pricing.json`,
filled 2026-10-01; cache writes priced at the 5-minute rate):

| Who | Model | Requests | Input | Output | Cache read | Cache write | USD |
|---|---|---|---|---|---|---|---|
| `project-initializer` (3 runs) | `claude-sonnet-5` | 180 | 360 | 19,726 | 35,184,674 | 609,307 | 8.76 |
| main session, up to the `git-publish` question | `claude-opus-5-5` | 12 | 24 | 8,358 | 1,002,614 | 104,349 | 0.89 |
| main session, `/init-project` skill | `claude-sonnet-5` | 3 | 6 | 990 | 156,789 | 80,293 | 0.24 |
| **Total** | | **195** | | **29,074** | **36,344,077** | **793,949** | **≈ 9.89** |

80 % of the cost is **cache reads**: 180 turns re-reading a context of about 199k tokens each.
Output is 2 %. Cost scales with the **number of turns**, not with what the agent writes.
Under the 1-hour cache-write rate the total would be about USD 11.4.

Do not use the harness task notification's `subagent_tokens` (355,963 for this run) as a
total. It matches no sum in the transcript.

## § 6 · `project-initializer` cannot interview when it runs as a background agent

`project-initializer` lists `AskUserQuestion` in `tools`, and its body runs the interview.
Delegated from `/init-project`, it ran as an async agent and reported "I have no
AskUserQuestion tool in this subagent". So it handed back **twice** with a blocked status:
once for blueprint, coordinates, build and features, and once mid-run for the
`transport-security-setup` and `sonarqube-setup` questions. The main session relayed each
question set and resumed the agent with `SendMessage`.

Consequences:

- Three agent runs instead of one, each resume re-reading a large context.
- The main session paraphrased the questions. `AskUserQuestion` takes at most 4 options, so 4
  of the 8 blueprints went behind "Other". A free-text coordinates question came back with
  only `groupId`, and needed a second round.
- The second question set arrived **23 minutes into the run**. The user had to stay at the
  terminal for a question that could have been asked before generation started, because it
  depends only on interview answers, not on generated files.

## § 7 · Where the 55 minutes went

`/init-project` was typed at 08:55:27Z. The `git-publish` question appeared at 09:50:16Z.
Total: **54 min 49 s**.

### 7.1 · Phases

| From | To | Duration | Phase | Who |
|---|---|---|---|---|
| 08:55:27 | 08:55:43 | 0:16 | `/init-project` lists blueprints, delegates | main |
| 08:55:43 | 08:56:52 | 1:09 | preconditions, cannot interview, hands back blocked | agent run 1 |
| 08:56:52 | 08:58:47 | 1:55 | interview relayed (1:22 of it the user answering) | main + **human** |
| 08:58:47 | 09:08:47 | 10:00 | steps 1–6.6: Initializr, `pom.xml`, 13 `package-info.java`, config, Docker, `CLAUDE.md`, CI, `export` | agent run 2 |
| 09:08:47 | 09:11:48 | 3:01 | step 8 build, logback fix (§ 1) | agent run 2 |
| 09:11:48 | 09:19:36 | **7:48** | boundary probe hang (§ 3) | agent run 2 |
| 09:19:36 | 09:22:02 | 2:26 | Lombok probe, autonomy, final verify, `doctor`, blocked report | agent run 2 |
| 09:22:02 | 09:34:51 | **12:49** | TLS + Sonar questions relayed (12:31 of it the user answering) | main + **human** |
| 09:34:51 | 09:40:06 | 5:15 | `transport-security-setup` + two fix rounds (§ 2) | agent run 3 |
| 09:40:06 | 09:43:24 | 3:18 | `sonarqube-setup`, compose service, verify, `doctor` | agent run 3 |
| 09:43:24 | 09:48:38 | 5:14 | `README.md`, `README.pt-br.md`, GENESIS | agent run 3 |
| 09:48:38 | 09:49:46 | 1:08 | final autonomy grep, report | agent run 3 |
| 09:49:46 | 09:50:16 | 0:30 | main relays report, `git-publish` reads state and asks | main |

### 7.2 · By kind of time

| Kind | Time | Share |
|---|---|---|
| Human answering questions | 13:53 | 25 % |
| Agent: model generating turns (180 requests, about 10.7 s each) | ≈ 32:00 | 58 % |
| Agent: tools executing (197 calls) | 7:13 | 13 % |
| — of which Maven (about 8 builds) | ≈ 2:50 | |
| — of which the `check` hang and its retries | ≈ 3:00 | |
| Main session relays | ≈ 1:45 | 3 % |

**Builds are not the bottleneck.** The bottleneck is the **number of model turns**, each
paying about 10 s of latency over a ~199k-token context, plus a human gap that sits in the
middle of the run instead of at its start.

### 7.3 · What turned into turns

- **13 `package-info.java`, one Write each**: 13 turns, about 75 s. Same pattern for the
  `mkdir` calls before each group.
- **Exemplars read one at a time**: about 30 Reads of templates and references, many in their
  own turn.
- **README skill and agent lists built with shell**: 09:44:03 to 09:46:34, eight turns of
  `for f in .claude/skills/*/SKILL.md`, `/tmp` files and Python, to produce a list
  `export` already knows. About 2.5 min.
- **Defect loops** (§§ 1–3): about 13 min in total, every minute caused by this repository.

### 7.4 · Estimated savings, for the designer to weigh

| Lever | Saves (estimate) | Touches |
|---|---|---|
| Give § 8 an executable boundary-probe command (stdin JSON form), or have `check` fail loudly when argv carries a path | ≈ 7 min | `verify-and-report.md` § 8, `ArchHook.java` `check` |
| Fix the three exemplars (§§ 1–2) | ≈ 4 min | `project-bootstrap/templates/`, `transport-security-setup/templates/` |
| Ask every interview question, transport and Sonar included, in the main session **before** delegating: one interview, one agent run, no mid-run question | up to 12 min of wall time when the user steps away, and two fewer resumes | `/init-project`, `project-initializer`, how `transport-security-setup` and `sonarqube-setup` receive answers |
| Batch mechanical writes and reads (one Bash for all `package-info.java`, one `cat` for a group of exemplars) | ≈ 5–8 min, and cost in proportion | `project-bootstrap` procedure |
| Let `ArchHook.java export` emit the skill and agent lists the README needs | ≈ 2–3 min | `export`, `verify-and-report.md` § 8.5 |

A realistic target from these alone: about 25 minutes of agent time, and a user who answers
once at the start and can leave.

## Constraints any fix must respect

- **Invariant 6.** A Finished ≥ Started check, or a cost figure, is deterministic work: a
  hook mode, not prose asking the model to be careful.
- **Invariant 8, extended to numbers that change outside this repository.** The model never
  writes a token count, a price or a timestamp from memory or estimate. Timestamps come from
  `date -u` at the moment they mean, prices from `pricing.json`, tokens from the transcript.
- **§ 8.6's fidelity rule is a recorded decision.** Adding cost to GENESIS reverses it, and
  that needs its own record in `decisions/`, not a quiet template edit.
- **`audit` is off in this repository on purpose.** Whatever reads the bootstrap transcript
  must not turn on the trail here.
- **Invariant 9.** GENESIS lives in the generated project, which does not have Nerviz.
  Whatever writes it runs at bootstrap time, from here.

## Open questions for `/claude-code-architect-designer`

1. Who captures `{{startIso}}`, and when: `/init-project` before delegating (it saw the
   command), or the agent at its first step? Should a timestamp travel in the delegation
   context?
2. Who computes GENESIS's tokens and cost: a new `ArchHook.java audit` sub-mode over the
   subagent transcript, run by `/init-project` after the agent returns, or something else?
   And does the figure cover the main session too, or only the agent?
3. Should the whole interview move into `/init-project`, which runs in the main session and
   has `AskUserQuestion`, so `project-initializer` becomes a pure executor? If so, does the
   agent class change?
4. Should `check` (and every stdin-reading mode) exit non-zero when it gets a path in argv
   and no payload, instead of passing silently?
