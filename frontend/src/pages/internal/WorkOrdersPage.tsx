import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { Plus, Eye, Copy } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/api/client'
import { useWorkOrders, useCreateWorkOrder } from '@/api/workOrders'
import { useCatalogServices } from '@/api/catalogServices'
import { StatusBadge } from '@/components/StatusBadge'
import { formatCurrency, isValidCpf, maskCpf, onlyDigits } from '@/lib/utils'
import { STATUS_LABELS } from '@/lib/constants'
import { useAuthStore } from '@/auth/store'
import type { WorkOrderStatus, CatalogServiceResponse, CustomerResponse, VehicleResponse } from '@/lib/types'

const ALL_STATUSES = Object.keys(STATUS_LABELS) as WorkOrderStatus[]

interface ServiceLine { catalogServiceId: string; quantity: number }

export default function WorkOrdersPage() {
  const { scope } = useAuthStore()
  const { data: workOrders = [], isLoading } = useWorkOrders()
  const { data: catalog = [] } = useCatalogServices()
  const createWO = useCreateWorkOrder()
  const canLookupCustomer = !!scope
  const customerLookupEndpoint =
    scope === 'ADMIN' || scope === 'MASTER' ? '/api/admin/clientes' : '/api/internal/clientes'
  const vehicleLookupEndpoint =
    scope === 'ADMIN' || scope === 'MASTER' ? '/api/admin/veiculos' : '/api/internal/veiculos'
  const { data: customers = [] } = useQuery({
    queryKey: ['customers', 'work-order-lookup', scope],
    queryFn: async () => {
      const { data } = await api.get<CustomerResponse[]>(customerLookupEndpoint)
      return data
    },
    enabled: canLookupCustomer,
  })
  const { data: vehicles = [] } = useQuery({
    queryKey: ['vehicles', 'work-order-lookup', scope],
    queryFn: async () => {
      const { data } = await api.get<VehicleResponse[]>(vehicleLookupEndpoint)
      return data
    },
    enabled: canLookupCustomer,
  })

  const [filterStatus, setFilterStatus] = useState<WorkOrderStatus | ''>('')
  const [showForm, setShowForm] = useState(false)

  // Form state
  const [form, setForm] = useState({
    customerTaxId: '', customerName: '', customerEmail: '', customerPhone: '',
    plate: '', vehicleBrand: '', vehicleModel: '', vehicleYear: '',
  })
  const [serviceLines, setServiceLines] = useState<ServiceLine[]>([])
  const customerByTaxId = useMemo(() => {
    const map = new Map<string, CustomerResponse>()
    customers.forEach((customer) => map.set(onlyDigits(customer.taxId), customer))
    return map
  }, [customers])

  const firstVehicleByCustomer = useMemo(() => {
    const map = new Map<string, VehicleResponse>()
    vehicles.forEach((vehicle) => {
      if (!map.has(vehicle.customerId)) map.set(vehicle.customerId, vehicle)
    })
    return map
  }, [vehicles])

  const filtered = filterStatus ? workOrders.filter(w => w.status === filterStatus) : workOrders

  const lookupCpf = onlyDigits(form.customerTaxId)
  const lookupCustomer = canLookupCustomer && isValidCpf(lookupCpf)
    ? customerByTaxId.get(lookupCpf) : undefined
  const lookupVehicle = lookupCustomer ? firstVehicleByCustomer.get(lookupCustomer.id) : undefined
  const [appliedLookup, setAppliedLookup] = useState<{
    cpf: string; customer?: CustomerResponse; vehicle?: VehicleResponse
  }>({ cpf: '' })
  // Retry when either query arrives, while preserving edits between query updates.
  if (appliedLookup.cpf !== lookupCpf || appliedLookup.customer !== lookupCustomer || appliedLookup.vehicle !== lookupVehicle) {
    setAppliedLookup({ cpf: lookupCpf, customer: lookupCustomer, vehicle: lookupVehicle })
    if (lookupCustomer) {
      setForm((prev) => ({
        ...prev,
        customerName: lookupCustomer.name,
        customerEmail: lookupCustomer.email ?? '',
        customerPhone: lookupCustomer.phone ?? '',
        plate: lookupVehicle?.plate ?? prev.plate,
        vehicleBrand: lookupVehicle?.brand ?? prev.vehicleBrand,
        vehicleModel: lookupVehicle?.model ?? prev.vehicleModel,
        vehicleYear: lookupVehicle?.year ? String(lookupVehicle.year) : prev.vehicleYear,
      }))
    }
  }

  function field(key: keyof typeof form) {
    return { value: form[key], onChange: (e: React.ChangeEvent<HTMLInputElement>) => setForm(f => ({ ...f, [key]: e.target.value })) }
  }

  function handleCustomerTaxIdChange(e: React.ChangeEvent<HTMLInputElement>) {
    const maskedCpf = maskCpf(e.target.value)
    setForm((f) => ({ ...f, customerTaxId: maskedCpf }))
    if (!canLookupCustomer) return
    const cpfDigits = onlyDigits(maskedCpf)
    if (cpfDigits.length !== 11) return
    if (!isValidCpf(cpfDigits)) {
      toast.error('CPF inválido. Verifique o número informado.')
      return
    }

  }

  function toggleService(svc: CatalogServiceResponse) {
    setServiceLines(prev => {
      const exists = prev.find(l => l.catalogServiceId === svc.id)
      if (exists) return prev.filter(l => l.catalogServiceId !== svc.id)
      return [...prev, { catalogServiceId: svc.id, quantity: 1 }]
    })
  }

  function setQty(id: string, qty: number) {
    setServiceLines(prev => prev.map(l => l.catalogServiceId === id ? { ...l, quantity: Math.max(1, qty) } : l))
  }

  async function copyTrackingCode(code: string) {
    try {
      await navigator.clipboard.writeText(code)
      toast.success('Código de rastreio copiado!')
    } catch {
      toast.error('Não foi possível copiar o código.')
    }
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    try {
      await createWO.mutateAsync({
        customerTaxId: onlyDigits(form.customerTaxId),
        customerName: form.customerName,
        customerEmail: form.customerEmail || undefined,
        customerPhone: form.customerPhone || undefined,
        plate: form.plate,
        vehicleBrand: form.vehicleBrand,
        vehicleModel: form.vehicleModel,
        vehicleYear: Number(form.vehicleYear),
        services: serviceLines,
        parts: [],
      })
      toast.success('Ordem de Serviço criada!')
      setShowForm(false)
      setForm({ customerTaxId: '', customerName: '', customerEmail: '', customerPhone: '', plate: '', vehicleBrand: '', vehicleModel: '', vehicleYear: '' })
      setServiceLines([])
    } catch { /* interceptor handles */ }
  }

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-bold text-stone-900">Ordens de Serviço</h1>
        {(scope === 'ATTENDANT' || scope === 'ADMIN' || scope === 'MASTER') && (
          <button onClick={() => setShowForm(true)} className="flex items-center gap-2 px-4 py-2 bg-orange-600 text-white text-sm rounded-lg hover:bg-orange-700">
            <Plus size={15} /> Nova OS
          </button>
        )}
      </div>

      {/* Filter */}
      <div className="mb-4">
        <select
          value={filterStatus}
          onChange={e => setFilterStatus(e.target.value as WorkOrderStatus | '')}
          className="px-3 py-2 border border-orange-200 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-orange-400"
        >
          <option value="">Todos os status</option>
          {ALL_STATUSES.map(s => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
        </select>
      </div>

      {/* Table */}
      {isLoading ? (
        <p className="text-stone-500 text-sm">Carregando...</p>
      ) : filtered.length === 0 ? (
        <div className="text-center py-12 text-stone-400">Nenhuma OS encontrada.</div>
      ) : (
        <div className="bg-white rounded-xl border border-orange-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-orange-50 border-b border-orange-200">
              <tr>
                <th className="text-left px-4 py-3 text-stone-600 font-medium">Código</th>
                <th className="text-left px-4 py-3 text-stone-600 font-medium">Status</th>
                <th className="text-left px-4 py-3 text-stone-600 font-medium">Total</th>
                <th className="px-4 py-3"></th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((wo, i) => (
                <tr key={wo.id} className={i % 2 === 0 ? 'bg-white' : 'bg-orange-50/40'}>
                  <td className="px-4 py-3">
                    <button
                      type="button"
                      onClick={() => copyTrackingCode(wo.trackingCode)}
                      className="inline-flex items-center gap-1 text-xs font-mono text-stone-700 hover:text-orange-700"
                      title="Copiar código completo"
                    >
                      {wo.trackingCode.slice(0, 8)}... <Copy size={12} />
                    </button>
                  </td>
                  <td className="px-4 py-3"><StatusBadge status={wo.status} /></td>
                  <td className="px-4 py-3 font-medium text-orange-600">{formatCurrency(wo.totalCents)}</td>
                  <td className="px-4 py-3 text-right">
                    <Link to={`/internal/ordens-servico/${wo.id}`} className="inline-flex items-center gap-1 text-orange-600 hover:text-orange-800 text-xs">
                      <Eye size={13} /> Ver
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Create OS Modal */}
      {showForm && (
        <div className="fixed inset-0 z-50 flex items-start justify-end">
          <div className="absolute inset-0 bg-black/40" onClick={() => setShowForm(false)} />
          <div className="relative bg-white w-full max-w-lg h-full overflow-y-auto shadow-2xl p-6 flex flex-col gap-4">
            <h2 className="text-base font-bold text-stone-900">Nova Ordem de Serviço</h2>

            <form onSubmit={handleCreate} className="flex flex-col gap-4">
              <section>
                <h3 className="text-xs font-semibold text-stone-500 uppercase mb-2">Cliente</h3>
                <div className="grid grid-cols-2 gap-3">
                  <FormInput label="CPF *" value={form.customerTaxId} onChange={handleCustomerTaxIdChange} maxLength={14} required />
                  <FormInput label="Nome *" {...field('customerName')} required />
                  <FormInput label="Email" type="email" {...field('customerEmail')} />
                  <FormInput label="Telefone" {...field('customerPhone')} />
                </div>
              </section>

              <section>
                <h3 className="text-xs font-semibold text-stone-500 uppercase mb-2">Veículo</h3>
                <div className="grid grid-cols-2 gap-3">
                  <FormInput label="Placa *" {...field('plate')} required />
                  <FormInput label="Marca *" {...field('vehicleBrand')} required />
                  <FormInput label="Modelo *" {...field('vehicleModel')} required />
                  <FormInput label="Ano *" type="number" min="1900" {...field('vehicleYear')} required />
                </div>
              </section>

              {catalog.length > 0 && (
                <section>
                  <h3 className="text-xs font-semibold text-stone-500 uppercase mb-2">Serviços</h3>
                  <div className="flex flex-col gap-2 max-h-48 overflow-y-auto">
                    {catalog.map(svc => {
                      const line = serviceLines.find(l => l.catalogServiceId === svc.id)
                      return (
                        <div key={svc.id} className="flex items-center gap-3 p-2 border border-orange-100 rounded-lg">
                          <input type="checkbox" checked={!!line} onChange={() => toggleService(svc)} className="accent-orange-600" />
                          <div className="flex-1 text-sm">
                            <span className="font-medium text-stone-800">{svc.name}</span>
                            <span className="text-stone-400 ml-2">{formatCurrency(svc.priceCents)}</span>
                          </div>
                          {line && (
                            <input
                              type="number" min="1" value={line.quantity}
                              onChange={e => setQty(svc.id, Number(e.target.value))}
                              className="w-16 px-2 py-1 border border-orange-200 rounded text-sm text-center"
                            />
                          )}
                        </div>
                      )
                    })}
                  </div>
                </section>
              )}

              <div className="flex gap-3 pt-2">
                <button type="button" onClick={() => setShowForm(false)} className="flex-1 py-2.5 border border-stone-200 rounded-lg text-sm text-stone-700 hover:bg-stone-50">
                  Cancelar
                </button>
                <button type="submit" disabled={createWO.isPending} className="flex-1 py-2.5 bg-orange-600 text-white text-sm font-semibold rounded-lg hover:bg-orange-700 disabled:opacity-60">
                  {createWO.isPending ? 'Criando...' : 'Criar OS'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}

function FormInput({ label, ...props }: { label: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div>
      <label className="block text-xs font-medium text-stone-600 mb-1">{label}</label>
      <input {...props} className="w-full px-3 py-2 border border-orange-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-orange-400 bg-white" />
    </div>
  )
}
