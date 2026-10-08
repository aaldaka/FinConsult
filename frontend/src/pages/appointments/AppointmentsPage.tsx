import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarClock } from 'lucide-react'
import { cancelAppointment, confirmAppointment, listMyAppointments, markNoShow } from '@/api/appointments'
import type { AppointmentResponse, AppointmentStatus } from '@/api/types'
import { useAuth } from '@/lib/AuthContext'
import { Card } from '@/components/ui/Card'
import { Select } from '@/components/ui/Select'
import { Badge, toneForStatus } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Textarea } from '@/components/ui/Textarea'
import { Modal } from '@/components/ui/Modal'
import { Spinner } from '@/components/ui/Spinner'
import { EmptyState } from '@/components/ui/EmptyState'
import { Alert } from '@/components/ui/Alert'
import { errorMessage } from '@/lib/errors'
import { formatDateTime } from '@/lib/format'
import { APPOINTMENT_STATUSES, labelFor } from '@/lib/options'

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

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4 animate-fade-in-up">
        <div>
          <h1 className="text-2xl font-semibold text-ink">My appointments</h1>
          <p className="text-sm text-muted">{isAdvisor ? 'Confirm, cancel or mark clients as no-show.' : 'Track and manage your bookings.'}</p>
        </div>
        <div className="w-48">
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
      {error && <Alert>{errorMessage(error)}</Alert>}
      {isLoading && <Spinner />}

      {data && data.length === 0 && (
        <EmptyState icon={<CalendarClock className="size-8" />} title="No appointments" hint="Nothing here yet." />
      )}

      {data && data.length > 0 && (
        <div className="stagger space-y-3">
          {data.map((appt) => {
            const cancellable = appt.status === 'PENDING' || appt.status === 'CONFIRMED'
            return (
              <Card key={appt.id} interactive className="flex flex-wrap items-center justify-between gap-4">
                <div>
                  <div className="flex items-center gap-2">
                    <p className="font-semibold text-ink">{appt.serviceName}</p>
                    <Badge tone={toneForStatus(appt.status)}>{labelFor(appt.status)}</Badge>
                  </div>
                  <p className="text-sm text-muted">
                    {isAdvisor ? `Client: ${appt.clientName}` : `${appt.advisorName} · ${labelFor(appt.advisorSpecialty)}`}
                  </p>
                  <p className="text-sm text-muted">{formatDateTime(appt.startTime)}</p>
                  {appt.cancellationReason && (
                    <p className="mt-1 text-xs text-muted">Cancelled: {appt.cancellationReason}</p>
                  )}
                </div>

                <div className="flex flex-wrap gap-2">
                  {isAdvisor && appt.status === 'PENDING' && (
                    <Button size="sm" onClick={() => confirmMutation.mutate(appt.id)} loading={confirmMutation.isPending}>
                      Confirm
                    </Button>
                  )}
                  {isAdvisor && appt.status === 'CONFIRMED' && (
                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={() => noShowMutation.mutate(appt.id)}
                      loading={noShowMutation.isPending}
                    >
                      Mark no-show
                    </Button>
                  )}
                  {cancellable && (
                    <Button size="sm" variant="danger" onClick={() => setCancelTarget(appt)}>
                      Cancel
                    </Button>
                  )}
                </div>
              </Card>
            )
          })}
        </div>
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
            maxLength={500}
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
