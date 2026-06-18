# ADR 0003 - Idempotência por transactionId

Status: Aceito

## Contexto

Clientes podem reenviar a mesma autorização por timeout, retry ou falha de rede.

## Decisão

Usar o `transactionId` da URL como chave idempotente e persisti-lo como identificador da transação.

## Consequências

Reenvios com o mesmo payload retornam o resultado já registrado sem reaplicar crédito ou débito. Reenvios com payload diferente retornam conflito.
