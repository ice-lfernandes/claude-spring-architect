# Workflow do `ai-spring-setup`

English version: [`docs/en/`](en/README.md).

Esta pasta documenta como as peças do `.claude/` deste repositório colaboram para
executar os três comandos principais: `/init-project`, `/new-feature` e `/arch-doctor`.

`ai-spring-setup` não é uma aplicação Java — é um meta-repositório. O que ele produz são
**arquivos de instrução** (skills, rules, agents, hooks) que, juntos, geram projetos
Spring Boot já preparados para desenvolvimento assistido por IA. Entender como esses
arquivos se chamam entre si é o pré-requisito para editar qualquer um deles sem quebrar
os outros.

## Leitura recomendada, em ordem

| Documento | Conteúdo |
|---|---|
| [00-visao-geral.md](00-visao-geral.md) | Diagrama geral da arquitetura do `.claude/`, com legenda por tipo de peça |
| [01-tipos-de-arquivo.md](01-tipos-de-arquivo.md) | Como funciona cada tipo — skill, rule, agent, `CLAUDE.md`, hook — segundo o runtime do Claude Code |
| [02-init-project.md](02-init-project.md) | Workflow completo de `/init-project`: quem chama quem, exemplo de invocação, relatório final |
| [03-new-feature.md](03-new-feature.md) | Workflow completo de `/new-feature`: pipeline de 5 skills + executor, exemplo, relatório final |
| [04-arch-doctor.md](04-arch-doctor.md) | Workflow de `/arch-doctor`: o que ele diagnostica e como ler a saída |
| [05-blueprints.md](05-blueprints.md) | Contrato `_schema.md`, o que cada blueprint declara, como criar um blueprint customizado |
| [06-claude-code-architect-designer.md](06-claude-code-architect-designer.md) | Workflow de `/claude-code-architect-designer`: as 5 fases, matriz de decisão, campos de frontmatter, exemplo completo |
| [07-ci-validate.md](07-ci-validate.md) | `validate.yml`: por que existe, o que cada job/passo verifica, o que ainda falta cobrir |

## Como este repositório está organizado (referência rápida)

```
.claude/
├── hooks/ + settings.json   infra de enforcement — determinístico
├── skills/                  procedimento + exemplares
├── agents/                  execução isolada
├── rules/ + blueprints/     normas e dados (folhas — não chamam ninguém)
└── decisions/               histórico de decisão — fora do grafo em runtime
```

Esse é o mesmo grafo de dependências descrito em `CLAUDE.md` § Architecture of the AI
files, aplicado à própria pasta `.claude/` como Clean Architecture. Os documentos desta
pasta detalham como esse grafo se comporta nos três comandos citados acima.

## O que esta documentação não é

Não é uma cópia das regras — cada `SKILL.md`, `rule` e `agent` continua sendo a fonte
única (`CLAUDE.md`, invariante 2). O que está aqui é a **leitura entre os arquivos**: a
sequência de chamadas, o motivo de cada peça existir na forma em que existe, e exemplos
concretos de execução. Onde este documento cita um comportamento do runtime do Claude
Code (frontmatter, hooks, subagents), a fonte é `claude-help.md`, na raiz deste
repositório.
