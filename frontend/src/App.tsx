import { Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from '@/components/layout/AppLayout'
import { ProtectedRoute } from '@/components/layout/ProtectedRoute'
import { LoginPage } from '@/pages/auth/LoginPage'
import { RegisterPage } from '@/pages/auth/RegisterPage'
import { ForgotPasswordPage } from '@/pages/auth/ForgotPasswordPage'
import { ResetPasswordPage } from '@/pages/auth/ResetPasswordPage'
import { VerifyEmailPage } from '@/pages/auth/VerifyEmailPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { AdvisorsListPage } from '@/pages/advisors/AdvisorsListPage'
import { AdvisorDetailPage } from '@/pages/advisors/AdvisorDetailPage'
import { AppointmentsPage } from '@/pages/appointments/AppointmentsPage'
import { SlotsPage } from '@/pages/slots/SlotsPage'
import { KycPage } from '@/pages/kyc/KycPage'
import { KycReviewPage } from '@/pages/kyc/KycReviewPage'
import { AdminPage } from '@/pages/admin/AdminPage'
import { NotificationsPage } from '@/pages/NotificationsPage'
import { ProfilePage } from '@/pages/ProfilePage'

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="/verify-email" element={<VerifyEmailPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route path="/profile" element={<ProfilePage />} />

          <Route element={<ProtectedRoute roles={['CLIENT']} />}>
            <Route path="/advisors" element={<AdvisorsListPage />} />
            <Route path="/advisors/:id" element={<AdvisorDetailPage />} />
            <Route path="/kyc" element={<KycPage />} />
          </Route>

          <Route element={<ProtectedRoute roles={['CLIENT', 'ADVISOR']} />}>
            <Route path="/appointments" element={<AppointmentsPage />} />
          </Route>

          <Route element={<ProtectedRoute roles={['ADVISOR']} />}>
            <Route path="/slots" element={<SlotsPage />} />
          </Route>

          <Route element={<ProtectedRoute roles={['ADMIN']} />}>
            <Route path="/admin" element={<AdminPage />} />
            <Route path="/kyc-review" element={<KycReviewPage />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
