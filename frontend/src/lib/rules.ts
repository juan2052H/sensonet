import type { RuleInput } from '../api/types'

/** Valida la histéresis igual que el backend, para avisar antes de enviar. */
export function hysteresisError(r: Pick<RuleInput, 'operator' | 'threshold' | 'recovery'>): string | null {
  if (r.operator === 'GT' && r.recovery > r.threshold) return 'Con ">" la recuperación debe ser menor o igual al umbral'
  if (r.operator === 'LT' && r.recovery < r.threshold) return 'Con "<" la recuperación debe ser mayor o igual al umbral'
  return null
}
