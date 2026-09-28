# 0057 · Remediation of lessons-learned-012 — the seven inferences

- **Date:** 2026-09-27
- **Scenario:** the second half of `@.claude/lessons-learned/lessons-learned-012.md`, the
  items `0056` deferred: the decisions the model made on its own because no axis, rule or
  template fixed them. Items **4, 5, 8, 9, 10, 11, 13** — the § Resumo table of that file.
- **Decision:** Option 1 — edit what exists. Two rules (`logging.md` § Masking candidates,
  `naming.md` § Use case identifier), eight skills, two agents, one reference, three
  templates, `extensions.json` and `@CLAUDE.md`. No new piece.
- **State:** approved by Lucas Fernandes, on 2026-09-27

## Interview

| Axis | Answer | Forms/options it eliminated |
|---|---|---|
| 1 — symptom | Ten decisions taken by improvised `AskUserQuestion` or direct judgment in one run; one of them (item 11) a real defect in shipped code — CPF logged raw since UC-001 | "create nothing" for all seven |
| 5 — nature | Six are declarative facts or ownership boundaries; one (item 13) is a verification step inside a procedure | Form 3 for every one of them |
| 7 — mandatoriness | Item 11 is the only one where prose already failed: `rest-api-architect` masks what `10-dominio.md` flagged, `10-dominio.md` did not flag, and the field crossed design, implementation, review and a green build | Prose-only for item 11 |
| 8 — destination | **Both**, for all of them. Every file touched is in `export`'s payload, and item 5 lands in `project-bootstrap`'s template for the generated project's own `CLAUDE.md` | — |
| Item 8 — the contradiction | Keep the rule, open the Form B exception: publication is a separate effect **under Form A**; under Form B the append shares the transaction and does not split the case | Redefining the effect so only consumption splits; three use cases |
| Item 4 — serialization | An explicit seventh axis in step 3, with the trigger written and the six-place cost listed inside the question | Recording the default and never asking; a conditional axis |
| Item 5 — bounded context | A project fact with the same standing as the base package: asked once in `/init-project`, written into the generated `CLAUDE.md`, **read** by `messaging-architect` | Blueprint data; asking inside a use case and writing it back |
| Item 9 — the outbox | **Move the whole outbox to persistence**: `persistence-architect` owns the table, its columns and the relay's pacing; `messaging-architect` only declares that the case needs one | Citing the exemplar and keeping retry with messaging; the tie-break alone |
| Item 10 — extension case | Its own `UC-NNN`, an explicit slug rule for a case that creates no use-case class, and a `CHANGELOG.md` in the folder of each altered use case | A separate `CR-NNN` artifact; the slug rule alone |
| Item 11 — detection | Name patterns **plus** the types of `value-objects.md`'s catalog | Every REST DTO implements `LogMask`, with no list; both rules together |
| Item 11 — placement | A guarded rule inside `ArchitectureTest`, written by `archunit-installer`, evaluated only where `LogMask` exists | A separate `MaskingTest` owned by `commons-logging-installer`; an unguarded rule |
| Item 13 — image tag | Verify the tag in the registry before writing the block, and give every new image a catalog line after its first use | The catalog line alone; verification alone |

### The answer that carries a caveat, stated before it is written

Item 9's answer moves the outbox to persistence **including the relay's pacing**. That
separates two things the lessons-learned found coupled: the owner of the delivery
guarantee (messaging) no longer owns the backoff that delivers it. The failure it prevents
is the one that happened — a behavior decision resolved by a rule written for table shape —
and the failure it opens is the mirror: a pacing decided by whoever does not own
at-least-once.

The line this record draws, so the next run does not have to guess it: **persistence owns
the outbox table, its columns, indexes, migration, the claim query and the backoff those
columns encode.** **Messaging owns the broker side of the relay** — topic, serialization,
DLQ routing, and the delivery guarantee it declares as a requirement in `25-mensageria.md`
§ 6. A requirement messaging writes ("at-least-once, per-row exponential backoff, N
attempts") is input to persistence, not a suggestion; persistence choosing a shape that
cannot satisfy it is a divergence that stops the pipeline, exactly like today.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Edit what exists, in the seven places the interview named: 2 rules, 8 skills, 2 agents, 1 reference, 3 templates, `extensions.json` | 9 | **Approved** |
| 2 | The same, but item 9 stays with messaging (cite the exemplar + a tie-break row in the precedence table) | 7 | Rejected by the user's answer — the tie-break leaves two owners for one fact, which is what produced the divergence |
| 3 | Item 10 as a separate `CR-NNN` artifact with its own template and numbering | 5 | Rejected — `/new-feature` would grow a second output shape and the executor a second spec form, for a case the `## Impact` section already carries |
| 4 | Create nothing; record the ten inferences as known pitfalls | 2 | Rejected — item 11 is a live defect in generated code, and items 4 and 8 change how many use cases a run produces |

### Option 1 — edit what exists (score 9)

**Motivator:** axis 1 — every one of the seven has an observed occurrence in a single run;
axis 5 — each lands on a file that already owns the subject.

**Pros.** No new piece. Item 11 becomes mechanical in two places at once: the candidate
list stops depending on a partial remembering, and the build fails on an unmasked DTO
field, which is what the CPF leak needed. Item 5 turns a per-use-case answer into a
project fact, which is what a topic prefix is. Item 9 removes the double ownership instead
of arbitrating it. Item 13 applies to an image tag the discipline invariant 8 already
applies to a Maven version.

**Cons.** Three, all real. Item 9's is above, and it is the reason this record exists at
all. Item 11's detection by name list cannot see a field named creatively — which is
precisely what leaked (`securityNumber` holding a CPF), so the list must carry that name
and every one like it, and the list will always trail reality by one field. Item 8's
exception makes the boundary test depend on the Form A/B choice, which `messaging.md`
settles later in the pipeline — so `use-case-design` has to apply Form B's **business**
criteria (irreversible external effect, reconcilable state) at boundary time, not the
technical choice.

**Points cut in the rubric:** criterion 2 — item 9 reassigns a norm's territory, and a
reassignment is a strain even when it is the right one; criterion 4 — item 11's name list
is persuasion pretending to be a guarantee for any field the list does not name.

### Option 2 — item 9 stays with messaging (score 7)

What `0056`'s draft proposed. Defensible: retry is delivery, delivery is messaging. It
keeps two owners for the same fact and resolves collisions with a precedence row, which is
the mechanism that let a column shape silently change the relay's behavior.

### Option 3 — `CR-NNN` for the extension case (score 5)

Clean separation on paper. In practice `/new-feature`'s guardrail, spec lifecycle,
consolidation and executor hand-off all key on `UC-NNN`, and a second artifact duplicates
four mechanisms to name one case differently.

### Option 4 — create nothing (score 2)

Recorded for what it costs: the next `/new-feature` over a domain with a CPF logs it
again, and two identical runs still split the same case differently.

## References

| Claim | Source |
|---|---|
| A rule default is not a question | `@.claude/rules/messaging.md` § Topics and serialization |
| Form B writes the outbox row in the same transaction | `@.claude/rules/messaging.md` § Publication timing |
| Publication never meets condition 1 of the boundary test | `.claude/skills/use-case-design/references/scope-boundary.md:27-29` |
| The topic name needs a bounded context nothing declares | `@.claude/rules/messaging.md` § Topics and serialization; no `packages.map` key, no rule, no partial carries it |
| The use case's verb is the trigger's | `scope-boundary.md` § 4 |
| Masking applies only to what `10-dominio.md` flagged | `.claude/skills/rest-api-architect/SKILL.md` step 5; `.claude/skills/test-architect/SKILL.md` step 4 |
| The candidate catalog already exists | `@.claude/rules/value-objects.md` § catalog (cpf, cnpj, email, phone, document) |
| Masking mechanism is `logging.md`'s territory, not `value-objects.md`'s | `@.claude/rules/00-index.md` — single owner per norm, invariant 2 |
| ArchUnit is installed and its rules are written per project | `.claude/agents/archunit-installer.md` step 3-6; `templates/ArchitectureTest.java.example` |
| A version is never written from memory | `@CLAUDE.md` invariant 8 — extended here to an image tag |
| Architecture is data; a project fact is not | `@CLAUDE.md` invariant 7 — why the bounded context is not a blueprint key |

## Propagation

| File | Change |
|---|---|
| `.claude/rules/logging.md` | ✅ Item 11: § Masking candidates — derivation by type (the catalog's personal-data value objects) **or** by name (7 groups, 38 names), the inverted burden (not masking is what needs a reason), the floor-not-ceiling clause, and a counter-list. ✅ § How to verify: the architecture test, guarded |
| `.claude/rules/naming.md` | ✅ Item 10: § Use case identifier — `NNN` never reused, the slug is the trigger's, and the exception for a case that creates no use-case class (slug names the capability) plus the per-folder `CHANGELOG.md` |
| `.claude/skills/use-case-design/references/scope-boundary.md` | ✅ Item 8: the Form B exception with three business criteria applicable at boundary time, and the clause that the **consumer** never merges in either form. ✅ Item 10: the `none — extension of UC-XXX` row in § 4 |
| `.claude/skills/new-feature/SKILL.md` | ✅ Item 9: the precedence table split — outbox table, columns, claim query and pacing to `20-persistencia.md`; topic, serialization, guarantee, consumer retry/DLQ to `25-mensageria.md`; plus the new stop rule for a column decision that would drop a declared guarantee. ✅ Item 10: consolidation writes a line into each altered case's `CHANGELOG.md`, and § Spec lifecycle names the all-`CHANGE` case |
| `.claude/skills/messaging-architect/SKILL.md` | ✅ Item 4: the serialization axis, with the buys/costs table the question must carry and the rule that no written trigger means no question. ✅ Item 5: step 2 reads the bounded context from the root `CLAUDE.md` and stops when it is missing. ✅ Item 9: step 4a declares table + guarantee + addressee and never a column; § 6's row description and the producer row follow |
| `.claude/skills/persistence-architect/SKILL.md` | ✅ Item 9: owns the column set (from the exemplar, named in full), the claim query, the pacing those columns encode and the `app.outbox.*` values; a § 6 row naming columns is reported as a divergence; a guarantee the shape can't hold stops the pipeline |
| `.claude/skills/domain-modeling/SKILL.md` | ✅ Item 11: derivation instead of a judgment call, over aggregate, command and response fields, with the written-reason requirement |
| `.claude/skills/rest-api-architect/SKILL.md` | ✅ Item 11: re-derives instead of trusting `10-dominio.md`, and sweeps the DTOs that already exist, reporting each unmasked match in `## Impact` |
| `.claude/skills/test-architect/SKILL.md` | ✅ Item 11: the masking test derives from the rule, and why the structural rule and the behavioural test are both needed |
| `.claude/skills/test-architect/templates/ArchitectureTest.java.example` | ✅ Item 11: `SENSITIVE_NAMES`, `SENSITIVE_TYPES`, `SENSITIVE_FIELD`, `CARRY_SENSITIVE_DATA`, the `COMMONS_INSTALLED` classpath guard, and the two rules `sensitive_dtos_implement_log_mask` / `sensitive_fields_are_marked` |
| `.claude/agents/archunit-installer.md` | ✅ Item 11: step 6a — install both rules, translate three names, copy the list verbatim, never drop the rules because `commons` isn't installed yet |
| `.claude/skills/docker-architect/SKILL.md` + `templates/schema-registry-service.yml.example` | ✅ Item 13: step 4 verifies the tag (`docker manifest inspect`, registry HTTP fallback, refuse to write when neither confirms), confirms or omits the healthcheck binary, and adds the catalog row; step 7 reports **how** the tag was confirmed and no longer treats `compose` as optional. ✅ New exemplar with the three verifications in its header and a catalog row |
| `.claude/skills/project-bootstrap/SKILL.md` + `templates/root.CLAUDE.md.example` · `.claude/agents/project-initializer.md` · `.claude/skills/init-project/SKILL.md` | ✅ Item 5: `{{boundedContext}}` resolved from step 3 (default: artifactId), asked as a field of the coordinates question, written into the generated `CLAUDE.md` even without messaging, and `--bounded-context` in the command's `argument-hint` |
| `.claude/schemas/extensions.json` | ✅ `doctor.uc_references.exempt_paths` rewritten without trailing slashes — a directory prefix spelled with one is an export residue marker, and the file was reporting itself. No masking list here: the rule owns it and no hook reads it |
| `CLAUDE.md` | ✅ § Known pitfalls: the outbox belongs to persistence (and what messaging keeps), and the bounded context is a project fact |

**Verified:** `schema` exit 0 here and in the exported tree; `claude plugin validate
.claude/skills` passes; all 7 blueprints export twice byte-identical outside the stamp with
no residue — after two real residue hits this change introduced, both in `extensions.json`
naming `.claude/decisions/` as data, which is what the `0056` markers exist to catch;
`rules/` still names no skill or agent; the three `.claude/.ci/` tests pass; `doctor` clean;
the new exemplar travels into the exported tree.

Goes to the generated project: **yes** — every file above is in `export`'s payload, and the
`project-bootstrap` templates reach it through generation.

## Open, and named here so it is not discovered by surprise

`CLAUDE.md` is **242 lines**, past the ~200-line target in `references/decision-matrix.md`
§ 6 — 218 before these two remediations, +14 from `0056` and +10 from this record, all of
it in § Known pitfalls. Nothing was extracted here because an extraction changes what loads
in every session and deserves its own decision. The obvious cut is § Known pitfalls, which
is now the longest section and is mostly about authoring extensions: it is territory a rule
with `paths: .claude/**` would cover, leaving `CLAUDE.md` the invariants and the routing.
