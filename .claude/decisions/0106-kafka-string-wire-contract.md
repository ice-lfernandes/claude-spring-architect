# 0106 · Kafka payloads travel as JSON text, serialized once per side in the adapter

- **Date:** 2026-10-01
- **Scenario:** Issue #58, triaged at 3d976c9: Kafka serialization contradiction
- **Decision:** edit the existing `messaging-architect` exemplars and `rules/messaging.md` § Topics and serialization (Option 1), plus a `migrations` entry in `.claude/schemas/extensions.json`. No new piece
- **State:** approved by Lucas Fernandes, on 2026-10-01

## Reproduced on disk

The confirmed rows of the `issue-verifier` table, checked at `3d976c9`. No commit since then
touches `.claude/skills/messaging-architect/` or `.claude/rules/messaging.md`. The issue's
proposed fix was not an input; F1–F4 are weighed below as options, on their own merits.

| # | Claim | Evidence |
|---|---|---|
| 1 | The yml sets global `JsonSerializer` / `JsonDeserializer` | `messaging-architect/templates/application-kafka.yml.example:37`, `:43`; no `ErrorHandlingDeserializer` anywhere under `.claude/` |
| 2 | The typed listener relies on that deserializer | `KafkaConsumerAdapter.java.example:76` — `onMessage(OrderConfirmedPayload payload, Acknowledgment ack)`; no message converter in any template |
| 3 | The Form B relay sends already-serialized JSON via `KafkaTemplate<String, String>` | `OutboxRelayPublisher.java.example:148`, `:317`, `:333`; `OutboxAppender` "serializes the payload record" at append time |
| 4 | Yml and relay together publish the payload double-encoded, with a `__TypeId__` header | spring-kafka 4.1.1 bytecode: `JsonSerializer` sets `addTypeInfo = true` and has no `String` special case |
| 5 | Both classes are `@Deprecated(forRemoval=true, since="4.0")` in spring-kafka 4.1.1 | `javap -v`; `JacksonJsonSerializer`, `JacksonJsonDeserializer`, `StringJacksonJsonMessageConverter` present and not deprecated |
| 6 | The rule says "JSON by default" and never says who converts | `rules/messaging.md:143`; `SKILL.md:280-281` points at the yml while the Form B exemplar needs a `String` value |
| 7 | No test pins a payload's JSON shape | zero hits in `rules/testing.md`, `test-architect/` |
| 8 | Unchanged since v0.13.0 | lines date back to `55588bd` |

Also checked in this run, against the local 4.1.1 jars:

- `spring-boot-kafka` `KafkaAnnotationDrivenConfiguration` takes `ObjectProvider<RecordMessageConverter>`, `getIfUnique()`, and sets it on the listener container factory configurer. One converter bean reaches every `@KafkaListener` with no hand-built factory.
- `spring-boot-jackson` `JacksonAutoConfiguration` registers a `@Primary` `JsonMapper` bean (Jackson 3, `tools.jackson.databind.json.JsonMapper`).
- `ExceptionClassifier` (the base of `DefaultErrorHandler`) lists `MessageConversionException`, `ConversionException`, `DeserializationException` and `MethodArgumentResolutionException` as not retryable by default. A malformed payload that fails conversion at the listener goes straight to the recoverer — the DLQ — with no retries spent.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | Confirmed by triage: two exemplars of one skill contradict each other, and the yml names classes deprecated for removal | create nothing |
| 2 — trigger | `messaging-architect` already fires inside `/new-feature`; the defect is in what it hands the executor | 2, 3, 6 — no new piece |
| 5 — nature | The wire contract is a declarative fact (who converts, what the bytes are); the shape is exemplar code | a new skill; boilerplate stays in `templates/` (invariant 3) |
| 7 — mandatoriness | Not a hook: the executor reads exemplars, and the failure is a contradiction between two of them, not a call to refuse | 7, 8 |
| 8 — destination | Both: the templates and the rule travel; existing projects carry the old yml | — (adds a `migrations` entry) |
| Wire format (user's call) | String on the wire, one serialization per side | options 2 and 3 below |
| F3 golden-file test (user's call) | Deferred — a described risk, not an observed failure | — |
| Migration (user's call) | Yes, a note and a prompt | — |
| 17 — CI (user's call) | Denylist only | the contract `grep` step and the round-trip test |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | String on the wire — edit the existing exemplars, the rule's serialization line, a `migrations` entry | 9 | **Approved** |
| 2 | Typed serializer — `DelegatingByTypeSerializer` routing `String` to `StringSerializer`, everything else to `JacksonJsonSerializer.noTypeInfo()` | 6 | Rejected — hand-wired producer factory, two producer paths, and the consumer still needs Option 1's converter |
| 3 | Relay rehydrates the payload class before sending (the issue's F1) | 4 | Rejected — breaks `rules/messaging.md` § Boundary: every payload record public to the outbox package |

### Option 1 — String on the wire (score 9)

**Motivator:** axis 1 (two exemplars disagree) and the user's wire-format answer.

**What changes.** Producer and consumer use `StringSerializer` / `StringDeserializer`. The
payload record is turned into JSON text **once**, on the producer side, by Boot's `JsonMapper`:
in the Form A adapter right before `send`, in Form B's `OutboxAppender` at append time — after
which the relay sends the row's text as it is. On the consumer side, one
`StringJacksonJsonMessageConverter` bean in the messaging adapter turns the text into the
listener's typed parameter; the listener exemplar stays as it is. Forms A and B then put the
same bytes on the wire: a JSON object, no `__TypeId__` header, so no producer class name leaks
into a consumer and `spring.json.trusted.packages` goes away.

**Pros:** one contract for both publication forms; no deprecated class; no hand-built
`ProducerFactory`, `KafkaTemplate` or container factory — the starter's autoconfiguration
picks up the converter; a malformed payload is a non-retryable conversion failure, routed to
the DLQ by the error handler the rule already requires, so no `ErrorHandlingDeserializer` is
needed. The relay keeps not knowing any flow's payload class (§ Boundary intact).

**Cons:** the Form A adapter now imports `JsonMapper` and does the conversion itself — one
line, in the class that already maps the domain event. Changes the bytes on the wire for
existing projects: Form A loses its type header, Form B stops double-encoding — consumers in
other services must be checked before the migration is applied.

**Points cut in the rubric:** criterion 4 (enforcement) — nothing fails when a later edit
makes the two exemplars disagree again; only the deprecated names are denied (user's CI call).

**CI:** `validate` › `exemplar-imports` › `exemplars don't use known deprecated symbols` gains
the two FQCNs and widens its scan to `*.yml.example`. `exemplar-imports` › `every exemplar
import exists in a JAR` already resolves the new `StringJacksonJsonMessageConverter` import;
`tools.jackson.databind.json.JsonMapper` sits outside its prefix list, like the
`tools.jackson` import `IdempotencyAspect.java.example` already carries, and was checked by hand against
jackson-databind 3.1.5. `design` › `frontmatter schema` checks the `migrations` entry.

### Option 2 — typed serializer with type delegation (score 6)

A `DefaultKafkaProducerFactoryCustomizer` sets a `DelegatingByTypeSerializer`: `String` to
`StringSerializer`, everything else to `JacksonJsonSerializer.noTypeInfo()`. Form A keeps
`KafkaTemplate<String, Payload>`, Form B keeps `KafkaTemplate<String, String>`. **Cut:**
criterion 5 — a `@Configuration` customizing the producer factory, exactly the hand-wiring the
yml's header warns against (lessons-learned-013 § 8.5); criterion 6 — no precedent; criterion
4 — two producer paths that can drift; and the consumer side still needs Option 1's converter,
so it adds wiring without removing any. **CI:** same denylist, plus nothing that proves the
delegation map.

### Option 3 — relay rehydrates the payload (score 4)

`TopicResolver` returns topic plus payload class; the relay reads the row back into that class
and sends it typed. **Cut:** criterion 2 — breaks `rules/messaging.md` § Boundary, since every
flow's payload record must become `public` to `…messaging.outbox`; criterion 8 — widens
visibility the rule keeps narrow on purpose; criterion 5 — a deserialize-reserialize round trip
per row; criterion 4 — still needs a serializer decision for Form A. **CI:** same denylist.

## References

| Claim | Source |
|---|---|
| Boilerplate lives in the skill's `templates/`, never in a norm | `@CLAUDE.md` invariant 3 |
| The rule owns "what the payload is"; the exemplars show the shape | `@CLAUDE.md` invariant 2 · `rules/messaging.md` § Topics and serialization |
| Payload records are package-private per flow | `rules/messaging.md` § Boundary |
| A convention change that leaves generated code behind is a `migrations` entry, printed by `arch-adopt`, never run | `extensions.json` `migrations.$comment` · `0104` |
| Hand-wiring Kafka beans is what the starter exists to avoid | `application-kafka.yml.example` header · lessons-learned-013 § 8.5 |
| Deprecated symbols in exemplars are caught by name, not by `javap` | `validate.yml` › `exemplar-imports` › `exemplars don't use known deprecated symbols` |
| Versions never from memory | `@CLAUDE.md` invariant 8 — no version is written; the starter's BOM manages spring-kafka |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/messaging-architect/templates/application-kafka.yml.example` | `StringSerializer` / `StringDeserializer` for the value; `spring.json.trusted.packages` removed; comments carry the contract and why a JSON value serializer breaks it |
| `.claude/skills/messaging-architect/templates/KafkaProducerAdapter.java.example` | `KafkaTemplate<String, String>` plus the injected `JsonMapper`, one conversion before `send` |
| `.claude/skills/messaging-architect/templates/KafkaConsumerAdapter.java.example` | `listener/KafkaListenerConfiguration` block with the one `StringJacksonJsonMessageConverter` bean; the typed listener unchanged |
| `.claude/skills/messaging-architect/templates/OutboxRelayPublisher.java.example` | "One serialization, at append time": `OutboxAppender` converts, the relay carries the text |
| `.claude/skills/messaging-architect/templates/messaging-spec.md.example` | § 1 names the serializer pair and who converts on each side; implementation order places the converter bean |
| `.claude/skills/messaging-architect/SKILL.md` | step 7: the String pair is the contract, not a per-case default |
| `.claude/rules/messaging.md` | § Topics and serialization: JSON text on the wire, converted once per side, no type header; § How to verify: a grep for any non-String value (de)serializer |
| `.claude/schemas/extensions.json` | `migrations` entry `kafka-string-wire-contract`, every blueprint but `custom` |
| `.github/workflows/validate.yml` | the deprecated-symbols denylist gains both FQCNs and scans `*.yml.example` |
| `docs/pt-br/07-ci-validate.md`, `docs/en/07-ci-validate.md` | the `exemplar-imports` row names the `.yml.example` scan |

Goes to the generated project: **yes** — the skill, its templates and the rule travel through
`export`, and the `migrations` entry travels with `extensions.json`, so `/arch-adopt` prints its
note and prompt on the next update of a project that predates it. The F3 golden-file payload
test was deferred by the user: a described risk, not an observed failure.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · exemplar-imports › exemplars don't use known deprecated symbols` | No `.java.example` or `.yml.example` names `org.springframework.kafka.support.serializer.JsonSerializer` or `…JsonDeserializer` | green on the tree · red with the `3d976c9` yml restored, both FQCNs reported by name, exit 1 |
| `validate · exemplar-imports › every exemplar import exists in a JAR` | `StringJacksonJsonMessageConverter` resolves in a real Initializr project with `kafka` | unchanged step |
| `validate · design › frontmatter schema` | The `migrations` entry has its required fields, a valid id, and existing blueprints | `ArchHook.java schema` exit 0 |

Not covered, by the user's choice: a later edit that makes the yml and the Java exemplars
disagree again with a serializer that is not deprecated (`JacksonJsonSerializer`). The
`design` grep step and the round-trip test in `templates.yml` were weighed and declined.
