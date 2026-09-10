package com.soat.tech.challenge.oficina.infrastructure

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class StaffCredentials(
    @Value("\${app.security.master.password}") masterPassword: String,
    @Value("\${app.security.admin.password}") adminPassword: String,
    @Value("\${app.security.attendant.password}") attendantPassword: String,
    @Value("\${app.security.technician.password}") technicianPassword: String,
    @Value("\${app.security.warehouse.password}") warehousePassword: String,
    springEnvironment: Environment,
) {
    private val values: Map<String, String>

    init {
        val profiles = springEnvironment.activeProfiles
        val allowDevelopmentDefaults = profiles.isNotEmpty() && profiles.all { it == "local" || it == "test" }
        values = mapOf(
            "master" to masterPassword,
            "admin" to adminPassword,
            "atendente" to attendantPassword,
            "tecnico" to technicianPassword,
            "almoxarife" to warehousePassword,
        ).mapValues { (username, password) ->
            password.trim().also {
                require(it.isNotBlank()) { "Password for $username must not be blank" }
                require(!it.isUnresolvedPlaceholder()) { "Password for $username must be configured" }
                require(allowDevelopmentDefaults || it != username) {
                    "Default password is forbidden for $username outside local/test"
                }
            }
        }
    }

    fun passwordFor(username: String): String = values.getValue(username)

    private fun String.isUnresolvedPlaceholder(): Boolean = startsWith("\${") && endsWith("}")
}
