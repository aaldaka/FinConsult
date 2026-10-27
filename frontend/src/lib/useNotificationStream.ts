import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '@/api/client'
import { session } from '@/lib/session'

// sse cant send headers so the token goes in the url
export function useNotificationStream(userId: number | undefined) {
  const queryClient = useQueryClient()

  useEffect(() => {
    const token = session.getToken()
    if (!userId || !token) return

    const source = new EventSource(`${api.defaults.baseURL}/notifications/stream?token=${encodeURIComponent(token)}`)

    source.addEventListener('notification', (event) => {
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

    // stream closed for good = token refused, refetch so axios gets the 401 and logs out
    source.onerror = () => {
      if (source.readyState === EventSource.CLOSED) {
        queryClient.invalidateQueries({ queryKey: ['notifications'] })
      }
    }

    return () => source.close()
  }, [userId, queryClient])
}
