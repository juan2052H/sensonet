import type { Metric, Severity } from '../api/types'

export type TempUnit = 'C' | 'F'

export function toUnit(celsius: number, unit: TempUnit): number {
  return unit === 'F' ? celsius * 9 / 5 + 32 : celsius
}

export function fmtTemp(v: number | null | undefined, unit: TempUnit = 'C'): string {
  if (v === null || v === undefined) return '—'
  return `${toUnit(v, unit).toFixed(1)} °${unit}`
}

export function fmtNum(v: number | null | undefined, digits = 1, suffix = ''): string {
  if (v === null || v === undefined) return '—'
  return `${v.toFixed(digits)}${suffix ? ` ${suffix}` : ''}`
}

/** Valor de una métrica con su unidad (temperatura respeta la preferencia °C/°F). */
export function fmtMetric(metric: Metric, v: number | null | undefined, unit: TempUnit = 'C'): string {
  switch (metric) {
    case 'TEMPERATURE': return fmtTemp(v, unit)
    case 'HUMIDITY': return fmtNum(v, 1, '%')
    case 'PRESSURE': return fmtNum(v, 1, 'hPa')
    case 'RSSI': return fmtNum(v, 0, 'dBm')
  }
}

export const METRIC_LABEL: Record<Metric, string> = {
  TEMPERATURE: 'Temperatura',
  HUMIDITY: 'Humedad',
  PRESSURE: 'Presión',
  RSSI: 'Señal WiFi',
}

export const SEVERITY_LABEL: Record<Severity, string> = {
  INFO: 'Información',
  WARNING: 'Advertencia',
  CRITICAL: 'Crítica',
}

const dateTime = new Intl.DateTimeFormat('es-CO', { dateStyle: 'short', timeStyle: 'short' })
const time = new Intl.DateTimeFormat('es-CO', { hour: '2-digit', minute: '2-digit', second: '2-digit' })

export function fmtDateTime(iso: string | null | undefined): string {
  return iso ? dateTime.format(new Date(iso)) : '—'
}

export function fmtTime(iso: string | null | undefined): string {
  return iso ? time.format(new Date(iso)) : '—'
}

export function fmtDuration(ms: number): string {
  const s = Math.max(0, Math.round(ms / 1000))
  if (s < 60) return `${s} s`
  const m = Math.floor(s / 60)
  if (m < 60) return `${m} min`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} h ${m % 60} min`
  return `${Math.floor(h / 24)} d ${h % 24} h`
}

export function timeAgo(iso: string | null | undefined, now = Date.now()): string {
  if (!iso) return 'nunca'
  const ms = now - Date.parse(iso)
  if (ms < 5_000) return 'ahora'
  return `hace ${fmtDuration(ms)}`
}

export function signalLabel(rssi: number | null | undefined): string {
  if (rssi === null || rssi === undefined) return '—'
  if (rssi >= -60) return 'Excelente'
  if (rssi >= -70) return 'Buena'
  if (rssi >= -80) return 'Regular'
  return 'Débil'
}
