package br.com.leandrotavares.accountmovementauthorizer

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

abstract class PostgreSqlIntegrationTest {
    companion object {
        private val postgres = TestPostgreSqlContainer().apply {
            withDatabaseName("account_authorizer")
            withUsername("account_authorizer")
            withPassword("account_authorizer")
        }

        init {
            postgres.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerPostgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
        }
    }

    private class TestPostgreSqlContainer : PostgreSQLContainer<TestPostgreSqlContainer>("postgres:16-alpine")
}
