import type { ReadingPoint } from '../api/types'

export type SeriesMetric = 'temperature' | 'humidity' | 'pressure'
export type Range = [number, number]

export interface BuiltSeries {
  /** Pares [ms, valor]; un null corta la línea donde faltan datos. */
  data: [number, number | null][]
  /** Tramos sin datos (el nodo estuvo offline y nada se recuperó). */
  gaps: Range[]
  /** Tramos rellenados después con datos del buffer offline del nodo. */
  backfilled: Range[]
}

/**
 * Prepara una serie para la gráfica: ordena, corta la línea en los huecos (más de 3 pasos sin
 * datos) y agrupa los puntos que llegaron tarde (desde el buffer) en tramos para resaltarlos.
 */
export function buildSeries(points: ReadingPoint[], metric: SeriesMetric, stepMs: number): BuiltSeries {
  const sorted = [...points].sort((a, b) => Date.parse(a.ts) - Date.parse(b.ts))
  const data: [number, number | null][] = []
  const gaps: Range[] = []
  const backfilled: Range[] = []
  let runStart: number | null = null
  let prevTs: number | null = null

  for (const p of sorted) {
    const ts = Date.parse(p.ts)
    if (prevTs !== null && ts - prevTs > 3 * stepMs) {
      gaps.push([prevTs, ts])
      data.push([prevTs + 1, null])
    }
    data.push([ts, p[metric]])

    if (p.backfilled) {
      if (runStart === null) runStart = ts
    } else if (runStart !== null) {
      backfilled.push(widen([runStart, prevTs ?? runStart], stepMs))
      runStart = null
    }
    prevTs = ts
  }
  if (runStart !== null && prevTs !== null) backfilled.push(widen([runStart, prevTs], stepMs))
  return { data, gaps, backfilled }
}

function widen([a, b]: Range, stepMs: number): Range {
  return a === b ? [a - stepMs / 2, b + stepMs / 2] : [a, b]
}

/**
 * Inserta una lectura recibida por WebSocket en la lista (ordenada, sin duplicados por ts) y
 * descarta lo que quedó fuera de la ventana visible.
 */
export function mergePoint(points: ReadingPoint[], p: ReadingPoint, windowMs: number, now = Date.now()): ReadingPoint[] {
  const ts = Date.parse(p.ts)
  const cutoff = now - windowMs
  if (ts < cutoff) return points
  const out = points.filter((x) => Date.parse(x.ts) >= cutoff && x.ts !== p.ts)
  let i = out.length
  while (i > 0 && Date.parse(out[i - 1].ts) > ts) i--
  out.splice(i, 0, p)
  return out
}

/** Tendencia de los últimos puntos: diferencia entre el promedio de la segunda y la primera mitad. */
export function trend(values: (number | null)[]): number | null {
  const v = values.filter((x): x is number => x !== null)
  if (v.length < 4) return null
  const half = Math.floor(v.length / 2)
  const avg = (a: number[]) => a.reduce((s, x) => s + x, 0) / a.length
  return avg(v.slice(half)) - avg(v.slice(0, half))
}
