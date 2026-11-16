/**
 * Mirrors the backend DTOs exactly as published at GET /v3/api-docs.
 * Do not add fields the API does not return.
 */

export type UserRole = 'ADMIN' | 'CLIENT' | 'ADVISOR'

export type AppointmentStatus = 'PENDING' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW'

export type Specialty =
  | 'GENERAL'
  | 'LOANS'
  | 'MORTGAGES'
  | 'INVESTMENTS'
  | 'RETIREMENT_PLANNING'
  | 'INSURANCE'
  | 'BUSINESS_BANKING'

export type SlotStatus = 'AVAILABLE' | 'BOOKED'

export type KycStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export type DocumentType = 'NATIONAL_ID' | 'PASSPORT' | 'PROOF_OF_ADDRESS' | 'INCOME_PROOF'

export type NotificationType =
  | 'APPOINTMENT_BOOKED'
  | 'APPOINTMENT_CONFIRMED'
  | 'APPOINTMENT_CANCELLED'
  | 'KYC_REVIEWED'
  | 'APPOINTMENT_NO_SHOW'
  | 'ADVISOR_APPLICATION_REVIEWED'
  | 'DOCUMENT_FLAGGED'

/** Shape of every error the API returns. */
export interface ErrorResponse {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
}

/** Auth payload. `token` is null on register, the user must verify their email first. */
export interface AuthResponse {
  id: number
  email: string
  firstName: string
  lastName: string
  role: UserRole
  token: string | null
}

export interface MessageResponse {
  msg: string
}

export interface StreamTicketResponse {
  ticket: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegistrationRequest {
  email: string
  password: string
  firstName: string
  lastName: string
  profilePicturePath?: string | null
}

export interface ForgetPasswordRequest {
  email: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export interface ResetPasswordRequest {
  token: string
  newPassword: string
}

export interface AdvisorResponse {
  id: number
  name: string
  title: string
  specialty: Specialty
  yearsOfExperience: number
  active: boolean
  /** null until the advisor uploads a profile picture */
  profilePicturePath?: string | null
}

export interface UserSummaryResponse {
  id: number
  name: string
  email: string
}

export interface UpdateAdvisorRequest {
  title?: string
  yearsOfExperience?: number
  specialty?: Specialty
}

export interface AdvisorRequest {
  email: string
  password: string
  firstName: string
  lastName: string
  title: string
  specialty?: Specialty
  yearsOfExperience?: number
}

export interface SlotResponse {
  id: number
  advisorId: number
  startTime: string
  endTime: string
  status: SlotStatus
}

export interface CreateSlotRequest {
  startingTime: string
  finishingTime: string
}

export interface AppointmentResponse {
  id: number
  status: AppointmentStatus
  notes: string | null
  cancellationReason: string | null
  excused: boolean
  excuseReason: string | null
  advisorId: number
  advisorName: string
  advisorSpecialty: Specialty
  clientName: string
  serviceName: string
  startTime: string
  endTime: string
  createdAt: string
}

export interface BookAppointmentRequest {
  serviceTypeId: number
}

export interface ServiceTypeResponse {
  id: number
  name: string
  description: string | null
  durationMinutes: number
  requiresKyc: boolean
  requiredDocumentType: DocumentType | null
  requiredSpecialty: Specialty | null
}

export interface ReasonRequest {
  reason: string
}

export interface ProfileUpdateResponse {
  firstName: string
  lastName: string
  phone: string | null
  dateOfBirth: string | null
  profilePicturePath: string
}

export interface UpdateProfileRequest {
  firstName?: string
  lastName?: string
  phone?: string
  dateOfBirth?: string
}

export interface KycDocResponse {
  id: number
  userEmail: string
  documentType: DocumentType
  originalFileName: string
  fileSize: number
  status: KycStatus
  rejectionReason: string | null
  submittedAt: string
  reviewedAt: string | null
  detectedType: string | null
  flagged: boolean
  /** Only sent to admins. */
  flagReason: string | null
}

export type CertificateStatus = 'PROCESSING' | 'VALID' | 'FLAGGED'

export interface CertificateResponse {
  id: number
  originalFileName: string
  fileSize: number
  status: CertificateStatus
  detectedType: string | null
}

export interface AdvisorApplicationResponse {
  id: number
  applicantEmail: string
  applicantName: string
  title: string
  specialty: Specialty
  yearsOfExperience: number
  status: KycStatus
  rejectionReason: string | null
  submittedAt: string
  reviewedAt: string | null
  certificates: CertificateResponse[]
}

export interface MyApplicationsResponse {
  applications: AdvisorApplicationResponse[]
  attemptsUsed: number
  maxAttempts: number
}

export interface AdvisorApplicationRequest {
  title: string
  specialty?: Specialty
  yearsOfExperience: number
}

export interface NotificationResponse {
  id: number
  type: NotificationType
  message: string
  read: boolean
  relatedAppointmentId: number | null
  createdAt: string
}

export interface AuditLogResponse {
  id: number
  actorEmail: string
  actorName: string
  action: string
  entityType: string
  entityId: number
  details: string
  timestamp: string
}

/** Spring Data page wrapper, as returned by the paged admin/KYC endpoints. */
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}
