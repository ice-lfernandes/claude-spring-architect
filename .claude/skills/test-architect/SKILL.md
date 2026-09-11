---
name: test-architect
description: >
  Designs the tests for an already-modeled use case — what test exists at what level,
  what data, what error cases — into the `40-testes.md` partial, and installs the
  architecture tests in a project that doesn't yet have them. Use when the request
  involves writing or designing tests, coverage, ArchUnit, boundary tests, integration
  tests, or "this is missing tests". Piece of the `/new-feature` pipeline: in design
  mode requires `10-dominio.md` in the given folder and stops without it.
argument-hint: "[path to the UC-NNN-<slug> folder, or empty to install ArchUnit]"
allowed-tools: Read, Write, Edit, Glob, Grep, Bash, AskUserQuestion, Agent
---

## Available specs

!`ls -1d docs/use-cases/UC-*/ 2>/dev/null || echo "(none — run /use-case-design first)"`

## Target

$ARGUMENTS

---

# Test Architect

Two things, and the argument decides which:

| Mode | When | Produces |
|---|---|---|
| **design** | `$ARGUMENTS` is a `UC-NNN-<slug>` folder | `docs/use-cases/UC-NNN-<slug>/40-testes.md` |
| **setup** | `$ARGUMENTS` empty | ArchUnit installed in the project (version in the POM, `ArchitectureTest.java` with the translated packages) and the coverage gate wired up (JaCoCo's `check` execution) — done by delegating to the `archunit-installer` agent |

They're not two disguised pieces: they share the rule, the vocabulary, and the
exemplars. Setup mode runs **once per project**; design mode runs once per use case.

**Design mode entry rule: without `10-dominio.md`, there's nothing to test.** Reads
`00-caso-de-uso.md` and `10-dominio.md` and treats them as a contract. Without the
domain partial, it stops and tells the caller to run `/domain-modeling` — designing
tests before the invariants exist produces tests for the form, not the business.

**Exit rule: writes no test code.** Design mode emits `40-testes.md`; the files in
`src/test/**` come from the executor agent, which reads the partial and the exemplars
in `templates/`. Inherits D15 — `@.claude/decisions/0003-skill-domain-modeling.md`.

Setup mode is the declared exception, and the only one: it produces
`ArchitectureTest.java` and two entries in the POM — the ArchUnit dependency and
JaCoCo's `check` execution — by delegating to the `archunit-installer` agent. It isn't
code for a use case — it's whole-project verification infrastructure, which no partial
describes, and running it inline would pin the whole session's context to a `curl` call
and up to three `./mvnw` builds it doesn't need to see. Split recorded in
`@.claude/decisions/0030-archunit-installer-split.md`.

**Rule rule: rules don't live here.** Pyramid, slices, doubles, names, data, database
engine, and coverage are `@.claude/rules/testing.md`. This piece applies them and cites
them; it doesn't reproduce them.

## How it's invoked

Two ways, and both matter: `/test-architect` by hand, or chained by `/new-feature` once
that orchestrator exists. That's why it does **not** carry
`disable-model-invocation` — that field hides the piece from the model, and what the
model can't see the orchestrator can't call.

The guard against out-of-order firing isn't the frontmatter: it's the entry rule above.
Without the domain partial, design mode stops and says what needs to run first.
Recorded in `@.claude/decisions/0007-pipeline-skills-invocation.md`.

## Why design mode isn't a subagent, and setup mode is

Step 3 of design mode goes back to asking the user what no prior partial fixes — which
error scenarios deserve their own test, what data represents the real case. A subagent
doesn't see the conversation, so design mode stays inline. Recorded in
`@.claude/decisions/0008-testing-rule-and-design.md`.

Setup mode has the opposite shape — no interview, purely mechanical, and its own Bash
output (a Maven Central lookup, up to three `./mvnw` builds) is the kind of thing that's
cheap to isolate and expensive to keep. It delegates to the `archunit-installer` agent.
Recorded in `@.claude/decisions/0030-archunit-installer-split.md`.

## Boundary with neighboring pieces

| Piece | Acts when | Produces |
|---|---|---|
| `use-case-design` | Before the domain exists | `00-caso-de-uso.md` |
| `domain-modeling` | After the parent spec | `10-dominio.md` — invariants and exceptions |
| `persistence-architect` | After the domain | `20-persistencia.md` — queries and indexes to check |
| `rest-api-architect` | In parallel | `30-rest.md` — **the HTTP contract's cases** |
| **this piece** | After all of them | `40-testes.md` |

The single point of contact, and it must be respected: the HTTP contract's cases —
status, error code, body shape — belong to `30-rest.md` (D16). This partial **cites
them and doesn't rewrite them**; what it adds is the level they run at, the data, and
what's left to cover outside of transport.

## Procedure — design mode

1. **Read the partials.** `00-caso-de-uso.md` and `10-dominio.md` are mandatory;
   without the second, stop. Read `20-persistencia.md` and `30-rest.md` when they
   exist. Extract: each invariant and its matching exception, each port, each query,
   and the HTTP contract's case table.

2. **Survey what already exists.**

   ```bash
   ls src/test/java 2>/dev/null
   grep -rln "@SpringBootTest\|@DataJpaTest\|@WebMvcTest" --include=*.java src/test/ 2>/dev/null
   ```

   A data factory that already exists gets reused. Two factories for the same
   aggregate is exactly the duplication the rule forbids.

3. **Interview — only what the partials don't fix.** `AskUserQuestion`, at most 4
   questions per call.

   | Axis | Decides |
   |---|---|
   | What error scenarios deserve their own test | An invariant with no named test is an invariant nobody guarantees |
   | What data represents this business's real case | Distinguishes a test that documents from a test that just pads |
   | Is there concurrency or time in the use case | Whether a fixed clock and an optimistic-locking test come in |
   | Does the use case touch an external system | Whether there's a double, and at which boundary |

4. **Distribute by level.** Each behavior at **one** level, per the pyramid table in
   `@.claude/rules/testing.md`. An aggregate invariant is always a domain unit test.
   Mapping and SQL are always integration. A behavior appearing at two levels is a bug
   in the distribution, not thoroughness.

   **A unique business key requires its own integration test.** For each `UNIQUE` that
   `20-persistencia.md` declares over a business key, name a test in the adapter that
   saves the same value twice and **asserts the domain exception's `errorCode`, not
   just its type**. It's the only test that tells apart an adapter that translates the
   violation from one that lets it leak: without a flush inside the adapter the
   exception is born at commit, the client gets a 500 instead of a 409, and a test that
   only checks the type still passes
   (`@.claude/rules/persistence.md` § Boundary). Exemplar:
   `templates/PersistenceIT.java.example`.

5. **Name each test.** Class and method per the rule's naming conventions, with the
   `IT` suffix on integration ones — without it failsafe doesn't run them and `verify`
   exits 0 without executing them.

6. **Fix the data and the doubles.** Which factory, which fields matter in the
   scenario, which port gets substituted and with what value. Fixed clock wherever
   there's time involved.

7. **Write the partial.** `docs/use-cases/UC-NNN-<slug>/40-testes.md`, from
   `templates/test-spec.md.example`. Four blocks, all mandatory.

8. **Report and stop.** File path, invariants from `10-dominio.md` left without a
   named test (if any, it's a gap to close before implementing), and what's left for
   the folder to be complete. Don't invoke anyone.

## Procedure — setup mode (install ArchUnit and wire up the coverage gate)

Run once business classes already exist. Not before: in a freshly generated project,
every rule about zero classes fails vacuously, and the build is born red for having
nothing to check. That's why `project-bootstrap` doesn't do it.

The coverage gate comes in through the same door and for the same reason: over zero
classes it either passes vacuously, which proves nothing, or breaks the build for
having nothing to measure. `project-bootstrap` leaves JaCoCo instrumenting and
reporting, without the `check` execution — this mode is the one that adds it. Record:
`@.claude/decisions/0011-bootstrap-without-business-code.md`.

**This mode delegates its execution.** Invoke the `archunit-installer` agent with the
project root. It resolves the ArchUnit version, writes `ArchitectureTest.java`,
translates the packages, wires the JaCoCo `check` execution, runs the builds, and pins
the Testcontainers image tag — all isolated from this conversation, since none of it
needs an interview. Reasoning and full procedure:
`@.claude/agents/archunit-installer.md`. Record of the split:
`@.claude/decisions/0030-archunit-installer-split.md`.

Read the agent's returned summary and report it as-is. If it flags a Testcontainers
tag mismatch against `docker-compose.yml`, invoke `docker-architect` next — the
installer agent deliberately doesn't touch that file, single owner rule.

## What the partial contains

Four blocks. A block with no content is written as "none" — deleting it hides a
question nobody asked.

| Block | Fixes | Shape exemplar |
|---|---|---|
| Distribution by level | Each behavior, the level it's tested at, and why | `DomainTest.java.example` |
| Cases per test class | Class name, each method's name, what it asserts | `UseCaseTest.java.example` · `ControllerTest.java.example` · `PersistenceIT.java.example` |
| Data and doubles | Factories, meaningful fields, substituted ports, clock | `TestFixtures.java.example` |
| Coverage and gaps | Invariants with no test, and what's deliberately left uncovered | `@.claude/rules/testing.md` § Coverage |

The HTTP contract's cases aren't repeated here: they're cited from `30-rest.md`. The
**shape** of the class that verifies them belongs to this skill —
`templates/ControllerTest.java.example`. Cases there, shape here; one owner for each
thing.

External sources in `references/best-practices-links.md`: JUnit, AssertJ, Mockito,
Testcontainers, ArchUnit, JaCoCo, and Spring's testing docs.

The exemplars in `templates/` are a **shape reference**, not files to copy. The
`java-spring-boot-developer` executor reads them when generating a use case's code;
`ArchitectureTest.java.example` is the one exception — the `archunit-installer` agent
reads it and writes the real file, once per project.

## Contract

**Reads** `docs/use-cases/UC-NNN-<slug>/00-caso-de-uso.md` and `10-dominio.md`
(mandatory in design mode — stops without the second), `20-persistencia.md` and
`30-rest.md` when they exist, `@.claude/rules/testing.md`,
`@.claude/rules/error-handling.md`, `@.claude/rules/naming.md`,
`@.claude/rules/code-quality.md`, `@.claude/rules/architecture-ddd.md`, and the active
blueprint's `packages.map`.

**Writes** `docs/use-cases/UC-NNN-<slug>/40-testes.md` in design mode, directly. Setup
mode writes nothing itself — it delegates to `archunit-installer`, which writes
`<main-module>/src/test/java/**/ArchitectureTest.java`, the ArchUnit entries in the
POM, the `jacoco-maven-plugin`'s `check` execution, and — the one exception, a single
line, not a file — the pinned image tag in the Initializr-generated
`TestcontainersConfiguration.java`. Nothing else in `src/test/**`. Contract of that
agent's own reads and writes: `@.claude/agents/archunit-installer.md`.

**Does not edit `docker-compose.yml`**, neither directly nor through the agent. A tag
mismatch against the compose-side service comes back in the agent's summary and this
skill invokes `docker-architect` — single owner of that file, see its Contract.

**Owns the coverage gate**, since
`@.claude/decisions/0011-bootstrap-without-business-code.md`. `project-bootstrap`
remains the owner of the POM and writes JaCoCo instrumenting and reporting; the `check`
execution, which is what turns a report into a gate, comes in here — alongside
ArchUnit, because both depend on code existing for them to apply to. Ownership is this
skill's; the writing happens in the delegated agent's isolated context.

**Doesn't write the use case's tests.** The files in `src/test/**` come from the
executor agent. Doesn't edit the other partials and doesn't touch `.claude/rules/**`.

**Doesn't duplicate `rest-api-architect`**: the HTTP contract's cases belong to
`30-rest.md`. This partial cites them and adds level, data, and gaps.
