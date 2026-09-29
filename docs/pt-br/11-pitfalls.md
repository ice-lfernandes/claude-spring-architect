# Pitfalls do runtime do Claude Code

Fonte primária: `@claude-help.md`, `.claude/schemas/extensions.json` (blocos `settings`,
`injections`, `arguments`, `types`), e o que `java .claude/hooks/ArchHook.java schema`
verifica.

## Por que esta página existe

Cada item abaixo é uma armadilha do **runtime**, não uma decisão deste repositório: vale
para qualquer projeto que use skills, agents, hooks ou MCP, e em todos eles o erro é
**silencioso** — nada falha, nada avisa, e a peça simplesmente não faz o que o autor
acredita que ela faz.

Eles moravam em `@CLAUDE.md` § Known pitfalls, que é lido em toda sessão. Fato de runtime
não precisa estar sempre em contexto: precisa estar onde quem escreve uma skill, um hook ou
um `.mcp.json` vai olhar. `@CLAUDE.md` mantém os pitfalls **deste repositório** (territórios
de escrita, congelamento de spec, quem escreve `pom.xml`, `audit` desligado aqui) e aponta
para cá com uma linha na tabela de routing.

O que **não** está aqui: os campos de frontmatter que o runtime reconhece, que são dados com
dono único em `.claude/schemas/extensions.json` e estão descritos em
[01-tipos-de-arquivo.md](01-tipos-de-arquivo.md).

## Skills

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
passa a mandar aplicar algo que o modelo nunca recebeu. Foi o caso de `java-patterns` em
`java-spring-boot-developer`. Para entregar conteúdo de uma skill manual a um agent, o caminho
é injetar no `SubagentStart` (`ArchHook.java context subagent`, decisão 0077), não o
`skills:`.

**Tudo que o modelo deve obedecer mora no corpo do arquivo**, nunca no frontmatter.
`metadata.*` foi removido de skills e agents: ownership, `reads`, `handoff` e contratos vivem
na seção `## Contrato` do corpo. Não devolva `metadata:` a um `SKILL.md` — custa tokens em
toda invocação e não impõe nada.

## Frontmatter e validação

**O runtime ignora frontmatter desconhecido, em silêncio.** Um campo inventado é decoração,
não comportamento. Lista dos campos nativos em `@claude-help.md`.

**`claude plugin validate` não valida campos.** Aceita `metadata:`, aceita camelCase numa
skill e aceita um campo inventado, sempre com `✔ Validation passed`. Não olha
`.claude/agents/`, `.claude/rules/` nem `.claude/settings.json`. Pega YAML malformado, e nada
mais. Quem valida campos é `java .claude/hooks/ArchHook.java schema`.

## Hooks

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
`tests`, `schema`, `audit`, `guard`, `context`. Invocar um deles à mão sem `</dev/null` bloqueia até
algo fechar o stdin, sem saída: um comando que parece pendurado, não falho. `export`,
`doctor`, `compose` e `build` são invocados por pessoas e não leem nada.

## `AskUserQuestion`

**Rejeita uma pergunta com menos de 2 opções, e rejeita o lote inteiro com ela:**
`InputValidationError ... "too_small" ... path: ["questions",1,"options"]`. Uma pergunta com
uma opção não é pergunta — decida, e registre a decisão onde a resposta iria. O lote também
tem limite superior de 4 perguntas por chamada. O projeto gerado carrega este mesmo pitfall
no seu próprio `CLAUDE.md`, vindo de
`project-bootstrap/templates/root.CLAUDE.md.example`.

## MCP

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
