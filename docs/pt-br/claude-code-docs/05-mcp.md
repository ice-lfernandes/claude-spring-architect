# 05 — MCP (Model Context Protocol)

Páginas oficiais cobertas:

| Página | Link |
|--------|------|
| Connect to MCP servers (quickstart) | <https://code.claude.com/docs/en/mcp-quickstart> |
| Connect Claude Code to tools via MCP (referência) | <https://code.claude.com/docs/en/mcp> |
| Control MCP server access for your organization | <https://code.claude.com/docs/en/managed-mcp> |
| Connect to external tools with MCP (SDK) | <https://code.claude.com/docs/en/agent-sdk/mcp> |
| Push events into a running session with channels | <https://code.claude.com/docs/en/channels> |

---

## O papel do MCP no ecossistema

MCP é um padrão aberto para conectar o Claude a ferramentas e dados externos: bancos,
issue trackers, browsers, APIs internas. Conecte um servidor quando você se pegar copiando
dados de outra ferramenta para o chat.

Contraste com skill: **MCP fornece a conexão e as tools; a skill ensina a usá-las bem.**
Os dois se combinam (MCP conecta ao banco, skill documenta o schema e os padrões de query).

> Verifique que confia em cada servidor antes de conectar. Servidores que buscam conteúdo
> externo expõem você a prompt injection.

## Instalar servidores

```bash
# HTTP remoto (recomendado)
claude mcp add --transport http notion https://mcp.notion.com/mcp
claude mcp add --transport http secure-api https://api.example.com/mcp --header "Authorization: Bearer token"

# SSE (deprecado; o HTTP cai para SSE sozinho na v2.1.265+)
claude mcp add --transport sse asana https://mcp.asana.com/sse

# stdio local — tudo depois de -- vai para o servidor
claude mcp add --env AIRTABLE_API_KEY=KEY --transport stdio airtable -- npx -y airtable-mcp-server

# WebSocket (só via JSON)
claude mcp add-json events '{"type":"ws","url":"wss://mcp.example.com/socket","headers":{"Authorization":"Bearer T"}}'
```

Notas de configuração:

- Em JSON, `type` aceita `streamable-http` como alias de `http`.
- Uma entrada com `url` e **sem** `type` é erro: o Claude Code interpreta como stdio e pula o
  servidor.
- `"type": "sdk"` só pode ser registrado por uma aplicação host do SDK.
- Servidores stdio recebem `CLAUDE_PROJECT_DIR` no ambiente. Para limitar acesso a
  diretórios, implemente `roots/list` (o Claude Code responde com o diretório de lançamento
  mais os adicionados e envia `notifications/roots/list_changed`).
- Para adaptar instruções escritas para outro cliente: URL → `--transport http`; comando
  `npx`/`uvx` → stdio com `--`; bloco `mcpServers` → `claude mcp add-json` com o objeto de
  dentro do wrapper.

Gerenciamento: `claude mcp list`, `claude mcp get <nome>`, `claude mcp remove <nome>`, e
`/mcp` dentro da sessão. Remover um servidor remoto também apaga os tokens OAuth guardados.

## Escopos e precedência

| Escopo | Carrega em | Compartilhado | Guardado em |
|--------|-----------|---------------|-------------|
| Local (padrão) | Só o projeto atual | Não | `~/.claude.json` |
| Project | Só o projeto atual | Sim, via git | `.mcp.json` na raiz |
| User | Todos os seus projetos | Não | `~/.claude.json` |

Precedência (do maior para o menor): managed (`managedMcpServers`) > local > project > user >
servidores de plugin > connectors da claude.ai. Duplicatas entre escopos são casadas por
**nome**; plugins e connectors, por **endpoint**.

Servidores de `.mcp.json` exigem aprovação interativa antes do uso. Em `claude -p`, no Agent
SDK e em sessões cloud não há prompt: eles carregam sem aprovação. Para restringir:
`disabledMcpjsonServers`, `--setting-sources` sem `project`, ou `--strict-mcp-config`
(só usa o que vier de `--mcp-config`).

Aprovações commitadas no repositório (`enableAllProjectMcpServers`, `enabledMcpjsonServers`)
só valem depois que você confia no workspace.

## Status e diagnóstico

`claude mcp list` mostra `✔ Connected`, `! Needs authentication`, `✘ Failed to connect`,
`⏸ Pending approval`, `✘ Rejected` ou `⊘ Disabled for this project`. Servidores WebSocket não
aparecem nessa lista — use `claude mcp get` ou `/mcp`.

Avisos de configuração que o Claude Code emite:

- **Whitespace escondido** em `command`, `url`, `args`, `env` ou `headers` (típico de token
  colado com newline). Ele não corta — você precisa editar.
- **Mesmo nome em mais de um escopo** com endpoints diferentes (logins OAuth são por
  endpoint).
- **Nomes reservados**: `workspace`, `claude-in-chrome`, `computer-use`, `Claude Preview`,
  `Claude Browser`.
- **Variável de ambiente ausente**: `${VAR}` sem valor e sem `:-default` carrega literal.

Cache de discovery (`MCP_DISCOVERY_CACHE=1`) permite status `cached ... connects on first use`,
conectando só na primeira chamada de tool.

## Variáveis de ambiente em `.mcp.json`

`${VAR}` e `${VAR:-default}` são expandidos, permitindo commitar a configuração sem segredos:

```json
{
  "mcpServers": {
    "notion": {
      "command": "npx",
      "args": ["-y", "@notionhq/notion-mcp-server"],
      "env": { "NOTION_TOKEN": "${NOTION_TOKEN}" }
    }
  }
}
```

Gotcha: uma variável não definida **não** falha a geração — o servidor carrega com o texto
`${VAR}` literal e falha só na conexão (com aviso em `claude mcp list` e `/mcp`).

## Tool search (contexto)

Por padrão, definições de tools MCP são **deferidas**: só nomes de tools e as instruções do
servidor carregam no início da sessão; os schemas completos entram sob demanda. Isso faz o
custo de adicionar servidores ficar quase plano.

- Requer modelo com suporte a `tool_reference` (Sonnet 4.5, Haiku 4.5, Opus 4.5 e posteriores).
- Desligado automaticamente quando `ANTHROPIC_BASE_URL` aponta para host não-first-party.
- Controlado por `ENABLE_TOOL_SEARCH`; `CLAUDE_CODE_DISABLE_EXPERIMENTAL_BETAS` mantém off.
- Descrições de tool e instruções de servidor são truncadas em 2.048 caracteres
  (`CLAUDE_CODE_MAX_MCP_DESCRIPTION_LENGTH` ajusta). Ponha o essencial no começo.

Para autores de servidor: as **server instructions** ficam mais importantes com tool search —
explique a categoria de tarefas, quando procurar suas tools e as capacidades principais.

## Permissões e nomes de tools

Tools MCP têm nome `mcp__<servidor>__<tool>` (ex.: `mcp__github__search_repositories`).
Servidores vindos de plugin usam segmento escopado: `mcp__plugin_my-plugin_db__query`.

- Allow: `mcp__puppeteer__*` ou `mcp__github__get_*` — o segmento do servidor não pode ter
  glob. Globs não ancorados (`"*"`, `"mcp__*"`) em allow são ignorados com aviso.
- Deny/ask: `mcp__*` bloqueia todas as tools MCP.
- Regras `mcp__` com parênteses em arquivos de settings são puladas (use
  `--disallowedTools` para casar parâmetro em tool MCP).
- Tools marcadas `requiresUserInteraction` sempre pedem aprovação, mesmo com hook `allow`.

## Autenticação

OAuth para servidores remotos (`/mcp` conduz o fluxo; `claude mcp add` aceita headers
estáticos). Recursos disponíveis: porta fixa de callback, credenciais OAuth
pré-configuradas, override da descoberta de metadata, restrição de scopes e
`headersHelper` para gerar headers dinamicamente no momento da conexão.

## Outros recursos

- **Channels**: um MCP server pode empurrar mensagens para dentro da sessão (Telegram,
  Discord, webhooks), fazendo o Claude reagir a eventos externos.
- **Recursos MCP**: `ListMcpResourcesTool` / `ReadMcpResourceTool`, e referência por `@`.
- **Elicitation**: o servidor pede input do usuário durante uma tool call (eventos de hook
  `Elicitation` e `ElicitationResult`).
- **Claude Code como MCP server**: expõe as capacidades dele para outros clientes.
- **Connectors da claude.ai**: entram na sessão com controles de organização
  (`disableClaudeAiConnectors` é honrado como `true` de qualquer escopo).
- **Limites de saída**: saídas grandes são truncadas com aviso; dá para elevar o limite por
  tool.
- **Backgrounding automático** de tool calls longas.
