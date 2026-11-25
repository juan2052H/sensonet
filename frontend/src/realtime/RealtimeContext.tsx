import { Client, type IMessage } from '@stomp/stompjs'
import { useQueryClient } from '@tanstack/react-query'
import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { wsUrl } from '../api/client'
import { keys } from '../api/hooks'
import type { CommandDto, NodeDto } from '../api/types'
import { useAuth } from '../auth/AuthContext'

export type ConnectionStatus = 'connecting' | 'live' | 'reconnecting' | 'offline'

interface RealtimeValue {
  status: ConnectionStatus
  client: Client | null
}

const RealtimeContext = createContext<RealtimeValue>({ status: 'offline', client: null })

/** Una sola conexión STOMP por sesión; reconecta sola y sincroniza la caché de TanStack Query. */
export function RealtimeProvider({ children }: { children: ReactNode }) {
  const { session } = useAuth()
  const [status, setStatus] = useState<ConnectionStatus>('offline')
  const [client, setClient] = useState<Client | null>(null)
  const everConnected = useRef(false)

  useEffect(() => {
    if (!session) return
    everConnected.current = false
    const c = new Client({
      brokerURL: wsUrl(),
      connectHeaders: { Authorization: `Bearer ${session.token}` },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        everConnected.current = true
        setStatus('live')
      },
      onWebSocketClose: () => setStatus(everConnected.current ? 'reconnecting' : 'connecting'),
      onStompError: () => setStatus('reconnecting'),
    })
    setStatus('connecting')
    c.activate()
    setClient(c)
    return () => {
      void c.deactivate()
      setClient(null)
      setStatus('offline')
    }
  }, [session])

  return (
    <RealtimeContext.Provider value={{ status, client }}>
      <CacheSync />
      {children}
    </RealtimeContext.Provider>
  )
}

export function useRealtime(): RealtimeValue {
  return useContext(RealtimeContext)
}

/** Se suscribe a un destino mientras la conexión está activa (y vuelve a suscribirse al reconectar). */
export function useTopic<T>(destination: string | null, onMessage: (body: T) => void) {
  const { status, client } = useRealtime()
  const handler = useRef(onMessage)
  useEffect(() => {
    handler.current = onMessage
  })

  useEffect(() => {
    if (!destination || status !== 'live' || !client?.connected) return
    const sub = client.subscribe(destination, (m: IMessage) => {
      try {
        handler.current(JSON.parse(m.body) as T)
      } catch {
        /* mensaje ilegible */
      }
    })
    return () => {
      try {
        sub.unsubscribe()
      } catch {
        /* la conexión ya se cerró */
      }
    }
  }, [destination, status, client])
}

function CacheSync() {
  const qc = useQueryClient()

  useTopic<NodeDto>('/topic/nodes', (node) => {
    qc.setQueryData<NodeDto[]>(keys.nodes, (old) => {
      if (!old) return old
      const i = old.findIndex((n) => n.id === node.id)
      if (i === -1) return [...old, node].sort((a, b) => a.name.localeCompare(b.name))
      const copy = [...old]
      copy[i] = node
      return copy
    })
    qc.setQueryData(keys.node(node.id), node)
  })

  useTopic('/topic/alerts', () => {
    void qc.invalidateQueries({ queryKey: ['alerts'] })
  })

  useTopic<CommandDto>('/topic/commands', (cmd) => {
    qc.setQueryData<CommandDto[]>(keys.commands(cmd.nodeId), (old) => {
      if (!old) return old
      return old.some((c) => c.id === cmd.id)
        ? old.map((c) => (c.id === cmd.id ? cmd : c))
        : [cmd, ...old].slice(0, 5)
    })
  })
  return null
}
