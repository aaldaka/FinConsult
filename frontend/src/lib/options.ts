import type { AppointmentStatus, DocumentType, KycStatus, Specialty } from '@/api/types'
import { humanize } from '@/lib/format'

export const SPECIALTIES: Specialty[] = [
  'GENERAL',
  'LOANS',
  'MORTGAGES',
  'INVESTMENTS',
  'RETIREMENT_PLANNING',
  'INSURANCE',
  'BUSINESS_BANKING',
]

export const DOCUMENT_TYPES: DocumentType[] = ['NATIONAL_ID', 'PASSPORT', 'PROOF_OF_ADDRESS', 'INCOME_PROOF']

export const KYC_STATUSES: KycStatus[] = ['PENDING', 'APPROVED', 'REJECTED']

export const APPOINTMENT_STATUSES: AppointmentStatus[] = ['PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'NO_SHOW']

export { humanize as labelFor }
