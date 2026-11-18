import { useState } from 'react'
import type { ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'
import { BriefcaseBusiness, ShieldCheck, UserX, Users } from 'lucide-react'
import { createAdvisor } from '@/api/admin'
import { listDocumentsByStatus } from '@/api/kyc'
import { listApplicationsByStatus } from '@/api/advisorApplications'
import { listNoShows } from '@/api/appointments'
import { listAdvisors } from '@/api/advisors'
import type { Specialty } from '@/api/types'
import { Card } from '@/components/ui/Card'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Button } from '@/components/ui/Button'
import { Alert } from '@/components/ui/Alert'
import { PageHeader } from '@/components/layout/PageHeader'
import { cn } from '@/lib/cn'
import { errorMessage } from '@/lib/errors'
import { SPECIALTIES, labelFor } from '@/lib/options'
import { DeactivatePanel } from '@/pages/admin/DeactivatePanel'
import { AuditLogList } from '@/pages/admin/AuditLogList'
import { NoShowsPanel } from '@/pages/admin/NoShowsPage'

const advisorSchema = z.object({
  email: z.string().min(1, 'Email is required').email('Enter a valid email'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
  firstName: z.string().min(1, 'First name is required'),
  lastName: z.string().min(1, 'Last name is required'),
  title: z.string().min(1, 'Title is required'),
  specialty: z.union([z.literal(''), z.string()]).optional(),
  yearsOfExperience: z
    .union([z.literal(''), z.string()])
    .optional()
    .refine((v) => !v || (/^\d+$/.test(v) && Number(v) <= 60), 'Enter whole years from 0 to 60'),
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
    <Card className="mx-auto max-w-3xl p-6">
      <h2 className="font-serif text-xl font-semibold text-ink">Create advisor</h2>
      <p className="mb-5 mt-1 text-sm text-muted">The advisor can log in straight away with this email and password.</p>
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
      {/* noValidate: the schema shows its own messages under each field */}
      <form noValidate onSubmit={handleSubmit((v) => mutation.mutate(v))} className="grid gap-4 sm:grid-cols-2">
        <Input label="First name" required autoComplete="off" error={errors.firstName?.message} {...register('firstName')} />
        <Input label="Last name" required autoComplete="off" error={errors.lastName?.message} {...register('lastName')} />
        <Input
          label="Email"
          type="email"
          required
          autoComplete="off"
          placeholder="name@finconsult.com"
          error={errors.email?.message}
          {...register('email')}
        />
        <Input
          label="Password"
          type="password"
          required
          autoComplete="new-password"
          placeholder="At least 8 characters"
          error={errors.password?.message}
          {...register('password')}
        />
        <Input label="Title" required placeholder="Senior Financial Advisor" error={errors.title?.message} {...register('title')} />
        <Input
          label="Years of experience"
          type="number"
          min={0}
          max={60}
          step={1}
          placeholder="0"
          error={errors.yearsOfExperience?.message}
          {...register('yearsOfExperience')}
        />
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
        <div className="flex justify-end sm:col-span-2">
          <Button type="submit" loading={isSubmitting || mutation.isPending}>
            Create advisor
          </Button>
        </div>
      </form>
    </Card>
  )
}

const TABS = [
  { id: 'advisors', label: 'Advisors' },
  { id: 'accounts', label: 'Accounts' },
  { id: 'no-shows', label: 'No-shows' },
  { id: 'audit', label: 'Audit log' },
] as const

type TabId = (typeof TABS)[number]['id']

interface StatProps {
  label: string
  value: number | undefined
  icon: ReactNode
  to?: string
  onClick?: () => void
}

// one summary tile on the navy band, a link to where the work is
function Stat({ label, value, icon, to, onClick }: StatProps) {
  const body = (
    <>
      <span className="text-white/60 transition-colors group-hover:text-gold">{icon}</span>
      <span>
        <span className="block text-2xl font-semibold tabular">{value ?? '–'}</span>
        <span className="block text-xs text-white/70">{label}</span>
      </span>
    </>
  )
  const className =
    'group flex cursor-pointer items-center gap-3 rounded-xl border border-white/20 bg-white/5 px-4 py-3 text-left transition-colors duration-200 hover:border-gold hover:bg-white/10'
  return to ? (
    <Link to={to} className={className}>
      {body}
    </Link>
  ) : (
    <button type="button" onClick={onClick} className={className}>
      {body}
    </button>
  )
}

export function AdminPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const tab: TabId = TABS.some((t) => t.id === searchParams.get('tab')) ? (searchParams.get('tab') as TabId) : 'advisors'
  const openTab = (id: TabId) => setSearchParams(id === 'advisors' ? {} : { tab: id })

  // size 1: only the total count is needed
  const pendingKyc = useQuery({
    queryKey: ['admin', 'stats', 'kyc'],
    queryFn: () => listDocumentsByStatus({ status: 'PENDING', page: 0, size: 1 }),
  })
  const pendingApplications = useQuery({
    queryKey: ['admin', 'stats', 'applications'],
    queryFn: () => listApplicationsByStatus({ status: 'PENDING', page: 0, size: 1 }),
  })
  const noShows = useQuery({ queryKey: ['appointments', 'no-shows'], queryFn: listNoShows })
  const advisors = useQuery({ queryKey: ['admin', 'advisors'], queryFn: () => listAdvisors() })

  return (
    <div className="space-y-8">
      <PageHeader title="Admin console" description="Create advisor accounts, deactivate users, excuse no-shows and review the audit trail.">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Stat
            label="KYC documents to review"
            value={pendingKyc.data?.totalElements}
            icon={<ShieldCheck className="size-6" aria-hidden="true" />}
            to="/kyc-review"
          />
          <Stat
            label="Applications to review"
            value={pendingApplications.data?.totalElements}
            icon={<BriefcaseBusiness className="size-6" aria-hidden="true" />}
            to="/advisor-applications"
          />
          <Stat
            label="No-shows to review"
            value={noShows.data?.filter((a) => !a.excused).length}
            icon={<UserX className="size-6" aria-hidden="true" />}
            onClick={() => openTab('no-shows')}
          />
          <Stat
            label="Active advisors"
            value={advisors.data?.length}
            icon={<Users className="size-6" aria-hidden="true" />}
            onClick={() => openTab('accounts')}
          />
        </div>

        <div role="tablist" aria-label="Admin sections" className="mt-6 inline-flex flex-wrap gap-1 rounded-full bg-white/10 p-1">
          {TABS.map((t) => (
            <button
              key={t.id}
              type="button"
              role="tab"
              aria-selected={tab === t.id}
              onClick={() => openTab(t.id)}
              className={cn(
                'cursor-pointer rounded-full px-4 py-1.5 text-sm font-medium transition-colors duration-200',
                tab === t.id ? 'bg-white text-navy shadow-card' : 'text-white/75 hover:text-white',
              )}
            >
              {t.label}
            </button>
          ))}
        </div>
      </PageHeader>

      <div role="tabpanel" className="animate-fade-in-up" key={tab}>
        {tab === 'advisors' && <CreateAdvisorCard />}
        {tab === 'accounts' && <DeactivatePanel />}
        {tab === 'no-shows' && <NoShowsPanel />}
        {tab === 'audit' && <AuditLogList />}
      </div>
    </div>
  )
}
