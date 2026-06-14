# ADR 0006 - Docker Compose e LocalStack no ambiente local

Status: Aceito

## Contexto

O avaliador precisa executar banco, SQS local e aplicacao sem depender de recursos AWS reais.

## Decisao

Usar Docker Compose com PostgreSQL, LocalStack, aplicacao containerizada e `message-generator` em profile `seed`.

## Consequencias

O ambiente local fica reproduzivel e isolado. LocalStack cobre o fluxo de desenvolvimento, mas nao substitui testes em uma conta cloud real antes de producao.
