import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarClock, Trash2 } from 'lucide-react'
import { createSlot, deleteSlot, listMySlots } from '@/api/slots'
import { Card } from '@/components/ui/Card'
import { Input } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Badge, toneForStatus } from '@/components/ui/Badge'
import { Spinner } from '@/components/ui/Spinner'
import { EmptyState } from '@/components/ui/EmptyState'
import { Alert } from '@/components/ui/Alert'
import { errorMessage } from '@/lib/errors'
import { formatDateTime } from '@/lib/format'

interface FormValues {
  startingTime: string
  finishingTime: string
}

export function SlotsPage() {
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isLoading, error } = useQuery({ queryKey: ['slots', 'mine'], queryFn: listMySlots })

  const { register, handleSubmit, reset } = useForm<FormValues>()

  const createMutation = useMutation({
    mutationFn: (values: FormValues) =>
        createSlot({
          startingTime: toLocalDateTime(values.startingTime),
          finishingTime: toLocalDateTime(values.finishingTime),
        }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['slots', 'mine'] })
      reset()
      setFormError(null)
    },
    onError: (err) => setFormError(errorMessage(err)),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteSlot(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['slots', 'mine'] }),
    onError: (err) => setFormError(errorMessage(err)),
  })

  function onSubmit(values: FormValues) {
    setFormError(null)
    createMutation.mutate(values)
  }

  const sorted = [...(data ?? [])].sort((a, b) => a.startTime.localeCompare(b.startTime))

  return (
    <div className="space-y-6">
      <div className="animate-fade-in-up">
        <h1 className="text-2xl font-semibold text-ink">My availability</h1>
        <p className="text-sm text-muted">Open new slots for clients to book, or remove ones that aren't booked yet.</p>
      </div>

      <Card className="animate-fade-in-up">
        <h2 className="mb-4 font-semibold text-ink">New slot</h2>
        {formError && (
          <div className="mb-4">
            <Alert>{formError}</Alert>
          </div>
        )}
        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-wrap items-end gap-3">
          <div className="min-w-[220px]">
            <Input label="Start" type="datetime-local" required {...register('startingTime')} />
          </div>
          <div className="min-w-[220px]">
            <Input label="End" type="datetime-local" required {...register('finishingTime')} />
          </div>
          <Button type="submit" loading={createMutation.isPending}>
            Add slot
          </Button>
        </form>
      </Card>

      {error && <Alert>{errorMessage(error)}</Alert>}
      {isLoading && <Spinner />}

      {sorted.length === 0 && !isLoading && (
        <EmptyState icon={<CalendarClock className="size-8" />} title="No slots yet" hint="Add your first availability slot above." />
      )}

      {sorted.length > 0 && (
        <div className="stagger space-y-2">
          {sorted.map((slot) => (
            <Card key={slot.id} interactive className="flex items-center justify-between gap-4">
              <div className="flex items-center gap-3">
                <Badge tone={toneForStatus(slot.status)}>{slot.status}</Badge>
                <span className="text-sm text-ink">
                  {formatDateTime(slot.startTime)} → {formatDateTime(slot.endTime)}
                </span>
              </div>
              {slot.status === 'AVAILABLE' && (
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => deleteMutation.mutate(slot.id)}
                  loading={deleteMutation.isPending}
                  aria-label="Delete slot"
                >
                  <Trash2 className="size-4" aria-hidden="true" />
                </Button>
              )}
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
