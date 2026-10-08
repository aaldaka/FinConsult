import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import * as authApi from '@/api/auth'
import { setUnauthorizedHandler } from '@/api/client'
import { session } from '@/lib/session'
import type { LoginRequest, RegistrationRequest } from '@/api/types'
import type { SessionUser } from '@/lib/session'

interface AuthContextValue {
  user: SessionUser | null
  isAuthenticated: boolean
  login: (body: LoginRequest) => Promise<void>
  register: (body: RegistrationRequest) => Promise<{ verified: boolean }>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(() => session.getUser())

  const logout = useCallback(() => {
    session.clear()
    setUser(null)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(() => setUser(null))
    return () => setUnauthorizedHandler(null)
  }, [])

  const login = useCallback(async (body: LoginRequest) => {
    const res = await authApi.login(body)
    if (!res.token) {
      throw new Error('Login did not return a token')
    }
    const nextUser: SessionUser = { id: res.id, email: res.email, firstName: res.firstName, lastName: res.lastName, role: res.role }
    session.save(res.token, nextUser)
    setUser(nextUser)
  }, [])

  const register = useCallback(async (body: RegistrationRequest) => {
    const res = await authApi.register(body)
    // Registration never returns a token — the account starts UNVERIFIED until the emailed link is used.
    return { verified: !!res.token }
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ user, isAuthenticated: !!user, login, register, logout }),
    [user, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider')
  return ctx
}
