# Diferenciais — o que este repositório faz que os vizinhos não fazem

Fonte primária: o próprio `.claude/` (`hooks/ArchHook.java`, `blueprints/*.yaml`,
`skills/*/SKILL.md`, `schemas/extensions.json`), `.github/workflows/validate.yml` e
`CLAUDE.md` § Invariants. A comparação com outros projetos foi feita em 2026-09-24 sobre
os READMEs públicos deles; os links estão no fim.

## Como os projetos vizinhos se organizam

Existem dezenas de repositórios "Claude Code + Spring Boot" no GitHub. Quase todos caem
em uma de três famílias:

| Família | O que entrega | Exemplos |
|---|---|---|
| **Template estático** | Um repositório com `pom.xml`, versão de Spring fixada e um `.claude/` com skills e agents. Clona-se e adapta-se. A arquitetura é a que veio no template | `piomin/claude-ai-spring-boot` e seus forks; `ryu-qqq/claude-spring-standards` (hexagonal fixo, 15 skills, 12 commands, 5 hooks) |
| **Pacote de skills** | Arquivos `SKILL.md` de conhecimento (Spring, JPA, Security, WebFlux) para copiar em `.claude/skills/`. Não geram projeto nem verificam nada | `rrezartprebreza/spring-boot-skills`, `a-pavithraa/springboot-skills-marketplace` |
| **Bundle de agents + hooks** | Agents por papel (backend, reviewer, security, devops…) mais hooks genéricos: formatar ao salvar, bloquear `rm -rf` | `altmemy/claude-code-templates/claude-spring-boot` (7 agents) |

O traço comum: **a arquitetura é prosa**. Ela vive num `CLAUDE.md` ou numa skill, e o
que garante que o modelo a respeite é o modelo lembrar. Quando há hook, ele formata ou
bloqueia comando perigoso — nunca lê a arquitetura declarada. As versões estão fixadas
no `pom.xml` do template. Nenhum deles gera um projeto; nenhum mede o que cada skill
custou.

## Os diferenciais, do maior para o menor

### 1 · Gerador, não template

`/init-project` não clona nada. Ele chama o Spring Initializr em tempo de execução
(`curl start.spring.io/starter.tgz`), reestrutura o resultado pelo blueprint escolhido e
copia para dentro do projeto tudo que o projeto vai citar: normas, skills de
desenvolvimento, agents executores, o hook e o schema. Consequências:

- **Versão nunca vem de memória** (`CLAUDE.md` invariante 8). Um template público com
  `spring-boot 3.4.1` fixo está obsoleto em semanas; aqui o CI falha se alguém escrever
  uma versão como fato.
- **O projeto gerado é autocontido** (invariante 9). Quem clona `pedidos-api` não tem
  este repositório e não precisa dele: `/new-feature`, `/arch-doctor`, `/audit-usage` e
  todas as normas já estão lá dentro. `README.md`, `README.pt-br.md` e um `GENESIS.md`
  registrando a própria geração viajam junto.
- **Zero código de negócio** (D21). Nenhum `ExampleController` para apagar: a primeira
  feature nasce de uma spec real.

### 2 · Arquitetura é dado, e o CI prova

Sete arquiteturas prontas — `hexagonal`, `clean-architecture-multi-module`,
`clean-architecture-single-module`, `layered`, `onion`, `vertical-slice`,
`modular-monolith` — mais `custom-template` para descrever a sua. Cada uma é um YAML
com módulos, `depends_on`, `forbidden_imports`, `packages.map`, `architecture_paths`,
features e trade-offs honestos (um blueprint sem `trade_offs` é inválido).

O teste que prova o desenho: **adicionar uma arquitetura não toca skill, agent ou
comando**. O job `new blueprint doesn't touch prompts` em `validate.yml` verifica isso a
cada push. Nos projetos vizinhos, trocar de hexagonal para onion significa reescrever
os prompts.

Ver [05-blueprints.md](05-blueprints.md).

### 3 · Uma declaração, três efeitos

O campo `forbidden_imports` de um módulo no blueprint alimenta, sem cópia manual:

1. o `CLAUDE.md` local do módulo — para o modelo **saber**;
2. `.claude/forbidden-imports.txt` — para o hook `check` **bloquear** a escrita no
   momento em que ela acontece (`exit 2`, dentro do Claude Code);
3. `depends_on` nos POMs — para o **compilador** recusar o import, em `multi-module`.

Depois, `test-architect` (modo setup) instala ArchUnit e o portão JaCoCo (80% linhas /
70% ramos) através do agent `archunit-installer`, que traduz `packages.map` para regras
ArchUnit. Somam-se Checkstyle na fase `validate` e `lombok.config` com
`flagUsage = ERROR` para `@Data`/`@Setter`. Nenhuma dessas camadas depende do modelo
lembrar a regra.

### 4 · Enforcement em um único arquivo Java, oito modos, sem shell

`.claude/hooks/ArchHook.java` roda em modo single-file (`java ArchHook.java <modo>`),
invocado em forma exec (`command: java`, `args: [...]`) — nenhum shell, nenhum
`chmod`, idêntico em Linux, macOS e Windows. Zero Python, zero `.sh`/`.ps1` em
paralelo. Os oito modos:

| Modo | Evento | Bloqueia? | O que faz |
|---|---|---|---|
| `check` | `PostToolUse` Write\|Edit | sim | imports proibidos + `test-compile` incremental do módulo tocado |
| `format` | `PostToolUse` Write\|Edit | não | `spotless:apply` no módulo |
| `tests` | `Stop` | sim | testes dos módulos alterados desde `HEAD` |
| `schema` | `PreToolUse`/`PostToolUse`/`Stop` | sim | frontmatter de skills/agents/rules e `.mcp.json` contra `extensions.json`, com scan de segredos |
| `guard` | `UserPromptSubmit`, `PreToolUse` | sim | skill de design não escreve em `src/`; spec aprovado é imutável |
| `audit` | 10 eventos do ciclo de vida | não | trilha de execução de toda skill e agent |
| `compose` | manual, e dentro de `doctor` | não | todo serviço do compose está `running`, nenhum container alheio nas portas |
| `doctor` | manual (`/arch-doctor`) | não | diagnóstico do setup |

O CI roda `doctor`, um `BoundaryTest` que injeta um import proibido e um
`InjectionPathTest` que injeta uma injection relativa ao cwd — ambos exigem `exit 2`
em `ubuntu-latest`, `macos-latest` e `windows-latest`. "Multiplataforma" é fato
verificado, não alegação.

Ver [01-tipos-de-arquivo.md § Hook](01-tipos-de-arquivo.md#hook).

### 5 · Trilha de auditoria determinística por execução

Nenhum projeto vizinho responde "quanto custou esta feature e o que ela encadeou". Aqui
o modo `audit` do hook escreve, a cada `Stop`, um relatório Markdown por invocação de
skill ou agent no projeto gerado — aberta por `/comando` ou pela própria chamada
`Skill`/`Agent` do modelo — com tokens e custo por peça (sem dupla contagem), árvore de
encadeamento com barras de duração, arquivos tocados, permissões pedidas, ferramentas
que falharam, regras que deveriam ter carregado, e `HEAD` antes e depois. O prompt
inicial entra redigido (tokens, senhas e chaves privadas apagados por padrão em
`extensions.json`). Dois ledgers (`history.jsonl`, `nodes.jsonl`) alimentam
`ArchHook.java audit summary`, e a skill `/audit-usage` renderiza a visão consolidada.

É hook, e não skill, porque precisa sobreviver ao modelo esquecer, à sessão morrer e ao
Ctrl+C (D35, D38). Zero tokens gastos para produzir o relatório.

Ver [08-audit-usage.md](08-audit-usage.md).

### 6 · Pipeline spec-first, com fronteiras que são hook

`/new-feature` desenha **um caso de uso por execução** em cinco parciais com dono único
(`use-case-design` → `domain-modeling` → `rest-api-architect` → `persistence-architect`
→ `test-architect`, mais `messaging-architect` condicional), consolida num
`UC-NNN-spec.md` com ciclo `draft → approved → implemented`, pede aprovação e só então
oferece o executor `java-spring-boot-developer` — um agent separado, com tools
restritas, que só escreve em `src/`.

Três fronteiras deixaram de ser prosa depois de serem violadas em execuções reais:

- **Design escreve só em `docs/`** — o `guard` bloqueia `Write`/`Edit` em `src/**`
  enquanto uma skill de design está aberta, exceto de dentro de um agent executor.
- **Spec aprovado é imutável** — o `guard` congela a pasta `docs/use-cases/UC-*/` cujo
  spec está `approved` ou `implemented`.
- **Git só por `git-publish`**, atrás de dois `AskUserQuestion`; `git push` é sempre
  `ask` e `git push --force` é `deny`.

Ver [03-new-feature.md](03-new-feature.md).

### 7 · O `.claude/` tem arquitetura própria, e 11 invariantes com portão em CI

A mesma Clean Architecture aplicada ao Java vale para os arquivos de IA: `hooks/`
verifica `agents/`, que invocam `skills/`, que citam `rules/` + `blueprints/` — folhas
que não citam ninguém. Onze invariantes (`CLAUDE.md`), e o job `design` de
`validate.yml` falha o build se um for violado. Os que nenhum vizinho tem:

- **`rules/` é folha** — nenhuma norma menciona skill, agent ou comando.
- **Cada norma tem um dono** — uma frase conhecida em dois arquivos de `rules/` é bug.
- **Norma não carrega código** — boilerplate vive em `skills/*/templates/*.example`.
- **Frontmatter tem schema** — `extensions.json` é o dono dos campos que o runtime
  reconhece. O runtime ignora um campo inventado em silêncio, e `claude plugin validate`
  deixa passar; `ArchHook.java schema` não.
- **Exemplares compilam de verdade** — o job `exemplar-imports` baixa um `starter.tgz`
  real e resolve cada `import` de cada `.java.example` contra o classpath.
- **Nenhuma versão escrita como fato** fora do histórico de decisões.
- **Nenhuma dependência fora de JDK, git e curl** — `pip install`/`npm install` em
  `.claude/` falha o CI.

Ver [07-ci-validate.md](07-ci-validate.md).

### 8 · Meta-ferramenta que decide a forma da próxima extensão

`/claude-code-architect-designer` entrevista, aplica uma matriz de decisão e escolhe
entre seis formas — skill auto-invocável, skill manual, subagent, rule, seção do
`CLAUDE.md`, servidor MCP compartilhado ou por agent — ou responde "não crie nada" (um
CLI já resolve, ou é caso de hook/`permissions.deny`). Um agent só nasce por um de três
motivos (contexto, tools, model); quem não se justifica vira skill.

Cada peça deste repositório nasceu de um sintoma observado em execução real, registrado
em `lessons-learned/` e remediado por uma decisão numerada em `decisions/`. Esses dois
diretórios ficam fora do repositório público de propósito (`.gitignore`): são história
do mantenedor, não norma.

Ver [06-claude-code-architect-designer.md](06-claude-code-architect-designer.md).

### 9 · Contexto barato por construção

- `CLAUDE.md` da raiz sob 200 linhas: invariantes e roteamento, nada mais.
- Normas entram por `paths` quando um arquivo do território é tocado. Os globs de
  `api-rest.md`, `persistence.md`, `value-objects.md`, `observability.md` e
  `messaging.md` são **reescritos na geração** a partir do `packages.map` do blueprint —
  um glob copiado literalmente deixaria a norma sem carregar, em silêncio.
- `metadata:` foi banido do frontmatter (custa tokens a cada invocação e não impõe
  nada); contratos vivem no corpo, onde são instrução de fato.
- Skills de design viajam preloaded no executor (`java-patterns` via `skills:`), sem
  turno próprio.

### 10 · Transversais que já vêm resolvidos

Nenhum é exclusivo por si só; juntos, nenhum vizinho os reúne:

| Preocupação | Onde mora |
|---|---|
| `Idempotency-Key` desde o primeiro endpoint, via AOP e tabela compartilhada | `rest-api-architect` + `persistence-architect` (D40, D44) |
| Logging estruturado com máscara de dado sensível (`@LogExecution`, `@MaskSensitiveData`) | `commons-logging-installer`, disparado pelo pre-flight de `/new-feature` |
| Observabilidade: OTLP collector com pipelines de traces **e** metrics, backend Jaeger ou Grafana + Tempo + Prometheus | `docker-architect` |
| Container "subiu" mas não responde, porta ocupada por projeto irmão | `ArchHook.java compose` |
| Kafka producer/consumer com at-least-once, retry e DLQ | `messaging-architect` + `rules/messaging.md` |
| Paginação sem `Pageable` cruzando o port da aplicação | `rules/architecture-ddd.md` (D42) |
| Segredos: `permissions.deny` em `*.env`, `*.pem`, `application-prod.yml`; scan de `headers`/`env` em `.mcp.json` | `settings.json` + `ArchHook.java schema` |

## Tabela comparativa

| Capacidade | claude-spring-architect | Template estático | Pacote de skills | Bundle agents + hooks |
|---|---|---|---|---|
| Gera o projeto pelo Initializr, versões ao vivo | ✅ | ❌ (clone, versão fixa) | ❌ | ❌ |
| Arquitetura selecionável como dado (7 + custom) | ✅ | ❌ (uma, fixa) | ❌ | ❌ |
| Boundary derivada do blueprint e bloqueada no hook | ✅ | ❌ | ❌ | ❌ (hooks genéricos) |
| Hook multiplataforma sem shell, testado em 3 OS no CI | ✅ | ❌ | — | ❌ (bash) |
| Trilha de auditoria por execução, custo por skill/agent | ✅ | ❌ | ❌ | ❌ |
| Pipeline spec-first com spec congelado por hook | ✅ | ❌ | ❌ | parcial (agents por papel, sem spec) |
| Invariantes do próprio `.claude/` verificados em CI | ✅ | ❌ | ❌ | ❌ |
| Schema de frontmatter que pega campo inventado | ✅ | ❌ | ❌ | ❌ |
| Exemplares compilados contra classpath real no CI | ✅ | ❌ | ❌ | ❌ |
| Projeto gerado autocontido, sem depender do gerador | ✅ | ✅ (é o próprio clone) | — | ✅ |
| Skills de conhecimento Spring/JPA | ✅ (12 normas + 9 skills de design) | ✅ | ✅ (às vezes mais amplas) | ✅ |
| Suporte a Codex/Cursor além de Claude Code | ❌ | parcial | ✅ | parcial |

## O que não é diferencial — dito com honestidade

- **Conhecimento Spring puro.** Pacotes de skills como `spring-boot-skills` cobrem
  Security, WebFlux e mais versões. Aqui não existe `rules/security.md` ainda
  (planejada em `00-index.md`).
- **Gradle.** O schema aceita `build.tool: gradle`, mas só existem templates de POM; o
  caminho exercitado ponta a ponta é Maven.
- **Custo.** O pipeline completo é caro por desenho — uma execução real de
  `/new-feature` custou ~USD 15 para um agregado de dois campos. A trilha de auditoria
  existe justamente para medir isso, e a disciplina de custo está em
  [03-new-feature.md § Disciplina de custo](03-new-feature.md).
- **Só Claude Code.** Hooks, `paths`, `disable-model-invocation` e `context: fork` são
  do runtime do Claude Code; nada aqui roda em Codex ou Cursor.
- **Não é afiliado à Anthropic.** "Claude" no nome segue a prática do ecossistema, não
  indica produto oficial.

## Fontes da comparação

- [piomin/claude-ai-spring-boot](https://github.com/piomin/claude-ai-spring-boot)
- [ryu-qqq/claude-spring-standards](https://github.com/ryu-qqq/claude-spring-standards)
- [rrezartprebreza/spring-boot-skills](https://github.com/rrezartprebreza/spring-boot-skills)
- [a-pavithraa/springboot-skills-marketplace](https://github.com/a-pavithraa/springboot-skills-marketplace)
- [altmemy/claude-code-templates — claude-spring-boot](https://github.com/altmemy/claude-code-templates/tree/main/claude-spring-boot)
