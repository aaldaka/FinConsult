import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ShieldCheck, Upload } from 'lucide-react'
import { listMyDocuments, submitDocument } from '@/api/kyc'
import type { DocumentType } from '@/api/types'
import { Card } from '@/components/ui/Card'
import { Select } from '@/components/ui/Select'
import { Button } from '@/components/ui/Button'
import { Badge, toneForStatus } from '@/components/ui/Badge'
import { Spinner } from '@/components/ui/Spinner'
import { EmptyState } from '@/components/ui/EmptyState'
import { Alert } from '@/components/ui/Alert'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'
import { DOCUMENT_TYPES, labelFor } from '@/lib/options'

export function KycPage() {
  const queryClient = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [documentType, setDocumentType] = useState<DocumentType>('NATIONAL_ID')
  const [error, setError] = useState<string | null>(null)

  const { data, isLoading } = useQuery({ queryKey: ['kyc', 'mine'], queryFn: listMyDocuments })

  const submitMutation = useMutation({
    mutationFn: (file: File) => submitDocument(file, documentType),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['kyc', 'mine'] })
      setError(null)
      if (fileInputRef.current) fileInputRef.current.value = ''
    },
    onError: (err) => setError(errorMessage(err)),
  })

  function handleUpload() {
    const file = fileInputRef.current?.files?.[0]
    if (!file) {
      setError('Choose a file first.')
      return
    }
    submitMutation.mutate(file)
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
          Submit a document
        </h2>
        {error && (
          <div className="mb-4">
            <Alert>{error}</Alert>
          </div>
        )}
        <div className="flex flex-wrap items-end gap-3">
          <div className="w-56">
            <Select label="Document type" value={documentType} onChange={(e) => setDocumentType(e.target.value as DocumentType)}>
              {DOCUMENT_TYPES.map((t) => (
                <option key={t} value={t}>
                  {labelFor(t)}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className="mb-1.5 block text-sm font-medium text-ink">File</label>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png,application/pdf,.docx"
              className="block text-sm text-ink file:mr-3 file:rounded-lg file:border-0 file:bg-royal/10 file:px-3 file:py-2 file:text-royal file:hover:bg-royal/15"
            />
          </div>
          <Button onClick={handleUpload} loading={submitMutation.isPending}>
            Upload
          </Button>
        </div>
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
                </div>
                <p className="text-sm text-muted">{doc.originalFileName}</p>
                {doc.rejectionReason && <p className="mt-1 text-xs text-danger">Rejected: {doc.rejectionReason}</p>}
              </div>
              <p className="text-xs text-muted">Submitted {formatDate(doc.submittedAt)}</p>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
