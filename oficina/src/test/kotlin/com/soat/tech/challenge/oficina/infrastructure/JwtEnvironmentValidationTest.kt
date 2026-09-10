package com.soat.tech.challenge.oficina.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtValidationException
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.security.MessageDigest
import java.time.Instant
import java.util.Date
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class JwtEnvironmentValidationTest {

    @Autowired private lateinit var decoder: JwtDecoder
    @Autowired private lateinit var context: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()
    }

    @Test
    fun `environment accepts only hml and prod`() {
        assertEquals("hml", AppEnvironment(" hml ").value)
        assertEquals("prod", AppEnvironment("prod").value)
        assertThrows(IllegalArgumentException::class.java) { AppEnvironment("local") }
        assertThrows(IllegalArgumentException::class.java) { AppEnvironment("") }
    }

    @Test
    fun `decoder accepts customer and staff subjects in active environment`() {
        assertEquals(UUID_SUBJECT, decoder.decode(token(subject = UUID_SUBJECT)).subject)
        assertEquals("admin", decoder.decode(token(subject = "admin", scope = listOf("ADMIN"))).subject)
    }

    @Test
    fun `decoder rejects token from another environment`() {
        assertThrows(JwtValidationException::class.java) { decoder.decode(token(environment = "prod")) }
    }

    @Test
    fun `decoder rejects missing audience`() {
        assertThrows(JwtValidationException::class.java) { decoder.decode(token(audience = null)) }
    }

    @Test
    fun `decoder rejects wrong issuer`() {
        assertThrows(JwtValidationException::class.java) { decoder.decode(token(issuer = "other")) }
    }

    @Test
    fun `decoder rejects expired token`() {
        assertThrows(JwtValidationException::class.java) {
            decoder.decode(
                token(
                    issuedAt = Instant.now().minusSeconds(120),
                    expiresAt = Instant.now().minusSeconds(60),
                ),
            )
        }
    }

    @Test
    fun `decoder rejects missing environment`() {
        assertThrows(JwtValidationException::class.java) { decoder.decode(token(environment = null)) }
    }

    @Test
    fun `decoder rejects corrupted signature`() {
        val valid = token()
        val corrupted = valid.dropLast(1) + if (valid.last() == 'a') "b" else "a"
        assertThrows(JwtException::class.java) { decoder.decode(corrupted) }
    }

    @Test
    fun `issuer created staff token carries issuer audience and environment`() {
        val body = mockMvc.perform(
            post("/api/public/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"admin"}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString

        val serialized = ObjectMapper().readTree(body)["accessToken"].asText()
        val claims = SignedJWT.parse(serialized).jwtClaimsSet
        assertEquals("oficina", claims.issuer)
        assertEquals(listOf("oficina-api"), claims.audience)
        assertEquals("hml", claims.getStringClaim("env"))
        decoder.decode(serialized)
    }

    @Test
    fun `real customer bearer reaches protected route while staff token is forbidden`() {
        mockMvc.perform(
            get("/api/customer/os/acompanhar")
                .param("codigo", "UNKNOWN")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${token()}"),
        ).andExpect(status().isNotFound)

        mockMvc.perform(
            get("/api/customer/os/acompanhar")
                .param("codigo", "UNKNOWN")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(subject = "admin", scope = listOf("ADMIN"))}"),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `real bearer from another environment is unauthorized`() {
        mockMvc.perform(
            get("/api/customer/os/acompanhar")
                .param("codigo", "UNKNOWN")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(environment = "prod")}"),
        ).andExpect(status().isUnauthorized)
    }

    private fun token(
        subject: String = UUID_SUBJECT,
        issuer: String = "oficina",
        audience: String? = "oficina-api",
        environment: String? = "hml",
        issuedAt: Instant = Instant.now(),
        expiresAt: Instant = Instant.now().plusSeconds(300),
        scope: List<String> = listOf("CUSTOMER"),
    ): String {
        val builder = JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(subject)
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .claim("scope", scope)
        audience?.let(builder::audience)
        environment?.let { builder.claim("env", it) }
        return SignedJWT(JWSHeader(JWSAlgorithm.HS256), builder.build()).also {
            it.sign(MACSigner(SIGNING_KEY))
        }.serialize()
    }

    private companion object {
        const val RAW_SECRET = "test-jwt-secret-adequado-para-hs256-bytes"
        const val UUID_SUBJECT = "8f647d7f-da76-418b-abcd-5cffbc62e391"
        val SIGNING_KEY: ByteArray = MessageDigest.getInstance("SHA-256")
            .digest(RAW_SECRET.toByteArray(Charsets.UTF_8))
    }
}
