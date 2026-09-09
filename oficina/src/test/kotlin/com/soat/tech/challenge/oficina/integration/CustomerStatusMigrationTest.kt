package com.soat.tech.challenge.oficina.integration

import com.soat.tech.challenge.oficina.domain.model.Customer
import com.soat.tech.challenge.oficina.domain.model.CustomerStatus
import com.soat.tech.challenge.oficina.domain.model.TaxDocument
import com.soat.tech.challenge.oficina.domain.port.CustomerRepository
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerStatusMigrationTest {

	@Autowired
	private lateinit var dataSource: DataSource

	@Autowired
	private lateinit var customerRepository: CustomerRepository

	private var migrationConnection: Connection? = null
	private lateinit var migrationJdbcTemplate: JdbcTemplate
	private lateinit var publicJdbcTemplate: JdbcTemplate

	private val migratedDocument = "52998224725"
	private val migrationSchema = "customer_status_${UUID.randomUUID().toString().replace("-", "")}"

	@BeforeAll
	fun migrateExistingCustomer() {
		dataSource.connection.use { connection ->
			connection.createStatement().use { statement ->
				statement.execute("CREATE SCHEMA ${quoted(migrationSchema)}")
			}
		}

		val flywayToV6 = Flyway.configure()
			.dataSource(dataSource)
			.schemas(migrationSchema)
			.defaultSchema(migrationSchema)
			.target("6")
			.load()
		flywayToV6.migrate()

		val isolatedConnection = dataSource.connection.apply { schema = migrationSchema }
		migrationConnection = isolatedConnection
		migrationJdbcTemplate = JdbcTemplate(SingleConnectionDataSource(isolatedConnection, true))
		publicJdbcTemplate = JdbcTemplate(dataSource)
		migrationJdbcTemplate.update(
			"INSERT INTO clientes(id, documento, nome) VALUES (?, ?, ?)",
			UUID.randomUUID().toString(),
			migratedDocument,
			"Cliente migrado",
		)

		Flyway.configure()
			.dataSource(dataSource)
			.schemas(migrationSchema)
			.defaultSchema(migrationSchema)
			.load()
			.migrate()
	}

	@AfterAll
	fun removeOwnedMigrationSchema() {
		migrationConnection?.close()
		dataSource.connection.use { connection ->
			connection.createStatement().use { statement ->
				statement.execute("DROP SCHEMA ${quoted(migrationSchema)} CASCADE")
			}
		}
	}

	@Test
	fun `existing and new customers are active by default`() {
		assertEquals(
			migrationSchema,
			migrationJdbcTemplate.queryForObject("SELECT current_schema()", String::class.java),
		)
		assertEquals(
			"ACTIVE",
			migrationJdbcTemplate.queryForObject(
				"SELECT status FROM clientes WHERE documento = ?",
				String::class.java,
				migratedDocument,
			),
		)

		val newDocument = "11144477735"
		migrationJdbcTemplate.update(
			"INSERT INTO clientes(id, documento, nome) VALUES (?, ?, ?)",
			UUID.randomUUID().toString(),
			newDocument,
			"Cliente novo",
		)
		assertEquals(
			"ACTIVE",
			migrationJdbcTemplate.queryForObject(
				"SELECT status FROM clientes WHERE documento = ?",
				String::class.java,
				newDocument,
			),
		)
	}

	@Test
	fun `blocked status round trips through repository and mapper`() {
		val customer = Customer(
			id = UUID.randomUUID(),
			fiscalDocument = TaxDocument.parse(uniqueCpf()),
			name = "Cliente bloqueado",
			status = CustomerStatus.BLOCKED,
		)

		try {
			val saved = customerRepository.save(customer)
			val reloaded = customerRepository.findById(saved.id).orElseThrow()

			assertEquals(CustomerStatus.BLOCKED, reloaded.status)
			assertEquals(
				"BLOCKED",
				publicJdbcTemplate.queryForObject(
					"SELECT status FROM clientes WHERE id = ?",
					String::class.java,
					saved.id.toString(),
				),
			)
		} finally {
			customerRepository.deleteById(customer.id)
		}
	}

	@Test
	fun `database rejects invalid customer status`() {
		assertFailsWith<DataIntegrityViolationException> {
			migrationJdbcTemplate.update(
				"INSERT INTO clientes(id, documento, nome, status) VALUES (?, ?, ?, ?)",
				UUID.randomUUID().toString(),
				"93541134780",
				"Cliente invalido",
				"INVALID",
			)
		}
	}

	private fun quoted(identifier: String): String = "\"${identifier.replace("\"", "\"\"")}\""

	private fun uniqueCpf(): String {
		val base = UUID.randomUUID().toString()
			.filter(Char::isDigit)
			.padEnd(9, '1')
			.take(9)
			.toCharArray()
			.also { digits ->
				if (digits.all { it == digits[0] }) digits[8] = if (digits[8] == '9') '8' else '9'
			}
			.concatToString()
		fun checkDigit(value: String, initialFactor: Int): Int {
			val remainder = value.mapIndexed { index, digit ->
				digit.digitToInt() * (initialFactor - index)
			}.sum() % 11
			return if (remainder < 2) 0 else 11 - remainder
		}
		val first = checkDigit(base, 10)
		val second = checkDigit(base + first, 11)
		return "$base$first$second"
	}
}
