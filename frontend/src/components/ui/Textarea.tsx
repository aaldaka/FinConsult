import { forwardRef, useId } from 'react'
import type { TextareaHTMLAttributes } from 'react'
import { cn } from '@/lib/cn'

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string
  error?: string
  hint?: string
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  { label, error, hint, id, className, rows = 3, ...props },
  ref,
) {
  const generatedId = useId()
  const textareaId = id ?? generatedId
  const errorId = `${textareaId}-error`
  const hintId = `${textareaId}-hint`

  return (
    <div className="space-y-1.5">
      <label htmlFor={textareaId} className="block text-sm font-medium text-ink">
        {label}
      </label>

      <textarea
        ref={ref}
        id={textareaId}
        rows={rows}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : hint ? hintId : undefined}
        className={cn(
          'w-full resize-none rounded-xl border bg-surface px-3 py-2 text-sm text-ink transition-colors',
          'placeholder:text-muted/70',
          'focus:border-royal focus:ring-2 focus:ring-royal/20 focus:outline-none',
          'disabled:cursor-not-allowed disabled:bg-canvas disabled:text-muted',
          error ? 'border-danger focus:border-danger focus:ring-danger/20' : 'border-hairline',
          className,
        )}
        {...props}
      />

      {error ? (
        <p id={errorId} role="alert" className="text-sm text-danger">
          {error}
        </p>
      ) : hint ? (
        <p id={hintId} className="text-sm text-muted">
          {hint}
        </p>
      ) : null}
    </div>
  )
})
