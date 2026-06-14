# ADR 0005 - SQS standard e idempotencia na abertura de contas

Status: Aceito

## Contexto

Mensagens de abertura de conta podem ser entregues mais de uma vez em filas standard.

## Decisao

Consumir a fila standard e usar `accounts.id` como chave idempotente para abertura de contas.

## Consequencias

A aplicacao tolera duplicidade sem criar contas repetidas. Ordenacao estrita e DLQ real ficam como evolucoes de infraestrutura.
