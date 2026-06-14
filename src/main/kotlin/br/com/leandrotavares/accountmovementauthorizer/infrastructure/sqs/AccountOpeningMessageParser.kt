package br.com.leandrotavares.accountmovementauthorizer.infrastructure.sqs

import br.com.leandrotavares.accountmovementauthorizer.application.accountopening.RegisterOpenedAccountCommand
import br.com.leandrotavares.accountmovementauthorizer.domain.AccountStatus
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Component
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Component
class AccountOpeningMessageParser(
    private val objectMapper: ObjectMapper,
) {
    fun parse(body: String): RegisterOpenedAccountCommand {
        val message = try {
            objectMapper.readValue<AccountOpeningMessageDto>(body)
        } catch (ex: Exception) {
            throw InvalidAccountOpeningMessageException("Invalid account opening message JSON", ex)
        }

        return try {
            RegisterOpenedAccountCommand(
                accountId = UUID.fromString(message.account.id),
                ownerId = UUID.fromString(message.account.owner),
                status = AccountStatus.valueOf(message.account.status),
                openedAt = Instant
                    .ofEpochSecond(message.account.createdAt.toLong())
                    .atOffset(ZoneOffset.UTC),
            )
        } catch (ex: IllegalArgumentException) {
            throw InvalidAccountOpeningMessageException("Invalid account opening message fields", ex)
        } catch (ex: DateTimeException) {
            throw InvalidAccountOpeningMessageException("Invalid account opening created_at", ex)
        }
    }
}

class InvalidAccountOpeningMessageException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)
