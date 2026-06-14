# ADR 0003 - Idempotencia por transactionId

Status: Aceito

## Contexto

Clientes podem reenviar a mesma autorizacao por timeout, retry ou falha de rede.

## Decisao

Usar o `transactionId` da URL como chave idempotente e persisti-lo como identificador da transacao.

## Consequencias

Reenvios com o mesmo payload retornam o resultado ja registrado sem reaplicar credito ou debito. Reenvios com payload diferente retornam conflito.
