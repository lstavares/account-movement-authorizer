# ADR 0002 - Valores monetarios em centavos

Status: Aceito

## Contexto

Autorizacao financeira nao deve depender de ponto flutuante para calcular saldo.

## Decisao

Persistir valores monetarios como `Long` em centavos e expor valores decimais apenas na API.

## Consequencias

A decisao evita erros de arredondamento e simplifica comparacoes. A aplicacao precisa validar escala maxima de duas casas decimais antes da conversao.
