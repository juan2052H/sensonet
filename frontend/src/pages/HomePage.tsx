import { Link } from 'react-router'
import { useAlerts, useNodes } from '../api/hooks'
import type { NodeDto } from '../api/types'
import { NodeCard } from '../components/NodeCard'
import { Card, CardTitle, Empty, ErrorBox, SeverityBadge, Spinner, StatusDot } from '../components/ui'
import { METRIC_LABEL, fmtMetric, timeAgo } from '../lib/format'
import { usePreferences } from '../lib/preferences'

function groupByLocation(nodes: NodeDto[]): [string, NodeDto[]][] {
  const map = new Map<string, NodeDto[]>()
  for (const n of nodes) {
    const key = n.location?.trim() || 'Sin ubicación'
    map.set(key, [...(map.get(key) ?? []), n])
  }
  return [...map.entries()].sort(([a], [b]) => a.localeCompare(b))
}

export function HomePage() {
  const nodes = useNodes()
  const alerts = useAlerts('OPEN')
  const { unit } = usePreferences()
  const list = nodes.data ?? []
  const online = list.filter((n) => n.online).length

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h1 className="text-2xl font-bold">Inicio</h1>
          <p className="text-sm text-slate-500">
            {list.length} nodos · {online} en línea · {alerts.data?.length ?? 0} alertas abiertas
          </p>
        </div>
      </div>

      {nodes.isPending && <Spinner />}
      {nodes.error && <ErrorBox error={nodes.error} />}
      {nodes.data && list.length === 0 && (
        <Card>
          <Empty>
            Aún no hay nodos. Inicia el simulador con <code>docker compose --profile demo up</code> o conecta un ESP32:
            los nodos se registran solos al enviar su primera lectura.
          </Empty>
        </Card>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {list.map((n) => <NodeCard key={n.id} node={n} />)}
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardTitle action={<Link to="/alertas" className="text-sm text-teal-700 hover:underline dark:text-teal-400">Ver todas</Link>}>
            Alertas abiertas
          </CardTitle>
          {alerts.isPending ? <Spinner /> : (alerts.data?.length ?? 0) === 0 ? (
            <Empty>Todo en orden: no hay alertas abiertas.</Empty>
          ) : (
            <ul className="divide-y divide-slate-100 dark:divide-slate-800">
              {alerts.data!.slice(0, 6).map((a) => (
                <li key={a.id} className="flex flex-wrap items-center gap-2 py-2 text-sm">
                  <SeverityBadge severity={a.severity} />
                  <span className="font-medium">{a.ruleName}</span>
                  <span className="text-slate-500">· {a.nodeName}</span>
                  <span className="ml-auto text-slate-500">
                    {METRIC_LABEL[a.metric]} máx. {fmtMetric(a.metric, a.peakValue, unit)} · {timeAgo(a.openedAt)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card>
          <CardTitle>Ubicaciones</CardTitle>
          {list.length === 0 ? <Empty>Sin nodos</Empty> : (
            <ul className="space-y-3">
              {groupByLocation(list).map(([loc, ns]) => (
                <li key={loc}>
                  <div className="text-sm font-medium">📍 {loc}</div>
                  <ul className="mt-1 space-y-1 pl-5">
                    {ns.map((n) => (
                      <li key={n.id} className="flex items-center justify-between text-sm">
                        <Link to={`/nodos/${n.id}`} className="hover:underline">{n.name}</Link>
                        <StatusDot online={n.online} label={n.online ? 'En línea' : 'Offline'} />
                      </li>
                    ))}
                  </ul>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  )
}
