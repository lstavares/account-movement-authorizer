# ADR 0001 - Stack Kotlin, Spring Boot e PostgreSQL

Status: Aceito

## Contexto

O sistema precisa expor uma API HTTP, consumir SQS, persistir saldos e transacoes com consistencia e ser simples de executar localmente.

## Decisao

Usar Kotlin com Spring Boot 3, Spring Web, Spring Data JPA, Flyway e PostgreSQL.

## Consequencias

A stack entrega produtividade, suporte maduro a transacoes e boa integracao com observabilidade. O custo e carregar o ecossistema Spring/JPA, maior que uma solucao minimalista.
