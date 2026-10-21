import { api } from './client'
import type {
  AdvisorApplicationRequest,
  AdvisorApplicationResponse,
  KycStatus,
  MyApplicationsResponse,
  Page,
} from './types'

export async function listMyApplications() {
  const { data } = await api.get<MyApplicationsResponse>('/advisor-applications/mine')
  return data
}

export interface ApplicationQuery {
  status?: KycStatus
  page?: number
  size?: number
}

export async function listApplicationsByStatus(query: ApplicationQuery = {}) {
  const { data } = await api.get<Page<AdvisorApplicationResponse>>('/advisor-applications', { params: query })
  return data
}

export async function submitApplication(request: AdvisorApplicationRequest, certificates: File[]) {
  const form = new FormData()
  form.append('application', new Blob([JSON.stringify(request)], { type: 'application/json' }))
  certificates.forEach((file) => form.append('certificates', file))
  const { data } = await api.post<AdvisorApplicationResponse>('/advisor-applications', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data
}

export async function approveApplication(id: number) {
  const { data } = await api.patch<AdvisorApplicationResponse>(`/advisor-applications/${id}/approve`)
  return data
}

export async function rejectApplication(id: number, reason: string) {
  const { data } = await api.patch<AdvisorApplicationResponse>(`/advisor-applications/${id}/reject`, { reason })
  return data
}

export async function downloadCertificate(certificateId: number) {
  const { data } = await api.get<Blob>(`/advisor-applications/certificates/${certificateId}/download`, {
    responseType: 'blob',
  })
  return data
}
