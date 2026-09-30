# 0069 · Backlog rows get `BL-NN`, so the `Satisfied by` gate stops being unsatisfiable

- **Date:** 2026-09-28
- **Scenario:** lessons-learned-014 § 7 — consolidation requires an impact row that adds a
  precondition to name its satisfier, allowing "a **named** backlog case", while `BACKLOG.md`'s
  own header states that a backlog row has no number and no slug. Two reports named the
  satisfier `UC-004`, a number that exists nowhere; it was invented twice, independently,
  because the sentence needed a name.
- **Decision:** No new piece. A `BL-NN` identifier owned by `use-case-design`, a retired table
  in `BACKLOG.md`, the gate citing it, and a `doctor` line that checks the citations still
  resolve.
- **State:** approved by Lucas Fernandes, on 2026-09-28.

## The contradiction, which is a real one and not a wording slip

`/new-feature` consolidation: the satisfier is "an approved `UC-NNN`, this case, or a **named**
backlog case", and the pipeline stops when it is "missing or vague".

`BACKLOG.md`'s header, written by `use-case-design`: *"**No number reserved** — a number is
given when the case is designed"* and *"No number, no slug. Both are fixed only when the case is
designed."*

A named backlog case cannot exist. The gate is **unsatisfiable by construction** whenever the
satisfier is in the backlog, which is the most common case by far — and a gate that cannot be
satisfied is not a gate, it is an invitation to invent a name, which is exactly what happened.

Both halves are right about what they are protecting. The header protects a real property: a
backlog row must not squat on a `UC` number, so skipping or dropping an entry leaves no gap in
the `UC` sequence. The gate protects another: a precondition with no satisfier makes an earlier
use case answer 422 on every real call. The fix is a **third namespace**, not a concession from
either.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | `UC-004` invented twice, in two reports, for a backlog row that has no number | 9 (create nothing) |
| — identifier | `BL-NN`, sequential, assigned at insertion, never reused | A slug derived from the description (it changes when the description is reworded, and a frozen spec's citation then points at nothing); a row number (it shifts the moment a row above is removed, silently) |
| — retirement | The row moves to a retired table, `BL-NN → UC-NNN-slug`, with the date | Deleting the row: a spec that cites `BL-07` is frozen, so the citation must stay resolvable exactly when the case stops being backlog |
| 9 — integration | `use-case-design` already owns `BACKLOG.md` and the `UC-NNN` assignment; the same skill assigns and retires `BL-NN` | A second owner |
| — detector | `doctor`, next to `UC references`, which already sweeps versioned files for citations of a folder that is gone | A new event, a new hook |
| 8 — destination | Both — `use-case-design` and `new-feature` travel, and `doctor` reads its data from `extensions.json`, which travels whole | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `BL-NN` + retired table + gate + `doctor` line | 8 | **Approved** |
| 2 | Drop "named" from the gate — accept a description | 5 | Rejected — it is the gate's whole content; "the KYC callback consumer" in one spec and "the callback listener" in another are not checkably the same row |
| 3 | Reserve a real `UC-NNN` for a backlog row | 4 | Rejected — it breaks the property the header protects: a dropped backlog entry would leave a hole in the `UC` sequence, and a number would exist with no folder |
| 4 | Create nothing | 3 | Rejected — the gate is unsatisfiable today and invented a number twice |

### Option 1 (score 8)

**Shape.**

- **`BL-NN`**, sequential across `BACKLOG.md`, assigned when the row is appended, never reused —
  the same discipline `@.claude/rules/naming.md` gives `UC-NNN`. It is deliberately *not* a `UC`
  number: the two sequences are independent, and that is what keeps the header's promise.
- **Two tables in `BACKLOG.md`.** The active one gains an `Id` column. A second, `## Retired`,
  holds `BL-NN | UC-NNN-slug | date` — a row moves there when `use-case-design` designs that
  case and assigns the real number.
- **The `Satisfied by` third value** becomes "a backlog case, by its `BL-NN`", in
  `use-case-design` where the column is defined and in consolidation where it is gated. The
  consequence sentence the spec already owes — *until that case ships, the earlier use case is
  unreachable end to end* — now names something a reader can look up.
- **`doctor` gains a `BL references` line**, reading `doctor.bl_references`: every `BL-NN` cited
  in a versioned file resolves in `BACKLOG.md`, active or retired. Reported, never blocking,
  exactly like `UC references`, and silent in this meta-repository, which has no
  `docs/use-cases`.

**Pros:** the gate becomes satisfiable without weakening it, and the frozen spec that cites
`BL-07` stays resolvable forever through one file. The retired table is also the only place that
records *which* backlog row a use case came from, which nothing carries today.

**Cons:** a third identifier namespace in a system that already has `UC-NNN` and slugs, and one
more table to keep ordered. Criterion 5. The retired table grows without bound — acceptable,
since it is a lookup, not a worklist.

**Points cut in the rubric:** § 8 criterion 5 (a third namespace and a second table).

### Option 2 — drop "named" (score 5)

Let the row cite the backlog entry in prose. Rejected: the gate exists because a precondition
with an unidentifiable satisfier is what shipped a 422-on-every-call use case. A description is
not checkable — two specs would write it two ways and `doctor` could never tell whether the row
still exists.

### Option 3 — reserve a `UC-NNN` at insertion (score 4)

The simplest-looking fix, and it breaks the property the header exists to protect: a backlog
entry that is dropped or merged leaves a permanently missing `UC` number, and a number with no
folder is exactly what `doctor`'s `UC references` sweep reports as a defect.

## References

| Claim | Source |
|---|---|
| The gate requires a named backlog case | `.claude/skills/new-feature/SKILL.md` § Consolidation, step 2 |
| A backlog row reserves no number and no slug | `.claude/skills/use-case-design/templates/backlog.md.example` header |
| `use-case-design` owns the number and the slug | `.claude/skills/use-case-design/SKILL.md` § Contract |
| A `UC-NNN` is never reused, even after its folder is deleted | `@.claude/rules/naming.md` § Use case identifier |
| `doctor` already sweeps versioned files for citations that no longer resolve | `doctor.uc_references` · `ArchHook.java` `orphanUseCaseRefs` |
| An approved spec is immutable, so a citation inside it cannot be updated later | `@CLAUDE.md` § Known pitfalls · `guard.frozen_statuses` |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/use-case-design/templates/backlog.md.example` | `Id` column, the `## Retired` table, and the header rewritten to say what `BL-NN` is and what it is not |
| `.claude/skills/use-case-design/SKILL.md` | step 2 assigns `BL-NN` on append; designing a case that came from the backlog moves its row to `## Retired`; the `Satisfied by` third value cites `BL-NN` |
| `.claude/skills/new-feature/SKILL.md` | the consolidation gate cites `BL-NN`; the final report repeats it |
| `.claude/schemas/extensions.json` | `doctor.bl_references` — pattern, the file it resolves against, extensions, exempt paths |
| `.claude/hooks/ArchHook.java` | the `BL references` line in `doctor`, mirroring `UC references` |
| `CLAUDE.md` § Routing | the `doctor` row mentions the new line |

Goes to the generated project: **yes** — both skills travel, and `ArchHook.java` plus
`extensions.json` travel whole.

**Restart warning:** none.

## Verification

`doctor`'s new line was exercised against a sandbox holding a `BACKLOG.md` with `BL-07` active
and `BL-02` retired, and a spec citing `BL-07`, `BL-02` and `BL-99`:

```
BL references ..... ❌ 1 citation(s) name a backlog row the file does not have
    docs/use-cases/UC-003-x/UC-003-spec.md cites BL-99, which is in neither table of BACKLOG.md
```

Both resolvable ids pass — the retired one included, which is the property the `## Retired`
table exists for. In this meta-repository the line reports
`no docs/use-cases — nothing to check (optional)`, like its twin.

`BACKLOG.md` itself is skipped by the sweep: it is where the ids are defined, so every id in it
would otherwise be reported as citing itself.

`claude plugin validate .claude/skills` passes and `java .claude/hooks/ArchHook.java schema`
passes.
