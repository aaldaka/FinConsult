import { useEffect, useState } from 'react'
import { ExternalLink, FileText, X } from 'lucide-react'
import { formatFileSize } from '@/lib/format'

interface FilePreviewProps {
  file: File
  /** Shown above the file name, for example the document type. */
  label?: string
  onRemove?: () => void
}

function openInNewTab(file: File) {
  const url = URL.createObjectURL(file)
  window.open(url, '_blank', 'noopener')
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}

// preview of a file picked in the browser, before it is uploaded
export function FilePreview({ file, label, onRemove }: FilePreviewProps) {
  const [thumbnail, setThumbnail] = useState<{ file: File; src: string } | null>(null)
  const isImage = file.type.startsWith('image/')

  useEffect(() => {
    if (!isImage) return
    let cancelled = false
    const reader = new FileReader()
    reader.onload = () => {
      if (!cancelled && typeof reader.result === 'string') setThumbnail({ file, src: reader.result })
    }
    reader.readAsDataURL(file)
    return () => {
      cancelled = true
      reader.abort()
    }
  }, [file, isImage])

  const src = thumbnail?.file === file ? thumbnail.src : null

  return (
    <div className="flex items-center gap-3 rounded-xl border border-hairline bg-surface p-2.5 shadow-card">
      <div className="flex size-14 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-hairline bg-canvas">
        {src ? (
          <img src={src} alt={`Preview of ${file.name}`} className="size-full object-cover" />
        ) : (
          <FileText className="size-6 text-royal" aria-hidden="true" />
        )}
      </div>

      <div className="min-w-0 flex-1">
        {label && <p className="text-xs font-medium uppercase tracking-wide text-gold-deep">{label}</p>}
        <p className="truncate text-sm font-medium text-ink">{file.name}</p>
        <div className="flex items-center gap-3 text-xs text-muted">
          <span>{formatFileSize(file.size)}</span>
          <button
            type="button"
            onClick={() => openInNewTab(file)}
            className="inline-flex cursor-pointer items-center gap-1 text-royal hover:underline"
          >
            Open
            <ExternalLink className="size-3" aria-hidden="true" />
          </button>
        </div>
      </div>

      {onRemove && (
        <button
          type="button"
          onClick={onRemove}
          aria-label={'Remove ' + file.name}
          className="cursor-pointer rounded-lg p-1.5 text-muted transition-colors hover:bg-danger/10 hover:text-danger"
        >
          <X className="size-4" aria-hidden="true" />
        </button>
      )}
    </div>
  )
}
