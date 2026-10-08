import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Briefcase, Users } from 'lucide-react'
import { listAdvisors } from '@/api/advisors'
import type { Specialty } from '@/api/types'
import { Card } from '@/components/ui/Card'
import { Select } from '@/components/ui/Select'
import { Spinner } from '@/components/ui/Spinner'
import { EmptyState } from '@/components/ui/EmptyState'
import { Alert } from '@/components/ui/Alert'
import { errorMessage } from '@/lib/errors'
import { SPECIALTIES, labelFor } from '@/lib/options'

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
          <h1 className="text-2xl font-semibold text-ink">Advisors</h1>
          <p className="text-sm text-muted">Browse active advisors and book a slot.</p>
        </div>

        <div className="w-48">
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
        <div className="stagger grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {data.map((advisor) => (
            <Link key={advisor.id} to={`/advisors/${advisor.id}`} className="group">
              <Card interactive className="h-full">
                <div className="mb-3 flex size-10 items-center justify-center rounded-xl bg-gold/15 text-gold-deep transition-colors duration-200 group-hover:bg-gold group-hover:text-navy">
                  <Briefcase className="size-5" aria-hidden="true" />
                </div>
                <h2 className="font-semibold text-ink">{advisor.name}</h2>
                <p className="text-sm text-muted">{advisor.title}</p>
                <p className="mt-2 text-xs font-medium uppercase tracking-wide text-royal">{labelFor(advisor.specialty)}</p>
                <p className="mt-1 text-xs text-muted">{advisor.yearsOfExperience} years experience</p>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
