import { useQueryClient } from '@tanstack/react-query'
import { keys, type RangeSpec } from '../api/hooks'
import type { ReadingPoint, SeriesResponse } from '../api/types'
import { mergePoint } from '../lib/series'
import { useTopic } from './RealtimeContext'

/**
 * Para la vista de lecturas crudas (1 h), añade cada lectura recibida por WebSocket a la caché
 * de la consulta, ordenada por tiempo (las del buffer llegan tarde y se insertan en su lugar).
 */
export function useLiveReadings(nodeId: number, range: RangeSpec) {
  const qc = useQueryClient()
  useTopic<ReadingPoint>(range.bucket === 'raw' ? `/topic/nodes/${nodeId}` : null, (p) => {
    qc.setQueryData<SeriesResponse>(keys.readings(nodeId, range.key), (old) =>
      old ? { ...old, points: mergePoint(old.points, p, range.ms) } : old,
    )
  })
}
