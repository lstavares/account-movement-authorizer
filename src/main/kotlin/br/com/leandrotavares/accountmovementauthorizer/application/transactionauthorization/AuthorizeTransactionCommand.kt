package br.com.leandrotavares.accountmovementauthorizer.application.transactionauthorization

import br.com.leandrotavares.accountmovementauthorizer.domain.FailureReason
import br.com.leandrotavares.accountmovementauthorizer.domain.TransactionStatus
import br.com.leandrotavares.accountmovementauthorizer.domain.TransactionType
import java.time.OffsetDateTime
import java.util.UUID

data class AuthorizeTransactionCommand(
    val transactionId: UUID,
    val accountId: UUID,
    val type: TransactionType,
    val amountValue: Long,
    val amountCurrency: String,
)

data class AuthorizeTransactionResult(
    val transactionId: UUID,
    val accountId: UUID,
    val type: TransactionType,
    val amountValue: Long,
    val amountCurrency: String,
    val status: TransactionStatus,
    val failureReason: FailureReason?,
    val timestamp: OffsetDateTime,
    val accountBalanceAmount: Long,
    val accountBalanceCurrency: String,
)

class IdempotencyConflictException(
    val transactionId: UUID,
) : RuntimeException("Transaction idempotency conflict. transactionId=$transactionId")
