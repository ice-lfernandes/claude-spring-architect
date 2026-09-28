# Lessons learned — diagnóstico do `.claude/`

Diagnóstico de 2026-09-28 contra o snapshot da documentação oficial em
`.claude/claude-code-docs/` (2026-09-27). Foco: boas práticas, custo de tokens/latência,
aproveitamento do ecossistema Claude Code.

Cada tópico é independente: ataque um por vez com `/claude-code-architect-designer`,
colando o bloco do tópico como entrada. Tokens estimados em bytes/4.

| ID | Tópico | Prioridade | Precisa decisão antes? |
|---|---|---|---|
| A | Hooks sobem JVM e recompilam `ArchHook.java` a cada chamada | P1 custo | Não |
| B | `project-bootstrap/SKILL.md` grande demais + cópias via modelo | P1 custo | **Sim** (como dividir) |
| C | `project-initializer` em `opus` | P1 custo | Não |
| D | Skills leves herdam modelo da sessão | P1 custo | Não |
| E | Rules sem `paths` carregam em toda sessão | P1 custo | Não |
| F | `allowed-tools: Bash` sem filtro | P2 segurança | Não |
| G | `git-publish` pré-aprova push / repo create | P2 segurança | Não |
| H | Deny de `git push --force` contornável | P2 segurança | Não |
| I | Deny de segredos incompleto | P2 segurança | Não |
| J | `.claude/decisions/` ignorado no git mas citado 70+ vezes | P3 consistência | **Sim** (versionar ou remover) |
| K | `java-patterns` com DMI mas roteada para o modelo | P3 consistência | Não |
| L | Linha `claude plugin validate` contraditória no `CLAUDE.md` | P3 consistência | Não |
| M | Armadilhas de `audit` no `CLAUDE.md` raiz | P3 custo | Não |
| N | Recursos do ecossistema não usados | P3 ecossistema | Não |
| O | Menores | P3 | Não |

Ordem sugerida: A → F/G/H → J/K/L → B/C.

---

## O que já está certo (não mexer)

Registrado para que nenhum tópico abaixo desfaça o que funciona:

- **Invariante 6 (regra obrigatória = hook):** `schema`, `guard`, secret scan. É a
  recomendação da doc: `CLAUDE.md`/skills são contexto, não enforcement.
- **Dono único dos campos** em `.claude/schemas/extensions.json`: cobre a falha silenciosa
  de campo desconhecido.
- **Hooks exec form** com `timeout`, `statusMessage` e (parcialmente) `if`.
- **Stop gate** de testes com guarda `stop_hook_active` (nível 3 de verificação da doc).
- **`disable-model-invocation: true`** em skills com efeito colateral ou manuais — tira a
  description do contexto.
- **Rules com `paths`** (11 de 13): só carregam quando o glob casa.
- **`@` dentro de code span** no `CLAUDE.md`: ponteiro, não import — `README.md` e
  `claude-help.md` não carregam no startup.
- **`CLAUDE.md` com 172 linhas** (alvo da doc: < 200).
- **Agents justificados** (contexto/tools/modelo); executores em `sonnet`.
- **Templates `.example` e `references/`** carregados sob demanda.
- **Injeção `` !`cmd` ``** para estado real.
- **`/clear` entre use cases** no `new-feature`.
- **Deny de `Read`** em segredos (cobre `cat`/`head`/`tail`/`sed` no Bash).
- **`git push` como `ask`** no template do projeto gerado.

---

## A — Hooks sobem JVM e recompilam `ArchHook.java` a cada chamada

**Prioridade:** P1 custo/latência

**Onde:**
- `.claude/settings.json` — PostToolUse `Write|Edit` → `format` e `check` **sem `if`**
- `.claude/skills/project-bootstrap/templates/settings.json.example` — ~20 entries JVM
- `.claude/hooks/ArchHook.java` (129 KB, source-launch)

**Problema:** `java ArchHook.java <modo>` compila o fonte inteiro a cada chamada. Medido:
`format` 2354 ms, `check` 1995 ms, `schema` 2584 ms — todos com trabalho útil zero
(exit 0). `format` sai logo para não-`.java` (`ArchHook.java:125`) e `check` sai sem
`forbidden-imports.txt`, mas o custo da JVM já foi pago.

- Neste repo: editar um `.md` custa 3 JVMs (~7 s). Todo Stop custa 2.
- Projeto gerado: cada prompt = 2 JVMs (`audit prompt`, `guard prompt`); cada edit ≈ 4
  (`audit file`, `format`, `check`, `guard`); cada Skill/Agent +2.

**Por que não está bom:** latência somada em todo turno; hooks síncronos travam a sessão.
A doc recomenda filtro `if` justamente para não disparar hook irrelevante.

**Sugestão:**
1. Imediato: `"if": "Edit(**/*.java)"` / `"if": "Write(**/*.java)"` em `format` e `check`
   (aqui e no template).
2. Estrutural: compilar uma vez (`javac -d .claude/hooks/classes`, com cache por hash do
   fonte) no passo 7 do bootstrap e invocar `java -cp` (~200 ms). Alternativa: AppCDS.
3. Consolidar modos por evento (ex.: `post-edit` = audit + format + check numa JVM).

**Pronto quando:** editar `.md` neste repo não sobe JVM de `format`/`check`; no projeto
gerado, um Edit em `.java` custa ≤ 1 JVM e < 500 ms de hook.

---

## B — `project-bootstrap/SKILL.md` grande demais + cópias via modelo

**Prioridade:** P1 custo · **Decisão necessária:** como dividir

**Onde:** `.claude/skills/project-bootstrap/SKILL.md` (1078 linhas, 69 KB ≈ 17k tokens);
passos 6.6, 6.7, 6.8, 7 (leitura de rules em ~`:1032`).

**Problema:**
- Doc: SKILL.md abaixo de ~500 linhas, resto em arquivos de apoio.
- Após auto-compaction a doc só re-anexa os **primeiros 5.000 tokens** de cada skill
  (orçamento total 25k). Passos finais (6.6+) somem numa sessão longa.
- O `project-initializer` (opus) lê as 13 rules (76 KB ≈ 19k tokens) só para copiá-las.
  ≈ 36k tokens de entrada em opus antes de gerar um arquivo.

**Por que não está bom:** cópia é mecânica — não precisa de raciocínio, e fazê-la pelo
modelo viola o espírito da invariante 6 (o que deve sempre acontecer é determinístico).
Custo alto e risco de perder instrução pós-compaction.

**Sugestão:**
1. SKILL.md vira índice curto; cada passo em `references/step-N.md`, lido na hora.
2. Cópias (rules, skills de desenvolvimento, hook, schema) viram modo do `ArchHook`
   (ex.: `bootstrap-copy <dest> <blueprint>`) ou `cp` + `sed` no Bash — sem `Read` das
   rules. Reescrita de `paths` por `packages.map` também dentro do modo.

**Pronto quando:** SKILL.md < 500 linhas; nenhuma rule é lida pelo modelo durante a
geração; projeto gerado idêntico ao atual (diff de árvore).

---

## C — `project-initializer` em `opus`

**Prioridade:** P1 custo

**Onde:** `.claude/agents/project-initializer.md` (`model: opus`, sem `effort`).

**Problema:** justificativa ("validar grafo de dependências") não sustenta opus: o
procedimento é guiado por dados (blueprint YAML + Initializr) e a entrevista é
`AskUserQuestion`.

**Por que não está bom:** doc — roteie para modelo mais barato quando a tarefa não exige;
opus aqui multiplica o custo dos ~36k tokens do tópico B.

**Sugestão:** `model: sonnet` + `effort: high`. Validar com um blueprint e comparar via
`/audit-usage` no projeto gerado. Manter opus só se a validação de blueprint regredir.

**Pronto quando:** geração ponta a ponta em sonnet passa o build e o `schema` do projeto
gerado.

---

## D — Skills leves herdam o modelo da sessão

**Prioridade:** P1 custo

**Onde:** `git-publish`, `docker-architect` (e avaliar `arch-doctor`, `audit-usage`).

**Problema:** sem `model`/`effort`, rodam no modelo da sessão (hoje Opus 5.5) para
tarefas procedurais (git init/commit, colar template de compose).

**Sugestão:** `model: sonnet` + `effort: low|medium` nessas skills. Skills de design
(domínio, REST, casos de uso) continuam herdando.

**Pronto quando:** frontmatter atualizado e `java .claude/hooks/ArchHook.java schema`
exit 0.

---

## E — Rules sem `paths` carregam em toda sessão

**Prioridade:** P1 custo

**Onde:** `.claude/rules/00-index.md` (4,5 KB), `.claude/rules/architecture-ddd.md`
(3,6 KB).

**Problema:** doc — rule sem `paths` carrega no lançamento, como `CLAUDE.md`. O
frontmatter do `00-index.md` diz "never auto-loaded": falso. Qualquer `.md` em `rules/`
é descoberto recursivamente. `frontmatter-fields.md` também diz que rule sem `paths` "só
entra por citação explícita" — falso.

**Por que não está bom:** ~2k tokens pagos em toda sessão, inclusive nas que não tocam
Java; documentação interna afirma comportamento errado.

**Sugestão:**
1. Mover o índice para fora de `rules/` (ex.: `.claude/rules-index.md`) e ajustar citações.
2. `architecture-ddd.md`: `paths` de exemplo reescrito pelo bootstrap a partir de
   `architecture_paths`, como já acontece com `api-rest.md`.
3. Corrigir a seção Rule de `frontmatter-fields.md`.

**Pronto quando:** sessão nova que não toca `.java` não mostra essas rules no contexto.

---

## F — `allowed-tools: Bash` sem filtro

**Prioridade:** P2 segurança

**Onde:** skills de design, `docker-architect`, `git-publish`, `arch-doctor`,
`audit-usage`, `project-bootstrap` (11 skills).

**Problema:** `allowed-tools` pré-aprova a tool durante o turno da skill. `Bash` sem
filtro = qualquer comando sem prompt. O `guard` só casa `Write|Edit`: uma skill de design
escreve em `src/` com `sed -i` ou `>` sem bloqueio.

**Por que não está bom:** o guard foi criado porque uma skill de design já escreveu em
`src/` (ver `$comment` do `guard` em `extensions.json`). O buraco continua aberto via
Bash.

**Sugestão:**
1. Restringir: `Bash(ls *)`, `Bash(find *)`,
   `Bash(java ${CLAUDE_PROJECT_DIR}/.claude/hooks/ArchHook.java *)`, etc.
2. Adicionar `Bash` ao matcher do `guard` (detectar escrita em `design_forbidden_paths`).
3. Atenção à armadilha de pipe: `init-project` usa `find | xargs | sed` — trocar por
   comando único (`ls .claude/blueprints`).

**Pronto quando:** nenhuma skill com `Bash` sem filtro, ou justificativa escrita no
`## Contrato` de cada uma que mantiver.

---

## G — `git-publish` pré-aprova push e criação de repo

**Prioridade:** P2 segurança

**Onde:** `.claude/skills/git-publish/SKILL.md` (`allowed-tools: Bash, AskUserQuestion`,
invocável pelo modelo); `.claude/settings.json` (sem `ask` para push).

**Problema:** as "duas confirmações" são prosa no corpo. Com `Bash` pré-aprovado,
`git push` e `gh repo create` rodam sem o prompt nativo.

**Por que não está bom:** invariante 6 — regra que deve sempre valer não pode ser prosa.
Push é ação externa e difícil de reverter.

**Sugestão:**
1. `allowed-tools` só com `Bash(git init *)`, `Bash(git add *)`, `Bash(git commit *)`,
   `Bash(git status *)`.
2. `ask: ["Bash(git push *)", "Bash(gh repo create *)"]` também no `settings.json` deste
   repo (hoje só no template).

**Pronto quando:** rodar `git-publish` mostra prompt nativo no push, mesmo após as
confirmações em prosa.

---

## H — Deny de `git push --force` contornável

**Prioridade:** P2 segurança

**Onde:** `.claude/settings.json` e template — `Bash(git push --force:*)`.

**Problema:** passam `git push -f`, `--force-with-lease`, `git push origin +main`,
`git -C . push --force`, `sh -c "git push --force"`. A doc lista essas variações como
limite de regra Bash por prefixo e recomenda hook `PreToolUse` ou sandbox.

**Sugestão:** hook `PreToolUse` matcher `Bash` (modo novo do `ArchHook`, ex.: `bash-guard`)
que parseia o comando e bloqueia force push em qualquer forma. Manter o deny como camada
extra.

**Pronto quando:** as 5 variações acima retornam exit 2.

---

## I — Deny de segredos incompleto

**Prioridade:** P2 segurança

**Onde:** `permissions.deny` em `.claude/settings.json` e no template.

**Problema:** cobre `.env`, `secrets`, `application-prod.yml|yaml`, `*.pem`, `*.p12`.
Faltam `.env.*` (`.env.local`, `.env.production`), `*.key`, `*.jks`, `id_rsa*`,
`application-prod.properties`.

**Sugestão:** adicionar as entradas `Read(...)` nos dois arquivos.

**Pronto quando:** `Read` de cada padrão acima é negado.

---

## J — `.claude/decisions/` ignorado no git mas citado 70+ vezes

**Prioridade:** P3 consistência · **Decisão necessária:** versionar ou remover citações

**Onde:** `.gitignore:50`. Citações: `project-bootstrap` 24×, `test-architect` 8×,
`claude-code-architect-designer` 5×, `rest-api-architect` 4×, `domain-modeling` 4×,
`persistence-architect` 3×, `new-feature` 3×, `archunit-installer` 3×,
`use-case-design` 2×, `git-publish` 2×, `observability.md` 2×, vários 1×; linha de
roteamento no `CLAUDE.md`; `$comment` do `extensions.json`; `frontmatter-fields.md:27`.

**Problema:** em qualquer clone e em todo projeto gerado a pasta não existe. O modelo
tenta `Read`, falha, gasta tool call. Viola invariante 9 (projeto gerado autocontido).

**Sugestão:** escolher uma:
1. Versionar `decisions/` (e manter fora do projeto gerado, removendo citações das
   skills/rules copiadas).
2. Trocar cada citação por uma linha de motivação inline e remover a linha do
   `CLAUDE.md`.

**Pronto quando:** `grep -r "decisions/" .claude CLAUDE.md` só aponta para arquivos que
existem no clone.

---

## K — `java-patterns` com DMI mas roteada para o modelo

**Prioridade:** P3 consistência

**Onde:** `.claude/skills/java-patterns/SKILL.md` (`disable-model-invocation: true`,
description "Use when…"); `CLAUDE.md` roteamento "Design pattern, growing `if`/`switch`
chain → skill `java-patterns`"; `java-spring-boot-developer` (`skills: java-patterns`).

**Problema:** com DMI o modelo não invoca e a description nem entra no contexto — o
roteamento aponta para algo inalcançável. A skill é conhecimento, não efeito colateral.
Snapshot da doc não esclarece se preload via `skills:` funciona com DMI.

**Sugestão:** tirar DMI e adicionar `paths: ["**/*.java"]`; ou marcar a linha de
roteamento como "manual `/java-patterns`". Testar o preload no agent em ambos os casos.

**Pronto quando:** modelo invoca `java-patterns` ao editar `if`/`switch` crescente, ou o
roteamento diz explicitamente que é manual.

---

## L — Linha `claude plugin validate` contraditória no `CLAUDE.md`

**Prioridade:** P3 consistência

**Onde:** `CLAUDE.md` tabela Commands ("Validate frontmatter of skills and agents");
`frontmatter-fields.md:166`; roteamento "Frontmatter fields the runtime recognizes →
`claude-help.md`".

**Problema:** a própria armadilha do `CLAUDE.md` diz que o comando não valida campos nem
olha agents. O comando também não aparece na doc oficial. O dono da lista de campos é
`extensions.json`, não `claude-help.md`.

**Sugestão:** remover a linha (o comando `schema` já está na tabela); apontar o
roteamento de campos para `extensions.json` / `frontmatter-fields.md`.

**Pronto quando:** `CLAUDE.md` não recomenda comando que ele mesmo desaconselha.

---

## M — Armadilhas de `audit` no `CLAUDE.md` raiz

**Prioridade:** P3 custo

**Onde:** `CLAUDE.md` § Known pitfalls — "The `audit` mode is off…", "The trail doesn't
record its observers…" (+ linha de roteamento longa).

**Problema:** só importam ao mexer em audit ou no projeto gerado, mas custam ~1k tokens em
toda sessão. Teste de corte da doc: "remover isto faria o Claude errar?" — aqui, só na
tarefa de audit.

**Sugestão:** mover para o corpo de `audit-usage/SKILL.md` (ou `references/`), deixando
uma linha no `CLAUDE.md`.

**Pronto quando:** `CLAUDE.md` perde ~20 linhas sem perder a informação.

---

## N — Recursos do ecossistema não usados

**Prioridade:** P3 ecossistema

| Recurso | Onde | Ganho |
|---|---|---|
| `omitClaudeMd: true` | `archunit-installer`, `commons-logging-installer` | Não precisam do roteamento do `CLAUDE.md`; menos tokens por spawn |
| `maxTurns` | os 4 agents | Teto de custo em loop |
| `memory: project` | `java-spring-boot-developer` | Acumula peculiaridades do projeto entre features |
| `/fewer-permission-prompts` | após F/G | Calibra allow com uso real |

Também: allow deste repo tem `./mvnw`/`gradlew` (não existem aqui) e não tem
`Bash(java .claude/hooks/ArchHook.java *)` — o comando manual documentado pede permissão.

**Pronto quando:** campos adicionados, `schema` exit 0, allow deste repo reflete
comandos reais.

---

## O — Menores

- `status: draft` numa rule é ignorado pelo runtime: a rule carrega e é aplicada. Só o
  `schema` entende. Documentar no `00-index.md` (ou mover drafts para fora de `rules/`).
- Stop `schema` roda em todo fim de turno (~2,5 s) mesmo sem mudança em `.claude/`.
  Resolve junto com A.
- `.claude/claude-code-docs/` está untracked: commitar ou mover para `docs/`.
