# 06 — Plugins e marketplaces

Páginas oficiais cobertas:

| Página | Link |
|--------|------|
| Plugins overview | <https://code.claude.com/docs/en/plugins/overview> |
| Install and manage plugins | <https://code.claude.com/docs/en/plugins/install> |
| Anthropic's marketplaces | <https://code.claude.com/docs/en/plugins/anthropic-marketplaces> |
| Code intelligence plugins | <https://code.claude.com/docs/en/plugins/code-intelligence> |
| Plugin security and trust | <https://code.claude.com/docs/en/plugins/security> |
| Create a Claude Code plugin | <https://code.claude.com/docs/en/plugins/create> |
| Add components to a plugin | <https://code.claude.com/docs/en/plugins/components> |
| Plugin dependencies | <https://code.claude.com/docs/en/plugins/dependencies> |
| Test plugins with evals | <https://code.claude.com/docs/en/plugin-evals> |
| Publish and distribute a plugin | <https://code.claude.com/docs/en/plugins/publish> |
| Measure plugin cost and usage | <https://code.claude.com/docs/en/plugins/measure> |
| Create a marketplace | <https://code.claude.com/docs/en/plugins/create-marketplace> |
| Host and maintain a marketplace | <https://code.claude.com/docs/en/plugins/host-marketplace> |
| Manage plugins for your organization | <https://code.claude.com/docs/en/plugins/org> |
| Plugin loading reference | <https://code.claude.com/docs/en/plugins/loading> |
| Plugin manifest reference | <https://code.claude.com/docs/en/plugins/manifest-reference> |
| Marketplace reference | <https://code.claude.com/docs/en/plugins/marketplace-reference> |
| Plugin commands reference | <https://code.claude.com/docs/en/plugins/cli-reference> |
| Troubleshoot plugins | <https://code.claude.com/docs/en/plugins/troubleshooting> |

---

## O que é um plugin

Um diretório de componentes que o Claude Code instala e carrega como uma unidade, geralmente
com um manifesto em `.claude-plugin/plugin.json` (nome obrigatório; versão, descrição e
metadados opcionais). Componentes possíveis:

- **Skills** (`skills/<nome>/SKILL.md`) — namespaced como `/plugin:skill`
- **Agents** (`agents/*.md`) — subagents delegáveis
- **Hooks** (`hooks/hooks.json`) — comandos em eventos de lifecycle
- **Mod** (`modules` em `hooks/hooks.json` apontando para um `.js`/`.ts`) — código que roda
  dentro do processo do Claude Code e pode desenhar na interface. Página [16](16-mods.md)
- **MCP servers** — servidores conectados enquanto o plugin estiver ativo

## Quando você precisa de um plugin

Skills, subagents, hooks e MCP servers funcionam sozinhos. Um plugin faz sentido quando:

- você quer **vários** desses componentes empacotados como uma unidade;
- quer instalar o setup que outra pessoa construiu, com um comando e com atualizações via
  marketplace;
- precisa do mesmo setup em vários repositórios.

## O que um plugin habilitado adiciona a toda sessão

- **Contexto e uso:** nome e descrição de cada skill/agent/command que o Claude pode invocar
  sozinho entram no contexto em toda requisição.
- **Processos:** os MCP servers do plugin rodam junto de cada sessão e os hooks disparam.
- **Permissões:** o que o plugin roda, roda **como você**.

Onde medir: antes de instalar, a aba **Marketplaces** em `/plugin` mostra estimativa de
*Context cost* (plugins do marketplace oficial); depois, veja
[Measure what a plugin costs](https://code.claude.com/docs/en/plugins/measure) e o grupo
**Not used recently** na aba **Installed**. Para parar sem desinstalar: `/plugin` ou
`claude plugin disable`.

## Marketplaces

Um marketplace é um repositório ou diretório com `.claude-plugin/marketplace.json` listando
plugins e de onde buscar cada um — é um catálogo, não uma loja hospedada.

O Claude Code adiciona o marketplace oficial da Anthropic na primeira sessão interativa de
terminal, salvo política gerenciada em contrário.

Três tiers, e os nomes oficiais/community só são aceitos para marketplaces vindos de
repositórios `github.com/anthropics/`:

- **Official** — nomes oficiais, incluindo `claude-plugins-official`
- **Community** — nomes community, como `claude-community`
- **Third-party** — todo o resto, inclusive o do seu colega ou da sua empresa

Independentemente do tier, um plugin instalado pode executar código com seus privilégios.
Leia [Plugin security and trust](https://code.claude.com/docs/en/plugins/security) antes.

```
/plugin                                   # abre o gerenciador
/plugin marketplace add anthropics/claude-plugins-official
/plugin install mcp-server-dev@claude-plugins-official
/plugin uninstall <plugin>@<marketplace>
/reload-plugins [--force]                 # aplica mudanças sem reiniciar
```

## Escopos de instalação

- **User** — habilitado para você em todos os projetos da máquina
- **Project** — habilitado para todo mundo do repositório, via `.claude/settings.json`
  commitado (cada pessoa ainda instala na própria máquina)
- **Local** — só para você, só neste repositório

Terminal, sessões locais do app desktop e extensão VS Code compartilham os mesmos settings de
usuário. **Sessões cloud não carregam os plugins dos seus settings locais.**

## Três camadas para um plugin funcionar

1. **Settings** — marketplaces adicionados e plugins habilitados
2. **Disco** — `~/.claude/plugins/` guarda o que foi baixado
3. **Sessão** — plugins carregam no startup ou via `/reload-plugins`

`/plugin` mostra em qual estágio um plugin parou; a referência de loading detalha as regras
de cada camada.

## Governança organizacional

Via managed settings, uma organização pode: permitir ou bloquear marketplaces, forçar
instalação de plugins, desligar carregamento só-de-sessão e restringir customização a
plugins (`strictPluginOnlyCustomization`). Ver
[Manage plugins for your organization](https://code.claude.com/docs/en/plugins/org).

## Desenvolvimento

- Durante o desenvolvimento não é preciso marketplace: carregue o plugin localmente.
- Um diretório de skill com `.claude-plugin/plugin.json` carrega como plugin
  `<nome>@skills-dir`, podendo então empacotar agents, hooks e MCP servers. Em
  `.claude/skills/` de projeto, exige aceitar o diálogo de workspace trust.
- Subagents de plugin **ignoram** `hooks`, `mcpServers` e `permissionMode`.
- `claude plugin eval` roda suites de eval para testar o plugin (JSON/report, sandbox, CI);
  `/skill-doctor` mostra custo de contexto e taxa de disparo das skills.
- `claude plugin validate <dir>` lê manifesto e hooks module sem executar; as linhas `hooks:` e
  `calls:` listam os eventos e as chamadas da mods API de um mod — é a revisão de segurança
  antes de instalar um plugin de terceiros. `claude plugin test` roda os `*.test.ts` de um mod.
- Um plugin instalado de GitHub, git, URL ou npm é **copiado para o cache** e conta como do
  usuário mesmo com `enabledPlugins` managed; só um marketplace em diretório local, listado
  por caminho relativo, carrega *in place* e conta como da organização (importa para mods,
  [16 § Managed settings](16-mods.md#managed-settings)).
- Variáveis úteis dentro de um plugin: `${CLAUDE_PLUGIN_ROOT}` (diretório de instalação) e
  `${CLAUDE_PLUGIN_DATA}` (diretório persistente que sobrevive a updates).
