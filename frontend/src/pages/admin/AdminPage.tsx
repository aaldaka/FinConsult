import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { UserX } from 'lucide-react'
import { createAdvisor } from '@/api/admin'
import type { Specialty } from '@/api/types'
import { Card } from '@/components/ui/Card'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Button } from '@/components/ui/Button'
import { Alert } from '@/components/ui/Alert'
import { errorMessage } from '@/lib/errors'
import { SPECIALTIES, labelFor } from '@/lib/options'
import { DeactivatePanel } from '@/pages/admin/DeactivatePanel'
import { AuditLogList } from '@/pages/admin/AuditLogList'

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
  const queryClient = useQueryClient()
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
      queryClient.invalidateQueries({ queryKey: ['admin', 'advisors'] })
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

export function AdminPage() {
  const navigate = useNavigate()
  return (
    <div className="stagger space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-ink">Admin console</h1>
          <p className="text-sm text-muted">Create advisor accounts, deactivate users, and review the audit trail.</p>
        </div>
        <Button variant="secondary" size="sm" onClick={() => navigate('/no-shows')}>
          <UserX className="size-4" aria-hidden="true" />
          Review no-shows
        </Button>
      </div>

      <CreateAdvisorCard />
      <DeactivatePanel />
      <AuditLogList />
    </div>
  )
}
