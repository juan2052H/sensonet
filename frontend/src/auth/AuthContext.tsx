import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, configureApi } from '../api/client'
import type { LoginResponse, Role } from '../api/types'

const KEY = 'sensonet.auth'

interface Session {
  token: string
  email: string
  role: Role
  expiresAt: string
}

interface AuthValue {
  session: Session | null
  isAdmin: boolean
  login: (email: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthValue | null>(null)

function loadSession(): Session | null {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return null
    const s = JSON.parse(raw) as Session
    return new Date(s.expiresAt).getTime() > Date.now() ? s : null
  } catch {
    return null
  }
}

// Se configura al cargar el módulo, no en un efecto: React ejecuta los efectos de los hijos antes
// que los del padre, y las primeras consultas saldrían sin token al recargar la página.
let onUnauthorized: () => void = () => {}
configureApi({ getToken: () => loadSession()?.token ?? null, onUnauthorized: () => onUnauthorized() })

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(loadSession)
  const queryClient = useQueryClient()

  const logout = useCallback(() => {
    localStorage.removeItem(KEY)
    setSession(null)
    queryClient.clear()
  }, [queryClient])

  useEffect(() => {
    onUnauthorized = logout
  }, [logout])

  // Cierra la sesión cuando vence el token
  useEffect(() => {
    if (!session) return
    const ms = new Date(session.expiresAt).getTime() - Date.now()
    const t = window.setTimeout(logout, Math.max(0, ms))
    return () => window.clearTimeout(t)
  }, [session, logout])

  const login = useCallback(async (email: string, password: string) => {
    const res = await api<LoginResponse>('/api/auth/login', { method: 'POST', json: { email, password } })
    const s: Session = { token: res.token, email: res.email, role: res.role, expiresAt: res.expiresAt }
    localStorage.setItem(KEY, JSON.stringify(s))
    setSession(s)
  }, [])

  const value = useMemo(
    () => ({ session, isAdmin: session?.role === 'ADMIN', login, logout }),
    [session, login, logout],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth debe usarse dentro de AuthProvider')
  return ctx
}
