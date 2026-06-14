package br.com.leandrotavares.accountmovementauthorizer.infrastructure.web

import br.com.leandrotavares.accountmovementauthorizer.application.transactionauthorization.AuthorizeTransactionCommand
import br.com.leandrotavares.accountmovementauthorizer.application.transactionauthorization.AuthorizeTransactionService
import br.com.leandrotavares.accountmovementauthorizer.application.transactionauthorization.MoneyConverter
import br.com.leandrotavares.accountmovementauthorizer.domain.TransactionType
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Transactions", description = "Authorization of credit and debit movements")
@RestController
class TransactionAuthorizationController(
    private val authorizeTransactionService: AuthorizeTransactionService,
    private val moneyConverter: MoneyConverter,
) {
    @Operation(
        summary = "Authorize an account transaction",
        description = "Authorizes CREDIT or DEBIT movements using transactionId as the idempotency key.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Transaction authorization request",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = TransactionAuthorizationRequest::class),
                    examples = [
                        ExampleObject(
                            name = "credit",
                            value = """
                            {
                              "account": { "id": "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975" },
                              "transaction": {
                                "type": "CREDIT",
                                "amount": { "value": 97.07, "currency": "BRL" }
                              }
                            }
                            """,
                        ),
                    ],
                ),
            ],
        ),
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Transaction accepted and recorded as SUCCEEDED or FAILED",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = TransactionAuthorizationResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request payload, UUID, transaction type, amount or currency",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiErrorResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "The same transactionId was already used with a different payload",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiErrorResponse::class),
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/transactions/{transactionId}")
    fun authorize(
        @Parameter(
            description = "Idempotency key and transaction identifier",
            example = "8e8ae808-b154-48b5-9f3e-553935cc4543",
        )
        @PathVariable
        transactionId: String,
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
