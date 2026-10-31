# ADR-002: Estilo de código — Google Java Style com indentação de 4 espaços, verificado por Checkstyle

**Status:** Accepted
**Date:** 2026-10-31
**Deciders:** Luiz Henrique Albuquerque Coelho (dono do produto e do repositório)
**Relacionada a:** [ADR-001](ADR-001-padroes-obrigatorios-de-engenharia.md) §7.3

## Context

A Definition of Done do roadmap (seção 2.3) exige "nenhuma violação de lint/formatação (mvn checkstyle)", e a skill de arquitetura do projeto adota o **Google Java Style Guide**. Mesmo assim, até a Sprint 1 o `pom.xml` não tinha plugin de Checkstyle nem conjunto de regras:

- a revisão de warnings rodava o Checkstyle avulso com as regras padrão (`sun_checks`, 80 colunas), que não refletem o estilo do projeto;
- as violações cresceram de 373 (AF-011) para 478 (AF-012) sem bloquear nada;
- cada revisor julgava estilo pelo próprio critério, o que gerava achados de "ordem de import" e "linha longa" task após task.

O código existente foi escrito com **indentação de 4 espaços**, enquanto o Google Java Style define **2 espaços** (4 na continuação de linha).

## Decision

1. **O estilo de código do projeto é o Google Java Style Guide**, verificado pelo `maven-checkstyle-plugin` com um conjunto de regras versionado no repositório, derivado do `google_checks.xml` oficial.
2. **Desvio único: indentação de 4 espaços.** No arquivo de regras, apenas o módulo `Indentation` muda:

   | Propriedade | Google oficial | AutoFolio |
   |---|---|---|
   | `basicOffset` | 2 | **4** |
   | `braceAdjustment` | 2 | **0** |
   | `caseIndent` | 2 | **4** |
   | `throwsIndent` | 4 | **8** |
   | `lineWrappingIndentation` | 4 | **8** |
   | `arrayInitIndent` | 2 | **4** |

   Todas as demais regras do Google ficam como estão: limite de 100 colunas, ordem e proibição de wildcard em imports, convenções de nomes, formato de Javadoc etc.
3. **O check bloqueia o build:** roda na fase `validate`, cobre `src/main` e `src/test` e falha em qualquer violação (`violationSeverity=warning`, `failOnViolation=true`).
4. **Supressões** só por arquivo `suppressions.xml` versionado, cada uma com justificativa. Nunca com `@SuppressWarnings("checkstyle:...")` espalhado sem explicação.

## Options Considered

### Opção A: Google Java Style puro (2 espaços)

| Dimensão | Avaliação |
|---|---|
| Complexidade | Baixa: usa o `google_checks.xml` sem alterações |
| Custo | Alto agora: reformata 100% dos arquivos |
| Aderência ao guia | Total |
| Impacto no histórico | Alto: `git blame` passa a apontar o commit de reformatação em quase toda linha |

**Prós:** fidelidade total ao guia; o formatador oficial (google-java-format) poderia ser usado sem configuração.
**Contras:** diff gigante, sem valor funcional, misturado a uma task de qualidade; atrapalha revisão e histórico; diverge do padrão de 4 espaços da IDE e do resto do ecossistema Spring.

### Opção B: Google Java Style com 4 espaços (escolhida)

| Dimensão | Avaliação |
|---|---|
| Complexidade | Baixa: um módulo ajustado num arquivo versionado |
| Custo | Baixo: corrige só as violações reais |
| Aderência ao guia | Alta: todas as regras, exceto a medida da indentação |
| Impacto no histórico | Baixo |

**Prós:** mantém o código como está, com diff pequeno e focado (imports, linhas longas, nomes, Javadoc); 4 espaços é o padrão do IntelliJ e da maioria dos projetos Spring.
**Contras:** não dá para usar o google-java-format sem ajuste (ele força 2 espaços); exige manter o arquivo de regras próprio em atualizações de versão do Checkstyle.

### Opção C: Sun Checks (padrão do Checkstyle)

| Dimensão | Avaliação |
|---|---|
| Complexidade | Baixa |
| Custo | Médio |
| Aderência ao guia | Nenhuma: não é o guia adotado pelo projeto |
| Impacto no histórico | Médio |

**Prós:** já vem pronto no plugin.
**Contras:** limite de 80 colunas e regras de Javadoc obrigatório em tudo, que geram ruído; contradiz a skill de arquitetura e a escolha do Google Style.

## Trade-off Analysis

A diferença entre A e B é só a *medida* da indentação. A *regra* (indentação consistente, blocos e continuações alinhados) continua verificada. O ganho de aderência total ao guia não paga o custo de reformatar o projeto inteiro e perder a rastreabilidade do `git blame`. A Opção C foi descartada por não ser o estilo adotado.

## Consequences

- **Fica mais fácil:** revisões deixam de discutir estilo, porque o build decide. Achados de import, linha longa e nome somem das revisões humanas e de agentes.
- **Fica mais difícil:** commit com violação de estilo não passa no build. Por isso é preciso rodar `mvn checkstyle:check` (ou `mvn validate`) antes de entregar.
- **Configuração da IDE:** o IntelliJ deve usar 4 espaços (indentação) e 8 (continuação), limite de 100 colunas e a ordem de imports do Google (estáticos primeiro, uma linha em branco entre o grupo estático e os demais), para não gerar violações ao formatar. Uma alternativa é importar o `checkstyle.xml` do projeto pelo plugin CheckStyle-IDEA.
- **Formatador automático:** o google-java-format não serve como está. Se for adotado um formatador (ex.: Spotless), ele precisa ser configurado com as mesmas regras desta ADR.
- **A revisitar:** ao atualizar a versão do Checkstyle, comparar o novo `google_checks.xml` oficial com o arquivo do projeto e reaplicar só o desvio de indentação.
