import { forwardRef, useId, useMemo, useState } from 'react'
import { Check, ChevronDown, Search } from 'lucide-react'
import { cn } from '@/lib/cn'
import { initials } from '@/lib/format'

function initialsOf(name: string) {
  const parts = name.trim().split(/\s+/)
  return initials(parts[0], parts.length > 1 ? parts[parts.length - 1] : undefined)
}

export interface ComboboxOption {
  id: number
  label: string
  hint?: string
}

interface ComboboxProps {
  label: string
  options: ComboboxOption[]
  value: number | null
  onChange: (id: number | null) => void
  placeholder?: string
  loading?: boolean
  emptyText?: string
}

/**
 * Text input with a filtered dropdown. Shows every option on focus, narrows it down as you type
 * (matches name, hint or #id) and supports arrow keys, Enter and Escape.
 */
export const Combobox = forwardRef<HTMLInputElement, ComboboxProps>(function Combobox(
  { label, options, value, onChange, placeholder, loading, emptyText = 'No matches' },
  ref,
) {
  const inputId = useId()
  const listId = `${inputId}-list`
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)

  const selected = options.find((o) => o.id === value) ?? null

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase().replace(/^#/, '')
    if (!q) return options
    return options.filter(
      (o) => o.label.toLowerCase().includes(q) || o.hint?.toLowerCase().includes(q) || String(o.id) === q,
    )
  }, [options, query])

  function pick(option: ComboboxOption) {
    onChange(option.id)
    setQuery('')
    setOpen(false)
  }

  function onKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setOpen(true)
      setActive((i) => Math.min(i + 1, filtered.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActive((i) => Math.max(i - 1, 0))
    } else if (e.key === 'Enter' && open && filtered[active]) {
      e.preventDefault()
      pick(filtered[active])
    } else if (e.key === 'Escape') {
      setOpen(false)
    }
  }

  // while closed the input shows the picked person, once you type it becomes the search text
  const inputValue = open || !selected ? query : selected.label

  return (
    <div className="space-y-1.5">
      <label htmlFor={inputId} className="block text-sm font-medium text-ink">
        {label}
      </label>
      <div className="relative">
        <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" aria-hidden="true">
          <Search className="size-4" />
        </span>
        <input
          ref={ref}
          id={inputId}
          role="combobox"
          aria-expanded={open}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={open && filtered[active] ? `${listId}-${filtered[active].id}` : undefined}
          autoComplete="off"
          value={inputValue}
          placeholder={selected ? selected.label : placeholder}
          onChange={(e) => {
            setQuery(e.target.value)
            setActive(0)
            setOpen(true)
            if (selected) onChange(null)
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setOpen(false)}
          onKeyDown={onKeyDown}
          className="h-11 w-full rounded-xl border border-hairline bg-surface pl-10 pr-10 text-sm text-ink transition-colors placeholder:text-muted/70 focus:border-royal focus:ring-2 focus:ring-royal/20 focus:outline-none"
        />
        <button
          type="button"
          tabIndex={-1}
          aria-label={open ? 'Close list' : 'Open list'}
          onMouseDown={(e) => e.preventDefault()}
          onClick={() => setOpen((o) => !o)}
          className="absolute right-2 top-1/2 flex size-7 -translate-y-1/2 cursor-pointer items-center justify-center rounded-lg text-muted hover:bg-canvas"
        >
          <ChevronDown className={cn('size-4 transition-transform duration-200', open && 'rotate-180')} aria-hidden="true" />
        </button>

        {open && (
          <ul
            id={listId}
            role="listbox"
            className="absolute z-30 mt-1.5 max-h-60 w-full overflow-y-auto rounded-xl border border-hairline bg-surface p-1 shadow-lifted animate-scale-in"
          >
            {loading && <li className="px-3 py-2 text-sm text-muted">Loading...</li>}
            {!loading && filtered.length === 0 && <li className="px-3 py-2 text-sm text-muted">{emptyText}</li>}
            {!loading &&
              filtered.map((o, i) => (
                <li
                  key={o.id}
                  id={`${listId}-${o.id}`}
                  role="option"
                  aria-selected={o.id === value}
                  onMouseDown={(e) => e.preventDefault()}
                  onMouseEnter={() => setActive(i)}
                  onClick={() => pick(o)}
                  className={cn(
                    'flex cursor-pointer items-center gap-3 rounded-lg px-2.5 py-2 text-sm',
                    i === active ? 'bg-royal/10' : 'hover:bg-canvas',
                  )}
                >
                  <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-navy text-xs font-semibold text-gold">
                    {initialsOf(o.label)}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-medium text-ink">{o.label}</span>
                    {o.hint && <span className="block truncate text-xs text-muted">{o.hint}</span>}
                  </span>
                  <span className="text-xs text-muted tabular">#{o.id}</span>
                  {o.id === value && <Check className="size-4 text-royal" aria-hidden="true" />}
                </li>
              ))}
          </ul>
        )}
      </div>
    </div>
  )
})
