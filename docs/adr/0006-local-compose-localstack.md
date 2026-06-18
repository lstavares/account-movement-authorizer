# ADR 0006 - Docker Compose e LocalStack no ambiente local

Status: Aceito

## Contexto

O avaliador precisa executar banco, SQS local e aplicação sem depender de recursos AWS reais.

## Decisão

Usar Docker Compose com PostgreSQL, LocalStack, aplicação containerizada e `message-generator` em profile `seed`.

## Consequências

O ambiente local fica reproduzível e isolado. LocalStack cobre o fluxo de desenvolvimento, mas não substitui testes em uma conta cloud real antes de produção.
