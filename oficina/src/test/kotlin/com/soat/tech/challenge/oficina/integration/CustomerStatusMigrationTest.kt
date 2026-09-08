package com.soat.tech.challenge.oficina.integration

import com.soat.tech.challenge.oficina.domain.model.Customer
import com.soat.tech.challenge.oficina.domain.model.CustomerStatus
import com.soat.tech.challenge.oficina.domain.model.TaxDocument
import com.soat.tech.challenge.oficina.domain.port.CustomerRepository
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerStatusMigrationTest {

	@Autowired
	private lateinit var dataSource: DataSource

	@Autowired
	private lateinit var customerRepository: CustomerRepository

	private lateinit var jdbcTemplate: JdbcTemplate

	private val migratedDocument = "52998224725"

	@BeforeAll
	fun migrateExistingCustomer() {
		val flywayToV6 = Flyway.configure()
			.dataSource(dataSource)
			.cleanDisabled(false)
			.target("6")
			.load()
		flywayToV6.clean()
		flywayToV6.migrate()

		jdbcTemplate = JdbcTemplate(dataSource)
		jdbcTemplate.update(
			"INSERT INTO clientes(id, documento, nome) VALUES (?, ?, ?)",
			UUID.randomUUID().toString(),
			migratedDocument,
			"Cliente migrado",
		)

		Flyway.configure().dataSource(dataSource).load().migrate()
	}

	@Test
	fun `existing and new customers are active by default`() {
		assertEquals(
			"ACTIVE",
			jdbcTemplate.queryForObject(
				"SELECT status FROM clientes WHERE documento = ?",
				String::class.java,
				migratedDocument,
			),
		)

		val newDocument = "11144477735"
		jdbcTemplate.update(
			"INSERT INTO clientes(id, documento, nome) VALUES (?, ?, ?)",
			UUID.randomUUID().toString(),
			newDocument,
			"Cliente novo",
		)
		assertEquals(
			"ACTIVE",
			jdbcTemplate.queryForObject(
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
			fiscalDocument = TaxDocument.parse("39053344705"),
			name = "Cliente bloqueado",
			status = CustomerStatus.BLOCKED,
		)

		val saved = customerRepository.save(customer)
		val reloaded = customerRepository.findById(saved.id).orElseThrow()

		assertEquals(CustomerStatus.BLOCKED, reloaded.status)
		assertEquals(
			"BLOCKED",
			jdbcTemplate.queryForObject(
				"SELECT status FROM clientes WHERE id = ?",
				String::class.java,
				saved.id.toString(),
			),
		)
	}

	@Test
	fun `database rejects invalid customer status`() {
		assertFailsWith<DataIntegrityViolationException> {
			jdbcTemplate.update(
				"INSERT INTO clientes(id, documento, nome, status) VALUES (?, ?, ?, ?)",
				UUID.randomUUID().toString(),
				"93541134780",
				"Cliente invalido",
				"INVALID",
			)
		}
	}
}
