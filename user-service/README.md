# user-service

Identity and accounts for FinConsult: registration, login, profiles, advisors and admin user management.

## Responsibility

Registration, login (issues the JWTs every service validates), email verification, password reset, profiles and pictures, advisors, admin user management and no-show strikes. It is a separate service because identity changes for different reasons than scheduling or documents, and every other service only needs to validate its tokens.

## Data

Owns `users`, `profiles`, `advisors`, `verification_tokens` and `password_reset_tokens`. Profile pictures are stored on disk under `APP_UPLOAD_DIR`.

It does not read other services' tables. Cancelling the appointments of a deactivated user or advisor is asked of booking-service over REST.

## API

| Method | Path | Access |
|---|---|---|
| POST | `/auth/register` | Public |
| GET | `/auth/verify-email?token=` | Public |
| POST | `/auth/login` | Public |
| POST | `/auth/forget-password` | Public |
| POST | `/auth/reset-password` | Public |
| POST | `/auth/change-password` | Private |
| GET | `/profile/me` | Private |
| PATCH | `/profile/me` | Private |
| POST | `/profile/me/picture` | Private |
| GET | `/uploads/profile_pictures/**` | Public |
| GET | `/advisors` | Private |
| GET | `/advisors/{id}` | Private |
| PATCH | `/advisors/me` | Advisor |
| POST | `/admin/advisors` | Admin |
| GET | `/admin/clients` | Admin |
| PATCH | `/admin/users/{id}/deactivate` | Admin |
| PATCH | `/admin/advisors/{id}/deactivate` | Admin |
| GET | `/internal/users/{id}`, POST `/internal/users/names`, POST `/internal/users/lookup`, GET `/internal/users/admins` | Internal API key |
| POST, DELETE | `/internal/users/{id}/strikes` | Internal API key |
| GET | `/internal/advisors/{id}`, `/internal/advisors/by-user/{userId}`; POST `/internal/advisors/lookup`, `/internal/advisors/promote` | Internal API key |

"Private" means any logged-in user with a valid JWT. `/auth/login`, `/auth/register`, `/auth/forget-password` and `/auth/reset-password` are limited to 5 requests a minute. Swagger: http://localhost:8080/user-api/

## Kafka

Produces only. Events are sent after the database transaction commits.

| Topic | Key | Event type | Payload |
|---|---|---|---|
| `audit-events` | `<entityType>:<entityId>` | `AUDIT` | `action`, `entityType`, `entityId`, `actorId`, `details` |
| `email-events` | recipient email | `EMAIL` | `template` (`EMAIL_VERIFICATION` or `PASSWORD_RESET`), `to`, `data` (`link`, plus `expiresInMinutes` for a reset) |

## Calls to other services

- Calls booking-service `POST /internal/appointments/cancel-for-client/{userId}` and `POST /internal/appointments/cancel-for-advisor/{advisorId}` when an admin deactivates a client or an advisor.
- Called by booking-service (users, names, strikes, advisor lookups), document-service (users, lookup, admins, advisor promotion) and notification-service (user lookup for the audit log).

## Configuration

`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `JWT_SECRET`, `JWT_EXPIRATIONMS`, `CORS_ALLOWED_ORIGINS`, `SERVER_PORT`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `INTERNAL_API_KEY`, `BOOKING_SERVICE_URL`, `APP_UPLOAD_DIR`, `APP_FRONTEND_URL`, `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE`, `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE`, `SPRINGDOC_SWAGGER_UI_CONFIG_URL`, `SPRINGDOC_SWAGGER_UI_URL`.

## Run and test

- Run with its dependencies (PostgreSQL and Kafka): `docker compose up -d --build user-service` from the repository root. Deactivation needs booking-service running.
- Tests: `./mvnw test` (Windows: `mvnw.cmd test`). They need the `finconsult_test` PostgreSQL database (`TEST_DB_URL`, `DB_USERNAME`, `DB_PASSWORD`), and only one service's tests may run at a time because they share it.
- 135 tests (38 unit, 97 integration).

See the [root README](../README.MD) for the full architecture, Kafka design, CI/CD and AWS deployment.
