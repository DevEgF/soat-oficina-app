import { createBrowserRouter, Navigate } from 'react-router-dom'
import { Login, TrackOrder, QuoteResult, WorkOrders, WorkOrderDetail, Customers, Vehicles, Parts, CatalogServices, Warehouse, Metrics, PageSuspense as S } from '@/pages/LazyPages'
import { AppLayout } from '@/components/AppLayout'
import { ProtectedRoute } from '@/auth/ProtectedRoute'

export const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/public/acompanhar" replace /> },
  { path: '/login', element: <S><Login /></S> },
  { path: '/public/acompanhar', element: <S><TrackOrder /></S> },
  { path: '/public/resultado', element: <S><QuoteResult /></S> },
  {
    path: '/internal',
    element: <ProtectedRoute><AppLayout /></ProtectedRoute>,
    children: [
      { index: true, element: <Navigate to="/internal/ordens-servico" replace /> },
      { path: 'ordens-servico', element: <S><WorkOrders /></S> },
      { path: 'ordens-servico/:id', element: <S><WorkOrderDetail /></S> },
      { path: 'clientes', element: <S><Customers /></S> },
      { path: 'veiculos', element: <S><Vehicles /></S> },
      { path: 'pecas', element: <S><Parts /></S> },
      { path: 'servicos-catalogo', element: <S><CatalogServices /></S> },
      { path: 'almoxarifado', element: <S><Warehouse /></S> },
      { path: 'metricas', element: <S><Metrics /></S> },
    ],
  },
])
