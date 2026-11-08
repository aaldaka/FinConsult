import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '@/api/client'
import { createStreamTicket } from '@/api/notifications'
import { session } from '@/lib/session'

const RECONNECT_DELAY_MS = 3000

// sse cant send headers, so the stream is opened with a one time ticket instead of the token
export function useNotificationStream(userId: number | undefined) {
  const queryClient = useQueryClient()

  useEffect(() => {
    if (!userId || !session.getToken()) return

    let source: EventSource | null = null
    let retryTimer: ReturnType<typeof setTimeout> | undefined
    let stopped = false

    function scheduleReconnect() {
      if (!stopped) retryTimer = setTimeout(connect, RECONNECT_DELAY_MS)
    }

    async function connect() {
      let ticket: string
      try {
        ticket = (await createStreamTicket()).ticket
      } catch {
        // a 401 already logs out through the axios interceptor, anything else is tried again
        scheduleReconnect()
        return
      }
      if (stopped) return

      const es = new EventSource(`${api.defaults.baseURL}/notifications/stream?ticket=${encodeURIComponent(ticket)}`)
      source = es

      es.addEventListener('notification', (event) => {
        queryClient.invalidateQueries({ queryKey: ['notifications'] })

        try {
          const payload = JSON.parse((event as MessageEvent<string>).data)
          if (payload.type === 'KYC_CHECKED') {
            queryClient.invalidateQueries({ queryKey: ['kyc'] })
          } else if (payload.type === 'CERTIFICATES_CHECKED') {
            queryClient.invalidateQueries({ queryKey: ['advisorApplications'] })
          }
        } catch {
          /* not json */
        }
      })

      // a ticket works once, so the browsers own reconnect would be refused. close and ask for a new one
      es.onerror = () => {
        es.close()
        queryClient.invalidateQueries({ queryKey: ['notifications'] })
        scheduleReconnect()
      }
    }

    connect()

    return () => {
      stopped = true
      clearTimeout(retryTimer)
      source?.close()
    }
  }, [userId, queryClient])
}
