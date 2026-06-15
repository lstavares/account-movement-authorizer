package br.com.leandrotavares.accountmovementauthorizer.infrastructure.observability

import br.com.leandrotavares.accountmovementauthorizer.domain.FailureReason
import br.com.leandrotavares.accountmovementauthorizer.domain.TransactionStatus
import br.com.leandrotavares.accountmovementauthorizer.domain.TransactionType
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

@Component
class OperationalMetrics(
    private val meterRegistry: MeterRegistry,
) {
    fun startTimer(): Timer.Sample =
        Timer.start(meterRegistry)

    fun incrementAccountOpeningMessage(result: AccountOpeningMessageResult) {
        counter(ACCOUNT_OPENING_MESSAGES, RESULT_TAG, result.tag).increment()
    }

    fun incrementSqsPollMessagesReceived(messagesCount: Int) {
        if (messagesCount > 0) {
            counter(SQS_POLL_MESSAGES_RECEIVED).increment(messagesCount.toDouble())
        }
    }

    fun recordAccountOpeningProcessingDuration(
        sample: Timer.Sample,
        result: AccountOpeningMessageResult,
    ): Long {
        val elapsedNanos = sample.stop(
            timer(ACCOUNT_OPENING_PROCESSING_DURATION, RESULT_TAG, result.tag),
        )

        return TimeUnit.NANOSECONDS.toMillis(elapsedNanos)
    }

    fun incrementTransactionAuthorization(
        type: TransactionType,
        status: TransactionStatus,
    ) {
        counter(
            TRANSACTION_AUTHORIZATIONS,
            TYPE_TAG,
            type.name,
            STATUS_TAG,
            status.name,
        ).increment()
    }

    fun incrementTransactionAuthorizationFailure(reason: TransactionAuthorizationFailureReason) {
        counter(
            TRANSACTION_AUTHORIZATION_FAILURES,
            REASON_TAG,
            reason.tag,
        ).increment()
    }

    fun incrementFunctionalTransactionAuthorizationFailure(reason: FailureReason) {
        val metricReason = TransactionAuthorizationFailureReason.from(reason) ?: return
        incrementTransactionAuthorizationFailure(metricReason)
    }

    fun recordTransactionAuthorizationDuration(
        sample: Timer.Sample,
        type: TransactionAuthorizationType,
        outcome: TransactionAuthorizationOutcome,
    ): Long {
        val elapsedNanos = sample.stop(
            transactionAuthorizationDurationTimer(type, outcome),
        )

        return TimeUnit.NANOSECONDS.toMillis(elapsedNanos)
    }

    fun recordTransactionAuthorizationDuration(
        duration: Duration,
        type: TransactionAuthorizationType,
        outcome: TransactionAuthorizationOutcome,
    ) {
        transactionAuthorizationDurationTimer(type, outcome).record(duration)
    }

    private fun transactionAuthorizationDurationTimer(
        type: TransactionAuthorizationType,
        outcome: TransactionAuthorizationOutcome,
    ): Timer =
        timer(
            TRANSACTION_AUTHORIZATION_DURATION,
            TYPE_TAG,
            type.tag,
            OUTCOME_TAG,
            outcome.tag,
        )

    private fun counter(
        name: String,
        vararg tags: String,
    ): Counter =
        Counter.builder(name)
            .tags(*tags)
            .register(meterRegistry)

    private fun timer(
        name: String,
        vararg tags: String,
    ): Timer =
        Timer.builder(name)
            .tags(*tags)
            .register(meterRegistry)

    companion object {
        const val ACCOUNT_OPENING_MESSAGES = "account.opening.messages"
        const val SQS_POLL_MESSAGES_RECEIVED = "sqs.poll.messages.received"
        const val TRANSACTION_AUTHORIZATIONS = "transaction.authorizations"
        const val TRANSACTION_AUTHORIZATION_FAILURES = "transaction.authorization.failures"
        const val TRANSACTION_AUTHORIZATION_DURATION = "transaction.authorization.duration"
        const val ACCOUNT_OPENING_PROCESSING_DURATION = "account.opening.processing.duration"

        private const val RESULT_TAG = "result"
        private const val TYPE_TAG = "type"
        private const val STATUS_TAG = "status"
        private const val REASON_TAG = "reason"
        private const val OUTCOME_TAG = "outcome"
    }
}

enum class AccountOpeningMessageResult(
    val tag: String,
) {
    CREATED("created"),
    ALREADY_EXISTS("already_exists"),
    INVALID("invalid"),
    ERROR("error"),
}

enum class TransactionAuthorizationFailureReason(
    val tag: String,
) {
    INSUFFICIENT_FUNDS("INSUFFICIENT_FUNDS"),
    ACCOUNT_NOT_FOUND("ACCOUNT_NOT_FOUND"),
    ACCOUNT_DISABLED("ACCOUNT_DISABLED"),
    IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT"),
    VALIDATION_ERROR("VALIDATION_ERROR"),
    ;

    companion object {
        fun from(reason: FailureReason): TransactionAuthorizationFailureReason? =
            when (reason) {
                FailureReason.INSUFFICIENT_FUNDS -> INSUFFICIENT_FUNDS
                FailureReason.ACCOUNT_NOT_FOUND -> ACCOUNT_NOT_FOUND
                FailureReason.ACCOUNT_DISABLED -> ACCOUNT_DISABLED
                FailureReason.INVALID_AMOUNT,
                FailureReason.INVALID_CURRENCY,
                -> VALIDATION_ERROR
            }
    }
}

enum class TransactionAuthorizationOutcome(
    val tag: String,
) {
    SUCCEEDED("SUCCEEDED"),
    FAILED("FAILED"),
    IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT"),
    VALIDATION_ERROR("VALIDATION_ERROR"),
    ERROR("ERROR"),
}

enum class TransactionAuthorizationType(
    val tag: String,
) {
    CREDIT("CREDIT"),
    DEBIT("DEBIT"),
    UNKNOWN("UNKNOWN"),
    ;

    companion object {
        fun from(type: TransactionType): TransactionAuthorizationType =
            when (type) {
                TransactionType.CREDIT -> CREDIT
                TransactionType.DEBIT -> DEBIT
            }
    }
}
