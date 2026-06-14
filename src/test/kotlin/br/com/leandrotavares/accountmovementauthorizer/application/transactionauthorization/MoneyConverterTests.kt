package br.com.leandrotavares.accountmovementauthorizer.application.transactionauthorization

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MoneyConverterTests {
    private val moneyConverter = MoneyConverter()

    @Test
    fun `deve converter valor decimal para centavos`() {
        assertThat(moneyConverter.toCents(BigDecimal("97.07"))).isEqualTo(97_07)
        assertThat(moneyConverter.toCents(BigDecimal("97"))).isEqualTo(97_00)
        assertThat(moneyConverter.toCents(BigDecimal("97.7"))).isEqualTo(97_70)
    }

    @Test
    fun `deve converter centavos para decimal`() {
        assertThat(moneyConverter.toDecimal(97_07)).isEqualByComparingTo(BigDecimal("97.07"))
        assertThat(moneyConverter.toDecimal(0)).isEqualByComparingTo(BigDecimal("0.00"))
    }

    @Test
    fun `deve rejeitar valor menor ou igual a zero`() {
        assertThatThrownBy { moneyConverter.toCents(BigDecimal.ZERO) }
            .isInstanceOf(InvalidMoneyAmountException::class.java)

        assertThatThrownBy { moneyConverter.toCents(BigDecimal("-0.01")) }
            .isInstanceOf(InvalidMoneyAmountException::class.java)
    }

    @Test
    fun `deve rejeitar valor com mais de duas casas decimais`() {
        assertThatThrownBy { moneyConverter.toCents(BigDecimal("10.001")) }
            .isInstanceOf(InvalidMoneyAmountException::class.java)
    }
}
