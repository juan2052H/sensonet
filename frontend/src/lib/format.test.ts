import { describe, expect, it } from 'vitest'
import { fmtDuration, fmtMetric, fmtTemp, signalLabel, timeAgo } from './format'

describe('format', () => {
  it('convierte temperatura a °F', () => {
    expect(fmtTemp(25, 'C')).toBe('25.0 °C')
    expect(fmtTemp(25, 'F')).toBe('77.0 °F')
    expect(fmtTemp(null)).toBe('—')
  })

  it('formatea cada métrica con su unidad', () => {
    expect(fmtMetric('HUMIDITY', 58.24)).toBe('58.2 %')
    expect(fmtMetric('PRESSURE', 851.3)).toBe('851.3 hPa')
    expect(fmtMetric('RSSI', -61)).toBe('-61 dBm')
  })

  it('formatea duraciones', () => {
    expect(fmtDuration(42_000)).toBe('42 s')
    expect(fmtDuration(5 * 60_000)).toBe('5 min')
    expect(fmtDuration(65 * 60_000)).toBe('1 h 5 min')
    expect(fmtDuration(26 * 3_600_000)).toBe('1 d 2 h')
  })

  it('tiempo relativo', () => {
    const now = Date.parse('2026-11-24T12:00:00Z')
    expect(timeAgo('2026-11-24T11:59:58Z', now)).toBe('ahora')
    expect(timeAgo('2026-11-24T11:50:00Z', now)).toBe('hace 10 min')
    expect(timeAgo(null, now)).toBe('nunca')
  })

  it('califica la señal WiFi', () => {
    expect(signalLabel(-55)).toBe('Excelente')
    expect(signalLabel(-75)).toBe('Regular')
    expect(signalLabel(-90)).toBe('Débil')
  })
})
