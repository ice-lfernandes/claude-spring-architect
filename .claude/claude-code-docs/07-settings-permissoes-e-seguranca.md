# 07 — Settings, permissões, sandbox e segurança

Páginas oficiais cobertas:

| Página | Link |
|--------|------|
| Settings files and precedence | <https://code.claude.com/docs/en/settings> |
| All settings | <https://code.claude.com/docs/en/settings-reference> |
| Example settings files | <https://code.claude.com/docs/en/settings-example> |
| Configure permissions | <https://code.claude.com/docs/en/permissions> |
| Choose a permission mode | <https://code.claude.com/docs/en/permission-modes> |
| Configure the sandboxed Bash tool | <https://code.claude.com/docs/en/sandboxing> |
| Choose a sandbox environment | <https://code.claude.com/docs/en/sandbox-environments> |
| Environment variables | <https://code.claude.com/docs/en/env-vars> |
| Security | <https://code.claude.com/docs/en/security> |
| Catch security issues as Claude writes code | <https://code.claude.com/docs/en/security-guidance> |
| Scan your codebase for vulnerabilities | <https://code.claude.com/docs/en/claude-security> |
| Configure auto mode | <https://code.claude.com/docs/en/auto-mode-config> |

---

## Arquivos de settings

| Escopo | Arquivo | Afeta | Use para |
|--------|---------|-------|----------|
| User | `~/.claude/settings.json` | Você, todos os projetos da máquina | Preferências pessoais, modelo padrão, suas regras |
| Shared project | `.claude/settings.json` | Todos no projeto (commitado) | Permissões, hooks, plugins, env do time |
| Project local | `.claude/settings.local.json` | Só você, só neste projeto | Overrides pessoais, testes antes de compartilhar |
| Managed | `managed-settings.json`, MDM, console claude.ai | Todos da organização | Política de segurança e compliance |

Um quinto arquivo, `~/.claude.json`, é escrito pelo próprio Claude Code (login, MCP,
estado por projeto, chaves de global config) e não deve ser editado à mão.

Instalar o Claude Code **não** cria arquivo de settings. O Claude Code cria
`~/.claude/settings.json` na primeira mudança via `/config`, e
`.claude/settings.local.json` na primeira aprovação "Yes, and don't ask again" — adicionando
`**/.claude/settings.local.json` ao arquivo global de excludes do git.

Settings são JSON estrito: `//` e vírgula final são erro de sintaxe. Adicione
`"$schema": "https://json.schemastore.org/claude-code-settings.json"` para autocomplete.

### Precedência

1. **Managed settings** (nada seu sobrescreve, salvo exceções abaixo)
2. **Argumentos de linha de comando** (`--settings`, flags de uma sessão)
3. **Project local** (`.claude/settings.local.json`)
4. **Shared project** (`.claude/settings.json`)
5. **User** (`~/.claude/settings.json`)

Regras derivadas:

- Listas (`permissions.allow`, etc.) **somam** entre camadas em vez de substituir. Exceções:
  `fallbackModel`, `modelPicker`, `availableModels` e `modelSettings` têm regras próprias.
- Variáveis de ambiente não são um nível da pilha: a relação é decidida par a par
  (`ANTHROPIC_MODEL` vence a chave `model` de qualquer arquivo; `ANTHROPIC_DEFAULT_MODEL`
  só vale se nenhum arquivo definir `model`).
- Algumas chaves **nunca** valem a partir de um arquivo commitado (escopo "User, local, or
  managed", "Managed" ou "Global config" na referência), e outras esperam pelo workspace
  trust (`permissions.allow`, `permissions.additionalDirectories`, `extraKnownMarketplaces`,
  a maior parte de `env`). `deny` e `ask` valem imediatamente.
- `permissions.defaultMode` com valores `auto` e `bypassPermissions` não pega a partir de
  settings de projeto ou local.

### Exceções: valor mais restritivo vence o managed

`disableClaudeAiConnectors: true`, `enableArtifact: false`/`disableArtifact: true`,
`isolatePeerMachines: true`, `remoteControlAtStartup: false` (projeto/local),
`crossSessionInbound` mais estrito, `useAutoModeDuringPlan: false`,
`syncClaudeAiSkills: false`, `syncClaudeAiPlugins: false`, `maxEffortLevel` mais baixo.

### Quando as edições valem

O Claude Code observa os arquivos e recarrega a maioria das mudanças na sessão em curso
(inclusive `permissions`, `hooks`, `apiKeyHelper`), disparando `ConfigChange`. Lidos só no
início: `model` (use `/model`), `effortLevel` e `modelSettings` (use `/effort`), entre
chaves administrativas como `requiredMinimumVersion`.

Verificação: `/status` → linha `Setting sources`; `claude doctor` lista entradas rejeitadas.

### Sessões cloud

Leem o `.claude/settings.json` commitado (em sessão de um repositório só) e apenas
server-managed settings. **Não** leem `~/.claude/settings.json` nem
`.claude/settings.local.json`.

## Sistema de permissões

| Tipo de tool | Aprovação no Manual mode | "Don't ask again" |
|--------------|--------------------------|-------------------|
| Só leitura (Read, Grep) | Não, dentro dos working directories | — |
| Comandos Bash | Sim, exceto conjunto embutido read-only | Permanente por repo e comando |
| Modificação de arquivo | Sim | Até o fim da sessão |
| WebFetch | Sim, exceto domínios de documentação pré-aprovados | Permanente por repo e domínio |
| WebSearch | Sim | Permanente por repositório |

`/permissions` lista todas as regras e o arquivo de origem de cada uma.

**Ordem de avaliação: deny → ask → allow.** A primeira que casa decide; especificidade não
muda a ordem. Um `deny` amplo (`Bash(aws *)`) bloqueia mesmo com allow mais específico —
allow não abre exceção em deny.

Deny por **nome puro** (`Bash`) remove a tool do contexto do Claude; deny com escopo
(`Bash(rm *)`) mantém a tool e bloqueia as chamadas que casam. `EndConversation` é a exceção:
não pode ser removida enquanto qualquer outra tool existir.

> Permissões são aplicadas pelo Claude Code, não pelo modelo. `CLAUDE.md` molda o que ele
> tenta; permissões decidem o que ele consegue.

### Sintaxe de regras

- `Tool` — todos os usos. `Bash(*)` equivale a `Bash`.
- `Tool(especificador)` — `Bash(npm run build)`, `Read(./.env)`,
  `WebFetch(domain:example.com)`.
- `Tool(param:valor)` em deny/ask — casa um parâmetro de topo:
  `Agent(model:opus)`, `Agent(isolation:worktree)`, `Bash(run_in_background:true)`.
  Não funciona no campo principal (`command`, `file_path`, `url`...).
- Globs no nome da tool em deny/ask: `"*"`, `"mcp__*"`. Em allow, só depois do prefixo
  literal `mcp__<servidor>__`.

### Wildcards em Bash

Ponha o `*` **depois do subcomando**: `Bash(git log *)` libera só `git log`, `Bash(git *)`
libera tudo do git (inclusive `-c`, que faz o git rodar um programa que você nomeia). O
Claude Code avisa no startup sobre allow com `*` antes do subcomando.

| Você escreve | Casa | Não casa |
|--------------|------|----------|
| `Bash(npm run build)` | `npm run build` | `npm run build --watch` |
| `Bash(npm run *)` | `npm run build`, `npm run` | `npm install` |
| `Bash(ls *)` | `ls -la`, `ls` | `lsof` |
| `Bash(ls*)` | `ls -la`, `lsof` | — |

`Bash(ls:*)` é forma equivalente de `Bash(ls *)`, reconhecida só no fim do padrão.

### Comandos compostos, wrappers e limites

- O Claude Code entende operadores de shell: `Bash(safe-cmd *)` não autoriza
  `safe-cmd && other-cmd`. Deny/ask se aplicam a qualquer subcomando, inclusive em subshells,
  substituições e corpos de `for`.
- Wrappers fixos são removidos antes do matching (`timeout`, `xargs` sem flags, atribuições
  de env conhecidas como `NODE_ENV=test`). Runners de ambiente (`direnv exec`, `devbox run`,
  `mise exec`, `npx`, `docker exec`) **não** são removidos; wrappers de exec como `watch`,
  `setsid`, `flock` não podem ser auto-aprovados por regra de prefixo.
- O que uma regra Bash **não** pega: caminho absoluto (`/usr/bin/curl`), invocação via
  `sh -c '...'`, e variações como `git -C . push` ou `git 'push'`. Para enforcement que não
  dependa do texto do comando, use **sandboxing**; para inspecionar o comando com sua lógica,
  use um hook `PreToolUse`.
- Redirecionamentos (`>`, `>>`, `<`, `tee`) são checados contra suas regras de arquivo.

> Padrões Bash que tentam restringir argumentos são frágeis: `Bash(curl http://github.com/ *)`
> é contornável por flag antes da URL, outro protocolo, redirect ou variável. Prefira negar
> `curl`/`wget` e usar `WebFetch(domain:...)`, ou um hook.

### Read e Edit

Usam sintaxe de padrão do gitignore, com quatro tipos de âncora:

| Padrão | Significado |
|--------|-------------|
| `//path` | Absoluto a partir da raiz do filesystem |
| `~/path` | A partir do home |
| `/path` | Relativo à **fonte do setting** (não à raiz do filesystem) |
| `path` ou `./path` | Relativo ao diretório atual |

`Edit` vale para todas as tools embutidas que editam arquivos; `Read` deny também bloqueia
Edit e Write no mesmo caminho, e se aplica a comandos de arquivo reconhecidos no Bash
(`cat`, `head`, `tail`, `sed`, `tee`). Regras de caminho só são checadas contra `Edit(...)` e
`Read(...)` — escrevê-las para `Write`, `Glob` ou `NotebookEdit` não funciona.

No Windows, caminhos são normalizados para POSIX (`C:\Users\alice` → `/c/Users/alice`).

## Permission modes

| Modo | Roda sem perguntar | Melhor para |
|------|--------------------|-------------|
| `default` (Manual) | Só leituras | Revisar cada ação, trabalho sensível |
| `acceptEdits` | Leituras, edições e comandos comuns de filesystem | Iterar em código que você está revisando |
| `plan` | Leituras (+ comandos aprovados pelo classificador, com auto mode) | Explorar antes de mudar |
| `auto` | Tudo, com checagens de segurança em background | Tarefas longas, menos fadiga de prompt |
| `dontAsk` | Leituras e tools pré-aprovadas; o resto é **negado** | CI e scripts travados |
| `bypassPermissions` | Tudo, exceto o que nenhum modo auto-aprova | Só em container/VM isolada |

`Shift+Tab` alterna. `permissions.disableBypassPermissionsMode` e
`permissions.disableAutoMode` (valor `"disable"`) travam modos — úteis em managed settings.

Auto mode usa um classificador server-side que bloqueia escalada de escopo, infraestrutura
desconhecida e ações induzidas por conteúdo hostil; `/auto-mode-setup` e
`autoMode.environment` ajustam o comportamento, e `/permissions` tem aba **Auto mode**.

Caminhos protegidos (`.git`, `.claude` e outros) e caminhos críticos têm tratamento especial
por modo — ver [Protected paths](https://code.claude.com/docs/en/permission-modes#protected-paths).

## Working directories

O diretório de lançamento é o primário. `--add-dir` / `/add-dir` adicionam outros e também
carregam `.claude/skills/`, `.claude/commands/` e `.claude/agents/` daquele diretório — já a
configuração `permissions.additionalDirectories` concede **apenas acesso a arquivos**, sem
carregar configuração. `/cd` move a sessão.

## Sandboxing

Isolamento em nível de sistema operacional para a tool Bash: restringe filesystem e rede,
permitindo que o Claude trabalhe mais livremente dentro de limites. Diferente de permissões,
não depende do texto do comando.

Pontos da documentação: modos de sandbox, desativação do isolamento de filesystem, proteção e
mascaramento de credenciais, isolamento de rede (com prompt de aprovação para requisições),
enforcement no nível do SO, proxy customizado, e como o sandbox se relaciona com regras e
modos de permissão. Organizações podem impor sandbox via managed settings e impedir que
desenvolvedores afrouxem a política. Há limitações de segurança e de compatibilidade de
plataforma documentadas — leia antes de confiar nele como fronteira dura.

## Segurança

- **Arquitetura baseada em permissão**: acesso de escrita restrito ao diretório do projeto e
  subdiretórios por padrão; operações que alteram estado exigem aprovação.
- **Prompt injection** é o risco central: conteúdo externo (páginas, issues, saídas de tool)
  pode tentar redirecionar o agente. Proteções incluem revisão de permissões, isolamento de
  contexto e allowlist de domínios; a responsabilidade final de revisar comandos propostos é
  do usuário.
- **MCP**: confie no servidor antes de conectar; servidores que buscam conteúdo externo
  aumentam a superfície de injection.
- **Trabalho com código sensível**: use deny rules em arquivos de credencial
  (`Read(./.env)`, `Read(./secrets/**)`), sandbox e, para times, managed settings.
- `/security-review` analisa as mudanças do branch em busca de vulnerabilidades; o plugin
  `security-guidance` roda revisão por modelo separado e devolve achados à sessão;
  ferramentas de varredura de codebase estão em
  [claude-security](https://code.claude.com/docs/en/claude-security).
- Reporte vulnerabilidades pelos canais indicados em
  [Security](https://code.claude.com/docs/en/security#reporting-security-issues).

## Exemplo de arquivo de settings

```json
{
  "$schema": "https://json.schemastore.org/claude-code-settings.json",
  "permissions": {
    "allow": ["Bash(npm run lint)", "Bash(npm run test *)"],
    "deny": ["Read(./.env)", "Read(./.env.*)", "Bash(git push *)"]
  },
  "hooks": {
    "PostToolUse": [
      { "matcher": "Edit|Write",
        "hooks": [{ "type": "command", "command": "jq -r '.tool_input.file_path' | xargs npx prettier --write" }] }
    ]
  }
}
```
