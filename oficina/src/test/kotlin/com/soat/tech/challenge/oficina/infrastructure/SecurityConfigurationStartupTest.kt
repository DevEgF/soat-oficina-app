package com.soat.tech.challenge.oficina.infrastructure

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.BeansException
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.mock.env.MockEnvironment
import java.util.stream.Stream

class SecurityConfigurationStartupTest {

    @Test
    fun `startup rejects missing and blank jwt secrets`() {
        assertSigningContextFails(emptyMap())
        assertSigningContextFails(mapOf(JWT_PROPERTY to "   "))
        assertSigningContextFails(mapOf(JWT_PROPERTY to "short-secret"))
    }

    @ParameterizedTest
    @MethodSource("committedJwtSecrets")
    fun `startup rejects committed jwt secrets outside local and test`(secret: String) {
        assertSigningContextFails(mapOf(JWT_PROPERTY to secret))
        assertSigningContextFails(mapOf(JWT_PROPERTY to " $secret "))
        assertContextFails(JwtSigningSecret::class.java, mapOf(JWT_PROPERTY to secret), "docker", "test")
        assertContextFails(JwtSigningSecret::class.java, mapOf(JWT_PROPERTY to secret), "docker", "local")
    }

    @Test
    fun `signing secret preserves accepted raw utf8 bytes`() {
        val raw = " 01234567890123456789012345678901 "
        val secret = JwtSigningSecret(raw, MockEnvironment().withProperty("unused", "value"))
        assertEquals(raw, secret.rawValue)
    }

    @ParameterizedTest
    @MethodSource("staffPasswords")
    fun `startup rejects each missing staff password`(property: String, ignoredDefault: String) {
        assertStaffContextFails(validStaffProperties() - property)
    }

    @ParameterizedTest
    @MethodSource("staffPasswords")
    fun `startup rejects each blank staff password`(property: String, ignoredDefault: String) {
        assertStaffContextFails(validStaffProperties() + (property to "  "))
    }

    @ParameterizedTest
    @MethodSource("staffPasswords")
    fun `startup rejects each default staff password`(property: String, defaultPassword: String) {
        assertStaffContextFails(validStaffProperties() + (property to defaultPassword))
        assertContextFails(StaffCredentials::class.java, validStaffProperties() + (property to defaultPassword), "docker", "test")
        assertContextFails(StaffCredentials::class.java, validStaffProperties() + (property to defaultPassword), "docker", "local")
    }

    private fun assertSigningContextFails(properties: Map<String, String>) =
        assertContextFails(JwtSigningSecret::class.java, properties)

    private fun assertStaffContextFails(properties: Map<String, String>) =
        assertContextFails(StaffCredentials::class.java, properties)

    private fun assertContextFails(component: Class<*>, properties: Map<String, String>, vararg profiles: String) {
        AnnotationConfigApplicationContext().use { context ->
            context.environment.setActiveProfiles(*profiles)
            context.environment.propertySources.addFirst(MapPropertySource("test", properties))
            context.register(component)
            assertThrows(BeansException::class.java) { context.refresh() }
        }
    }

    private fun validStaffProperties(): Map<String, String> = mapOf(
        "app.security.master.password" to "secure-master-value",
        "app.security.admin.password" to "secure-admin-value",
        "app.security.attendant.password" to "secure-attendant-value",
        "app.security.technician.password" to "secure-technician-value",
        "app.security.warehouse.password" to "secure-warehouse-value",
    )

    companion object {
        private const val JWT_PROPERTY = "app.jwt.secret"

        @JvmStatic
        fun committedJwtSecrets(): Stream<String> = Stream.of(
            "dev-oficina-jwt-secret-troque-em-producao-32b",
            "local-oficina-jwt-secret-for-development-only",
            "test-jwt-secret-adequado-para-hs256-bytes",
        )

        @JvmStatic
        fun staffPasswords(): Stream<Arguments> = Stream.of(
            Arguments.of("app.security.master.password", "master"),
            Arguments.of("app.security.admin.password", "admin"),
            Arguments.of("app.security.attendant.password", "atendente"),
            Arguments.of("app.security.technician.password", "tecnico"),
            Arguments.of("app.security.warehouse.password", "almoxarife"),
        )
    }
}
