import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarClock, CalendarDays, List } from 'lucide-react'
import { cancelAppointment, confirmAppointment, listMyAppointments, markNoShow } from '@/api/appointments'
import { listMySlots } from '@/api/slots'
import type { AppointmentResponse, AppointmentStatus } from '@/api/types'
import { useAuth } from '@/lib/AuthContext'
import { Card } from '@/components/ui/Card'
import { Select } from '@/components/ui/Select'
import { StatusPill } from '@/components/ui/StatusPill'
import { Button } from '@/components/ui/Button'
import { Textarea } from '@/components/ui/Textarea'
import { Modal } from '@/components/ui/Modal'
import { Spinner } from '@/components/ui/Spinner'
import { EmptyState } from '@/components/ui/EmptyState'
import { Alert } from '@/components/ui/Alert'
import { PageHeader } from '@/components/layout/PageHeader'
import { errorMessage } from '@/lib/errors'
import { formatDate, formatTime, initials } from '@/lib/format'
import { APPOINTMENT_STATUSES, labelFor } from '@/lib/options'
import { cn } from '@/lib/cn'
import { ScheduleView } from '@/pages/appointments/ScheduleView'

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
  const [searchParams, setSearchParams] = useSearchParams()

  const isAdvisor = user?.role === 'ADVISOR'
  // the calendar always shows every status, so the filter only applies to the list
  const schedule = searchParams.get('view') === 'schedule'
  const listStatus = schedule ? '' : status

  const { data, isLoading, error } = useQuery({
    queryKey: ['appointments', listStatus],
    queryFn: () => listMyAppointments({ status: listStatus || undefined, sort: 'slot.startTime,desc' }),
  })

  // only advisors have slots; a client's calendar shows just their bookings
  const slots = useQuery({ queryKey: ['slots', 'mine'], queryFn: listMySlots, enabled: schedule && isAdvisor })

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['appointments'] })
    queryClient.invalidateQueries({ queryKey: ['slots', 'mine'] }) // a cancelled booking can free its slot
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
      <PageHeader
        title="My appointments"
        description={isAdvisor ? 'Confirm, cancel or mark clients as no-show.' : 'Track and manage your bookings.'}
      />

      <div className="flex flex-wrap items-end justify-between gap-3 animate-fade-in-up">
        <div role="tablist" aria-label="View" className="inline-flex gap-1 rounded-full border border-hairline bg-surface p-1 shadow-card">
          {[
            { id: 'list', label: 'List', icon: List },
            { id: 'schedule', label: 'Schedule', icon: CalendarDays },
          ].map(({ id, label, icon: Icon }) => {
            const active = (id === 'schedule') === schedule
            return (
              <button
                key={id}
                type="button"
                role="tab"
                aria-selected={active}
                onClick={() => setSearchParams(id === 'schedule' ? { view: 'schedule' } : {})}
                className={cn(
                  'inline-flex cursor-pointer items-center gap-1.5 rounded-full px-4 py-1.5 text-sm font-medium transition-colors duration-200',
                  active ? 'bg-navy text-white' : 'text-muted hover:text-ink',
                )}
              >
                <Icon className="size-4" aria-hidden="true" />
                {label}
              </button>
            )
          })}
        </div>

        {!schedule && (
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
        )}
      </div>

      {actionError && <Alert>{actionError}</Alert>}

      {schedule && (
        <div className="animate-fade-in-up">
          {(error || slots.error) && <Alert>{errorMessage(error ?? slots.error)}</Alert>}
          {(isLoading || slots.isLoading) && <Spinner />}
          {data && (!isAdvisor || slots.data) && (
            <ScheduleView
              appointments={data}
              slots={slots.data ?? []}
              isAdvisor={isAdvisor}
              renderStatus={(s) => <StatusPill status={s} />}
              renderActions={actionsFor}
            />
          )}
        </div>
      )}

      {!schedule && (
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
      )}

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
