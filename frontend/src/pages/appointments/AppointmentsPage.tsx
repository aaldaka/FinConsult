import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarClock } from 'lucide-react'
import { cancelAppointment, confirmAppointment, listMyAppointments, markNoShow } from '@/api/appointments'
import type { AppointmentResponse, AppointmentStatus } from '@/api/types'
import { useAuth } from '@/lib/AuthContext'
import { Card } from '@/components/ui/Card'
import { Select } from '@/components/ui/Select'
import { toneForStatus } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Textarea } from '@/components/ui/Textarea'
import { Modal } from '@/components/ui/Modal'
import { Spinner } from '@/components/ui/Spinner'
import { EmptyState } from '@/components/ui/EmptyState'
import { Alert } from '@/components/ui/Alert'
import { errorMessage } from '@/lib/errors'
import { formatDate, formatTime, initials } from '@/lib/format'
import { APPOINTMENT_STATUSES, labelFor } from '@/lib/options'
import { cn } from '@/lib/cn'

// a status as a pill with a coloured dot, the same look as the audit log's actions
const pillTone: Record<string, string> = {
  pending: 'bg-pending/10 text-amber-700 border-pending/30',
  royal: 'bg-royal/8 text-royal border-royal/20',
  success: 'bg-success/10 text-success border-success/25',
  danger: 'bg-danger/8 text-danger border-danger/20',
  neutral: 'bg-canvas text-muted border-hairline',
}

const dotTone: Record<string, string> = {
  pending: 'bg-pending',
  royal: 'bg-royal',
  success: 'bg-success',
  danger: 'bg-danger',
  neutral: 'bg-muted',
}

function StatusPill({ status }: { status: AppointmentStatus }) {
  const tone = toneForStatus(status)
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border px-2.5 py-0.5 text-xs font-medium',
        pillTone[tone] ?? pillTone.neutral,
      )}
    >
      <span className={cn('size-1.5 rounded-full', dotTone[tone] ?? dotTone.neutral)} aria-hidden="true" />
      {labelFor(status)}
    </span>
  )
}

// the other person on the appointment: the client for an advisor, the advisor for a client
function Person({ appt, isAdvisor }: { appt: AppointmentResponse; isAdvisor: boolean }) {
  const name = isAdvisor ? appt.clientName : appt.advisorName
  const [first, ...rest] = name.split(' ')
  return (
    <div className="flex items-center gap-3">
      <span
        className="flex size-9 shrink-0 items-center justify-center rounded-full bg-royal/10 text-xs font-semibold text-royal"
        aria-hidden="true"
      >
        {initials(first, rest.join(' '))}
      </span>
      <div className="min-w-0">
        <p className="truncate font-medium text-ink">{name}</p>
        <p className="truncate text-xs text-muted">{isAdvisor ? 'Client' : labelFor(appt.advisorSpecialty)}</p>
      </div>
    </div>
  )
}

function noteFor(appt: AppointmentResponse): string | null {
  if (appt.cancellationReason) return `Cancelled: ${appt.cancellationReason}`
  if (appt.excused) return `Excused${appt.excuseReason ? `: ${appt.excuseReason}` : ''}`
  return null
}

export function AppointmentsPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<AppointmentStatus | ''>('')
  const [cancelTarget, setCancelTarget] = useState<AppointmentResponse | null>(null)
  const [cancelReason, setCancelReason] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)

  const { data, isLoading, error } = useQuery({
    queryKey: ['appointments', status],
    queryFn: () => listMyAppointments({ status: status || undefined, sort: 'slot.startTime,desc' }),
  })

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['appointments'] })
  }

  const confirmMutation = useMutation({
    mutationFn: (id: number) => confirmAppointment(id),
    onSuccess: invalidate,
    onError: (err) => setActionError(errorMessage(err)),
  })

  const noShowMutation = useMutation({
    mutationFn: (id: number) => markNoShow(id),
    onSuccess: invalidate,
    onError: (err) => setActionError(errorMessage(err)),
  })

  const cancelMutation = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) => cancelAppointment(id, reason),
    onSuccess: () => {
      invalidate()
      setCancelTarget(null)
      setCancelReason('')
    },
    onError: (err) => setActionError(errorMessage(err)),
  })

  const isAdvisor = user?.role === 'ADVISOR'

  // the buttons an appointment offers the viewer; nothing once it is cancelled, completed or a no-show
  function actionsFor(appt: AppointmentResponse) {
    if (appt.status !== 'PENDING' && appt.status !== 'CONFIRMED') return null
    return (
      <div className="flex flex-wrap justify-end gap-2">
        {isAdvisor && appt.status === 'PENDING' && (
          <Button size="sm" onClick={() => confirmMutation.mutate(appt.id)} loading={confirmMutation.isPending}>
            Confirm
          </Button>
        )}
        {isAdvisor && appt.status === 'CONFIRMED' && (
          <Button size="sm" variant="secondary" onClick={() => noShowMutation.mutate(appt.id)} loading={noShowMutation.isPending}>
            Mark no-show
          </Button>
        )}
        <Button size="sm" variant="danger" onClick={() => setCancelTarget(appt)}>
          Cancel
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4 animate-fade-in-up">
        <div>
          <h1 className="text-2xl font-semibold text-ink">My appointments</h1>
          <p className="text-sm text-muted">{isAdvisor ? 'Confirm, cancel or mark clients as no-show.' : 'Track and manage your bookings.'}</p>
        </div>
        <div className="w-52">
          <Select label="Status" value={status} onChange={(e) => setStatus(e.target.value as AppointmentStatus | '')}>
            <option value="">All statuses</option>
            {APPOINTMENT_STATUSES.map((s) => (
              <option key={s} value={s}>
                {labelFor(s)}
              </option>
            ))}
          </Select>
        </div>
      </div>

      {actionError && <Alert>{actionError}</Alert>}

      <Card className="p-0 animate-fade-in-up">
        <div className="p-5">
          {error && <Alert>{errorMessage(error)}</Alert>}
          {isLoading && <Spinner />}

          {data && data.length === 0 && (
            <EmptyState icon={<CalendarClock className="size-8" />} title="No appointments" hint="Nothing here yet." />
          )}

          {data && data.length > 0 && (
            <>
              {/* wide screens: a table, one row per appointment */}
              <div className="hidden overflow-x-auto rounded-xl border border-hairline md:block">
                <table className="w-full text-left text-sm">
                  <thead className="bg-canvas text-xs font-medium text-muted">
                    <tr>
                      <th className="px-4 py-3 font-medium">Service</th>
                      <th className="px-4 py-3 font-medium">{isAdvisor ? 'Client' : 'Advisor'}</th>
                      <th className="px-4 py-3 font-medium">Date &amp; time</th>
                      <th className="px-4 py-3 font-medium">Status</th>
                      <th className="px-4 py-3 font-medium">Note</th>
                      <th className="px-4 py-3" aria-label="Actions" />
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-hairline">
                    {data.map((appt) => {
                      const note = noteFor(appt)
                      return (
                        <tr key={appt.id} className="align-middle transition-colors duration-150 hover:bg-canvas/70">
                          <td className="px-4 py-3 font-medium text-ink">{appt.serviceName}</td>
                          <td className="max-w-56 px-4 py-3">
                            <Person appt={appt} isAdvisor={isAdvisor} />
                          </td>
                          <td className="tabular whitespace-nowrap px-4 py-3">
                            <p className="text-ink">{formatDate(appt.startTime)}</p>
                            <p className="text-xs text-muted">{formatTime(appt.startTime)}</p>
                          </td>
                          <td className="px-4 py-3">
                            <StatusPill status={appt.status} />
                          </td>
                          <td className="max-w-56 px-4 py-3 text-muted">
                            <p className="line-clamp-2" title={note ?? undefined}>
                              {note ?? '—'}
                            </p>
                          </td>
                          <td className="px-4 py-3">{actionsFor(appt)}</td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>

              {/* small screens: compact rows */}
              <ul className="divide-y divide-hairline md:hidden">
                {data.map((appt) => {
                  const note = noteFor(appt)
                  return (
                    <li key={appt.id} className="space-y-2 py-3 first:pt-0 last:pb-0">
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <p className="font-medium text-ink">{appt.serviceName}</p>
                          <p className="tabular text-xs text-muted">
                            {isAdvisor ? appt.clientName : appt.advisorName} · {formatDate(appt.startTime)}, {formatTime(appt.startTime)}
                          </p>
                        </div>
                        <StatusPill status={appt.status} />
                      </div>
                      {note && <p className="text-xs text-muted">{note}</p>}
                      {actionsFor(appt)}
                    </li>
                  )
                })}
              </ul>

              <p className="tabular mt-4 text-sm text-muted">
                {data.length} {data.length === 1 ? 'appointment' : 'appointments'}, latest first
              </p>
            </>
          )}
        </div>
      </Card>

      {cancelTarget && (
        <Modal>
          <h2 className="mb-1 font-semibold text-ink">Cancel appointment</h2>
          <p className="mb-4 text-sm text-muted">
            {isAdvisor
              ? 'Let the client know why.'
              : 'Cancelling less than 24 hours before the slot adds a strike to your account.'}
          </p>
          <Textarea
            label="Reason (optional)"
            value={cancelReason}
            onChange={(e) => setCancelReason(e.target.value)}
            maxLength={255}
          />
          <div className="mt-4 flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setCancelTarget(null)}>
              Close
            </Button>
            <Button
              variant="danger"
              loading={cancelMutation.isPending}
              onClick={() => cancelMutation.mutate({ id: cancelTarget.id, reason: cancelReason })}
            >
              Confirm cancellation
            </Button>
          </div>
        </Modal>
      )}
    </div>
  )
}
