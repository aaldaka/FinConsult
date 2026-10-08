import { api } from './client'
import type { CreateSlotRequest, MessageResponse, SlotResponse } from './types'

export async function listAdvisorSlots(advisorId: number) {
  const { data } = await api.get<SlotResponse[]>(`/slots/advisor/${advisorId}`)
  return data
}

export async function listMySlots() {
  const { data } = await api.get<SlotResponse[]>('/slots/mine')
  return data
}

export async function createSlot(body: CreateSlotRequest) {
  const { data } = await api.post<SlotResponse>('/slots', body)
  return data
}

export async function deleteSlot(id: number) {
  const { data } = await api.delete<MessageResponse>(`/slots/${id}`)
  return data
}
