# Pitfalls

Fonte primária: `@claude-help.md`, `.claude/schemas/extensions.json` (blocos `settings`,
`injections`, `arguments`, `types`, `guard` e `compose`), o que
`java .claude/hooks/ArchHook.java schema` verifica, e os registros de decisão que cada item
cita.

## Por que esta página existe

Cada item abaixo é uma armadilha que falha **em silêncio**, ou que um hook recusa com uma
mensagem que o leitor já precisa entender. A página tem duas partes:

- **Parte 1 — o runtime do Claude Code.** Vale para qualquer projeto que use skills, agents,
  hooks ou MCP: nada falha, nada avisa, e a peça simplesmente não faz o que o autor acredita
  que ela faz.
- **Parte 2 — este repositório.** Fatos deste `.claude/`: territórios de escrita, pastas de
  spec congeladas, quem é dono do `pom.xml` e do outbox, o que `guard sweep` e `compose gate`
  cobrem e o que não cobrem.

As duas moravam em `@CLAUDE.md` § Known pitfalls, que é lido em toda sessão. A parte 1 saiu
na decisão `0061`, a parte 2 na decisão `0090`. Nenhuma precisa estar sempre em contexto:
são necessárias enquanto alguém projeta ou edita uma peça do `.claude/`, ou escreve sobre
uma — que é quando `claude-code-architect-designer` lê esta página. A maior parte da parte 2
também está atrás de `guard`, `schema` ou `compose gate`, cuja mensagem de bloqueio nomeia a
regra e a correção. `@CLAUDE.md` mantém só os três fatos que mordem em qualquer sessão sem
hook nenhum por trás, e aponta para cá com uma linha na tabela de routing.

**Um pitfall novo entra aqui, nas duas línguas — nunca de volta no `@CLAUDE.md`.** Foi esse
caminho que reencheu a seção entre a `0061` e a `0090`.

O que **não** está aqui: os campos de frontmatter que o runtime reconhece, que são dados com
dono único em `.claude/schemas/extensions.json` e estão descritos em
[01-tipos-de-arquivo.md](01-tipos-de-arquivo.md).

## Parte 1 · Runtime do Claude Code

### Skills

**Uma skill não pode ter o nome de um comando nativo.** O nome da pasta vira o comando, e
`/doctor`, `/init`, `/context`, `/memory` já existem no runtime. É por isso que a skill de
diagnóstico deste repositório se chama `arch-doctor`. Sombrear um comando nativo não gera
erro — executa o comando errado. `ArchHook.java schema` reprova a pasta pelo nome, contra a
lista `types.skill.native_commands` de `.claude/schemas/extensions.json` — que precisa
crescer quando o runtime ganha comando novo.

**`$ARGUMENTS` no corpo de uma skill é interpolado em toda ocorrência**, não apenas sob
`## Target`. Uma frase que *fala sobre* o argumento chega ao modelo com o valor real dentro
dela: o `"invoque test-architect com $ARGUMENTS vazio (modo setup)"` do `/new-feature`
chegou como `"com UC-003-initiate-kyc-verification vazio (modo setup)"` — uma ordem para usar
o modo setup nomeando o argumento que significa modo design. Escreva "o argumento" ou "o
alvo acima"; `ArchHook.java schema` rejeita o literal, lendo `arguments` de
`.claude/schemas/extensions.json`.

**`allowed-tools` com `Bash(comando:*)` verifica cada segmento do pipe separadamente.** Uma
injeção `` !`a | b | c` `` no corpo precisa de regra para `a`, `b` e `c`; falte uma e o
comando inteiro é bloqueado antes de rodar. Escreva injeções como um comando único
(`ls .claude/skills`, não `find … | sed | sort`). Só afeta skills que restringem Bash:
`allowed-tools: Bash` sem filtro deixa o pipeline inteiro passar — e por isso
`ArchHook.java schema` o recusa, salvo com uma linha `**Unfiltered Bash:** <motivo>` no
`## Contract` (decisão 0076).

**Uma injeção `` !`…` `` de frontmatter roda no shell persistente da sessão, no cwd que ele
tiver naquele momento.** Um `cd` para o diretório de uma skill em uma chamada `Bash`
contamina toda injeção de toda skill invocada depois, e um `test -f` relativo passa a
reportar como ausente um arquivo que existe. Leia um template com `Read` em caminho
absoluto, nunca `cd` + `cat`. Toda injeção deste repositório resolve caminhos a partir de
`"${CLAUDE_PROJECT_DIR:-.}"`, e `ArchHook.java schema` bloqueia quem não faz isso — uma
injeção genuinamente independente de cwd precisa de uma regex em
`injections.exempt_patterns`.

**Uma skill com `disable-model-invocation: true` listada no `skills:` de um agent não é
pré-carregada — e nada avisa.** O agent sobe sem ela e o corpo que diz "catálogo pré-carregado"
passa a mandar aplicar algo que o modelo nunca recebeu. Foi o caso de `gof-design-patterns` (então `java-patterns`) em
`java-spring-boot-developer`. Para entregar conteúdo de uma skill manual a um agent, o caminho
é injetar no `SubagentStart` (`ArchHook.java context subagent`, decisão 0077), não o
`skills:`.

**Tudo que o modelo deve obedecer mora no corpo do arquivo**, nunca no frontmatter.
`metadata.*` foi removido de skills e agents: ownership, `reads`, `handoff` e contratos vivem
na seção `## Contract` do corpo. Não devolva `metadata:` a um `SKILL.md` — custa tokens em
toda invocação e não impõe nada.

### Frontmatter e validação

**O runtime ignora frontmatter desconhecido, em silêncio.** Um campo inventado é decoração,
não comportamento. Lista dos campos nativos em `@claude-help.md`.

**`claude plugin validate` não valida campos.** Aceita `metadata:`, aceita camelCase numa
skill e aceita um campo inventado, sempre com `✔ Validation passed`. Não olha
`.claude/agents/`, `.claude/rules/` nem `.claude/settings.json`. Pega YAML malformado, e nada
mais. Quem valida campos é `java .claude/hooks/ArchHook.java schema`.

### Hooks

**`.claude/settings.json` só é lido no início da sessão.** Editar hooks no meio da sessão
não tem efeito — é preciso reiniciar o `claude`.

**Um hook falha silenciosamente de quatro formas, nenhuma delas um erro.** Nome de evento
inexistente — nunca dispara. `matcher` em um evento que não lê matcher — não filtra nada.
Pipe ou `&&` dentro de `"command"` — vira parte do nome do arquivo (forma exec: `command` é o
binário, `args` são os argumentos). Um modo que lança exceção — sai 0 pelo catch do `main`,
e parece ter passado. `schema` pega as três primeiras, a partir do bloco `settings` de
`.claude/schemas/extensions.json`; a quarta, só rodando o modo à mão.

**Um `if` com caminho só casa via `Edit(...)` ou `Read(...)`.** `Edit` cobre toda ferramenta
nativa que escreve arquivo, `Write` incluído; um `"if": "Write(.claude/**/*.md)"` parece um
filtro e não filtra nada. Fonte: `docs/pt-br/claude-code-docs/07-settings-permissoes-e-seguranca.md`.

**Só os modos de protocolo de hook do `ArchHook.java` leem stdin** — `check`, `format`,
`tests`, `schema`, `audit`, `guard`, `context` e `compose gate`. Invocar um deles à mão sem
`</dev/null` bloqueia até algo fechar o stdin, sem saída: um comando que parece pendurado, não
falho. `export`, `doctor`, `build` e o `compose` puro são invocados por pessoas e não leem nada.

### `AskUserQuestion`

**Rejeita uma pergunta com menos de 2 opções, e rejeita o lote inteiro com ela:**
`InputValidationError ... "too_small" ... path: ["questions",1,"options"]`. Uma pergunta com
uma opção não é pergunta — decida, e registre a decisão onde a resposta iria. O lote também
tem limite superior de 4 perguntas por chamada. O projeto gerado carrega este mesmo pitfall
no seu próprio `CLAUDE.md`, vindo de
`project-bootstrap/templates/root.CLAUDE.md.example`.

### MCP

**`.mcp.json` só é lido no início da sessão**, igual ao `settings.json`. Adicionar ou editar
um servidor no meio da sessão não tem efeito até reiniciar o `claude`.

**Um servidor de escopo project em `.mcp.json` precisa de aprovação humana uma única vez**, na
primeira vez que carrega (`claude mcp list` mostra os pendentes). Esse prompt é a fronteira
de confiança que um repositório clonado não pode pular — nunca contorne com
`enableAllProjectMcpServers`.

**A precedência de servidores é `local > project > user`, silenciosamente.** Um servidor
pessoal com o mesmo nome do servidor do time em `.mcp.json` sombreia o do time — sem aviso de
nenhum dos lados.

**Um `${VAR}` não definido em `.mcp.json` não falha a geração.** O servidor carrega com o
texto literal `${VAR}` e falha ao conectar em runtime. `claude mcp list` mostra o aviso de
variável ausente; a validação de schema de frontmatter não.

## Parte 2 · Este repositório

### Território de escrita e os guards

**Uma skill escreve só no território da sua classe, e `guard` bloqueia o resto com exit 2.**
Negação por padrão; a mensagem nomeia a classe e o seu `write_allow`, e a correção é o dado
(`skill_classes` em `.claude/schemas/extensions.json`), nunca uma nova tentativa. Uma rodada
de design só escreve docs e não pode nem *chamar* uma skill de classe `build` —
`docker-architect` inclusive: o serviço de compose que falta é registrado no parcial e
materializado depois por `/docker-architect`. Sem fase de skill aberta, o território é livre,
e é por isso que editar um arquivo à mão nunca é bloqueado.

**Uma skill que encadeia outra da mesma classe soma o território dela; de outra classe, troca.**
Mesma classe — `arch-adopt` → `sonarqube-setup` → `docker-architect`, todas `build` — e a fase
guarda as três, então cada uma escreve os próprios caminhos e quem chamou continua escrevendo
depois que a chamada volta. Outra classe — `/new-feature` chamando uma skill de design — e a
fase se estreita para a chamada, que é o que mantém um passo de design só em docs. A regra
mantinha só o território de quem chamou numa chamada da mesma classe, supondo que uma classe
é um território; `build` dá um território a cada skill, e o `sonarqube-setup` encadeado pelo
`arch-adopt` teve o `pom.xml` recusado. Design:
`.claude/decisions/0092-guard-same-class-chain-sums-territories.md`.

**A escrita de um subagent é julgada por `agent_classes`, não pela fase de quem o chamou.**
Toda escrita carrega o seu `agent_type`, e `guard` confere o caminho contra o `write_allow`
do próprio agent, então uma fase de design aberta nem amplia nem estreita o território dele.
Um agent que nenhuma classe lista cai de volta na fase de quem chamou — nada mais descreve o
que ele pode escrever.

**O enforcement deixou de depender da tool, mas ainda não é do tamanho do filesystem.** Até
o lessons-learned-014 § 1, todo hook casava pelo nome de uma tool, então um heredoc, um
`sed -i` ou um `tee` passavam por `guard`, `check`, `format`, `audit` e `schema` do mesmo
jeito — exatamente as formas que uma instrução do host para preferir `Bash` a `Write`/`Edit`
produz. `guard bash` (`PreToolUse`, matcher `Bash`) agora lê o comando procurando as formas
de escrita de `guard.bash_write_shapes` e aplica as checagens de território e de pasta
congelada a todo alvo que consegue ler literalmente. **O que ele deliberadamente não faz:**
um alvo com `$`, crase ou glob, ou que cai fora do repositório, é pulado sem aviso, e `check`
e `format` ficam fora do `Bash` — ArchUnit e `spotless:apply` já rechecam os dois, e um
matcher `PostToolUse` ali pagaria uma JVM a cada `ls`. A afirmação verdadeira é estreita:
**dentro do Claude Code, via `Write`/`Edit`/`MultiEdit`/`NotebookEdit`, e via as formas de
shell que a lista nomeia.** Design: `.claude/decisions/0063-bash-write-enforcement.md`.

**`guard sweep` cobre os dois guards de escrita no `Stop`, e ele é do tamanho do git.** Ele
compara a working tree com a baseline que `guard prompt` tira no `UserPromptSubmit`, então
uma árvore já suja antes do turno não é reportada, e roda as mesmas checagens de território e
de pasta congelada sobre o que mudou — não importa qual tool escreveu. Dois limites antes de
lê-lo como cobertura total: é **detecção, não prevenção** (a escrita já aconteceu), e um
caminho que o git ignora nunca aparece em `git status --porcelain` e nunca é varrido — o que,
neste repositório, é só o que o `.gitignore` ainda lista, já que `.claude/decisions/` e
`.claude/lessons-learned/` são versionados desde 2026-09-28. O fechamento do `status:` de uma
spec e os toggles `[ ]` → `[x]` são admitidos comparando com `git show HEAD:`, já que o sweep
não tem `old_string` para ler. Caminhos em `guard.sweep_exempt` são pulados — hoje `.claude/audit-usage/**`, a trilha versionada que o hook `audit` grava em paralelo com a baseline; os guards de tool-time continuam recusando escrita do modelo ali. **Um caminho que `guard write` ou `guard bash` já admitiu no turno também é pulado:** ele foi julgado contra a fase e o status da spec do próprio momento, e julgá-lo de novo no `Stop` contra a fase aberta ali — o território vazio do `git-publish` depois do `/new-feature` — ou contra uma pasta aprovada depois marcava a própria spec, os parciais e as escritas do executor da rodada (issue #74). O que o sweep julga são as escritas que nenhum guard de tool-time viu, contra a fase aberta no `Stop`; um caminho admitido antes e reescrito depois por uma grafia que nenhum guard lê é pulado junto. Design: `.claude/decisions/0065-guard-sweep-on-stop.md`, isenção `0105`, caminhos admitidos `0114`.

**Um push sempre pede confirmação, um force push nunca roda, e o `Bash` de uma skill é
restrito.** `git push` e `gh repo create` ficam em `permissions.ask`, que é avaliado antes de
qualquer allow — então o prompt aparece até dentro de `git-publish`, depois do gate dela.
`guard bash` recusa force push em toda grafia que consegue ler (`-f`, `-uf`,
`--force-with-lease`, `+ref`, `git -C … push`, `sh -c "…"`), listadas em `guard.force_push`;
a antiga linha de `deny` só pegava `--force`. Uma skill cujo `allowed-tools` nomeia `Bash` sem
filtro falha no `schema`, a menos que o seu `## Contract` traga uma linha
`**Unfiltered Bash:**` dizendo por quê — quatro trazem (as que rodam build, rede e docker); as
demais listam prefixos, e um comando de injeção que falta nessa lista aborta a skill. Design:
`.claude/decisions/0076-bash-scope-and-force-push-guard.md`.

**Comentar, editar, fechar uma issue e `gh api` sempre pedem confirmação, neste repo.** As
quatro formas ficam em `permissions.ask` de `.claude/settings.json` — o prompt aparece até
depois do sim do `/triage-issue`, e é a mesma confirmação que você vê se o corpo de uma issue
convencer o `issue-verifier` a tentar publicar algo. Não é ferramenta quebrada: o texto de uma
issue pública é de terceiros. Só aqui; o projeto gerado não tem essas linhas. Design:
`.claude/decisions/0103-issue-filing-and-skeptical-triage.md`.

### Classes e modelos

**Toda skill declara `model`, dentro do conjunto da sua classe.** `schema` reprova um
`SKILL.md` sem ele, ou com um valor fora de `skill_classes.classes.<c>.allowed_models`
(`design` e `meta` só `opus`, `observer` e `ops` só `sonnet`). O `model` de uma skill vale
pelo resto do turno, não só pela skill — é por isso que um modelo fixado sozinho não é motivo
para ser agent, e por isso que uma skill com modelo mais baixo disparada no meio do turno é o
caso a vigiar. Design: `.claude/decisions/0081-skill-model-required-per-class.md`.

### Specs de caso de uso

**A pasta de um caso de uso implementado fica congelada, exceto por três escritas:** a linha
`status:` da spec movida ao longo de `guard.status_transitions`, um toggle de checklist nela,
e `UC-NNN/CHANGELOG.md` — a escrita que a consolidação do `/new-feature` *exige* para toda
mudança que uma linha de impacto faz. Os basenames isentos são dados
(`guard.frozen_exempt_basenames`), casados diretamente sob a pasta: `notes/CHANGELOG.md`
continua congelado.

**A spec tem três estados fechados, e o checklist é marcado antes de o status fechar.**
`approved` fecha em `implemented`, ou em `implemented-blocked` quando a rodada deixou um caso
de uso aprovado inalcançável de ponta a ponta — o código está em disco e verde, e um
`Satisfied by` que a spec nomeou ainda não existe. Os dois voltam para `approved`, que é a
saída que uma rodada fechada por engano não tinha. Um toggle `[ ]` → `[x]` é admitido **nos
três** (`guard.checklist_toggle_statuses`): ele é monotônico, e exigir `approved` já congelou
23 caixas desmarcadas para sempre. A ordem continua sendo contrato do executor, não do hook: a
linha de status é a última escrita que ele faz na pasta. Design:
`.claude/decisions/0066-spec-state-machine.md`.

### Compose

**`compose gate` roda a checagem de compose sem ninguém pedir, e bloqueia.** O step 7 do
`docker-architect` já chamava `ArchHook.java compose` de "não opcional", e ele nunca tinha
sido rodado contra um projeto: um bloco `kafka` publicando 9092 enquanto anunciava só
`kafka:9092` saiu no primeiro dia e ficou inalcançável para todo cliente do host até um caso
de uso precisar dele. O gate é o mesmo `composeReport()` — uma definição só de saudável,
compartilhada com o `doctor` — silencioso enquanto saudável, exit 2 com as linhas que falharam
caso contrário. Um serviço parado de propósito também bloqueia o stop; isso é um gate, não um
bug. Design: `.claude/decisions/0064-compose-gate-on-stop.md`.

**Um healthcheck que passa não prova nada sobre alcance a partir do host** — ele roda dentro
do container, onde `localhost` é o próprio serviço. Um serviço que publica uma porta no host
enquanto anuncia só o seu nome na rede do compose (`KAFKA_ADVERTISED_LISTENERS:
PLAINTEXT://kafka:9092` ao lado de `ports: "9092:9092"`) não é alcançável por nenhum cliente
do host, e nem o healthcheck nem o Testcontainers percebem — o Testcontainers monta os
próprios listeners. `ArchHook.java compose` lê o arquivo procurando isso
(`compose.advertised_env_suffixes`); um serviço que não anuncia nada não afirma nada e é
deixado em paz.

**Todo `${VAR:default}` que aponta para um serviço do compose vale em dois lugares.** O
default serve o `./mvnw spring-boot:run` no host: precisa ser `localhost` numa porta que o
serviço **publica**. A variável no `environment:` do `app` serve o container: sem ela,
`localhost` lá dentro é a própria aplicação. O collector OTLP não publicou porta da 0046 até
a issue #66, e todo export do host falhou com o `compose` dizendo saudável. Agora a pergunta 5
do `compose` lê os dois lados, e o gate bloqueia um projeto antigo logo depois do
`/arch-adopt`. A saída é o prompt da migration `otlp-host-run`. Se outro projeto segura a
4318, use `OTLP_HTTP_PORT` no `.env` e a mesma porta em `OTLP_ENDPOINT`/`OTLP_METRICS_ENDPOINT`
no host. Design: `.claude/decisions/0110-otlp-host-first-collector.md`.

**`grep -A2 "^services:" docker-compose.yml` não é a lista de serviços.** Ele lê duas linhas
e para, perdendo serviços declarados mais abaixo e reportando os filhos de `volumes:` como
serviços. Toda peça que precisa dessa lista — a injeção e o step 3 do `docker-architect`, o
step 9 do `messaging-architect`, o step 9 do `persistence-architect` — usa o one-liner `awk`
limitado ao bloco `services:`. Um retrato errado convida a recriar um serviço que já existe.

### Donos dentro de uma rodada de feature

**O `pom.xml` tem exatamente um escritor numa rodada de feature: o executor, para uma
dependência que a spec declara** (o § 7 do parcial de mensageria, ou o equivalente de
persistência). As skills de design só escrevem docs, `/new-feature` escreve a spec, e os dois
installers são donos só do próprio setup. Qualquer outra coisa no arquivo — um plugin, uma
property, um bump de versão — é reportada, nunca escrita.

**Fora de uma rodada de feature, as linhas `sonar.*` do build file raiz são do
`sonarqube-setup`.** Ele escreve o plugin do scanner e as properties uma vez — encadeado pelo
step 8.4 do `project-bootstrap` ou pelo `arch-adopt` — e para numa segunda execução quando
o scanner já está lá. O serviço `sonarqube` do compose continua sendo do `docker-architect`, e
o token não fica em arquivo nenhum: o scanner lê `SONAR_TOKEN` do ambiente. Design:
`.claude/decisions/0091-sonarqube-setup-skill.md`.

**O outbox tem três donos, divididos por pergunta.** `persistence-architect`: a tabela, as
colunas, a claim query, o batch size, o teto de tentativas, a janela de retenção e o
statement do prune. `messaging-architect`: se o caso precisa de um, a garantia de entrega, e a
passada do relay com o lado do broker. `jobs-architect`: quando qualquer coisa roda — o
intervalo de poll do relay, o liga/desliga, o número de réplicas que a claim precisa
suportar, e o job de prune. Uma linha de § 6 nomeando colunas é divergência, e uma decisão de
coluna ou de claim que derrubaria uma garantia declarada para o pipeline em vez de ser
resolvida por precedência (`.claude/decisions/0087-jobs-architect-skill.md`).

**O bounded context é um fato do projeto, não uma resposta por caso de uso.** É o primeiro
segmento de todo nome de tópico, perguntado junto com as coordenadas no `/init-project` e
escrito no `CLAUDE.md` raiz do projeto gerado. Um prefixo escolhido dentro de um caso de uso
dá a um sistema dois namespaces. Um projeto adotado por `/arch-adopt`, ou gerado antes de a
pergunta existir, recebe a pergunta no step 8 dessa skill — mas só na **segunda** execução
de `/arch-adopt` depois que a mudança for publicada: a primeira roda a cópia antiga da
skill, que ainda não tem o step. Até lá, escrever a linha à mão é o jeito imediato de
resolver. Design: `.claude/decisions/0093-lessons-learned-016-fact-sources.md`.

### Rules e decisions

**Uma rule sem `paths` carrega no launch, em toda sessão — não é "só citação".** Por isso toda
rule declara o glob mais estreito que a sustenta, e Java é `**/src/**/*.java`, nunca
`**/*.java`: esse casa com `.claude/hooks/ArchHook.java` e puxa toda norma Java a cada leitura
do hook. O nome antigo de uma rule renomeada vai em `export.retired`, ou o destino fica com as
duas. Design: `.claude/decisions/0082-rules-without-paths-load-at-launch.md`.

**`.claude/decisions/` não é norma nem documentação viva.** Registra o que foi decidido na
data, não o que vale hoje: sem `paths`, fora do `00-index.md`, e não viaja para o projeto
gerado. Um registro novo substitui o antigo; o antigo não é reescrito.
