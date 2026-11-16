import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ArrowRight, ChevronLeft, ChevronRight, ScrollText } from 'lucide-react'
import { listAuditLogs } from '@/api/admin'
import type { AuditLogResponse } from '@/api/types'
import { Card } from '@/components/ui/Card'
import { Select } from '@/components/ui/Select'
import { Button } from '@/components/ui/Button'
import { Spinner } from '@/components/ui/Spinner'
import { EmptyState } from '@/components/ui/EmptyState'
import { Alert } from '@/components/ui/Alert'
import { errorMessage } from '@/lib/errors'
import { formatDate, formatTime, initials } from '@/lib/format'
import { AUDIT_ACTIONS, labelFor } from '@/lib/options'
import { cn } from '@/lib/cn'

const PAGE_SIZE = 10

// the colour of an action's pill: what was created, approved, refused or changed
type Tone = 'royal' | 'success' | 'danger' | 'pending' | 'gold' | 'neutral'

const pill: Record<Tone, string> = {
  royal: 'bg-royal/8 text-royal border-royal/20',
  success: 'bg-success/10 text-success border-success/25',
  danger: 'bg-danger/8 text-danger border-danger/20',
  pending: 'bg-pending/10 text-amber-700 border-pending/30',
  gold: 'bg-gold/15 text-gold-deep border-gold/30',
  neutral: 'bg-canvas text-muted border-hairline',
}

const dot: Record<Tone, string> = {
  royal: 'bg-royal',
  success: 'bg-success',
  danger: 'bg-danger',
  pending: 'bg-pending',
  gold: 'bg-gold',
  neutral: 'bg-muted',
}

function toneFor(action: string): Tone {
  if (/APPROVED|CONFIRMED/.test(action)) return 'success'
  if (/CANCELLED|REJECTED|DEACTIVATED|BLACKLISTED|NO_SHOW$/.test(action)) return 'danger'
  if (/EXCUSED/.test(action)) return 'gold'
  if (/UPDATED|REPLACED|RESUBMITTED/.test(action)) return 'pending'
  if (/BOOKED|SUBMITTED|CREATED/.test(action)) return 'royal'
  return 'neutral'
}

function ActionPill({ action }: { action: string }) {
  const tone = toneFor(action)
  return (
    <span className={cn('inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border px-2.5 py-0.5 text-xs font-medium', pill[tone])}>
      <span className={cn('size-1.5 rounded-full', dot[tone])} aria-hidden="true" />
      {labelFor(action)}
    </span>
  )
}

function Actor({ entry }: { entry: AuditLogResponse }) {
  const system = entry.actorEmail === 'SYSTEM'
  const [first, ...rest] = entry.actorName.split(' ')
  return (
    <div className="flex items-center gap-3">
      <span
        className={cn(
          'flex size-9 shrink-0 items-center justify-center rounded-full text-xs font-semibold',
          system ? 'bg-hairline text-muted' : 'bg-royal/10 text-royal',
        )}
        aria-hidden="true"
      >
        {system ? 'SY' : initials(first, rest.join(' '))}
      </span>
      <div className="min-w-0">
        <p className="truncate font-medium text-ink">{entry.actorName}</p>
        {!system && <p className="truncate text-xs text-muted">{entry.actorEmail}</p>}
      </div>
    </div>
  )
}

function NoShowLink({ entry }: { entry: AuditLogResponse }) {
  if (entry.action !== 'APPOINTMENT_NO_SHOW' || entry.entityType !== 'Appointment') return null
  return (
    <Link
      to={`/no-shows?appointment=${entry.entityId}`}
      className="inline-flex items-center gap-1 whitespace-nowrap text-xs font-medium text-royal hover:underline"
    >
      Review
      <ArrowRight className="size-3" aria-hidden="true" />
    </Link>
  )
}

// wide screens: a table, one row per entry
function AuditTable({ entries }: { entries: AuditLogResponse[] }) {
  return (
    <div className="hidden overflow-x-auto rounded-xl border border-hairline md:block">
      <table className="w-full text-left text-sm">
        <thead className="bg-canvas text-xs font-medium text-muted">
          <tr>
            <th className="px-4 py-3 font-medium">User</th>
            <th className="px-4 py-3 font-medium">Action</th>
            <th className="px-4 py-3 font-medium">Date &amp; time</th>
            <th className="px-4 py-3 font-medium">Subject</th>
            <th className="px-4 py-3 font-medium">Details</th>
            <th className="px-4 py-3" aria-label="Links" />
          </tr>
        </thead>
        <tbody className="divide-y divide-hairline">
          {entries.map((entry) => (
            <tr key={entry.id} className="align-middle transition-colors duration-150 hover:bg-canvas/70">
              <td className="max-w-56 px-4 py-3">
                <Actor entry={entry} />
              </td>
              <td className="px-4 py-3">
                <ActionPill action={entry.action} />
              </td>
              <td className="tabular whitespace-nowrap px-4 py-3">
                <p className="text-ink">{formatDate(entry.timestamp)}</p>
                <p className="text-xs text-muted">{formatTime(entry.timestamp)}</p>
              </td>
              <td className="whitespace-nowrap px-4 py-3 text-muted">
                {entry.entityType} <span className="tabular text-ink">#{entry.entityId}</span>
              </td>
              <td className="max-w-72 px-4 py-3 text-muted">
                <p className="line-clamp-2" title={entry.details ?? undefined}>
                  {entry.details || '—'}
                </p>
              </td>
              <td className="px-4 py-3 text-right">
                <NoShowLink entry={entry} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// small screens: a timeline, newest at the top
function AuditTimeline({ entries }: { entries: AuditLogResponse[] }) {
  return (
    <ol className="relative space-y-6 md:hidden">
      <span className="absolute bottom-2 left-[5px] top-2 w-px bg-hairline" aria-hidden="true" />
      {entries.map((entry, i) => (
        <li key={entry.id} className="relative pl-7">
          <span
            className={cn(
              'absolute left-0 top-1.5 size-[11px] rounded-full ring-4 ring-surface',
              i === 0 ? 'bg-ink' : dot[toneFor(entry.action)],
            )}
            aria-hidden="true"
          />
          <div className="flex flex-wrap items-center gap-2">
            <ActionPill action={entry.action} />
            <span className="text-xs text-muted">
              {entry.entityType} #{entry.entityId}
            </span>
          </div>
          {entry.details && <p className="mt-1.5 text-sm text-ink">{entry.details}</p>}
          <p className="mt-1 text-xs text-muted">
            {entry.actorName} · {formatDate(entry.timestamp)}, {formatTime(entry.timestamp)}
          </p>
          <div className="mt-1">
            <NoShowLink entry={entry} />
          </div>
        </li>
      ))}
    </ol>
  )
}

export function AuditLogList() {
  const [page, setPage] = useState(0)
  const [action, setAction] = useState('')

  const { data, isLoading, error } = useQuery({
    queryKey: ['audit-logs', page, action],
    queryFn: () => listAuditLogs({ page, size: PAGE_SIZE, action: action || undefined }),
  })

  return (
    <Card className="p-0">
      <div className="flex flex-wrap items-end justify-between gap-4 border-b border-hairline px-5 py-4">
        <div>
          <h2 className="font-serif text-xl font-semibold text-ink">Audit logs</h2>
          <p className="text-sm text-muted">Every sensitive change, newest first.</p>
        </div>
        <div className="w-60">
          <Select
            label="Action"
            value={action}
            onChange={(e) => {
              setAction(e.target.value)
              setPage(0)
            }}
          >
            <option value="">All actions</option>
            {AUDIT_ACTIONS.map((a) => (
              <option key={a} value={a}>
                {labelFor(a)}
              </option>
            ))}
          </Select>
        </div>
      </div>

      <div className="p-5">
        {error && <Alert>{errorMessage(error)}</Alert>}
        {isLoading && <Spinner />}

        {data && data.content.length === 0 && (
          <EmptyState
            icon={<ScrollText className="size-8" />}
            title={action ? 'No entries for this action' : 'No audit entries yet'}
            hint={action ? 'Try another action or show all.' : undefined}
          />
        )}

        {data && data.content.length > 0 && (
          <>
            <AuditTable entries={data.content} />
            <AuditTimeline entries={data.content} />

            <div className="mt-4 flex items-center justify-between">
              <p className="tabular text-sm text-muted">
                {data.totalElements} {data.totalElements === 1 ? 'entry' : 'entries'} · page {data.number + 1} of{' '}
                {Math.max(data.totalPages, 1)}
              </p>
              <div className="flex gap-2">
                <Button size="sm" variant="secondary" disabled={data.first} onClick={() => setPage((p) => p - 1)}>
                  <ChevronLeft className="size-4" aria-hidden="true" />
                  Previous
                </Button>
                <Button size="sm" variant="secondary" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
                  Next
                  <ChevronRight className="size-4" aria-hidden="true" />
                </Button>
              </div>
            </div>
          </>
        )}
      </div>
    </Card>
  )
}
