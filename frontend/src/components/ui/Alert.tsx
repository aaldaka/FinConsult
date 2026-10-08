import type { ReactNode } from 'react'
import { AlertTriangle, CheckCircle2 } from 'lucide-react'
import { cn } from '@/lib/cn'

export function Alert({ variant = 'error', children }: { variant?: 'error' | 'success'; children: ReactNode }) {
  const isError = variant === 'error'
  return (
    <div
      role={isError ? 'alert' : 'status'}
      className={cn(
        'flex items-start gap-2 rounded-xl border px-3 py-2.5 text-sm',
        isError ? 'border-danger/20 bg-danger/10 text-danger' : 'border-success/20 bg-success/10 text-success',
      )}
    >
      {isError ? (
        <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      ) : (
        <CheckCircle2 className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      )}
      <span>{children}</span>
    </div>
  )
}
