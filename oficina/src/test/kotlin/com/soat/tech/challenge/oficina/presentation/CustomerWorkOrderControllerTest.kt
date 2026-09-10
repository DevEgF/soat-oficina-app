package com.soat.tech.challenge.oficina.presentation

import com.soat.tech.challenge.oficina.application.WorkOrderApplicationService
import com.soat.tech.challenge.oficina.application.api.dto.WorkOrderTrackingResponse
import com.soat.tech.challenge.oficina.domain.exception.NotFoundException
import com.soat.tech.challenge.oficina.domain.model.WorkOrderStatus
import com.soat.tech.challenge.oficina.domain.port.BusinessMetricsPort
import com.soat.tech.challenge.oficina.infrastructure.web.CorrelationIdFilter
import com.soat.tech.challenge.oficina.infrastructure.AppEnvironment
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import java.util.UUID

@SpringJUnitConfig
@WebAppConfiguration
@ContextConfiguration(
    classes = [
        CustomerWorkOrderController::class,
        RestExceptionHandler::class,
        CorrelationIdFilter::class,
        TestSecurityConfig::class,
    ],
)
class CustomerWorkOrderControllerTest {

    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var service: WorkOrderApplicationService

    private lateinit var mockMvc: MockMvc
    private val customerId = UUID.randomUUID()
    private val trackingCode = "OS-123"
    private val response = WorkOrderTrackingResponse(
        trackingCode = trackingCode,
        status = WorkOrderStatus.PENDING_APPROVAL,
        statusLabel = WorkOrderStatus.PENDING_APPROVAL.label,
        totalCents = 1_000,
        vehiclePlate = "ABC1234",
        maskedCustomerTaxId = "***.529.982-**",
    )

    @BeforeEach
    fun setup() {
        val builder: DefaultMockMvcBuilder = MockMvcBuilders.webAppContextSetup(context)
        builder.addFilters<DefaultMockMvcBuilder>(context.getBean(CorrelationIdFilter::class.java))
        builder.apply<DefaultMockMvcBuilder>(springSecurity())
        mockMvc = builder.build()
    }

    @Test
    fun `tracking without token returns 401`() {
        mockMvc.perform(get("/api/customer/os/acompanhar").param("codigo", trackingCode))
            .andExpect(status().isUnauthorized)
            .andExpect(header().exists("X-Correlation-Id"))
    }

    @Test
    fun `tracking with staff scope returns 403`() {
        mockMvc.perform(
            get("/api/customer/os/acompanhar")
                .param("codigo", trackingCode)
                .with(customerAuthentication("admin", "SCOPE_ADMIN")),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `tracking with malformed customer subject returns 401`() {
        mockMvc.perform(
            get("/api/customer/os/acompanhar")
                .param("codigo", trackingCode)
                .with(customerAuthentication("not-a-uuid", "SCOPE_CUSTOMER")),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `malformed budget decision json returns 400`() {
        mockMvc.perform(
            post("/api/customer/os/orcamento/decisao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"codigo":"$trackingCode","decisao":}""")
                .with(customerAuthentication(customerId.toString(), "SCOPE_CUSTOMER")),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `missing tracking query parameter returns 400`() {
        mockMvc.perform(
            get("/api/customer/os/acompanhar")
                .with(customerAuthentication(customerId.toString(), "SCOPE_CUSTOMER")),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `tracking another customer's order returns 404`() {
        val otherCustomerId = UUID.randomUUID()
        every { service.trackForCustomer(otherCustomerId, trackingCode) } throws NotFoundException("Work order not found")

        mockMvc.perform(
            get("/api/customer/os/acompanhar")
                .param("codigo", trackingCode)
                .with(customerAuthentication(otherCustomerId.toString(), "SCOPE_CUSTOMER")),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `tracking own order returns 200`() {
        every { service.trackForCustomer(customerId, trackingCode) } returns response

        mockMvc.perform(
            get("/api/customer/os/acompanhar")
                .param("codigo", trackingCode)
                .with(customerAuthentication(customerId.toString(), "SCOPE_CUSTOMER")),
        ).andExpect(status().isOk)
    }

    @Test
    fun `customer can approve quote with identity from token`() {
        every { service.approveCustomerQuoteForCustomer(customerId, trackingCode) } returns response

        mockMvc.perform(
            post("/api/customer/os/aprovar-orcamento")
                .param("codigo", trackingCode)
                .with(customerAuthentication(customerId.toString(), "SCOPE_CUSTOMER")),
        ).andExpect(status().isOk)
    }

    @Test
    fun `customer can reject quote with identity from token`() {
        every { service.rejectCustomerQuoteForCustomer(customerId, trackingCode) } returns response

        mockMvc.perform(
            post("/api/customer/os/reprovar-orcamento")
                .param("codigo", trackingCode)
                .with(customerAuthentication(customerId.toString(), "SCOPE_CUSTOMER")),
        ).andExpect(status().isOk)
    }

    @Test
    fun `budget decision body needs no customer document`() {
        every { service.approveCustomerQuoteForCustomer(customerId, trackingCode) } returns response

        mockMvc.perform(
            post("/api/customer/os/orcamento/decisao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"codigo":"$trackingCode","decisao":"APROVADO"}""")
                .with(customerAuthentication(customerId.toString(), "SCOPE_CUSTOMER")),
        ).andExpect(status().isOk)
    }

    private fun token(subject: String): Jwt = Jwt.withTokenValue("token")
        .header("alg", "none")
        .subject(subject)
        .build()

    private fun customerAuthentication(subject: String, authority: String) = authentication(
        JwtAuthenticationToken(token(subject), listOf(SimpleGrantedAuthority(authority))),
    )
}

@TestConfiguration
@EnableWebSecurity
@EnableWebMvc
private class TestSecurityConfig : WebMvcConfigurer {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(AuthenticationPrincipalArgumentResolver())
    }

    @Bean
    fun service(): WorkOrderApplicationService = mockk()

    @Bean
    fun appEnvironment(): AppEnvironment = AppEnvironment("hml")

    @Bean
    fun businessMetrics(): BusinessMetricsPort = mockk(relaxed = true)

    @Bean
    fun jwtDecoder(): JwtDecoder = mockk()

    @Bean
    fun securityFilterChain(http: HttpSecurity, jwtDecoder: JwtDecoder): SecurityFilterChain {
        http.csrf { it.disable() }
        http.authorizeHttpRequests { authorize ->
            authorize.requestMatchers("/api/customer/**").hasAuthority("SCOPE_CUSTOMER")
            authorize.anyRequest().denyAll()
        }
        http.oauth2ResourceServer { oauth2 -> oauth2.jwt { it.decoder(jwtDecoder) } }
        return http.build()
    }
}
