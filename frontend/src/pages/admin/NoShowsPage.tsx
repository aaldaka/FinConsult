import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { UserX } from 'lucide-react'
import { excuseNoShow, listNoShows } from '@/api/appointments'
import type { AppointmentResponse } from '@/api/types'
import { Card } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Textarea } from '@/components/ui/Textarea'
import { Modal } from '@/components/ui/Modal'
import { Spinner } from '@/components/ui/Spinner'
import { EmptyState } from '@/components/ui/EmptyState'
import { Alert } from '@/components/ui/Alert'
import { PageHeader } from '@/components/layout/PageHeader'
import { cn } from '@/lib/cn'
import { errorMessage } from '@/lib/errors'
import { formatDateTime } from '@/lib/format'
import { labelFor } from '@/lib/options'

export function NoShowsPage() {
  return (
    <div className="space-y-6">
      <PageHeader title="No-shows" description="Excusing a no-show removes the strike it added to the client." />
      <NoShowsPanel />
    </div>
  )
}

// the list and the excuse dialog, also shown on the No-shows tab of the admin console
export function NoShowsPanel() {
  const queryClient = useQueryClient()
  // ?appointment=ID comes from the audit log link and highlights that no-show
  const [searchParams] = useSearchParams()
  const highlightId = Number(searchParams.get('appointment')) || null
  const [target, setTarget] = useState<AppointmentResponse | null>(null)
  const [reason, setReason] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)

  const { data, isLoading, error } = useQuery({ queryKey: ['appointments', 'no-shows'], queryFn: listNoShows })

  const excuseMutation = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) => excuseNoShow(id, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['appointments', 'no-shows'] })
      setTarget(null)
      setReason('')
      setActionError(null)
    },
    onError: (err) => {
      setTarget(null)
      setActionError(errorMessage(err))
    },
  })

  return (
    <div className="space-y-6">
      {actionError && <Alert>{actionError}</Alert>}
      {error && <Alert>{errorMessage(error)}</Alert>}
      {isLoading && <Spinner />}

      {data && data.length === 0 && (
        <EmptyState icon={<UserX className="size-8" />} title="No no-shows" hint="Clients marked as no-show appear here." />
      )}

      {data && data.length > 0 && (
        <div className="stagger space-y-3">
          {data.map((appt) => (
            <Card
              key={appt.id}
              interactive
              className={cn(
                'flex flex-wrap items-center justify-between gap-4',
                appt.id === highlightId && 'border-gold ring-2 ring-gold/40',
              )}
            >
              <div>
                <div className="flex items-center gap-2">
                  <p className="font-semibold text-ink">{appt.clientName}</p>
                  {appt.excused ? <Badge tone="success">Excused</Badge> : <Badge tone="danger">No-show</Badge>}
                </div>
                <p className="text-sm text-muted">
                  {appt.serviceName} with {appt.advisorName} · {labelFor(appt.advisorSpecialty)}
                </p>
                <p className="text-sm text-muted">{formatDateTime(appt.startTime)}</p>
                {appt.excused && appt.excuseReason && <p className="mt-1 text-xs text-muted">Excused: {appt.excuseReason}</p>}
              </div>

              {!appt.excused && (
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() => {
                    setActionError(null)
                    setTarget(appt)
                  }}
                >
                  Excuse
                </Button>
              )}
            </Card>
          ))}
        </div>
      )}

      {target && (
        <Modal>
          <h2 className="mb-1 font-semibold text-ink">Excuse no-show</h2>
          <p className="mb-4 text-sm text-muted">
            {target.clientName}, {formatDateTime(target.startTime)}. One strike is removed and the client is told the reason.
          </p>
          <Textarea label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255} required />
          <div className="mt-4 flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setTarget(null)}>
              Cancel
            </Button>
            <Button
              loading={excuseMutation.isPending}
              disabled={!reason.trim()}
              onClick={() => excuseMutation.mutate({ id: target.id, reason })}
            >
              Excuse no-show
            </Button>
          </div>
        </Modal>
      )}
    </div>
  )
}
