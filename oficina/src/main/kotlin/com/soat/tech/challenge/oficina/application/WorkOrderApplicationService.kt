package com.soat.tech.challenge.oficina.application

import com.soat.tech.challenge.oficina.application.api.dto.WorkOrderTrackingResponse
import com.soat.tech.challenge.oficina.application.api.dto.CreateWorkOrderRequest
import com.soat.tech.challenge.oficina.application.api.dto.UpdateDiagnosisPlanRequest
import com.soat.tech.challenge.oficina.application.api.dto.WorkOrderResponse
import com.soat.tech.challenge.oficina.application.api.toResponse
import com.soat.tech.challenge.oficina.domain.exception.BusinessRuleException
import com.soat.tech.challenge.oficina.domain.exception.InsufficientStockException
import com.soat.tech.challenge.oficina.domain.exception.NotFoundException
import com.soat.tech.challenge.oficina.domain.model.Customer
import com.soat.tech.challenge.oficina.domain.model.TaxDocument
import com.soat.tech.challenge.oficina.domain.model.PartLine
import com.soat.tech.challenge.oficina.domain.model.ServiceLine
import com.soat.tech.challenge.oficina.domain.model.WorkOrder
import com.soat.tech.challenge.oficina.domain.model.WorkOrderStatus
import com.soat.tech.challenge.oficina.domain.model.LicensePlate
import com.soat.tech.challenge.oficina.domain.model.Vehicle
import com.soat.tech.challenge.oficina.domain.port.CustomerRepository
import com.soat.tech.challenge.oficina.domain.port.WorkOrderRepository
import com.soat.tech.challenge.oficina.domain.port.PartRepository
import com.soat.tech.challenge.oficina.domain.port.PartReservationRepository
import com.soat.tech.challenge.oficina.domain.port.CatalogServiceRepository
import com.soat.tech.challenge.oficina.domain.port.VehicleRepository
import com.soat.tech.challenge.oficina.domain.port.NotificationPort
import com.soat.tech.challenge.oficina.domain.port.BusinessMetricsPort
import com.soat.tech.challenge.oficina.domain.port.WorkOrderStage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.util.UUID
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
class WorkOrderApplicationService(
    private val workOrders: WorkOrderRepository,
    private val customers: CustomerRepository,
    private val vehicles: VehicleRepository,
    private val catalogServices: CatalogServiceRepository,
    private val parts: PartRepository,
    private val reservations: PartReservationRepository,
    private val notificationPort: NotificationPort,
    private val businessMetrics: BusinessMetricsPort,
    private val clock: Clock,
) {

	private fun catalogServiceName(id: UUID): String? =
		catalogServices.findById(id).map { it.name }.orElse(null)

	private fun partName(id: UUID): String? =
		parts.findById(id).map { it.name }.orElse(null)

	private fun WorkOrder.toDto(): WorkOrderResponse =
		toResponse({ catalogServiceName(it) }, { partName(it) })

	private fun now() = clock.instant()

	private fun emitAfterCommit(metric: () -> Unit) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
				override fun afterCommit() {
					runCatching(metric)
				}
			})
		} else {
			runCatching(metric)
		}
	}

	private fun validateAndReplaceReservations(wo: WorkOrder) {
		val map = wo.partLines.associate { it.partId to it.quantity }
		for ((partId, need) in map) {
			val part = parts.findById(partId).orElseThrow { NotFoundException("Part not found") }
			val reservedElsewhere = reservations.sumPendingReservedQuantityExcludingWorkOrder(partId, wo.id)
			val available = part.stockQuantity - reservedElsewhere
			if (need > available) {
				throw InsufficientStockException(partId.toString(), need, available)
			}
		}
		reservations.replacePendingReservations(wo.id, map)
	}

	private fun cancelReservationsIfAny(woId: UUID) {
		reservations.cancelPendingForWorkOrder(woId)
	}

	@Transactional
	fun create(req: CreateWorkOrderRequest): WorkOrderResponse {
		val doc = TaxDocument.parse(req.customerTaxIdDigits)
		val customer = customers.findByFiscalDocument(doc).orElseGet {
			customers.save(
				Customer(
					id = UUID.randomUUID(),
					fiscalDocument = doc,
					name = req.customerName.trim(),
					email = req.customerEmail?.trim()?.takeIf { it.isNotEmpty() },
					phone = req.customerPhone?.trim()?.takeIf { it.isNotEmpty() },
				),
			)
		}
		val plate = LicensePlate.parse(req.plate)
		val vehicle = vehicles.findByLicensePlate(plate).orElseGet {
			vehicles.save(
				Vehicle(
					id = UUID.randomUUID(),
					customerId = customer.id,
					licensePlate = plate,
					brand = req.vehicleBrand.trim(),
					model = req.vehicleModel.trim(),
					year = req.vehicleYear!!,
				),
			)
		}
		if (vehicle.customerId != customer.id) {
			throw BusinessRuleException("Vehicle belongs to another customer")
		}
		val serviceLines = req.services.map { s ->
			val cat = catalogServices.findById(s.catalogServiceId!!)
				.orElseThrow { NotFoundException("Catalog service not found") }
			ServiceLine(
				catalogServiceId = cat.id,
				quantity = s.quantity!!,
				unitPriceCents = cat.priceCents,
			)
		}
		val partLines = req.parts.map { p ->
			val part = parts.findById(p.partId!!)
				.orElseThrow { NotFoundException("Part not found") }
			PartLine(
				partId = part.id,
				quantity = p.quantity!!,
				unitPriceCents = part.priceCents,
			)
		}
		val wo = WorkOrder.create(
			customerId = customer.id,
			vehicleId = vehicle.id,
			serviceLines = serviceLines,
			partLines = partLines,
		)
		val response = workOrders.save(wo).toDto()
		emitAfterCommit { businessMetrics.workOrderCreated() }
		return response
	}

	@Transactional(readOnly = true)
	fun list(): List<WorkOrderResponse> =
	    workOrders.findAll()
	        .filterNot { it.status.hiddenFromListing }
	        .sortedWith(compareBy({ it.status.listingPriority }, { it.createdAt }))
	        .map { it.toDto() }

	@Transactional(readOnly = true)
	fun get(id: UUID): WorkOrderResponse =
		workOrders.findById(id).map { it.toDto() }.orElseThrow { NotFoundException("Work order not found") }

	@Transactional(readOnly = true)
	fun trackForCustomer(customerId: UUID, trackingCode: String): WorkOrderTrackingResponse {
		val wo = loadWorkOrderForCustomer(customerId, trackingCode)
		val customer = customers.findById(wo.customerId).orElseThrow { NotFoundException("Customer not found") }
		if (
			wo.status == WorkOrderStatus.RECEIVED ||
			wo.status == WorkOrderStatus.IN_DIAGNOSIS ||
			wo.status == WorkOrderStatus.PENDING_INTERNAL_APPROVAL
		) {
			throw NotFoundException("Work order not available for customer tracking yet")
		}
		val vehicle = vehicles.findById(wo.vehicleId).orElseThrow { NotFoundException("Vehicle not found") }
		return WorkOrderTrackingResponse(
			trackingCode = wo.trackingCode,
			status = wo.status,
			statusLabel = wo.status.label,
			totalCents = wo.totalCents,
			vehiclePlate = vehicle.licensePlate.normalized,
			maskedCustomerTaxId = maskTaxId(customer.fiscalDocument.digits),
		)
	}

	private fun maskTaxId(d: String): String = when (d.length) {
		11 -> "***.${d.take(3)}.${d.substring(3, 6)}-**"
		14 -> "**.${d.substring(2, 5)}.${d.substring(5, 8)}/****-**"
		else -> "***"
	}

	private fun loadWorkOrderForCustomer(customerId: UUID, trackingCode: String): WorkOrder {
		val wo = workOrders.findByTrackingCode(trackingCode.trim())
			.orElseThrow { NotFoundException("Work order not found") }
		if (wo.customerId != customerId) {
			throw NotFoundException("Work order not found")
		}
		return wo
	}

	@Transactional
	fun approveCustomerQuoteForCustomer(customerId: UUID, trackingCode: String): WorkOrderTrackingResponse {
		val wo = loadWorkOrderForCustomer(customerId, trackingCode)
		// Idempotência: aprovação já aplicada (liberada ao almoxarife ou em execução) não reaplica a transição.
		if (wo.status == WorkOrderStatus.AWAITING_PARTS_RELEASE || wo.status == WorkOrderStatus.IN_EXECUTION) {
			return trackForCustomer(customerId, trackingCode)
		}
		// Swimlane: a aprovação do cliente apenas libera a OS ao almoxarife (AWAITING_PARTS_RELEASE).
		// A execução só inicia na confirmação de saída das peças (WarehouseApplicationService.confirmStockExitForWorkOrder).
		val completedAt = now()
		val startedAt = wo.quoteSentAt
		wo.approveCustomerQuote(completedAt)
		requireNotNull(startedAt) { "Quote timestamp is required" }
		workOrders.save(wo)
		val response = trackForCustomer(customerId, trackingCode)
		emitAfterCommit { businessMetrics.stageCompleted(WorkOrderStage.APPROVAL, Duration.between(startedAt, completedAt)) }
		return response
	}

	@Transactional
	fun rejectCustomerQuoteForCustomer(customerId: UUID, trackingCode: String): WorkOrderTrackingResponse {
		val wo = loadWorkOrderForCustomer(customerId, trackingCode)
		// Idempotência: decisão de recusa reenviada não reaplica a transição.
		if (wo.status == WorkOrderStatus.CANCELLED) {
			return trackForCustomer(customerId, trackingCode)
		}
		cancelReservationsIfAny(wo.id)
		val completedAt = now()
		val startedAt = wo.quoteSentAt
		wo.rejectCustomerQuote(completedAt)
		requireNotNull(startedAt) { "Quote timestamp is required" }
		workOrders.save(wo)
		val response = trackForCustomer(customerId, trackingCode)
		emitAfterCommit { businessMetrics.stageCompleted(WorkOrderStage.APPROVAL, Duration.between(startedAt, completedAt)) }
		return response
	}

	@Transactional
	fun startDiagnosis(id: UUID): WorkOrderResponse {
		val wo = workOrders.findById(id).orElseThrow { NotFoundException("Work order not found") }
		wo.startDiagnosis(now())
		return workOrders.save(wo).toDto()
	}

	@Transactional
	fun updateDiagnosisPlan(id: UUID, req: UpdateDiagnosisPlanRequest): WorkOrderResponse {
		val wo = workOrders.findById(id).orElseThrow { NotFoundException("Work order not found") }
		val serviceLines = req.services.map { s ->
			val cat = catalogServices.findById(s.catalogServiceId!!)
				.orElseThrow { NotFoundException("Catalog service not found") }
			ServiceLine(
				catalogServiceId = cat.id,
				quantity = s.quantity!!,
				unitPriceCents = cat.priceCents,
			)
		}
		val partLines = req.parts.map { p ->
			val part = parts.findById(p.partId!!)
				.orElseThrow { NotFoundException("Part not found") }
			PartLine(
				partId = part.id,
				quantity = p.quantity!!,
				unitPriceCents = part.priceCents,
			)
		}
		wo.updateDiagnosisPlan(serviceLines, partLines, req.diagnosisNotes)
		return workOrders.save(wo).toDto()
	}

	/** Técnico: submete plano e cria reservas de peças. */
	@Transactional
	fun submitPlanForInternalApproval(id: UUID): WorkOrderResponse {
		val wo = workOrders.findById(id).orElseThrow { NotFoundException("Work order not found") }
		val completedAt = now()
		val startedAt = wo.diagnosedAt
		wo.submitPlanForInternalApproval(completedAt)
		requireNotNull(startedAt) { "Diagnosis timestamp is required" }
		validateAndReplaceReservations(wo)
		val response = workOrders.save(wo).toDto()
		emitAfterCommit { businessMetrics.stageCompleted(WorkOrderStage.DIAGNOSIS, Duration.between(startedAt, completedAt)) }
		return response
	}

	/** Administrador: reprova plano interno. */
	@Transactional
	fun rejectInternal(id: UUID): WorkOrderResponse {
		val wo = workOrders.findById(id).orElseThrow { NotFoundException("Work order not found") }
		cancelReservationsIfAny(wo.id)
		wo.rejectInternal(now())
		return workOrders.save(wo).toDto()
	}

	/** Administrador: aprova execução interna. */
	@Transactional
	fun approveInternal(id: UUID): WorkOrderResponse {
		val wo = workOrders.findById(id).orElseThrow { NotFoundException("Work order not found") }
		wo.approveInternal(now())
		return workOrders.save(wo).toDto()
	}

	/** Atendente: envia orçamento ao cliente. */
	@Transactional
	fun sendQuoteToCustomer(id: UUID): WorkOrderResponse {
		val wo = workOrders.findById(id).orElseThrow { NotFoundException("Work order not found") }
		if (wo.status == WorkOrderStatus.PENDING_APPROVAL) {
			return wo.toDto()
		}
		wo.sendQuoteToCustomer(now())
		val saved = workOrders.save(wo)
		val customer = customers.findById(saved.customerId).orElseThrow { NotFoundException("Customer not found") }
		val vehicle = vehicles.findById(saved.vehicleId).orElseThrow { NotFoundException("Vehicle not found") }
		notificationPort.notifyQuoteSentToCustomer(
			customerName = customer.name,
			customerEmail = customer.email,
			vehicleModel = "${vehicle.brand} ${vehicle.model}",
			quoteTotalCents = saved.totalCents,
		)
		return saved.toDto()
	}

	@Transactional
	fun returnToDiagnosis(id: UUID): WorkOrderResponse {
		val wo = workOrders.findById(id).orElseThrow { NotFoundException("Work order not found") }
		cancelReservationsIfAny(wo.id)
		wo.returnToDiagnosis(now())
		return workOrders.save(wo).toDto()
	}

	@Transactional
	fun completeServices(id: UUID): WorkOrderResponse {
		val wo = workOrders.findById(id).orElseThrow { NotFoundException("Work order not found") }
		val pending = reservations.findPendingByWorkOrder(wo.id)
		if (pending.isNotEmpty()) {
			throw IllegalStateException(
				"Existem reservas de peças pendentes de confirmação de saída pelo almoxarife",
			)
		}
		val completedAt = now()
		val startedAt = wo.workStartedAt
		wo.completeServices(completedAt)
		requireNotNull(startedAt) { "Execution timestamp is required" }
		val saved = workOrders.save(wo)
		val customer = customers.findById(saved.customerId).orElseThrow { NotFoundException("Customer not found") }
		val vehicle = vehicles.findById(saved.vehicleId).orElseThrow { NotFoundException("Vehicle not found") }
		notificationPort.notifyWorkOrderFinalized(
		    customerName = customer.name,
		    customerEmail = customer.email,
		    vehicleModel = "${vehicle.brand} ${vehicle.model}",
		)
		val response = saved.toDto()
		emitAfterCommit { businessMetrics.stageCompleted(WorkOrderStage.EXECUTION, Duration.between(startedAt, completedAt)) }
		return response
	}

	@Transactional
	fun registerDelivery(id: UUID): WorkOrderResponse {
		val wo = workOrders.findById(id).orElseThrow { NotFoundException("Work order not found") }
		wo.registerDelivery(now())
		val saved = workOrders.save(wo)
		val customer = customers.findById(saved.customerId).orElseThrow { NotFoundException("Customer not found") }
		val vehicle = vehicles.findById(saved.vehicleId).orElseThrow { NotFoundException("Vehicle not found") }
		notificationPort.notifyDeliveryRegistered(
			customerName = customer.name,
			customerEmail = customer.email,
			vehicleModel = "${vehicle.brand} ${vehicle.model}",
		)
		return saved.toDto()
	}
}
