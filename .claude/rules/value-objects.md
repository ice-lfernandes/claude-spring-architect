---
paths:
  - "**/domain/model/**/*.java"
  - "**/domain/**/*.java"
status: active
---

# Value objects — when a field stops being `String`

A field with a formation rule isn't text. If there's an input capable of being
rejected, that rule has to live in a type — and in one place only. `String email`
forces every caller to remember the validation; `Email` makes it impossible to
construct an invalid one.

## Why this is a rule and not a `CLAUDE.md` section

It's a declarative fact with a territory: it applies within `domain/model` files, and
nowhere else. Adapters receive text from the outside world on purpose, and the
criterion applied here would produce noise there. Motivated by axis 4 of the interview
(identifiable territory); the nearest rejected form was a `CLAUDE.md` section, which
would cost context in every session for knowledge specific to a single package. Full
record in `@.claude/decisions/0004-value-objects-rule.md`.

## The criterion

A field becomes a value object if it has **at least one** of these:

1. **Formation rule** — there's a syntactically invalid input (`"abc"` isn't a CPF).
2. **Unit or currency** — the number alone doesn't say what it measures.
3. **Closed set** — the valid values are enumerable (then it's an `enum`, not a
   `record`).
4. **Own behavior** — there's an operation that belongs to the value (`Money.add`,
   `Cpf.masked()`), not to the aggregate that holds it.

None of these → it stays a primitive. Don't create a class just to name free text.

## Catalog — becomes a value object

| Typical field | Type | Rule that justifies it |
|---|---|---|
| `email` | `Email` | Format, maximum length, normalization to lowercase |
| `phone`, `telefone`, `celular` | `PhoneNumber` | Digits, country/area code, length per country |
| `cpf` | `Cpf` | 11 digits and check digit |
| `cnpj` | `Cnpj` | 14 digits and check digit |
| `document`, `documento` | `Document` | Discriminates the type and delegates validation — never a generic `String` |
| `amount`, `price`, `valor` | `Money` | Quantity **and** currency, together. Never `double` |
| `percentage`, `rate` | `Percentage` | Closed interval, explicit scale |
| `zipCode`, `cep` | `ZipCode` | Positional format |
| aggregate `id` | `<Aggregate>Id` | Prevents passing the wrong id as an argument |
| `status`, `type` with fixed values | `enum` | Closed set |

A document with more than one valid format (CPF or CNPJ) is **one** value object that
knows which one it is, never two fields with one of them `null`.

## Counter-catalog — stays a primitive

`name`, `description`, `comments`, `notes`, `title`, `observacao`: free text, no
formation rule. Don't wrap these in a class.

- **A length limit doesn't justify a class.** A `description` with a 500-character max
  is validated in the constructor of the aggregate that holds it.
- **Being required doesn't justify a class.** "Must not be null or empty" is an
  invariant of the aggregate.
- If the text later gains a rule (`Slug`, `Username`), then it does get promoted to a
  value object.

## Numbers — `int`, `long`, `BigDecimal`

A number alone doesn't say what it measures. The criterion is the same, applied to the
unit: if the answer to "unit of what?" isn't "of nothing," the number has a type.

### Becomes a value object

| Primitive field | Type | Why |
|---|---|---|
| `BigDecimal amount` | `Money` | A quantity without currency can't be added or compared. Scale and rounding belong to the currency |
| `BigDecimal price` | `Money` | Same. Price is money |
| `BigDecimal rate`, `BigDecimal discount` | `Percentage` | `0.15` or `15`? The scale has to be in the type, not in convention |
| `int weight`, `int distance`, `int duration` | `Weight`, `Distance`, `Duration` | Grams or kilos? Adding two numbers in different units compiles and gives the wrong result |
| `long accountNumber`, `int orderNumber` | `AccountNumber`, `OrderNumber` | An identifier with a formation rule. Arithmetic on it never makes sense — and the primitive type allows it |
| `int status`, `int type` | `enum` | Closed set written as a magic number |

**`double` and `float` don't enter the domain.** Not for money, not for percentage, not
for a measurement with a rule. Base 2 can't represent `0.1`, and the error accumulates
with every sum. Wherever there are decimal places, it's `BigDecimal`.

### Stays a primitive

| Field | Type | Why |
|---|---|---|
| item `quantity` | `int` | Dimensionless count. The "greater than zero" rule is an aggregate invariant |
| `version` | `long` | Control counter, no unit and no business operation |
| `retryCount`, `attempts` | `int` | Technical count |
| `pageSize`, `pageNumber` | `int` | Pagination is a transport concern, not a domain one |
| `positionIndex`, `order` | `int` | Pure ordinal |

A count that gains a unit stops being a count: `int quantity` stays `int`, but
`int quantityInBoxes` added to `int quantityInUnits` is exactly the defect that a
`Quantity` with a unit fixes.

A unit suffix in the name (`weightInGrams`, `timeoutInSeconds`) is the **minimum
acceptable**, not the goal. It documents the unit but doesn't verify it: nothing
prevents passing seconds where milliseconds were expected, because both are `int`.
Acceptable in a configuration parameter; not acceptable in an aggregate field subject
to arithmetic.

Dates and instants use `java.time` — `LocalDate`, `Instant`, `Duration`, `Period`.
They're already immutable value objects with a unit. Don't wrap them in another
`record`, and never store an instant as epoch `long` in the domain.

## Mandatory shape

- Immutable `record`. Validation in the compact constructor: no construction path
  bypasses it.
- Static factory `of` to normalize before validating — `trim`, lowercase, mask removal
  (`@.claude/rules/naming.md`).
- One exception per invariant, with `errorCode` in `UPPER_SNAKE_CASE`, from the typed
  family of `@.claude/rules/error-handling.md`. An invariant without a named exception
  isn't implementable.
- No framework and no I/O, per `@.claude/rules/architecture-ddd.md` § Domain. No
  `jakarta.validation`, no serialization annotations.
- Stores the **normalized** value, not the text as it arrived. `Cpf` stores digits; the
  mask is output formatting.
- Never leaves the domain as-is: the adapter maps it explicitly
  (`@.claude/rules/architecture-ddd.md` § Adapters).
- Generated accessor only if the return is immutable — `@.claude/rules/lombok.md`.

## Admitted exception

A field that crosses the boundary with no rule on our side — an opaque identifier from
an external system, a payload that's only forwarded — stays `String`, with a comment
saying whose rule it is. Validating what isn't ours invents an invariant the other side
doesn't guarantee.

## How to verify

Review. Signal to look for in a `domain/model` diff:

```bash
# Primitive fields named after a type with a formation rule.
grep -rnE '\b(String|Long|Double|double|BigDecimal)\s+(email|phone|telefone|celular|cpf|cnpj|document|documento|amount|price|valor|cep|zipCode)\b' \
  --include='*.java' -- '*/domain/model/'
```

A non-empty result is a candidate for a missing value object. Each line is justified by
the criterion above, or fixed.
