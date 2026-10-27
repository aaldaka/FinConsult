import type { AuthResponse } from '@/api/types'

const TOKEN_KEY = 'finconsult.token'
const USER_KEY = 'finconsult.user'

export type SessionUser = Omit<AuthResponse, 'token'>

/**
 * Session lives in localStorage so a refresh keeps the user signed in.
 * Reads are wrapped in try/catch so a cleared or broken store doesnt crash the app.
 */
export const session = {
  getToken(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },

  getUser(): SessionUser | null {
    try {
      const raw = localStorage.getItem(USER_KEY)
      return raw ? (JSON.parse(raw) as SessionUser) : null
    } catch {
      return null
    }
  },

  save(token: string, user: SessionUser) {
    try {
      localStorage.setItem(TOKEN_KEY, token)
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    } catch {
      /* private mode / blocked storage, session stays in memory for this tab only */
    }
  },

  clear() {
    try {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    } catch {
      /* nothing to clear */
    }
  },
}
