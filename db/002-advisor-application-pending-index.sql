-- one pending advisor application per user
CREATE UNIQUE INDEX IF NOT EXISTS ux_advisor_applications_one_pending_per_user
    ON advisor_applications (user_id)
    WHERE status = 'PENDING';
