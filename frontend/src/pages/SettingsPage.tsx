import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { useCreateUser, useDeleteUser, useInvalidReadings, useSystem, useTestNotification, useUsers } from '../api/hooks'
import type { Role } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Badge, Button, Card, CardTitle, Empty, ErrorBox, Field, Spinner, cx, inputClass } from '../components/ui'
import { fmtDateTime } from '../lib/format'
import { usePreferences } from '../lib/preferences'

export function SettingsPage() {
  const { isAdmin } = useAuth()
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Ajustes</h1>
      <div className="grid gap-4 lg:grid-cols-2">
        <PreferencesCard />
        {isAdmin && <SystemCard />}
        {isAdmin && <UsersCard />}
        {isAdmin && <InvalidReadingsCard />}
      </div>
    </div>
  )
}

function PreferencesCard() {
  const { unit, setUnit, theme, setTheme } = usePreferences()
  const toggle = (active: boolean) => cx('rounded-md px-3 py-1 text-sm', active ? 'bg-teal-600 text-white' : 'text-slate-600 dark:text-slate-300')
  return (
    <Card>
      <CardTitle>Preferencias</CardTitle>
      <div className="space-y-3 text-sm">
        <div className="flex items-center justify-between">
          <span>Unidad de temperatura</span>
          <div className="inline-flex rounded-lg border border-slate-200 p-0.5 dark:border-slate-700">
            <button type="button" className={toggle(unit === 'C')} onClick={() => setUnit('C')} aria-pressed={unit === 'C'}>°C</button>
            <button type="button" className={toggle(unit === 'F')} onClick={() => setUnit('F')} aria-pressed={unit === 'F'}>°F</button>
          </div>
        </div>
        <div className="flex items-center justify-between">
          <span>Tema</span>
          <div className="inline-flex rounded-lg border border-slate-200 p-0.5 dark:border-slate-700">
            <button type="button" className={toggle(theme === 'light')} onClick={() => setTheme('light')} aria-pressed={theme === 'light'}>Claro</button>
            <button type="button" className={toggle(theme === 'dark')} onClick={() => setTheme('dark')} aria-pressed={theme === 'dark'}>Oscuro</button>
          </div>
        </div>
      </div>
    </Card>
  )
}

function SystemCard() {
  const system = useSystem(true)
  const test = useTestNotification()
  const s = system.data
  const yes = 'bg-emerald-100 text-emerald-800 dark:bg-emerald-500/15 dark:text-emerald-300'
  const no = 'bg-red-100 text-red-800 dark:bg-red-500/15 dark:text-red-300'
  return (
    <Card>
      <CardTitle>Sistema y notificaciones</CardTitle>
      {system.isPending ? <Spinner /> : system.error ? <ErrorBox error={system.error} /> : s && (
        <div className="space-y-3 text-sm">
          <div className="flex justify-between"><span>Broker MQTT</span><Badge className={s.mqttConnected ? yes : no}>{s.mqttConnected ? 'Conectado' : 'Desconectado'}</Badge></div>
          <div className="flex justify-between"><span>Base de datos</span><Badge>{s.dbFlavor === 'timescale' ? 'PostgreSQL + TimescaleDB' : 'PostgreSQL'}</Badge></div>
          <div className="flex justify-between"><span>Tiempo máximo de confirmación</span><span>{s.commandTimeoutSeconds} s</span></div>
          <div className="flex justify-between">
            <span>Bot de Telegram</span>
            <Badge className={s.telegramEnabled ? yes : undefined}>{s.telegramEnabled ? `Activo · chat ${s.telegramChatId}` : 'No configurado'}</Badge>
          </div>
          {s.telegramEnabled ? (
            <div className="flex items-center gap-2">
              <Button variant="secondary" disabled={test.isPending} onClick={() => test.mutate()}>Enviar mensaje de prueba</Button>
              {test.data && <span className={test.data.delivered ? 'text-emerald-600' : 'text-red-600'}>{test.data.delivered ? 'Enviado' : 'No se pudo enviar'}</span>}
            </div>
          ) : (
            <details className="rounded-lg bg-slate-50 p-3 dark:bg-slate-800/50">
              <summary className="cursor-pointer font-medium">Cómo vincular el bot</summary>
              <ol className="mt-2 list-decimal space-y-1 pl-5 text-slate-600 dark:text-slate-400">
                <li>En Telegram, habla con @BotFather y crea un bot con <code>/newbot</code>; copia el token.</li>
                <li>Escríbele cualquier mensaje a tu bot.</li>
                <li>Abre <code>https://api.telegram.org/bot&lt;TOKEN&gt;/getUpdates</code> y copia el <code>chat.id</code>.</li>
                <li>Pon <code>TELEGRAM_BOT_TOKEN</code> y <code>TELEGRAM_CHAT_ID</code> en <code>.env</code> y reinicia el backend.</li>
              </ol>
              <p className="mt-2 text-xs text-slate-500">El token es un secreto: nunca lo subas al repositorio.</p>
            </details>
          )}
          <p className="text-xs text-slate-500">Máximo un mensaje por regla y nodo cada {s.telegramMinIntervalSeconds} s.</p>
        </div>
      )}
    </Card>
  )
}

function UsersCard() {
  const users = useUsers(true)
  const create = useCreateUser()
  const remove = useDeleteUser()
  const { session } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<Role>('VIEWER')
  const errors = create.error instanceof ApiError ? create.error.problem.errors ?? {} : {}

  function submit(e: FormEvent) {
    e.preventDefault()
    create.mutate({ email, password, role }, { onSuccess: () => { setEmail(''); setPassword('') } })
  }

  return (
    <Card>
      <CardTitle>Usuarios</CardTitle>
      {users.isPending ? <Spinner /> : users.error ? <ErrorBox error={users.error} /> : (users.data?.length ?? 0) === 0 ? <Empty>Sin usuarios</Empty> : (
        <ul className="divide-y divide-slate-100 text-sm dark:divide-slate-800">
          {users.data!.map((u) => (
            <li key={u.id} className="flex items-center justify-between gap-2 py-2">
              <span>{u.email} <Badge>{u.role === 'ADMIN' ? 'Administrador' : 'Lectura'}</Badge></span>
              {u.email !== session?.email && (
                <Button variant="ghost" className="text-red-600" disabled={remove.isPending}
                  onClick={() => window.confirm(`¿Eliminar a ${u.email}?`) && remove.mutate(u.id)}>Eliminar</Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {remove.error && <ErrorBox error={remove.error} />}
      <form onSubmit={submit} className="mt-4 grid gap-2 border-t border-slate-100 pt-4 sm:grid-cols-2 dark:border-slate-800">
        <Field label="Correo" error={errors.email}>
          <input className={inputClass} type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label="Contraseña" hint="Mínimo 10 caracteres" error={errors.password}>
          <input className={inputClass} type="password" required minLength={10} value={password} autoComplete="new-password"
            onChange={(e) => setPassword(e.target.value)} />
        </Field>
        <Field label="Rol">
          <select className={inputClass} value={role} onChange={(e) => setRole(e.target.value as Role)}>
            <option value="VIEWER">Lectura</option>
            <option value="ADMIN">Administrador</option>
          </select>
        </Field>
        <div className="flex items-end">
          <Button type="submit" disabled={create.isPending || !email || password.length < 10}>Crear usuario</Button>
        </div>
        {create.error && !Object.keys(errors).length && <div className="sm:col-span-2"><ErrorBox error={create.error} /></div>}
      </form>
    </Card>
  )
}

function InvalidReadingsCard() {
  const invalid = useInvalidReadings(true)
  return (
    <Card className="lg:col-span-2">
      <CardTitle>Lecturas rechazadas recientes</CardTitle>
      {invalid.isPending ? <Spinner /> : invalid.error ? <ErrorBox error={invalid.error} /> : (invalid.data?.length ?? 0) === 0 ? (
        <Empty>Ninguna lectura rechazada.</Empty>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase text-slate-500">
              <tr><th className="py-1 pr-4">Recibida</th><th className="py-1 pr-4">Nodo</th><th className="py-1 pr-4">Motivo</th><th className="py-1">Mensaje</th></tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
              {invalid.data!.map((r) => (
                <tr key={r.id}>
                  <td className="py-1 pr-4 whitespace-nowrap">{fmtDateTime(r.receivedAt)}</td>
                  <td className="py-1 pr-4">{r.nodeCode}</td>
                  <td className="py-1 pr-4">{r.reason}</td>
                  <td className="max-w-xs truncate py-1 font-mono text-xs text-slate-500" title={r.payload ?? ''}>{r.payload}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}
