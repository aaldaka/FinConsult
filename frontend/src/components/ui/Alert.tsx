import type { ReactNode } from 'react'
import { AlertTriangle, CheckCircle2 } from 'lucide-react'
import { cn } from '@/lib/cn'

type Variant = 'error' | 'success' | 'warning'

const styles: Record<Variant, string> = {
  error: 'border-danger/20 bg-danger/10 text-danger',
  success: 'border-success/20 bg-success/10 text-success',
  warning: 'border-pending/30 bg-pending/10 text-gold-deep',
}

export function Alert({ variant = 'error', children }: { variant?: Variant; children: ReactNode }) {
  const isError = variant === 'error'
  return (
    <div role={isError ? 'alert' : 'status'} className={cn('flex items-start gap-2 rounded-xl border px-3 py-2.5 text-sm', styles[variant])}>
      {variant === 'success' ? (
        <CheckCircle2 className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      ) : (
        <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      )}
      <span>{children}</span>
    </div>
  )
}
