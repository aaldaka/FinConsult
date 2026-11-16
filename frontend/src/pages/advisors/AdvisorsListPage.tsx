import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Award, Briefcase, CalendarCheck, UserRound, Users } from 'lucide-react'
import { listAdvisors } from '@/api/advisors'
import type { AdvisorResponse, Specialty } from '@/api/types'
import { Select } from '@/components/ui/Select'
import { Spinner } from '@/components/ui/Spinner'
import { EmptyState } from '@/components/ui/EmptyState'
import { Alert } from '@/components/ui/Alert'
import { Logo } from '@/components/ui/Logo'
import { errorMessage } from '@/lib/errors'
import { initials, pictureUrl } from '@/lib/format'
import { SPECIALTIES, labelFor } from '@/lib/options'

// the top of a card: the advisor's photo, or their initials on the brand blue until they upload one
function AdvisorPhoto({ advisor }: { advisor: AdvisorResponse }) {
  const [broken, setBroken] = useState(false)
  const [first, ...rest] = advisor.name.split(' ')

  return (
    <div className="relative aspect-[4/3] overflow-hidden bg-canvas">
      {advisor.profilePicturePath && !broken ? (
        <img
          src={pictureUrl(advisor.profilePicturePath)}
          alt={advisor.name}
          loading="lazy"
          onError={() => setBroken(true)}
          className="size-full object-cover object-top transition-transform duration-500 group-hover:scale-[1.03]"
        />
      ) : (
        <div className="gradient-auth relative flex size-full items-center justify-center">
          <Logo className="absolute -bottom-6 -right-6 size-36 text-white/10" title="" />
          <span className="font-serif text-5xl font-semibold tracking-wide text-white">{initials(first, rest.join(' '))}</span>
        </div>
      )}

      <span className="absolute bottom-3 left-3 inline-flex items-center gap-1 rounded-lg bg-success px-2 py-1 text-xs font-semibold text-white shadow-card">
        <Award className="size-3.5" aria-hidden="true" />
        {advisor.yearsOfExperience} yrs
      </span>
    </div>
  )
}

function AdvisorCard({ advisor }: { advisor: AdvisorResponse }) {
  const link = `/advisors/${advisor.id}`
  return (
    <article className="group card-interactive flex flex-col overflow-hidden rounded-2xl border border-hairline bg-surface shadow-card">
      <Link to={link} aria-label={`View ${advisor.name}`}>
        <AdvisorPhoto advisor={advisor} />
      </Link>

      <div className="flex flex-1 flex-col px-5 pb-5 pt-4">
        <span className="w-fit rounded-md bg-royal/8 px-2 py-1 text-[11px] font-semibold uppercase tracking-wide text-royal">
          {labelFor(advisor.specialty)}
        </span>
        <h2 className="mt-3 text-lg font-bold text-ink">{advisor.name}</h2>
        <p className="text-sm text-muted">{advisor.title}</p>
        <p className="mt-3 flex items-center gap-1.5 text-sm text-muted">
          <Briefcase className="size-4 text-royal" aria-hidden="true" />
          {advisor.yearsOfExperience} years of experience
        </p>
      </div>

      <div className="grid grid-cols-2 border-t border-hairline text-sm font-medium">
        <Link
          to={link}
          className="flex items-center justify-center gap-1.5 py-3 text-muted transition-colors duration-200 hover:bg-canvas hover:text-ink"
        >
          <UserRound className="size-4 text-royal" aria-hidden="true" />
          View profile
        </Link>
        <Link
          to={link}
          className="flex items-center justify-center gap-1.5 border-l border-hairline py-3 text-royal transition-colors duration-200 hover:bg-royal hover:text-white"
        >
          <CalendarCheck className="size-4" aria-hidden="true" />
          Book now
        </Link>
      </div>
    </article>
  )
}

export function AdvisorsListPage() {
  const [specialty, setSpecialty] = useState<Specialty | ''>('')

  const { data, isLoading, error } = useQuery({
    queryKey: ['advisors', specialty],
    queryFn: () => listAdvisors(specialty || undefined),
  })

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4 animate-fade-in-up">
        <div>
          <h1 className="text-3xl font-semibold text-ink">Our advisors</h1>
          <p className="text-sm text-muted">Certified specialists for every financial decision. Pick one and book a slot.</p>
        </div>

        <div className="w-52">
          <Select label="Specialty" value={specialty} onChange={(e) => setSpecialty(e.target.value as Specialty | '')}>
            <option value="">All specialties</option>
            {SPECIALTIES.map((s) => (
              <option key={s} value={s}>
                {labelFor(s)}
              </option>
            ))}
          </Select>
        </div>
      </div>

      {error && <Alert>{errorMessage(error)}</Alert>}
      {isLoading && <Spinner />}

      {data && data.length === 0 && (
        <EmptyState icon={<Users className="size-8" />} title="No advisors found" hint="Try a different specialty filter." />
      )}

      {data && data.length > 0 && (
        <div className="stagger grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {data.map((advisor) => (
            <AdvisorCard key={advisor.id} advisor={advisor} />
          ))}
        </div>
      )}
    </div>
  )
}
