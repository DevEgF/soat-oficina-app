package com.soat.tech.challenge.oficina.integration

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID
import javax.crypto.SecretKey
import kotlin.random.Random

@SpringBootTest
@ActiveProfiles("test")
class CustomerJwtFlowIntegrationTest {

	@Autowired private lateinit var context: WebApplicationContext
	@Autowired private lateinit var jwtSigningKey: SecretKey

	private val mapper = ObjectMapper()
	private lateinit var mockMvc: MockMvc
	private lateinit var tokens: CustomerJwtTestTokenFactory

	@BeforeEach
	fun setup() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context)
			.apply<DefaultMockMvcBuilder>(springSecurity())
			.build()
		tokens = CustomerJwtTestTokenFactory(jwtSigningKey)
	}

	@Test
	fun `customer bearer owns quote decision and tracking without document input`() {
		val suffix = UUID.randomUUID().toString().take(8)
		val serviceId = create(
			"/api/admin/servicos-catalogo",
			"""{"name":"JWT-$suffix","description":"x","priceCents":1000,"estimatedMinutes":30}""",
			"SCOPE_ADMIN",
		)["id"].asText()
		val partId = create(
			"/api/admin/pecas",
			"""{"code":"JWT-$suffix","name":"Part","priceCents":100,"stockQuantity":10,"replenishmentPoint":5}""",
			"SCOPE_ADMIN",
		)["id"].asText()
		val workOrder = create(
			"/api/attendant/ordens-servico",
			"""{
				"customerTaxId":"${UniqueCustomerFixture.cpf()}","customerName":"JWT Customer",
				"plate":"${UniqueCustomerFixture.plate()}","vehicleBrand":"VW","vehicleModel":"Gol","vehicleYear":2020,
				"services":[{"catalogServiceId":"$serviceId","quantity":1}],
				"parts":[{"partId":"$partId","quantity":1}]
			}""".trimIndent(),
			"SCOPE_ATTENDANT",
		)
		val id = workOrder["id"].asText()
		val code = workOrder["trackingCode"].asText()
		val customerId = UUID.fromString(workOrder["customerId"].asText())
		advanceToCustomerDecision(id)

		mockMvc.perform(get("/api/customer/os/acompanhar").param("codigo", code))
			.andExpect(status().isUnauthorized)
		mockMvc.perform(
			get("/api/customer/os/acompanhar")
				.param("codigo", code)
				.bearer(tokens.staff()),
		).andExpect(status().isForbidden)
		mockMvc.perform(
			get("/api/customer/os/acompanhar")
				.param("codigo", code)
				.bearer(tokens.customerSubject("not-a-uuid")),
		).andExpect(status().isUnauthorized)
		mockMvc.perform(
			get("/api/customer/os/acompanhar")
				.param("codigo", code)
				.bearer(tokens.customer(UUID.randomUUID())),
		).andExpect(status().isNotFound)

		val customerToken = tokens.customer(customerId)
		mockMvc.perform(
			post("/api/customer/os/orcamento/decisao")
				.bearer(customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"codigo":"$code","decisao":"APROVADO"}"""),
		)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.status").value("AWAITING_PARTS_RELEASE"))
		mockMvc.perform(
			get("/api/customer/os/acompanhar")
				.param("codigo", code)
				.bearer(customerToken),
		)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.status").value("AWAITING_PARTS_RELEASE"))
	}

	private fun create(url: String, body: String, scope: String) = mapper.readTree(
		mockMvc.perform(
			post(url)
				.with(jwt().authorities(SimpleGrantedAuthority(scope)))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body),
		).andExpect(status().isCreated).andReturn().response.contentAsString,
	)

	private fun advanceToCustomerDecision(id: String) {
		postAs("/api/technician/ordens-servico/$id/iniciar-diagnostico", "SCOPE_TECHNICIAN")
		postAs("/api/technician/ordens-servico/$id/submeter-plano", "SCOPE_TECHNICIAN")
		postAs("/api/admin/ordens-servico/$id/aprovar-interno", "SCOPE_ADMIN")
		postAs("/api/attendant/ordens-servico/$id/enviar-orcamento-cliente", "SCOPE_ATTENDANT")
	}

	private fun postAs(url: String, scope: String) {
		mockMvc.perform(post(url).with(jwt().authorities(SimpleGrantedAuthority(scope))))
			.andExpect(status().isOk)
	}

	private fun org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder.bearer(token: String) =
		header(HttpHeaders.AUTHORIZATION, "Bearer $token")
}
