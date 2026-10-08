import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link, useSearchParams } from 'react-router-dom'
import { CheckCircle2 } from 'lucide-react'
import { resetPassword } from '@/api/auth'
import { errorMessage } from '@/lib/errors'
import { AuthShell } from '@/components/layout/AuthShell'
import { Input } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Alert } from '@/components/ui/Alert'

const schema = z
  .object({
    token: z.string().min(1, 'Paste the token from your email'),
    newPassword: z.string().min(8, 'Password must be at least 8 characters'),
    confirmPassword: z.string().min(1, 'Confirm your new password'),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  })

type FormValues = z.infer<typeof schema>

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const tokenFromUrl = searchParams.get('token') ?? ''
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { token: tokenFromUrl, newPassword: '', confirmPassword: '' },
  })

  async function onSubmit(values: FormValues) {
    setError(null)
    try {
      await resetPassword({ token: values.token, newPassword: values.newPassword })
      setDone(true)
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  if (done) {
    return (
      <AuthShell>
        <div className="text-center">
          <div className="mx-auto mb-3 flex size-12 items-center justify-center rounded-full bg-success/10 text-success animate-scale-in">
            <CheckCircle2 className="size-6" aria-hidden="true" />
          </div>
          <p className="text-sm text-ink">Your password has been reset. You can now log in.</p>
          <Link to="/login">
            <Button fullWidth className="mt-4">
              Go to login
            </Button>
          </Link>
        </div>
      </AuthShell>
    )
  }

  return (
    <AuthShell title="Set a new password" subtitle="Choose something you haven't used before">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        {error && <Alert>{error}</Alert>}

        {!tokenFromUrl && (
          <Input
            label="Reset token"
            hint="Paste the token from the email we sent you"
            error={errors.token?.message}
            {...register('token')}
          />
        )}

        <Input
          label="New password"
          type="password"
          autoComplete="new-password"
          hint="At least 8 characters"
          error={errors.newPassword?.message}
          {...register('newPassword')}
        />
        <Input
          label="Confirm new password"
          type="password"
          autoComplete="new-password"
          error={errors.confirmPassword?.message}
          {...register('confirmPassword')}
        />

        <Button type="submit" fullWidth loading={isSubmitting}>
          Reset password
        </Button>

        <Link to="/login" className="block text-center text-sm text-royal transition-colors hover:text-royal/80 hover:underline">
          Back to login
        </Link>
      </form>
    </AuthShell>
  )
}
