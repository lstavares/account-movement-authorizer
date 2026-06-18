# ADR 0005 - SQS standard e idempotência na abertura de contas

Status: Aceito

## Contexto

Mensagens de abertura de conta podem ser entregues mais de uma vez em filas standard.

## Decisão

Consumir a fila standard e usar `accounts.id` como chave idempotente para abertura de contas.

## Consequências

A aplicação tolera duplicidade sem criar contas repetidas. Ordenação estrita e DLQ real ficam como evoluções de infraestrutura.
