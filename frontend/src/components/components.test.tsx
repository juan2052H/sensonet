import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { NodeDto } from '../api/types'
import { AuthProvider } from '../auth/AuthContext'
import { PreferencesProvider } from '../lib/preferences'
import { LoginPage } from '../pages/LoginPage'
import { hysteresisError } from '../lib/rules'
import { NodeCardView } from './NodeCard'

function wrap(ui: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <PreferencesProvider>
        <AuthProvider>
          <MemoryRouter>{ui}</MemoryRouter>
        </AuthProvider>
      </PreferencesProvider>
    </QueryClientProvider>,
  )
}

const node: NodeDto = {
  id: 1, code: 'sim-sala', name: 'Sala', location: 'Casa', online: false, intervalSeconds: 10,
  relayOn: true, buffered: 12, lastSeen: new Date().toISOString(),
  lastReading: { ts: new Date().toISOString(), temperature: 24.61, humidity: 58.2, pressure: 851.3 },
}

afterEach(() => vi.restoreAllMocks())

describe('NodeCardView', () => {
  it('muestra estado, valores, relé y lecturas en buffer', () => {
    wrap(<NodeCardView node={node} temps={[24, 24.2, 24.4, 24.6]} />)
    expect(screen.getByText('Sala')).toBeInTheDocument()
    expect(screen.getByText('Sin conexión')).toBeInTheDocument()
    expect(screen.getByText(/24\.6 °C/)).toBeInTheDocument()
    expect(screen.getByText(/58\.2 %/)).toBeInTheDocument()
    expect(screen.getByText('Relé encendido')).toBeInTheDocument()
    expect(screen.getByText('12 en buffer')).toBeInTheDocument()
    expect(screen.getByTitle('Subiendo')).toBeInTheDocument()
  })

  it('respeta la preferencia °F', () => {
    localStorage.setItem('sensonet.unit', 'F')
    wrap(<NodeCardView node={{ ...node, online: true }} temps={[]} />)
    expect(screen.getByText(/76\.3 °F/)).toBeInTheDocument()
    expect(screen.getByText('En línea')).toBeInTheDocument()
  })
})

describe('LoginPage', () => {
  it('muestra el error del servidor cuando las credenciales son incorrectas', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ title: 'Credenciales inválidas', detail: 'Correo o contraseña incorrectos', status: 401 }),
        { status: 401, headers: { 'Content-Type': 'application/problem+json' } }),
    )
    wrap(<LoginPage />)
    await userEvent.type(screen.getByLabelText('Correo'), 'admin@x.co')
    await userEvent.type(screen.getByLabelText('Contraseña'), 'mala')
    await userEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Correo o contraseña incorrectos')
  })

  it('guarda la sesión cuando el inicio de sesión es correcto', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ token: 'abc', expiresAt: new Date(Date.now() + 3_600_000).toISOString(), email: 'admin@x.co', role: 'ADMIN' }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }),
    )
    wrap(<LoginPage />)
    await userEvent.type(screen.getByLabelText('Correo'), 'admin@x.co')
    await userEvent.type(screen.getByLabelText('Contraseña'), 'buena-clave')
    await userEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))
    await vi.waitFor(() => expect(localStorage.getItem('sensonet.auth')).toContain('"role":"ADMIN"'))
  })
})

describe('hysteresisError', () => {
  it('valida igual que el backend', () => {
    expect(hysteresisError({ operator: 'GT', threshold: 30, recovery: 28 })).toBeNull()
    expect(hysteresisError({ operator: 'GT', threshold: 30, recovery: 31 })).toMatch(/menor/)
    expect(hysteresisError({ operator: 'LT', threshold: 25, recovery: 30 })).toBeNull()
    expect(hysteresisError({ operator: 'LT', threshold: 25, recovery: 20 })).toMatch(/mayor/)
  })
})
