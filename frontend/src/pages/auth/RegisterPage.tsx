import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link } from 'react-router-dom'
import { MailCheck } from 'lucide-react'
import { useAuth } from '@/lib/AuthContext'
import { errorMessage } from '@/lib/errors'
import { AuthShell } from '@/components/layout/AuthShell'
import { Input } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Alert } from '@/components/ui/Alert'

const schema = z.object({
  firstName: z.string().min(1, 'First name is required'),
  lastName: z.string().min(1, 'Last name is required'),
  email: z.string().min(1, 'Email is required').email('Enter a valid email'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
})

type FormValues = z.infer<typeof schema>

export function RegisterPage() {
  const { register: registerAccount } = useAuth()
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  async function onSubmit(values: FormValues) {
    setError(null)
    try {
      await registerAccount(values)
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
            <MailCheck className="size-6" aria-hidden="true" />
          </div>
          <h1 className="mb-2 text-lg font-semibold text-ink">Check your email</h1>
          <p className="text-sm text-muted">
            We sent a verification link to your inbox. Verify your email to activate your account, then log in.
          </p>
          <Link to="/login" className="mt-4 inline-block text-sm text-royal transition-colors hover:text-royal/80 hover:underline">
            Back to login
          </Link>
        </div>
      </AuthShell>
    )
  }

  return (
    <AuthShell title="Create your account" subtitle="Book advisors through FinConsult" tabs>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        {error && <Alert>{error}</Alert>}

        <div className="grid grid-cols-2 gap-3">
          <Input label="First name" autoComplete="given-name" error={errors.firstName?.message} {...register('firstName')} />
          <Input label="Last name" autoComplete="family-name" error={errors.lastName?.message} {...register('lastName')} />
        </div>
        <Input label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <Input
          label="Password"
          type="password"
          autoComplete="new-password"
          hint="At least 8 characters"
          error={errors.password?.message}
          {...register('password')}
        />

        <Button type="submit" fullWidth loading={isSubmitting}>
          Create account
        </Button>

        <p className="text-center text-sm text-muted">
          Already have an account?{' '}
          <Link to="/login" className="text-royal transition-colors hover:text-royal/80 hover:underline">
            Log in
          </Link>
        </p>
      </form>
    </AuthShell>
  )
}
