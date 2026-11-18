import type { ReactNode } from 'react'

interface PageHeaderProps {
  title: ReactNode
  description?: ReactNode
  /** buttons on the right of the title, they sit on the navy band */
  actions?: ReactNode
  /** small line above the title, for example a back link */
  eyebrow?: ReactNode
  /** extra content at the bottom of the band, for example tabs or summary tiles */
  children?: ReactNode
}

// the navy band at the top of every page, the same style as the dashboard hero. It breaks out of the centered
// main column to the full screen width and ends in a curve into the page
export function PageHeader({ title, description, actions, eyebrow, children }: PageHeaderProps) {
  return (
    <section className="relative left-1/2 -mt-8 w-screen -translate-x-1/2 overflow-hidden gradient-band text-white">
      <div
        className="pointer-events-none absolute inset-0"
        style={{ backgroundImage: 'radial-gradient(circle at 85% 0%, rgba(201,162,75,0.18), transparent 40%)' }}
        aria-hidden="true"
      />

      <div className="relative mx-auto max-w-6xl px-4 pb-14 pt-10 sm:px-6">
        {eyebrow && <div className="mb-3 text-sm text-white/70">{eyebrow}</div>}
        <div className="flex flex-wrap items-end justify-between gap-4 animate-fade-in-up">
          <div className="min-w-0">
            <h1 className="text-3xl font-semibold leading-tight">{title}</h1>
            {description && <p className="mt-2 max-w-2xl text-sm text-white/75">{description}</p>}
          </div>
          {actions && <div className="flex flex-wrap items-center gap-3">{actions}</div>}
        </div>
        {children && <div className="mt-8">{children}</div>}
      </div>

      <svg
        className="absolute inset-x-0 bottom-0 h-8 w-full text-canvas"
        viewBox="0 0 1440 80"
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <path d="M0 80C420 10 1020 10 1440 80Z" fill="currentColor" />
      </svg>
    </section>
  )
}
