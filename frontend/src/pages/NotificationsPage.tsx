import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, BriefcaseBusiness, CalendarCheck, CalendarClock, CalendarX, Check, CheckCheck, ShieldAlert, ShieldCheck, UserX } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { listMyNotifications, markAllNotificationsRead, markNotificationRead } from '@/api/notifications'
import type { NotificationResponse, NotificationType } from '@/api/types'
import { Card } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Spinner } from '@/components/ui/Spinner'
import { EmptyState } from '@/components/ui/EmptyState'
import { Alert } from '@/components/ui/Alert'
import { PageHeader } from '@/components/layout/PageHeader'
import { errorMessage } from '@/lib/errors'
import { formatTime } from '@/lib/format'
import { cn } from '@/lib/cn'

const ICONS: Record<NotificationType, LucideIcon> = {
  APPOINTMENT_BOOKED: CalendarClock,
  APPOINTMENT_CONFIRMED: CalendarCheck,
  APPOINTMENT_CANCELLED: CalendarX,
  APPOINTMENT_NO_SHOW: UserX,
  KYC_REVIEWED: ShieldCheck,
  DOCUMENT_FLAGGED: ShieldAlert,
  ADVISOR_APPLICATION_REVIEWED: BriefcaseBusiness,
}

// booking-service ends an advisor's "booked" message with this sentence; it is shown as a badge instead
const PENDING_SUFFIX = ' Pending your confirmation.'

function splitMessage(n: NotificationResponse): { text: string; pending: boolean } {
  return n.message.endsWith(PENDING_SUFFIX)
    ? { text: n.message.slice(0, -PENDING_SUFFIX.length), pending: true }
    : { text: n.message, pending: false }
}

function dayKey(iso: string): string {
  const d = new Date(iso)
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`
}

// "Today", "Yesterday", or the date, for the group headings
function dayLabel(iso: string, now: Date): string {
  const d = new Date(iso)
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (dayKey(iso) === dayKey(now.toISOString())) return 'Today'
  if (dayKey(iso) === dayKey(yesterday.toISOString())) return 'Yesterday'
  return d.toLocaleDateString(undefined, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    ...(d.getFullYear() !== now.getFullYear() && { year: 'numeric' }),
  })
}

// one compact row: icon, message, then the time, any badge and the read button on the right
function NotificationRow({ n, onRead, reading }: { n: NotificationResponse; onRead: () => void; reading: boolean }) {
  const Icon = ICONS[n.type] ?? Bell
  const { text, pending } = splitMessage(n)
  return (
    <li className={cn('flex items-center gap-3 px-4 py-3 transition-colors duration-150', n.read ? 'hover:bg-canvas/60' : 'bg-sky-50/70')}>
      <span
        className={cn(
          'flex size-8 shrink-0 items-center justify-center rounded-full',
          n.read ? 'bg-canvas text-muted' : 'bg-royal/10 text-royal',
        )}
        aria-hidden="true"
      >
        <Icon className="size-4" />
      </span>

      <p className={cn('min-w-0 flex-1 text-sm', n.read ? 'text-muted' : 'font-medium text-ink')}>
        {!n.read && <span className="mr-2 inline-block size-2 rounded-full bg-gold align-middle" aria-label="Unread" />}
        {text}
      </p>

      {pending && (
        <span className="hidden shrink-0 rounded-full border border-gold/40 bg-gold/15 px-2 py-0.5 text-[11px] font-semibold text-gold-deep sm:inline">
          Pending confirmation
        </span>
      )}
      <span className="tabular w-16 shrink-0 text-right text-xs text-muted">{formatTime(n.createdAt)}</span>
      {n.read ? (
        <span className="size-7 shrink-0" aria-hidden="true" />
      ) : (
        <button
          type="button"
          onClick={onRead}
          disabled={reading}
          aria-label="Mark as read"
          title="Mark as read"
          className="flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-full border border-hairline bg-surface text-muted transition-colors duration-200 hover:border-success/40 hover:bg-success/10 hover:text-success disabled:opacity-50"
        >
          <Check className="size-3.5" aria-hidden="true" />
        </button>
      )}
    </li>
  )
}

export function NotificationsPage() {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const unreadOnly = searchParams.get('show') === 'unread'
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

  const [now] = useState(() => new Date()) // read once when the page opens, for "Today" and "Yesterday"

  // newest first, grouped by the day they arrived
  const groups = useMemo(() => {
    const shown = (data ?? [])
      .filter((n) => !unreadOnly || !n.read)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    const result: { label: string; items: NotificationResponse[] }[] = []
    for (const n of shown) {
      const label = dayLabel(n.createdAt, now)
      const last = result[result.length - 1]
      if (last && last.label === label) last.items.push(n)
      else result.push({ label, items: [n] })
    }
    return result
  }, [data, unreadOnly, now])

  return (
    <div className="space-y-6">
      <PageHeader
        title="Notifications"
        description="Updates about your appointments and account."
        actions={
          unreadCount > 0 && (
            <Button variant="gold" size="sm" loading={readAllMutation.isPending} onClick={() => readAllMutation.mutate()}>
              <CheckCheck className="size-4" aria-hidden="true" />
              Mark all read ({unreadCount})
            </Button>
          )
        }
      />

      {data && data.length > 0 && (
        <div role="tablist" aria-label="Show" className="inline-flex gap-1 rounded-full border border-hairline bg-surface p-1 shadow-card animate-fade-in-up">
          {[
            { id: 'all', label: 'All', count: data.length },
            { id: 'unread', label: 'Unread', count: unreadCount },
          ].map(({ id, label, count }) => {
            const active = (id === 'unread') === unreadOnly
            return (
              <button
                key={id}
                type="button"
                role="tab"
                aria-selected={active}
                onClick={() => setSearchParams(id === 'unread' ? { show: 'unread' } : {})}
                className={cn(
                  'inline-flex cursor-pointer items-center gap-2 rounded-full px-4 py-1.5 text-sm font-medium transition-colors duration-200',
                  active ? 'bg-navy text-white' : 'text-muted hover:text-ink',
                )}
              >
                {label}
                <span
                  className={cn(
                    'tabular rounded-full px-1.5 text-xs',
                    active ? 'bg-white/15 text-white' : 'bg-canvas text-muted',
                  )}
                >
                  {count}
                </span>
              </button>
            )
          })}
        </div>
      )}

      {readAllMutation.error && <Alert>{errorMessage(readAllMutation.error)}</Alert>}
      {readMutation.error && <Alert>{errorMessage(readMutation.error)}</Alert>}
      {error && <Alert>{errorMessage(error)}</Alert>}
      {isLoading && <Spinner />}

      {data && groups.length === 0 && (
        <EmptyState
          icon={<Bell className="size-8" />}
          title="You're all caught up"
          hint={unreadOnly && data.length > 0 ? 'No unread notifications.' : undefined}
        />
      )}

      {groups.map((group) => (
        <section key={group.label} className="animate-fade-in-up">
          <h2 className="mb-2 text-xs font-semibold uppercase tracking-[0.2em] text-muted">{group.label}</h2>
          <Card className="overflow-hidden p-0">
            <ul className="stagger divide-y divide-hairline">
              {group.items.map((n) => (
                <NotificationRow
                  key={n.id}
                  n={n}
                  reading={readMutation.isPending && readMutation.variables === n.id}
                  onRead={() => readMutation.mutate(n.id)}
                />
              ))}
            </ul>
          </Card>
        </section>
      ))}
    </div>
  )
}
