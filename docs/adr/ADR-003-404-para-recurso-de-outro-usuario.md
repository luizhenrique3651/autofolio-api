# ADR-003: Recurso de outro usuário responde 404, igual ao inexistente

**Status:** Accepted
**Date:** 2026-11-09
**Deciders:** Luiz Henrique Albuquerque Coelho (dono do produto e do repositório)
**Relacionada a:** [ADR-001](ADR-001-padroes-obrigatorios-de-engenharia.md) §2.4 e §2.5

## Context

Os cards AF-018 e AF-019 pedem **403** quando o veículo (ou outro recurso) existe mas pertence a outro vendedor. A ADR-001 §2.5 já previa 404 "recurso inexistente ou de outro usuário" (`EntityNotFoundException`). Os endpoints por id começam na AF-018 e devem nascer com `findByIdAndUserId`. Era preciso decidir se os próximos endpoints (`GET/PUT/PATCH/DELETE /vehicles/{id}`, vendas, clientes, fluxo de caixa) seguem o card (403) ou o que já está implementado (404).

Os ids são UUID, mas aparecem em URLs, logs de front-end e links compartilhados; ainda assim, a API não deve confirmar a quem não é dono que um id existe.

## Decision

**Todo acesso a recurso de outro usuário responde 404**, com a mesma resposta (corpo e tempo) do recurso inexistente. A consulta filtra por id e `user_id` na mesma query, sem uma busca separada que distinga os dois casos. O 403 fica reservado a falta de permissão por papel (hoje não existe papel além do vendedor). Os cards AF-018/019 são lidos com 404 onde dizem 403, e o teste "acesso a veículo de outro usuário" espera `EntityNotFoundException`.

## Options Considered

### Opção A: 403 para recurso de outro usuário (texto do roadmap)

| Dimensão | Avaliação |
|---|---|
| Complexidade | Maior: exige buscar por id e comparar o dono, ou uma segunda consulta |
| Segurança | Vaza a existência do id para quem não é dono (enumeração) |
| Aderência ao roadmap | Total |
| Clareza para o cliente | Alta: o front sabe que o recurso existe, mas não é dele |

### Opção B: 404 para recurso de outro usuário (escolhida)

| Dimensão | Avaliação |
|---|---|
| Complexidade | Menor: uma query `findByIdAndUserId`, sem ramo extra |
| Segurança | Não revela existência; coerente com o login (mesmo 401 para e-mail inexistente e senha errada, §2.4) |
| Aderência ao roadmap | Desvio consciente dos cards AF-018/019 |
| Clareza para o cliente | Menor: não distingue "não existe" de "não é seu" (na prática irrelevante para o front) |

## Trade-off Analysis

A Opção A só traz ao cliente uma distinção que ele não usa: um vendedor nunca navega para o id de outro vendedor em operação normal, e o 403 não habilita nenhuma ação diferente do 404. Em troca, ela abre um canal de enumeração de ids e obriga um ramo extra de código e de teste. A Opção B custa o desvio do roadmap, registrado aqui e em "Desvios conscientes do roadmap" da ADR-001, e uma mensagem de erro um pouco menos específica.

## Consequences

- **Fica mais fácil:** o repositório expõe só consultas já escopadas por dono; esquecer a checagem de dono passa a ser difícil.
- **Fica mais difícil:** diagnosticar, pelo cliente, "o id está errado" versus "o id é de outro usuário". Use `requestId` e o log (que registra `userId` e o id pedido).
- **Revisitar** se surgir papel administrativo ou compartilhamento de recursos entre usuários (aí o 403 volta a fazer sentido).
- Os cards AF-018/019 devem ser implementados com 404; o maestro e os revisores não devem tratá-lo como violação do critério de aceite.
