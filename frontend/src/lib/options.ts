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

// every action the audit log knows, the same list as GET /admin/logs accepts as a filter
export const AUDIT_ACTIONS = [
  'APPOINTMENT_BOOKED',
  'APPOINTMENT_CONFIRMED',
  'APPOINTMENT_CANCELLED',
  'APPOINTMENT_CANCELLED_BY_ADMIN',
  'APPOINTMENT_NO_SHOW',
  'NO_SHOW_EXCUSED',
  'CLIENT_BLACKLISTED',
  'KYC_SUBMITTED',
  'KYC_RESUBMITTED',
  'KYC_APPROVED',
  'KYC_REJECTED',
  'ADVISOR_APPLICATION_SUBMITTED',
  'ADVISOR_APPLICATION_APPROVED',
  'ADVISOR_APPLICATION_REJECTED',
  'CERTIFICATE_REPLACED',
  'ADVISOR_CREATED',
  'ADVISOR_DEACTIVATED',
  'USER_DEACTIVATED',
  'PROFILE_UPDATED',
  'PROFILE_PICTURE_UPDATED',
] as const

export { humanize as labelFor }
