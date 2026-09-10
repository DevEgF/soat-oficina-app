package com.soat.tech.challenge.oficina.infrastructure

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource

class MigrationExitRunnerTest {
    @Test
    fun `normal context does not contain an exit runner`() {
        AnnotationConfigApplicationContext(MigrationExitRunner::class.java).use { context ->
            assertTrue(context.getBeansOfType(MigrationExitRunner::class.java).isEmpty())
            assertTrue(context.isActive)
        }
    }

    @Test
    fun `migration context closes after runner executes`() {
        AnnotationConfigApplicationContext().use { context ->
            context.environment.propertySources.addFirst(MapPropertySource("migration", mapOf("app.migration-only" to "true")))
            context.register(MigrationExitRunner::class.java)
            context.refresh()
            assertTrue(context.isActive)
            context.getBean(MigrationExitRunner::class.java).run(DefaultApplicationArguments())
            assertFalse(context.isActive)
        }
    }
}
