import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import { CalendarCheck, ShieldCheck, TrendingUp } from 'lucide-react'
import { Logo } from '@/components/ui/Logo'
import { cn } from '@/lib/cn'

const YEAR = new Date().getFullYear()

/**
 * Shared chrome for the public auth screens (login, register, reset, verify): one wide card on a deep blue page.
 * On large screens the left half is a blue panel with a small preview of the app and its promise; the right half
 * holds the form. On small screens only the form shows. `tabs` adds the Sign in / Sign up switch.
 */
export function AuthShell({
  title,
  subtitle,
  tabs,
  children,
}: {
  title?: string
  subtitle?: string
  tabs?: boolean
  children: ReactNode
}) {
  return (
    <div className="gradient-navy-soft flex min-h-screen items-center justify-center px-4 py-10">
      <div className="grid w-full max-w-5xl overflow-hidden rounded-3xl bg-canvas p-3 shadow-glass animate-fade-in-up lg:grid-cols-2">
        <ShowcasePanel />

        <div className="flex flex-col px-4 py-8 sm:px-10 lg:px-14">
          <div className="flex flex-1 flex-col justify-center">
            <div className="mx-auto w-full max-w-sm">
              <div className="mb-6 flex flex-col items-center gap-3 text-center">
                <Logo className="size-14 text-royal" />
                {title && <h1 className="text-3xl font-semibold text-ink">{title}</h1>}
                {subtitle && <p className="text-sm text-muted">{subtitle}</p>}
              </div>

              {tabs && <AuthTabs />}

              {children}
            </div>
          </div>

          <p className="mt-8 text-center text-xs text-muted">© {YEAR} FinConsult</p>
        </div>
      </div>
    </div>
  )
}

function AuthTabs() {
  const tab = ({ isActive }: { isActive: boolean }) =>
    cn(
      'flex-1 rounded-lg py-2 text-center text-sm font-medium transition-colors duration-200',
      isActive ? 'bg-surface text-ink shadow-card' : 'text-muted hover:text-ink',
    )
  return (
    <div className="mb-6 flex gap-1 rounded-xl bg-hairline/70 p-1">
      <NavLink to="/login" className={tab}>
        Sign in
      </NavLink>
      <NavLink to="/register" className={tab}>
        Sign up
      </NavLink>
    </div>
  )
}

// the left half: a few floating cards that look like the app, over a blue gradient, and the promise underneath
function ShowcasePanel() {
  return (
    <div className="relative hidden flex-col justify-end overflow-hidden rounded-2xl gradient-auth p-10 text-white lg:flex">
      <div
        className="pointer-events-none absolute inset-0 opacity-60"
        style={{
          backgroundImage:
            'radial-gradient(circle at 20% 15%, rgba(255,255,255,0.18), transparent 40%), radial-gradient(circle at 90% 60%, rgba(255,255,255,0.12), transparent 35%)',
        }}
        aria-hidden="true"
      />

      <div className="relative mb-10 h-72" aria-hidden="true">
        {/* the next consultation */}
        <div className="absolute left-6 right-4 top-10 rounded-2xl bg-white p-5 text-ink shadow-glass">
          <div className="flex items-center justify-between">
            <p className="text-xs font-medium uppercase tracking-wide text-muted">Next consultation</p>
            <span className="rounded-full bg-success/10 px-2 py-0.5 text-[11px] font-semibold text-success">Confirmed</span>
          </div>
          <div className="mt-3 flex items-center gap-3">
            <span className="flex size-11 items-center justify-center rounded-full bg-royal/10 font-semibold text-royal">RM</span>
            <div>
              <p className="font-semibold">Reem Al-Mannai</p>
              <p className="text-xs text-muted">Financial Planner · 7 years</p>
            </div>
          </div>
          <div className="mt-4 flex items-center gap-2 rounded-xl bg-canvas px-3 py-2 text-sm">
            <CalendarCheck className="size-4 text-royal" />
            <span className="font-medium">Tue, 10:00</span>
            <span className="text-muted">· Retirement review</span>
          </div>
        </div>

        {/* savings goal */}
        <div className="absolute -left-2 top-0 w-44 -rotate-3 rounded-2xl bg-white p-3.5 text-ink shadow-lifted">
          <p className="text-[11px] font-medium text-muted">Savings goal</p>
          <p className="mt-0.5 text-lg font-bold">BHD 12,400</p>
          <div className="mt-2 h-1.5 rounded-full bg-hairline">
            <div className="h-1.5 w-3/4 rounded-full bg-royal" />
          </div>
          <p className="mt-1.5 flex items-center gap-1 text-[11px] font-medium text-success">
            <TrendingUp className="size-3" /> 75% reached
          </p>
        </div>

        {/* kyc */}
        <div className="absolute bottom-2 right-0 flex w-52 rotate-2 items-center gap-3 rounded-2xl bg-white p-3.5 text-ink shadow-lifted">
          <span className="flex size-9 items-center justify-center rounded-xl bg-success/10 text-success">
            <ShieldCheck className="size-5" />
          </span>
          <div>
            <p className="text-sm font-semibold">Identity verified</p>
            <p className="text-[11px] text-muted">KYC approved</p>
          </div>
        </div>
      </div>

      <div className="relative text-center">
        <h2 className="font-serif text-3xl font-semibold">Plan smarter. Decide with confidence.</h2>
        <p className="mx-auto mt-3 max-w-sm text-sm text-white/85">
          Book trusted financial advisors, keep your documents verified and follow every consultation in one place.
        </p>
        <div className="mt-6 flex justify-center gap-1.5" aria-hidden="true">
          <span className="h-1.5 w-6 rounded-full bg-white" />
          <span className="size-1.5 rounded-full bg-white/50" />
          <span className="size-1.5 rounded-full bg-white/50" />
        </div>
      </div>
    </div>
  )
}
