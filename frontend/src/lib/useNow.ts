import { useEffect, useState } from 'react'

/** Hora actual que se actualiza cada `everyMs` (para duraciones "en vivo"). */
export function useNow(everyMs: number): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const t = window.setInterval(() => setNow(Date.now()), everyMs)
    return () => window.clearInterval(t)
  }, [everyMs])
  return now
}
