import { forwardRef } from 'react'
import type { ButtonHTMLAttributes } from 'react'
import { Loader2 } from 'lucide-react'
import { cn } from '@/lib/cn'

type Variant = 'primary' | 'gold' | 'secondary' | 'ghost' | 'danger'
type Size = 'sm' | 'md' | 'lg'

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  loading?: boolean
  fullWidth?: boolean
}

const variants: Record<Variant, string> = {
  primary:
    'gradient-navy text-white shadow-card hover:shadow-lifted hover:brightness-110 active:brightness-95',
  gold: 'bg-gold text-navy font-semibold shadow-card hover:shadow-lifted hover:brightness-105 active:brightness-95',
  secondary:
    'bg-surface text-navy border border-hairline shadow-card hover:border-navy/30 hover:bg-canvas',
  ghost: 'bg-transparent text-navy hover:bg-navy/5',
  danger: 'bg-danger text-white shadow-card hover:brightness-110 active:brightness-95',
}

const sizes: Record<Size, string> = {
  sm: 'h-9 px-3 text-sm gap-1.5',
  md: 'h-11 px-4 text-sm gap-2',
  lg: 'h-12 px-6 text-base gap-2',
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', loading = false, fullWidth, className, children, disabled, ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cn(
        'inline-flex cursor-pointer items-center justify-center rounded-xl font-medium transition-all duration-150 active:scale-[0.98]',
        'disabled:cursor-not-allowed disabled:opacity-60 disabled:shadow-none disabled:brightness-100 disabled:active:scale-100',
        variants[variant],
        sizes[size],
        fullWidth && 'w-full',
        className,
      )}
      {...props}
    >
      {loading && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
      {children}
    </button>
  )
})
