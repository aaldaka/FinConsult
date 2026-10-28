import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, CheckCheck } from 'lucide-react'
import { listMyNotifications, markAllNotificationsRead, markNotificationRead } from '@/api/notifications'
import { Card } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Spinner } from '@/components/ui/Spinner'
import { EmptyState } from '@/components/ui/EmptyState'
import { Alert } from '@/components/ui/Alert'
import { errorMessage } from '@/lib/errors'
import { formatDateTime } from '@/lib/format'
import { cn } from '@/lib/cn'

export function NotificationsPage() {
  const queryClient = useQueryClient()
  const { data, isLoading, error } = useQuery({ queryKey: ['notifications', 'all'], queryFn: listMyNotifications })

  const readMutation = useMutation({
    mutationFn: (id: number) => markNotificationRead(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })

  const readAllMutation = useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })

  const unreadCount = data?.filter((n) => !n.read).length ?? 0

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4 animate-fade-in-up">
        <div>
          <h1 className="text-2xl font-semibold text-ink">Notifications</h1>
          <p className="text-sm text-muted">Updates about your appointments and account.</p>
        </div>
        {unreadCount > 0 && (
          <Button variant="secondary" size="sm" loading={readAllMutation.isPending} onClick={() => readAllMutation.mutate()}>
            <CheckCheck className="size-4" aria-hidden="true" />
            Mark all read ({unreadCount})
          </Button>
        )}
      </div>

      {readAllMutation.error && <Alert>{errorMessage(readAllMutation.error)}</Alert>}

      {error && <Alert>{errorMessage(error)}</Alert>}
      {isLoading && <Spinner />}

      {data && data.length === 0 && <EmptyState icon={<Bell className="size-8" />} title="You're all caught up" />}

      {data && data.length > 0 && (
        <div className="stagger space-y-2">
          {data.map((n) => (
            <Card
              key={n.id}
              interactive
              className={cn('flex items-start justify-between gap-4', !n.read && 'border-royal/30 bg-royal/[0.03]')}
            >
              <div>
                <p className={cn('text-sm', !n.read && 'font-medium text-ink')}>{n.message}</p>
                <p className="mt-1 text-xs text-muted">{formatDateTime(n.createdAt)}</p>
              </div>
              {!n.read && (
                <Button size="sm" variant="ghost" onClick={() => readMutation.mutate(n.id)}>
                  Mark read
                </Button>
              )}
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
