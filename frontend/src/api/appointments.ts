import { api } from './client'
import type { AppointmentResponse, AppointmentStatus, MessageResponse } from './types'

/** Backend only allows sorting on createdAt, status and slot.startTime. */
export type AppointmentSortField = 'createdAt' | 'status' | 'slot.startTime'

export interface MyAppointmentsQuery {
  status?: AppointmentStatus
  sort?: `${AppointmentSortField},${'asc' | 'desc'}`
}

export async function listMyAppointments(query: MyAppointmentsQuery = {}) {
  const { data } = await api.get<AppointmentResponse[]>('/appointments/mine', {
    params: { status: query.status, sort: query.sort },
  })
  return data
}

export async function bookSlot(slotId: number, serviceTypeId: number) {
  const { data } = await api.post<AppointmentResponse>(`/appointments/book/${slotId}`, {
    serviceTypeId,
  })
  return data
}

export async function confirmAppointment(id: number) {
  const { data } = await api.patch<AppointmentResponse>(`/appointments/${id}/confirm`)
  return data
}

export async function cancelAppointment(id: number, reason: string) {
  const { data } = await api.patch<MessageResponse>(`/appointments/${id}/cancel`, { reason })
  return data
}

export async function markNoShow(id: number) {
  const { data } = await api.patch<AppointmentResponse>(`/appointments/${id}/no-show`)
  return data
}

export async function listNoShows() {
  const { data } = await api.get<AppointmentResponse[]>('/appointments/no-shows')
  return data
}

export async function excuseNoShow(id: number, reason: string) {
  const { data } = await api.patch<AppointmentResponse>(`/appointments/${id}/excuse`, { reason })
  return data
}
