import {
  Banknote,
  Briefcase,
  Building2,
  Calculator,
  ChartLine,
  Coins,
  Compass,
  HandCoins,
  HeartPulse,
  Hourglass,
  House,
  KeyRound,
  Landmark,
  Palmtree,
  Percent,
  PiggyBank,
  ShieldCheck,
  Store,
  Sunset,
  TrendingUp,
  Umbrella,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import type { Specialty } from '@/api/types'
import { cn } from '@/lib/cn'

interface Art {
  background: string
  main: LucideIcon
  // two smaller icons that sit behind the main one
  extras: [LucideIcon, LucideIcon]
}

const ART: Record<Specialty, Art> = {
  GENERAL: { background: 'specialty-general', main: Compass, extras: [Briefcase, Calculator] },
  LOANS: { background: 'specialty-loans', main: HandCoins, extras: [Banknote, Percent] },
  MORTGAGES: { background: 'specialty-mortgages', main: House, extras: [KeyRound, Landmark] },
  INVESTMENTS: { background: 'specialty-investments', main: TrendingUp, extras: [ChartLine, Coins] },
  RETIREMENT_PLANNING: { background: 'specialty-retirement', main: Sunset, extras: [Palmtree, PiggyBank] },
  INSURANCE: { background: 'specialty-insurance', main: ShieldCheck, extras: [Umbrella, HeartPulse] },
  BUSINESS_BANKING: { background: 'specialty-business', main: Building2, extras: [Store, Hourglass] },
}

// an illustration for a specialty, drawn from icons on the specialty's colour, used on the advisor cards when the
// advisor has no photo
export function SpecialtyArt({ specialty, className }: { specialty: Specialty; className?: string }) {
  const art = ART[specialty] ?? ART.GENERAL
  const Main = art.main
  const [First, Second] = art.extras

  return (
    <div className={cn('relative size-full overflow-hidden', art.background, className)} aria-hidden="true">
      {/* soft gold light in the corner and a faint grid, the same feel as the dashboard hero */}
      <div
        className="absolute inset-0"
        style={{
          backgroundImage:
            'radial-gradient(circle at 85% 15%, rgba(201,162,75,0.35), transparent 45%), linear-gradient(rgba(255,255,255,0.05) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.05) 1px, transparent 1px)',
          backgroundSize: '100% 100%, 28px 28px, 28px 28px',
        }}
      />

      <First className="absolute -left-4 top-6 size-24 rotate-[-12deg] text-white/10" strokeWidth={1.25} />
      <Second className="absolute -bottom-6 right-6 size-28 rotate-[10deg] text-white/10" strokeWidth={1.25} />

      <div className="absolute inset-0 flex items-center justify-center">
        <span className="flex size-24 items-center justify-center rounded-3xl border border-white/25 bg-white/10 shadow-glass backdrop-blur-sm transition-transform duration-500 group-hover:scale-105">
          <Main className="size-12 text-white" strokeWidth={1.5} />
        </span>
      </div>
    </div>
  )
}
