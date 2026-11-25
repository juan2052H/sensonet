import { useMemo, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { RANGES, useNode, useReadings, useRules, useSetInterval, useUpdateNode } from '../api/hooks'
import type { NodeDto, ReadingPoint } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Chart } from '../components/Chart'
import { RelayControl } from '../components/RelayControl'
import { Button, Card, CardTitle, ErrorBox, Field, Spinner, StatusDot, cx, inputClass } from '../components/ui'
import { fmtDateTime, fmtNum, signalLabel, timeAgo, toUnit } from '../lib/format'
import { usePreferences } from '../lib/preferences'
import { buildSeries, type SeriesMetric } from '../lib/series'
import { useLiveReadings } from '../realtime/useLiveReadings'

const METRICS: { key: SeriesMetric; title: string; color: string; ruleMetric: string }[] = [
  { key: 'temperature', title: 'Temperatura', color: '#0d9488', ruleMetric: 'TEMPERATURE' },
  { key: 'humidity', title: 'Humedad relativa', color: '#0284c7', ruleMetric: 'HUMIDITY' },
  { key: 'pressure', title: 'Presión', color: '#7c3aed', ruleMetric: 'PRESSURE' },
]

export function NodeDetailPage() {
  const id = Number(useParams().id)
  const node = useNode(id)
  const { isAdmin } = useAuth()
  const [rangeKey, setRangeKey] = useState('1h')
  const range = RANGES.find((r) => r.key === rangeKey) ?? RANGES[0]
  const readings = useReadings(id, range)
  useLiveReadings(id, range)
  const rules = useRules()
  const { unit } = usePreferences()

  const points: ReadingPoint[] = useMemo(() => readings.data?.points ?? [], [readings.data])
  const charts = useMemo(
    () =>
      METRICS.map((m) => {
        const converted = m.key === 'temperature'
          ? points.map((p) => ({ ...p, temperature: p.temperature === null ? null : toUnit(p.temperature, unit) }))
          : points
        const thresholds = (rules.data ?? [])
          .filter((r) => r.enabled && r.metric === m.ruleMetric && (r.nodeId == null || r.nodeId === id))
          .map((r) => ({ value: m.key === 'temperature' ? toUnit(r.threshold, unit) : r.threshold, label: r.name }))
        return { ...m, series: buildSeries(converted, m.key, range.stepMs), thresholds }
      }),
    [points, unit, rules.data, id, range.stepMs],
  )
  const backfilledCount = points.filter((p) => p.backfilled).length

  if (node.isPending) return <Spinner />
  if (node.error) return <ErrorBox error={node.error} />
  const n = node.data

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <Link to="/" className="text-sm text-teal-700 hover:underline dark:text-teal-400">← Inicio</Link>
          <h1 className="text-2xl font-bold">{n.name}</h1>
          <p className="text-sm text-slate-500">{n.code}{n.location ? ` · ${n.location}` : ''}</p>
        </div>
        <div className="flex items-center gap-3">
          <StatusDot online={n.online} />
          <div className="inline-flex rounded-lg border border-slate-200 p-0.5 dark:border-slate-700" role="group" aria-label="Rango">
            {RANGES.map((r) => (
              <button key={r.key} type="button" onClick={() => setRangeKey(r.key)} aria-pressed={r.key === rangeKey}
                className={cx('rounded-md px-3 py-1 text-sm', r.key === rangeKey ? 'bg-teal-600 text-white' : 'text-slate-600 dark:text-slate-300')}>
                {r.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          {readings.error && <ErrorBox error={readings.error} />}
          {charts.map((c) => (
            <Card key={c.key}>
              <CardTitle action={readings.isFetching ? <span className="text-xs text-slate-400">actualizando…</span> : undefined}>
                {c.title}
              </CardTitle>
              <Chart series={c.series} color={c.color} title={c.title}
                unit={c.key === 'temperature' ? `°${unit}` : c.key === 'humidity' ? '%' : 'hPa'}
                thresholds={c.thresholds} />
            </Card>
          ))}
          <p className="flex flex-wrap gap-4 text-xs text-slate-500">
            <span><span className="mr-1 inline-block h-3 w-3 rounded-sm bg-violet-500/30 align-middle" />Recuperado del buffer offline ({backfilledCount} puntos)</span>
            <span><span className="mr-1 inline-block h-3 w-3 rounded-sm bg-slate-400/30 align-middle" />Sin datos</span>
            <span><span className="mr-1 inline-block w-4 border-t-2 border-dashed border-red-500 align-middle" />Umbral de regla</span>
          </p>
        </div>

        <div className="space-y-4">
          <RelayControl node={n} canControl={isAdmin} />
          <NodeInfo node={n} />
          {isAdmin && <NodeSettings node={n} />}
        </div>
      </div>
    </div>
  )
}

function NodeInfo({ node }: { node: NodeDto }) {
  const rows: [string, string][] = [
    ['Última lectura', timeAgo(node.lastReading?.ts)],
    ['Último contacto', fmtDateTime(node.lastSeen)],
    ['Firmware', node.firmware ?? '—'],
    ['Señal WiFi', `${fmtNum(node.rssi, 0, 'dBm')} (${signalLabel(node.rssi)})`],
    ['Intervalo de muestreo', `${node.intervalSeconds} s`],
    ['Lecturas en buffer', String(node.buffered ?? 0)],
    ['Descartadas (buffer lleno)', String(node.dropped ?? 0)],
  ]
  return (
    <Card>
      <CardTitle>Estado del nodo</CardTitle>
      <dl className="space-y-1.5 text-sm">
        {rows.map(([k, v]) => (
          <div key={k} className="flex justify-between gap-2">
            <dt className="text-slate-500">{k}</dt>
            <dd className="text-right font-medium">{v}</dd>
          </div>
        ))}
      </dl>
    </Card>
  )
}

function NodeSettings({ node }: { node: NodeDto }) {
  const update = useUpdateNode(node.id)
  const setInterval = useSetInterval(node.id)
  const [name, setName] = useState(node.name)
  const [location, setLocation] = useState(node.location ?? '')
  const [interval, setIntervalValue] = useState(node.intervalSeconds)

  function save(e: FormEvent) {
    e.preventDefault()
    update.mutate({ name, location })
  }

  return (
    <Card>
      <CardTitle>Configuración</CardTitle>
      <form onSubmit={save} className="space-y-3">
        <Field label="Nombre"><input className={inputClass} value={name} maxLength={80} onChange={(e) => setName(e.target.value)} /></Field>
        <Field label="Ubicación"><input className={inputClass} value={location} maxLength={120} onChange={(e) => setLocation(e.target.value)} /></Field>
        <Button type="submit" variant="secondary" disabled={update.isPending}>Guardar</Button>
        {update.error && <ErrorBox error={update.error} />}
      </form>
      <div className="mt-4 border-t border-slate-100 pt-4 dark:border-slate-800">
        <Field label="Intervalo de muestreo (s)" hint="Se envía al nodo por MQTT (tópico config, retenido)">
          <div className="flex gap-2">
            <input className={inputClass} type="number" min={5} max={3600} value={interval}
              onChange={(e) => setIntervalValue(Number(e.target.value))} />
            <Button variant="secondary" disabled={setInterval.isPending || interval < 5 || interval > 3600}
              onClick={() => setInterval.mutate(interval)}>Aplicar</Button>
          </div>
        </Field>
        {setInterval.error && <div className="mt-2"><ErrorBox error={setInterval.error} /></div>}
        {setInterval.isSuccess && <p className="mt-2 text-xs text-emerald-600">Intervalo enviado al nodo.</p>}
      </div>
    </Card>
  )
}
