import { api } from './client'
import type { ServiceTypeResponse } from './types'

export async function listServiceTypes() {
  const { data } = await api.get<ServiceTypeResponse[]>('/service-types')
  return data
}
