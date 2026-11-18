import { useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getMyProfile, updateMyProfile, updateMyPicture } from '@/api/profile'
import { changePassword } from '@/api/auth'
import { getMyAdvisorProfile, updateMyAdvisorProfile } from '@/api/advisors'
import { useAuth } from '@/lib/AuthContext'
import { Card } from '@/components/ui/Card'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Button } from '@/components/ui/Button'
import { Spinner } from '@/components/ui/Spinner'
import { Alert } from '@/components/ui/Alert'
import { Badge } from '@/components/ui/Badge'
import { FilePreview } from '@/components/ui/FilePreview'
import { errorMessage } from '@/lib/errors'
import { pictureUrl } from '@/lib/format'
import { SPECIALTIES, labelFor } from '@/lib/options'
import type { Specialty, UserRole } from '@/api/types'

const roleBadge: Record<UserRole, { label: string; tone: 'royal' | 'gold' | 'neutral' }> = {
  ADMIN: { label: 'Admin', tone: 'gold' },
  ADVISOR: { label: 'Advisor', tone: 'royal' },
  CLIENT: { label: 'Client', tone: 'neutral' },
}

interface ProfileFormValues {
  firstName: string
  lastName: string
  phone: string
  dateOfBirth: string
}

function ProfileDetailsCard() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const { data, isLoading } = useQuery({ queryKey: ['profile', 'me'], queryFn: getMyProfile })
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [pendingPicture, setPendingPicture] = useState<File | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  function clearPendingPicture() {
    setPendingPicture(null)
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  const { register, handleSubmit, reset } = useForm<ProfileFormValues>({
    values: data
      ? {
          firstName: data.firstName,
          lastName: data.lastName,
          phone: data.phone ?? '',
          dateOfBirth: data.dateOfBirth ?? '',
        }
      : undefined,
  })

  const updateMutation = useMutation({
    mutationFn: (values: ProfileFormValues) =>
      updateMyProfile({
        firstName: values.firstName || undefined,
        lastName: values.lastName || undefined,
        phone: values.phone || undefined,
        dateOfBirth: values.dateOfBirth || undefined,
      }),
    onSuccess: (res) => {
      queryClient.setQueryData(['profile', 'me'], res)
      setError(null)
      setSuccess('Profile updated.')
    },
    onError: (err) => setError(errorMessage(err)),
  })

  const pictureMutation = useMutation({
    mutationFn: (file: File) => updateMyPicture(file),
    onSuccess: (res) => {
      queryClient.setQueryData(['profile', 'me'], res)
      setError(null)
      setSuccess('Profile picture updated.')
      clearPendingPicture()
    },
    onError: (err) => setError(errorMessage(err)),
  })

  if (isLoading) return <Spinner />
  if (!data) return null

  return (
    <Card>
      <h2 className="mb-4 font-semibold text-ink">My details</h2>
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

      <div className="mb-6 flex items-center gap-4">
        <img
          src={pictureUrl(data.profilePicturePath)}
          alt="Profile"
          className="size-16 rounded-full border border-hairline object-cover shadow-card transition-transform duration-200 hover:scale-105"
        />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <p className="font-semibold text-ink">
              {data.firstName} {data.lastName}
            </p>
            {user && <Badge tone={roleBadge[user.role].tone}>{roleBadge[user.role].label}</Badge>}
          </div>
          {user && <p className="truncate text-sm text-muted">{user.email}</p>}
        </div>
        <div>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png,image/jpg"
            className="hidden"
            onChange={(e) => {
              setSuccess(null)
              setPendingPicture(e.target.files?.[0] ?? null)
            }}
          />
          <Button variant="secondary" size="sm" onClick={() => fileInputRef.current?.click()}>
            Change picture
          </Button>
        </div>
      </div>

      {pendingPicture && (
        <div className="mb-6 space-y-3 rounded-xl border border-gold/30 bg-gold/5 p-3">
          <p className="text-xs font-medium text-muted">New picture, not saved yet</p>
          <FilePreview file={pendingPicture} onRemove={clearPendingPicture} />
          <div className="flex gap-2">
            <Button size="sm" loading={pictureMutation.isPending} onClick={() => pictureMutation.mutate(pendingPicture)}>
              Save picture
            </Button>
            <Button size="sm" variant="ghost" onClick={clearPendingPicture}>
              Cancel
            </Button>
          </div>
        </div>
      )}

      <form
        onSubmit={handleSubmit((v) => {
          setSuccess(null)
          updateMutation.mutate(v)
        })}
        className="grid gap-3 sm:grid-cols-2"
      >
        <Input label="First name" {...register('firstName')} />
        <Input label="Last name" {...register('lastName')} />
        <Input label="Phone" {...register('phone')} />
        <Input label="Date of birth" type="date" {...register('dateOfBirth')} />
        <div className="sm:col-span-2">
          <Button type="submit" loading={updateMutation.isPending} onClick={() => reset(undefined, { keepValues: true })}>
            Save changes
          </Button>
        </div>
      </form>
    </Card>
  )
}

interface AdvisorFormValues {
  title: string
  yearsOfExperience: number
  specialty: Specialty
}

function AdvisorDetailsCard() {
  const { data, isLoading } = useQuery({ queryKey: ['advisor', 'self'], queryFn: getMyAdvisorProfile })
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  const { register, handleSubmit } = useForm<AdvisorFormValues>({
    values: data ? { title: data.title, yearsOfExperience: data.yearsOfExperience, specialty: data.specialty } : undefined,
  })

  const mutation = useMutation({
    mutationFn: (values: AdvisorFormValues) => updateMyAdvisorProfile(values),
    onSuccess: () => {
      setError(null)
      setSuccess('Advisor profile updated.')
    },
    onError: (err) => setError(errorMessage(err)),
  })

  if (isLoading) return <Spinner />

  return (
    <Card>
      <h2 className="mb-4 font-semibold text-ink">Advisor profile</h2>
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
        <Input label="Title" {...register('title')} />
        <Input label="Years of experience" type="number" min={0} {...register('yearsOfExperience', { valueAsNumber: true })} />
        <div className="sm:col-span-2">
          <Select label="Specialty" {...register('specialty')}>
            {SPECIALTIES.map((s) => (
              <option key={s} value={s}>
                {labelFor(s)}
              </option>
            ))}
          </Select>
        </div>
        <div className="sm:col-span-2">
          <Button type="submit" loading={mutation.isPending}>
            Save advisor details
          </Button>
        </div>
      </form>
    </Card>
  )
}

interface PasswordFormValues {
  currentPassword: string
  newPassword: string
}

function ChangePasswordCard() {
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const { register, handleSubmit, reset } = useForm<PasswordFormValues>()

  const mutation = useMutation({
    mutationFn: (values: PasswordFormValues) => changePassword(values),
    onSuccess: (res) => {
      setError(null)
      setSuccess(res.msg)
      reset()
    },
    onError: (err) => setError(errorMessage(err)),
  })

  return (
    <Card>
      <h2 className="mb-4 font-semibold text-ink">Change password</h2>
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
        <Input label="Current password" type="password" autoComplete="current-password" {...register('currentPassword')} />
        <Input label="New password" type="password" autoComplete="new-password" hint="At least 8 characters" {...register('newPassword')} />
        <div className="sm:col-span-2">
          <Button type="submit" loading={mutation.isPending}>
            Update password
          </Button>
        </div>
      </form>
    </Card>
  )
}

export function ProfilePage() {
  const { user } = useAuth()
  if (!user) return null

  return (
    <div className="stagger max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-ink">Profile</h1>
        <p className="text-sm text-muted">Manage your account details.</p>
      </div>

      <ProfileDetailsCard />
      {user.role === 'ADVISOR' && <AdvisorDetailsCard />}
      <ChangePasswordCard />
    </div>
  )
}
