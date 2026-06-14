package br.com.leandrotavares.accountmovementauthorizer.infrastructure.web

import br.com.leandrotavares.accountmovementauthorizer.application.transactionauthorization.IdempotencyConflictException
import br.com.leandrotavares.accountmovementauthorizer.application.transactionauthorization.InvalidMoneyAmountException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(value = [InvalidTransactionRequestException::class, InvalidMoneyAmountException::class])
    fun handleBadRequest(ex: RuntimeException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiErrorResponse(error = "BAD_REQUEST", message = ex.message ?: "Invalid request"))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableJson(ex: HttpMessageNotReadableException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiErrorResponse(error = "BAD_REQUEST", message = "Invalid request body"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleIdempotencyConflict(ex: IdempotencyConflictException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(
                ApiErrorResponse(
                    error = "IDEMPOTENCY_CONFLICT",
                    message = "Transaction idempotency conflict. transactionId=${ex.transactionId}",
                ),
            )
}

data class ApiErrorResponse(
    val error: String,
    val message: String,
)
