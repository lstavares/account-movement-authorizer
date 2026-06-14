package br.com.leandrotavares.accountmovementauthorizer.infrastructure.web

import br.com.leandrotavares.accountmovementauthorizer.application.transactionauthorization.AuthorizeTransactionResult
import br.com.leandrotavares.accountmovementauthorizer.application.transactionauthorization.MoneyConverter
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.OffsetDateTime

@Schema(description = "Transaction authorization request")
data class TransactionAuthorizationRequest(
    @field:Schema(description = "Account involved in the transaction")
    val account: TransactionAuthorizationAccountRequest,
    @field:Schema(description = "Transaction to authorize")
    val transaction: TransactionAuthorizationTransactionRequest,
)

@Schema(description = "Account reference")
data class TransactionAuthorizationAccountRequest(
    @field:Schema(description = "Account UUID", example = "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975")
    val id: String,
)

@Schema(description = "Transaction data")
data class TransactionAuthorizationTransactionRequest(
    @field:Schema(description = "Transaction type", example = "CREDIT", allowableValues = ["CREDIT", "DEBIT"])
    val type: String,
    @field:Schema(description = "Transaction amount")
    val amount: TransactionAuthorizationAmountRequest,
)

@Schema(description = "Money amount")
data class TransactionAuthorizationAmountRequest(
    @field:Schema(description = "Decimal amount with at most two fraction digits", example = "97.07")
    val value: BigDecimal,
    @field:Schema(description = "Currency code accepted by this version", example = "BRL", allowableValues = ["BRL"])
    val currency: String,
)

@Schema(description = "Transaction authorization response")
data class TransactionAuthorizationResponse(
    @field:Schema(description = "Persisted transaction result")
    val transaction: TransactionAuthorizationTransactionResponse,
    @field:Schema(description = "Account state after the authorization decision")
    val account: TransactionAuthorizationAccountResponse,
)

@Schema(description = "Persisted transaction result")
data class TransactionAuthorizationTransactionResponse(
    @field:Schema(description = "Transaction UUID", example = "8e8ae808-b154-48b5-9f3e-553935cc4543")
    val id: String,
    @field:Schema(description = "Transaction type", example = "CREDIT", allowableValues = ["CREDIT", "DEBIT"])
    val type: String,
    @field:Schema(description = "Authorized or rejected amount")
    val amount: TransactionAuthorizationAmountResponse,
    @field:Schema(description = "Authorization status", example = "SUCCEEDED", allowableValues = ["SUCCEEDED", "FAILED"])
    val status: String,
    @field:Schema(description = "Authorization timestamp", example = "2025-07-08T15:57:55-03:00")
    val timestamp: OffsetDateTime,
)

@Schema(description = "Money amount returned by the API")
data class TransactionAuthorizationAmountResponse(
    @field:Schema(description = "Decimal amount", example = "97.07")
    val value: BigDecimal,
    @field:Schema(description = "Currency code", example = "BRL")
    val currency: String,
)

@Schema(description = "Account returned by the API")
data class TransactionAuthorizationAccountResponse(
    @field:Schema(description = "Account UUID", example = "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975")
    val id: String,
    @field:Schema(description = "Balance after the authorization decision")
    val balance: TransactionAuthorizationBalanceResponse,
)

@Schema(description = "Account balance")
data class TransactionAuthorizationBalanceResponse(
    @field:Schema(description = "Balance amount", example = "183.12")
    val amount: BigDecimal,
    @field:Schema(description = "Balance currency", example = "BRL")
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
