package br.com.leandrotavares.accountmovementauthorizer.infrastructure.web

import br.com.leandrotavares.accountmovementauthorizer.application.transactionauthorization.AuthorizeTransactionCommand
import br.com.leandrotavares.accountmovementauthorizer.application.transactionauthorization.AuthorizeTransactionService
import br.com.leandrotavares.accountmovementauthorizer.application.transactionauthorization.MoneyConverter
import br.com.leandrotavares.accountmovementauthorizer.domain.TransactionType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class TransactionAuthorizationController(
    private val authorizeTransactionService: AuthorizeTransactionService,
    private val moneyConverter: MoneyConverter,
) {
    @PostMapping("/transactions/{transactionId}")
    fun authorize(
        @PathVariable transactionId: String,
        @RequestBody request: TransactionAuthorizationRequest,
    ): ResponseEntity<TransactionAuthorizationResponse> {
        val command = AuthorizeTransactionCommand(
            transactionId = parseUuid("transactionId", transactionId),
            accountId = parseUuid("account.id", request.account.id),
            type = parseTransactionType(request.transaction.type),
            amountValue = moneyConverter.toCents(request.transaction.amount.value),
            amountCurrency = parseCurrency(request.transaction.amount.currency),
        )

        val result = authorizeTransactionService.authorize(command)

        return ResponseEntity.ok(result.toResponse(moneyConverter))
    }

    private fun parseUuid(field: String, value: String): UUID =
        try {
            UUID.fromString(value)
        } catch (ex: IllegalArgumentException) {
            throw InvalidTransactionRequestException("$field must be a valid UUID", ex)
        }

    private fun parseTransactionType(value: String): TransactionType =
        try {
            TransactionType.valueOf(value)
        } catch (ex: IllegalArgumentException) {
            throw InvalidTransactionRequestException("transaction.type must be CREDIT or DEBIT", ex)
        }

    private fun parseCurrency(value: String): String {
        if (value != "BRL") {
            throw InvalidTransactionRequestException("transaction.amount.currency must be BRL")
        }

        return value
    }
}

class InvalidTransactionRequestException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
