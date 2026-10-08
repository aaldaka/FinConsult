import type { HTMLAttributes } from 'react'
import { cn } from '@/lib/cn'

type Tone = 'neutral' | 'success' | 'pending' | 'danger' | 'royal' | 'gold'

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: Tone
}

const tones: Record<Tone, string> = {
  neutral: 'bg-canvas text-muted border-hairline',
  success: 'bg-success/10 text-success border-success/20',
  pending: 'bg-pending/10 text-pending border-pending/20',
  danger: 'bg-danger/10 text-danger border-danger/20',
  royal: 'bg-royal/10 text-royal border-royal/20',
  gold: 'bg-gold/15 text-gold-deep border-gold/30',
}

export function Badge({ tone = 'neutral', className, ...props }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-medium',
        tones[tone],
        className,
      )}
      {...props}
    />
  )
}

const statusTones: Record<string, Tone> = {
  PENDING: 'pending',
  CONFIRMED: 'royal',
  COMPLETED: 'success',
  CANCELLED: 'neutral',
  NO_SHOW: 'danger',
  AVAILABLE: 'success',
  BOOKED: 'pending',
  APPROVED: 'success',
  REJECTED: 'danger',
}

export function toneForStatus(status: string): Tone {
  return statusTones[status] ?? 'neutral'
}
