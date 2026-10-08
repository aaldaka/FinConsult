import { api } from './client'
import type {
  AuthResponse,
  ChangePasswordRequest,
  ForgetPasswordRequest,
  LoginRequest,
  MessageResponse,
  RegistrationRequest,
  ResetPasswordRequest,
} from './types'

export async function login(body: LoginRequest) {
  const { data } = await api.post<AuthResponse>('/auth/login', body)
  return data
}

export async function register(body: RegistrationRequest) {
  const { data } = await api.post<AuthResponse>('/auth/register', body)
  return data
}

export async function forgetPassword(body: ForgetPasswordRequest) {
  const { data } = await api.post<MessageResponse>('/auth/forget-password', body)
  return data
}

export async function resetPassword(body: ResetPasswordRequest) {
  const { data } = await api.post<MessageResponse>('/auth/reset-password', body)
  return data
}

export async function changePassword(body: ChangePasswordRequest) {
  const { data } = await api.post<MessageResponse>('/auth/change-password', body)
  return data
}

/** Returns a plain-text confirmation, not JSON. */
export async function verifyEmail(token: string) {
  const { data } = await api.get<string>('/auth/verify-email', {
    params: { token },
    responseType: 'text',
  })
  return data
}
