import { describe, expect, it } from 'vitest'
import type { ReadingPoint } from '../api/types'
import { buildSeries, mergePoint, trend } from './series'

const T0 = Date.parse('2026-11-24T12:00:00Z')
const pt = (sec: number, t: number, backfilled = false): ReadingPoint => ({
  ts: new Date(T0 + sec * 1000).toISOString(),
  temperature: t,
  humidity: 50,
  pressure: 851,
  backfilled,
})

describe('buildSeries', () => {
  it('ordena los puntos y no marca huecos si llegan a tiempo', () => {
    const s = buildSeries([pt(20, 22), pt(0, 20), pt(10, 21)], 'temperature', 10_000)
    expect(s.data.map(([, v]) => v)).toEqual([20, 21, 22])
    expect(s.gaps).toEqual([])
    expect(s.backfilled).toEqual([])
  })

  it('corta la línea cuando faltan más de 3 intervalos', () => {
    const s = buildSeries([pt(0, 20), pt(10, 21), pt(600, 22)], 'temperature', 10_000)
    expect(s.gaps).toEqual([[T0 + 10_000, T0 + 600_000]])
    expect(s.data).toContainEqual([T0 + 10_001, null])
  })

  it('agrupa en tramos los puntos recuperados del buffer', () => {
    const pts = [pt(0, 20), pt(10, 21, true), pt(20, 21, true), pt(30, 21, true), pt(40, 22), pt(50, 23, true)]
    const s = buildSeries(pts, 'temperature', 10_000)
    expect(s.backfilled).toEqual([
      [T0 + 10_000, T0 + 30_000],
      [T0 + 45_000, T0 + 55_000], // punto aislado: se ensancha medio intervalo a cada lado
    ])
    expect(s.gaps).toEqual([])
  })
})

describe('mergePoint', () => {
  it('inserta en orden, sin duplicados y respetando la ventana', () => {
    const now = T0 + 120_000
    let pts = [pt(0, 20), pt(20, 22)]
    pts = mergePoint(pts, pt(10, 21, true), 3_600_000, now)
    expect(pts.map((p) => p.temperature)).toEqual([20, 21, 22])
    pts = mergePoint(pts, pt(10, 99), 3_600_000, now)
    expect(pts).toHaveLength(3)
    expect(pts[1].temperature).toBe(99)
    pts = mergePoint(pts, pt(115, 25), 60_000, now)
    expect(pts.map((p) => p.temperature)).toEqual([25])
    expect(mergePoint(pts, pt(0, 1), 60_000, now)).toBe(pts)
  })
})

describe('trend', () => {
  it('detecta subidas, bajadas y datos insuficientes', () => {
    expect(trend([20, 20, 21, 21])).toBeCloseTo(1)
    expect(trend([22, 22, 21, 21])).toBeCloseTo(-1)
    expect(trend([20, null, 21])).toBeNull()
  })
})
