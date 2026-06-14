package br.com.leandrotavares.accountmovementauthorizer.infrastructure.web

import br.com.leandrotavares.accountmovementauthorizer.application.transactionauthorization.AuthorizeTransactionResult
import br.com.leandrotavares.accountmovementauthorizer.application.transactionauthorization.MoneyConverter
import java.math.BigDecimal
import java.time.OffsetDateTime

data class TransactionAuthorizationRequest(
    val account: TransactionAuthorizationAccountRequest,
    val transaction: TransactionAuthorizationTransactionRequest,
)

data class TransactionAuthorizationAccountRequest(
    val id: String,
)

data class TransactionAuthorizationTransactionRequest(
    val type: String,
    val amount: TransactionAuthorizationAmountRequest,
)

data class TransactionAuthorizationAmountRequest(
    val value: BigDecimal,
    val currency: String,
)

data class TransactionAuthorizationResponse(
    val transaction: TransactionAuthorizationTransactionResponse,
    val account: TransactionAuthorizationAccountResponse,
)

data class TransactionAuthorizationTransactionResponse(
    val id: String,
    val type: String,
    val amount: TransactionAuthorizationAmountResponse,
    val status: String,
    val timestamp: OffsetDateTime,
)

data class TransactionAuthorizationAmountResponse(
    val value: BigDecimal,
    val currency: String,
)

data class TransactionAuthorizationAccountResponse(
    val id: String,
    val balance: TransactionAuthorizationBalanceResponse,
)

data class TransactionAuthorizationBalanceResponse(
    val amount: BigDecimal,
    val currency: String,
)

fun AuthorizeTransactionResult.toResponse(moneyConverter: MoneyConverter): TransactionAuthorizationResponse =
    TransactionAuthorizationResponse(
        transaction = TransactionAuthorizationTransactionResponse(
            id = transactionId.toString(),
            type = type.name,
            amount = TransactionAuthorizationAmountResponse(
                value = moneyConverter.toDecimal(amountValue),
                currency = amountCurrency,
            ),
            status = status.name,
            timestamp = timestamp,
        ),
        account = TransactionAuthorizationAccountResponse(
            id = accountId.toString(),
            balance = TransactionAuthorizationBalanceResponse(
                amount = moneyConverter.toDecimal(accountBalanceAmount),
                currency = accountBalanceCurrency,
            ),
        ),
    )
