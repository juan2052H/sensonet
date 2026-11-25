import { useCommands, useRelayCommand } from '../api/hooks'
import type { CommandDto, NodeDto } from '../api/types'
import { fmtTime } from '../lib/format'
import { Badge, Button, Card, CardTitle, ErrorBox } from './ui'

const STATUS_LABEL: Record<CommandDto['status'], { text: string; cls: string }> = {
  PENDING: { text: 'Esperando confirmación…', cls: 'bg-amber-100 text-amber-800 dark:bg-amber-500/15 dark:text-amber-300' },
  CONFIRMED: { text: 'Confirmado', cls: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-500/15 dark:text-emerald-300' },
  FAILED: { text: 'Fallido', cls: 'bg-red-100 text-red-800 dark:bg-red-500/15 dark:text-red-300' },
}

function describe(c: CommandDto): string {
  try {
    return (JSON.parse(c.payload) as { on?: boolean }).on ? 'Encender' : 'Apagar'
  } catch {
    return c.type
  }
}

export function RelayControl({ node, canControl }: { node: NodeDto; canControl: boolean }) {
  const commands = useCommands(node.id)
  const send = useRelayCommand(node.id)
  const pending = commands.data?.some((c) => c.status === 'PENDING') ?? false

  return (
    <Card>
      <CardTitle>Relé</CardTitle>
      <div className="flex items-center justify-between gap-3">
        <div>
          <div className="text-2xl font-semibold">{node.relayOn ? 'Encendido' : node.relayOn === false ? 'Apagado' : 'Desconocido'}</div>
          <p className="text-xs text-slate-500">Estado confirmado por el nodo</p>
        </div>
        {canControl ? (
          <div className="flex gap-2">
            <Button disabled={send.isPending || pending || node.relayOn === true} onClick={() => send.mutate(true)}>Encender</Button>
            <Button variant="secondary" disabled={send.isPending || pending || node.relayOn === false} onClick={() => send.mutate(false)}>Apagar</Button>
          </div>
        ) : (
          <Badge>Solo administradores</Badge>
        )}
      </div>
      {!node.online && canControl && (
        <p className="mt-2 text-xs text-amber-700 dark:text-amber-400">
          El nodo está sin conexión: el comando quedará como fallido si no responde en 10 s.
        </p>
      )}
      {send.error && <div className="mt-2"><ErrorBox error={send.error} /></div>}
      {(commands.data?.length ?? 0) > 0 && (
        <ul className="mt-3 space-y-1.5 border-t border-slate-100 pt-3 text-sm dark:border-slate-800">
          {commands.data!.map((c) => (
            <li key={c.id} className="flex items-center justify-between gap-2">
              <span className="text-slate-600 dark:text-slate-400">
                {fmtTime(c.createdAt)} · {describe(c)}
              </span>
              <span className="flex items-center gap-2">
                {c.error && <span className="text-xs text-slate-500" title={c.error}>{c.error}</span>}
                <Badge className={STATUS_LABEL[c.status].cls}>{STATUS_LABEL[c.status].text}</Badge>
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}
