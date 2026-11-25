import { useState, type FormEvent } from 'react'
import { Navigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { Button, ErrorBox, Field, inputClass } from '../components/ui'

export function LoginPage() {
  const { session, login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [loading, setLoading] = useState(false)

  if (session) return <Navigate to="/" replace />

  async function submit(e: FormEvent) {
    e.preventDefault()
    setLoading(true)
    setError(null)
    try {
      await login(email.trim(), password)
    } catch (err) {
      setError(err)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-[#0f2a44] to-slate-900 px-4">
      <div className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl dark:bg-slate-900">
        <div className="mb-6 flex items-center gap-3">
          <img src="./favicon.svg" alt="" className="h-10 w-10" />
          <div>
            <h1 className="text-xl font-bold text-slate-900 dark:text-white">SensoNet</h1>
            <p className="text-sm text-slate-500">Monitor IoT en tiempo real</p>
          </div>
        </div>
        <form onSubmit={submit} className="space-y-4" noValidate>
          <Field label="Correo">
            <input className={inputClass} type="email" autoComplete="username" required value={email}
              onChange={(e) => setEmail(e.target.value)} />
          </Field>
          <Field label="Contraseña">
            <input className={inputClass} type="password" autoComplete="current-password" required value={password}
              onChange={(e) => setPassword(e.target.value)} />
          </Field>
          {error !== null && <ErrorBox error={error} />}
          <Button type="submit" className="w-full" disabled={loading || !email || !password}>
            {loading ? 'Entrando…' : 'Iniciar sesión'}
          </Button>
        </form>
      </div>
    </div>
  )
}
