import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import type { TempUnit } from './format'

type Theme = 'light' | 'dark'

interface Prefs {
  unit: TempUnit
  setUnit: (u: TempUnit) => void
  theme: Theme
  setTheme: (t: Theme) => void
}

const PrefsContext = createContext<Prefs | null>(null)

function initialTheme(): Theme {
  const saved = localStorage.getItem('sensonet.theme')
  if (saved === 'dark' || saved === 'light') return saved
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function PreferencesProvider({ children }: { children: ReactNode }) {
  const [unit, setUnit] = useState<TempUnit>(() => (localStorage.getItem('sensonet.unit') === 'F' ? 'F' : 'C'))
  const [theme, setTheme] = useState<Theme>(initialTheme)

  useEffect(() => {
    localStorage.setItem('sensonet.unit', unit)
  }, [unit])

  useEffect(() => {
    localStorage.setItem('sensonet.theme', theme)
    document.documentElement.classList.toggle('dark', theme === 'dark')
  }, [theme])

  const value = useMemo(() => ({ unit, setUnit, theme, setTheme }), [unit, theme])
  return <PrefsContext.Provider value={value}>{children}</PrefsContext.Provider>
}

export function usePreferences(): Prefs {
  const ctx = useContext(PrefsContext)
  if (!ctx) throw new Error('usePreferences debe usarse dentro de PreferencesProvider')
  return ctx
}
