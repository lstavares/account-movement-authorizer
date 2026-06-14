package br.com.leandrotavares.accountmovementauthorizer.infrastructure.web

import br.com.leandrotavares.accountmovementauthorizer.PostgreSqlIntegrationTest
import br.com.leandrotavares.accountmovementauthorizer.domain.AccountStatus
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.entity.AccountEntity
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.repository.AccountRepository
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.repository.TransactionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransactionAuthorizationControllerIntegrationTests : PostgreSqlIntegrationTest() {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @BeforeEach
    fun cleanDatabase() {
        transactionRepository.deleteAll()
        accountRepository.deleteAll()
    }

    @Test
    fun `deve retornar response de sucesso no formato esperado`() {
        val account = accountRepository.saveAndFlush(newAccount(balanceAmount = 86_05))
        val transactionId = UUID.randomUUID()

        val response = postTransaction(
            transactionId = transactionId,
            payload = requestPayload(accountId = account.id, type = "CREDIT", value = "97.07"),
        )

        val body = objectMapper.readTree(response.body)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(body.size()).isEqualTo(2)
        assertThat(body["transaction"].size()).isEqualTo(6)
        assertThat(body["account"].size()).isEqualTo(2)
        assertThat(body["transaction"]["amount"].size()).isEqualTo(2)
        assertThat(body["account"]["balance"].size()).isEqualTo(2)
        assertThat(body.at("/transaction/id").asText()).isEqualTo(transactionId.toString())
        assertThat(body.at("/transaction/type").asText()).isEqualTo("CREDIT")
        assertThat(body.at("/transaction/amount/value").decimalValue()).isEqualByComparingTo(BigDecimal("97.07"))
        assertThat(body.at("/transaction/amount/currency").asText()).isEqualTo("BRL")
        assertThat(body.at("/transaction/status").asText()).isEqualTo("SUCCEEDED")
        assertThat(body.at("/transaction/timestamp").asText()).isNotBlank()
        assertThat(body.at("/account/id").asText()).isEqualTo(account.id.toString())
        assertThat(body.at("/account/balance/amount").decimalValue()).isEqualByComparingTo(BigDecimal("183.12"))
        assertThat(body.at("/account/balance/currency").asText()).isEqualTo("BRL")
    }

    @Test
    fun `deve retornar response de falha funcional no mesmo envelope`() {
        val account = accountRepository.saveAndFlush(newAccount(balanceAmount = 10_00))
        val transactionId = UUID.randomUUID()

        val response = postTransaction(
            transactionId = transactionId,
            payload = requestPayload(accountId = account.id, type = "DEBIT", value = "10.01"),
        )

        val body = objectMapper.readTree(response.body)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(body.size()).isEqualTo(2)
        assertThat(body.at("/transaction/id").asText()).isEqualTo(transactionId.toString())
        assertThat(body.at("/transaction/type").asText()).isEqualTo("DEBIT")
        assertThat(body.at("/transaction/status").asText()).isEqualTo("FAILED")
        assertThat(body.at("/transaction/amount/value").decimalValue()).isEqualByComparingTo(BigDecimal("10.01"))
        assertThat(body.at("/account/id").asText()).isEqualTo(account.id.toString())
        assertThat(body.at("/account/balance/amount").decimalValue()).isEqualByComparingTo(BigDecimal("10.00"))
        assertThat(body.at("/account/balance/currency").asText()).isEqualTo("BRL")
    }

    @Test
    fun `deve retornar 409 para conflito de idempotencia`() {
        val account = accountRepository.saveAndFlush(newAccount(balanceAmount = 100_00))
        val transactionId = UUID.randomUUID()

        val firstResponse = postTransaction(
            transactionId = transactionId,
            payload = requestPayload(accountId = account.id, type = "CREDIT", value = "25.00"),
        )
        val conflictingResponse = postTransaction(
            transactionId = transactionId,
            payload = requestPayload(accountId = account.id, type = "DEBIT", value = "25.00"),
        )

        assertThat(firstResponse.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(conflictingResponse.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(accountRepository.findById(account.id).orElseThrow().balanceAmount).isEqualTo(125_00)
    }

    @Test
    fun `deve retornar 400 para moeda invalida`() {
        val account = accountRepository.saveAndFlush(newAccount())

        val response = postTransaction(
            transactionId = UUID.randomUUID(),
            payload = requestPayload(accountId = account.id, type = "CREDIT", value = "10.00", currency = "USD"),
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "-0.01"])
    fun `deve retornar 400 para valor menor ou igual a zero`(value: String) {
        val account = accountRepository.saveAndFlush(newAccount())

        val response = postTransaction(
            transactionId = UUID.randomUUID(),
            payload = requestPayload(accountId = account.id, type = "CREDIT", value = value),
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `deve retornar 400 para valor com mais de duas casas decimais`() {
        val account = accountRepository.saveAndFlush(newAccount())

        val response = postTransaction(
            transactionId = UUID.randomUUID(),
            payload = requestPayload(accountId = account.id, type = "CREDIT", value = "10.001"),
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `deve retornar 400 para tipo invalido`() {
        val account = accountRepository.saveAndFlush(newAccount())

        val response = postTransaction(
            transactionId = UUID.randomUUID(),
            payload = requestPayload(accountId = account.id, type = "PIX", value = "10.00"),
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `deve retornar 400 para uuid invalido`() {
        val response = postTransaction(
            transactionId = "not-a-uuid",
            payload = requestPayload(accountId = UUID.randomUUID(), type = "CREDIT", value = "10.00"),
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    private fun postTransaction(
        transactionId: Any,
        payload: String,
    ): ResponseEntity<String> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        return restTemplate.postForEntity(
            "/transactions/$transactionId",
            HttpEntity(payload, headers),
            String::class.java,
        )
    }

    private fun requestPayload(
        accountId: UUID,
        type: String,
        value: String,
        currency: String = "BRL",
    ): String =
        """
        {
          "account": {
            "id": "$accountId"
          },
          "transaction": {
            "type": "$type",
            "amount": {
              "value": $value,
              "currency": "$currency"
            }
          }
        }
        """.trimIndent()

    private fun newAccount(
        id: UUID = UUID.randomUUID(),
        status: AccountStatus = AccountStatus.ENABLED,
        balanceAmount: Long = 0,
    ): AccountEntity =
        AccountEntity(
            id = id,
            ownerId = UUID.randomUUID(),
            status = status,
            balanceAmount = balanceAmount,
            balanceCurrency = "BRL",
            openedAt = OffsetDateTime.now().minusDays(1),
            receivedAt = OffsetDateTime.now(),
            createdAt = OffsetDateTime.now(),
            updatedAt = OffsetDateTime.now(),
        )
}
