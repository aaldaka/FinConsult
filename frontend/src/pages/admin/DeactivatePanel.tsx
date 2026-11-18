import { useRef, useState } from 'react'
import type { FocusEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft } from 'lucide-react'
import { deactivateAdvisor, deactivateUser, listActiveClients } from '@/api/admin'
import { listAdvisors } from '@/api/advisors'
import { Button } from '@/components/ui/Button'
import { Textarea } from '@/components/ui/Textarea'
import { Alert } from '@/components/ui/Alert'
import { Modal } from '@/components/ui/Modal'
import { Combobox } from '@/components/ui/Combobox'
import type { ComboboxOption } from '@/components/ui/Combobox'
import { cn } from '@/lib/cn'
import { errorMessage } from '@/lib/errors'
import { labelFor } from '@/lib/options'
import advisorIcon from '@/assets/advisor-icon.png'
import clientIcon from '@/assets/client-icon.png'

type Kind = 'client' | 'advisor'

function PersonArt({ kind }: { kind: Kind }) {
  return <img src={kind === 'advisor' ? advisorIcon : clientIcon} alt="" className="size-32 object-contain" draggable={false} />
}

interface FlipCardProps {
  kind: Kind
  options: ComboboxOption[]
  loading: boolean
  needsReason: boolean
  pending: boolean
  onConfirm: (id: number, reason: string) => Promise<string>
}

function FlipCard({ kind, options, loading, needsReason, pending, onConfirm }: FlipCardProps) {
  const [hovered, setHovered] = useState(false)
  const [focused, setFocused] = useState(false)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [reason, setReason] = useState('')
  const [confirming, setConfirming] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const pickerRef = useRef<HTMLInputElement>(null)

  const title = kind === 'client' ? 'Client' : 'Advisor'
  const selected = options.find((o) => o.id === selectedId)
  const dirty = selectedId !== null || reason !== '' || message !== null || error !== null
  // stays open while the admin is typing or has picked someone, so moving the mouse away doesnt wipe the form
  const open = hovered || focused || dirty || confirming

  function onBlur(e: FocusEvent<HTMLDivElement>) {
    if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setFocused(false)
  }

  function reset() {
    setSelectedId(null)
    setReason('')
    setMessage(null)
    setError(null)
    setHovered(false)
  }

  async function confirm() {
    if (selectedId === null) return
    try {
      const msg = await onConfirm(selectedId, reason.trim())
      setMessage(`${selected?.label ?? 'Account'}: ${msg.toLowerCase()}.`)
      setError(null)
      setSelectedId(null)
      setReason('')
    } catch (err) {
      setError(errorMessage(err))
      setMessage(null)
    } finally {
      setConfirming(false)
    }
  }

  return (
    <div
      className="relative h-[26rem] focus-within:z-10"
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onFocus={() => setFocused(true)}
      onBlur={onBlur}
    >
      {/* picture side */}
      <button
        type="button"
        onClick={() => {
          setHovered(true)
          requestAnimationFrame(() => pickerRef.current?.focus())
        }}
        className={cn(
          'absolute inset-0 flex cursor-pointer flex-col items-center justify-center gap-5 rounded-2xl border border-white/25 gradient-navy-soft p-6 text-white shadow-card transition-all duration-300 ease-out-expo',
          open ? 'pointer-events-none invisible scale-95 opacity-0' : 'visible scale-100 opacity-100',
        )}
        tabIndex={open ? -1 : 0}
        aria-label={`Deactivate a ${kind}`}
      >
        <PersonArt kind={kind} />
        <span className="font-serif text-2xl font-semibold">{title}</span>
        <span className="text-xs text-white/60">Hover or click to deactivate a {kind} account</span>
      </button>

      {/* form side */}
      <div
        className={cn(
          'absolute inset-0 flex flex-col rounded-2xl border border-hairline bg-surface p-5 shadow-card transition-all duration-300 ease-out-expo',
          open ? 'visible translate-y-0 opacity-100' : 'pointer-events-none invisible translate-y-3 opacity-0',
        )}
      >
        <div className="mb-4 flex items-center justify-between">
          <h3 className="font-serif text-lg font-semibold text-ink">Deactivate {kind}</h3>
          <button
            type="button"
            onClick={reset}
            className="inline-flex cursor-pointer items-center gap-1 rounded-lg px-2 py-1 text-xs font-medium text-muted hover:bg-canvas hover:text-ink"
          >
            <ArrowLeft className="size-3.5" aria-hidden="true" />
            Back
          </button>
        </div>

        <div className="flex flex-1 flex-col gap-3">
          {error && <Alert>{error}</Alert>}
          {message && <Alert variant="success">{message}</Alert>}

          <Combobox
            ref={pickerRef}
            label={title}
            options={options}
            value={selectedId}
            onChange={(id) => {
              setSelectedId(id)
              setMessage(null)
              setError(null)
            }}
            loading={loading}
            placeholder="Type a name, email or #id"
            emptyText={`No active ${kind}s match`}
          />

          {needsReason && (
            <Textarea label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255} rows={3} />
          )}

          <p className="text-xs text-muted">
            {kind === 'client'
              ? 'Their pending and confirmed appointments are cancelled.'
              : 'Their upcoming appointments are cancelled and the clients are notified.'}
          </p>

          <div className="mt-auto">
            <Button
              variant="danger"
              fullWidth
              disabled={selectedId === null || (needsReason && !reason.trim())}
              onClick={() => setConfirming(true)}
            >
              Deactivate {kind}
            </Button>
          </div>
        </div>
      </div>

      {confirming && selected && (
        <Modal>
          <h2 className="mb-1 font-semibold text-ink">Deactivate {selected.label}?</h2>
          <p className="mb-4 text-sm text-muted">
            {selected.hint} (#{selected.id}). They will not be able to sign in again until reactivated.
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setConfirming(false)}>
              Cancel
            </Button>
            <Button variant="danger" loading={pending} onClick={confirm}>
              Deactivate
            </Button>
          </div>
        </Modal>
      )}
    </div>
  )
}

export function DeactivatePanel() {
  const queryClient = useQueryClient()

  const clients = useQuery({ queryKey: ['admin', 'clients'], queryFn: listActiveClients })
  const advisors = useQuery({ queryKey: ['admin', 'advisors'], queryFn: () => listAdvisors() })

  const clientMutation = useMutation({
    mutationFn: (id: number) => deactivateUser(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'clients'] }),
  })

  const advisorMutation = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) => deactivateAdvisor(id, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'advisors'] })
      queryClient.invalidateQueries({ queryKey: ['advisors'] })
    },
  })

  const clientOptions: ComboboxOption[] = (clients.data ?? []).map((c) => ({ id: c.id, label: c.name, hint: c.email }))
  const advisorOptions: ComboboxOption[] = (advisors.data ?? []).map((a) => ({
    id: a.id,
    label: a.name,
    hint: `${a.title} · ${labelFor(a.specialty)}`,
  }))

  return (
    <section className="mx-auto max-w-3xl">
      <h2 className="font-serif text-xl font-semibold text-ink">Deactivate accounts</h2>
      <p className="mb-4 mt-1 text-sm text-muted">Pick who to deactivate. Only active accounts are listed.</p>
      {(clients.error || advisors.error) && (
        <div className="mb-4">
          <Alert>{errorMessage(clients.error ?? advisors.error)}</Alert>
        </div>
      )}
      <div className="grid gap-5 sm:grid-cols-2">
        <FlipCard
          kind="client"
          options={clientOptions}
          loading={clients.isLoading}
          needsReason={false}
          pending={clientMutation.isPending}
          onConfirm={async (id) => (await clientMutation.mutateAsync(id)).msg}
        />
        <FlipCard
          kind="advisor"
          options={advisorOptions}
          loading={advisors.isLoading}
          needsReason
          pending={advisorMutation.isPending}
          onConfirm={async (id, reason) => (await advisorMutation.mutateAsync({ id, reason })).msg}
        />
      </div>
    </section>
  )
}
