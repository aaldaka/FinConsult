import { useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { CalendarClock, Check, CheckCheck, ChevronLeft, ChevronRight, X } from 'lucide-react'
import type { AppointmentResponse, AppointmentStatus, SlotResponse } from '@/api/types'
import { Card } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { cn } from '@/lib/cn'
import { formatTime } from '@/lib/format'

interface ScheduleViewProps {
  appointments: AppointmentResponse[]
  slots: SlotResponse[]
  renderStatus: (status: AppointmentStatus) => ReactNode
  renderActions: (appt: AppointmentResponse) => ReactNode
  /** advisors see their open slots and the client's name; clients see the advisor's name */
  isAdvisor: boolean
}

// the times have no zone (the server stores LocalDateTime), so the day is the first ten characters, YYYY-MM-DD
const dayOf = (iso: string) => iso.slice(0, 10)

function keyOf(date: Date): string {
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${m}-${d}`
}

// the mark a day shows: the most urgent status of its appointments wins, pending first
const PRIORITY: AppointmentStatus[] = ['PENDING', 'CONFIRMED', 'COMPLETED', 'NO_SHOW', 'CANCELLED']

const MARKS: Record<AppointmentStatus, { label: string; className: string; icon: ReactNode }> = {
  PENDING: { label: 'Pending', className: 'bg-pending text-white', icon: <span className="text-[11px] font-bold leading-none">!</span> },
  // the same colours as the status pills: confirmed is blue, completed is green
  CONFIRMED: { label: 'Confirmed', className: 'bg-royal text-white', icon: <Check className="size-3" strokeWidth={3} /> },
  COMPLETED: { label: 'Completed', className: 'bg-success text-white', icon: <CheckCheck className="size-3" strokeWidth={2.5} /> },
  NO_SHOW: { label: 'No-show', className: 'bg-danger text-white', icon: <X className="size-3" strokeWidth={3} /> },
  CANCELLED: { label: 'Cancelled', className: 'bg-hairline text-muted', icon: <X className="size-3" strokeWidth={3} /> },
}

function Mark({ status, className }: { status: AppointmentStatus; className?: string }) {
  const mark = MARKS[status]
  return (
    <span
      className={cn('flex size-5 shrink-0 items-center justify-center rounded-full shadow-sm', mark.className, className)}
      aria-hidden="true"
    >
      {mark.icon}
    </span>
  )
}

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

export function ScheduleView({ appointments, slots, renderStatus, renderActions, isAdvisor }: ScheduleViewProps) {
  const [today] = useState(() => keyOf(new Date())) // read once when the view opens
  const [month, setMonth] = useState(() => {
    const now = new Date()
    return new Date(now.getFullYear(), now.getMonth(), 1)
  })
  const [selected, setSelected] = useState(today)

  const byDay = useMemo(() => {
    const map = new Map<string, AppointmentResponse[]>()
    for (const appt of appointments) {
      const list = map.get(dayOf(appt.startTime)) ?? []
      list.push(appt)
      map.set(dayOf(appt.startTime), list)
    }
    for (const list of map.values()) list.sort((a, b) => a.startTime.localeCompare(b.startTime))
    return map
  }, [appointments])

  // open slots only: a booked slot already shows as its appointment
  const openByDay = useMemo(() => {
    const map = new Map<string, SlotResponse[]>()
    for (const slot of slots) {
      if (slot.status !== 'AVAILABLE') continue
      const list = map.get(dayOf(slot.startTime)) ?? []
      list.push(slot)
      map.set(dayOf(slot.startTime), list)
    }
    for (const list of map.values()) list.sort((a, b) => a.startTime.localeCompare(b.startTime))
    return map
  }, [slots])

  // six weeks from the Monday on or before the 1st, so every month fits
  const cells = useMemo(() => {
    const start = new Date(month)
    start.setDate(1 - ((month.getDay() + 6) % 7))
    return Array.from({ length: 42 }, (_, i) => {
      const date = new Date(start)
      date.setDate(start.getDate() + i)
      return date
    })
  }, [month])

  function shiftMonth(delta: number) {
    setMonth((m) => new Date(m.getFullYear(), m.getMonth() + delta, 1))
  }

  function goToday() {
    const now = new Date()
    setMonth(new Date(now.getFullYear(), now.getMonth(), 1))
    setSelected(today)
  }

  const dayAppointments = byDay.get(selected) ?? []
  const dayOpen = openByDay.get(selected) ?? []
  const selectedLabel = new Date(`${selected}T00:00:00`).toLocaleDateString(undefined, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  })

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
      <Card className="p-4 sm:p-6">
        <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
          <h2 className="font-serif text-xl font-semibold text-ink">
            {month.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })}
          </h2>
          <div className="flex items-center gap-1">
            <Button size="sm" variant="secondary" onClick={goToday}>
              Today
            </Button>
            <button
              type="button"
              onClick={() => shiftMonth(-1)}
              aria-label="Previous month"
              className="flex size-9 cursor-pointer items-center justify-center rounded-lg text-muted transition-colors hover:bg-canvas hover:text-ink"
            >
              <ChevronLeft className="size-5" aria-hidden="true" />
            </button>
            <button
              type="button"
              onClick={() => shiftMonth(1)}
              aria-label="Next month"
              className="flex size-9 cursor-pointer items-center justify-center rounded-lg text-muted transition-colors hover:bg-canvas hover:text-ink"
            >
              <ChevronRight className="size-5" aria-hidden="true" />
            </button>
          </div>
        </div>

        <div className="grid grid-cols-7 gap-1 sm:gap-2" role="grid" aria-label="Month">
          {WEEKDAYS.map((d) => (
            <div key={d} className="pb-1 text-center text-xs font-semibold uppercase tracking-wider text-muted">
              {d}
            </div>
          ))}

          {cells.map((date) => {
            const key = keyOf(date)
            const inMonth = date.getMonth() === month.getMonth()
            const appts = byDay.get(key) ?? []
            const open = openByDay.get(key)?.length ?? 0
            const top = PRIORITY.find((s) => appts.some((a) => a.status === s))
            const isToday = key === today
            const isSelected = key === selected
            const isPast = key < today

            return (
              <button
                key={key}
                type="button"
                role="gridcell"
                aria-selected={isSelected}
                aria-label={`${date.toDateString()}: ${appts.length} appointments, ${open} open slots`}
                onClick={() => setSelected(key)}
                className={cn(
                  'group relative flex aspect-square cursor-pointer flex-col rounded-xl border p-1.5 text-left transition-all duration-200 sm:aspect-auto sm:min-h-24 sm:p-2',
                  inMonth ? 'border-hairline bg-surface' : 'border-transparent bg-transparent opacity-45',
                  isSelected ? 'border-royal ring-2 ring-royal/25' : 'hover:border-royal/40 hover:shadow-card',
                  open > 0 && !isSelected && 'border-dashed border-royal/40',
                )}
              >
                <span
                  className={cn(
                    'flex size-7 items-center justify-center rounded-full text-sm tabular',
                    isToday ? 'bg-navy font-semibold text-white ring-2 ring-gold' : isPast ? 'text-muted' : 'text-ink',
                  )}
                >
                  {date.getDate()}
                </span>

                <span className="mt-auto flex items-center gap-1">
                  {top && <Mark status={top} />}
                  {appts.length > 1 && <span className="hidden text-xs font-medium text-muted sm:inline">{appts.length}</span>}
                </span>
                {open > 0 && (
                  <span className="absolute right-1.5 top-1.5 hidden rounded-full bg-royal/8 px-1.5 text-[10px] font-medium text-royal sm:inline">
                    {open} open
                  </span>
                )}
              </button>
            )
          })}
        </div>

        <div className="mt-5 flex flex-wrap items-center gap-x-5 gap-y-2 border-t border-hairline pt-4 text-xs text-muted">
          {PRIORITY.map((s) => (
            <span key={s} className="inline-flex items-center gap-1.5">
              <Mark status={s} />
              {MARKS[s].label}
            </span>
          ))}
          {isAdvisor && (
            <span className="inline-flex items-center gap-1.5">
              <span className="size-5 rounded-md border border-dashed border-royal/50" aria-hidden="true" />
              Open slot
            </span>
          )}
        </div>
      </Card>

      <Card className="p-5 lg:sticky lg:top-24 lg:self-start">
        <p className="text-xs font-semibold uppercase tracking-[0.2em] text-gold-deep">{selected === today ? 'Today' : 'Selected day'}</p>
        <h3 className="mt-1 font-serif text-lg font-semibold text-ink">{selectedLabel}</h3>

        {dayAppointments.length === 0 && dayOpen.length === 0 && (
          <div className="mt-4">
            <EmptyState icon={<CalendarClock className="size-8" />} title="Nothing on this day" hint="Pick a marked day to see its appointments." />
          </div>
        )}

        {dayAppointments.length > 0 && (
          <ul className="stagger mt-4 space-y-3">
            {dayAppointments.map((appt) => (
              <li key={appt.id} className="rounded-xl border border-hairline p-3">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="tabular text-sm font-semibold text-ink">
                      {formatTime(appt.startTime)} – {formatTime(appt.endTime)}
                    </p>
                    <p className="truncate text-sm text-ink">{appt.serviceName}</p>
                    <p className="truncate text-xs text-muted">{isAdvisor ? appt.clientName : `with ${appt.advisorName}`}</p>
                  </div>
                  {renderStatus(appt.status)}
                </div>
                {appt.cancellationReason && <p className="mt-2 text-xs text-muted">Cancelled: {appt.cancellationReason}</p>}
                <div className="mt-3 empty:hidden">{renderActions(appt)}</div>
              </li>
            ))}
          </ul>
        )}

        {dayOpen.length > 0 && (
          <div className="mt-4">
            <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted">Open slots</p>
            <ul className="flex flex-wrap gap-2">
              {dayOpen.map((slot) => (
                <li key={slot.id} className="tabular rounded-lg border border-dashed border-royal/40 px-2.5 py-1 text-xs text-royal">
                  {formatTime(slot.startTime)} – {formatTime(slot.endTime)}
                </li>
              ))}
            </ul>
          </div>
        )}
      </Card>
    </div>
  )
}
