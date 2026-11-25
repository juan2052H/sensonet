import { Navigate, Route, Routes } from 'react-router'
import { useAuth } from './auth/AuthContext'
import { Layout } from './components/Layout'
import { AlertsPage } from './pages/AlertsPage'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { NodeDetailPage } from './pages/NodeDetailPage'
import { RulesPage } from './pages/RulesPage'
import { SettingsPage } from './pages/SettingsPage'
import { RealtimeProvider } from './realtime/RealtimeContext'

function Protected() {
  const { session } = useAuth()
  if (!session) return <Navigate to="/login" replace />
  return (
    <RealtimeProvider>
      <Layout />
    </RealtimeProvider>
  )
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<Protected />}>
        <Route index element={<HomePage />} />
        <Route path="nodos/:id" element={<NodeDetailPage />} />
        <Route path="alertas" element={<AlertsPage />} />
        <Route path="reglas" element={<RulesPage />} />
        <Route path="ajustes" element={<SettingsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
