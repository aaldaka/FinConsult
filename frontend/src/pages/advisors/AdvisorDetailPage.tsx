import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, CalendarClock } from 'lucide-react'
import { getAdvisor } from '@/api/advisors'
import { listAdvisorSlots } from '@/api/slots'
import { listServiceTypes } from '@/api/serviceTypes'
import { bookSlot } from '@/api/appointments'
import { Card } from '@/components/ui/Card'
import { Select } from '@/components/ui/Select'
import { Button } from '@/components/ui/Button'
import { Spinner } from '@/components/ui/Spinner'
import { EmptyState } from '@/components/ui/EmptyState'
import { Alert } from '@/components/ui/Alert'
import { errorMessage, isConflict } from '@/lib/errors'
import { formatTime } from '@/lib/format'
import { labelFor } from '@/lib/options'
import type { SlotResponse } from '@/api/types'

const dayHeader = new Intl.DateTimeFormat(undefined, { weekday: 'long', month: 'short', day: 'numeric' })

// "Sunday, Nov 22"
function formatDay(iso: string): string {
  return dayHeader.format(new Date(iso))
}

// the slots in time order, grouped by the day they start on (in the viewer's time zone)
function slotsByDay(slots: SlotResponse[]): { day: string; slots: SlotResponse[] }[] {
  const days = new Map<string, SlotResponse[]>()
  for (const slot of [...slots].sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime())) {
    const start = new Date(slot.startTime)
    const day = `${start.getFullYear()}-${start.getMonth()}-${start.getDate()}`
    days.set(day, [...(days.get(day) ?? []), slot])
  }
  return [...days].map(([day, daySlots]) => ({ day, slots: daySlots }))
}

export function AdvisorDetailPage() {
  const { id } = useParams<{ id: string }>()
  const advisorId = Number(id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [selectedSlot, setSelectedSlot] = useState<number | null>(null)
  const [serviceTypeId, setServiceTypeId] = useState<number | ''>('')
  const [bookError, setBookError] = useState<string | null>(null)
  const [now] = useState(() => Date.now()) // when the page opened: slots that start before this are hidden

  const advisorQuery = useQuery({ queryKey: ['advisor', advisorId], queryFn: () => getAdvisor(advisorId) })
  const slotsQuery = useQuery({ queryKey: ['slots', 'advisor', advisorId], queryFn: () => listAdvisorSlots(advisorId) })
  const serviceTypesQuery = useQuery({ queryKey: ['service-types'], queryFn: listServiceTypes })

  const bookMutation = useMutation({
    mutationFn: () => bookSlot(selectedSlot!, Number(serviceTypeId)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['slots', 'advisor', advisorId] })
      queryClient.invalidateQueries({ queryKey: ['appointments'] })
      navigate('/appointments')
    },
    onError: (err) => {
      setSelectedSlot(null)
      // 409 is also used for kyc/strikes errors, only replace the race ones
      const message = errorMessage(err)
      const lostRace =
        message === 'This slot is no longer available' ||
        message === 'This record was changed by someone else. Please refresh and try again' ||
        message === 'This conflicts with an existing record'
      setBookError(isConflict(err) && lostRace ? 'This slot was just booked by someone else. Please pick another time.' : message)
    },
  })

  function handleBook() {
    setBookError(null)
    if (!selectedSlot || !serviceTypeId) {
      setBookError('Pick a slot and a service to continue.')
      return
    }
    bookMutation.mutate()
  }

  if (advisorQuery.isLoading) return <Spinner />
  if (advisorQuery.error) return <Alert>{errorMessage(advisorQuery.error)}</Alert>
  const advisor = advisorQuery.data
  if (!advisor) return null

  // past slots cannot be booked, so they are not shown at all
  const availableSlots = (slotsQuery.data ?? []).filter((s) => s.status === 'AVAILABLE' && new Date(s.startTime).getTime() > now)
  const bookableServices = (serviceTypesQuery.data ?? []).filter(
    (st) => advisor.specialty === 'GENERAL' || st.requiredSpecialty === null || st.requiredSpecialty === advisor.specialty,
  )

  return (
    <div className="stagger space-y-6">
      <Link
        to="/advisors"
        className="inline-flex items-center gap-1.5 text-sm text-royal transition-colors duration-200 hover:text-royal/80 hover:underline"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        Back to advisors
      </Link>

      <Card>
        <h1 className="text-xl font-semibold text-ink">{advisor.name}</h1>
        <p className="text-sm text-muted">{advisor.title}</p>
        <div className="mt-3 flex flex-wrap gap-4 text-sm">
          <span className="font-medium text-royal">{labelFor(advisor.specialty)}</span>
          <span className="text-muted">{advisor.yearsOfExperience} years experience</span>
        </div>
      </Card>

      <Card>
        <h2 className="mb-4 flex items-center gap-2 font-semibold text-ink">
          <CalendarClock className="size-5 text-royal" aria-hidden="true" />
          Book a slot
        </h2>

        {bookError && (
          <div className="mb-4">
            <Alert>{bookError}</Alert>
          </div>
        )}

        {slotsQuery.isLoading && <Spinner />}

        {!slotsQuery.isLoading && availableSlots.length === 0 && (
          <EmptyState title="No available slots" hint="This advisor has no open slots right now. Check back later." />
        )}

        {availableSlots.length > 0 && (
          <div className="space-y-4">
            <div className="stagger space-y-5">
              {slotsByDay(availableSlots).map(({ day, slots }) => (
                <section key={day}>
                  <h3 className="mb-2 text-sm font-semibold text-ink">{formatDay(slots[0].startTime)}</h3>
                  <div className="flex flex-wrap gap-2">
                    {slots.map((slot) => (
                      <button
                        key={slot.id}
                        type="button"
                        onClick={() => setSelectedSlot(slot.id)}
                        className={`tabular min-w-24 cursor-pointer rounded-xl border px-3 py-2 text-center text-sm transition-all duration-200 active:scale-[0.98] ${
                          selectedSlot === slot.id
                            ? 'border-royal bg-royal/5 text-royal shadow-card'
                            : 'border-hairline bg-surface text-ink hover:border-royal/40 hover:bg-royal/[0.02]'
                        }`}
                      >
                        {formatTime(slot.startTime)}
                      </button>
                    ))}
                  </div>
                </section>
              ))}
            </div>

            {bookableServices.length === 0 ? (
              <Alert>This advisor has no bookable services.</Alert>
            ) : (
              <Select
                label="Service"
                value={serviceTypeId}
                onChange={(e) => setServiceTypeId(e.target.value ? Number(e.target.value) : '')}
              >
                <option value="">Select a service…</option>
                {bookableServices.map((st) => (
                  <option key={st.id} value={st.id}>
                    {st.name}
                    {st.requiresKyc ? ' (KYC required)' : ''}
                  </option>
                ))}
              </Select>
            )}

            <Button
              onClick={handleBook}
              loading={bookMutation.isPending}
              disabled={!selectedSlot || !serviceTypeId || bookableServices.length === 0}
            >
              Book appointment
            </Button>
          </div>
        )}
      </Card>
    </div>
  )
}
