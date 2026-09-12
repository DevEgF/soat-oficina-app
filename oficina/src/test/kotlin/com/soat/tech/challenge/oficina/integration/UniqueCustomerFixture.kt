package com.soat.tech.challenge.oficina.integration

import kotlin.random.Random

/** Valid synthetic identifiers with a fresh namespace for each test invocation. */
object UniqueCustomerFixture {
    fun cpf(): String {
        val digits = MutableList(9) { Random.nextInt(10) }
        if (digits.distinct().size == 1) digits[0] = (digits[0] + 1) % 10
        repeat(2) {
            val remainder = digits.mapIndexed { index, digit -> digit * (digits.size + 1 - index) }.sum() % 11
            digits.add(if (remainder < 2) 0 else 11 - remainder)
        }
        return digits.joinToString("")
    }

    fun plate(): String = buildString {
        repeat(3) { append(('A'..'Z').random()) }
        repeat(4) { append(Random.nextInt(10)) }
    }
}
