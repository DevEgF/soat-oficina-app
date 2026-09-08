package com.soat.tech.challenge.oficina.domain.model

import java.util.UUID

data class Customer(
	val id: UUID,
	val fiscalDocument: TaxDocument,
	val name: String,
	val email: String? = null,
	val phone: String? = null,
	val status: CustomerStatus = CustomerStatus.ACTIVE,
)
