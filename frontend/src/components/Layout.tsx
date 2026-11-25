import { NavLink, Outlet } from 'react-router'
import { useAlerts } from '../api/hooks'
import { useAuth } from '../auth/AuthContext'
import { usePreferences } from '../lib/preferences'
import { useRealtime, type ConnectionStatus } from '../realtime/RealtimeContext'
import { Button, cx } from './ui'

const STATUS: Record<ConnectionStatus, { label: string; cls: string }> = {
  live: { label: 'En vivo', cls: 'bg-emerald-500' },
  connecting: { label: 'Conectando…', cls: 'bg-amber-400 animate-pulse' },
  reconnecting: { label: 'Reconectando…', cls: 'bg-amber-400 animate-pulse' },
  offline: { label: 'Sin conexión', cls: 'bg-slate-400' },
}

export function ConnectionBadge() {
  const { status } = useRealtime()
  const s = STATUS[status]
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-600 dark:border-slate-700 dark:text-slate-300" aria-live="polite">
      <span className={cx('h-2 w-2 rounded-full', s.cls)} />
      {s.label}
    </span>
  )
}

export function Layout() {
  const { session, logout } = useAuth()
  const { theme, setTheme } = usePreferences()
  const openAlerts = useAlerts('OPEN')
  const count = openAlerts.data?.length ?? 0

  const link = ({ isActive }: { isActive: boolean }) =>
    cx(
      'rounded-lg px-3 py-1.5 text-sm font-medium transition',
      isActive
        ? 'bg-teal-50 text-teal-800 dark:bg-teal-500/15 dark:text-teal-300'
        : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800',
    )

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 dark:bg-slate-950 dark:text-slate-100">
      <header className="sticky top-0 z-10 border-b border-slate-200 bg-white/90 backdrop-blur dark:border-slate-800 dark:bg-slate-900/90">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-3 px-4 py-2.5">
          <NavLink to="/" className="flex items-center gap-2 font-bold">
            <img src="./favicon.svg" alt="" className="h-7 w-7" />
            SensoNet
          </NavLink>
          <nav className="flex flex-wrap gap-1" aria-label="Principal">
            <NavLink to="/" end className={link}>Inicio</NavLink>
            <NavLink to="/alertas" className={link}>
              Alertas
              {count > 0 && <span className="ml-1.5 rounded-full bg-red-600 px-1.5 text-xs text-white">{count}</span>}
            </NavLink>
            <NavLink to="/reglas" className={link}>Reglas</NavLink>
            <NavLink to="/ajustes" className={link}>Ajustes</NavLink>
          </nav>
          <div className="ml-auto flex items-center gap-2">
            <ConnectionBadge />
            <Button variant="ghost" onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
              aria-label={theme === 'dark' ? 'Usar tema claro' : 'Usar tema oscuro'} title="Cambiar tema">
              <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                {theme === 'dark'
                  ? <><circle cx="12" cy="12" r="4" /><path d="M12 2v2m0 16v2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4M2 12h2m16 0h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" /></>
                  : <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />}
              </svg>
            </Button>
            <span className="hidden text-xs text-slate-500 sm:inline">
              {session?.email} · {session?.role === 'ADMIN' ? 'Administrador' : 'Lectura'}
            </span>
            <Button variant="secondary" onClick={logout}>Salir</Button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-4 py-6">
        <Outlet />
      </main>
      <footer className="mx-auto max-w-7xl px-4 pb-6 text-xs text-slate-400">
        SensoNet · proyecto de portafolio · los datos de la demo provienen de nodos simulados
      </footer>
    </div>
  )
}
