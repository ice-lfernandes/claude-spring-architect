---
paths:
  - "**/*.java"
status: active
---

# Lombok — what's allowed to be generated

Lombok generates code that nobody reviews. The criterion is a single one: **only
generate what cannot violate an invariant**. A read accessor can't; a mutator always
can. Hence the list below isn't negotiable for convenience.

The dependency is `provided`/`optional` and the annotations have `SOURCE` retention:
nothing from Lombok survives in the published bytecode. That's why Lombok doesn't count
as a framework for the purposes of `@.claude/rules/architecture-ddd.md` § Domain, and
it's legitimate to use it in the innermost module.

## Forbidden

| Annotation | Why |
|---|---|
| `@Data` | Generates `@Setter` on every field, `equals`/`hashCode` over mutable state, and a `toString` that dumps everything. An aggregate with `@Data` stops guaranteeing its invariants: any caller writes the field directly and skips the constructor |
| `@Setter` | On a class or on a field. Mutation from outside is exactly what `@.claude/rules/architecture-ddd.md` forbids: an invariant is the aggregate's responsibility, not the caller's. A state change enters through a method with a business name (`confirm()`, `cancel(reason)`), which validates before writing |

There's no exemption by layer. An inbound DTO with `@Setter` is the same defect with
fewer consequences, and the exception opens the door to the rest. For deserialization,
see below.

## `@Getter` — allowed with a condition

Allowed **if the returned object is immutable**. The getter is a read gate; if what
comes out of it is mutable, it's a write gate in disguise.

Immutable for this purpose:

- primitive, `String`, `enum`, `record`, `java.time.*`, `BigDecimal`, `UUID`
- domain value object (immutable per `@.claude/rules/architecture-ddd.md`)
- collection already returned defensively

A field of collection or mutable type **does not get `@Getter`**. Write the accessor by
hand and return a copy or immutable view:

```java
public List<OrderLine> lines() {
    return List.copyOf(lines);
}
```

`@Getter` on the field, not on the class, when only part of the fields are exposed.
`@Getter` on the class declares that **all** fields are publicly readable — rarely true
for an aggregate.

## `@FieldDefaults` — mandatory when every field is private

A class whose fields are all private declares it once at the top and omits the modifier
on each field:

```java
// without @FieldDefaults — the modifier repeats on every field
private final OrderId id;
private final Clock clock;
private List<OrderLine> lines;

// with @FieldDefaults(level = AccessLevel.PRIVATE) declared at the top of the class
final OrderId id;
final Clock clock;
List<OrderLine> lines;
```

`makeFinal = true` is allowed and recommended when **all** fields are final —
`@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)`. Don't use it when some
field has to vary: forcing `final` and then annotating exceptions costs more than
writing `final` on the ones that are.

Practical rule: if at least one field needs a visibility other than private,
`@FieldDefaults` doesn't apply — declare all modifiers by hand and don't mix the two
styles in the same file.

## Deserialization without setters

Jackson and JPA don't need `@Setter`:

- **Jackson** — a constructor with `@JsonCreator` + `@JsonProperty`, or a `record` as
  the DTO. A `record` solves the common case and allows no mutation at all
- **JPA** — a `protected` no-args constructor on the entity, with fields written via
  reflection. `@NoArgsConstructor(access = AccessLevel.PROTECTED)` is allowed: it opens
  no public write point

## Remaining annotations

`@RequiredArgsConstructor`, `@AllArgsConstructor`, `@Builder`, `@Value`,
`@EqualsAndHashCode` and `@ToString` are allowed. Two cautions:

- **`@ToString`** — exclude sensitive fields (`@ToString.Exclude`) or don't use it. A
  `toString` generated over every field prints passwords, tokens, and PII the moment the
  class ends up in a log message
- **Generated constructor and injection** — `@RequiredArgsConstructor` over `private
  final` fields generates exactly the constructor that `@.claude/rules/architecture-ddd.md`
  § Composition requires. Field `@Autowired` remains forbidden

## How to verify

Not review: the compiler. `lombok.config` at the project root flags the forbidden
annotations with `flagUsage = ERROR`, and the build breaks at compile time, with file
and line.

```
lombok.data.flagUsage = ERROR
lombok.setter.flagUsage = ERROR
```

```bash
./mvnw -q test-compile
```

The `@Getter` immutability condition is the only part of this rule that no tool
enforces. That's verified in review.
