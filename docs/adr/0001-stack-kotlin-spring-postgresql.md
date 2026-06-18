# ADR 0001 - Stack Kotlin, Spring Boot e PostgreSQL

Status: Aceito

## Contexto

O sistema precisa expor uma API HTTP, consumir SQS, persistir saldos e transações com consistência e ser simples de executar localmente.

## Decisão

Usar Kotlin com Spring Boot 3, Spring Web, Spring Data JPA, Flyway e PostgreSQL.

## Consequências

A stack entrega produtividade, suporte maduro a transações e boa integração com observabilidade. O custo é carregar o ecossistema Spring/JPA, maior que uma solução minimalista.
