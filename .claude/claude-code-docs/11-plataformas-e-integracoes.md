# 11 — Plataformas e integrações

Páginas oficiais cobertas:

| Página | Link |
|--------|------|
| Platforms and integrations | <https://code.claude.com/docs/en/platforms> |
| Use Claude Code in VS Code | <https://code.claude.com/docs/en/vs-code> |
| JetBrains IDEs | <https://code.claude.com/docs/en/jetbrains> |
| Get started with the desktop app | <https://code.claude.com/docs/en/desktop-quickstart> |
| Desktop application | <https://code.claude.com/docs/en/desktop> |
| Claude Desktop on Linux (beta) | <https://code.claude.com/docs/en/desktop-linux> |
| Claude Code Desktop in WSL | <https://code.claude.com/docs/en/desktop-wsl> |
| Schedule recurring tasks in Desktop | <https://code.claude.com/docs/en/desktop-scheduled-tasks> |
| Test iOS apps in the simulator | <https://code.claude.com/docs/en/desktop-ios-simulator> |
| Get started with Claude Code in the cloud | <https://code.claude.com/docs/en/web-quickstart> |
| Use Claude Code in the cloud | <https://code.claude.com/docs/en/claude-code-on-the-web> |
| Continue local sessions with Remote Control | <https://code.claude.com/docs/en/remote-control> |
| Claude Code on mobile | <https://code.claude.com/docs/en/mobile> |
| Let Claude coordinate ongoing work with Projects | <https://code.claude.com/docs/en/claude-projects> |
| Use Claude Code with Chrome | <https://code.claude.com/docs/en/chrome> |
| Let Claude use your computer from the CLI | <https://code.claude.com/docs/en/computer-use> |
| Claude Code in Slack | <https://code.claude.com/docs/en/slack> |
| Claude Tag | <https://code.claude.com/docs/en/claude-tag> |
| Code Review | <https://code.claude.com/docs/en/code-review> |
| Find bugs with ultrareview | <https://code.claude.com/docs/en/ultrareview> |
| Claude Code GitHub Actions | <https://code.claude.com/docs/en/github-actions> |
| GitHub Actions com cloud providers | <https://code.claude.com/docs/en/github-actions-cloud-providers> |
| GitHub Enterprise Server | <https://code.claude.com/docs/en/github-enterprise-server> |
| Claude Code GitLab CI/CD | <https://code.claude.com/docs/en/gitlab-ci-cd> |
| Catch security issues as Claude writes code | <https://code.claude.com/docs/en/security-guidance> |
| Scan your codebase for vulnerabilities | <https://code.claude.com/docs/en/claude-security> |
| Configure cloud environments | <https://code.claude.com/docs/en/cloud-environments> |

---

## Regra geral

Todas as superfícies compartilham o mesmo motor: `CLAUDE.md`, settings e MCP servers do
repositório valem em todas. O que muda é **onde o código roda** e **como você interage**.

## Superfícies

| Superfície | Característica |
|------------|----------------|
| **Terminal (CLI)** | Completa; base de tudo. Suporta provedores third-party |
| **VS Code** | Diffs inline, @-mentions, revisão de plano, histórico de conversas no editor |
| **JetBrains** | IntelliJ, PyCharm, WebStorm etc.; visualização de diff e compartilhamento de seleção. Exige a CLI instalada |
| **Desktop (macOS/Windows/Linux beta)** | Sessões paralelas com isolamento git, layout de painéis, terminal e editor integrados, side chats, computer use, tarefas agendadas locais, simulador iOS |
| **Web (claude.ai/code)** | Sem setup local; tarefas longas, repos que você não tem localmente, sessões em paralelo |
| **Mobile (iOS/Android)** | Iniciar, monitorar e dirigir tarefas pelo celular |

Movimentação entre superfícies:

- `claude --cloud` cria sessão na nuvem; `claude --teleport` puxa uma sessão cloud para o
  terminal local (exige assinatura claude.ai).
- `/desktop` continua a sessão atual no app desktop (macOS e Windows x64).
- **Remote Control** mantém execução e arquivos locais, mas você controla pelo browser ou
  celular.
- **Dispatch** cria uma sessão desktop a partir de uma mensagem do celular.

## Cloud

- **Web quickstart:** conecte um repositório GitHub, envie a tarefa, revise o PR.
- **Cloud environments:** definem o que carrega numa sessão cloud. Regra crítica: sessões
  cloud **não** leem `~/.claude/settings.json`, `.claude/settings.local.json`, skills
  pessoais em `~/.claude/skills/`, nem plugins habilitados só nos seus settings locais.
  Leem o `.claude/settings.json` commitado (em sessão de um repositório) e server-managed
  settings.
- **Projects:** uma conversa contínua onde o Claude coordena sessões paralelas (threads) que
  compartilham repositórios, instruções e memória.
- **Routines:** rodam na nuvem em agenda, por chamada de API ou reagindo a eventos do GitHub.
- **Ultrareview:** revisão multi-agente profunda em sandbox na nuvem
  (`/code-review ultra`, `/ultrareview` é alias depreciado). É acionada pelo usuário e
  cobrada; precisa de repositório git.

## Browser e computador

- **Chrome:** testar web apps, depurar com console logs, automatizar preenchimento de
  formulários e extrair dados (`--chrome` / `/chrome`).
- **Computer use (macOS):** o Claude abre apps, clica, digita e vê a tela — para testar apps
  nativos, depurar problemas visuais e automatizar GUI.

## Chat

- **Claude Code in Slack:** delega tarefas do workspace. A Anthropic está aposentando essa
  versão para workspaces Team e Enterprise em favor do **Claude Tag**; segue como caminho de
  setup no Pro.
- **Claude Tag:** traz o Claude para canais do Slack (`/install-slack-app`).

## Revisão de código e CI/CD

| Ferramenta | Uso |
|-----------|-----|
| `/code-review` (`/review`) | Revisão do diff local em subagent isolado; aceita PR, branch ou path, `--fix`, `--comment` |
| Code Review (GitHub) | Revisão automática de PR com análise multi-agente do codebase |
| `security-guidance` (plugin) | O Claude revisa as próprias mudanças em busca de vulnerabilidades e corrige na mesma sessão |
| Claude Security (plugin) | Varre o codebase e transforma achados em patches para revisão |
| `/security-review` | Analisa as mudanças do branch atual |
| GitHub Actions | Responde a menções `@claude`, automatiza tarefas, converte issues em PRs |
| GitLab CI/CD | Integração equivalente no GitLab |

GitHub Actions pode rodar via Amazon Bedrock, Google Cloud Agent Platform ou Microsoft
Foundry em vez da Claude API, e há caminho documentado para GitHub Enterprise Server
(sessões cloud, code review e marketplaces de plugin).

## Dev containers

`devcontainer` documentado para ambientes isolados e consistentes entre o time
(<https://code.claude.com/docs/en/devcontainer>).
