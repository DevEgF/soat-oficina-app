package com.soat.tech.challenge.oficina.integration

import com.fasterxml.jackson.databind.JsonNode
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
import org.springframework.web.filter.CharacterEncodingFilter
import java.util.UUID
import javax.crypto.SecretKey

@SpringBootTest
@ActiveProfiles("test")
class WorkOrderFlowIntegrationTest {

	@Autowired
	private lateinit var webApplicationContext: WebApplicationContext

	@Autowired
	private lateinit var jwtSigningKey: SecretKey

	private val objectMapper = ObjectMapper()

	private lateinit var mockMvc: MockMvc
	private lateinit var customerTokens: CustomerJwtTestTokenFactory

	@BeforeEach
	fun setup() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
			.addFilter<DefaultMockMvcBuilder>(CharacterEncodingFilter("UTF-8", true))
			.apply<DefaultMockMvcBuilder>(springSecurity())
			.build()
		customerTokens = CustomerJwtTestTokenFactory(jwtSigningKey)
	}

	private fun loginToken(username: String, password: String): String {
		val body = mockMvc
			.perform(
				post("/api/public/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"username":"$username","password":"$password"}"""),
			)
			.andExpect(status().isOk)
			.andReturn()
			.response
			.contentAsString
		return objectMapper.readTree(body)["accessToken"].asText()
	}

	private fun postJsonWithScope(url: String, json: String, scope: String, expectedStatus: Int = 200): String =
		mockMvc
			.perform(
				post(url)
					.with(jwt().authorities(SimpleGrantedAuthority(scope)))
					.contentType(MediaType.APPLICATION_JSON)
					.content(json),
			)
			.andExpect(status().`is`(expectedStatus))
			.andReturn()
			.response
			.contentAsString

	@Test
	fun `fluxo swimlane catalogo peca os reservas almoxarife cliente e metricas`() {
		val fixtureSuffix = UUID.randomUUID().toString()
		val fixtureCpf = UniqueCustomerFixture.cpf()
		val fixturePlate = UniqueCustomerFixture.plate()
		kotlin.test.assertTrue(loginToken("admin", "admin").isNotBlank())

		val servicoJson = postJsonWithScope(
			"/api/admin/servicos-catalogo",
			"""{"name":"Troca de oleo","description":"Exemplo","priceCents":15000,"estimatedMinutes":45}""",
			"SCOPE_ADMIN",
			201,
		)
		val servicoId = objectMapper.readTree(servicoJson)["id"].asText()

		val pecaJson = postJsonWithScope(
			"/api/admin/pecas",
			"""{"code":"FILTRO-$fixtureSuffix","name":"Filtro oleo","priceCents":3500,"stockQuantity":10,"replenishmentPoint":5}""",
			"SCOPE_ADMIN",
			201,
		)
		val pecaId = objectMapper.readTree(pecaJson)["id"].asText()

		val osJson = postJsonWithScope(
			"/api/attendant/ordens-servico",
			"""
			{
			  "customerTaxId": "$fixtureCpf",
			  "customerName": "Maria Teste",
			  "plate": "$fixturePlate",
			  "vehicleBrand": "VW",
			  "vehicleModel": "Gol",
			  "vehicleYear": 2020,
			  "services": [{"catalogServiceId": "$servicoId", "quantity": 1}],
			  "parts": [{"partId": "$pecaId", "quantity": 2}]
			}
			""".trimIndent(),
			"SCOPE_ATTENDANT",
			201,
		)
		val os: JsonNode = objectMapper.readTree(osJson)
		val osId = os["id"].asText()
		val codigo = os["trackingCode"].asText()
		val customerToken = customerTokens.customer(UUID.fromString(os["customerId"].asText()))

		mockMvc.perform(
			post("/api/technician/ordens-servico/$osId/iniciar-diagnostico")
				.with(jwt().authorities(SimpleGrantedAuthority("SCOPE_TECHNICIAN"))),
		).andExpect(status().isOk).andExpect(jsonPath("$.status").value("IN_DIAGNOSIS"))

		mockMvc.perform(
			post("/api/technician/ordens-servico/$osId/submeter-plano")
				.with(jwt().authorities(SimpleGrantedAuthority("SCOPE_TECHNICIAN"))),
		).andExpect(status().isOk).andExpect(jsonPath("$.status").value("PENDING_INTERNAL_APPROVAL"))

		mockMvc.perform(
			post("/api/admin/ordens-servico/$osId/aprovar-interno")
				.with(jwt().authorities(SimpleGrantedAuthority("SCOPE_ADMIN"))),
		).andExpect(status().isOk).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))

		mockMvc.perform(
			post("/api/attendant/ordens-servico/$osId/enviar-orcamento-cliente")
				.with(jwt().authorities(SimpleGrantedAuthority("SCOPE_ATTENDANT"))),
		).andExpect(status().isOk).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))

		mockMvc.perform(
			post("/api/customer/os/aprovar-orcamento")
				.param("codigo", codigo)
				.header(HttpHeaders.AUTHORIZATION, "Bearer $customerToken"),
		)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.status").value("AWAITING_PARTS_RELEASE"))

		mockMvc.perform(
			post("/api/warehouse/ordens-servico/$osId/confirmar-saida")
				.with(jwt().authorities(SimpleGrantedAuthority("SCOPE_WAREHOUSE"))),
		).andExpect(status().isNoContent)

		mockMvc.perform(
			get("/api/customer/os/acompanhar")
				.param("codigo", codigo)
				.header(HttpHeaders.AUTHORIZATION, "Bearer $customerToken"),
		)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.status").value("IN_EXECUTION"))
			.andExpect(jsonPath("$.vehiclePlate").value(fixturePlate))

		mockMvc.perform(
			post("/api/technician/ordens-servico/$osId/concluir-servicos")
				.with(jwt().authorities(SimpleGrantedAuthority("SCOPE_TECHNICIAN"))),
		).andExpect(status().isOk).andExpect(jsonPath("$.status").value("FINALIZED"))

		mockMvc.perform(
			post("/api/attendant/ordens-servico/$osId/registrar-entrega")
				.with(jwt().authorities(SimpleGrantedAuthority("SCOPE_ATTENDANT"))),
		).andExpect(status().isOk).andExpect(jsonPath("$.status").value("DELIVERED"))

		val metricasJson = mockMvc.perform(
			get("/api/admin/metricas/tempo-medio-execucao-servicos")
				.with(jwt().authorities(SimpleGrantedAuthority("SCOPE_ADMIN"))),
		)
			.andExpect(status().isOk)
			.andReturn().response.contentAsString
		val metricas = objectMapper.readTree(metricasJson)
		val temMetricaDoServico = metricas.elements().asSequence()
			.any { it.path("catalogServiceId").asText() == UUID.fromString(servicoId).toString() }
		kotlin.test.assertTrue(temMetricaDoServico, "esperado metrica para servicoId=$servicoId em $metricasJson")
	}

	@Test
	fun `admin sem token recebe nao autorizado`() {
		mockMvc.perform(get("/api/admin/clientes")).andExpect(status().isUnauthorized)
	}

	@Test
	fun `jwt do login admin permite listar clientes`() {
		val t = loginToken("admin", "admin")
		mockMvc.perform(
			get("/api/admin/clientes").header(HttpHeaders.AUTHORIZATION, "Bearer $t"),
		).andExpect(status().isOk)
	}

	@Test
	fun `atendente nao acessa endpoint admin de pecas`() {
		val t = loginToken("atendente", "atendente")
		mockMvc.perform(
			get("/api/admin/pecas").header(HttpHeaders.AUTHORIZATION, "Bearer $t"),
		).andExpect(status().isForbidden)
	}
}
