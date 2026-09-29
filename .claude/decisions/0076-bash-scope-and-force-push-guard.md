# 0076 · Bash scoped per skill, `git push` behind a prompt, force push blocked by `guard bash`

- **Date:** 2026-09-29
- **Scenario:** lessons-learned-015 topics F, G, H — "siga a ordem sugerida"
- **Decision:** option 1 — F filters + `schema` check, G `permissions.ask` in both settings, H force-push detection inside `guard bash`
- **State:** approved by Lucas Fernandes, 2026-09-29
- **Goes to the generated project:** yes — skills via `export`, `ArchHook.java`/`.jar`/`extensions.json` copied whole, the `ask` in `project-bootstrap/templates/settings.json.example`

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | 13 of 15 skills pre-approve bare `Bash` for their whole turn; `git-publish` pre-approves `git push` itself; the only force-push barrier is `deny: Bash(git push --force:*)`, which `-f`, `--force-with-lease`, `+main`, `git -C . push --force` and `sh -c "git push --force"` all walk past | 1 · 2 · 4 · 5 (prose already failed to hold it) |
| 7 — Mandatoriness | A force push to a shared branch is irreversible; a push must be seen by a human | everything above the line in § 1 of the decision matrix |
| 15 — Reaction | F: narrow a pre-approval · G: prompt · H: block | G is Form 8 `ask`; H cannot be Form 8 alone — the docs say a prefix rule misses `git -C` and `sh -c` |
| 16 — Existing mode | `guard bash` (0063) is already `PreToolUse`/`Bash` in both settings files and already tokenizes commands into segments | a new mode and a second registration (one more JVM per shell command) |
| 8 — Destination | Both — skills travel via `export`, `ArchHook.java`/`.jar`/`extensions.json` are copied whole, template settings get the `ask` | — |
| F scope | Hybrid: filter the 9 skills whose command set is short and fixed; the 4 that run builds, network and docker keep bare `Bash` with a written reason | "filter all 13" (lists of ~15 commands in bootstrap/arch-adopt, and an injection command missing from the list **aborts** the skill at runtime) · "justify all" (prose where a guarantee was available — invariant 6) |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | F filters + `schema` check · G `ask` in both settings · H force-push detection inside `guard bash`, data in `guard.force_push` | 8 | **approved** |
| 2 | Same F/G, H as a new `push-guard` mode with its own registration | 5 | rejected — a second JVM on every Bash call for a check that shares the tokenizer already paid for |
| 3 | F/G, H as a wider `deny` list only | 4 | rejected — `07-settings…md:141` names `git -C . push` and `sh -c` as what a Bash rule does not catch; it closes 3 of the 5 variants |
| 4 | create nothing | 1 | rejected — lessons-learned-015 observed the gap, it is not anticipation |

### Option 1 (score 8)

**Motivator:** axes 7 + 16 — the guarantee is mandatory and the hook that sees every shell command already exists.

**F — `allowed-tools` per skill.**

| Skill | New Bash scope |
|---|---|
| use-case-design, domain-modeling, rest-api-architect, persistence-architect | `Bash(find *)`, `Bash(ls *)`, `Bash(grep *)`, `Bash(awk *)` |
| messaging-architect | same + `Bash(./mvnw *)` |
| arch-doctor | `Bash(java *)`, `Bash(find *)` |
| audit-usage | `Bash(java *)`, `Bash(ls *)`, `Bash(grep *)` |
| init-project | `Bash(ls *)` — its injection's `find \| xargs \| sed` becomes `ls .claude/blueprints` (015 § F) |
| git-publish | `Bash(git init *)`, `add`, `commit`, `status`, `diff`, `log`, `remote`, `rev-parse`, `check-ignore`, `Bash(gh auth status *)` — **no** `git push`, **no** `gh repo create`: those reach the `ask` |

**As written** (re-derived from each body; `:*` spelling, the one the designer's own frontmatter already used):

| Skill | Final list | Differs from the table because |
|---|---|---|
| use-case-design | `find`, `ls`, `grep`, `sort`, `tail` | its injection is `find \| sort \| tail -5`; every pipe segment needs a rule |
| domain-modeling, rest-api-architect | `find`, `ls`, `grep`, `sort` | injection `find \| sort`; no `awk` in either body |
| persistence-architect, messaging-architect | `find`, `ls`, `grep`, `sort`, `awk` | messaging cites `./mvnw spring-boot:run` only as prose about the host — it never runs it, so no `./mvnw` |
| arch-doctor | `java`, `find`, `sort` | injection `find … \| sort` |
| audit-usage | `java`, `ls`, `grep` | its `mkdir`/`printf` are commands it hands the user and is told not to run |
| init-project | `find`, `sort`, `ls`, `head` | `ls .claude/blueprints` would list `_schema.md`, `README*`, `references/`, and `custom-template/` holds `custom.template.yaml` — the injection became `find … -name '*.yaml' \| sort`, dropping `xargs` (which runs any program) and `sed` |
| git-publish | local `git` subcommands above + `gh auth status` + `echo` | its state probe is `git rev-parse … && echo TRACKED \|\| echo UNTRACKED` |
| project-bootstrap, arch-adopt, docker-architect, test-architect | bare `Bash`, with `**Unfiltered Bash:** <reason>` in `## Contract` |

`ArchHook.java schema` fails by name on a skill whose `allowed-tools` has bare `Bash` and whose body carries no marker. Marker text is data: `skill_classes.unfiltered_bash_marker` in `extensions.json` (invariant 10). Exact lists are re-derived from each body while writing — the table above is the measured starting point.

**G — `permissions.ask`.** `Bash(git push *)`, `Bash(gh repo create *)` in `.claude/settings.json` (block does not exist today) and in the template (has `git push` only). `ask` is evaluated before `allow`, and a skill's `allowed-tools` is an allow — so the prompt appears even inside `git-publish` (`07-settings…md:98`). The existing `deny: Bash(git push --force:*)` stays as the zero-cost first layer.

**H — force push inside `guard bash`.** Per segment already produced by `bashSegments`:
1. `sh`/`bash`/`zsh` followed by `-c` → the next token is re-parsed as a command (one level).
2. Head `git` → skip global options, including the ones that take a value (`-C <dir>`, `-c <k=v>`, `--git-dir`, `--work-tree`); the next word is the subcommand.
3. Subcommand `push` with a `--force*` flag, a short-flag cluster containing `f` (`-f`, `-uf`), or an operand starting with `+` → exit 2, stderr naming the variant found and the way out (push without force; rewriting a shared branch is done by a human outside Claude Code).

All lists — shell wrappers, global options with a value, force flags, the short letter, the refspec prefix — live in `guard.force_push`. Exit 2 also applies to `--force-with-lease`: it is safer than `--force`, and it is still a rewrite of published history that 015 asked to block.

**Pros:** closes all 5 variants; no new registration and no new process; lists are data; the `deny` and the `ask` still act before any JVM.

**Cons:** Java change → jar rebuild (0075); a variable holding the flag (`git push $F`) is unreadable, same bar 0063 set — not completeness. `git -c alias.p='push --force' p` is not caught. The hybrid in F leaves 4 skills with bare `Bash`; H covers force push for them, `guard bash`/`guard sweep` cover in-repo writes.

**Points cut in the rubric:** criterion "cost" — one rebuild and a `schema` check; criterion "completeness" — shell-level aliases and expansions.

### Option 2 (score 5)

Same logic in a `push-guard` mode. Separation is cleaner to read, but it pays a second JVM start on every Bash command, which is the reason 0063 kept `check`/`format` off `Bash`. Topic P (nested classes) is where readability is solved.

### Option 3 (score 4)

`deny: Bash(git push -f*)`, `Bash(git push --force-with-lease*)`, `Bash(git push * +*)`. Free at runtime, and blind to `git -C . push --force` and `sh -c`, by the docs' own statement.

## References

| Claim | Source |
|---|---|
| deny → ask → allow, first match decides | `.claude/claude-code-docs/07-settings-permissoes-e-seguranca.md:98` |
| `allowed-tools` pre-approves during the turn | same file, `:460` |
| a prefix rule misses `sh -c`, `git -C . push`, `git 'push'` | same file, `:141` |
| an injection command not allowed aborts the skill | same file, `:545` |
| `Bash(git *)` admits `git -c`, which runs a named program | same file, `:124` |
| `guard bash` already registered PreToolUse/Bash in both settings files | `.claude/decisions/0063-bash-write-enforcement.md` |
| lists read from `extensions.json`, never from Java | `@CLAUDE.md` invariant 10 |
| every `ArchHook.java` change rebuilds the jar | `.claude/decisions/0075-precompiled-hook-jar.md` |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` + `.jar` | `bashForcePush` called first in `guardBash`; `checkSkillBody` rejects bare `Bash` without the marker. Jar rebuilt |
| `.claude/schemas/extensions.json` | `guard.force_push`; `skill_classes.unfiltered_bash_marker` |
| 9 skills' frontmatter | `allowed-tools` scoped, table above; `init-project`'s blueprint injection rewritten |
| project-bootstrap, arch-adopt, docker-architect, test-architect | `**Unfiltered Bash:**` line in `## Contract` |
| `git-publish/SKILL.md` | Contract and gate 2 say the push prompts through `permissions.ask` and a force push is refused by `guard bash` |
| `.claude/settings.json` | new `ask`: `Bash(git push:*)`, `Bash(gh repo create:*)`; `deny` for `--force` kept |
| `project-bootstrap/templates/settings.json.example` | `Bash(gh repo create:*)` added to the existing `ask` |
| `CLAUDE.md` | § Commands `guard bash` row; § Known pitfalls bullet |
| `docs/11-pitfalls.md` | bare `Bash` now fails `schema` |
| designer `templates/SKILL.command.md.example`, `references/frontmatter-fields.md` | template no longer ships bare `Bash`; the marker and the `ask` interaction documented |

Written by the main thread, not delegated — Form 7/8 propagation (designer Phase 4 step 8).
