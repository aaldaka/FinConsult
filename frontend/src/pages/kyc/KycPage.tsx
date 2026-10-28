import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Paperclip, RefreshCw, ShieldCheck, Upload } from 'lucide-react'
import { listMyDocuments, replaceDocumentFile, submitDocuments } from '@/api/kyc'
import type { DocumentType, KycDocResponse } from '@/api/types'
import { Card } from '@/components/ui/Card'
import { Select } from '@/components/ui/Select'
import { Button } from '@/components/ui/Button'
import { Badge, toneForStatus } from '@/components/ui/Badge'
import { Spinner } from '@/components/ui/Spinner'
import { EmptyState } from '@/components/ui/EmptyState'
import { Alert } from '@/components/ui/Alert'
import { FilePreview } from '@/components/ui/FilePreview'
import { ReplaceFileModal } from '@/components/ReplaceFileModal'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'
import { DOCUMENT_TYPES, labelFor } from '@/lib/options'

interface QueuedDocument {
  file: File
  documentType: DocumentType
}

const ACCEPTED_TYPES = 'image/jpeg,image/png,application/pdf,.docx'

// the background check has not finished yet, so the file can't be replaced
function beingChecked(doc: KycDocResponse) {
  return !doc.flagged && !doc.detectedType && doc.status === 'PENDING'
}

export function KycPage() {
  const queryClient = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [documentType, setDocumentType] = useState<DocumentType>('NATIONAL_ID')
  const [queue, setQueue] = useState<QueuedDocument[]>([])
  const [selected, setSelected] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [replaceTarget, setReplaceTarget] = useState<KycDocResponse | null>(null)
  const [replaceError, setReplaceError] = useState<string | null>(null)

  const { data, isLoading } = useQuery({ queryKey: ['kyc', 'mine'], queryFn: listMyDocuments })

  const replaceMutation = useMutation({
    mutationFn: ({ id, file }: { id: number; file: File }) => replaceDocumentFile(id, file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['kyc', 'mine'] })
      setReplaceTarget(null)
      setReplaceError(null)
    },
    onError: (err) => setReplaceError(errorMessage(err)),
  })

  const submitMutation = useMutation({
    mutationFn: () => submitDocuments(queue),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['kyc', 'mine'] })
      setError(null)
      setQueue([])
    },
    onError: (err) => setError(errorMessage(err)),
  })

  function clearSelected() {
    setSelected(null)
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  function handleAdd() {
    const file = selected
    if (!file) {
      setError('Choose a file first.')
      return
    }
    if (queue.some((item) => item.documentType === documentType)) {
      setError('You already added a ' + labelFor(documentType) + ' to the list.')
      return
    }
    setQueue([...queue, { file, documentType }])
    setError(null)
    clearSelected()
  }

  function handleRemove(index: number) {
    setQueue(queue.filter((_, i) => i !== index))
  }

  return (
      <div className="space-y-6">
        <div className="animate-fade-in-up">
          <h1 className="text-2xl font-semibold text-ink">KYC documents</h1>
          <p className="text-sm text-muted">Some services require an approved identity document before you can book.</p>
        </div>

        <Card className="animate-fade-in-up">
          <h2 className="mb-4 flex items-center gap-2 font-semibold text-ink">
            <Upload className="size-5 text-royal" aria-hidden="true" />
            Submit documents
          </h2>
          {error && (
              <div className="mb-4">
                <Alert>{error}</Alert>
              </div>
          )}
          <div className="grid items-end gap-3 sm:grid-cols-[14rem_1fr_auto]">
            <div>
              <Select label="Document type" value={documentType} onChange={(e) => setDocumentType(e.target.value as DocumentType)}>
                {DOCUMENT_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {labelFor(t)}
                    </option>
                ))}
              </Select>
            </div>
            <div className="space-y-1.5">
              <label htmlFor="kyc-file" className="block text-sm font-medium text-ink">
                File
              </label>
              {/* the native input is hidden, this box fills the row so the button sits at the end */}
              <input
                  id="kyc-file"
                  ref={fileInputRef}
                  type="file"
                  accept={ACCEPTED_TYPES}
                  onChange={(e) => setSelected(e.target.files?.[0] ?? null)}
                  className="sr-only"
              />
              <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  className="flex h-11 w-full cursor-pointer items-center gap-3 rounded-xl border border-dashed border-hairline bg-surface px-3 text-left text-sm transition-colors hover:border-royal/40 hover:bg-royal/[0.03]"
              >
                <span className="inline-flex items-center gap-1.5 rounded-lg bg-royal/10 px-2.5 py-1 font-medium text-royal">
                  <Paperclip className="size-3.5" aria-hidden="true" />
                  Choose file
                </span>
                <span className={selected ? 'truncate text-ink' : 'truncate text-muted'}>
                  {selected ? selected.name : 'PDF, JPG, PNG or DOCX'}
                </span>
              </button>
            </div>
            <Button onClick={handleAdd} className="h-11">
              Add to list
            </Button>
          </div>

          {selected && (
              <div className="mt-4 max-w-md">
                <p className="mb-1.5 text-xs font-medium text-muted">Preview</p>
                <FilePreview file={selected} label={labelFor(documentType)} onRemove={clearSelected} />
              </div>
          )}

          {queue.length > 0 && (
              <div className="mt-4 space-y-2">
                <p className="text-xs font-medium text-muted">Ready to submit</p>
                <div className="grid gap-2 sm:grid-cols-2">
                  {queue.map((item, index) => (
                      <FilePreview
                          key={item.documentType}
                          file={item.file}
                          label={labelFor(item.documentType)}
                          onRemove={() => handleRemove(index)}
                      />
                  ))}
                </div>
                <Button onClick={() => submitMutation.mutate()} loading={submitMutation.isPending}>
                  Submit all ({queue.length})
                </Button>
              </div>
          )}
        </Card>

        {isLoading && <Spinner />}

        {data && data.length === 0 && (
            <EmptyState icon={<ShieldCheck className="size-8" />} title="No documents submitted" />
        )}

        {data && data.length > 0 && (
            <div className="stagger space-y-2">
              {data.map((doc) => (
                  <Card key={doc.id} interactive className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <div className="flex items-center gap-2">
                        <p className="font-medium text-ink">{labelFor(doc.documentType)}</p>
                        <Badge tone={toneForStatus(doc.status)}>{doc.status}</Badge>
                        {doc.flagged && <Badge tone="danger">Flagged for review</Badge>}
                        {!doc.flagged && !doc.detectedType && doc.status === 'PENDING' && (
                          <Badge tone="pending">Being checked</Badge>
                        )}
                      </div>
                      <p className="text-sm text-muted">{doc.originalFileName}</p>
                      {doc.rejectionReason && <p className="mt-1 text-xs text-danger">Rejected: {doc.rejectionReason}</p>}
                    </div>
                    <div className="flex flex-col items-end gap-2">
                      <p className="text-xs text-muted">Submitted {formatDate(doc.submittedAt)}</p>
                      <Button
                          size="sm"
                          variant="secondary"
                          disabled={beingChecked(doc)}
                          title={beingChecked(doc) ? 'Wait until the check has finished' : undefined}
                          onClick={() => {
                            setReplaceError(null)
                            setReplaceTarget(doc)
                          }}
                      >
                        <RefreshCw className="size-4" aria-hidden="true" />
                        Replace file
                      </Button>
                    </div>
                  </Card>
              ))}
            </div>
        )}

        {replaceTarget && (
            <ReplaceFileModal
                title={'Replace ' + labelFor(replaceTarget.documentType)}
                description={`Current file: ${replaceTarget.originalFileName}. The new file is checked again and goes to the admin for review.`}
                warning={
                  replaceTarget.status === 'APPROVED'
                      ? 'This document is approved. A new file sends it back to review, and services that need it cannot be booked until it is approved again.'
                      : undefined
                }
                accept={ACCEPTED_TYPES}
                hint="JPEG, PNG, PDF or DOCX, up to 5 MB"
                pending={replaceMutation.isPending}
                error={replaceError}
                onClose={() => setReplaceTarget(null)}
                onSubmit={(file) => replaceMutation.mutate({ id: replaceTarget.id, file })}
            />
        )}
      </div>
  )
}