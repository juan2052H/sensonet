import type { Problem } from './types'

/** URL del backend. Vacía = mismo origen (proxy de Vite en desarrollo, nginx en Docker). */
export const API_BASE = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '')

export class ApiError extends Error {
  readonly status: number
  readonly problem: Problem

  constructor(status: number, problem: Problem) {
    super(problem.detail || problem.title || `Error ${status}`)
    this.status = status
    this.problem = problem
  }
}

let getToken: () => string | null = () => null
let onUnauthorized: () => void = () => {}

export function configureApi(opts: { getToken: () => string | null; onUnauthorized: () => void }) {
  getToken = opts.getToken
  onUnauthorized = opts.onUnauthorized
}

export async function api<T>(path: string, init: RequestInit & { json?: unknown } = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const token = getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  let body = init.body
  if (init.json !== undefined) {
    headers.set('Content-Type', 'application/json')
    body = JSON.stringify(init.json)
  }
  let res: Response
  try {
    res = await fetch(`${API_BASE}${path}`, { ...init, headers, body })
  } catch {
    throw new ApiError(0, { title: 'Sin conexión', detail: 'No se pudo contactar al servidor' })
  }
  if (res.status === 401 && token) onUnauthorized()
  if (!res.ok) {
    let problem: Problem = { status: res.status }
    try {
      problem = { status: res.status, ...(await res.json()) }
    } catch {
      /* respuesta sin cuerpo JSON */
    }
    throw new ApiError(res.status, problem)
  }
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

/** URL del WebSocket STOMP. */
export function wsUrl(): string {
  if (API_BASE) return API_BASE.replace(/^http/, 'ws') + '/ws'
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${proto}://${window.location.host}/ws`
}
