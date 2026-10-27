import { api } from './client'
import type { AdvisorResponse, Specialty, UpdateAdvisorRequest } from './types'

export async function listAdvisors(specialty?: Specialty) {
  const { data } = await api.get<AdvisorResponse[]>('/advisors', {
    params: specialty ? { specialty } : undefined,
  })
  return data
}

export async function getAdvisor(id: number) {
  const { data } = await api.get<AdvisorResponse>(`/advisors/${id}`)
  return data
}

export async function updateMyAdvisorProfile(body: UpdateAdvisorRequest) {
  const { data } = await api.patch<AdvisorResponse>('/advisors/me', body)
  return data
}

/**
 * There is no GET /advisors/me, the backend only exposes lookup by advisor ID
 * (which the frontend doesn't know for "myself") or a partial-update PATCH.
 * `updateAdvisor` only applies non-null fields, so an empty body is a safe,
 * side-effect-free way to read back the caller's own current advisor record.
 */
export async function getMyAdvisorProfile() {
  return updateMyAdvisorProfile({})
}
