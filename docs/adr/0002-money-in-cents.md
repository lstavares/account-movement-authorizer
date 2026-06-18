# ADR 0002 - Valores monetários em centavos

Status: Aceito

## Contexto

Autorização financeira não deve depender de ponto flutuante para calcular saldo.

## Decisão

Persistir valores monetários como `Long` em centavos e expor valores decimais apenas na API.

## Consequências

A decisão evita erros de arredondamento e simplifica comparações. A aplicação precisa validar escala máxima de duas casas decimais antes da conversão.
