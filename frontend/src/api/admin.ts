import { api } from './client'
import type { AdvisorRequest, AdvisorResponse, AuditLogResponse, MessageResponse, Page } from './types'

export async function createAdvisor(body: AdvisorRequest) {
  const { data } = await api.post<AdvisorResponse>('/admin/advisors', body)
  return data
}

export async function deactivateUser(id: number) {
  const { data } = await api.patch<MessageResponse>(`/admin/users/${id}/deactivate`)
  return data
}

export async function deactivateAdvisor(id: number, reason: string) {
  const { data } = await api.patch<MessageResponse>(`/admin/advisors/${id}/deactivate`, { reason })
  return data
}

export interface AuditLogQuery {
  page?: number
  size?: number
  action?: string
}

export async function listAuditLogs(query: AuditLogQuery = {}) {
  const { data } = await api.get<Page<AuditLogResponse>>('/admin/logs', { params: query })
  return data
}
