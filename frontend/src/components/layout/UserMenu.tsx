import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronDown, LogOut, User as UserIcon } from 'lucide-react'
import { cn } from '@/lib/cn'
import { initials } from '@/lib/format'
import { labelFor } from '@/lib/options'

interface UserMenuProps {
  firstName: string
  lastName: string
  role: string
  onLogout: () => void
}

// the avatar in the top bar. Profile and log out live in its menu, which closes on a click outside or Escape
export function UserMenu({ firstName, lastName, role, onLogout }: UserMenuProps) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    function onPointer(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
        className="flex cursor-pointer items-center gap-2 rounded-full py-1 pl-1 pr-2 text-sm text-white/90 transition-colors duration-200 hover:bg-white/10"
      >
        <span className="flex size-8 items-center justify-center rounded-full bg-white/10 text-xs font-semibold ring-2 ring-sky-200/60">
          {initials(firstName, lastName)}
        </span>
        <span className="hidden font-medium sm:inline">{firstName}</span>
        <ChevronDown className={cn('size-4 transition-transform duration-200', open && 'rotate-180')} aria-hidden="true" />
      </button>

      {open && (
        <div
          role="menu"
          className="absolute right-0 mt-2 w-56 overflow-hidden rounded-xl border border-hairline bg-surface text-ink shadow-lifted animate-scale-in"
        >
          <div className="border-b border-hairline px-4 py-3">
            <p className="truncate text-sm font-semibold">
              {firstName} {lastName}
            </p>
            <p className="text-xs text-muted">{labelFor(role)}</p>
          </div>
          <Link
            to="/profile"
            role="menuitem"
            onClick={() => setOpen(false)}
            className="flex items-center gap-2 px-4 py-2.5 text-sm transition-colors hover:bg-canvas"
          >
            <UserIcon className="size-4 text-muted" aria-hidden="true" />
            Profile
          </Link>
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              setOpen(false)
              onLogout()
            }}
            className="flex w-full cursor-pointer items-center gap-2 px-4 py-2.5 text-left text-sm text-danger transition-colors hover:bg-canvas"
          >
            <LogOut className="size-4" aria-hidden="true" />
            Log out
          </button>
        </div>
      )}
    </div>
  )
}
