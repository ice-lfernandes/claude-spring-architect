# Visão geral — arquitetura do `.claude/`

## O que este repositório gera

`ai-spring-setup` transforma um diretório vazio em um projeto Spring Boot com
arquitetura declarada, boundaries executáveis (hooks) e uma pipeline de design→código
já instalada. Ele mesmo não compila nada — não tem `pom.xml`, não tem testes Maven. O
produto são arquivos de instrução.

Essa ideia central molda tudo: o próprio `.claude/` deste repositório é organizado como
uma Clean Architecture, com dependências apontando em uma única direção. Isso não é
metáfora — é a regra real de quem pode citar quem (`CLAUDE.md` § Invariants 1 e 2).

## Diagrama geral

```mermaid
flowchart TB
    subgraph L0["Enforcement — determinístico"]
        SETTINGS["settings.json"]:::hook
        HOOK["ArchHook.java\n(check · format · tests · schema · doctor)"]:::hook
    end

    subgraph L1["Procedimento — skills"]
        CLAUDEMD["CLAUDE.md\n(raiz — índice e roteamento)"]:::claudemd
        SK_INIT["skill: init-project"]:::skill
        SK_BOOT["skill: project-bootstrap"]:::skill
        SK_NF["skill: new-feature"]:::skill
        SK_UC["skill: use-case-design"]:::skill
        SK_DOM["skill: domain-modeling"]:::skill
        SK_PERS["skill: persistence-architect"]:::skill
        SK_REST["skill: rest-api-architect"]:::skill
        SK_TEST["skill: test-architect"]:::skill
        SK_DOCKER["skill: docker-architect"]:::skill
        SK_MSG["skill: messaging-architect"]:::skill
        SK_DOCTOR["skill: arch-doctor"]:::skill
    end

    subgraph L2["Execução isolada — agents"]
        AG_INITZR["agent: project-initializer\n(model: opus)"]:::agent
        AG_DEV["agent: java-spring-boot-developer\n(model: sonnet, effort: max)"]:::agent
        AG_ARCH["agent: archunit-installer\n(model: sonnet, effort: medium)"]:::agent
    end

    subgraph L3["Normas e dados — folhas"]
        RULES["rules/*.md\n(architecture-ddd, naming, error-handling,\ncode-quality, api-rest, lombok,\nvalue-objects, persistence, testing,\nobservability, logging, messaging)"]:::rule
        BLUEPRINTS["blueprints/*/*.yaml\n(_schema.md define o contrato)"]:::blueprint
    end

    CLAUDEMD -->|roteia por tabela| SK_INIT
    CLAUDEMD -->|roteia por tabela| SK_NF
    CLAUDEMD -->|roteia por tabela| SK_DOCTOR

    SK_INIT -->|Agent tool: contexto + tools restritos + model opus| AG_INITZR
    AG_INITZR -->|segue o procedimento de| SK_BOOT
    SK_BOOT -->|lê e valida| BLUEPRINTS
    SK_BOOT -->|lê e copia para o projeto gerado| RULES
    SK_BOOT -->|instala| SETTINGS
    SK_BOOT -->|copia verbatim| HOOK
    SK_BOOT -->|copia| SK_UC & SK_DOM & SK_PERS & SK_REST & SK_TEST & SK_DOCKER & SK_MSG & SK_DOCTOR & SK_NF
    SK_BOOT -->|copia| AG_DEV & AG_ARCH

    SK_NF -->|Skill tool, em sequência| SK_UC
    SK_UC --> SK_DOM
    SK_DOM --> SK_PERS
    SK_DOM --> SK_REST
    SK_DOM -.->|se evento pede entrega externa| SK_MSG
    SK_PERS -.->|encadeia, sob demanda| SK_DOCKER
    SK_MSG -.->|encadeia, sob demanda| SK_DOCKER
    SK_TEST -.->|encadeia, sob demanda| SK_DOCKER
    SK_PERS --> SK_TEST
    SK_MSG --> SK_TEST
    SK_REST --> SK_TEST
    SK_NF -->|Agent tool, opcional, após consolidar| AG_DEV
    AG_DEV -->|escreve| SRC["src/** do projeto gerado"]:::out

    SK_TEST -->|Agent tool, modo setup, sem argumento| AG_ARCH
    AG_ARCH -->|escreve| ARCHTEST["ArchitectureTest.java + gate JaCoCo"]:::out

    SK_DOCTOR -->|Bash, sem model| HOOK

    SETTINGS -->|PreToolUse Write / PostToolUse Write,Edit / Stop| HOOK
    HOOK -->|bloqueia ou avisa sobre| SRC

    RULES -.->|citadas por path, nunca copiadas| SK_UC & SK_DOM & SK_PERS & SK_REST & SK_TEST & SK_MSG
    BLUEPRINTS -.->|citado por path| SK_BOOT

    classDef hook fill:#5c1a1a,stroke:#ff6b6b,color:#fff,stroke-width:2px
    classDef skill fill:#123a5c,stroke:#5aa9e6,color:#fff,stroke-width:2px
    classDef agent fill:#3a1a5c,stroke:#b98aff,color:#fff,stroke-width:2px
    classDef rule fill:#1a4d2e,stroke:#5ad17a,color:#fff,stroke-width:2px
    classDef blueprint fill:#5c4a1a,stroke:#e6b45a,color:#fff,stroke-width:2px
    classDef claudemd fill:#333,stroke:#ccc,color:#fff,stroke-width:2px
    classDef out fill:#222,stroke:#999,color:#eee,stroke-dasharray: 4 3
```

## Legenda

| Símbolo/cor | Tipo | O que significa aqui |
|---|---|---|
| 🟥 Vermelho | **Hook** (`settings.json` + `ArchHook.java`) | Enforcement determinístico. Roda sempre que o evento dispara, independente da decisão do modelo. Ver [01-tipos-de-arquivo.md](01-tipos-de-arquivo.md#hook) |
| 🟦 Azul | **Skill** (`SKILL.md`) | Procedimento. Vira parte do repertório do modelo; pode ser invocado por comando ou automaticamente. Ver [§ Skill](01-tipos-de-arquivo.md#skill) |
| 🟪 Roxo | **Agent** (`.claude/agents/*.md`) | Execução isolada — contexto próprio, tools restritas, model diferente. Ver [§ Agent](01-tipos-de-arquivo.md#agent-subagent) |
| 🟩 Verde | **Rule** (`.claude/rules/*.md`) | Norma. Folha do grafo — nunca menciona skill, agent ou comando. Ver [§ Rule](01-tipos-de-arquivo.md#rule) |
| 🟧 Laranja | **Blueprint** (`.claude/blueprints/*/*.yaml`) | Dado declarativo de arquitetura. Folha, como as rules, mas em YAML, não em prosa. Ver [05-blueprints.md](05-blueprints.md) |
| ⬛ Cinza | **`CLAUDE.md`** | Índice e tabela de roteamento. Carrega sempre no início da sessão. Ver [§ CLAUDE.md](01-tipos-de-arquivo.md#claudemd) |
| Linha sólida | Chamada ativa | Uma peça invoca a outra via Skill tool, Agent tool, ou exec direto |
| Linha tracejada | Citação/leitura | Uma peça lê ou cita a outra por path, sem invocá-la |

## Por que a direção das setas importa

A regra estrutural do repositório (`CLAUDE.md` § Invariants 1, 4 e 6) é: **rules nunca
mencionam skills, agents ou comandos**. Se uma rule precisasse invocar algo, ela
deixaria de ser norma e passaria a ser procedimento — e procedimento é skill. É por
isso que, no diagrama, as setas de `rules/` e `blueprints/` são sempre tracejadas e
saem de quem as lê, nunca o contrário.

Da mesma forma, um agent só existe por um dos três motivos válidos (`CLAUDE.md` §
Invariant 5): preservar contexto, restringir tools, ou trocar de modelo. `project-initializer`
e `java-spring-boot-developer` atendem aos três motivos simultaneamente;
`archunit-installer` atende só ao primeiro (preservar contexto — o modo setup da
`test-architect` não tem entrevista, e isolar o `curl`/`./mvnw` que ele roda evita que
esse ruído fique permanente na conversa principal). Um motivo já basta pelo Invariant 5.
Cada agent documenta o próprio motivo na seção `## Why this is an agent` (ou `## Why
this is Form 3`).

## Os três comandos, em uma frase cada

| Comando | O que faz | Detalhes |
|---|---|---|
| `/init-project` | Interview → escolhe blueprint → gera estrutura completa do projeto Spring Boot, sem código de negócio | [02-init-project.md](02-init-project.md) |
| `/new-feature UC-NNN-slug` | Orquestra 5 skills de design (caso de uso → domínio → persistência → REST → testes) em um spec único, e opcionalmente aciona o executor | [03-new-feature.md](03-new-feature.md) |
| `/arch-doctor` | Diagnostica hooks ativos, boundaries carregadas, wrapper do Maven, `java` no PATH | [04-arch-doctor.md](04-arch-doctor.md) |
