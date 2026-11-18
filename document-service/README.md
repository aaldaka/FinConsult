# document-service

Verification documents for FinConsult: KYC documents and advisor applications with certificates.

## Responsibility

KYC documents (upload, batch upload, background checks, VirusTotal lookup, approve, reject) and advisor applications with certificates. It is a separate service because document checks run in the background and handle personal files, which is a different concern from identity or scheduling.

## Data

Owns `kyc_documents`, `advisor_applications` and `application_certificates`. Files are stored on disk under `APP_UPLOAD_DIR`.

Data it does not own comes from user-service over internal REST: the user (`GET /internal/users/{id}`), names and emails (`POST /internal/users/lookup`), the admins to notify (`GET /internal/users/admins`), whether the applicant is already an advisor (`GET /internal/advisors/by-user/{userId}`) and the promotion when an application is approved (`POST /internal/advisors/promote`).

## API

| Method | Path | Access |
|---|---|---|
| POST | `/kyc` | Client |
| POST | `/kyc/batch` | Client |
| PUT | `/kyc/{id}/file` | Client |
| GET | `/kyc/me` | Client |
| GET | `/kyc` | Admin |
| PATCH | `/kyc/{id}/approve` | Admin |
| PATCH | `/kyc/{id}/reject` | Admin |
| GET | `/kyc/{id}/download` | Admin |
| POST | `/advisor-applications` | Client |
| GET | `/advisor-applications/mine` | Client |
| PUT | `/advisor-applications/certificates/{certificateId}/file` | Client |
| GET | `/advisor-applications` | Admin |
| PATCH | `/advisor-applications/{id}/approve` | Admin |
| PATCH | `/advisor-applications/{id}/reject` | Admin |
| GET | `/advisor-applications/certificates/{certificateId}/download` | Admin |
| GET | `/internal/kyc/approved` | Internal API key |

Swagger: http://localhost:8080/document-api/

## Kafka

Produces only.

| Topic | Key | Event type | Payload |
|---|---|---|---|
| `notification-events` | recipient user id | `NOTIFICATION` | `userId`, `notificationType` (`KYC_REVIEWED`, `ADVISOR_APPLICATION_REVIEWED`, `DOCUMENT_FLAGGED`), `message` |
| `notification-events` | recipient user id | `STREAM_PUSH` | `userId`, `eventName` (`KYC_CHECKED`, `CERTIFICATES_CHECKED`), `data` |
| `audit-events` | `<entityType>:<entityId>` | `AUDIT` | `action`, `entityType`, `entityId`, `actorId`, `details` |

## Calls to other services

- Calls user-service as listed under Data, and the VirusTotal API (`/files/{hash}`) to look up an uploaded file's hash when `VIRUSTOTAL_API_KEY` is set.
- Called by booking-service (`GET /internal/kyc/approved`) before a booking for a service that requires KYC.

## Configuration

`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `SERVER_PORT`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `INTERNAL_API_KEY`, `USER_SERVICE_URL`, `APP_UPLOAD_DIR`, `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE`, `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE`, `VIRUSTOTAL_API_KEY`, `KYC_CHECK_DELAY_MS`, `SPRINGDOC_SWAGGER_UI_CONFIG_URL`, `SPRINGDOC_SWAGGER_UI_URL`.

## Run and test

- Run with its dependencies (PostgreSQL and Kafka): `docker compose up -d --build document-service` from the repository root. Uploads and reviews need user-service running.
- Tests: `./mvnw test` (Windows: `mvnw.cmd test`). They need the `finconsult_test` PostgreSQL database (`TEST_DB_URL`, `DB_USERNAME`, `DB_PASSWORD`), and only one service's tests may run at a time because they share it.
- 170 tests (28 unit, 142 integration), including the race tests `KycConcurrencyTest` and `ApplicationConcurrencyTest`.
- Cucumber: `src/test/resources/features/kyc.feature` describes the KYC workflow in 5 scenarios and runs with `./mvnw test`.

See the [root README](../README.MD) for the full architecture, Kafka design, CI/CD and AWS deployment.
