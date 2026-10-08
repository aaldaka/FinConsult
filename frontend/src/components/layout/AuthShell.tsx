import type { ReactNode } from 'react'

/**
 * Shared chrome for the public auth screens (login, register, reset, verify):
 * a soft navy radial backdrop behind a centered glass-edged card, with a brief
 * entrance animation so the first screen someone sees doesn't feel static.
 */
export function AuthShell({ title, subtitle, children }: { title?: string; subtitle?: string; children: ReactNode }) {
  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-canvas px-4 py-10">
      <div
        className="pointer-events-none absolute inset-0 opacity-90"
        style={{
          backgroundImage:
            'radial-gradient(circle at 15% 15%, rgba(30,58,138,0.10), transparent 45%), radial-gradient(circle at 85% 85%, rgba(201,162,75,0.12), transparent 40%)',
        }}
        aria-hidden="true"
      />

      <div className="relative w-full max-w-sm animate-fade-in-up">
        {(title || subtitle) && (
          <div className="mb-8 flex flex-col items-center gap-2 text-center">
            <span className="flex size-12 items-center justify-center rounded-2xl gradient-navy text-lg font-bold text-gold shadow-glass">
              FC
            </span>
            {title && <h1 className="text-xl font-semibold text-ink">{title}</h1>}
            {subtitle && <p className="text-sm text-muted">{subtitle}</p>}
          </div>
        )}

        <div className="rounded-2xl border border-hairline bg-surface/90 p-6 shadow-lifted backdrop-blur-sm">{children}</div>
      </div>
    </div>
  )
}
