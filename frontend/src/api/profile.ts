import { api } from './client'
import type { ProfileUpdateResponse, UpdateProfileRequest } from './types'

export async function getMyProfile() {
  const { data } = await api.get<ProfileUpdateResponse>('/profile/me')
  return data
}

export async function updateMyProfile(body: UpdateProfileRequest) {
  const { data } = await api.patch<ProfileUpdateResponse>('/profile/me', body)
  return data
}

export async function updateMyPicture(file: File) {
  const form = new FormData()
  form.append('file', file)
  const { data } = await api.post<ProfileUpdateResponse>('/profile/me/picture', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data
}
