import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link } from 'react-router-dom'
import { forgetPassword } from '@/api/auth'
import { errorMessage } from '@/lib/errors'
import { AuthShell } from '@/components/layout/AuthShell'
import { Input } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Alert } from '@/components/ui/Alert'

const schema = z.object({
  email: z.string().min(1, 'Email is required').email('Enter a valid email'),
})

type FormValues = z.infer<typeof schema>

export function ForgotPasswordPage() {
  const [error, setError] = useState<string | null>(null)
  const [sent, setSent] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  async function onSubmit(values: FormValues) {
    setError(null)
    try {
      await forgetPassword(values)
      setSent(true)
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <AuthShell title="Reset your password" subtitle="We'll email you a link to get back in">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        {error && <Alert>{error}</Alert>}
        {sent && <Alert variant="success">If that email is registered, a reset link is on its way. Check your inbox.</Alert>}

        <Input label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />

        <Button type="submit" fullWidth loading={isSubmitting}>
          Send reset link
        </Button>

        <Link to="/login" className="block text-center text-sm text-royal transition-colors hover:text-royal/80 hover:underline">
          Back to login
        </Link>
      </form>
    </AuthShell>
  )
}
