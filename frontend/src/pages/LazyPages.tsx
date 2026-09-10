import { lazy, Suspense } from 'react'

export const Login = lazy(() => import('./internal/LoginPage'))
export const TrackOrder = lazy(() => import('./public/TrackOrderPage'))
export const QuoteResult = lazy(() => import('./public/QuoteResultPage'))
export const WorkOrders = lazy(() => import('./internal/WorkOrdersPage'))
export const WorkOrderDetail = lazy(() => import('./internal/WorkOrderDetailPage'))
export const Customers = lazy(() => import('./internal/CustomersPage'))
export const Vehicles = lazy(() => import('./internal/VehiclesPage'))
export const Parts = lazy(() => import('./internal/PartsPage'))
export const CatalogServices = lazy(() => import('./internal/CatalogServicesPage'))
export const Warehouse = lazy(() => import('./internal/WarehousePage'))
export const Metrics = lazy(() => import('./internal/MetricsPage'))

export const PageSuspense = ({ children }: { children: React.ReactNode }) => (
  <Suspense fallback={<div className="p-6 text-stone-400 text-sm">Carregando...</div>}>{children}</Suspense>
)
