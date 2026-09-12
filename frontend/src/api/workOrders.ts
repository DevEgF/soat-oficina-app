import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import { useAuthStore } from '@/auth/store'
import { trackCustomerWorkOrder, approveCustomerQuote, rejectCustomerQuote } from './customerAuth'
import type { WorkOrderResponse, CreateWorkOrderRequest, UpdateDiagnosisPlanRequest } from '@/lib/types'

function getListEndpoint(scope: string | null) {
  if (scope === 'MASTER') return '/api/admin/ordens-servico'
  if (scope === 'ADMIN') return '/api/admin/ordens-servico'
  if (scope === 'ATTENDANT') return '/api/attendant/ordens-servico'
  if (scope === 'TECHNICIAN') return '/api/technician/ordens-servico'
  if (scope === 'WAREHOUSE') return '/api/warehouse/ordens-servico'
  return null
}

function getDetailEndpoint(scope: string | null, id: string) {
  if (scope === 'MASTER') return `/api/admin/ordens-servico/${id}`
  if (scope === 'ADMIN') return `/api/admin/ordens-servico/${id}`
  if (scope === 'ATTENDANT') return `/api/attendant/ordens-servico/${id}`
  if (scope === 'TECHNICIAN') return `/api/technician/ordens-servico/${id}`
  if (scope === 'WAREHOUSE') return `/api/warehouse/ordens-servico/${id}`
  return null
}

export function useWorkOrders() {
  const { scope } = useAuthStore()
  const endpoint = getListEndpoint(scope)
  return useQuery({
    queryKey: ['workOrders', scope],
    queryFn: async () => {
      const { data } = await api.get<WorkOrderResponse[]>(endpoint!)
      return data
    },
    enabled: !!endpoint,
  })
}

export function useWorkOrder(id: string) {
  const { scope } = useAuthStore()
  const endpoint = getDetailEndpoint(scope, id)
  return useQuery({
    queryKey: ['workOrder', id, scope],
    queryFn: async () => {
      const { data } = await api.get<WorkOrderResponse>(endpoint!)
      return data
    },
    enabled: !!endpoint && !!id,
  })
}

export function useCreateWorkOrder() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (req: CreateWorkOrderRequest) => {
      const { data } = await api.post<WorkOrderResponse>('/api/attendant/ordens-servico', req)
      return data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['workOrders'] }),
  })
}

function workOrderAction(path: string) {
  return async (id: string) => {
    const { data } = await api.post<WorkOrderResponse>(path.replace(':id', id))
    return data
  }
}

export function useStartDiagnosis() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: workOrderAction('/api/technician/ordens-servico/:id/iniciar-diagnostico'),
    onSuccess: (_, id) => qc.invalidateQueries({ queryKey: ['workOrder', id] }),
  })
}

export function useSubmitPlan() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: workOrderAction('/api/technician/ordens-servico/:id/submeter-plano'),
    onSuccess: (_, id) => qc.invalidateQueries({ queryKey: ['workOrder', id] }),
  })
}

export function useUpdateDiagnosisPlan() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, req }: { id: string; req: UpdateDiagnosisPlanRequest }) => {
      const { data } = await api.put<WorkOrderResponse>(`/api/technician/ordens-servico/${id}/plano`, req)
      return data
    },
    onSuccess: (_, vars) => qc.invalidateQueries({ queryKey: ['workOrder', vars.id] }),
  })
}

export function useCompleteServices() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: workOrderAction('/api/technician/ordens-servico/:id/concluir-servicos'),
    onSuccess: (_, id) => qc.invalidateQueries({ queryKey: ['workOrder', id] }),
  })
}

export function useApproveInternal() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: workOrderAction('/api/admin/ordens-servico/:id/aprovar-interno'),
    onSuccess: (_, id) => qc.invalidateQueries({ queryKey: ['workOrder', id] }),
  })
}

export function useRejectInternal() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: workOrderAction('/api/admin/ordens-servico/:id/reprovar-interno'),
    onSuccess: (_, id) => qc.invalidateQueries({ queryKey: ['workOrder', id] }),
  })
}

export function useSendQuoteToCustomer() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: workOrderAction('/api/attendant/ordens-servico/:id/enviar-orcamento-cliente'),
    onSuccess: (_, id) => qc.invalidateQueries({ queryKey: ['workOrder', id] }),
  })
}

export function useReturnToDiagnosis() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: workOrderAction('/api/attendant/ordens-servico/:id/voltar-diagnostico'),
    onSuccess: (_, id) => qc.invalidateQueries({ queryKey: ['workOrder', id] }),
  })
}

export function useRegisterDelivery() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: workOrderAction('/api/attendant/ordens-servico/:id/registrar-entrega'),
    onSuccess: (_, id) => {
      qc.invalidateQueries({ queryKey: ['workOrder', id] })
      qc.invalidateQueries({ queryKey: ['workOrders'] })
    },
  })
}

export function useTrackWorkOrder() {
  return useMutation({ mutationFn: trackCustomerWorkOrder })
}

export function useApproveCustomerQuote() {
  return useMutation({ mutationFn: approveCustomerQuote })
}

export function useRejectCustomerQuote() {
  return useMutation({ mutationFn: rejectCustomerQuote })
}
