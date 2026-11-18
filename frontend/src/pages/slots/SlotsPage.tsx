import { useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarClock, CalendarPlus, ChevronDown, Plus, X } from 'lucide-react'
import { createSlot, deleteSlot, listMySlots } from '@/api/slots'
import type { SlotResponse } from '@/api/types'
import { Card } from '@/components/ui/Card'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Button } from '@/components/ui/Button'
import { Spinner } from '@/components/ui/Spinner'
import { EmptyState } from '@/components/ui/EmptyState'
import { Alert } from '@/components/ui/Alert'
import { PageHeader } from '@/components/layout/PageHeader'
import { errorMessage } from '@/lib/errors'
import { formatDateTime, formatTime } from '@/lib/format'
import { cn } from '@/lib/cn'

// Slot times have no zone (the server stores LocalDateTime) and come as YYYY-MM-DDTHH:mm:ss, so days and times are
// handled as strings in that same form and compare correctly as text.

const pad = (n: number) => String(n).padStart(2, '0')

function dayKey(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function addDays(day: string, days: number): string {
  const [y, m, d] = day.split('-').map(Number)
  return dayKey(new Date(y, m - 1, d + days))
}

function localNow(): string {
  const d = new Date()
  return `${dayKey(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const toMinutes = (time: string) => {
  const [h, m] = time.split(':').map(Number)
  return h * 60 + m
}
const fromMinutes = (minutes: number) => `${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}`

const overlaps = (aStart: number, aEnd: number, bStart: number, bEnd: number) => aStart < bEnd && bStart < aEnd

function dayLabel(day: string, options: Intl.DateTimeFormatOptions): string {
  return new Date(`${day}T00:00:00`).toLocaleDateString(undefined, options)
}

const START_TIMES = Array.from({ length: 12 }, (_, i) => `${pad(8 + i)}:00`) // 08:00 to 19:00
const DURATIONS = [
  { minutes: 30, label: '30 min' },
  { minutes: 45, label: '45 min' },
  { minutes: 60, label: '1 h' },
  { minutes: 90, label: '1.5 h' },
]

interface Planned {
  day: string
  start: string // HH:mm
  end: string // HH:mm
  clash: boolean // overlaps a slot that already exists
}

export function SlotsPage() {
  const queryClient = useQueryClient()
  const formRef = useRef<HTMLDivElement>(null)
  // read once when the page opens: a slot starting before this counts as past
  const [now] = useState(localNow)
  const today = now.slice(0, 10)

  const [day, setDay] = useState(() => addDays(today, 1))
  const [times, setTimes] = useState<string[]>([])
  const [duration, setDuration] = useState(60)
  const [weeks, setWeeks] = useState(1)
  const [customTime, setCustomTime] = useState('')
  const [result, setResult] = useState<{ added: number; failed: string[] } | null>(null)
  const [showPast, setShowPast] = useState(false)

  const { data, isLoading, error } = useQuery({ queryKey: ['slots', 'mine'], queryFn: listMySlots })

  // the existing slots of a day, as minute ranges
  const rangesByDay = useMemo(() => {
    const map = new Map<string, [number, number][]>()
    for (const slot of data ?? []) {
      const d = slot.startTime.slice(0, 10)
      const start = toMinutes(slot.startTime.slice(11, 16))
      const end = slot.endTime.slice(0, 10) === d ? toMinutes(slot.endTime.slice(11, 16)) : 24 * 60
      map.set(d, [...(map.get(d) ?? []), [start, end]])
    }
    return map
  }, [data])

  const clashesWithExisting = (d: string, start: number, end: number) =>
    (rangesByDay.get(d) ?? []).some(([s, e]) => overlaps(start, end, s, e))

  // why a start time cannot be picked on the chosen day, or null when it can
  function blockedReason(time: string): string | null {
    const start = toMinutes(time)
    const end = start + duration
    if (end > 24 * 60) return 'Ends after midnight'
    if (`${day}T${time}:00` < now) return 'In the past'
    if (clashesWithExisting(day, start, end)) return 'You already have a slot then'
    const clashWithPicked = times.some((t) => t !== time && overlaps(start, end, toMinutes(t), toMinutes(t) + duration))
    if (clashWithPicked) return 'Overlaps another time you picked'
    return null
  }

  function toggleTime(time: string) {
    setResult(null)
    setTimes((current) => (current.includes(time) ? current.filter((t) => t !== time) : [...current, time].sort()))
  }

  function addCustomTime() {
    if (!customTime || times.includes(customTime) || blockedReason(customTime)) return
    toggleTime(customTime)
    setCustomTime('')
  }

  // every slot the form would create, the chosen day first and then the same times in the following weeks
  const planned: Planned[] = useMemo(() => {
    const items: Planned[] = []
    for (let w = 0; w < weeks; w++) {
      const d = addDays(day, w * 7)
      for (const time of times) {
        const start = toMinutes(time)
        const end = start + duration
        const clash = (rangesByDay.get(d) ?? []).some(([s, e]) => overlaps(start, end, s, e))
        items.push({ day: d, start: time, end: fromMinutes(end), clash })
      }
    }
    return items
  }, [day, times, duration, weeks, rangesByDay])

  const toCreate = planned.filter((p) => !p.clash)

  // the API creates one slot per call, so they are sent one after the other; a failure is kept and the rest go on
  const createMutation = useMutation({
    mutationFn: async (items: Planned[]) => {
      let added = 0
      const failed: string[] = []
      for (const item of items) {
        try {
          await createSlot({ startingTime: `${item.day}T${item.start}:00`, finishingTime: `${item.day}T${item.end}:00` })
          added++
        } catch (err) {
          failed.push(`${dayLabel(item.day, { day: 'numeric', month: 'short' })} ${formatTime(`${item.day}T${item.start}:00`)}: ${errorMessage(err)}`)
        }
      }
      return { added, failed }
    },
    onSuccess: (outcome) => {
      setResult(outcome)
      setTimes([])
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['slots', 'mine'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteSlot(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['slots', 'mine'] }),
  })
  const deletingId = deleteMutation.isPending ? deleteMutation.variables : null

  function pickDay(d: string) {
    setDay(d < today ? today : d)
    setTimes([])
    setResult(null)
    formRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  const sorted = [...(data ?? [])].sort((a, b) => a.startTime.localeCompare(b.startTime))
  const upcoming = sorted.filter((slot) => slot.startTime >= now)
  const past = sorted.filter((slot) => slot.startTime < now)
  const openCount = upcoming.filter((slot) => slot.status === 'AVAILABLE').length
  const bookedCount = upcoming.filter((slot) => slot.status === 'BOOKED').length
  const nextOpen = upcoming.find((slot) => slot.status === 'AVAILABLE')

  return (
    <div className="space-y-6">
      <PageHeader title="My availability" description="Open new slots for clients to book, or remove ones that aren't booked yet.">
        {data && (
          <div className="flex flex-wrap gap-3 text-sm">
            <span className="rounded-full border border-white/25 bg-white/5 px-3 py-1">
              <span className="tabular font-semibold">{openCount}</span> open
            </span>
            <span className="rounded-full border border-white/25 bg-white/5 px-3 py-1">
              <span className="tabular font-semibold">{bookedCount}</span> booked
            </span>
            {nextOpen && (
              <span className="rounded-full border border-gold/50 px-3 py-1 text-gold">
                Next open: {formatDateTime(nextOpen.startTime)}
              </span>
            )}
          </div>
        )}
      </PageHeader>

      <div ref={formRef} className="scroll-mt-24">
        <Card className="animate-fade-in-up p-6">
          <h2 className="font-serif text-xl font-semibold text-ink">Add slots</h2>
          <p className="mt-1 text-sm text-muted">Pick a day, tap the start times you are free, and choose how long each slot is.</p>

          <div className="mt-5 grid gap-4 sm:grid-cols-[14rem_1fr]">
            <Input
              label="Day"
              type="date"
              required
              min={today}
              value={day}
              onChange={(e) => {
                setDay(e.target.value || today)
                setTimes([])
                setResult(null)
              }}
            />
            <div className="space-y-1.5">
              <p className="text-sm font-medium text-ink">Length of each slot</p>
              <div className="flex flex-wrap gap-2">
                {DURATIONS.map((d) => (
                  <button
                    key={d.minutes}
                    type="button"
                    onClick={() => {
                      setDuration(d.minutes)
                      setTimes([])
                    }}
                    aria-pressed={duration === d.minutes}
                    className={cn(
                      'h-11 cursor-pointer rounded-xl border px-4 text-sm font-medium transition-colors duration-200',
                      duration === d.minutes
                        ? 'border-navy bg-navy text-white'
                        : 'border-hairline bg-surface text-ink hover:border-royal/40',
                    )}
                  >
                    {d.label}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div className="mt-5">
            <p className="mb-2 text-sm font-medium text-ink">
              Start times on {dayLabel(day, { weekday: 'long', day: 'numeric', month: 'long' })}
            </p>
            <div className="flex flex-wrap gap-2">
              {[...new Set([...START_TIMES, ...times])].sort().map((time) => {
                const picked = times.includes(time)
                const reason = picked ? null : blockedReason(time)
                return (
                  <button
                    key={time}
                    type="button"
                    onClick={() => toggleTime(time)}
                    disabled={!!reason}
                    title={reason ?? undefined}
                    aria-pressed={picked}
                    className={cn(
                      'tabular h-9 cursor-pointer rounded-full border px-3.5 text-sm transition-colors duration-200',
                      picked && 'border-royal bg-royal text-white shadow-card',
                      !picked && !reason && 'border-hairline bg-surface text-ink hover:border-royal/50 hover:bg-royal/5',
                      reason && 'cursor-not-allowed border-hairline bg-canvas text-muted/60 line-through',
                    )}
                  >
                    {formatTime(`${day}T${time}:00`)}
                  </button>
                )
              })}
              <div className="flex items-center gap-1">
                <input
                  type="time"
                  value={customTime}
                  onChange={(e) => setCustomTime(e.target.value)}
                  aria-label="Another start time"
                  className="h-9 rounded-full border border-hairline bg-surface px-3 text-sm text-ink focus:border-royal focus:outline-none focus:ring-2 focus:ring-royal/20"
                />
                <button
                  type="button"
                  onClick={addCustomTime}
                  disabled={!customTime || !!blockedReason(customTime)}
                  title={customTime ? (blockedReason(customTime) ?? 'Add this time') : 'Pick a time first'}
                  aria-label="Add this time"
                  className="flex size-9 cursor-pointer items-center justify-center rounded-full border border-hairline bg-surface text-royal transition-colors hover:bg-royal/5 disabled:cursor-not-allowed disabled:opacity-40"
                >
                  <Plus className="size-4" aria-hidden="true" />
                </button>
              </div>
            </div>
          </div>

          <div className="mt-5 grid items-end gap-4 sm:grid-cols-[14rem_1fr_auto]">
            <Select label="Repeat" value={weeks} onChange={(e) => setWeeks(Number(e.target.value))}>
              <option value={1}>Just this day</option>
              <option value={2}>Weekly, for 2 weeks</option>
              <option value={3}>Weekly, for 3 weeks</option>
              <option value={4}>Weekly, for 4 weeks</option>
            </Select>
            <p className="text-sm text-muted" aria-live="polite">
              {times.length === 0
                ? 'No times picked yet.'
                : `Adds ${toCreate.length} ${toCreate.length === 1 ? 'slot' : 'slots'}${
                    planned.length > toCreate.length ? `, ${planned.length - toCreate.length} skipped (already taken)` : ''
                  }.`}
            </p>
            <Button
              type="button"
              disabled={toCreate.length === 0}
              loading={createMutation.isPending}
              onClick={() => {
                setResult(null)
                createMutation.mutate(toCreate)
              }}
            >
              <CalendarPlus className="size-4" aria-hidden="true" />
              {toCreate.length > 1 ? `Add ${toCreate.length} slots` : 'Add slot'}
            </Button>
          </div>

          {planned.length > 0 && (
            <ul className="mt-4 flex flex-wrap gap-2 border-t border-hairline pt-4">
              {planned.map((p) => (
                <li
                  key={`${p.day}-${p.start}`}
                  className={cn(
                    'tabular rounded-lg px-2.5 py-1 text-xs',
                    p.clash ? 'bg-canvas text-muted line-through' : 'bg-royal/8 text-royal',
                  )}
                  title={p.clash ? 'Skipped: you already have a slot then' : undefined}
                >
                  {dayLabel(p.day, { weekday: 'short', day: 'numeric', month: 'short' })} · {formatTime(`${p.day}T${p.start}:00`)} –{' '}
                  {formatTime(`${p.day}T${p.end}:00`)}
                </li>
              ))}
            </ul>
          )}

          {result && (
            <div className="mt-4 space-y-2">
              {result.added > 0 && (
                <Alert variant="success">
                  Added {result.added} {result.added === 1 ? 'slot' : 'slots'}.
                </Alert>
              )}
              {result.failed.length > 0 && (
                <Alert>
                  {result.failed.length === 1 ? 'One slot was not added:' : `${result.failed.length} slots were not added:`}{' '}
                  {result.failed.join('; ')}
                </Alert>
              )}
            </div>
          )}
        </Card>
      </div>

      {error && <Alert>{errorMessage(error)}</Alert>}
      {deleteMutation.error && <Alert>{errorMessage(deleteMutation.error)}</Alert>}
      {isLoading && <Spinner />}

      {data && (
        <Card className="overflow-hidden p-0 animate-fade-in-up">
          <div className="border-b border-hairline px-5 py-4">
            <h2 className="font-serif text-xl font-semibold text-ink">Upcoming</h2>
            <p className="mt-1 text-sm text-muted">Click a day to add more slots to it.</p>
          </div>

          {upcoming.length === 0 ? (
            <div className="p-5">
              <EmptyState icon={<CalendarClock className="size-8" />} title="No upcoming slots" hint="Add your next availability above." />
            </div>
          ) : (
            <ul className="divide-y divide-hairline">
              {groupByDay(upcoming).map(([d, slots]) => (
                <AgendaRow
                  key={d}
                  day={d}
                  slots={slots}
                  today={today}
                  onPickDay={pickDay}
                  onDelete={(id) => deleteMutation.mutate(id)}
                  deletingId={deletingId}
                />
              ))}
            </ul>
          )}

          {past.length > 0 && (
            <div className="border-t border-hairline">
              <button
                type="button"
                onClick={() => setShowPast((v) => !v)}
                className="flex w-full cursor-pointer items-center gap-1.5 px-5 py-3 text-sm font-medium text-royal transition-colors hover:bg-canvas/60"
              >
                <ChevronDown className={cn('size-4 transition-transform duration-200', showPast && 'rotate-180')} aria-hidden="true" />
                {showPast ? 'Hide' : 'Show'} past slots ({past.length})
              </button>
              {showPast && (
                <ul className="divide-y divide-hairline border-t border-hairline opacity-60">
                  {groupByDay(past)
                    .reverse()
                    .map(([d, slots]) => (
                      <AgendaRow key={d} day={d} slots={slots} today={today} />
                    ))}
                </ul>
              )}
            </div>
          )}
        </Card>
      )}
    </div>
  )
}

function groupByDay(slots: SlotResponse[]): [string, SlotResponse[]][] {
  const groups = new Map<string, SlotResponse[]>()
  for (const slot of slots) {
    const d = slot.startTime.slice(0, 10)
    groups.set(d, [...(groups.get(d) ?? []), slot])
  }
  return [...groups.entries()]
}

interface AgendaRowProps {
  day: string
  slots: SlotResponse[]
  today: string
  onPickDay?: (day: string) => void
  onDelete?: (id: number) => void
  deletingId?: number | null
}

// one day on one line: the date on the left, its slots as chips on the right
function AgendaRow({ day, slots, today, onPickDay, onDelete, deletingId }: AgendaRowProps) {
  const tag = day === today ? 'Today' : day === addDays(today, 1) ? 'Tomorrow' : null
  const date = (
    <>
      <span className="block font-semibold text-ink">{dayLabel(day, { weekday: 'short', day: 'numeric', month: 'short' })}</span>
      <span className="block text-xs text-muted">{tag ?? dayLabel(day, { year: 'numeric' })}</span>
    </>
  )

  return (
    <li className="grid items-center gap-3 px-5 py-3 sm:grid-cols-[9rem_1fr]">
      {onPickDay ? (
        <button
          type="button"
          onClick={() => onPickDay(day)}
          title="Add slots on this day"
          className="w-fit cursor-pointer rounded-lg text-left text-sm transition-colors hover:text-royal"
        >
          {date}
        </button>
      ) : (
        <div className="text-sm">{date}</div>
      )}

      <ul className="flex flex-wrap gap-2">
        {slots.map((slot) => {
          const open = slot.status === 'AVAILABLE'
          const time = `${formatTime(slot.startTime)} – ${formatTime(slot.endTime)}`
          return (
            <li
              key={slot.id}
              className={cn(
                'tabular inline-flex h-8 items-center gap-2 rounded-full border pl-3 text-sm',
                open ? 'border-dashed border-success/50 bg-surface text-ink' : 'border-pending/30 bg-pending/10 pr-3 text-amber-800',
                open && !onDelete && 'pr-3',
              )}
            >
              <span className={cn('size-1.5 rounded-full', open ? 'bg-success' : 'bg-pending')} aria-hidden="true" />
              {time}
              <span className="text-xs text-muted">{open ? 'Open' : 'Booked'}</span>
              {open && onDelete && (
                <button
                  type="button"
                  onClick={() => onDelete(slot.id)}
                  disabled={deletingId === slot.id}
                  aria-label={`Delete the ${time} slot`}
                  title="Delete slot"
                  className="mr-1 flex size-6 cursor-pointer items-center justify-center rounded-full text-muted transition-colors hover:bg-danger/10 hover:text-danger disabled:opacity-40"
                >
                  <X className="size-3.5" aria-hidden="true" />
                </button>
              )}
            </li>
          )
        })}
      </ul>
    </li>
  )
}
