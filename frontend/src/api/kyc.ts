import { api } from './client'
import type { DocumentType, KycDocResponse, KycStatus, Page } from './types'

export async function listMyDocuments() {
  const { data } = await api.get<KycDocResponse[]>('/kyc/me')
  return data
}

export interface KycQuery {
  status?: KycStatus
  documentType?: DocumentType
  page?: number
  size?: number
}

export async function listDocumentsByStatus(query: KycQuery = {}) {
  const { data } = await api.get<Page<KycDocResponse>>('/kyc', { params: query })
  return data
}

export async function submitDocuments(items: { file: File; documentType: DocumentType }[]) {
  const form = new FormData()
  items.forEach((item) => {
    form.append('files', item.file)
    form.append('documentTypes', item.documentType)
  })
  const { data } = await api.post<KycDocResponse[]>('/kyc/batch', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data
}

export async function approveDocument(id: number) {
  const { data } = await api.patch<KycDocResponse>(`/kyc/${id}/approve`)
  return data
}

export async function rejectDocument(id: number, reason: string) {
  const { data } = await api.patch<KycDocResponse>(`/kyc/${id}/reject`, { reason })
  return data
}

export async function downloadDocument(id: number) {
  const { data } = await api.get<Blob>(`/kyc/${id}/download`, { responseType: 'blob' })
  return data
}
