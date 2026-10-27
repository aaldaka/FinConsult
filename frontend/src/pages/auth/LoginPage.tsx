import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '@/lib/AuthContext'
import { errorMessage } from '@/lib/errors'
import { AuthShell } from '@/components/layout/AuthShell'
import { Input } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Alert } from '@/components/ui/Alert'

const schema = z.object({
  email: z.string().min(1, 'Email is required').email('Enter a valid email'),
  password: z.string().min(1, 'Password is required'),
})

type FormValues = z.infer<typeof schema>

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const [error, setError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const expired = searchParams.get('expired') === '1'
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  async function onSubmit(values: FormValues) {
    setError(null)
    try {
      await login(values)
      navigate(from, { replace: true })
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <AuthShell title="Welcome back" subtitle="Log in to FinConsult">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        {expired && <Alert>Your session has ended, please log in again.</Alert>}
        {error && <Alert>{error}</Alert>}

        <Input label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <Input
          label="Password"
          type="password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...register('password')}
        />

        <Button type="submit" fullWidth loading={isSubmitting}>
          Log in
        </Button>

        <div className="flex items-center justify-between text-sm">
          <Link to="/forgot-password" className="text-royal transition-colors hover:text-royal/80 hover:underline">
            Forgot password?
          </Link>
          <Link to="/register" className="text-royal transition-colors hover:text-royal/80 hover:underline">
            Create account
          </Link>
        </div>
      </form>
    </AuthShell>
  )
}
