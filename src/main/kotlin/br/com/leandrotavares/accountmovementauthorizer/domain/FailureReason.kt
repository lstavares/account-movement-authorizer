package br.com.leandrotavares.accountmovementauthorizer.domain

enum class FailureReason {
    ACCOUNT_NOT_FOUND,
    INSUFFICIENT_FUNDS,
    ACCOUNT_DISABLED,
    INVALID_AMOUNT,
    INVALID_CURRENCY,
}
