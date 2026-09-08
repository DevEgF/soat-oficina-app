package com.soat.tech.challenge.oficina.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReleasedDependenciesTest {
	@Test
	fun `build does not depend on snapshot repositories or versions`() {
		val build = File("build.gradle.kts").readText()
		val settings = File("settings.gradle.kts").readText()
		assertFalse(build.contains("SNAPSHOT"))
		assertFalse(settings.contains("repo.spring.io/snapshot"))
		assertTrue(build.contains("""id("org.springframework.boot") version "4.1.1"""))
		assertTrue(build.contains("""kotlin("jvm") version "2.4.10"""))
	}
}
