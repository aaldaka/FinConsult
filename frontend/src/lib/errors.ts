import { AxiosError } from 'axios'
import type { ErrorResponse } from '@/api/types'

const NETWORK_MESSAGE = 'Cannot reach the server. Check your connection and try again.'
const FALLBACK_MESSAGE = 'Something went wrong. Please try again.'

export function isConflict(error: unknown): boolean {
  return error instanceof AxiosError && error.response?.status === 409
}

/**
 * Pulls the `message` field out of the API's ErrorResponse, which is what the
 * backend intends users to read. Falls back to a default when the server is
 * unreachable or returns something unexpected.
 */
export function errorMessage(error: unknown): string {
  if (error instanceof AxiosError) {
    if (!error.response) return NETWORK_MESSAGE

    const data = error.response.data as Partial<ErrorResponse> | string | undefined

    if (typeof data === 'string' && data.trim()) return data
    if (data && typeof data === 'object' && typeof data.message === 'string' && data.message) {
      return data.message
    }
    return error.response.statusText || FALLBACK_MESSAGE
  }

  if (error instanceof Error && error.message) return error.message
  return FALLBACK_MESSAGE
}
