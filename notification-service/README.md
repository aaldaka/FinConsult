# notification-service

In-app notifications, live browser push (SSE), audit log storage and email sending for FinConsult.

## Responsibility

Stores and serves each user's notifications, pushes them to the open browser over Server-Sent Events, keeps the audit log and sends emails. It is a separate service because it is a pure consumer: the other services publish events and never wait for a notification, a push or an email.

## Data

Owns `notifications`, `audit_logs` and `processed_events` (created at startup by `SchemaFixes`).

Users and appointments are only ids here. Everything else arrives in Kafka events, except the actor names on the audit log, which come from user-service (`POST /internal/users/lookup`).

## API

| Method | Path | Access |
|---|---|---|
| GET | `/notifications/mine` | Private |
| GET | `/notifications/mine/unread` | Private |
| PATCH | `/notifications/{id}/read` | Private |
| PATCH | `/notifications/mine/read-all` | Private |
| POST | `/notifications/stream-ticket` | Private |
| GET | `/notifications/stream?ticket=` | Ticket (SSE) |
| GET | `/admin/logs` | Admin |

"Private" means any logged-in user with a valid JWT. Swagger: http://localhost:8080/notification-api/

## Kafka

Consumes only; consumer group `notification-service`.

| Topic | Event type | Payload | On receipt |
|---|---|---|---|
| `notification-events` | `NOTIFICATION` | `userId`, `notificationType`, `message`, `relatedAppointmentId` | Saves a notification, then pushes it over SSE after commit |
| `notification-events` | `STREAM_PUSH` | `userId`, `eventName`, `data` | Pushes to the user's open stream only, nothing is stored |
| `audit-events` | `AUDIT` | `action`, `entityType`, `entityId`, `actorId`, `details` | Writes an `audit_logs` row timestamped with `occurredAt` (details cut to 800 chars) |
| `email-events` | `EMAIL` | `template`, `to`, `data` | Builds the email from the template (`EMAIL_VERIFICATION`, `PASSWORD_RESET`) and sends it by SMTP |

Every message has the envelope `eventId`, `type`, `occurredAt`, `source`, `payload`.

- Idempotency: each `eventId` is stored in `processed_events` in the same transaction as the work, so a repeated event is skipped.
- Retry and dead letter: a failed message is retried 3 times, 1 second apart, then sent unchanged to `<topic>.DLT` with the same key. A message that is not valid JSON or breaks the contract (`InvalidEventException`) skips the retries and goes straight to the DLT.
- Offsets start from earliest and are committed after the message is handled.

## Calls to other services

- Calls user-service `POST /internal/users/lookup` for the actor names on `GET /admin/logs`.
- No service calls it over REST; it only receives Kafka events.

## Configuration

`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `SERVER_PORT`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `INTERNAL_API_KEY`, `USER_SERVICE_URL`, `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE`, `MANAGEMENT_HEALTH_MAIL_ENABLED`, `SPRINGDOC_SWAGGER_UI_CONFIG_URL`, `SPRINGDOC_SWAGGER_UI_URL`.

## Run and test

- Run with its dependencies (PostgreSQL and Kafka): `docker compose up -d --build notification-service` from the repository root. The audit log names need user-service running.
- Tests: `./mvnw test` (Windows: `mvnw.cmd test`). They need the `finconsult_test` PostgreSQL database (`TEST_DB_URL`, `DB_USERNAME`, `DB_PASSWORD`), and only one service's tests may run at a time because they share it.
- 76 tests (18 unit, 58 integration), including the Kafka retry, dead-letter and idempotency tests.

See the [root README](../README.MD) for the full architecture, Kafka design, CI/CD and AWS deployment.
