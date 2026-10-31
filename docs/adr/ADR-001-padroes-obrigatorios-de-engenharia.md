# ADR-001: Padrões obrigatórios de engenharia da AutoFolio API

**Status:** Accepted
**Date:** 2026-10-31
**Deciders:** Luiz Henrique Albuquerque Coelho (dono do produto e do repositório)
**Escopo:** todo código, configuração e teste da `autofolio-api` a partir da Sprint 1 (AF-009 em diante)

## Context

A Sprint 1 (AF-009 a AF-012: login, refresh, recuperação e redefinição de senha) foi implementada por uma bancada de agentes: desenvolvedor, revisores de arquitetura, performance e manutenibilidade, tech lead, revisores de erros e de warnings, e QA. Durante essas tasks, aconteceram problemas que se repetiram:

- **Erros visíveis só na IDE:** um bean que não subia sem profile e warnings de inspeção chegaram ao fim do ciclo sem serem detectados. Os testes passavam porque usavam mocks.
- **Mudanças sem revisão:** correções feitas depois da aprovação (por bug do QA) chegaram a ir para commit sem passar pelo tech lead.
- **Diagnóstico tratado como opcional:** achados sobre log e rastreamento de erro foram classificados como "observação" e quase ficaram de fora.
- **Contrato de erro divergente:** havia dois formatos de erro (ProblemDetail próprio e o padrão do Spring, em inglês).
- **Commit incompleto:** arquivos novos ficaram fora do commit, e os testes não compilavam a partir daquele ponto do histórico.

A Definition of Done do roadmap (seção 2.3) já pedia testes, lint e Swagger, mas não cobria esses pontos. Esta ADR consolida o que foi decidido e passa a valer como regra.

## Decision

As regras abaixo são **obrigatórias**. Revisões devem tratar qualquer violação como correção obrigatória, nunca como opcional.

### 1. Logs e rastreabilidade de erros em produção

1. **Nenhum achado sobre log, nível, stacktrace, correlação ou dado pessoal em log é opcional.** Entra sempre como correção obrigatória.
2. **Toda requisição tem um `X-Request-Id`.** O `RequestIdFilter` aceita o id do cliente (`[A-Za-z0-9._-]{1,64}`) ou gera um UUID. O id vai para o MDC (`requestId`) e volta no header da resposta. O CORS libera o envio e a leitura desse header.
3. **O MDC chega ao `@Async`** via `TaskDecorator`. Código assíncrono novo deve usar o executor configurado.
4. **O padrão de log inclui `%X{requestId}`** em todos os profiles.
5. **Todo `ProblemDetail` traz `requestId`**, além de `timestamp`.
6. **Log nunca contém token, hash completo, senha ou e-mail.** Identifique pelo `userId`. Para token, use no máximo um prefixo do hash.
7. **Falha esperada de dependência externa** (ex.: SMTP) sai em `ERROR`, com `userId`, tipo da exceção e tipo da causa raiz, sem a mensagem quando ela puder conter dado pessoal. A stacktrace completa vai para `DEBUG`.
8. **Falha inesperada** sai em `ERROR` com stacktrace, **uma única vez**. Quem relança não loga a stacktrace; ela fica no handler global.
9. **O catch-all 500** loga o erro e relança `AccessDeniedException` e `AuthenticationException`, para não transformá-las em 500.
10. **`orElseThrow()` sem argumento é proibido** em código de produção. A exceção precisa explicar o que faltou.

### 2. Contrato de erro da API

1. **Todo erro responde `application/problem+json`** (RFC 7807), montado pela factory `ProblemDetails`, com `type` da autofolio, `title` em português, `status`, `timestamp` e `requestId`.
2. **O `GlobalExceptionHandler` estende `ResponseEntityExceptionHandler`.** Assim, 404, 405, 406, 415 e demais erros do Spring MVC seguem o mesmo contrato.
3. **400 de validação** traz a propriedade `errors`. **JSON malformado** dá 400 com mensagem genérica, sem detalhes do parser.
4. **Fluxos sensíveis não revelam existência de conta:**
   - mesmo 401 para e-mail inexistente e senha errada, inclusive no tempo de resposta;
   - `forgot-password` responde sempre 202;
   - mesma resposta para token inexistente, expirado ou revogado.

### 3. Segurança e autenticação

1. **JWT de acesso** HS256 via `spring-boot-starter-oauth2-resource-server` (Nimbus), sem filtro JWT próprio. Validade de 1 hora. A chave tem no mínimo 32 bytes.
2. **Rotas públicas ignoram Bearer inválido** (`BearerTokenResolver`). Um token expirado no header não pode derrubar login, refresh ou portfólio.
3. **Refresh token opaco** (UUID), validade de 30 dias, persistido, não rotacionado, com vários dispositivos por usuário.
4. **Token de uso único** (redefinição de senha) é armazenado **apenas como hash SHA-256** e consumido de forma **atômica**, por `UPDATE` condicional na mesma transação da operação.
5. **Senha:** BCrypt força 12 e a política `@StrongPassword` (8 a 72 bytes, 1 número, 1 maiúscula). A política vale igual no cadastro e na redefinição: o cadastro nunca exige menos que a redefinição.
6. **Redefinir a senha** revoga todos os refresh tokens e os demais tokens de reset pendentes do usuário.

### 4. Performance e I/O

1. **I/O lento** (SMTP, chamadas externas) roda fora da requisição (`@Async` sobre virtual threads), **sem transação nem conexão de banco aberta** durante a chamada.
2. **Todo cliente de rede tem timeout explícito.** SMTP: 5 segundos para conexão, leitura e escrita.
3. **`spring.jpa.open-in-view: false`.** Operações de CPU caras, como BCrypt, não seguram conexão do pool.
4. **Health check não depende de recurso não crítico.** `management.health.mail.enabled: false`.
5. **Operações de limpeza** em endpoints públicos são restritas ao escopo do usuário e cobertas por índice.

### 5. Configuração

1. **A API sobe sem profile.** Todo bean obrigatório precisa existir na configuração base, com defaults de dev quando fizer sentido. O profile `prod` exige credenciais sem default (fail-fast).
2. **Toda variável lida do `.env`** é mapeada explicitamente num yml (`chave: ${VARIAVEL:default}`). O `dotenv-java` grava System properties com o nome da variável, que não se ligam automaticamente a propriedades com ponto.
3. **Cada configuração tem o default em um só lugar** (yml ou constante), nunca duplicado em `@Value`.
4. **O `.env.example` lista todas as variáveis** usadas por qualquer profile, sem valores reais. O `.env` nunca é commitado nem editado por agentes.
5. **Dependência nova só com aprovação explícita do dono do repositório.**
6. **Toda mudança de schema é uma migration Flyway nova.** Migrations aplicadas nunca são editadas. O Hibernate roda com `ddl-auto: validate`.

### 6. Testes

1. **Testes de integração** usam Testcontainers sobre a base comum `AbstractPostgresIT` (PostgreSQL singleton). Nunca usam o banco local.
2. **O `ApplicationContextIT`** sobe o contexto completo sem profile e sem mocks e deve continuar passando. Ele cobre o que mocks escondem, como beans ausentes.
3. **Testes nunca enviam e-mail nem chamam serviço externo real.** Exceção conhecida: `CloudinaryIntegrationTest`, a isolar.
4. **Toda decisão de log e segurança tem teste:** formato do log sem dado pessoal, ramo de cada exceção, concorrência no consumo de tokens e `requestId` no corpo igual ao do header.
5. **`mvn test` e `mvn test -Dtest='*IT'`** (todos juntos) passam antes de qualquer revisão.

### 7. Qualidade estática

1. **Inspeções do IntelliJ** (Qodana Community, perfil "Project Default") rodam em toda task. Todo achado é tratado, exceto a regra `unused`, que é falso positivo sem o plugin Spring. Inspeções exclusivas do Ultimate (autowire, SQL) são cobertas pelo `ApplicationContextIT` e pela configuração do Data Source na IDE.
2. **Compilação com `-Xlint:all`** não tem warnings novos.
3. **Checkstyle com `google_checks`** é o conjunto de regras do projeto. Até a task de configuração do plugin, as violações não bloqueiam; depois dela, a DoD exige zero.
4. **Warnings** são avaliados um a um pelo tech lead (corrigir, adiar como débito ou ignorar com justificativa).

### 8. Processo de entrega

1. **Fluxo de toda task:**
   1. desenvolvimento;
   2. revisão de **erros** no projeto inteiro;
   3. revisões paralelas de **arquitetura**, **performance** e **manutenibilidade** (o diff avaliado contra a codebase inteira);
   4. revisão geral do **tech lead**;
   5. **warnings**, com laço tech lead ↔ desenvolvedor;
   6. **QA funcional** contra os critérios do card e esta ADR;
   7. **commit pelo dono do repositório**.
2. **Mudança de produção feita depois de uma aprovação** (inclusive correção de bug do QA) volta ao ciclo de revisão e ao QA. Exceção: correções de warnings, que o tech lead reavalia diretamente. Mudanças só em `src/test` ou sem efeito em runtime não voltam, mas são informadas.
3. **Agentes nunca fazem commit, push, reset ou checkout de arquivos.** A cada task, o dono do repositório commita.
4. **Antes do commit**, `git status` não pode ter arquivo da task fora do stage (use `git add -A` e confira). O commit precisa compilar e passar nos testes sozinho.
5. **A mensagem de commit segue Conventional Commits** com o id do card: `feat(AF-XXX): ...`.

## Options Considered

### Opção A: Regras implícitas, decididas task a task (situação até a AF-011)

| Dimensão | Avaliação |
|---|---|
| Complexidade | Baixa no início, crescente com o tempo |
| Custo | Alto: retrabalho e rodadas extras de revisão |
| Escalabilidade | Ruim: cada revisor ou agente novo reaprende as regras |
| Familiaridade | Depende de quem lembra do histórico |

**Prós:** nenhuma burocracia inicial.
**Contras:** regras se perdem entre tasks e contextos limpos; achados importantes viram "opcional"; revisões discordam entre si; o histórico de decisões fica espalhado em conversas.

### Opção B: Só ampliar a Definition of Done do roadmap

| Dimensão | Avaliação |
|---|---|
| Complexidade | Baixa |
| Custo | Baixo |
| Escalabilidade | Média |
| Familiaridade | Alta |

**Prós:** um único lugar já conhecido.
**Contras:** a DoD é um checklist sem o porquê. Não registra decisões técnicas (bibliotecas, armazenamento de token, contrato de erro), e o roadmap está num `.docx` fora do controle de versão (`doc/` está no `.gitignore`).

### Opção C: ADR versionada no repositório com as regras obrigatórias (escolhida)

| Dimensão | Avaliação |
|---|---|
| Complexidade | Média |
| Custo | Baixo: um documento mantido junto do código |
| Escalabilidade | Boa: base de referência para revisores, QA e novos agentes |
| Familiaridade | Padrão de mercado (ADR) |

**Prós:** versionada com o código; traz o porquê de cada regra; pode ser citada em revisões ("viola ADR-001 §1.6"); decisões futuras entram como novas ADRs.
**Contras:** precisa ser mantida. Uma regra desatualizada aqui engana mais do que ajuda.

## Trade-off Analysis

A Opção C troca um pequeno custo de manutenção por previsibilidade. Em tasks já entregues, o custo de não ter regras explícitas foi alto: a AF-010 e a AF-011 precisaram de várias rodadas extras de revisão e teste por causa de pontos (contrato de erro, log, bean sem profile) que regras escritas teriam evitado na primeira passada. A DoD (Opção B) continua valendo como checklist de saída e é complementada por esta ADR, que é a fonte das regras técnicas.

## Consequences

- **Fica mais fácil:** revisar com critério objetivo, recusar opcionais que são obrigatórios, integrar um revisor ou agente novo e explicar decisões ao front-end. Exemplos para o front: `requestId` no corpo, `X-Request-Id` no header e política de senha no cadastro.
- **Fica mais difícil:** atalhos. Toda correção de produção depois de uma aprovação custa uma nova rodada de revisão.
- **Precisa ser revisitado:**
  - esta ADR, a cada nova decisão transversal (que deve gerar ADR própria ou uma emenda aqui);
  - a regra de checkstyle, depois da task de configuração;
  - a exceção do `CloudinaryIntegrationTest`.

## Action Items

1. [ ] Configurar o plugin de checkstyle com `google_checks` e zerar as violações (task já na fila).
2. [ ] Configurar o `maven-failsafe-plugin` para os `*IT` rodarem em `mvn verify` (pré-requisito do CI na AF-043).
3. [ ] Configurar `-javaagent` do Mockito no surefire.
4. [ ] Isolar o `CloudinaryIntegrationTest` da rede externa (mock ou tag separada).
5. [ ] Guardar o refresh token como hash SHA-256 (card próprio; alinha com §3.4).
6. [ ] Índice único parcial para tokens de reset pendentes por usuário (forgot-password simultâneo).
7. [ ] Limpeza global agendada (`@Scheduled`) de tokens expirados, com índice em `expires_at`.
8. [ ] Invalidar o access token após a redefinição de senha (blacklist ou versionamento de token).
9. [ ] Liberar `/actuator/health` para monitoramento externo (AF-042/AF-046).
10. [ ] `detail` dos erros do Spring em português via `messages.properties` (`problemDetail.<FQCN>`).
11. [ ] Tipar o `Map` cru do `CloudinaryConfig`.
12. [ ] Rate limit no forgot-password e limites do executor `@Async` (hardening).
13. [ ] Padronizar a configuração tipada: migrar `JwtConfig` e `CloudinaryConfig` de `@Value` para `@ConfigurationProperties` com validação de obrigatoriedade e valor em branco, como já faz o `FrontendProperties`.
