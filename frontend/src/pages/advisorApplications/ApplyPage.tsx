import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BriefcaseBusiness, Check, FileText, RefreshCw, X } from 'lucide-react'
import { listMyApplications, replaceCertificate, submitApplication } from '@/api/advisorApplications'
import { listMyDocuments } from '@/api/kyc'
import type { CertificateResponse, CertificateStatus, Specialty } from '@/api/types'
import { Card } from '@/components/ui/Card'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Button } from '@/components/ui/Button'
import { Badge, toneForStatus } from '@/components/ui/Badge'
import { Spinner } from '@/components/ui/Spinner'
import { Alert } from '@/components/ui/Alert'
import { FilePreview } from '@/components/ui/FilePreview'
import { ReplaceFileModal } from '@/components/ReplaceFileModal'
import { PageHeader } from '@/components/layout/PageHeader'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'
import { SPECIALTIES, labelFor } from '@/lib/options'

const CERTIFICATE_TYPES = 'application/pdf,image/jpeg,image/png'

const certificateLabel: Record<CertificateStatus, string> = {
  PROCESSING: 'Being checked',
  VALID: 'Checked',
  FLAGGED: 'Flagged for review',
}

const certificateTone: Record<CertificateStatus, 'pending' | 'success' | 'danger'> = {
  PROCESSING: 'pending',
  VALID: 'success',
  FLAGGED: 'danger',
}

function Requirement({ met, children }: { met: boolean; children: React.ReactNode }) {
  return (
    <li className="flex items-start gap-2 text-sm">
      {met ? (
        <Check className="mt-0.5 size-4 shrink-0 text-success" aria-hidden="true" />
      ) : (
        <X className="mt-0.5 size-4 shrink-0 text-danger" aria-hidden="true" />
      )}
      <span className={met ? 'text-ink' : 'text-muted'}>{children}</span>
      <span className="sr-only">{met ? '(met)' : '(not met)'}</span>
    </li>
  )
}

export function ApplyPage() {
  const queryClient = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [title, setTitle] = useState('')
  const [specialty, setSpecialty] = useState<Specialty>('GENERAL')
  const [yearsOfExperience, setYearsOfExperience] = useState('0')
  const [error, setError] = useState<string | null>(null)
  const [submitted, setSubmitted] = useState(false)
  const [certificates, setCertificates] = useState<File[]>([])
  const [replaceTarget, setReplaceTarget] = useState<CertificateResponse | null>(null)
  const [replaceError, setReplaceError] = useState<string | null>(null)

  const { data, isLoading } = useQuery({ queryKey: ['advisorApplications', 'mine'], queryFn: listMyApplications })
  const { data: documents } = useQuery({ queryKey: ['kyc', 'mine'], queryFn: listMyDocuments })

  const hasApprovedId = (documents ?? []).some(
    (doc) => doc.status === 'APPROVED' && (doc.documentType === 'NATIONAL_ID' || doc.documentType === 'PASSPORT'),
  )
  const hasPending = (data?.applications ?? []).some((a) => a.status === 'PENDING')
  const attemptsLeft = data ? data.maxAttempts - data.attemptsUsed : 0
  const canApply = hasApprovedId && !hasPending && attemptsLeft > 0

  const submitMutation = useMutation({
    mutationFn: (certificates: File[]) =>
      submitApplication({ title, specialty, yearsOfExperience: Number(yearsOfExperience) || 0 }, certificates),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['advisorApplications', 'mine'] })
      setError(null)
      setSubmitted(true)
      setTitle('')
      setCertificates([])
      if (fileInputRef.current) fileInputRef.current.value = ''
    },
    onError: (err) => {
      setSubmitted(false)
      setError(errorMessage(err))
    },
  })

  const replaceMutation = useMutation({
    mutationFn: ({ id, file }: { id: number; file: File }) => replaceCertificate(id, file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['advisorApplications', 'mine'] })
      setReplaceTarget(null)
      setReplaceError(null)
    },
    onError: (err) => setReplaceError(errorMessage(err)),
  })

  function handleSubmit() {
    if (!title.trim()) {
      setError('Enter the title you are applying under.')
      return
    }
    if (certificates.length === 0) {
      setError('Attach at least one certificate.')
      return
    }
    submitMutation.mutate(certificates)
  }

  function addCertificates(files: FileList | null) {
    const picked = Array.from(files ?? [])
    // skip a file that is already in the list (same name and size)
    setCertificates((current) => [
      ...current,
      ...picked.filter((f) => !current.some((c) => c.name === f.name && c.size === f.size)),
    ])
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Become an advisor"
        description="Apply with your credentials. An admin reviews every application before an advisor account is created."
      />

      {isLoading && <Spinner />}

      {data && (
        <Card className="animate-fade-in-up">
          <h2 className="mb-3 font-semibold text-ink">Before you apply</h2>
          <ul className="space-y-2">
            <Requirement met={hasApprovedId}>An approved national ID or passport in your KYC documents</Requirement>
            <Requirement met={!hasPending}>No application already waiting for review</Requirement>
            <Requirement met={attemptsLeft > 0}>
              Attempts left: {attemptsLeft} of {data.maxAttempts}
            </Requirement>
          </ul>
        </Card>
      )}

      {submitted && (
        <Alert variant="success">
          Application submitted. You will be notified once an admin reviews it. If it is approved, log in again to use
          your advisor account.
        </Alert>
      )}

      {data && canApply && (
        <Card className="animate-fade-in-up">
          <h2 className="mb-4 flex items-center gap-2 font-semibold text-ink">
            <BriefcaseBusiness className="size-5 text-royal" aria-hidden="true" />
            Your application
          </h2>
          {error && (
            <div className="mb-4">
              <Alert>{error}</Alert>
            </div>
          )}
          <div className="grid gap-4 sm:grid-cols-2">
            <Input
              label="Title"
              placeholder="Senior Financial Advisor"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={100}
            />
            <Select label="Specialty" value={specialty} onChange={(e) => setSpecialty(e.target.value as Specialty)}>
              {SPECIALTIES.map((s) => (
                <option key={s} value={s}>
                  {labelFor(s)}
                </option>
              ))}
            </Select>
            <Input
              label="Years of experience"
              type="number"
              min={0}
              max={60}
              value={yearsOfExperience}
              onChange={(e) => setYearsOfExperience(e.target.value)}
            />
            <div>
              <label className="mb-1.5 block text-sm font-medium text-ink">Certificates</label>
              <input
                ref={fileInputRef}
                type="file"
                multiple
                accept={CERTIFICATE_TYPES}
                onChange={(e) => addCertificates(e.target.files)}
                className="block text-sm text-ink file:mr-3 file:rounded-lg file:border-0 file:bg-royal/10 file:px-3 file:py-2 file:text-royal file:hover:bg-royal/15"
              />
              <p className="mt-1 text-xs text-muted">PDF, JPG or PNG. You can attach more than one.</p>
            </div>
          </div>
          {certificates.length > 0 && (
            <div className="mt-4 grid gap-2 sm:grid-cols-2">
              {certificates.map((file, index) => (
                <FilePreview
                  key={file.name + file.size}
                  file={file}
                  label={`Certificate ${index + 1}`}
                  onRemove={() => setCertificates((current) => current.filter((_, i) => i !== index))}
                />
              ))}
            </div>
          )}
          <div className="mt-5">
            <Button onClick={handleSubmit} loading={submitMutation.isPending}>
              Submit application
            </Button>
          </div>
        </Card>
      )}

      {data && !canApply && !hasPending && attemptsLeft <= 0 && (
        <Alert>You have used all {data.maxAttempts} of your application attempts.</Alert>
      )}

      {data && data.applications.length > 0 && (
        <div className="space-y-2">
          <h2 className="font-semibold text-ink">Your applications</h2>
          <div className="stagger space-y-2">
            {data.applications.map((application) => (
              <Card key={application.id} interactive className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2">
                    <p className="font-medium text-ink">{application.title}</p>
                    <Badge tone={toneForStatus(application.status)}>{application.status}</Badge>
                  </div>
                  <p className="text-sm text-muted">
                    {labelFor(application.specialty)} · {application.yearsOfExperience} years
                  </p>
                  <ul className="mt-1 space-y-0.5">
                    {application.certificates.map((certificate) => (
                      <li key={certificate.id} className="flex items-center gap-1.5 text-xs text-muted">
                        <FileText className="size-3.5" aria-hidden="true" />
                        {certificate.originalFileName}
                        <Badge tone={certificateTone[certificate.status]}>{certificateLabel[certificate.status]}</Badge>
                        {application.status === 'PENDING' && certificate.status !== 'PROCESSING' && (
                          <button
                            type="button"
                            onClick={() => {
                              setReplaceError(null)
                              setReplaceTarget(certificate)
                            }}
                            className="ml-1 inline-flex cursor-pointer items-center gap-1 rounded-md px-1.5 py-0.5 text-royal transition-colors hover:bg-royal/10"
                          >
                            <RefreshCw className="size-3" aria-hidden="true" />
                            Replace
                          </button>
                        )}
                      </li>
                    ))}
                  </ul>
                  {application.rejectionReason && (
                    <p className="mt-1 text-xs text-danger">Rejected: {application.rejectionReason}</p>
                  )}
                </div>
                <p className="text-xs text-muted">Submitted {formatDate(application.submittedAt)}</p>
              </Card>
            ))}
          </div>
        </div>
      )}

      {replaceTarget && (
        <ReplaceFileModal
          title="Replace certificate"
          description={`Current file: ${replaceTarget.originalFileName}. The new file is checked again before an admin can approve your application.`}
          accept={CERTIFICATE_TYPES}
          hint="PDF, JPG or PNG"
          pending={replaceMutation.isPending}
          error={replaceError}
          onClose={() => setReplaceTarget(null)}
          onSubmit={(file) => replaceMutation.mutate({ id: replaceTarget.id, file })}
        />
      )}
    </div>
  )
}
