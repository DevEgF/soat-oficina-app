import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { toast } from 'sonner'
import { useTrackWorkOrder, useApproveCustomerQuote, useRejectCustomerQuote } from '@/api/workOrders'
import { authenticateCustomer } from '@/api/customerAuth'
import { StatusBadge } from '@/components/StatusBadge'
import { formatCurrency } from '@/lib/utils'
import type { WorkOrderTrackingResponse } from '@/lib/types'

export default function TrackOrderPage() {
  const [documento, setDocumento] = useState('')
  const [codigo, setCodigo] = useState('')
  const [result, setResult] = useState<WorkOrderTrackingResponse | null>(null)
  const [session, setSession] = useState<{ accessToken: string; expiresAt: number } | null>(null)
  const [authenticating, setAuthenticating] = useState(false)

  const track = useTrackWorkOrder()
  const approve = useApproveCustomerQuote()
  const reject = useRejectCustomerQuote()
  const busy = authenticating || track.isPending || approve.isPending || reject.isPending

  function clearResult() {
    setResult(null)
    setSession(null)
    track.reset()
    approve.reset()
    reject.reset()
  }

  async function handleTrack(e: React.FormEvent) {
    e.preventDefault()
    clearResult()
    setAuthenticating(true)
    try {
      const issued = await authenticateCustomer(documento)
      const data = await track.mutateAsync({ accessToken: issued.accessToken, codigo })
      setSession({ accessToken: issued.accessToken, expiresAt: Date.now() + issued.expiresIn * 1000 })
      setResult(data)
    } catch {
      clearResult()
      toast.error('Não foi possível consultar. Verifique o CPF e o código da OS.')
    } finally {
      setAuthenticating(false)
    }
  }

  const navigate = useNavigate()

  async function handleApprove() {
    if (!session || !result || Date.now() >= session.expiresAt) {
      clearResult()
      toast.error('Sessão expirada. Consulte a OS novamente.')
      return
    }
    try {
      const data = await approve.mutateAsync({ accessToken: session.accessToken, codigo: result.trackingCode })
      navigate('/public/resultado', { state: { result: data, action: 'aprovado' } })
    } catch {
      clearResult()
      toast.error('Não foi possível aprovar. Consulte a OS novamente.')
    }
  }

  async function handleReject() {
    if (!session || !result || Date.now() >= session.expiresAt) {
      clearResult()
      toast.error('Sessão expirada. Consulte a OS novamente.')
      return
    }
    try {
      const data = await reject.mutateAsync({ accessToken: session.accessToken, codigo: result.trackingCode })
      navigate('/public/resultado', { state: { result: data, action: 'reprovado' } })
    } catch {
      clearResult()
      toast.error('Não foi possível reprovar. Consulte a OS novamente.')
    }
  }

  return (
    <div className="min-h-screen bg-orange-50 flex flex-col items-center justify-center px-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-6">
          <span className="text-4xl">🔧</span>
          <h1 className="text-2xl font-bold text-stone-900 mt-2">Acompanhe sua OS</h1>
          <p className="text-stone-500 text-sm mt-1">Digite seu CPF e o código de rastreio da OS</p>
          <p className="text-stone-400 text-xs mt-1">Você encontra o código na tela de detalhe da Ordem de Serviço.</p>
        </div>

        <div className="bg-white rounded-2xl shadow border border-orange-200 p-6 mb-4">
          <form onSubmit={handleTrack} className="flex flex-col gap-4">
            <div>
              <label className="block text-sm font-medium text-stone-700 mb-1">CPF</label>
              <input
                value={documento}
                onChange={e => { setDocumento(e.target.value); clearResult() }}
                disabled={busy}
                className="w-full px-3 py-2 border border-orange-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-orange-400"
                placeholder="000.000.000-00"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-stone-700 mb-1">Código de Rastreio</label>
              <input
                value={codigo}
                onChange={e => { setCodigo(e.target.value); clearResult() }}
                disabled={busy}
                className="w-full px-3 py-2 border border-orange-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-orange-400"
                placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                required
              />
            </div>
            <button
              type="submit"
              disabled={busy}
              className="w-full py-2.5 bg-orange-600 hover:bg-orange-700 text-white text-sm font-semibold rounded-lg disabled:opacity-60"
            >
              {busy ? 'Consultando...' : 'Consultar →'}
            </button>
          </form>
        </div>

        {result && (
          <div className="bg-white rounded-2xl shadow border border-orange-200 p-6 animate-in fade-in">
            <div className="flex justify-between items-start mb-3">
              <div>
                <p className="text-xs text-stone-500 uppercase tracking-wide">Placa</p>
                <p className="font-semibold text-stone-900">{result.vehiclePlate}</p>
              </div>
              <StatusBadge status={result.status} />
            </div>
            <div className="mb-4">
              <p className="text-xs text-stone-500 uppercase tracking-wide">Código</p>
              <p className="text-sm font-mono text-stone-700">{result.trackingCode}</p>
            </div>
            <div className="pt-3 border-t border-orange-100 flex justify-between items-center">
              <span className="text-stone-500 text-sm">Total</span>
              <span className="text-lg font-bold text-orange-600">{formatCurrency(result.totalCents)}</span>
            </div>

            {result.status === 'PENDING_APPROVAL' && (
              <div className="mt-4 flex gap-3">
                <button
                  onClick={handleReject}
                  disabled={busy}
                  className="flex-1 py-2.5 bg-red-600 hover:bg-red-700 text-white text-sm font-semibold rounded-lg disabled:opacity-60"
                >
                  ✕ Reprovar
                </button>
                <button
                  onClick={handleApprove}
                  disabled={busy}
                  className="flex-1 py-2.5 bg-green-600 hover:bg-green-700 text-white text-sm font-semibold rounded-lg disabled:opacity-60"
                >
                  ✓ Aprovar
                </button>
              </div>
            )}
          </div>
        )}
      </div>

      <div className="mt-8 text-center">
        <Link to="/login" className="text-xs text-orange-600 hover:text-orange-800 underline underline-offset-2">
          Acesso ao sistema interno →
        </Link>
      </div>
    </div>
  )
}
