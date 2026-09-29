# 01 — Rules: CLAUDE.md, `.claude/rules/` e memória

Páginas oficiais cobertas:

| Página | Link |
|--------|------|
| How Claude remembers your project | <https://code.claude.com/docs/en/memory> |
| Extend Claude Code (comparativos) | <https://code.claude.com/docs/en/features-overview> |
| Best practices — escrever um CLAUDE.md eficaz | <https://code.claude.com/docs/en/best-practices> |
| Monorepos e repositórios grandes | <https://code.claude.com/docs/en/large-codebases> |
| Debug your configuration | <https://code.claude.com/docs/en/debug-your-config> |

---

## Dois mecanismos de memória

| | `CLAUDE.md` | Auto memory |
|---|---|---|
| Quem escreve | Você | O Claude |
| Conteúdo | Instruções e regras | Aprendizados e padrões |
| Escopo | Projeto, usuário ou organização | Por repositório, compartilhado entre worktrees |
| Carregado | Toda sessão | Toda sessão (200 linhas ou 25 KB de `MEMORY.md`) |

Regra central: **ambos são contexto, não configuração aplicada**. Para bloquear uma ação
independentemente do que o modelo decidir, use um hook `PreToolUse`.

## Onde colocar `CLAUDE.md`

Ordem de carga, do escopo mais amplo ao mais específico:

| Escopo | Local | Compartilhado com |
|--------|-------|-------------------|
| Managed policy | macOS `/Library/Application Support/ClaudeCode/CLAUDE.md`; Linux/WSL `/etc/claude-code/CLAUDE.md`; Windows `C:\Program Files\ClaudeCode\CLAUDE.md` | Todos da organização |
| User | `~/.claude/CLAUDE.md` | Só você, todos os projetos |
| Project | `./CLAUDE.md` ou `./.claude/CLAUDE.md` | Time, via controle de versão |
| Local | `./CLAUDE.local.md` (gitignore) | Só você, neste projeto |

### Como carregam

- `CLAUDE.md` e `CLAUDE.local.md` do diretório atual **e de todos os diretórios acima** são
  carregados no lançamento. Todos são concatenados (não se sobrescrevem), do root do
  filesystem até o working directory — ou seja, o mais próximo de onde você lançou é lido
  por último. Dentro de cada diretório, `CLAUDE.local.md` vem depois de `CLAUDE.md`.
- Arquivos em subdiretórios abaixo do working directory carregam sob demanda, quando o
  Claude lê arquivos daquela pasta.
- Comentários HTML de bloco (`<!-- nota -->`) são removidos antes de injetar no contexto —
  úteis para notas de manutenção sem custo de tokens.
- `--add-dir` **não** carrega `CLAUDE.md` da pasta adicional, a menos que
  `CLAUDE_CODE_ADDITIONAL_DIRECTORIES_CLAUDE_MD=1`.

## Normas para escrever um `CLAUDE.md` eficaz

- **Tamanho:** alvo abaixo de 200 linhas. Arquivos maiores consomem contexto e reduzem
  aderência. Arquivos acima de 4 MiB são ignorados por completo.
- **Estrutura:** headers e bullets; seções organizadas funcionam melhor que parágrafos.
- **Especificidade:** "Use 2-space indentation", não "formate bem"; "Run `npm test` before
  committing", não "teste suas mudanças".
- **Consistência:** regras contraditórias fazem o modelo escolher uma arbitrariamente.
  Revise periodicamente `CLAUDE.md`, os aninhados e `.claude/rules/`.
- **Teste de corte:** para cada linha, pergunte "remover isto faria o Claude errar?". Se
  não, corte. `CLAUDE.md` inchado faz o modelo ignorar as regras que importam.
- **Ênfase:** se uma instrução é sempre ignorada, marque só ela com "IMPORTANT". Enfatizar
  várias linhas anula o efeito.
- Trate o arquivo como código: commite, revise quando algo der errado, pode e teste.

### Incluir vs. excluir

| Incluir | Excluir |
|---------|---------|
| Comandos bash que o Claude não adivinha | O que ele descobre lendo o código |
| Estilo que difere do padrão da linguagem | Convenções padrão da linguagem |
| Instruções de teste e runner preferido | Documentação detalhada de API (linke) |
| Etiqueta do repositório (branch, PR) | Informação que muda com frequência |
| Decisões arquiteturais do projeto | Explicações longas e tutoriais |
| Peculiaridades do ambiente (env vars) | Descrição arquivo por arquivo |
| Gotchas e comportamentos não óbvios | Obviedades como "escreva código limpo" |

## Imports com `@path`

- Sintaxe `@caminho/arquivo`; caminhos relativos resolvem em relação ao arquivo que importa.
- Recursivo, profundidade máxima de 4 hops.
- Imports dentro de code spans/blocos são ignorados: `` `@README` `` é literal.
- Imports aumentam organização, **não** reduzem contexto: o arquivo importado é carregado no
  lançamento.
- Import externo (fora do working directory) em arquivo de projeto abre um diálogo de
  aprovação na primeira vez. Arquivos de escopo do usuário (`~/.claude/...`) são confiados
  sem diálogo, exceto em sessões Cowork no desktop.

## `.claude/rules/`

Instruções divididas por tópico, opcionalmente presas a caminhos:

```
.claude/
├── CLAUDE.md
└── rules/
    ├── code-style.md
    ├── testing.md
    └── security.md
```

- Arquivos `.md` são descobertos recursivamente (`rules/frontend/react.md` funciona).
- Sem frontmatter `paths`, a rule carrega no lançamento, com a mesma prioridade de
  `.claude/CLAUDE.md`.
- Com `paths`, carrega só quando o Claude lê um arquivo que casa com o glob:

```markdown
---
paths:
  - "src/api/**/*.ts"
  - "tests/**/*.test.ts"
---

# API Development Rules
- Todo endpoint valida entrada
- Formato padrão de erro de resposta
```

- `paths` é o **único** campo que o Claude Code lê de uma rule; outros são ignorados sem
  erro. YAML inválido faz a rule carregar como se não tivesse `paths` (`claude --debug`
  mostra o erro de parse).
- Expansão de chaves (`{ts,tsx}`) tem orçamento de 1.000 padrões expandidos e 4 MiB por rule.
- `[` inicia bracket expression; para literal, escape: `photos \[2024/**`.
- Symlinks são suportados (inclusive circulares, tratados com segurança). Symlink apontando
  para fora do working directory segue a regra de import externo. Caminhos de rede
  (UNC `\\server\share`, `/net`, `/Network`) não são seguidos.
- Rules de usuário em `~/.claude/rules/` valem para todos os projetos e carregam antes das
  de projeto; nenhuma sobrescreve a outra, então mantenha-as consistentes.

## `AGENTS.md`

Desde a v2.1.277 o Claude Code lê `AGENTS.md` diretamente:

| Repositório tem | Claude lê |
|-----------------|-----------|
| `AGENTS.md`, sem `CLAUDE.md`/`CLAUDE.local.md` no path | `AGENTS.md` |
| Ambos | Só os `CLAUDE.md` |
| `CLAUDE.md` que importa `AGENTS.md` | `CLAUDE.md`, com o import expandido |

Para mudar, `/config` → **Project instructions**: `claude-md-or-agents-md` (padrão),
`claude-md-and-agents-md`, `claude-md`, `managed-only`. Também configurável via
`pluginConfigs` do plugin embutido `agents-md@builtin` (ignorado em settings de projeto/local).

Diferenças: hooks `InstructionsLoaded` não disparam para um `AGENTS.md` lido pela
configuração; `--add-dir` não carrega `AGENTS.md`; `AGENTS.local.md`, `AGENTS.override.md` e
`.agents/` nunca são lidos.

Para compartilhar um arquivo entre ferramentas, prefira `@AGENTS.md` dentro de um
`CLAUDE.md` em vez de symlink (no Windows, symlink commitado vira arquivo de texto de uma
linha sem `core.symlinks`).

## Auto memory

O Claude salva quatro tipos de nota, marcados no frontmatter (`type`):

- `user` — seu papel, expertise, preferências de trabalho
- `feedback` — correções que você deu e abordagens confirmadas
- `project` — trabalho em curso, prazos, decisões não deriváveis do código
- `reference` — onde achar informação fora do projeto

Ele **pula** o que dá para derivar do codebase (arquitetura, paths, fixes) e o que o
`CLAUDE.md` já diz.

- Local: `~/.claude/projects/<projeto>/memory/`, com `MEMORY.md` (índice) e um arquivo por
  tópico. Derivado do repositório git, então todos os worktrees compartilham.
- Só as primeiras 200 linhas (ou 25 KB) de `MEMORY.md` entram no contexto; arquivos de
  tópico são lidos sob demanda.
- Configurável: `autoMemoryEnabled: false`, `autoMemoryDirectory`, env
  `CLAUDE_CODE_DISABLE_AUTO_MEMORY=1`, toggle em `/memory`.
- É local à máquina; não é sincronizada entre máquinas nem para sessões cloud.
- Excluída do sweep de retenção (`cleanupPeriodDays`) que apaga transcripts antigos.
- Subagents podem ter memória própria com o campo `memory` no frontmatter.

## Escala: organização e monorepo

- Managed `CLAUDE.md` não pode ser excluído por settings individuais; também pode ser
  embutido na chave `claudeMd` do `managed-settings.json`.
- Use managed **settings** para enforcement técnico (deny de tools, sandbox, env, login) e
  managed **CLAUDE.md** para orientação comportamental.
- `claudeMdExcludes` (globs contra caminhos absolutos) pula `CLAUDE.md` de outros times em
  monorepos; arrays somam entre camadas.

## Troubleshooting

- **"O Claude não segue meu CLAUDE.md":** o conteúdo entra como mensagem de usuário depois do
  system prompt — não há garantia de cumprimento. Cheque `/context` → **Memory files**, torne
  a instrução mais específica, elimine conflitos. Se precisa acontecer sempre, vire hook.
- **Instrução perdida após `/compact`:** `CLAUDE.md` da raiz é relido do disco e reinjetado.
  Aninhados e rules com `paths:` voltam quando o Claude lê um arquivo correspondente. O que
  só existia na conversa se perde.
- **Arquivo grande demais:** aviso no startup e em `/status`; `/doctor` propõe cortes para um
  `CLAUDE.md` commitado (remove o que é derivável do código e mantém gotchas e convenções).
- Hook `InstructionsLoaded` serve para logar quais arquivos carregaram, quando e por quê.
