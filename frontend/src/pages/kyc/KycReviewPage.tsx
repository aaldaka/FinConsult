import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Download, ShieldCheck } from 'lucide-react'
import { approveDocument, downloadDocument, listDocumentsByStatus, rejectDocument } from '@/api/kyc'
import type { DocumentType, KycStatus } from '@/api/types'
import { Card } from '@/components/ui/Card'
import { Select } from '@/components/ui/Select'
import { Button } from '@/components/ui/Button'
import { Textarea } from '@/components/ui/Textarea'
import { Badge, toneForStatus } from '@/components/ui/Badge'
import { Modal } from '@/components/ui/Modal'
import { Spinner } from '@/components/ui/Spinner'
import { EmptyState } from '@/components/ui/EmptyState'
import { Alert } from '@/components/ui/Alert'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'
import { DOCUMENT_TYPES, labelFor } from '@/lib/options'

export function KycReviewPage() {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<KycStatus>('PENDING')
  const [documentType, setDocumentType] = useState<DocumentType | ''>('')
  const [page, setPage] = useState(0)
  const [rejectTarget, setRejectTarget] = useState<number | null>(null)
  const [rejectReason, setRejectReason] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)

  const { data, isLoading, error } = useQuery({
    queryKey: ['kyc', 'review', status, documentType, page],
    queryFn: () => listDocumentsByStatus({ status, documentType: documentType || undefined, page, size: 10 }),
  })

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['kyc', 'review'] })
  }

  const approveMutation = useMutation({
    mutationFn: (id: number) => approveDocument(id),
    onSuccess: invalidate,
    onError: (err) => setActionError(errorMessage(err)),
  })

  const rejectMutation = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) => rejectDocument(id, reason),
    onSuccess: () => {
      invalidate()
      setRejectTarget(null)
      setRejectReason('')
    },
    onError: (err) => setActionError(errorMessage(err)),
  })

  async function handleDownload(id: number, filename: string) {
    try {
      const blob = await downloadDocument(id)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setActionError(errorMessage(err))
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4 animate-fade-in-up">
        <div>
          <h1 className="text-2xl font-semibold text-ink">KYC review</h1>
          <p className="text-sm text-muted">Approve or reject submitted identity documents.</p>
        </div>
        <div className="flex gap-3">
          <div className="w-40">
            <Select
              label="Status"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as KycStatus)
                setPage(0)
              }}
            >
              <option value="PENDING">Pending</option>
              <option value="APPROVED">Approved</option>
              <option value="REJECTED">Rejected</option>
            </Select>
          </div>
          <div className="w-48">
            <Select
              label="Document type"
              value={documentType}
              onChange={(e) => {
                setDocumentType(e.target.value as DocumentType | '')
                setPage(0)
              }}
            >
              <option value="">All types</option>
              {DOCUMENT_TYPES.map((t) => (
                <option key={t} value={t}>
                  {labelFor(t)}
                </option>
              ))}
            </Select>
          </div>
        </div>
      </div>

      {actionError && <Alert>{actionError}</Alert>}
      {error && <Alert>{errorMessage(error)}</Alert>}
      {isLoading && <Spinner />}

      {data && data.content.length === 0 && (
        <EmptyState icon={<ShieldCheck className="size-8" />} title="Nothing to review" />
      )}

      {data && data.content.length > 0 && (
        <div className="stagger space-y-2">
          {data.content.map((doc) => (
            <Card key={doc.id} interactive className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div className="flex items-center gap-2">
                  <p className="font-medium text-ink">{doc.userEmail}</p>
                  <Badge tone={toneForStatus(doc.status)}>{doc.status}</Badge>
                  {doc.flagged && <Badge tone="danger">Flagged</Badge>}
                  {!doc.flagged && !doc.detectedType && doc.status === 'PENDING' && (
                    <Badge tone="pending">Being checked</Badge>
                  )}
                </div>
                <p className="text-sm text-muted">
                  {labelFor(doc.documentType)} · {doc.originalFileName}
                </p>
                <p className="text-xs text-muted">Submitted {formatDate(doc.submittedAt)}</p>
                {doc.flagReason && <p className="mt-1 text-xs text-danger">Flagged: {doc.flagReason}</p>}
                {doc.rejectionReason && <p className="mt-1 text-xs text-danger">Rejected: {doc.rejectionReason}</p>}
              </div>

              <div className="flex gap-2">
                <Button size="sm" variant="ghost" onClick={() => handleDownload(doc.id, doc.originalFileName)}>
                  <Download className="size-4" aria-hidden="true" />
                </Button>
                {doc.status === 'PENDING' && (
                  <>
                    <Button size="sm" onClick={() => approveMutation.mutate(doc.id)} loading={approveMutation.isPending}>
                      Approve
                    </Button>
                    <Button size="sm" variant="danger" onClick={() => setRejectTarget(doc.id)}>
                      Reject
                    </Button>
                  </>
                )}
              </div>
            </Card>
          ))}

          <div className="flex items-center justify-between pt-2">
            <Button size="sm" variant="secondary" disabled={data.first} onClick={() => setPage((p) => p - 1)}>
              Previous
            </Button>
            <span className="text-sm text-muted">
              Page {data.number + 1} of {Math.max(data.totalPages, 1)}
            </span>
            <Button size="sm" variant="secondary" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
              Next
            </Button>
          </div>
        </div>
      )}

      {rejectTarget !== null && (
        <Modal>
          <h2 className="mb-4 font-semibold text-ink">Reject document</h2>
          <Textarea
            label="Reason"
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
            maxLength={500}
            required
          />
          <div className="mt-4 flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setRejectTarget(null)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={rejectMutation.isPending}
              disabled={!rejectReason.trim()}
              onClick={() => rejectMutation.mutate({ id: rejectTarget, reason: rejectReason })}
            >
              Reject
            </Button>
          </div>
        </Modal>
      )}
    </div>
  )
}
