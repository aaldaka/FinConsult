import { useRef, useState } from 'react'
import { Upload } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'
import { Button } from '@/components/ui/Button'
import { Alert } from '@/components/ui/Alert'
import { FilePreview } from '@/components/ui/FilePreview'

interface ReplaceFileModalProps {
  title: string
  /** What the user is replacing, shown under the title. */
  description: string
  /** Extra warning, for example when an approved document goes back to review. */
  warning?: string
  accept: string
  hint: string
  pending: boolean
  error: string | null
  onClose: () => void
  onSubmit: (file: File) => void
}

export function ReplaceFileModal({ title, description, warning, accept, hint, pending, error, onClose, onSubmit }: ReplaceFileModalProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)

  return (
    <Modal className="max-w-md">
      <h2 className="mb-1 font-semibold text-ink">{title}</h2>
      <p className="mb-4 text-sm text-muted">{description}</p>

      {warning && (
        <div className="mb-4">
          <Alert variant="warning">{warning}</Alert>
        </div>
      )}
      {error && (
        <div className="mb-4">
          <Alert>{error}</Alert>
        </div>
      )}

      <input
        ref={inputRef}
        type="file"
        accept={accept}
        className="hidden"
        onChange={(e) => setFile(e.target.files?.[0] ?? null)}
      />

      {file ? (
        <FilePreview
          file={file}
          onRemove={() => {
            setFile(null)
            if (inputRef.current) inputRef.current.value = ''
          }}
        />
      ) : (
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          className="flex w-full cursor-pointer flex-col items-center gap-1 rounded-xl border-2 border-dashed border-royal/20 bg-royal/[0.02] px-4 py-6 text-sm text-muted transition-colors hover:border-gold hover:bg-gold/5"
        >
          <Upload className="size-5 text-royal" aria-hidden="true" />
          <span className="font-medium text-ink">Choose a file</span>
          <span className="text-xs">{hint}</span>
        </button>
      )}

      <div className="mt-5 flex justify-end gap-2">
        <Button variant="ghost" onClick={onClose}>
          Cancel
        </Button>
        <Button disabled={!file} loading={pending} onClick={() => file && onSubmit(file)}>
          Upload and send for review
        </Button>
      </div>
    </Modal>
  )
}
