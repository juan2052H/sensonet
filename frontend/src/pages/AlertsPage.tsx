import { useState } from 'react'
import { useNow } from '../lib/useNow'
import { Link } from 'react-router'
import { useAckAlert, useAlerts, useNodes } from '../api/hooks'
import { useAuth } from '../auth/AuthContext'
import { Badge, Button, Card, Empty, ErrorBox, SeverityBadge, Spinner, cx, inputClass } from '../components/ui'
import { METRIC_LABEL, fmtDateTime, fmtDuration, fmtMetric } from '../lib/format'
import { usePreferences } from '../lib/preferences'

type Status = 'OPEN' | 'CLOSED' | 'ALL'
const TABS: [Status, string][] = [['OPEN', 'Abiertas'], ['CLOSED', 'Cerradas'], ['ALL', 'Todas']]

export function AlertsPage() {
  const [status, setStatus] = useState<Status>('OPEN')
  const [nodeId, setNodeId] = useState<string>('')
  const alerts = useAlerts(status)
  const nodes = useNodes()
  const ack = useAckAlert()
  const { isAdmin } = useAuth()
  const { unit } = usePreferences()
  const now = useNow(30_000)
  const list = (alerts.data ?? []).filter((a) => !nodeId || a.nodeId === Number(nodeId))

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Alertas</h1>
      <div className="flex flex-wrap items-center gap-3">
        <div className="inline-flex rounded-lg border border-slate-200 p-0.5 dark:border-slate-700" role="tablist">
          {TABS.map(([k, label]) => (
            <button key={k} role="tab" aria-selected={k === status} type="button" onClick={() => setStatus(k)}
              className={cx('rounded-md px-3 py-1 text-sm', k === status ? 'bg-teal-600 text-white' : 'text-slate-600 dark:text-slate-300')}>
              {label}
            </button>
          ))}
        </div>
        <select className={cx(inputClass, 'w-auto')} value={nodeId} onChange={(e) => setNodeId(e.target.value)} aria-label="Filtrar por nodo">
          <option value="">Todos los nodos</option>
          {nodes.data?.map((n) => <option key={n.id} value={n.id}>{n.name}</option>)}
        </select>
      </div>

      {ack.error && <ErrorBox error={ack.error} />}
      <Card className="overflow-x-auto p-0">
        {alerts.isPending ? <div className="p-4"><Spinner /></div> : alerts.error ? <div className="p-4"><ErrorBox error={alerts.error} /></div> : list.length === 0 ? (
          <Empty>No hay alertas {status === 'OPEN' ? 'abiertas' : status === 'CLOSED' ? 'cerradas' : ''}.</Empty>
        ) : (
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 text-xs uppercase text-slate-500 dark:border-slate-800">
              <tr>
                <th className="px-4 py-2">Severidad</th>
                <th className="px-4 py-2">Regla</th>
                <th className="px-4 py-2">Nodo</th>
                <th className="px-4 py-2">Abierta</th>
                <th className="px-4 py-2">Duración</th>
                <th className="px-4 py-2">Valor extremo</th>
                <th className="px-4 py-2">Estado</th>
                <th className="px-4 py-2" />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
              {list.map((a) => {
                const end = a.closedAt ? Date.parse(a.closedAt) : now
                return (
                  <tr key={a.id}>
                    <td className="px-4 py-2"><SeverityBadge severity={a.severity} /></td>
                    <td className="px-4 py-2">
                      <div className="font-medium">{a.ruleName}</div>
                      <div className="text-xs text-slate-500">
                        {METRIC_LABEL[a.metric]} {a.operator === 'GT' ? '>' : '<'} {fmtMetric(a.metric, a.threshold, unit)}
                      </div>
                    </td>
                    <td className="px-4 py-2"><Link className="hover:underline" to={`/nodos/${a.nodeId}`}>{a.nodeName ?? a.nodeId}</Link></td>
                    <td className="px-4 py-2 whitespace-nowrap">{fmtDateTime(a.openedAt)}</td>
                    <td className="px-4 py-2 whitespace-nowrap">{fmtDuration(end - Date.parse(a.openedAt))}</td>
                    <td className="px-4 py-2 whitespace-nowrap font-medium">{fmtMetric(a.metric, a.peakValue, unit)}</td>
                    <td className="px-4 py-2">
                      <div className="flex flex-col gap-1">
                        <Badge className={a.status === 'OPEN' ? 'bg-red-100 text-red-800 dark:bg-red-500/15 dark:text-red-300' : undefined}>
                          {a.status === 'OPEN' ? 'Abierta' : 'Cerrada'}
                        </Badge>
                        {a.ackedBy && <span className="text-xs text-slate-500" title={fmtDateTime(a.ackedAt)}>Reconocida por {a.ackedBy}</span>}
                      </div>
                    </td>
                    <td className="px-4 py-2 text-right">
                      {isAdmin && !a.ackedBy && (
                        <Button variant="secondary" disabled={ack.isPending} onClick={() => ack.mutate(a.id)}>Reconocer</Button>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  )
}
