# 02 — Skills

Páginas oficiais cobertas:

| Página | Link |
|--------|------|
| Extend Claude with skills | <https://code.claude.com/docs/en/skills> |
| Commands (inclui skills bundled) | <https://code.claude.com/docs/en/commands> |
| Extend agents with skills (SDK) | <https://code.claude.com/docs/en/agent-sdk/skills> |
| Agent Skills (padrão aberto) | <https://agentskills.io> |

---

## O que é e quando criar

Uma skill é um `SKILL.md` com instruções que o Claude adiciona ao seu toolkit. Você invoca
com `/nome-da-skill`, ou o Claude carrega sozinho quando a `description` casa com a tarefa.

Crie uma skill quando:

- você cola as mesmas instruções, checklist ou procedimento no chat repetidamente;
- uma seção do `CLAUDE.md` virou procedimento em vez de fato.

Diferença essencial para `CLAUDE.md`: o corpo da skill **só carrega quando usada**, então
material de referência longo custa quase nada até ser necessário.

Comandos customizados foram fundidos com skills: `.claude/commands/deploy.md` e
`.claude/skills/deploy/SKILL.md` criam ambos `/deploy`. O formato de skill adiciona
diretório para arquivos de apoio, frontmatter de controle de invocação e carregamento
automático pelo modelo.

Claude Code segue o padrão aberto [Agent Skills](https://agentskills.io) e o estende.

## Onde as skills vivem

| Local | Caminho | Carrega em |
|-------|---------|-----------|
| Enterprise | `.claude/skills/<nome>/SKILL.md` no diretório de managed settings | Todas as máquinas da organização |
| Pessoal | `~/.claude/skills/<nome>/SKILL.md` | Todos os seus projetos (não em Cowork/cloud) |
| Projeto | `.claude/skills/<nome>/SKILL.md` | Sessões neste repositório (commite) |
| Aninhada | `<subdir>/.claude/skills/<nome>/SKILL.md` | Sessões iniciadas em ou abaixo de `<subdir>` |
| `--add-dir` | `.claude/skills/` no diretório adicionado | Aquela sessão |
| Plugin | `<plugin>/skills/<nome>/SKILL.md` | Onde o plugin está habilitado, como `/plugin:skill` |
| claude.ai | Skills habilitadas na conta | Cowork, cloud e terminal logado com a conta |

Regras de resolução de nomes: enterprise > pessoal > projeto; skill vence arquivo em
`.claude/commands/`; skill de projeto substitui um comando bundled (mas não os aliases dele);
skills de plugin são namespaced e coexistem; skill sincronizada perde o nome curto para
qualquer outro comando homônimo e roda como `/anthropic-skills:<nome>`.

Nomes reservados: `synced` (pasta de skills sincronizadas) e `anthropic-skills` (namespace
das skills da conta claude.ai).

Em monorepos, skills carregam do diretório onde você iniciou até a raiz do repo. Skills em
subdiretórios abaixo só carregam quando o Claude toca um arquivo naquela pasta (ou depois de
`/add-dir`). Se o nome colide, `/deploy` roda a da raiz e `/apps/web:deploy` roda a aninhada.

## Anatomia

```yaml
---
name: my-skill
description: O que a skill faz e quando usá-la
disable-model-invocation: true
allowed-tools: Read Grep
---

Instruções em Markdown aqui...
```

- Todos os campos são opcionais; só `description` é recomendado — é ele que decide quando o
  Claude carrega a skill sozinho.
- O frontmatter só é lido se o `---` de abertura for a **primeira linha** do arquivo.
- Campo desconhecido é ignorado silenciosamente (nada de erro) — cuidado com typos.
- `description` + `when_to_use` são truncados em 1.536 caracteres na listagem: ponha o caso
  de uso principal primeiro.
- Booleanos aceitam `yes/no/on/off/1/0` além de `true/false` (v2.1.218+).

### Campos de frontmatter (principais)

| Campo | Efeito |
|-------|--------|
| `name` | Nome do comando no menu `/`; default é o nome do diretório |
| `description` | O gatilho: o que faz e quando usar |
| `when_to_use` | Frases-gatilho adicionais, anexadas à description |
| `argument-hint` | Dica de autocomplete, ex.: `[issue-number]` |
| `arguments` | Argumentos posicionais nomeados para substituição `$nome` |
| `disable-model-invocation` | `true` = só você invoca; tira a description do contexto |
| `user-invocable` | `false` = só o Claude invoca; some do menu `/` |
| `allowed-tools` | Pré-aprova tools **durante o turno** que invocou a skill |
| `disallowed-tools` | Remove tools do pool enquanto a skill está ativa |
| `model` / `effort` | Sobrescreve modelo e nível de esforço durante o turno |
| `context: fork` | Roda em subagent isolado |
| `agent` | Qual tipo de subagent usar com `context: fork` |
| `background` | `false` faz o fork bloquear o turno até terminar |
| `hooks` | Registra hooks quando a skill é invocada, válidos pelo resto da sessão |
| `paths` | Globs que limitam quando o Claude ativa a skill automaticamente |
| `shell` | `bash` (padrão) ou `powershell` para comandos injetados |
| `metadata`, `license`, `compatibility` | Metadados; o Claude Code aceita mas não age |

Fora do Claude Code (upload para claude.ai, Skills API, `package_skill.py`) só valem os seis
campos do spec: `name`, `description`, `license`, `compatibility`, `metadata`,
`allowed-tools`. Campo extra causa erro duro de upload.

## Controle de invocação

| Frontmatter | Você invoca | Claude invoca | Contexto |
|-------------|-------------|---------------|----------|
| (padrão) | Sim | Sim | Description sempre no contexto; corpo ao invocar |
| `disable-model-invocation: true` | Sim | Não | Description fora do contexto |
| `user-invocable: false` | Não | Sim | Description sempre no contexto |

Use `disable-model-invocation: true` para tudo com efeito colateral (`/commit`, `/deploy`,
`/send-slack-message`). Use `user-invocable: false` para conhecimento de fundo que não é uma
ação (ex.: `legacy-system-context`).

## Ciclo de vida do conteúdo

- Ao invocar, o `SKILL.md` renderizado entra na conversa como **uma mensagem e permanece nos
  turnos seguintes**. O arquivo não é relido — escreva instruções permanentes, não passos de
  uma vez só.
- Reinvocar com conteúdo idêntico adiciona só uma nota "já carregada"; se mudou (argumentos
  ou contexto dinâmico novo), o conteúdo completo é anexado de novo.
- Auto-compactação reanexa a invocação mais recente de cada skill, mantendo os primeiros
  5.000 tokens de cada, com orçamento combinado de 25.000 tokens (das mais recentes para as
  mais antigas).
- Se a skill "parou de influenciar", o conteúdo geralmente continua lá e o modelo está
  escolhendo outro caminho: reforce a `description`, ou use hooks para enforcement.
- Mantenha `SKILL.md` abaixo de ~500 linhas; material extenso vai para arquivos de apoio
  referenciados a partir dele.

## Argumentos e substituições

| Variável | Significado |
|----------|-------------|
| `$ARGUMENTS` | Tudo que veio depois do nome da skill |
| `$ARGUMENTS[N]` / `$N` | Argumento por posição (0-based) |
| `$nome` | Argumento nomeado declarado em `arguments` |
| `${CLAUDE_SESSION_ID}` | ID da sessão |
| `${CLAUDE_EFFORT}` | Nível de esforço atual |
| `${CLAUDE_SKILL_DIR}` | Diretório do `SKILL.md` |
| `${CLAUDE_PROJECT_DIR}` | Raiz do projeto |
| `${CLAUDE_PLUGIN_ROOT}` / `${CLAUDE_PLUGIN_DATA}` | Só em skills de plugin |

`${CLAUDE_SKILL_DIR}` e `${CLAUDE_PROJECT_DIR}` também são substituídos dentro de regras Bash
em `allowed-tools` — é assim que uma skill roda um script próprio sem prompt de permissão:

```yaml
---
name: render-chart
description: Render a chart from a CSV file
allowed-tools: Bash(${CLAUDE_SKILL_DIR}/scripts/render.sh *)
---
Run `${CLAUDE_SKILL_DIR}/scripts/render.sh <csv-file>` to render the chart.
```

Dá para empilhar skills numa mensagem: `/write-tests /fix-issue 123` carrega ambas e passa
`123` como argumento para as duas (até 6 no total; a expansão para na primeira que não for
uma skill inline).

## Contexto dinâmico (`!` commands)

`` !`comando` `` roda o comando **antes** de o conteúdo chegar ao modelo e substitui a linha
pela saída. Para múltiplas linhas, use um bloco cercado aberto com ` ```! `.

Regras importantes:

- Só é reconhecido quando `!` está no início da linha ou após espaço em branco.
- A substituição roda uma vez; a saída não é reescaneada.
- Diretório de trabalho é o do shell da sessão; `stderr` é mesclado ao `stdout` no bash;
  timeout padrão de 2 minutos da tool Bash.
- **Falha aborta a invocação inteira** (`Shell command failed for pattern "..."`), não só o
  placeholder. Exit code 1 de comandos de busca/comparação é tolerado; use `|| true` no resto.
- Comandos injetados nunca pedem permissão: são checados contra as regras antes. Deny aborta;
  fora do auto mode, qualquer resultado diferente de "allow" também aborta.
- `disableSkillShellExecution: true` nos settings desliga isso para skills de usuário,
  projeto, plugin e `--add-dir` (bundled e managed não são afetadas).
- Skills sincronizadas da claude.ai nunca rodam esses comandos na sua máquina.

## Rodar em subagent (`context: fork`)

```yaml
---
name: deep-research
description: Research a topic thoroughly
context: fork
agent: Explore
---
Research $ARGUMENTS thoroughly: ...
```

- Cria um subagent do tipo em `agent` (default `general-purpose`) e entrega o conteúdo da
  skill como prompt. O subagent **não vê o histórico da conversa** — as instruções precisam
  se sustentar sozinhas.
- Roda em background por padrão (v2.1.218+); `background: false` bloqueia o turno. Também
  bloqueia em `-p`, com `CLAUDE_CODE_DISABLE_BACKGROUND_TASKS=1`, quando já há uma invocação
  rodando, e em scheduled tasks.
- Fork em background usa o conjunto reduzido de tools de subagents em background, e as edições
  ficam fora dos checkpoints (`/rewind` não desfaz; use git).
- Só faz sentido para skills com tarefa explícita; skills que são só diretrizes retornam vazio.

## Restringir o acesso do Claude às skills

- Negar a tool `Skill` inteira desliga todas.
- Regras de permissão: `Skill(commit)` (exato), `Skill(review-pr *)` (prefixo),
  `Skill(deploy *)` em deny. Deny também bloqueia por alias e por nome não qualificado.
- `Skill(anthropic-skills:pdf)` aprova uma skill sincronizada específica.
- `skillOverrides` nos settings muda visibilidade de skills que você não escreveu
  (`"off"`, `"user-invocable-only"`).

## Skills bundled

Vêm com o Claude Code e são prompt-based: `/doctor`, `/code-review`, `/batch`, `/debug`,
`/loop`, `/verify`, `/run`, `/run-skill-generator`, `/simplify`, `/design`, `/dataviz`,
`/claude-api`, `/update-config`, `/fewer-permission-prompts`, `/workflow-authoring`, entre
outras. `disableBundledSkills: true` desliga o conjunto.

Trio de execução/verificação:

| Skill | Função |
|-------|--------|
| `/run` | Sobe e dirige a aplicação para ver a mudança funcionando |
| `/verify` | Builda e roda a app para confirmar a mudança, sem cair em testes/type-check |
| `/run-skill-generator` | Grava a receita de build e launch como skill em `.claude/skills/run-<nome>/` |

## Edição durante a sessão

O Claude Code observa os diretórios de skills e aplica mudanças sem restart (exceto em bare
mode). Se você criar um diretório de skills que não existia no início da sessão, rode
`/reload-skills`. Mudanças em `hooks/`, `.mcp.json`, `agents/` de um plugin exigem
`/reload-plugins`.
