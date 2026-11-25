import { useMemo } from 'react'
import { Link } from 'react-router'
import { RANGES, useReadings } from '../api/hooks'
import { useLiveReadings } from '../realtime/useLiveReadings'
import type { NodeDto } from '../api/types'
import { fmtNum, fmtTemp, timeAgo, toUnit } from '../lib/format'
import { usePreferences } from '../lib/preferences'
import { buildSeries, trend } from '../lib/series'
import { Chart } from './Chart'
import { Badge, Card, StatusDot } from './ui'

export function TrendArrow({ delta }: { delta: number | null }) {
  if (delta === null || Math.abs(delta) < 0.15) return <span className="text-slate-400" title="Estable">→</span>
  return delta > 0
    ? <span className="text-red-500" title="Subiendo">↗</span>
    : <span className="text-sky-500" title="Bajando">↘</span>
}

export function NodeCardView({ node, temps }: { node: NodeDto; temps: (number | null)[] }) {
  const { unit } = usePreferences()
  const last = node.lastReading
  return (
    <div className="flex flex-1 flex-col gap-3">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <h3 className="truncate font-semibold">{node.name}</h3>
          <p className="truncate text-xs text-slate-500">{node.code}{node.location ? ` · ${node.location}` : ''}</p>
        </div>
        <StatusDot online={node.online} />
      </div>
      <div className="flex items-end gap-4">
        <div>
          <div className="flex items-center gap-1 text-3xl font-semibold tabular-nums">
            {fmtTemp(last?.temperature, unit)} <TrendArrow delta={trend(temps)} />
          </div>
          <div className="mt-1 flex gap-3 text-sm text-slate-600 dark:text-slate-400">
            <span title="Humedad relativa">💧 {fmtNum(last?.humidity, 1, '%')}</span>
            <span title="Presión">⏲ {fmtNum(last?.pressure, 0, 'hPa')}</span>
          </div>
        </div>
      </div>
      <div className="mt-auto flex flex-wrap items-center gap-1.5 text-xs">
        <Badge>{node.relayOn ? 'Relé encendido' : 'Relé apagado'}</Badge>
        {(node.buffered ?? 0) > 0 && (
          <Badge className="bg-violet-100 text-violet-800 dark:bg-violet-500/15 dark:text-violet-300">
            {node.buffered} en buffer
          </Badge>
        )}
        <span className="ml-auto text-slate-500">{timeAgo(last?.ts ?? node.lastSeen)}</span>
      </div>
    </div>
  )
}

export function NodeCard({ node }: { node: NodeDto }) {
  const range = RANGES[0]
  const readings = useReadings(node.id, range)
  useLiveReadings(node.id, range)
  const { unit } = usePreferences()
  const points = useMemo(() => readings.data?.points ?? [], [readings.data])
  const temps = useMemo(() => points.slice(-30).map((p) => p.temperature), [points])
  const series = useMemo(() => {
    const converted = points.map((p) => ({ ...p, temperature: p.temperature === null ? null : toUnit(p.temperature, unit) }))
    return buildSeries(converted, 'temperature', range.stepMs)
  }, [points, unit, range.stepMs])

  return (
    <Link to={`/nodos/${node.id}`} className="block h-full rounded-xl focus:outline-none focus-visible:ring-2 focus-visible:ring-teal-500">
      <Card className="flex h-full flex-col transition hover:border-teal-400 hover:shadow-md">
        <NodeCardView node={node} temps={temps} />
        <div className="-mx-1 mt-3">
          <Chart series={series} color="#0d9488" unit={`°${unit}`} title="Temperatura última hora" height={48} compact />
        </div>
      </Card>
    </Link>
  )
}
