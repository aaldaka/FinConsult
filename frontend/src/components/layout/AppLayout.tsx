import { useEffect } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Bell } from 'lucide-react'
import { useAuth } from '@/lib/AuthContext'
import { listMyUnreadNotifications } from '@/api/notifications'
import { cn } from '@/lib/cn'
import { useNotificationStream } from '@/lib/useNotificationStream'
import { Logo } from '@/components/ui/Logo'
import { UserMenu } from '@/components/layout/UserMenu'

interface NavItem {
  to: string
  label: string
}

const navByRole: Record<string, NavItem[]> = {
  CLIENT: [
    { to: '/advisors', label: 'Advisors' },
    { to: '/appointments', label: 'My Appointments' },
    { to: '/kyc', label: 'KYC' },
    { to: '/become-advisor', label: 'Become an Advisor' },
  ],
  ADVISOR: [
    { to: '/slots', label: 'My Slots' },
    { to: '/appointments', label: 'My Appointments' },
  ],
  ADMIN: [
    { to: '/admin', label: 'Admin' },
    { to: '/kyc-review', label: 'KYC Review' },
    { to: '/advisor-applications', label: 'Applications' },
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

  // a new page starts at the top, otherwise the scroll position of the last page hides the title under the nav.
  // Only the path counts, so switching a tab (?tab=, ?view=) keeps the scroll
  const { pathname } = useLocation()
  useEffect(() => {
    window.scrollTo(0, 0)
  }, [pathname])

  if (!user) return null

  const items = navByRole[user.role] ?? []

  function handleLogout() {
    logout()
    navigate('/login')
  }

  return (
    <div className="min-h-screen overflow-x-clip bg-canvas">
      {/* solid navy, the same width as the page content, the active link has a gold underline */}
      <header className="sticky top-0 z-20 bg-navy text-white">
        <div className="mx-auto flex h-16 max-w-6xl items-center gap-8 px-4 sm:px-6">
          <NavLink to="/" className="group flex shrink-0 items-center gap-2 font-semibold tracking-tight">
            <Logo className="size-8 text-white/90 transition-transform duration-200 group-hover:scale-105" />
            <span className="hidden font-serif text-lg sm:inline">FinConsult</span>
          </NavLink>

          <nav className="flex flex-1 items-stretch gap-1 self-stretch overflow-x-auto">
            {items.map(({ to, label }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  cn(
                    'relative flex items-center whitespace-nowrap px-3 text-sm font-medium transition-colors duration-200',
                    isActive ? 'text-white' : 'text-white/65 hover:text-white',
                  )
                }
              >
                {({ isActive }) => (
                  <>
                    {label}
                    {isActive && (
                      <span className="absolute inset-x-3 bottom-0 h-0.5 rounded-full bg-gold animate-scale-in" aria-hidden="true" />
                    )}
                  </>
                )}
              </NavLink>
            ))}
          </nav>

          <div className="flex shrink-0 items-center gap-2">
            <NavLink
              to="/notifications"
              className="relative flex size-9 items-center justify-center rounded-full text-white/85 transition-colors duration-200 hover:bg-white/10"
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

            <UserMenu firstName={user.firstName} lastName={user.lastName} role={user.role} onLogout={handleLogout} />
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
        <Outlet />
      </main>
    </div>
  )
}
