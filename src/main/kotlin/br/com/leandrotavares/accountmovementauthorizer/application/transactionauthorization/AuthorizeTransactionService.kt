package br.com.leandrotavares.accountmovementauthorizer.application.transactionauthorization

import br.com.leandrotavares.accountmovementauthorizer.domain.AccountStatus
import br.com.leandrotavares.accountmovementauthorizer.domain.FailureReason
import br.com.leandrotavares.accountmovementauthorizer.domain.TransactionStatus
import br.com.leandrotavares.accountmovementauthorizer.domain.TransactionType
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.entity.AccountEntity
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.entity.TransactionEntity
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.repository.AccountRepository
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.repository.TransactionRepository
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.observability.OperationalMetrics
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.observability.TransactionAuthorizationFailureReason
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.observability.TransactionAuthorizationOutcome
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.observability.TransactionAuthorizationType
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Service
class AuthorizeTransactionService(
    private val transactionRepository: TransactionRepository,
    private val transactionAuthorizationWriter: TransactionAuthorizationWriter,
    private val operationalMetrics: OperationalMetrics,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun authorize(command: AuthorizeTransactionCommand): AuthorizeTransactionResult {
        val sample = operationalMetrics.startTimer()
        val metricType = TransactionAuthorizationType.from(command.type)

        try {
            val result = authorizeWithIdempotency(command)
            operationalMetrics.incrementTransactionAuthorization(result.type, result.status)
            result.failureReason?.let { operationalMetrics.incrementFunctionalTransactionAuthorizationFailure(it) }

            val durationMs = operationalMetrics.recordTransactionAuthorizationDuration(
                sample,
                metricType,
                result.status.toMetricOutcome(),
            )
            logger.info(
                "Transaction authorization observed. transactionId={} accountId={} type={} status={} failureReason={} durationMs={}",
                result.transactionId,
                result.accountId,
                result.type,
                result.status,
                result.failureReason ?: "NONE",
                durationMs,
            )

            return result
        } catch (ex: IdempotencyConflictException) {
            val durationMs = operationalMetrics.recordTransactionAuthorizationDuration(
                sample,
                metricType,
                TransactionAuthorizationOutcome.IDEMPOTENCY_CONFLICT,
            )
            logger.info(
                "Transaction authorization observed. transactionId={} accountId={} type={} status={} failureReason={} durationMs={}",
                command.transactionId,
                command.accountId,
                command.type,
                TransactionStatus.FAILED,
                TransactionAuthorizationFailureReason.IDEMPOTENCY_CONFLICT.tag,
                durationMs,
            )
            throw ex
        } catch (ex: Exception) {
            val durationMs = operationalMetrics.recordTransactionAuthorizationDuration(
                sample,
                metricType,
                TransactionAuthorizationOutcome.ERROR,
            )
            logger.error(
                "Transaction authorization failed unexpectedly. transactionId={} accountId={} type={} status={} failureReason={} durationMs={}",
                command.transactionId,
                command.accountId,
                command.type,
                TransactionStatus.FAILED,
                "ERROR",
                durationMs,
                ex,
            )
            throw ex
        }
    }

    private fun authorizeWithIdempotency(command: AuthorizeTransactionCommand): AuthorizeTransactionResult {
        transactionRepository.findById(command.transactionId).orElse(null)?.let { existingTransaction ->
            if (!existingTransaction.matches(command)) {
                logger.info(
                    "Transaction idempotency conflict detected. transactionId={} accountId={} type={} idempotencyResult={}",
                    command.transactionId,
                    command.accountId,
                    command.type,
                    "CONFLICT",
                )
            }

            return existingTransaction.toResultFor(command).also { result ->
                logger.info(
                    "Transaction idempotency replay accepted. transactionId={} accountId={} type={} status={} failureReason={} idempotencyResult={}",
                    result.transactionId,
                    result.accountId,
                    result.type,
                    result.status,
                    result.failureReason ?: "NONE",
                    "REPLAYED",
                )
            }
        }

        return try {
            transactionAuthorizationWriter.authorize(command)
        } catch (ex: DataIntegrityViolationException) {
            logger.info(
                "Transaction already persisted by a concurrent process. transactionId={} accountId={} type={} idempotencyResult={}",
                command.transactionId,
                command.accountId,
                command.type,
                "CONCURRENT_DUPLICATE",
            )

            val existingTransaction = transactionRepository.findById(command.transactionId).orElseThrow { ex }
            if (!existingTransaction.matches(command)) {
                logger.info(
                    "Transaction idempotency conflict detected after concurrent insert. transactionId={} accountId={} type={} idempotencyResult={}",
                    command.transactionId,
                    command.accountId,
                    command.type,
                    "CONFLICT",
                )
            }

            existingTransaction.toResultFor(command).also { result ->
                logger.info(
                    "Transaction concurrent idempotency replay accepted. transactionId={} accountId={} type={} status={} failureReason={} idempotencyResult={}",
                    result.transactionId,
                    result.accountId,
                    result.type,
                    result.status,
                    result.failureReason ?: "NONE",
                    "CONCURRENT_REPLAYED",
                )
            }
        }
    }
}

@Component
class TransactionAuthorizationWriter(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val transactionZone = ZoneId.of("America/Sao_Paulo")

    @Transactional
    fun authorize(command: AuthorizeTransactionCommand): AuthorizeTransactionResult {
        transactionRepository.findById(command.transactionId).orElse(null)?.let { existingTransaction ->
            return existingTransaction.toResultFor(command).also { result ->
                logger.info(
                    "Transaction idempotency replay accepted inside authorization transaction. transactionId={} accountId={} type={} status={} failureReason={} idempotencyResult={}",
                    result.transactionId,
                    result.accountId,
                    result.type,
                    result.status,
                    result.failureReason ?: "NONE",
                    "REPLAYED",
                )
            }
        }

        val now = currentTimestamp()
        val account = accountRepository.findByIdForUpdate(command.accountId)

        transactionRepository.findById(command.transactionId).orElse(null)?.let { existingTransaction ->
            return existingTransaction.toResultFor(command).also { result ->
                logger.info(
                    "Transaction idempotency replay accepted after account lock. transactionId={} accountId={} type={} status={} failureReason={} idempotencyResult={}",
                    result.transactionId,
                    result.accountId,
                    result.type,
                    result.status,
                    result.failureReason ?: "NONE",
                    "REPLAYED_AFTER_LOCK",
                )
            }
        }

        if (account == null) {
            val transaction = saveFailedTransaction(
                command = command,
                failureReason = FailureReason.ACCOUNT_NOT_FOUND,
                balanceBeforeAmount = null,
                balanceAfterAmount = null,
                now = now,
            )

            logger.info(
                "Transaction authorization finished. transactionId={} accountId={} type={} status={} failureReason={}",
                command.transactionId,
                command.accountId,
                command.type,
                TransactionStatus.FAILED,
                FailureReason.ACCOUNT_NOT_FOUND,
            )

            return transaction.toResult(accountBalanceAmount = 0, accountBalanceCurrency = command.amountCurrency)
        }

        val balanceBefore = account.balanceAmount

        if (account.status != AccountStatus.ENABLED) {
            val transaction = saveFailedTransaction(
                command = command,
                failureReason = FailureReason.ACCOUNT_DISABLED,
                balanceBeforeAmount = balanceBefore,
                balanceAfterAmount = balanceBefore,
                now = now,
            )

            logger.info(
                "Transaction authorization finished. transactionId={} accountId={} type={} status={} failureReason={}",
                command.transactionId,
                command.accountId,
                command.type,
                TransactionStatus.FAILED,
                FailureReason.ACCOUNT_DISABLED,
            )

            return transaction.toResult(
                accountBalanceAmount = balanceBefore,
                accountBalanceCurrency = account.balanceCurrency,
            )
        }

        return when (command.type) {
            TransactionType.CREDIT -> authorizeCredit(command, account, balanceBefore, now)
            TransactionType.DEBIT -> authorizeDebit(command, account, balanceBefore, now)
        }
    }

    private fun authorizeCredit(
        command: AuthorizeTransactionCommand,
        account: AccountEntity,
        balanceBefore: Long,
        now: OffsetDateTime,
    ): AuthorizeTransactionResult {
        val balanceAfter = Math.addExact(balanceBefore, command.amountValue)

        account.balanceAmount = balanceAfter
        account.updatedAt = now

        val transaction = saveSucceededTransaction(
            command = command,
            balanceBeforeAmount = balanceBefore,
            balanceAfterAmount = balanceAfter,
            now = now,
        )

        logger.info(
            "Transaction authorization finished. transactionId={} accountId={} type={} status={} failureReason={}",
            command.transactionId,
            command.accountId,
            command.type,
            TransactionStatus.SUCCEEDED,
            "NONE",
        )

        return transaction.toResult(
            accountBalanceAmount = balanceAfter,
            accountBalanceCurrency = account.balanceCurrency,
        )
    }

    private fun authorizeDebit(
        command: AuthorizeTransactionCommand,
        account: AccountEntity,
        balanceBefore: Long,
        now: OffsetDateTime,
    ): AuthorizeTransactionResult {
        if (balanceBefore < command.amountValue) {
            val transaction = saveFailedTransaction(
                command = command,
                failureReason = FailureReason.INSUFFICIENT_FUNDS,
                balanceBeforeAmount = balanceBefore,
                balanceAfterAmount = balanceBefore,
                now = now,
            )

            logger.info(
                "Transaction authorization finished. transactionId={} accountId={} type={} status={} failureReason={}",
                command.transactionId,
                command.accountId,
                command.type,
                TransactionStatus.FAILED,
                FailureReason.INSUFFICIENT_FUNDS,
            )

            return transaction.toResult(
                accountBalanceAmount = balanceBefore,
                accountBalanceCurrency = account.balanceCurrency,
            )
        }

        val balanceAfter = balanceBefore - command.amountValue
        account.balanceAmount = balanceAfter
        account.updatedAt = now

        val transaction = saveSucceededTransaction(
            command = command,
            balanceBeforeAmount = balanceBefore,
            balanceAfterAmount = balanceAfter,
            now = now,
        )

        logger.info(
            "Transaction authorization finished. transactionId={} accountId={} type={} status={} failureReason={}",
            command.transactionId,
            command.accountId,
            command.type,
            TransactionStatus.SUCCEEDED,
            "NONE",
        )

        return transaction.toResult(
            accountBalanceAmount = balanceAfter,
            accountBalanceCurrency = account.balanceCurrency,
        )
    }

    private fun saveSucceededTransaction(
        command: AuthorizeTransactionCommand,
        balanceBeforeAmount: Long,
        balanceAfterAmount: Long,
        now: OffsetDateTime,
    ): TransactionEntity =
        transactionRepository.saveAndFlush(
            TransactionEntity(
                id = command.transactionId,
                accountId = command.accountId,
                type = command.type,
                amountValue = command.amountValue,
                amountCurrency = command.amountCurrency,
                status = TransactionStatus.SUCCEEDED,
                failureReason = null,
                balanceBeforeAmount = balanceBeforeAmount,
                balanceAfterAmount = balanceAfterAmount,
                requestedAt = now,
                createdAt = now,
            ),
        )

    private fun saveFailedTransaction(
        command: AuthorizeTransactionCommand,
        failureReason: FailureReason,
        balanceBeforeAmount: Long?,
        balanceAfterAmount: Long?,
        now: OffsetDateTime,
    ): TransactionEntity =
        transactionRepository.saveAndFlush(
            TransactionEntity(
                id = command.transactionId,
                accountId = command.accountId,
                type = command.type,
                amountValue = command.amountValue,
                amountCurrency = command.amountCurrency,
                status = TransactionStatus.FAILED,
                failureReason = failureReason,
                balanceBeforeAmount = balanceBeforeAmount,
                balanceAfterAmount = balanceAfterAmount,
                requestedAt = now,
                createdAt = now,
            ),
        )

    private fun currentTimestamp(): OffsetDateTime =
        OffsetDateTime.now(transactionZone).truncatedTo(ChronoUnit.MICROS)
}

private fun TransactionEntity.toResultFor(command: AuthorizeTransactionCommand): AuthorizeTransactionResult {
    if (!matches(command)) {
        throw IdempotencyConflictException(command.transactionId)
    }

    return toResult(
        accountBalanceAmount = balanceAfterAmount ?: 0,
        accountBalanceCurrency = amountCurrency,
    )
}

private fun TransactionEntity.matches(command: AuthorizeTransactionCommand): Boolean =
    accountId == command.accountId &&
        type == command.type &&
        amountValue == command.amountValue &&
        amountCurrency == command.amountCurrency

private fun TransactionEntity.toResult(
    accountBalanceAmount: Long,
    accountBalanceCurrency: String,
): AuthorizeTransactionResult =
    AuthorizeTransactionResult(
        transactionId = id,
        accountId = accountId,
        type = type,
        amountValue = amountValue,
        amountCurrency = amountCurrency,
        status = status,
        failureReason = failureReason,
        timestamp = requestedAt,
        accountBalanceAmount = accountBalanceAmount,
        accountBalanceCurrency = accountBalanceCurrency,
    )

private fun TransactionStatus.toMetricOutcome(): TransactionAuthorizationOutcome =
    when (this) {
        TransactionStatus.SUCCEEDED -> TransactionAuthorizationOutcome.SUCCEEDED
        TransactionStatus.FAILED -> TransactionAuthorizationOutcome.FAILED
    }
