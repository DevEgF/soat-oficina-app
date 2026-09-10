package com.soat.tech.challenge.oficina.infrastructure.jpa.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "clientes")
class CustomerEntity(
	@Id
	@Column(length = 36)
	var id: String = "",
	@Column(nullable = false, unique = true, length = 14, name = "documento")
	var documentDigits: String = "",
	@Column(nullable = false, name = "nome")
	var name: String = "",
	@Column
	var email: String? = null,
	@Column(length = 50, name = "telefone")
	var phone: String? = null,
	@Column(nullable = false, length = 16)
	var status: String = "ACTIVE",
)
