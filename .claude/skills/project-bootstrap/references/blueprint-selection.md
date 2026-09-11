# Choosing the blueprint

Present this table to the user when they don't indicate the architecture. The data
comes from the YAML files themselves (`when_to_choose`, `trade_offs`) — read them, don't
copy them from here.

| Blueprint | Choose if | Cost |
|---|---|---|
| `layered` | CRUD, team already fluent in Spring's controller/service/repository vocabulary | Single-module boundary cost, same as `clean-architecture-single-module` — ArchUnit only, not the compiler |
| `clean-multimodule` | Long-lived system, medium/large team | More ceremony; many mappings |
| `hexagonal` | Multiple inbound/outbound channels | More modules than layered |
| `onion` | Palermo's own terminology (Application Core, Gateway), persistence/presentation as independent peers | Domain module carries model + services + every Gateway — bigger compilation unit than hexagonal's or clean architecture's |
| `vertical-slice` | Many loosely coupled features, fast delivery | Duplication between slices; cross-cutting refactor is expensive |
| `modular-monolith` | Multiple bounded contexts, future exit to microservices | A second verification tool (`ApplicationModules.verify()`) alongside ArchUnit; still a single deployable — no runtime isolation between modules |
| `custom` | You already have an in-house pattern | You have to describe and validate it |

## Tie-breaker questions

1. How many inbound channels will you have in 12 months? One → `layered`/`vertical-slice`.
   Two or more → `hexagonal`.
2. Does the domain have its own rules or is it just passing data through? Pass-through →
   don't pay for `clean-multimodule`.
3. Is there more than one bounded context? Yes → `modular-monolith`.
4. How many people touch the repo? More than 4 → build-enforced boundaries
   (multi-module) pay off.

Don't default to recommending the most sophisticated architecture. The cost of
over-engineering is paid every day; the cost of under-engineering is paid once, at
refactor time.
