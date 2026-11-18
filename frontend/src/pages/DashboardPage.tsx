import { Link } from 'react-router-dom'
import {
  ArrowRight,
  Bell,
  BriefcaseBusiness,
  Building2,
  CalendarClock,
  CalendarRange,
  FileCheck2,
  Lock,
  ShieldCheck,
  UserRound,
  UserX,
  Users,
  Zap,
} from 'lucide-react'
import { useAuth } from '@/lib/AuthContext'
import { cn } from '@/lib/cn'

interface Tile {
  to: string
  label: string
  hint: string
  icon: typeof Users
}

const tilesByRole: Record<string, Tile[]> = {
  CLIENT: [
    { to: '/advisors', label: 'Find an advisor', hint: 'Browse advisors by specialty and book a slot', icon: Users },
    { to: '/appointments', label: 'My appointments', hint: 'View, cancel or track your bookings', icon: CalendarClock },
    { to: '/kyc', label: 'KYC documents', hint: 'Submit and track identity verification', icon: ShieldCheck },
    { to: '/become-advisor', label: 'Become an advisor', hint: 'Apply with your certificates', icon: BriefcaseBusiness },
  ],
  ADVISOR: [
    { to: '/slots', label: 'My availability', hint: 'Open new slots or remove unbooked ones', icon: CalendarRange },
    { to: '/appointments', label: 'My appointments', hint: 'Confirm, cancel and mark no-shows', icon: CalendarClock },
    { to: '/profile', label: 'Advisor profile', hint: 'Update your title, specialty and experience', icon: UserRound },
  ],
  ADMIN: [
    { to: '/admin', label: 'Admin console', hint: 'Create advisors, deactivate accounts, audit log', icon: Building2 },
    { to: '/kyc-review', label: 'KYC review', hint: 'Approve or reject submitted documents', icon: ShieldCheck },
    { to: '/advisor-applications', label: 'Advisor applications', hint: 'Review applications and their certificates', icon: BriefcaseBusiness },
    { to: '/no-shows', label: 'No-shows', hint: 'Excuse a no-show and remove the strike', icon: UserX },
  ],
}

const heroByRole: Record<string, { lead: string; cta: string; to: string }> = {
  CLIENT: { lead: 'Expert financial guidance, booked in minutes.', cta: 'Find an advisor', to: '/advisors' },
  ADVISOR: { lead: 'Your clients and availability in one place.', cta: 'Manage availability', to: '/slots' },
  ADMIN: { lead: 'Keep the platform safe, verified and accountable.', cta: 'Open admin console', to: '/admin' },
}

const focus = [
  { icon: FileCheck2, title: 'Verified', text: 'Every advisor and KYC document is checked before it counts.' },
  { icon: Lock, title: 'Secure', text: 'Uploads are hashed and scanned, accounts are token protected.' },
  { icon: Zap, title: 'Real time', text: 'Bookings and reviews reach you the moment they happen.' },
  { icon: CalendarClock, title: 'Fair booking', text: 'A slot can only ever go to one client, even under load.' },
]

// buildings for the hero skyline: x, width, height (viewBox is 640 x 260)
const buildings = [
  [0, 46, 110], [50, 38, 150], [92, 60, 95], [156, 44, 185], [204, 70, 130], [278, 40, 210],
  [322, 64, 160], [390, 48, 235], [442, 72, 175], [518, 42, 140], [564, 76, 200],
]

function Skyline() {
  return (
    <svg
      viewBox="0 0 640 260"
      preserveAspectRatio="xMaxYMax meet"
      className="pointer-events-none absolute bottom-0 right-0 h-[85%] w-auto max-w-none opacity-40 sm:opacity-70"
      aria-hidden="true"
    >
      {buildings.map(([x, w, h], b) => {
        const cols = Math.floor((w - 8) / 10)
        const rows = Math.floor((h - 14) / 14)
        return (
          <g key={b}>
            <rect x={x} y={260 - h} width={w} height={h} className="fill-white/[0.05] stroke-white/15" strokeWidth="1" />
            {Array.from({ length: rows }).flatMap((_, r) =>
              Array.from({ length: cols }).map((_, c) => {
                // a fixed pattern so some windows look lit without being random on every render
                const lit = (b * 7 + r * 3 + c * 5) % 6 === 0
                return (
                  <rect
                    key={`${r}-${c}`}
                    x={x + 6 + c * 10}
                    y={260 - h + 10 + r * 14}
                    width="5"
                    height="7"
                    className={lit ? 'fill-gold/70' : 'fill-white/[0.07]'}
                  />
                )
              }),
            )}
          </g>
        )
      })}
    </svg>
  )
}

export function DashboardPage() {
  const { user } = useAuth()
  if (!user) return null

  const tiles = tilesByRole[user.role] ?? []
  const hero = heroByRole[user.role]

  return (
    <div className="space-y-12">
      {/* full width: breaks out of the centered main column */}
      <section className="relative left-1/2 -mt-8 w-screen -translate-x-1/2 overflow-hidden gradient-band text-white">
        <div
          className="pointer-events-none absolute inset-0"
          style={{
            backgroundImage:
              'radial-gradient(circle at 80% 10%, rgba(201,162,75,0.22), transparent 40%), radial-gradient(circle at 5% 100%, rgba(255,255,255,0.07), transparent 45%)',
          }}
          aria-hidden="true"
        />
        <Skyline />

        <div className="relative mx-auto max-w-6xl px-4 pb-28 pt-14 sm:px-6 sm:pt-20">
          <div className="max-w-xl animate-fade-in-up">
            <p className="mb-3 text-xs font-semibold uppercase tracking-[0.25em] text-gold">FinConsult</p>
            <h1 className="text-4xl font-semibold leading-tight sm:text-5xl">Welcome back, {user.firstName}</h1>
            {hero && <p className="mt-4 text-base text-white/75">{hero.lead}</p>}
            <div className="mt-7 flex flex-wrap gap-3">
              {hero && (
                <Link
                  to={hero.to}
                  className="inline-flex items-center gap-2 rounded-lg bg-gold px-5 py-2.5 text-sm font-semibold text-navy shadow-card transition-all duration-150 hover:brightness-105 active:scale-[0.98]"
                >
                  {hero.cta}
                  <ArrowRight className="size-4" aria-hidden="true" />
                </Link>
              )}
              <Link
                to="/notifications"
                className="inline-flex items-center gap-2 rounded-lg border border-white/40 px-5 py-2.5 text-sm font-semibold text-white transition-colors duration-150 hover:border-white hover:bg-white/10"
              >
                <Bell className="size-4" aria-hidden="true" />
                Notifications
              </Link>
            </div>
          </div>

          <div
            className={cn(
              'stagger mt-14 grid gap-4 sm:grid-cols-2',
              tiles.length >= 4 ? 'lg:grid-cols-4' : 'lg:grid-cols-3',
            )}
          >
            {tiles.map(({ to, label, hint, icon: Icon }) => (
              <Link
                key={to + label}
                to={to}
                className="group flex min-h-52 flex-col rounded-2xl border border-white/35 bg-navy/30 p-6 backdrop-blur-sm transition-all duration-300 ease-out-expo hover:-translate-y-1 hover:border-gold hover:bg-navy/60"
              >
                <Icon
                  className="mb-5 size-10 text-white transition-colors duration-300 group-hover:text-gold"
                  strokeWidth={1.25}
                  aria-hidden="true"
                />
                <h3 className="font-serif text-lg font-semibold">{label}</h3>
                <p className="mt-2 text-sm leading-relaxed text-white/70">{hint}</p>
                <span className="mt-auto inline-flex items-center gap-1 pt-4 text-xs font-semibold uppercase tracking-wider text-gold opacity-0 transition-opacity duration-300 group-hover:opacity-100 group-focus-visible:opacity-100">
                  Open
                  <ArrowRight className="size-3.5 transition-transform duration-300 group-hover:translate-x-0.5" aria-hidden="true" />
                </span>
              </Link>
            ))}
          </div>
        </div>

        {/* curved edge into the page, like the hero in the reference design */}
        <svg
          className="absolute inset-x-0 bottom-0 h-14 w-full text-canvas"
          viewBox="0 0 1440 80"
          preserveAspectRatio="none"
          aria-hidden="true"
        >
          <path d="M0 80C420 10 1020 10 1440 80Z" fill="currentColor" />
        </svg>
      </section>

      <section className="animate-fade-in-up">
        <p className="mb-8 text-center text-sm font-semibold uppercase tracking-[0.25em] text-gold-deep">Why FinConsult</p>
        <div className="grid gap-8 sm:grid-cols-2 lg:grid-cols-4 lg:gap-0 lg:divide-x lg:divide-hairline">
          {focus.map(({ icon: Icon, title, text }) => (
            <div key={title} className="px-6 text-center">
              <Icon className="mx-auto mb-3 size-9 text-royal" strokeWidth={1.25} aria-hidden="true" />
              <h3 className="font-serif text-lg font-semibold text-ink">{title}</h3>
              <p className="mt-2 text-sm text-muted">{text}</p>
            </div>
          ))}
        </div>
      </section>
    </div>
  )
}
