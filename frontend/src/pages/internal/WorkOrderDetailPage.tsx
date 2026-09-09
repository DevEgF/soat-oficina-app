import { useMemo, useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { ArrowLeft } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/api/client'
import { useWorkOrder, useStartDiagnosis, useSubmitPlan, useCompleteServices, useApproveInternal, useRejectInternal, useReturnToDiagnosis, useRegisterDelivery, useUpdateDiagnosisPlan } from '@/api/workOrders'
import { StatusBadge } from '@/components/StatusBadge'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { formatCurrency } from '@/lib/utils'
import { STATUS_LABELS, STATUS_ORDER } from '@/lib/constants'
import { useAuthStore } from '@/auth/store'
import type { WorkOrderStatus, CatalogServiceResponse, PartAvailabilityResponse } from '@/lib/types'

type Tab = 'services' | 'parts' | 'progress'

export default function WorkOrderDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { scope } = useAuthStore()
  const { data: wo, isLoading } = useWorkOrder(id!)
  const [tab, setTab] = useState<Tab>('progress')
  const [confirm, setConfirm] = useState<string | null>(null)
  const [notes, setNotes] = useState('')
  const [serviceLines, setServiceLines] = useState<Array<{ catalogServiceId: string; quantity: number }>>([])
  const [partLines, setPartLines] = useState<Array<{ partId: string; quantity: number }>>([])

  const startDiagnosis = useStartDiagnosis()
  const updateDiagnosisPlan = useUpdateDiagnosisPlan()
  const submitPlan = useSubmitPlan()
  const completeServices = useCompleteServices()
  const approveInternal = useApproveInternal()
  const rejectInternal = useRejectInternal()
  const returnToDiag = useReturnToDiagnosis()
  const registerDelivery = useRegisterDelivery()
  const serviceCatalogEndpoint = scope === 'ADMIN' || scope === 'MASTER' ? '/api/admin/servicos-catalogo' : '/api/technician/servicos-catalogo'
  const partsAvailabilityEndpoint = '/api/technician/pecas/disponibilidade'
  const { data: catalogServices = [] } = useQuery({
    queryKey: ['diagnosis-catalog-services', scope],
    queryFn: async () => {
      const { data } = await api.get<CatalogServiceResponse[]>(serviceCatalogEndpoint)
      return data
    },
    enabled: scope === 'TECHNICIAN' || scope === 'MASTER',
  })
  const { data: parts = [] } = useQuery({
    queryKey: ['diagnosis-parts', scope],
    queryFn: async () => {
      const { data } = await api.get<PartAvailabilityResponse[]>(partsAvailabilityEndpoint)
      return data
    },
    enabled: scope === 'TECHNICIAN' || scope === 'MASTER',
  })

  const showBudgetSection = wo?.status !== 'RECEIVED'
  const canEditDiagnosisPlan = (scope === 'TECHNICIAN' || scope === 'MASTER') && wo?.status === 'IN_DIAGNOSIS'

  const [previousWorkOrder, setPreviousWorkOrder] = useState<typeof wo>(undefined)
  if (wo && wo !== previousWorkOrder) {
    setPreviousWorkOrder(wo)
    setServiceLines(wo.services.map((line) => ({ catalogServiceId: line.catalogServiceId, quantity: line.quantity })))
    setPartLines(wo.parts.map((line) => ({ partId: line.partId, quantity: line.quantity })))
    setNotes(wo.diagnosisNotes ?? '')
  }

  const diagnosisTotals = useMemo(() => {
    const servicesTotal = serviceLines.reduce((sum, line) => {
      const svc = catalogServices.find((item) => item.id === line.catalogServiceId)
      return sum + (svc ? svc.priceCents * line.quantity : 0)
    }, 0)
    const partsTotal = partLines.reduce((sum, line) => {
      const part = parts.find((item) => item.partId === line.partId)
      return sum + (part ? part.priceCents * line.quantity : 0)
    }, 0)
    return {
      servicesTotal,
      partsTotal,
      total: servicesTotal + partsTotal,
    }
  }, [catalogServices, partLines, parts, serviceLines])

  function toggleService(id: string) {
    setServiceLines((prev) => {
      const line = prev.find((item) => item.catalogServiceId === id)
      if (line) return prev.filter((item) => item.catalogServiceId !== id)
      return [...prev, { catalogServiceId: id, quantity: 1 }]
    })
  }

  function setServiceQty(id: string, qty: number) {
    setServiceLines((prev) => prev.map((item) => item.catalogServiceId === id ? { ...item, quantity: Math.max(1, qty) } : item))
  }

  function togglePart(id: string) {
    setPartLines((prev) => {
      const line = prev.find((item) => item.partId === id)
      if (line) return prev.filter((item) => item.partId !== id)
      return [...prev, { partId: id, quantity: 1 }]
    })
  }

  function setPartQty(id: string, qty: number) {
    setPartLines((prev) => prev.map((item) => item.partId === id ? { ...item, quantity: Math.max(1, qty) } : item))
  }

  async function runAction(
    mutation: { mutateAsync: (id: string) => Promise<unknown> },
    successMsg: string,
    onSuccess?: () => void,
  ) {
    try {
      await mutation.mutateAsync(id!)
      toast.success(successMsg)
      onSuccess?.()
    } catch { /* interceptor handles */ }
    setConfirm(null)
  }

  async function handleSaveDiagnosisPlan() {
    try {
      await updateDiagnosisPlan.mutateAsync({
        id: id!,
        req: {
          services: serviceLines,
          parts: partLines,
          diagnosisNotes: notes || undefined,
        },
      })
      toast.success('Plano de diagnóstico atualizado!')
    } catch {
      // interceptor
    }
  }

  if (isLoading) return <div className="p-6 text-stone-500 text-sm">Carregando...</div>
  if (!wo) return <div className="p-6 text-stone-500 text-sm">OS não encontrada.</div>

  const actions: { key: string; label: string; variant: 'primary' | 'danger' | 'secondary'; condition: boolean; onConfirm: () => Promise<void> }[] = [
    {
      key: 'startDiag', label: 'Iniciar Diagnóstico', variant: 'primary',
      condition: (scope === 'TECHNICIAN' || scope === 'MASTER') && wo.status === 'RECEIVED',
      onConfirm: () => runAction(startDiagnosis, 'Diagnóstico iniciado!'),
    },
    {
      key: 'submitPlan', label: 'Submeter Plano', variant: 'primary',
      condition: (scope === 'TECHNICIAN' || scope === 'MASTER') && wo.status === 'IN_DIAGNOSIS',
      onConfirm: () => runAction(submitPlan, 'Plano submetido para aprovação interna!'),
    },
    {
      key: 'approveInt', label: 'Aprovar Internamente', variant: 'primary',
      condition: (scope === 'ADMIN' || scope === 'MASTER') && wo.status === 'PENDING_INTERNAL_APPROVAL',
      onConfirm: () => runAction(approveInternal, 'Aprovação interna realizada!', () => navigate('/internal/ordens-servico')),
    },
    {
      key: 'rejectInt', label: 'Reprovar Internamente', variant: 'danger',
      condition: (scope === 'ADMIN' || scope === 'MASTER') && wo.status === 'PENDING_INTERNAL_APPROVAL',
      onConfirm: () => runAction(rejectInternal, 'OS reprovada.'),
    },
    {
      key: 'returnDiag', label: 'Voltar ao Diagnóstico', variant: 'secondary',
      condition: (scope === 'ATTENDANT' || scope === 'MASTER') && wo.status === 'PENDING_APPROVAL',
      onConfirm: () => runAction(returnToDiag, 'OS retornada ao diagnóstico.'),
    },
    {
      key: 'complete', label: 'Concluir Serviços', variant: 'primary',
      condition: (scope === 'TECHNICIAN' || scope === 'MASTER') && wo.status === 'IN_EXECUTION',
      onConfirm: () => runAction(completeServices, 'Serviços concluídos!'),
    },
    {
      key: 'deliver', label: 'Registrar Entrega', variant: 'primary',
      condition: (scope === 'ATTENDANT' || scope === 'MASTER') && wo.status === 'FINALIZED',
      onConfirm: () => runAction(registerDelivery, 'Veículo entregue!'),
    },
  ]

  const visibleActions = actions.filter(a => a.condition)
  const activeAction = confirm ? actions.find(a => a.key === confirm) : null

  return (
    <div className="p-6 max-w-3xl">
      {/* Header */}
      <div className="flex items-start gap-4 mb-6">
        <Link to="/internal/ordens-servico" className="mt-1 text-stone-400 hover:text-stone-700">
          <ArrowLeft size={18} />
        </Link>
        <div className="flex-1">
          <div className="flex items-center gap-3 flex-wrap">
            <h1 className="text-lg font-bold text-stone-900">Ordem de Serviço</h1>
            <StatusBadge status={wo.status} />
          </div>
          <p className="text-xs text-stone-400 font-mono mt-0.5">Código: {wo.trackingCode}</p>
        </div>
      </div>

      {/* Info cards */}
      <div className="grid grid-cols-2 gap-4 mb-5">
        <InfoCard title="Cliente" lines={[`ID: ${wo.customerId.slice(0, 8)}...`]} />
        <InfoCard title="Veículo" lines={[`ID: ${wo.vehicleId.slice(0, 8)}...`]} />
      </div>

      {showBudgetSection && (
        <div className="flex gap-0 border-b-2 border-orange-200 mb-4">
          {(['services', 'parts', 'progress'] as Tab[]).map(t => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={`px-4 py-2 text-sm font-medium transition-colors ${tab === t ? 'text-orange-600 border-b-2 border-orange-600 -mb-0.5' : 'text-stone-500 hover:text-stone-700'}`}
            >
              {t === 'services' ? 'Serviços' : t === 'parts' ? 'Peças' : 'Progresso'}
            </button>
          ))}
        </div>
      )}

      {!showBudgetSection && (
        <div className="bg-orange-50 border border-orange-200 rounded-xl p-4 mb-5">
          <p className="text-sm text-stone-600">
            Inicie o diagnóstico para montar o orçamento com serviços e peças.
          </p>
        </div>
      )}

      {showBudgetSection && canEditDiagnosisPlan && (
        <div className="bg-white border border-orange-200 rounded-xl p-4 mb-5 space-y-4">
          <p className="text-xs font-semibold text-stone-500 uppercase tracking-wide">Montagem do diagnóstico</p>

          <section>
            <h3 className="text-sm font-semibold text-stone-700 mb-2">Serviços</h3>
            <div className="space-y-2 max-h-56 overflow-y-auto">
              {catalogServices.map((service) => {
                const line = serviceLines.find((item) => item.catalogServiceId === service.id)
                return (
                  <div key={service.id} className="flex items-center gap-3 border border-orange-100 rounded-lg p-2">
                    <input type="checkbox" checked={!!line} onChange={() => toggleService(service.id)} className="accent-orange-600" />
                    <div className="flex-1 text-sm">
                      <p className="font-medium text-stone-800">{service.name}</p>
                      <p className="text-stone-400">{formatCurrency(service.priceCents)}</p>
                    </div>
                    {line && (
                      <input
                        type="number"
                        min="1"
                        value={line.quantity}
                        onChange={(e) => setServiceQty(service.id, Number(e.target.value))}
                        className="w-16 px-2 py-1 border border-orange-200 rounded text-sm text-center"
                      />
                    )}
                  </div>
                )
              })}
            </div>
          </section>

          <section>
            <h3 className="text-sm font-semibold text-stone-700 mb-2">Peças</h3>
            <div className="space-y-2 max-h-56 overflow-y-auto">
              {parts.map((part) => {
                const line = partLines.find((item) => item.partId === part.partId)
                const selectedQty = line?.quantity ?? 0
                const unavailable = part.availableQuantity <= 0
                const insufficientForSelection = selectedQty > part.availableQuantity
                const needsReplenishmentAlert =
                  selectedQty > 0 &&
                  part.replenishmentPoint != null &&
                  (part.availableQuantity - selectedQty) <= part.replenishmentPoint
                return (
                  <div key={part.partId} className="flex items-center gap-3 border border-orange-100 rounded-lg p-2">
                    <input
                      type="checkbox"
                      checked={!!line}
                      disabled={unavailable}
                      onChange={() => togglePart(part.partId)}
                      className="accent-orange-600 disabled:opacity-50"
                    />
                    <div className="flex-1 text-sm">
                      <p className="font-medium text-stone-800">{part.name}</p>
                      <p className="text-stone-400">{formatCurrency(part.priceCents)}</p>
                      <p className="text-xs text-stone-400">
                        Disponível: {part.availableQuantity} (Estoque: {part.stockQuantity}, Reservado: {part.pendingReservedQuantity})
                      </p>
                      {unavailable && <p className="text-xs text-red-600 font-medium">Indisponível (sem saldo livre)</p>}
                      {insufficientForSelection && <p className="text-xs text-red-600 font-medium">Quantidade selecionada acima do disponível</p>}
                      {needsReplenishmentAlert && <p className="text-xs text-yellow-700 font-medium">Aviso: atingiu ponto de reposição</p>}
                    </div>
                    {line && (
                      <input
                        type="number"
                        min="1"
                        value={line.quantity}
                        onChange={(e) => setPartQty(part.partId, Number(e.target.value))}
                        className="w-16 px-2 py-1 border border-orange-200 rounded text-sm text-center"
                      />
                    )}
                  </div>
                )
              })}
            </div>
          </section>

          <section>
            <label className="block text-sm font-semibold text-stone-700 mb-2">Observações do diagnóstico</label>
            <textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              rows={3}
              className="w-full px-3 py-2 border border-orange-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-orange-400"
              placeholder="Descreva o diagnóstico técnico..."
            />
          </section>

          <button
            onClick={handleSaveDiagnosisPlan}
            disabled={updateDiagnosisPlan.isPending}
            className="px-4 py-2 bg-orange-600 text-white text-sm font-semibold rounded-lg hover:bg-orange-700 disabled:opacity-60"
          >
            {updateDiagnosisPlan.isPending ? 'Salvando...' : 'Salvar plano'}
          </button>
        </div>
      )}

      {showBudgetSection && tab === 'services' && (
        <div className="flex flex-col gap-2 mb-5">
          {wo.services.length === 0 ? (
            <p className="text-sm text-stone-400 py-4 text-center">Nenhum serviço registrado.</p>
          ) : wo.services.map(s => (
            <div key={s.catalogServiceId} className="flex justify-between items-center bg-white border border-orange-100 rounded-lg px-4 py-3">
              <div>
                <p className="text-sm font-medium text-stone-800">{s.serviceName ?? s.catalogServiceId}</p>
                <p className="text-xs text-stone-400">Qtd: {s.quantity}</p>
              </div>
              <p className="text-sm font-semibold text-orange-600">{formatCurrency(s.unitPriceCents * s.quantity)}</p>
            </div>
          ))}
        </div>
      )}

      {showBudgetSection && tab === 'parts' && (
        <div className="flex flex-col gap-2 mb-5">
          {wo.parts.length === 0 ? (
            <p className="text-sm text-stone-400 py-4 text-center">Nenhuma peça registrada.</p>
          ) : wo.parts.map(p => (
            <div key={p.partId} className="flex justify-between items-center bg-white border border-orange-100 rounded-lg px-4 py-3">
              <div>
                <p className="text-sm font-medium text-stone-800">{p.partName ?? p.partId}</p>
                <p className="text-xs text-stone-400">Qtd: {p.quantity}</p>
              </div>
              <p className="text-sm font-semibold text-orange-600">{formatCurrency(p.unitPriceCents * p.quantity)}</p>
            </div>
          ))}
        </div>
      )}

      {showBudgetSection && tab === 'progress' && (
        <div className="py-2 mb-5 space-y-4">
          <StatusStepper current={wo.status} />
          {wo.diagnosisNotes && (
            <div className="bg-white border border-orange-200 rounded-xl p-4">
              <p className="text-xs text-stone-400 uppercase tracking-wide mb-1">Observações do Diagnóstico</p>
              <p className="text-sm text-stone-700 whitespace-pre-wrap">{wo.diagnosisNotes}</p>
            </div>
          )}
        </div>
      )}

      {/* Totals */}
      {showBudgetSection && (
        <div className="bg-white border border-orange-200 rounded-xl p-4 mb-5">
          <div className="flex justify-between text-sm text-stone-500 mb-1">
            <span>Serviços</span><span>{formatCurrency(canEditDiagnosisPlan ? diagnosisTotals.servicesTotal : wo.servicesTotalCents)}</span>
          </div>
          <div className="flex justify-between text-sm text-stone-500 mb-2">
            <span>Peças</span><span>{formatCurrency(canEditDiagnosisPlan ? diagnosisTotals.partsTotal : wo.partsTotalCents)}</span>
          </div>
          <div className="flex justify-between font-bold text-stone-900 pt-2 border-t border-orange-100">
            <span>Total</span><span className="text-orange-600">{formatCurrency(canEditDiagnosisPlan ? diagnosisTotals.total : wo.totalCents)}</span>
          </div>
        </div>
      )}

      {/* Actions */}
      {visibleActions.length > 0 && (
        <div className="bg-orange-50 border border-orange-200 rounded-xl p-4">
          <p className="text-xs font-semibold text-stone-500 uppercase tracking-wide mb-3">Ações disponíveis</p>
          <div className="flex flex-wrap gap-2">
            {visibleActions.map(action => (
              <button
                key={action.key}
                onClick={() => setConfirm(action.key)}
                className={`px-4 py-2 text-sm font-semibold rounded-lg transition-colors ${
                  action.variant === 'primary' ? 'bg-orange-600 text-white hover:bg-orange-700' :
                  action.variant === 'danger' ? 'bg-red-600 text-white hover:bg-red-700' :
                  'border border-stone-300 text-stone-700 hover:bg-stone-50'
                }`}
              >
                {action.label}
              </button>
            ))}
          </div>
        </div>
      )}

      {activeAction && (
        <ConfirmDialog
          open
          title={activeAction.label}
          description={`Confirma a ação "${activeAction.label}" para esta OS?`}
          confirmLabel={activeAction.label}
          onConfirm={activeAction.onConfirm}
          onCancel={() => setConfirm(null)}
        />
      )}
    </div>
  )
}

function InfoCard({ title, lines }: { title: string; lines: string[] }) {
  return (
    <div className="bg-white border border-orange-200 rounded-xl p-4">
      <p className="text-xs text-stone-400 uppercase tracking-wide mb-1">{title}</p>
      {lines.map((l, i) => <p key={i} className="text-sm text-stone-700">{l}</p>)}
    </div>
  )
}

function StatusStepper({ current }: { current: WorkOrderStatus }) {
  const cancelled = current === 'CANCELLED'
  const steps = cancelled ? [...STATUS_ORDER, 'CANCELLED' as WorkOrderStatus] : STATUS_ORDER
  const currentIdx = steps.indexOf(current)

  return (
    <div className="flex flex-col gap-2">
      {steps.map((status, i) => {
        const done = i < currentIdx
        const active = i === currentIdx
        return (
          <div key={status} className="flex items-center gap-3">
            <div className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold shrink-0 ${
              done ? 'bg-green-500 text-white' :
              active ? 'bg-orange-600 text-white' :
              'bg-stone-200 text-stone-400'
            }`}>
              {done ? '✓' : i + 1}
            </div>
            <span className={`text-sm ${active ? 'font-semibold text-orange-600' : done ? 'text-stone-500' : 'text-stone-300'}`}>
              {STATUS_LABELS[status]}
            </span>
          </div>
        )
      })}
    </div>
  )
}
