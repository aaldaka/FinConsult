import { api } from './client'
import type { MessageResponse, NotificationResponse } from './types'

export async function listMyNotifications() {
  const { data } = await api.get<NotificationResponse[]>('/notifications/mine')
  return data
}

export async function listMyUnreadNotifications() {
  const { data } = await api.get<NotificationResponse[]>('/notifications/mine/unread')
  return data
}

export async function markNotificationRead(id: number) {
  const { data } = await api.patch<NotificationResponse>(`/notifications/${id}/read`)
  return data
}

export async function markAllNotificationsRead() {
  const { data } = await api.patch<MessageResponse>('/notifications/mine/read-all')
  return data
}
