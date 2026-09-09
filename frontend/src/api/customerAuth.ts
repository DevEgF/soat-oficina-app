import axios from 'axios'
import type { WorkOrderTrackingResponse } from '@/lib/types'

// Customer sessions never use the staff client's persistence or interceptors.
export const customerApi = axios.create({
  baseURL: import.meta.env.VITE_CUSTOMER_API_BASE_URL ?? import.meta.env.VITE_API_BASE_URL ?? '',
})

export async function authenticateCustomer(cpf: string) {
  const { data } = await customerApi.post<{ accessToken: string; expiresIn: number }>('/auth/token', { cpf })
  if (!data.accessToken || !Number.isFinite(data.expiresIn) || data.expiresIn <= 0) {
    throw new Error('Invalid authentication response')
  }
  return { accessToken: data.accessToken, expiresIn: data.expiresIn }
}

export type CustomerOrderInput = { accessToken: string; codigo: string }
const options = ({ accessToken, codigo }: CustomerOrderInput) => {
  if (!accessToken) throw new Error('Customer authentication required')
  return { params: { codigo }, headers: { Authorization: `Bearer ${accessToken}` } }
}

export async function trackCustomerWorkOrder(input: CustomerOrderInput) {
  const { data } = await customerApi.get<WorkOrderTrackingResponse>('/api/customer/os/acompanhar', options(input))
  return data
}

export async function approveCustomerQuote(input: CustomerOrderInput) {
  const { data } = await customerApi.post<WorkOrderTrackingResponse>('/api/customer/os/aprovar-orcamento', null, options(input))
  return data
}

export async function rejectCustomerQuote(input: CustomerOrderInput) {
  const { data } = await customerApi.post<WorkOrderTrackingResponse>('/api/customer/os/reprovar-orcamento', null, options(input))
  return data
}
