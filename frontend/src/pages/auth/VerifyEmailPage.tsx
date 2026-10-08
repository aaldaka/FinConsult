import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { CheckCircle2, Loader2, XCircle } from 'lucide-react'
import { verifyEmail } from '@/api/auth'
import { errorMessage } from '@/lib/errors'
import { AuthShell } from '@/components/layout/AuthShell'

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const [state, setState] = useState<'loading' | 'done' | 'error'>('loading')
  const [message, setMessage] = useState('')

  useEffect(() => {
    if (!token) {
      setState('error')
      setMessage('Missing verification token.')
      return
    }
    verifyEmail(token)
      .then((msg) => {
        setMessage(msg)
        setState('done')
      })
      .catch((err) => {
        setMessage(errorMessage(err))
        setState('error')
      })
  }, [token])

  return (
    <AuthShell>
      <div className="text-center">
        {state === 'loading' && <Loader2 className="mx-auto size-8 animate-spin text-royal" aria-hidden="true" />}
        {state === 'done' && (
          <CheckCircle2 className="mx-auto size-8 animate-scale-in text-success" aria-hidden="true" />
        )}
        {state === 'error' && <XCircle className="mx-auto size-8 animate-scale-in text-danger" aria-hidden="true" />}

        <p className="mt-4 text-sm text-ink">{state === 'loading' ? 'Verifying your email…' : message}</p>

        {state !== 'loading' && (
          <Link to="/login" className="mt-4 inline-block text-sm text-royal transition-colors hover:text-royal/80 hover:underline">
            Go to login
          </Link>
        )}
      </div>
    </AuthShell>
  )
}
