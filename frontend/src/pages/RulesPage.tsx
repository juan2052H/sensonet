import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { useDeleteRule, useNodes, usePreviewRule, useRules, useSaveRule } from '../api/hooks'
import type { Metric, Operator, RuleDto, RuleInput, Severity } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Badge, Button, Card, CardTitle, Empty, ErrorBox, Field, SeverityBadge, Spinner, cx, inputClass } from '../components/ui'
import { METRIC_LABEL, SEVERITY_LABEL, fmtDateTime, fmtDuration } from '../lib/format'
import { hysteresisError } from '../lib/rules'

const EMPTY: RuleInput = {
  nodeId: null, name: '', metric: 'TEMPERATURE', operator: 'GT', threshold: 30, recovery: 28,
  durationSeconds: 300, severity: 'WARNING', enabled: true,
}

const UNITS: Record<Metric, string> = { TEMPERATURE: '°C', HUMIDITY: '%', PRESSURE: 'hPa', RSSI: 'dBm' }


export function RulesPage() {
  const rules = useRules()
  const nodes = useNodes()
  const remove = useDeleteRule()
  const { isAdmin } = useAuth()
  const [editing, setEditing] = useState<{ id?: number; input: RuleInput } | null>(null)
  const nodeName = (id?: number | null) => (id == null ? 'Todos los nodos' : nodes.data?.find((n) => n.id === id)?.name ?? `#${id}`)

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Reglas de alerta</h1>
        {isAdmin && !editing && <Button onClick={() => setEditing({ input: EMPTY })}>Nueva regla</Button>}
      </div>
      <p className="max-w-3xl text-sm text-slate-600 dark:text-slate-400">
        Una regla abre una alerta cuando la condición se cumple durante el tiempo indicado y la cierra cuando el valor
        cruza el umbral de recuperación. Los dos umbrales (histéresis) evitan que la alerta se abra y cierre muchas
        veces cuando el valor oscila cerca del límite.
      </p>

      {editing && (
        <RuleForm key={editing.id ?? 'new'} id={editing.id} initial={editing.input} onDone={() => setEditing(null)} />
      )}

      {remove.error && <ErrorBox error={remove.error} />}
      <Card className="overflow-x-auto p-0">
        {rules.isPending ? <div className="p-4"><Spinner /></div> : rules.error ? <div className="p-4"><ErrorBox error={rules.error} /></div> : (rules.data?.length ?? 0) === 0 ? (
          <Empty>No hay reglas configuradas.</Empty>
        ) : (
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 text-xs uppercase text-slate-500 dark:border-slate-800">
              <tr>
                <th className="px-4 py-2">Nombre</th>
                <th className="px-4 py-2">Condición</th>
                <th className="px-4 py-2">Nodo</th>
                <th className="px-4 py-2">Severidad</th>
                <th className="px-4 py-2">Estado</th>
                <th className="px-4 py-2" />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
              {rules.data!.map((r) => (
                <tr key={r.id}>
                  <td className="px-4 py-2 font-medium">{r.name}</td>
                  <td className="px-4 py-2">{describe(r)}</td>
                  <td className="px-4 py-2">{nodeName(r.nodeId)}</td>
                  <td className="px-4 py-2"><SeverityBadge severity={r.severity} /></td>
                  <td className="px-4 py-2">{r.enabled ? <Badge className="bg-emerald-100 text-emerald-800 dark:bg-emerald-500/15 dark:text-emerald-300">Activa</Badge> : <Badge>Inactiva</Badge>}</td>
                  <td className="px-4 py-2 text-right whitespace-nowrap">
                    {isAdmin && (
                      <>
                        <Button variant="ghost" onClick={() => setEditing({ id: r.id, input: toInput(r) })}>Editar</Button>
                        <Button variant="ghost" className="text-red-600" disabled={remove.isPending}
                          onClick={() => window.confirm(`¿Eliminar la regla "${r.name}"?`) && remove.mutate(r.id)}>Eliminar</Button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  )
}

function describe(r: RuleDto): string {
  const op = r.operator === 'GT' ? '>' : '<'
  const back = r.operator === 'GT' ? 'baja de' : 'sube de'
  const dur = r.durationSeconds > 0 ? ` durante ${fmtDuration(r.durationSeconds * 1000)}` : ''
  return `${METRIC_LABEL[r.metric]} ${op} ${r.threshold} ${r.unit}${dur}; se cierra si ${back} ${r.recovery} ${r.unit}`
}

function toInput(r: RuleDto): RuleInput {
  return {
    nodeId: r.nodeId ?? null, name: r.name, metric: r.metric, operator: r.operator, threshold: r.threshold,
    recovery: r.recovery, durationSeconds: r.durationSeconds, severity: r.severity, enabled: r.enabled,
  }
}

function RuleForm({ id, initial, onDone }: { id?: number; initial: RuleInput; onDone: () => void }) {
  const [form, setForm] = useState<RuleInput>(initial)
  const [previewNode, setPreviewNode] = useState<number | ''>(initial.nodeId ?? '')
  const nodes = useNodes()
  const save = useSaveRule()
  const preview = usePreviewRule()
  const hErr = hysteresisError(form)
  const fieldErrors = save.error instanceof ApiError ? save.error.problem.errors ?? {} : {}
  const set = <K extends keyof RuleInput>(k: K, v: RuleInput[K]) => setForm((f) => ({ ...f, [k]: v }))

  function submit(e: FormEvent) {
    e.preventDefault()
    if (hErr) return
    save.mutate({ id, input: { ...form, name: form.name.trim() } }, { onSuccess: onDone })
  }

  const targetNode = form.nodeId ?? (previewNode === '' ? null : previewNode)

  return (
    <Card>
      <CardTitle>{id ? 'Editar regla' : 'Nueva regla'}</CardTitle>
      {id && <p className="mb-3 text-xs text-amber-700 dark:text-amber-400">Al guardar se cierran las alertas abiertas de esta regla.</p>}
      <form onSubmit={submit} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Field label="Nombre" error={fieldErrors.name}>
          <input className={inputClass} required maxLength={80} value={form.name} onChange={(e) => set('name', e.target.value)} />
        </Field>
        <Field label="Nodo">
          <select className={inputClass} value={form.nodeId ?? ''} onChange={(e) => set('nodeId', e.target.value ? Number(e.target.value) : null)}>
            <option value="">Todos los nodos</option>
            {nodes.data?.map((n) => <option key={n.id} value={n.id}>{n.name}</option>)}
          </select>
        </Field>
        <Field label="Métrica">
          <select className={inputClass} value={form.metric} onChange={(e) => set('metric', e.target.value as Metric)}>
            {(Object.keys(METRIC_LABEL) as Metric[]).map((m) => <option key={m} value={m}>{METRIC_LABEL[m]}</option>)}
          </select>
        </Field>
        <Field label="Severidad">
          <select className={inputClass} value={form.severity} onChange={(e) => set('severity', e.target.value as Severity)}>
            {(Object.keys(SEVERITY_LABEL) as Severity[]).map((s) => <option key={s} value={s}>{SEVERITY_LABEL[s]}</option>)}
          </select>
        </Field>
        <Field label="Condición">
          <select className={inputClass} value={form.operator} onChange={(e) => set('operator', e.target.value as Operator)}>
            <option value="GT">Mayor que (&gt;)</option>
            <option value="LT">Menor que (&lt;)</option>
          </select>
        </Field>
        <Field label={`Umbral de disparo (${UNITS[form.metric]})`}>
          <input className={inputClass} type="number" step="0.1" value={form.threshold} onChange={(e) => set('threshold', Number(e.target.value))} />
        </Field>
        <Field label={`Umbral de recuperación (${UNITS[form.metric]})`} error={hErr ?? undefined}>
          <input className={cx(inputClass, hErr && 'border-red-500')} type="number" step="0.1" value={form.recovery}
            onChange={(e) => set('recovery', Number(e.target.value))} />
        </Field>
        <Field label="Duración mínima (s)" hint={fmtDuration(form.durationSeconds * 1000)} error={fieldErrors.durationSeconds}>
          <input className={inputClass} type="number" min={0} max={86400} value={form.durationSeconds}
            onChange={(e) => set('durationSeconds', Number(e.target.value))} />
        </Field>
        <label className="flex items-center gap-2 text-sm sm:col-span-2 lg:col-span-4">
          <input type="checkbox" checked={form.enabled} onChange={(e) => set('enabled', e.target.checked)} /> Regla activa
        </label>

        <div className="rounded-lg bg-slate-50 p-3 text-sm sm:col-span-2 lg:col-span-4 dark:bg-slate-800/50">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium">Vista previa sobre las últimas 24 h:</span>
            {form.nodeId == null && (
              <select className={cx(inputClass, 'w-auto')} value={previewNode} aria-label="Nodo para la vista previa"
                onChange={(e) => setPreviewNode(e.target.value ? Number(e.target.value) : '')}>
                <option value="">Elige un nodo</option>
                {nodes.data?.map((n) => <option key={n.id} value={n.id}>{n.name}</option>)}
              </select>
            )}
            <Button variant="secondary" disabled={targetNode == null || !!hErr || preview.isPending}
              onClick={() => targetNode != null && preview.mutate({ ...form, nodeId: targetNode })}>
              Probar regla
            </Button>
          </div>
          {preview.data && (
            <p className="mt-2">
              Esta regla se habría disparado <strong>{preview.data.openings} {preview.data.openings === 1 ? 'vez' : 'veces'}</strong>{' '}
              en las últimas 24 h ({preview.data.samples} lecturas analizadas).
              {preview.data.intervals.slice(0, 3).map((i) => (
                <span key={i.openedAt} className="ml-2 text-xs text-slate-500">
                  [{fmtDateTime(i.openedAt)}{i.closedAt ? ` → ${fmtDateTime(i.closedAt)}` : ' → sigue abierta'}]
                </span>
              ))}
            </p>
          )}
          {preview.error && <div className="mt-2"><ErrorBox error={preview.error} /></div>}
        </div>

        {save.error && <div className="sm:col-span-2 lg:col-span-4"><ErrorBox error={save.error} /></div>}
        <div className="flex gap-2 sm:col-span-2 lg:col-span-4">
          <Button type="submit" disabled={save.isPending || !!hErr || !form.name.trim()}>{save.isPending ? 'Guardando…' : 'Guardar'}</Button>
          <Button variant="secondary" onClick={onDone}>Cancelar</Button>
        </div>
      </form>
    </Card>
  )
}
