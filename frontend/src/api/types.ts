export type Role = 'ADMIN' | 'VIEWER'
export type Metric = 'TEMPERATURE' | 'HUMIDITY' | 'PRESSURE' | 'RSSI'
export type Operator = 'GT' | 'LT'
export type Severity = 'INFO' | 'WARNING' | 'CRITICAL'
export type CommandStatus = 'PENDING' | 'CONFIRMED' | 'FAILED'
export type BucketCode = 'raw' | '1m' | '1h' | '1d'

export interface LoginResponse {
  token: string
  expiresAt: string
  email: string
  role: Role
}

export interface LastReading {
  ts: string
  temperature: number | null
  humidity: number | null
  pressure: number | null
}

export interface NodeDto {
  id: number
  code: string
  name: string
  location?: string | null
  firmware?: string | null
  online: boolean
  lastSeen?: string | null
  relayOn?: boolean | null
  rssi?: number | null
  buffered?: number | null
  dropped?: number | null
  intervalSeconds: number
  lastReading?: LastReading | null
}

export interface ReadingPoint {
  ts: string
  temperature: number | null
  humidity: number | null
  pressure: number | null
  backfilled: boolean
}

export interface SeriesResponse {
  nodeId: number
  from: string
  to: string
  bucket: string
  points: ReadingPoint[]
}

export interface AlertDto {
  id: number
  ruleId?: number | null
  ruleName: string
  nodeId: number
  nodeName?: string | null
  metric: Metric
  unit: string
  operator: Operator
  threshold: number
  severity: Severity
  status: 'OPEN' | 'CLOSED'
  openedAt: string
  closedAt?: string | null
  openValue: number
  peakValue: number
  ackedBy?: string | null
  ackedAt?: string | null
}

export interface RuleDto {
  id: number
  nodeId?: number | null
  name: string
  metric: Metric
  unit: string
  operator: Operator
  threshold: number
  recovery: number
  durationSeconds: number
  severity: Severity
  enabled: boolean
  createdAt: string
}

export interface RuleInput {
  nodeId: number | null
  name: string
  metric: Metric
  operator: Operator
  threshold: number
  recovery: number
  durationSeconds: number
  severity: Severity
  enabled: boolean
}

export interface PreviewResult {
  samples: number
  openings: number
  intervals: { openedAt: string; closedAt?: string | null; peak: number }[]
}

export interface CommandDto {
  id: string
  nodeId: number
  type: string
  payload: string
  status: CommandStatus
  error?: string | null
  createdBy?: string | null
  createdAt: string
  ackedAt?: string | null
}

export interface UserDto {
  id: number
  email: string
  role: Role
  createdAt: string
}

export interface SystemStatus {
  mqttConnected: boolean
  dbFlavor: string
  telegramEnabled: boolean
  telegramChatId?: string | null
  telegramMinIntervalSeconds: number
  commandTimeoutSeconds: number
}

export interface InvalidReading {
  id: number
  nodeCode: string
  receivedAt: string
  reason: string
  payload?: string | null
}

export interface Problem {
  title?: string
  detail?: string
  status?: number
  errors?: Record<string, string>
}
