import axios from 'axios'
import { session } from '@/lib/session'

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = session.getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** Set by AuthContext so a 401 can reset React state, not just storage. */
let onUnauthorized: (() => void) | null = null

export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler
}

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status
    const url: string = error?.config?.url ?? ''

    // A failed login legitimately returns 401 — that belongs to the form, not the
    // session reaper, otherwise submitting bad credentials would bounce the page.
    const isLoginAttempt = url.includes('/auth/login')

    if (status === 401 && !isLoginAttempt) {
      session.clear()
      onUnauthorized?.()
      if (!window.location.pathname.startsWith('/login')) {
        window.location.assign('/login?expired=1')
      }
    }
    return Promise.reject(error)
  },
)
