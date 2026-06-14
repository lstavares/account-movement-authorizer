package br.com.leandrotavares.accountmovementauthorizer.application.transactionauthorization

import br.com.leandrotavares.accountmovementauthorizer.domain.AccountStatus
import br.com.leandrotavares.accountmovementauthorizer.domain.FailureReason
import br.com.leandrotavares.accountmovementauthorizer.domain.TransactionStatus
import br.com.leandrotavares.accountmovementauthorizer.domain.TransactionType
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.entity.AccountEntity
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.entity.TransactionEntity
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.repository.AccountRepository
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.repository.TransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneId

@Service
class AuthorizeTransactionService(
    private val transactionRepository: TransactionRepository,
    private val transactionAuthorizationWriter: TransactionAuthorizationWriter,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun authorize(command: AuthorizeTransactionCommand): AuthorizeTransactionResult {
        transactionRepository.findById(command.transactionId).orElse(null)?.let {
            return it.toResultFor(command)
        }

        return try {
            transactionAuthorizationWriter.authorize(command)
        } catch (ex: DataIntegrityViolationException) {
            logger.info(
                "Transaction already persisted by a concurrent process. transactionId={} accountId={}",
                command.transactionId,
                command.accountId,
            )

            val existingTransaction = transactionRepository.findById(command.transactionId).orElseThrow { ex }
            existingTransaction.toResultFor(command)
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
        transactionRepository.findById(command.transactionId).orElse(null)?.let {
            return it.toResultFor(command)
        }

        val now = OffsetDateTime.now(transactionZone)
        val account = accountRepository.findByIdForUpdate(command.accountId)

        if (account == null) {
            val transaction = saveFailedTransaction(
                command = command,
                failureReason = FailureReason.ACCOUNT_NOT_FOUND,
                balanceBeforeAmount = null,
                balanceAfterAmount = null,
                now = now,
            )

            logger.info(
                "Transaction authorization failed because account was not found. transactionId={} accountId={}",
                command.transactionId,
                command.accountId,
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
                "Transaction authorization failed because account is disabled. transactionId={} accountId={}",
                command.transactionId,
                command.accountId,
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
            "Credit transaction authorized. transactionId={} accountId={}",
            command.transactionId,
            command.accountId,
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
                "Debit transaction rejected because account has insufficient funds. transactionId={} accountId={}",
                command.transactionId,
                command.accountId,
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
            "Debit transaction authorized. transactionId={} accountId={}",
            command.transactionId,
            command.accountId,
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
