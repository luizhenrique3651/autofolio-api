# Backlog técnico

Pendências, hardening e débitos técnicos da AutoFolio API que não pertencem a um card do roadmap em andamento. Origem: Action Items da [ADR-001](adr/ADR-001-padroes-obrigatorios-de-engenharia.md) e a avaliação geral do tech lead (chore de 2026-11-09).

**Prioridade:** Alta (bloqueia ou expõe risco real), Média (qualidade/robustez), Baixa (hardening ou conveniência).
**Status:** Aberto, Em andamento, Feito.

## Pendências abertas

### Segurança

| # | Item | Prioridade | Sprint / card sugerido | Status |
|---|---|---|---|---|
| 1 | Guardar o refresh token como hash SHA-256 (alinha com ADR-001 §3.4; hoje é UUID em texto na tabela) | Alta | Sprint 2/3, card de segurança próprio | Aberto |
| 2 | Invalidar o access token após a redefinição de senha (blacklist ou versionamento de token) | Média | Sprint 3 | Aberto |
| 3 | Rate limit no forgot-password e limites do executor `@Async` | Média | Hardening, antes do go-live | Aberto |
| 4 | Rate limit nos endpoints que confirmam senha (`DELETE /users/me`, `PATCH /users/me/password`), junto com o do login | Média | Hardening, antes do go-live | Aberto |
| 5 | Índice único parcial para tokens de reset pendentes por usuário (forgot-password simultâneo) | Baixa | Sprint 3 | Aberto |
| 6 | Limpeza global agendada (`@Scheduled`) de tokens expirados, com índice em `expires_at` | Baixa | Sprint 3 | Aberto |
| 7 | `allowCredentials(true)` no CORS (`SecurityConfig`) sem uso real: a API é stateless por Bearer, sem cookie; remover ou justificar | Baixa | Hardening | Aberto |
| 8 | Endpoint de reativação de conta e anonimização LGPD | Média | Sprint 4 (clientes/LGPD) | Aberto |

### Build e qualidade

| # | Item | Prioridade | Sprint / card sugerido | Status |
|---|---|---|---|---|
| 9 | Chore de build: `maven-failsafe-plugin` (os `*IT` rodarem em `mvn verify`), agente do Mockito (`-javaagent` no surefire, usando `@{argLine}` para não sobrescrever o agente do JaCoCo), `@ConfigurationProperties` validado no `JwtConfig` e no `CloudinaryConfig` (como o `FrontendProperties`), `maven-enforcer-plugin` com Java `[21,22)` (o Maven do Homebrew roda em Java 26 e quebra o Byte Buddy) e `-Xlint:all` + `showWarnings` no compilador (ADR-001 §7.2) | Alta | Antes da AF-043 (CI) | Aberto |
| 9b | ITs sensíveis a carga do host: `ForgotPasswordQaIT` (limite fixo de 1 s em `doesNotWaitForSmtp` e `await()` com timeout padrão de 10 s) e `ResetPasswordIT.newRequestInvalidatesPreviousAndCleansUp` (`await()`) falharam com o host em load ~7 a 15 e o Docker lento, sem erro funcional (QA da chore pré-AF-017). Definir timeout explícito do Awaitility (ex.: `Awaitility.setDefaultTimeout` no `AbstractPostgresIT`) e trocar o limite de 1 s por verificação que não dependa de relógio (ex.: SMTP bloqueado por latch) | Média | Antes da AF-043 (CI) | Aberto |
| 10 | Índices redundantes `idx_users_email` e `idx_users_slug` (as colunas já têm índice único) | Baixa | Próxima migration de índices | Aberto |
| 11 | Migration V7: `NOT NULL` em `vehicles.status` (a V1 só tem `DEFAULT 'DRAFT'`) | Média | V7 da AF-017 | Aberto |
| 11b | `vehicles.deleted_at` para o soft delete da AF-018, com a listagem da AF-017 já compatível (filtra `deleted_at IS NULL`) | Média | V7 da AF-017 / AF-018 | Aberto |

### Arquitetura e módulos

| # | Item | Prioridade | Sprint / card sugerido | Status |
|---|---|---|---|---|
| 12 | Mover entidades e repositórios de usuário/perfil/tokens de `auth` para o pacote `user` | Média | Sprint 5, antes do portfólio | Aberto |
| 13 | Storage genérico em `shared/media` (hoje `ProfilePhotoStorage` é específico do perfil) e limites multipart por endpoint | Média | AF-020 (fotos de veículo) | Aberto |
| 14 | `vehicle_photos` sem `public_id` do Cloudinary e sem `UNIQUE(vehicle_id, position)`; migration necessária para apagar a mídia e garantir a ordem | Alta | AF-020 | Aberto |
| 15 | `cashflow_category`: o enum do banco usa `SALE`, o card da AF-022 pede `VENDA`; alinhar antes de implementar | Média | AF-022 | Aberto |
| 16 | Sprint 3: o portfólio público deve filtrar `active=true` | Média | Sprint 3 | Aberto |
| 16b | Índice `(user_id, status, created_at)` em `vehicles` para a listagem do vendedor | Média | V7 da AF-017 | Aberto |

### Infra e deploy

| # | Item | Prioridade | Sprint / card sugerido | Status |
|---|---|---|---|---|
| 17 | Liberar `/actuator/health` para monitoramento externo | Média | AF-042 / AF-046 | Aberto |
| 18 | Limitar o corpo das requisições no Nginx (`client_max_body_size` ~6 MB): o `server.tomcat.max-swallow-size` de 10 MB drena até 10 MB de requisição não autenticada | Média | AF-042 | Aberto |
| 19 | `server.forward-headers-strategy` (framework ou native), para `Location` e URLs geradas usarem host e scheme públicos atrás do Nginx/TLS | Média | AF-042 | Aberto |
| 20 | Retry com backoff no upload ao Cloudinary (Spring Retry). Dependência nova: aguarda aprovação do dono (ADR-001 §5.5) | Baixa | Sprint 3 (fotos) | Aberto |
| 21 | Limpeza das fotos antigas de perfil no Cloudinary (o upload novo não apaga a anterior, conforme a AF-014) | Baixa | Sprint 3 (fotos) | Aberto |
| 22 | Limitar uploads concorrentes por usuário | Baixa | Hardening | Aberto |

### Processo

| # | Item | Prioridade | Sprint / card sugerido | Status |
|---|---|---|---|---|
| 23 | Retomar o fluxo `feature/AF-XXX` → PR → `develop` (hoje as tasks são commitadas direto na `develop`) | Média | Próxima task | Aberto |
| 24 | Branch de produção `master` x `main` do roadmap (AF-001): decidir renomear ou manter | Baixa | Antes da AF-043 (CI) | Aberto |

## Histórico (feito)

| # | Item | Resolução |
|---|---|---|
| H1 | Checkstyle com `google_checks` e zero violações | Plugin no build, [ADR-002](adr/ADR-002-estilo-de-codigo-checkstyle-google-4-espacos.md) |
| H2 | Isolar o `CloudinaryIntegrationTest` da rede externa | `@Tag("external")` + profile `external-tests` |
| H3 | `detail` dos erros do Spring em português | `messages.properties` (`problemDetail.<FQCN>`), AF-015 |
| H4 | Tipar o `Map` cru do `CloudinaryConfig` | Cast isolado com `@SuppressWarnings` pontual, AF-014 |
| H5 | Centralizar a checagem de conta ativa | `ActiveAccountValidator` no `JwtDecoder`, AF-016 |
| H6 | Logs de negócio de cadastro, login recusado, refresh recusado e corrida do 1º upload (ADR-001 §1) | Chore da avaliação geral |
| H7 | `ddl-auto: validate` na configuração base (valida entidade x migration também nos ITs) | Chore da avaliação geral |
| H8 | JaCoCo com relatório no `mvn test` e check de 80% (linhas e branches) nos `*Service` no `mvn verify` | Chore da avaliação geral |
