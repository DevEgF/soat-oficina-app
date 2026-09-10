package com.soat.tech.challenge.oficina.infrastructure.persistence

import com.soat.tech.challenge.oficina.domain.model.Customer
import com.soat.tech.challenge.oficina.domain.model.CustomerStatus
import com.soat.tech.challenge.oficina.domain.model.TaxDocument
import com.soat.tech.challenge.oficina.domain.model.PartLine
import com.soat.tech.challenge.oficina.domain.model.ServiceLine
import com.soat.tech.challenge.oficina.domain.model.WorkOrder
import com.soat.tech.challenge.oficina.domain.model.Part
import com.soat.tech.challenge.oficina.domain.model.LicensePlate
import com.soat.tech.challenge.oficina.domain.model.CatalogService
import com.soat.tech.challenge.oficina.domain.model.WorkOrderStatus
import com.soat.tech.challenge.oficina.domain.model.Vehicle
import com.soat.tech.challenge.oficina.infrastructure.jpa.entity.CustomerEntity
import com.soat.tech.challenge.oficina.infrastructure.jpa.entity.WorkOrderEntity
import com.soat.tech.challenge.oficina.infrastructure.jpa.entity.PartEntity
import com.soat.tech.challenge.oficina.infrastructure.jpa.entity.CatalogServiceEntity
import com.soat.tech.challenge.oficina.infrastructure.jpa.entity.VehicleEntity
import java.util.UUID

fun CustomerEntity.toDomain(): Customer = Customer(
	id = UUID.fromString(id),
	fiscalDocument = TaxDocument.parse(documentDigits),
	name = name,
	email = email,
	phone = phone,
	status = CustomerStatus.valueOf(status),
)

fun Customer.toEntity(): CustomerEntity = CustomerEntity(
	id = id.toString(),
	documentDigits = fiscalDocument.digits,
	name = name,
	email = email,
	phone = phone,
	status = status.name,
)

fun VehicleEntity.toDomain(): Vehicle = Vehicle(
	id = UUID.fromString(id),
	customerId = UUID.fromString(customer!!.id),
	licensePlate = LicensePlate.parse(licensePlate),
	brand = brand,
	model = model,
	year = year,
)

fun CatalogServiceEntity.toDomain(): CatalogService = CatalogService(
	id = UUID.fromString(id),
	name = name,
	description = description,
	priceCents = priceCents,
	estimatedMinutes = estimatedMinutes,
)

fun PartEntity.toDomain(): Part = Part(
	id = UUID.fromString(id),
	code = code,
	name = name,
	priceCents = priceCents,
	stockQuantity = stockQuantity,
	replenishmentPoint = replenishmentPoint,
)

fun WorkOrderEntity.toDomain(): WorkOrder {
	val linesS = serviceLines.toList().map {
		ServiceLine(
			catalogServiceId = UUID.fromString(it.catalogService!!.id),
			quantity = it.quantity,
			unitPriceCents = it.unitPriceCents,
		)
	}
	val linesP = partLines.toList().map {
		PartLine(
			partId = UUID.fromString(it.part!!.id),
			quantity = it.quantity,
			unitPriceCents = it.unitPriceCents,
		)
	}
	return WorkOrder(
		id = UUID.fromString(id),
		trackingCode = trackingCode,
		customerId = UUID.fromString(customer!!.id),
		vehicleId = UUID.fromString(vehicle!!.id),
		diagnosisNotes = diagnosisNotes,
		status = WorkOrderStatus.valueOf(status),
		serviceLines = linesS,
		partLines = linesP,
		servicesTotalCents = servicesTotalCents,
		partsTotalCents = partsTotalCents,
		totalCents = totalCents,
		diagnosedAt = diagnosedAt,
		planSubmittedAt = planSubmittedAt,
		internalApprovedAt = internalApprovedAt,
		quoteSentAt = quoteSentAt,
		approvedAt = approvedAt,
		workStartedAt = workStartedAt,
		completedAt = completedAt,
		deliveredAt = deliveredAt,
		cancelledAt = cancelledAt,
		returnedToDiagnosisAt = returnedToDiagnosisAt,
		createdAt = createdAt,
	)
}
