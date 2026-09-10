package com.soat.tech.challenge.oficina.infrastructure

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class JwtSigningSecret(
    @Value("\${app.jwt.secret}") val rawValue: String,
    springEnvironment: Environment,
) {
    init {
        require(rawValue.isNotBlank()) { "app.jwt.secret must not be blank" }
        require(!rawValue.isUnresolvedPlaceholder()) { "app.jwt.secret must be configured" }
        require(rawValue.toByteArray(Charsets.UTF_8).size >= MINIMUM_BYTES) {
            "app.jwt.secret must contain at least $MINIMUM_BYTES UTF-8 bytes"
        }
        val profiles = springEnvironment.activeProfiles
        val allowCommittedSentinel = profiles.isNotEmpty() && profiles.all { it == "local" || it == "test" }
        require(allowCommittedSentinel || rawValue.trim() !in COMMITTED_SENTINELS) {
            "A committed development JWT secret is forbidden outside local/test"
        }
    }

    private companion object {
        const val MINIMUM_BYTES = 32
        val COMMITTED_SENTINELS = setOf(
            "dev-oficina-jwt-secret-troque-em-producao-32b",
            "local-oficina-jwt-secret-for-development-only",
            "test-jwt-secret-adequado-para-hs256-bytes",
        )
    }

    private fun String.isUnresolvedPlaceholder(): Boolean = startsWith("\${") && endsWith("}")
}
