import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type {
  AlertDto,
  BucketCode,
  CommandDto,
  InvalidReading,
  NodeDto,
  PreviewResult,
  Role,
  RuleDto,
  RuleInput,
  SeriesResponse,
  SystemStatus,
  UserDto,
} from './types'

export const keys = {
  nodes: ['nodes'] as const,
  node: (id: number) => ['nodes', id] as const,
  readings: (id: number, range: string) => ['readings', id, range] as const,
  alerts: (status: string) => ['alerts', status] as const,
  rules: ['rules'] as const,
  commands: (nodeId: number) => ['commands', nodeId] as const,
  users: ['users'] as const,
  system: ['system'] as const,
  invalid: ['invalid-readings'] as const,
}

export function useNodes() {
  return useQuery({ queryKey: keys.nodes, queryFn: () => api<NodeDto[]>('/api/nodes'), refetchInterval: 60_000 })
}

export function useNode(id: number) {
  return useQuery({ queryKey: keys.node(id), queryFn: () => api<NodeDto>(`/api/nodes/${id}`) })
}

export interface RangeSpec {
  key: string
  label: string
  ms: number
  bucket: BucketCode
  stepMs: number
}

export const RANGES: RangeSpec[] = [
  { key: '1h', label: '1 h', ms: 3_600_000, bucket: 'raw', stepMs: 10_000 },
  { key: '24h', label: '24 h', ms: 86_400_000, bucket: '1m', stepMs: 60_000 },
  { key: '7d', label: '7 días', ms: 7 * 86_400_000, bucket: '1h', stepMs: 3_600_000 },
]

export function useReadings(nodeId: number, range: RangeSpec) {
  return useQuery({
    queryKey: keys.readings(nodeId, range.key),
    queryFn: () => {
      const to = new Date()
      const from = new Date(to.getTime() - range.ms)
      const qs = new URLSearchParams({ from: from.toISOString(), to: to.toISOString(), bucket: range.bucket })
      return api<SeriesResponse>(`/api/nodes/${nodeId}/readings?${qs}`)
    },
    placeholderData: keepPreviousData,
    // El rango de 1 h se actualiza por WebSocket; los agregados se refrescan cada minuto.
    refetchInterval: range.bucket === 'raw' ? 5 * 60_000 : 60_000,
  })
}

export function useAlerts(status: 'OPEN' | 'CLOSED' | 'ALL') {
  return useQuery({
    queryKey: keys.alerts(status),
    queryFn: () => api<AlertDto[]>(`/api/alerts?status=${status}&limit=200`),
  })
}

export function useAckAlert() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api<AlertDto>(`/api/alerts/${id}/ack`, { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['alerts'] }),
  })
}

export function useRules() {
  return useQuery({ queryKey: keys.rules, queryFn: () => api<RuleDto[]>('/api/rules') })
}

export function useSaveRule() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id?: number; input: RuleInput }) =>
      id
        ? api<RuleDto>(`/api/rules/${id}`, { method: 'PUT', json: input })
        : api<RuleDto>('/api/rules', { method: 'POST', json: input }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.rules })
      qc.invalidateQueries({ queryKey: ['alerts'] })
    },
  })
}

export function useDeleteRule() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api<void>(`/api/rules/${id}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.rules }),
  })
}

export function usePreviewRule() {
  return useMutation({
    mutationFn: (input: RuleInput & { nodeId: number }) =>
      api<PreviewResult>('/api/rules/preview', { method: 'POST', json: input }),
  })
}

export function useCommands(nodeId: number) {
  return useQuery({
    queryKey: keys.commands(nodeId),
    queryFn: () => api<CommandDto[]>(`/api/nodes/${nodeId}/commands?limit=5`),
  })
}

export function useRelayCommand(nodeId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (on: boolean) =>
      api<CommandDto>(`/api/nodes/${nodeId}/commands/relay`, { method: 'POST', json: { on } }),
    onSuccess: (cmd) => {
      qc.setQueryData<CommandDto[]>(keys.commands(nodeId), (old) => [cmd, ...(old ?? []).filter((c) => c.id !== cmd.id)])
    },
  })
}

export function useUpdateNode(nodeId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { name: string; location: string }) =>
      api<NodeDto>(`/api/nodes/${nodeId}`, { method: 'PUT', json: body }),
    onSuccess: (node) => {
      qc.setQueryData(keys.node(nodeId), node)
      qc.invalidateQueries({ queryKey: keys.nodes })
    },
  })
}

export function useSetInterval(nodeId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (intervalSeconds: number) =>
      api<NodeDto>(`/api/nodes/${nodeId}/config`, { method: 'PUT', json: { intervalSeconds } }),
    onSuccess: (node) => qc.setQueryData(keys.node(nodeId), node),
  })
}

export function useUsers(enabled: boolean) {
  return useQuery({ queryKey: keys.users, queryFn: () => api<UserDto[]>('/api/users'), enabled })
}

export function useCreateUser() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { email: string; password: string; role: Role }) =>
      api<UserDto>('/api/users', { method: 'POST', json: body }),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.users }),
  })
}

export function useDeleteUser() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api<void>(`/api/users/${id}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.users }),
  })
}

export function useSystem(enabled: boolean) {
  return useQuery({
    queryKey: keys.system,
    queryFn: () => api<SystemStatus>('/api/settings/system'),
    enabled,
    refetchInterval: 15_000,
  })
}

export function useInvalidReadings(enabled: boolean) {
  return useQuery({
    queryKey: keys.invalid,
    queryFn: () => api<InvalidReading[]>('/api/settings/invalid-readings?limit=20'),
    enabled,
  })
}

export function useTestNotification() {
  return useMutation({
    mutationFn: () => api<{ delivered: boolean }>('/api/settings/notifications/test', { method: 'POST' }),
  })
}
