# Lessons learned — pacote de persistência organizado por agregado (demo-clean-arch-single-module)

Origem: pedido do usuário pra revisar a organização de
`infrastructure.persistence` no projeto de demo `demo-clean-arch-single-module`
(blueprint `clean-architecture-single-module`) — 13 classes de 3 agregados diferentes
(`Account`, `Customer`, `IdempotencyKey`) todas soltas no mesmo pacote, sem nenhuma
separação. Sugeri reorganizar em subpacote por agregado (SRP a nível de pacote), o
usuário aprovou e pediu pra aplicar. Fiz o move manualmente (13 arquivos main + 3 de
teste), com `./mvnw -q -o clean test-compile`, `test` e `verify` completo (Checkstyle +
ArchUnit + IT reais contra Testcontainers Postgres + gate de coverage) verdes ao final.
O resultado só existe hoje neste projeto de demo — nada no meta-repo gera esse layout
por padrão, e é isso que este registro pede pra corrigir.

---

## 1. Os exemplares de `persistence-architect` geram um pacote único e flat por projeto, não um subpacote por agregado — SRP violado desde a geração

**Onde:** `@.claude/rules/persistence.md` § Boundary (não diz nada sobre layout de
pacote — só "um repository por agregado", que é sobre forma da query, não sobre onde o
arquivo mora); `.claude/skills/persistence-architect/templates/RepositoryAdapter.java.example:19`
(`package com.example.demo.adapter.out.persistence;` — flat, sem subpacote por
agregado); o mesmo padrão em `JpaEntity.java.example` e
`SpringDataRepository.java.example`. O projeto de demo, gerado a partir desses
exemplares, chegou com as 13 classes dos 3 agregados soltas em
`infrastructure/persistence/` até esta sessão.

**O que ocorreu:** o usuário percebeu o pacote lotado e pediu uma sugestão de
organização com foco em SOLID. A reorganização proposta e aplicada foi: subpacote por
agregado —`persistence/account/`, `persistence/customer/`, `persistence/idempotency/` —
espelhando o que o lado de `test/` já fazia (`test/.../persistence/AccountRepositoryJpaAdapterIT.java`
etc. também flat, e também reorganizados junto). Nada quebrou do lado do ArchUnit:
`ArchitectureTest.java.example:122` já usa glob com dois pontos
(`..adapter.out.persistence..`), então tolera subpacotes sem qualquer ajuste de regra.

**Lições:**

- O exemplar devia gerar o subpacote desde o início — um por agregado raiz que o
  modelo de domínio declara — em vez de deixar pra um refactor manual descobrir a
  mesma necessidade a cada projeto novo com mais de um agregado.
- `persistence.md` § Boundary é o dono certo pra essa norma (ela é sobre onde o
  adapter mora, não sobre nomenclatura nem sobre a query) — falta uma linha do tipo
  "um subpacote por agregado dentro do adapter de persistência, espelhado em `test/`".
  Sem isso, cada `/init-project` novo com blueprint `clean-architecture-single-module`
  reproduz o mesmo pacote flat e precisa do mesmo refactor manual de novo.
- O glob com dois pontos em `ArchitectureTest.java.example:122` já é à prova de
  subpacote — vale um comentário ali registrando que isso é intencional, pra ninguém
  "simplificar" pra um glob de um nível só mais tarde e quebrar essa tolerância sem
  perceber.

---

## 2. Separar por agregado expõe uma dependência cross-agregado legítima — visibilidade package-private por padrão não tem exceção documentada

**Onde:** `.claude/skills/persistence-architect/templates/RepositoryAdapter.java.example:32`
(`class UserRepositoryAdapter` — package-private por convenção, nenhuma regra diz
quando isso pode virar `public`); `AccountRepositoryJpaAdapterIT` no projeto de demo —
precisa de `CustomerRepositoryJpaAdapter`/`CustomerPersistenceMapper` pra popular a
linha de customer referenciada antes de inserir a account (FK `accounts.customer_id`).

**O que ocorreu:** depois do split, a compilação de teste falhou —
`cannot find symbol: class CustomerRepositoryJpaAdapter` dentro de
`AccountRepositoryJpaAdapterIT`, agora em subpacote diferente. Corrigido abrindo só as
duas classes realmente consumidas por fora do próprio agregado
(`CustomerRepositoryJpaAdapter` e `CustomerPersistenceMapper`) pra `public`, e trocando
o tipo do campo no teste de `Account` pelo port da aplicação
(`CustomerRepository`, que a classe já implementa) em vez do adapter concreto —
depender da abstração mesmo dentro do setup de um teste de integração.

**Lições:**

- Nenhuma regra hoje diz o critério pra abrir uma dessas classes normalmente
  package-private pra `public`. Vale uma linha em `persistence.md` ou
  `code-quality.md`: padrão é package-private; só vira `public` a classe específica
  que um **outro** agregado genuinamente precisa, e a preferência é depender do port
  em vez do adapter concreto sempre que o port já cobrir a necessidade.
- Esse gap é consequência direta e mecânica do gap 1: só aparece quando o subpacote
  por agregado é o padrão. Quem corrigir o gap 1 (exemplares de
  `persistence-architect` e/ou o passo de geração do `java-spring-boot-developer`)
  devia documentar essa bifurcação de visibilidade na mesma passada — senão todo
  projeto gerado com mais de um agregado precisando de fixture cross-agregado em teste
  bate nesse erro de compilação do zero, sem nenhuma regra pra apontar o caminho.
