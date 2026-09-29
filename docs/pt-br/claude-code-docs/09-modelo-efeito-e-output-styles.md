# 09 — Modelo, effort e estilo de resposta

Páginas oficiais cobertas:

| Página | Link |
|--------|------|
| Model configuration | <https://code.claude.com/docs/en/model-config> |
| Speed up responses with fast mode | <https://code.claude.com/docs/en/fast-mode> |
| Escalate hard decisions with the advisor tool | <https://code.claude.com/docs/en/advisor> |
| Output styles | <https://code.claude.com/docs/en/output-styles> |
| How Claude Code uses prompt caching | <https://code.claude.com/docs/en/prompt-caching> |

---

## Aliases de modelo

| Alias | Comportamento |
|-------|---------------|
| `default` | Limpa qualquer override e volta ao padrão da conta |
| `best` | O modelo do alias `fable` quando disponível; senão, o mesmo que `opus` |
| `fable` | Modelo Fable do provedor, para tarefas mais difíceis e longas |
| `sonnet` | Último Sonnet, para codificação do dia a dia |
| `opus` | Último Opus, para raciocínio complexo |
| `haiku` | Rápido e barato, para tarefas simples |
| `sonnet[1m]` / `opus[1m]` | Janela de contexto de 1 milhão de tokens |
| `opusplan` | Opus durante plan mode, Sonnet na execução |

Aliases apontam para a versão recomendada **por provedor** e mudam com o tempo (na Anthropic
API, `opus` → Opus 5.5 e `sonnet` → Sonnet 5 no snapshot desta documentação). Para fixar,
use o nome completo (`claude-opus-5-5`).

`ANTHROPIC_BASE_URL` muda **para onde** as requisições vão, não qual modelo responde.

Configuração: `/model` (salva como padrão; `s` aplica só à sessão), `--model`,
chave `model` nos settings, `ANTHROPIC_MODEL`. Cada modelo tem seu próprio prompt cache —
trocar de modelo relê a conversa inteira sem cache.

Outros recursos: `availableModels` (allowlist organizacional), `modelPicker`,
`modelOverrides`, cadeias de `fallbackModel` e fallback automático em sobrecarga,
IDs de modelo customizados, e ajuste da janela de auto-compactação
(`/autocompact`, `--autocompact`).

## Effort level

Controla raciocínio adaptativo: quanto o modelo pensa antes de agir.

| Modelo | Níveis |
|--------|--------|
| Fable 5.1 e Fable 5 | `low`, `medium`, `high`, `xhigh`, `max` |
| Opus 5.5, Opus 5, Sonnet 5, Opus 4.8, Opus 4.7 | `low`, `medium`, `high`, `xhigh`, `max` |
| Opus 4.6, Sonnet 4.6 | `low`, `medium`, `high`, `max` |

Se você pedir um nível não suportado, o Claude Code cai para o maior nível suportado abaixo
dele. Ordem de resolução: escolha explícita (`CLAUDE_CODE_EFFORT_LEVEL`, `--effort`,
`/effort`) → seus settings (`modelSettings`/`effortLevel`) → default do modelo (`high` na
maioria; `medium` no Opus 5.5; `xhigh` no Opus 4.7).

No `/effort` e no `/model`: `Enter` salva como padrão do modelo, `s` aplica só à sessão.
`max` vale só para a sessão, a menos que venha da variável de ambiente.

**Ultracode** não é um nível de modelo: é um setting que envia `xhigh` e adiciona
comportamento próprio do Claude Code. Liga por `/effort ultracode`, `--effort ultracode`,
`"ultracode": true`, ou pelo slider do `/model`.

## Extended thinking

O raciocínio que o modelo emite antes de responder. Em modelos com raciocínio adaptativo, o
effort level é o controle principal.

| Controle | Como |
|----------|------|
| Alternar na sessão | `Option+T` (macOS) / `Alt+T` (Windows, Linux) |
| Padrão global | `/config` → thinking mode (`alwaysThinkingEnabled`) |
| Desligar por env | `MAX_THINKING_TOKENS=0` |

Não é possível desligar thinking no Opus 5.5 nem nos modelos Fable. A saída é recolhida por
padrão; `Ctrl+O` mostra o raciocínio em itálico cinza.

## Fast mode e advisor

- **Fast mode** (`/fast`, `--fast` conforme disponibilidade) acelera a saída do Claude Opus
  sem trocar por um modelo menor. Invalida o prompt cache ao ligar.
- **Advisor tool** (`/advisor <modelo|off>`, `--advisor`) consulta um segundo modelo em
  decisões difíceis.

## Output styles

Um output style define **papel, tom e formato** de resposta para toda a sessão. É instrução,
não garantia.

| Estilo | O que muda | Use quando |
|--------|------------|-----------|
| Default | Nenhuma instrução extra; prompt padrão de engenharia | Caso geral |
| Proactive | Começa a trabalhar de imediato e assume decisões de rotina | Quer menos perguntas sobre trivialidades |
| Concise | Respostas lideram com o resultado, sem preâmbulo nem recap | Respostas padrão longas demais |
| Explanatory | Adiciona blocos `Insight` explicando as escolhas | Aprendendo o codebase |
| Learning | `Insight` + deixa trechos `TODO(human)` para você escrever | Quer praticar enquanto a tarefa anda |

Observações:

- Proactive **não** muda o permission mode: prompts de permissão continuam conforme o modo.
- Concise mantém texto completo onde a segurança exige: relatórios de erro, saída de teste
  falhando, avisos de segurança e confirmações destrutivas.
- Trocar de estilo vale a partir da próxima mensagem.

Como trocar: `/output-style <estilo>`, `/config` → **Output style**, menu da extensão VS
Code, ou a chave `outputStyle` num arquivo de settings (**case-sensitive**: `Proactive`,
`Concise`, `Explanatory`, `Learning`). Em `~/.claude/settings.json` vira padrão global.

Estilo customizado é um Markdown com frontmatter de metadados e as instruções logo abaixo,
guardado em `output-styles/` (também distribuível por plugin).

### Output style vs. outras features

| Precisa | Use |
|---------|-----|
| O que o Claude deve **saber** do projeto | `CLAUDE.md` |
| **Como** ele responde (tom, tamanho, formato, papel) | Output style |
| Algo que acontece **sempre** (formatar, bloquear) | Hook |
| Conhecimento ou procedimento sob demanda | Skill |

`CLAUDE.md` e output style se combinam: o primeiro continua carregado qualquer que seja o
estilo, e o Claude segue ambos como instruções — nenhum dos dois é enforcement.
