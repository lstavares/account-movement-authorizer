# ADR 0004 - Lock pessimista para saldo

Status: Aceito

## Contexto

Transacoes concorrentes na mesma conta podem disputar o mesmo saldo disponivel.

## Decisao

Buscar a conta com lock pessimista durante a autorizacao e persistir saldo e transacao na mesma transacao de banco.

## Consequencias

A solucao privilegia consistencia financeira e simplicidade. Em alta concorrencia na mesma conta, a vazao pode ser limitada pela serializacao dos acessos.
