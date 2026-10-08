import { cn } from '@/lib/cn'

export function Spinner({ className }: { className?: string }) {
  return (
    <div className={cn('flex items-center justify-center gap-1.5 py-10', className)} role="status" aria-live="polite">
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          className="size-2.5 animate-bounce rounded-full bg-royal"
          style={{ animationDelay: `${i * 120}ms`, animationDuration: '900ms' }}
        />
      ))}
      <span className="sr-only">Loading…</span>
    </div>
  )
}
