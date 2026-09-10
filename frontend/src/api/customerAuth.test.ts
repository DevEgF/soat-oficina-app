import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { authenticateCustomer, customerApi, trackCustomerWorkOrder, approveCustomerQuote, rejectCustomerQuote } from './customerAuth'

describe('customer authentication isolation', () => {
  const mock = new MockAdapter(customerApi)
  const storage = { getItem: vi.fn(() => JSON.stringify({ state: { token: 'staff-token' } })), setItem: vi.fn(), removeItem: vi.fn() }
  beforeEach(() => { mock.reset(); vi.clearAllMocks(); vi.stubGlobal('localStorage', storage) })
  afterEach(() => vi.unstubAllGlobals())

  it('sends CPF only to auth and uses customer bearer even with a staff session', async () => {
    mock.onPost('/auth/token').reply(config => {
      expect(JSON.parse(config.data)).toEqual({ cpf: '52998224725' })
      expect(config.headers?.Authorization).toBeUndefined()
      return [200, { accessToken: 'customer-token', expiresIn: 900 }]
    })
    mock.onGet('/api/customer/os/acompanhar').reply(config => {
      expect(config.params).toEqual({ codigo: 'owned-order' })
      expect(config.headers?.Authorization).toBe('Bearer customer-token')
      expect(JSON.stringify(config.params)).not.toContain('52998224725')
      return [200, { trackingCode: 'owned-order' }]
    })
    const issued = await authenticateCustomer('52998224725')
    await expect(trackCustomerWorkOrder({ accessToken: issued.accessToken, codigo: 'owned-order' })).resolves.toEqual({ trackingCode: 'owned-order' })
    expect(storage.getItem).not.toHaveBeenCalled()
    expect(storage.setItem).not.toHaveBeenCalled()
  })

  it('uses customer identity for both decisions without document parameters', async () => {
    mock.onPost(/\/api\/customer\/os\/(aprovar|reprovar)-orcamento/).reply(config => {
      expect(config.params).toEqual({ codigo: 'owned-order' })
      expect(config.headers?.Authorization).toBe('Bearer customer-token')
      expect(config.data).toBeNull()
      return [200, { trackingCode: 'owned-order' }]
    })
    const input = { accessToken: 'customer-token', codigo: 'owned-order' }
    await approveCustomerQuote(input)
    await rejectCustomerQuote(input)
    expect(mock.history.post).toHaveLength(2)
  })

  it('does not erase or redirect the staff session after customer rejection', async () => {
    mock.onPost('/auth/token').reply(401, { message: 'Unauthorized' })
    await expect(authenticateCustomer('52998224725')).rejects.toThrow()
    expect(storage.removeItem).not.toHaveBeenCalled()
    expect(storage.setItem).not.toHaveBeenCalled()
  })

  it('fails before tracking when no customer bearer exists', async () => {
    await expect(trackCustomerWorkOrder({ accessToken: '', codigo: 'owned-order' })).rejects.toThrow('Customer authentication required')
    expect(mock.history.get).toHaveLength(0)
  })
})
