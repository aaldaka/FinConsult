const dateTime = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
})

const dateOnly = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' })

const timeOnly = new Intl.DateTimeFormat(undefined, { timeStyle: 'short' })

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : dateTime.format(d)
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : dateOnly.format(d)
}

export function formatTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : timeOnly.format(d)
}

/** Turns backend SCREAMING_SNAKE enums into readable labels. */
export function humanize(value: string | null | undefined): string {
  if (!value) return '—'
  return value
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function initials(firstName?: string, lastName?: string): string {
  const a = firstName?.trim()?.charAt(0) ?? ''
  const b = lastName?.trim()?.charAt(0) ?? ''
  return (a + b).toUpperCase() || '?'
}
