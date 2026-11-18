import { toneForStatus } from '@/components/ui/Badge'
import { cn } from '@/lib/cn'
import { labelFor } from '@/lib/options'

// a status as a pill with a coloured dot, used for appointments, slots and documents
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

interface StatusPillProps {
  status: string
  /** text to show instead of the status name */
  label?: string
  /** a tone instead of the one the status normally has */
  tone?: string
}

export function StatusPill({ status, label, tone }: StatusPillProps) {
  const t = tone ?? toneForStatus(status)
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border px-2.5 py-0.5 text-xs font-medium',
        pillTone[t] ?? pillTone.neutral,
      )}
    >
      <span className={cn('size-1.5 rounded-full', dotTone[t] ?? dotTone.neutral)} aria-hidden="true" />
      {label ?? labelFor(status)}
    </span>
  )
}
