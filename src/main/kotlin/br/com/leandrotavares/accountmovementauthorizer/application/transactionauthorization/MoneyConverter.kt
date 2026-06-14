package br.com.leandrotavares.accountmovementauthorizer.application.transactionauthorization

import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

@Component
class MoneyConverter {
    fun toCents(value: BigDecimal): Long {
        if (value <= BigDecimal.ZERO) {
            throw InvalidMoneyAmountException("amount.value must be greater than zero")
        }

        if (value.scale() > 2) {
            throw InvalidMoneyAmountException("amount.value must have at most two decimal places")
        }

        return try {
            value
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact()
        } catch (ex: ArithmeticException) {
            throw InvalidMoneyAmountException("amount.value must be representable in cents", ex)
        }
    }

    fun toDecimal(cents: Long): BigDecimal =
        BigDecimal.valueOf(cents, 2).setScale(2)
}

class InvalidMoneyAmountException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
