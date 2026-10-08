import { Link } from 'react-router-dom'
import { Building2, CalendarClock, ShieldCheck, Users } from 'lucide-react'
import { useAuth } from '@/lib/AuthContext'
import { Card } from '@/components/ui/Card'

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
  ],
  ADVISOR: [
    { to: '/slots', label: 'My availability', hint: 'Open new slots or remove unbooked ones', icon: CalendarClock },
    { to: '/appointments', label: 'My appointments', hint: 'Confirm bookings and mark no-shows', icon: CalendarClock },
  ],
  ADMIN: [
    { to: '/admin', label: 'Admin console', hint: 'Create advisors, deactivate accounts, audit log', icon: Building2 },
    { to: '/kyc-review', label: 'KYC review', hint: 'Approve or reject submitted documents', icon: ShieldCheck },
  ],
}

export function DashboardPage() {
  const { user } = useAuth()
  if (!user) return null

  const tiles = tilesByRole[user.role] ?? []

  return (
    <div className="space-y-6">
      <div className="animate-fade-in-up">
        <h1 className="text-2xl font-semibold text-ink">Welcome back, {user.firstName}</h1>
        <p className="text-sm text-muted">Here's what you can do today.</p>
      </div>

      <div className="stagger grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {tiles.map(({ to, label, hint, icon: Icon }) => (
          <Link key={to} to={to} className="group">
            <Card interactive className="h-full">
              <div className="mb-3 flex size-10 items-center justify-center rounded-xl bg-royal/10 text-royal transition-colors duration-200 group-hover:bg-royal group-hover:text-white">
                <Icon className="size-5" aria-hidden="true" />
              </div>
              <h2 className="font-semibold text-ink">{label}</h2>
              <p className="mt-1 text-sm text-muted">{hint}</p>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  )
}
