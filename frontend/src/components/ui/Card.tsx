import type { HTMLAttributes } from 'react'
import { cn } from '@/lib/cn'

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  /** Adds a hover lift + shadow — use for cards that sit inside a link or button. */
  interactive?: boolean
}

export function Card({ interactive, className, ...props }: CardProps) {
  return (
    <div
      className={cn(
        'rounded-2xl border border-hairline bg-surface p-5 shadow-card',
        interactive && 'card-interactive',
        className,
      )}
      {...props}
    />
  )
}
