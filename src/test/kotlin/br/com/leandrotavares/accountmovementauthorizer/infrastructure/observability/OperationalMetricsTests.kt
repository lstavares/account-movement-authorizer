package br.com.leandrotavares.accountmovementauthorizer.infrastructure.observability

import br.com.leandrotavares.accountmovementauthorizer.domain.FailureReason
import br.com.leandrotavares.accountmovementauthorizer.domain.TransactionStatus
import br.com.leandrotavares.accountmovementauthorizer.domain.TransactionType
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class OperationalMetricsTests {
    private val meterRegistry = SimpleMeterRegistry()
    private val operationalMetrics = OperationalMetrics(meterRegistry)

    @Test
    fun `deve incrementar counters operacionais com tags esperadas`() {
        operationalMetrics.incrementAccountOpeningMessage(AccountOpeningMessageResult.CREATED)
        operationalMetrics.incrementAccountOpeningMessage(AccountOpeningMessageResult.ALREADY_EXISTS)
        operationalMetrics.incrementSqsPollMessagesReceived(3)
        operationalMetrics.incrementTransactionAuthorization(TransactionType.CREDIT, TransactionStatus.SUCCEEDED)
        operationalMetrics.incrementFunctionalTransactionAuthorizationFailure(FailureReason.INSUFFICIENT_FUNDS)
        operationalMetrics.incrementTransactionAuthorizationFailure(
            TransactionAuthorizationFailureReason.IDEMPOTENCY_CONFLICT,
        )

        assertThat(counterValue(OperationalMetrics.ACCOUNT_OPENING_MESSAGES, "result", "created")).isEqualTo(1.0)
        assertThat(counterValue(OperationalMetrics.ACCOUNT_OPENING_MESSAGES, "result", "already_exists"))
            .isEqualTo(1.0)
        assertThat(counterValue(OperationalMetrics.SQS_POLL_MESSAGES_RECEIVED)).isEqualTo(3.0)
        assertThat(
            counterValue(
                OperationalMetrics.TRANSACTION_AUTHORIZATIONS,
                "type",
                "CREDIT",
                "status",
                "SUCCEEDED",
            ),
        ).isEqualTo(1.0)
        assertThat(
            counterValue(
                OperationalMetrics.TRANSACTION_AUTHORIZATION_FAILURES,
                "reason",
                "INSUFFICIENT_FUNDS",
            ),
        ).isEqualTo(1.0)
        assertThat(
            counterValue(
                OperationalMetrics.TRANSACTION_AUTHORIZATION_FAILURES,
                "reason",
                "IDEMPOTENCY_CONFLICT",
            ),
        ).isEqualTo(1.0)
    }

    @Test
    fun `deve registrar timers de autorizacao e processamento de abertura`() {
        val authorizationSample = operationalMetrics.startTimer()
        val accountOpeningSample = operationalMetrics.startTimer()

        val authorizationDurationMs = operationalMetrics.recordTransactionAuthorizationDuration(
            authorizationSample,
            TransactionAuthorizationType.DEBIT,
            TransactionAuthorizationOutcome.FAILED,
        )
        val accountOpeningDurationMs = operationalMetrics.recordAccountOpeningProcessingDuration(
            accountOpeningSample,
            AccountOpeningMessageResult.ERROR,
        )
        operationalMetrics.recordTransactionAuthorizationDuration(
            Duration.ofMillis(1),
            TransactionAuthorizationType.UNKNOWN,
            TransactionAuthorizationOutcome.VALIDATION_ERROR,
        )

        assertThat(authorizationDurationMs).isGreaterThanOrEqualTo(0)
        assertThat(accountOpeningDurationMs).isGreaterThanOrEqualTo(0)
        assertThat(
            timerCount(
                OperationalMetrics.TRANSACTION_AUTHORIZATION_DURATION,
                "type",
                "DEBIT",
                "outcome",
                "FAILED",
            ),
        ).isEqualTo(1)
        assertThat(
            timerCount(
                OperationalMetrics.TRANSACTION_AUTHORIZATION_DURATION,
                "type",
                "UNKNOWN",
                "outcome",
                "VALIDATION_ERROR",
            ),
        ).isEqualTo(1)
        assertThat(
            timerCount(
                OperationalMetrics.ACCOUNT_OPENING_PROCESSING_DURATION,
                "result",
                "error",
            ),
        ).isEqualTo(1)
    }

    private fun counterValue(
        name: String,
        vararg tags: String,
    ): Double =
        meterRegistry.find(name).tags(*tags).counter()?.count() ?: 0.0

    private fun timerCount(
        name: String,
        vararg tags: String,
    ): Long =
        meterRegistry.find(name).tags(*tags).timer()?.count() ?: 0
}
