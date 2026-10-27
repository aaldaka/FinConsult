import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BriefcaseBusiness, Download } from 'lucide-react'
import {
  approveApplication,
  downloadCertificate,
  listApplicationsByStatus,
  rejectApplication,
} from '@/api/advisorApplications'
import type { AdvisorApplicationResponse, KycStatus } from '@/api/types'
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
import { labelFor } from '@/lib/options'

function stillChecking(application: AdvisorApplicationResponse) {
  return application.certificates.some((c) => c.status === 'PROCESSING')
}

export function ApplicationReviewPage() {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<KycStatus>('PENDING')
  const [page, setPage] = useState(0)
  const [rejectTarget, setRejectTarget] = useState<number | null>(null)
  const [rejectReason, setRejectReason] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)

  const { data, isLoading, error } = useQuery({
    queryKey: ['advisorApplications', 'review', status, page],
    queryFn: () => listApplicationsByStatus({ status, page, size: 10 }),
  })

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['advisorApplications', 'review'] })
  }

  const approveMutation = useMutation({
    mutationFn: (id: number) => approveApplication(id),
    onSuccess: invalidate,
    onError: (err) => setActionError(errorMessage(err)),
  })

  const rejectMutation = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) => rejectApplication(id, reason),
    onSuccess: () => {
      invalidate()
      setRejectTarget(null)
      setRejectReason('')
    },
    onError: (err) => setActionError(errorMessage(err)),
  })

  async function handleDownload(certificateId: number, filename: string) {
    try {
      const blob = await downloadCertificate(certificateId)
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
          <h1 className="text-2xl font-semibold text-ink">Advisor applications</h1>
          <p className="text-sm text-muted">
            Approving creates the advisor profile and switches the applicant's role.
          </p>
        </div>
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
      </div>

      {actionError && <Alert>{actionError}</Alert>}
      {error && <Alert>{errorMessage(error)}</Alert>}
      {isLoading && <Spinner />}

      {data && data.content.length === 0 && (
        <EmptyState icon={<BriefcaseBusiness className="size-8" />} title="Nothing to review" />
      )}

      {data && data.content.length > 0 && (
        <div className="stagger space-y-2">
          {data.content.map((application) => (
            <Card key={application.id} interactive className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <div className="flex items-center gap-2">
                  <p className="font-medium text-ink">{application.applicantName}</p>
                  <Badge tone={toneForStatus(application.status)}>{application.status}</Badge>
                </div>
                <p className="text-sm text-muted">{application.applicantEmail}</p>
                <p className="text-sm text-muted">
                  {application.title} · {labelFor(application.specialty)} · {application.yearsOfExperience} years
                </p>
                <p className="text-xs text-muted">Submitted {formatDate(application.submittedAt)}</p>
                {application.rejectionReason && (
                  <p className="mt-1 text-xs text-danger">Rejected: {application.rejectionReason}</p>
                )}
                <div className="mt-2 flex flex-wrap gap-2">
                  {application.certificates.map((certificate) => (
                    <div key={certificate.id} className="flex items-center gap-1">
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => handleDownload(certificate.id, certificate.originalFileName)}
                      >
                        <Download className="size-4" aria-hidden="true" />
                        {certificate.originalFileName}
                      </Button>
                      {certificate.status === 'PROCESSING' && <Badge tone="pending">Being checked</Badge>}
                      {certificate.status === 'FLAGGED' && <Badge tone="danger">Flagged</Badge>}
                    </div>
                  ))}
                </div>
              </div>

              {application.status === 'PENDING' && (
                <div className="flex gap-2">
                  <Button
                    size="sm"
                    onClick={() => approveMutation.mutate(application.id)}
                    loading={approveMutation.isPending}
                    disabled={stillChecking(application)}
                    title={stillChecking(application) ? 'Certificates are still being checked' : undefined}
                  >
                    Approve
                  </Button>
                  <Button size="sm" variant="danger" onClick={() => setRejectTarget(application.id)}>
                    Reject
                  </Button>
                </div>
              )}
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
          <h2 className="mb-4 font-semibold text-ink">Reject application</h2>
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
