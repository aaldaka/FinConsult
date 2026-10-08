import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { createAdvisor, deactivateAdvisor, deactivateUser, listAuditLogs } from '@/api/admin'
import type { Specialty } from '@/api/types'
import { Card } from '@/components/ui/Card'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Button } from '@/components/ui/Button'
import { Textarea } from '@/components/ui/Textarea'
import { Spinner } from '@/components/ui/Spinner'
import { EmptyState } from '@/components/ui/EmptyState'
import { Alert } from '@/components/ui/Alert'
import { errorMessage } from '@/lib/errors'
import { formatDateTime } from '@/lib/format'
import { SPECIALTIES, labelFor } from '@/lib/options'

const advisorSchema = z.object({
  email: z.string().min(1, 'Email is required').email('Enter a valid email'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
  firstName: z.string().min(1, 'First name is required'),
  lastName: z.string().min(1, 'Last name is required'),
  title: z.string().min(1, 'Title is required'),
  specialty: z.union([z.literal(''), z.string()]).optional(),
  yearsOfExperience: z.union([z.literal(''), z.string()]).optional(),
})

type AdvisorFormValues = z.infer<typeof advisorSchema>

function CreateAdvisorCard() {
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<AdvisorFormValues>({ resolver: zodResolver(advisorSchema) })

  const mutation = useMutation({
    mutationFn: (values: AdvisorFormValues) =>
      createAdvisor({
        ...values,
        specialty: (values.specialty || undefined) as Specialty | undefined,
        yearsOfExperience: values.yearsOfExperience ? Number(values.yearsOfExperience) : 0,
      }),
    onSuccess: (advisor) => {
      setError(null)
      setSuccess(`Created advisor account for ${advisor.name}.`)
      reset()
    },
    onError: (err) => {
      setSuccess(null)
      setError(errorMessage(err))
    },
  })

  return (
    <Card>
      <h2 className="mb-4 font-semibold text-ink">Create advisor</h2>
      {error && (
        <div className="mb-4">
          <Alert>{error}</Alert>
        </div>
      )}
      {success && (
        <div className="mb-4">
          <Alert variant="success">{success}</Alert>
        </div>
      )}
      <form onSubmit={handleSubmit((v) => mutation.mutate(v))} className="grid gap-3 sm:grid-cols-2">
        <Input label="First name" error={errors.firstName?.message} {...register('firstName')} />
        <Input label="Last name" error={errors.lastName?.message} {...register('lastName')} />
        <Input label="Email" type="email" error={errors.email?.message} {...register('email')} />
        <Input label="Password" type="password" hint="At least 8 characters" error={errors.password?.message} {...register('password')} />
        <Input label="Title" placeholder="Senior Financial Advisor" error={errors.title?.message} {...register('title')} />
        <Input label="Years of experience" type="number" min={0} {...register('yearsOfExperience')} />
        <div className="sm:col-span-2">
          <Select label="Specialty" {...register('specialty')}>
            <option value="">General</option>
            {SPECIALTIES.map((s) => (
              <option key={s} value={s}>
                {labelFor(s)}
              </option>
            ))}
          </Select>
        </div>
        <div className="sm:col-span-2">
          <Button type="submit" loading={isSubmitting}>
            Create advisor
          </Button>
        </div>
      </form>
    </Card>
  )
}

function DeactivateCard() {
  const [userId, setUserId] = useState('')
  const [advisorId, setAdvisorId] = useState('')
  const [reason, setReason] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const deactivateUserMutation = useMutation({
    mutationFn: (id: number) => deactivateUser(id),
    onSuccess: (res) => {
      setError(null)
      setMessage(res.msg)
      setUserId('')
    },
    onError: (err) => setError(errorMessage(err)),
  })

  const deactivateAdvisorMutation = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) => deactivateAdvisor(id, reason),
    onSuccess: (res) => {
      setError(null)
      setMessage(res.msg)
      setAdvisorId('')
      setReason('')
    },
    onError: (err) => setError(errorMessage(err)),
  })

  return (
    <Card>
      <h2 className="mb-4 font-semibold text-ink">Deactivate accounts</h2>
      {error && (
        <div className="mb-4">
          <Alert>{error}</Alert>
        </div>
      )}
      {message && (
        <div className="mb-4">
          <Alert variant="success">{message}</Alert>
        </div>
      )}

      <div className="grid gap-6 sm:grid-cols-2">
        <div className="space-y-3">
          <Input label="User ID" type="number" value={userId} onChange={(e) => setUserId(e.target.value)} />
          <Button
            variant="danger"
            loading={deactivateUserMutation.isPending}
            disabled={!userId}
            onClick={() => deactivateUserMutation.mutate(Number(userId))}
          >
            Deactivate user
          </Button>
          <p className="text-xs text-muted">For clients. Advisors must use the form on the right.</p>
        </div>

        <div className="space-y-3">
          <Input label="Advisor ID" type="number" value={advisorId} onChange={(e) => setAdvisorId(e.target.value)} />
          <Textarea label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} />
          <Button
            variant="danger"
            loading={deactivateAdvisorMutation.isPending}
            disabled={!advisorId || !reason.trim()}
            onClick={() => deactivateAdvisorMutation.mutate({ id: Number(advisorId), reason })}
          >
            Deactivate advisor
          </Button>
        </div>
      </div>
    </Card>
  )
}

function AuditLogCard() {
  const [page, setPage] = useState(0)
  const { data, isLoading, error } = useQuery({
    queryKey: ['audit-logs', page],
    queryFn: () => listAuditLogs({ page, size: 10 }),
  })

  return (
    <Card>
      <h2 className="mb-4 font-semibold text-ink">Audit log</h2>
      {error && <Alert>{errorMessage(error)}</Alert>}
      {isLoading && <Spinner />}

      {data && data.content.length === 0 && <EmptyState title="No audit entries yet" />}

      {data && data.content.length > 0 && (
        <div className="stagger space-y-2">
          {data.content.map((entry) => (
            <div
              key={entry.id}
              className="rounded-xl border border-hairline px-3 py-2.5 text-sm transition-colors duration-200 hover:border-royal/30"
            >
              <div className="flex items-center justify-between">
                <span className="font-medium text-ink">{labelFor(entry.action)}</span>
                <span className="text-xs text-muted">{formatDateTime(entry.timestamp)}</span>
              </div>
              <p className="text-muted">
                {entry.actorEmail} · {entry.entityType} #{entry.entityId}
              </p>
              {entry.details && <p className="mt-1 text-xs text-muted">{entry.details}</p>}
            </div>
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
    </Card>
  )
}

export function AdminPage() {
  return (
    <div className="stagger space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-ink">Admin console</h1>
        <p className="text-sm text-muted">Create advisor accounts, deactivate users, and review the audit trail.</p>
      </div>

      <CreateAdvisorCard />
      <DeactivateCard />
      <AuditLogCard />
    </div>
  )
}
