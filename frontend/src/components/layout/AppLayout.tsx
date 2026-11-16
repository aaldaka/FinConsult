import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Bell, BriefcaseBusiness, Building2, CalendarClock, LayoutDashboard, LogOut, ShieldCheck, User as UserIcon, Users } from 'lucide-react'
import { useAuth } from '@/lib/AuthContext'
import { listMyUnreadNotifications } from '@/api/notifications'
import { cn } from '@/lib/cn'
import { initials } from '@/lib/format'
import { useNotificationStream } from '@/lib/useNotificationStream'
import { Logo } from '@/components/ui/Logo'

interface NavItem {
  to: string
  label: string
  icon: typeof LayoutDashboard
}

const navByRole: Record<string, NavItem[]> = {
  CLIENT: [
    { to: '/advisors', label: 'Advisors', icon: Users },
    { to: '/appointments', label: 'My Appointments', icon: CalendarClock },
    { to: '/kyc', label: 'KYC', icon: ShieldCheck },
    { to: '/become-advisor', label: 'Become an Advisor', icon: BriefcaseBusiness },
  ],
  ADVISOR: [
    { to: '/slots', label: 'My Slots', icon: CalendarClock },
    { to: '/appointments', label: 'My Appointments', icon: CalendarClock },
  ],
  ADMIN: [
    { to: '/admin', label: 'Admin', icon: Building2 },
    { to: '/kyc-review', label: 'KYC Review', icon: ShieldCheck },
    { to: '/advisor-applications', label: 'Applications', icon: BriefcaseBusiness },
  ],
}

export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const { data: unread } = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: listMyUnreadNotifications,
    enabled: !!user,
  })

  useNotificationStream(user?.id)

  if (!user) return null

  const items = navByRole[user.role] ?? []

  function handleLogout() {
    logout()
    navigate('/login')
  }

  return (
    <div className="min-h-screen overflow-x-clip bg-canvas">
      <header className="glass-nav sticky top-0 z-10 rounded-b-md text-white shadow-card">
        <div className="flex h-14 items-center gap-6 px-4 sm:px-6 lg:px-8">
          <NavLink to="/" className="group flex items-center gap-2 font-semibold tracking-tight">
            <Logo className="size-8 text-white/90 transition-transform duration-200 group-hover:scale-105" />
            <span className="hidden font-serif text-lg sm:inline">FinConsult</span>
          </NavLink>

          <nav className="flex flex-1 items-center gap-1 overflow-x-auto">
            {items.map(({ to, label, icon: Icon }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  cn(
                    'relative flex items-center gap-1.5 whitespace-nowrap rounded-lg px-3 py-2 text-sm font-medium transition-colors duration-200',
                    isActive ? 'text-white' : 'text-white/70 hover:bg-white/10 hover:text-white',
                  )
                }
              >
                {({ isActive }) => (
                  <>
                    {isActive && (
                      <span className="absolute inset-0 rounded-lg bg-white/15 animate-scale-in" aria-hidden="true" />
                    )}
                    <Icon className="relative size-4" aria-hidden="true" />
                    <span className="relative">{label}</span>
                  </>
                )}
              </NavLink>
            ))}
          </nav>

          <NavLink
            to="/notifications"
            className="relative flex size-9 items-center justify-center rounded-lg text-white/85 transition-colors duration-200 hover:bg-white/10"
            aria-label="Notifications"
          >
            <Bell className="size-5" aria-hidden="true" />
            {!!unread?.length && (
              <span className="absolute -right-0.5 -top-0.5 flex size-4 items-center justify-center rounded-full bg-gold text-[10px] font-bold text-navy shadow-[0_0_0_2px_rgb(11,17,32)]">
                <span className="absolute inset-0 animate-ping rounded-full bg-gold/70" aria-hidden="true" />
                <span className="relative">{unread.length > 9 ? '9+' : unread.length}</span>
              </span>
            )}
          </NavLink>

          <NavLink
            to="/profile"
            className="flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm text-white/90 transition-colors duration-200 hover:bg-white/10"
          >
            <span className="flex size-7 items-center justify-center rounded-full bg-white/15 text-xs font-semibold">
              {initials(user.firstName, user.lastName)}
            </span>
            <span className="hidden items-center gap-1 sm:flex">
              <UserIcon className="size-3.5" aria-hidden="true" />
              {user.firstName}
            </span>
          </NavLink>

          <button
            type="button"
            onClick={handleLogout}
            className="flex size-9 cursor-pointer items-center justify-center rounded-lg text-white/75 transition-colors duration-200 hover:bg-white/10 hover:text-white"
            aria-label="Log out"
            title="Log out"
          >
            <LogOut className="size-5" aria-hidden="true" />
          </button>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
        <Outlet />
      </main>
    </div>
  )
}
