# ADR 0004 - Lock pessimista para saldo

Status: Aceito

## Contexto

Transações concorrentes na mesma conta podem disputar o mesmo saldo disponível.

## Decisão

Buscar a conta com lock pessimista durante a autorização e persistir saldo e transação na mesma transação de banco.

## Consequências

A solução privilegia consistência financeira e simplicidade. Em alta concorrência na mesma conta, a vazão pode ser limitada pela serialização dos acessos.
