# Tipos de arquivo — mecânica de runtime

Este documento explica, para cada tipo de peça usada em `.claude/`, o que ela é, para
que serve, quando entra em contexto, e quem a invoca — o modelo ou o usuário. Toda
afirmação sobre comportamento do runtime cita a seção correspondente de
`claude-help.md` (raiz deste repositório), que é a fonte única sobre como o Claude Code
funciona neste projeto.

---

## Skill

**Onde mora:** `.claude/skills/<nome>/SKILL.md`, mais `templates/` e `references/`
opcionais dentro da mesma pasta.

**O que é:** um arquivo markdown com instruções que vira parte do repertório do
Claude — "a mais flexível das extensões" (`claude-help.md` § 5). O nome da pasta vira o
comando: `.claude/skills/arch-doctor/SKILL.md` cria `/arch-doctor`.

**Propósito:** empacotar um procedimento — uma checklist ou um fluxo que, de outra
forma, seria colado repetidamente no chat. Neste repositório, cada skill de design
(`use-case-design`, `domain-modeling`, `persistence-architect`, `rest-api-architect`,
`test-architect`) é dona de uma única camada da spec de uma feature; `project-bootstrap`
é dona do procedimento de 8 passos que gera um projeto do zero.

**Quando entra em contexto:** só quando invocada. Ao contrário de um `CLAUDE.md`, o
corpo de uma skill não carrega no início da sessão — carrega no turno em que é usada, e
o conteúdo renderizado entra como mensagem e **permanece nos turnos seguintes**; o
Claude não relê o arquivo depois (`claude-help.md` § Content lifecycle). É por isso que
uma skill deve escrever instrução permanente ("sempre faça X ao longo desta tarefa"), e
não um passo único.

**Quem invoca — modelo ou usuário:**

| Frontmatter | Efeito | Exemplo neste repo |
|---|---|---|
| (nenhum campo de controle) | O modelo decide, a partir da `description`, se a skill é relevante | `project-bootstrap` — description lista gatilhos como "scaffolding", "new Spring project" |
| `disable-model-invocation: true` | Só o usuário invoca, digitando `/nome` | `init-project`, `new-feature`, `arch-doctor` — os três comandos documentados aqui são side-effect-heavy e **não** disparam sozinhos |
| `user-invocable: false` | Só o modelo invoca — conhecimento de fundo, sem comando visível | Não usado neste repositório hoje |

Os três comandos centrais deste documento são todos `disable-model-invocation: true` —
decisão deliberada: gerar um projeto ou rodar a pipeline de feature tem efeito
colateral grande demais para disparar por inferência da conversa.

**Campos de frontmatter reconhecidos** (fonte única: `.claude/schemas/extensions.json`,
validado por `ArchHook.java schema` — `CLAUDE.md` § Invariant 10):

```
name, description, when_to_use, argument-hint, arguments,
disable-model-invocation, user-invocable, allowed-tools, disallowed-tools,
model, effort, paths, context, agent, background, hooks
```

Um campo fora dessa lista (por exemplo `metadata:`) não gera erro — o runtime **ignora
silenciosamente** campos desconhecidos, e `claude plugin validate` deixa passar também
(`CLAUDE.md` § Known pitfalls). É `ArchHook.java schema` quem bloqueia.

**Injeção dinâmica de contexto:** a sintaxe `` !`comando` `` roda um comando **antes**
do conteúdo chegar ao modelo e substitui pelo resultado — é o que faz `init-project`
listar blueprints disponíveis dinamicamente (`` !`find .claude/blueprints ...` ``) e
`arch-doctor` embutir a saída real de `ArchHook.java doctor` no corpo da skill
(`claude-help.md` § Dynamic context injection). Um comando que falha (exit ≠ 0) aborta a
invocação inteira — por isso `CLAUDE.md` § Known pitfalls alerta sobre pipes dentro de
`allowed-tools: Bash(comando:*)`: cada segmento do pipe precisa da própria regra, ou o
comando inteiro é bloqueado antes de rodar.

**`context: fork`:** roda a skill num subagent isolado, sem ver o histórico da
conversa — o corpo da skill vira o prompt do subagent (`claude-help.md` §
`context: fork`). Nenhuma skill deste repositório usa esse campo hoje: as skills daqui
preferem delegar via `Agent tool` explicitamente para um `.claude/agents/*.md` nomeado,
quando isolamento é necessário (ver § Agent abaixo).

---

## Rule

**Onde mora:** `.claude/rules/<nome>.md`.

**O que é:** uma norma — "o que deve ser verdade", nunca um procedimento. `CLAUDE.md` §
Invariant 1 é categórico: *"a norma nunca menciona uma skill, um agent, ou um comando.
Se precisar, é um procedimento e pertence a uma skill."*

**Propósito:** fixar um padrão que se aplica sempre que certos arquivos são tocados —
convenção de nomes (`naming.md`), taxonomia de exceção (`error-handling.md`), limites de
Clean Code (`code-quality.md`), e assim por diante. Cada norma tem **um único dono**
(`CLAUDE.md` § Invariant 2) — quem precisa dela cita o path, nunca copia o conteúdo.

**Quando entra em contexto:** dois mecanismos, e o primeiro é preferível
(`.claude/rules/00-index.md` § How a rule enters context):

1. **Auto-loading via `paths`** — a rule declara globs no frontmatter, e entra em
   contexto sozinha quando um arquivo correspondente é tocado. Sem depender de alguém
   lembrar de citá-la.
2. **Citação explícita** — para rules transversais que nenhum glob captura
   (`00-index.md` em si). Quem precisa cita o path.

Sem `paths`, a rule carrega **na mesma prioridade que o `CLAUDE.md` do projeto**, ou
seja, toda sessão (`claude-help.md` § 4). Neste repositório, `architecture-ddd.md` é a
única rule sem `paths` próprio por design: os globs vêm de `architecture_paths` do
blueprint ativo, e só existem depois que o projeto é gerado.

**Quem invoca:** ninguém invoca uma rule — ela **carrega**, automaticamente (por
`paths`) ou por citação. Não existe comando `/rule-name`. É por isso que a tabela de
`00-index.md` lista, para cada rule, "Verificado por" em vez de "Invocado por": uma rule
que precisa de verificação mecânica aponta para um hook, um plugin de build
(Checkstyle) ou um teste (ArchUnit) — nunca para si mesma.

**Frontmatter reconhecido** (`.claude/schemas/extensions.json`):

```
status (obrigatório: active | draft | deprecated), paths (opcional)
```

Nenhum outro campo é permitido em uma rule — o schema para esse tipo é o mais estrito
dos três.

---

## Agent (subagent)

**Onde mora:** `.claude/agents/<nome>.md`.

**O que é:** um assistente especializado que roda em **janela de contexto isolada**,
com prompt de sistema, acesso a tools e model opcionalmente próprios
(`claude-help.md` § 7). O frontmatter define os metadados; o corpo markdown vira o
**system prompt** do subagent.

**Propósito — e quando NÃO virar um agent:** `CLAUDE.md` § Invariant 5 fixa o critério:
um agent só se justifica por um dos três motivos — preservar contexto (saída verbosa
que não deveria poluir a conversa principal), restringir tools, ou trocar de model. Se
nenhum se aplica, a peça é uma skill.

Os três agents deste repositório documentam explicitamente qual motivo aplica, na
própria seção `## Why this is an agent` (ou `## Why this is Form 3`):

| Agent | Contexto | Tools | Model |
|---|---|---|---|
| `project-initializer` | Saída verbosa da extração do `starter.tgz`, POMs, build output | `Read, Write, Edit, Bash, Glob, Grep, AskUserQuestion` — restrito | `opus` — validar grafo de dependências e reestruturar módulos falha caro |
| `java-spring-boot-developer` | Spec completo substitui a entrevista — o agent só executa | `Read, Write, Bash` — só lê spec/templates, só escreve em `src/` | `sonnet`, `effort: max` — gerar ~19 passos de código compilável |
| `archunit-installer` | Modo setup da `test-architect` não tem entrevista — `curl` no Maven Central e até três builds `./mvnw` ficariam permanentes na conversa principal se rodassem inline | `Read, Write, Edit, Bash` — só dentro do projeto | `sonnet`, `effort: medium` — traduzir pacotes do exemplar para o layout real do blueprint e diagnosticar falha de regra ArchUnit exige julgamento, não só execução mecânica |

**Quando entra em contexto:** só quando invocado — nunca automaticamente por
`paths` (agents não têm esse campo). **Não vê** o histórico da conversa principal, nem
a memória automática da sessão principal, nem o que foi lido antes (`claude-help.md` §
What the subagent sees). Vê: o próprio system prompt, a hierarquia de `CLAUDE.md`, git
status, a mensagem de delegação, e skills pré-carregadas via o campo `skills:` do seu
próprio frontmatter — é assim que `java-spring-boot-developer` carrega o catálogo de
`java-patterns` inteiro, sem invocá-lo como turno separado.

**Quem invoca:** sempre outra peça do sistema, nunca o usuário diretamente por
`/nome-do-agent` — não existe esse comando. Aqui, é sempre uma skill que delega via
`Agent tool`: `init-project` delega para `project-initializer`, `new-feature` delega
para `java-spring-boot-developer` após consolidar a spec, `test-architect` delega para
`archunit-installer` no modo setup (sem argumento).

**Fork — um caso diferente de agent:** um *fork* herda a conversa inteira em vez de
começar do zero (`claude-help.md` § Forks). Só o resultado final volta à conversa
principal; as chamadas de tool internas ficam fora do seu contexto. Nenhum dos agents
deste repositório é um fork — todos recebem apenas a mensagem de delegação, de
propósito, porque o ponto é justamente **não** herdar o ruído da conversa de design.

**Campos de frontmatter reconhecidos** (`.claude/schemas/extensions.json`, `case: camel`):

```
name, description, tools, disallowedTools, model, permissionMode,
maxTurns, skills, mcpServers, hooks, memory, background, effort,
isolation, color
```

**Arquivos silenciosamente ignorados:** um agent sem `name`, com `---` fora da
primeira linha, com `name` começando com `-` ou contendo `:`, sem `description`, ou com
YAML inválido — o Claude Code simplesmente pula o arquivo, sem erro visível
(`claude-help.md` § Files silently ignored). É por isso que este repositório valida com
`java .claude/hooks/ArchHook.java schema`, que é estrito onde o runtime é silencioso.

---

## `CLAUDE.md`

**Onde mora:** raiz do repositório (ou `.claude/CLAUDE.md`), com hierarquia adicional
em `~/.claude/CLAUDE.md` (usuário) e `./CLAUDE.local.md` (pessoal, fora do git).

**O que é:** o arquivo de memória de projeto — instruções que o time versiona e que
todo mundo (humano ou modelo) lê. `claude-help.md` § 3 descreve a hierarquia completa;
todos os níveis são **aditivos**, não se substituem.

**Propósito neste repositório:** índice e roteamento, não manual. `CLAUDE.md` § How a
rule enters context e a própria seção `## Routing` deste arquivo funcionam como uma
tabela "se a tarefa envolve X, vá para Y" — a norma em si mora na skill ou na rule
citada, nunca dentro do `CLAUDE.md`.

**Quando entra em contexto:** sempre, no início da sessão — arquivos no diretório
atual **e em todo diretório acima dele** carregam no startup; arquivos em
subdiretórios carregam sob demanda, quando o Claude lê algo lá dentro
(`claude-help.md` § File hierarchy). Diferente de uma skill, não há como um `CLAUDE.md`
carregar "só quando necessário" — é sempre pago, por isso a meta de manter abaixo de
200 linhas (`claude-help.md` § Writing an effective CLAUDE.md).

**Quem invoca:** ninguém — carrega automaticamente. O comando `/init` gera um
`CLAUDE.md` inicial a partir do código existente; `/memory` lista e abre os arquivos de
memória (nativos do Claude Code, distintos deste repositório).

**Duas versões relevantes aqui:**

| `CLAUDE.md` | Papel |
|---|---|
| Raiz deste repositório | Documenta o meta-repositório — invariantes de arquitetura do `.claude/`, tabela de roteamento para as skills daqui |
| `.claude/skills/project-bootstrap/templates/root.CLAUDE.md.example` | Molde do `CLAUDE.md` que `project-bootstrap` escreve **dentro do projeto gerado** — dados diferentes (nome, módulos, versões resolvidas), mesma disciplina de tamanho e de citar por path |

`CLAUDE.md` § Known pitfalls é explícito: *"o `CLAUDE.md` na raiz de um projeto gerado
não é este arquivo."* Confundir os dois é o erro mais comum ao editar este repositório.

---

## Hook

**Onde mora:** `.claude/settings.json` (configuração) + `.claude/hooks/ArchHook.java`
(lógica).

**O que é:** um script disparado em eventos do ciclo de vida — "determinístico:
sempre acontece no evento, independente do que o modelo decide. Essa é a diferença
entre pedir e garantir" (`claude-help.md` § 8). É a única peça deste sistema que o
modelo **não pode** optar por pular.

**Propósito:** tudo que precisa valer sempre, sem depender de o modelo lembrar —
`CLAUDE.md` § Invariant 6: *"se uma regra precisa valer sempre, é um hook ou
`permissions.deny` — não prosa em markdown."* `ArchHook.java` tem cinco modos:

| Modo | Evento | Bloqueia? | O que faz |
|---|---|---|---|
| `check` | `PostToolUse` (Write\|Edit) | Sim (exit 2) | Imports proibidos (via `.claude/forbidden-imports.txt`) + compilação incremental do módulo tocado |
| `format` | `PostToolUse` (Write\|Edit) | Nunca | `spotless:apply` no módulo tocado |
| `tests` | `Stop` | Sim (exit 2) | Roda testes dos módulos alterados desde `HEAD` |
| `schema` | `PreToolUse` (Write) + `PostToolUse` (Edit) + `Stop` | Sim (exit 2) | Valida frontmatter de skills/agents/rules contra `.claude/schemas/extensions.json` |
| `doctor` | Manual (`/arch-doctor`) | Nunca | Diagnóstico do setup na máquina |

**Quando entra em contexto:** hooks não "entram em contexto" como texto — rodam como
**processo externo** (aqui, `java ArchHook.java <modo>`), e o que volta para o modelo é
saída estruturada (stderr vira a razão do bloqueio) ou JSON (`claude-help.md` §
Structured JSON output). O campo `if` em cada entrada de `settings.json` filtra por
regra de permissão **antes** de instanciar a JVM — sem isso, cada `Write` pagaria o
custo de subir uma JVM à toa (comentário `_comment` em `.claude/settings.json`).

**Quem invoca:** o runtime, automaticamente, no evento configurado — nunca o modelo
nem o usuário diretamente. O único jeito de rodar `ArchHook.java` "manualmente" é via
`java .claude/hooks/ArchHook.java doctor` no terminal, ou indiretamente pela skill
`arch-doctor`, que embute essa chamada via `` !`comando` `` no próprio corpo (ver §
Skill acima).

**Forma exec vs. shell:** `settings.json` usa **forma exec** (`command: java` + `args:
[...]`) em vez de forma shell — sem shell, sem bit de execução, idêntico em Linux,
macOS e Windows (`claude-help.md` § Configuration; `CLAUDE.md` § Commands). É por isso
que não há `chmod` em nenhum passo de `project-bootstrap`.

**Códigos de saída:**

| Código | Comportamento |
|---|---|
| `0` | Sucesso — se stdout for JSON válido, é interpretado |
| `2` | **Bloqueia a ação** (em eventos que suportam bloqueio) — stderr vira a razão mostrada ao modelo |
| Outro | Erro não-bloqueante — a ação prossegue |

Em `PostToolUse` o exit `2` não desfaz a ação (a tool já rodou), mas o stderr é
mostrado ao Claude — é assim que `check` reporta uma violação de boundary depois que o
arquivo já foi escrito: a escrita aconteceu, mas o modelo é avisado para corrigir.

**Onde configurar:** `.claude/settings.json` (deste projeto, versionado) é o único
lugar usado aqui — não há `~/.claude/settings.json` pessoal nem
`.claude/settings.local.json` específicos deste fluxo (`claude-help.md` § Where to
configure lista as demais opções, incluindo hooks por frontmatter de skill/subagent,
não usados neste repositório).

---

## Resumo comparativo

| | Skill | Rule | Agent | `CLAUDE.md` | Hook |
|---|---|---|---|---|---|
| Formato | `SKILL.md` + pasta | `.md` solto | `.md` solto | `.md` solto | `.json` + script |
| Contém | Procedimento | Norma | System prompt + config | Índice/roteamento | Lógica determinística |
| Entra em contexto | Sob invocação | Auto (`paths`) ou sempre | Sob invocação, isolado | Sempre, no startup | Nunca como texto — roda como processo |
| Invocado por | Modelo e/ou usuário (`disable-model-invocation`) | Ninguém — carrega | Outra skill/agent, via Agent tool | Ninguém — carrega | Runtime, no evento |
| Pode citar | Rules, blueprints, outras skills, agents | Nada (folha) | Rules, skills que segue | Skills, rules, agents (por path) | Nada — é código |
| Bloqueia algo? | Não, por si só | Não, por si só | Não, por si só | Não, por si só | Sim, com exit 2 |
