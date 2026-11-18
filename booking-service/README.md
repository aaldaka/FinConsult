# booking-service

Scheduling for FinConsult: service types, advisor availability slots and appointments.

## Responsibility

Service types, advisor availability slots, appointments (book, confirm, cancel, no-show, excuse) and a job that completes finished appointments every 15 minutes. It is a separate service because scheduling has its own rules and its own concurrency control: double booking is prevented in the database with row locks and partial unique indexes, so more than one instance can run.

## Data

Owns `service_types`, `availability_slots` and `appointments`.

Data it does not own comes over internal REST:

- user-service: the user (`GET /internal/users/{id}`), names for messages (`POST /internal/users/names`), strikes (`POST` and `DELETE /internal/users/{id}/strikes`), advisors (`GET /internal/advisors/{id}`, `GET /internal/advisors/by-user/{userId}`, `POST /internal/advisors/lookup`).
- document-service: whether the client has an approved KYC document (`GET /internal/kyc/approved`), asked before a booking for a service that requires one.

## API

| Method | Path | Access |
|---|---|---|
| GET | `/service-types` | Private |
| GET | `/slots/advisor/{advisorId}` | Private |
| GET | `/slots/mine` | Advisor |
| POST | `/slots` | Advisor |
| DELETE | `/slots/{id}` | Advisor |
| POST | `/appointments/book/{slotId}` | Client (`201`, or `409` if someone else booked it first) |
| GET | `/appointments/mine` | Private |
| PATCH | `/appointments/{id}/confirm` | Advisor |
| PATCH | `/appointments/{id}/cancel` | Private (appointment parties) |
| PATCH | `/appointments/{id}/no-show` | Advisor |
| PATCH | `/appointments/{id}/excuse` | Admin |
| GET | `/appointments/no-shows` | Admin |
| POST | `/internal/appointments/cancel-for-advisor/{advisorId}` | Internal API key |
| POST | `/internal/appointments/cancel-for-client/{userId}` | Internal API key |

"Private" means any logged-in user with a valid JWT. Swagger: http://localhost:8080/booking-api/

## Kafka

Produces only.

| Topic | Key | Event type | Payload |
|---|---|---|---|
| `notification-events` | recipient user id | `NOTIFICATION` | `userId`, `notificationType` (`APPOINTMENT_BOOKED`, `APPOINTMENT_CONFIRMED`, `APPOINTMENT_CANCELLED`, `APPOINTMENT_NO_SHOW`), `message`, `relatedAppointmentId` |
| `audit-events` | `<entityType>:<entityId>` | `AUDIT` | `action`, `entityType`, `entityId`, `actorId`, `details` |

## Calls to other services

- Calls user-service and document-service as listed under Data.
- Called by user-service, which cancels the appointments of a deactivated client or advisor.

## Configuration

`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `SERVER_PORT`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `INTERNAL_API_KEY`, `USER_SERVICE_URL`, `DOCUMENT_SERVICE_URL`, `SPRINGDOC_SWAGGER_UI_CONFIG_URL`, `SPRINGDOC_SWAGGER_UI_URL`.

## Run and test

- Run with its dependencies (PostgreSQL and Kafka): `docker compose up -d --build booking-service` from the repository root. Booking needs user-service and document-service running.
- Two instances: `docker compose up -d --scale booking-service=2 --no-recreate`, then `docker compose up -d --no-deps --force-recreate gateway`. `scripts/concurrency-demo.sh` checks that one booking wins and the rest get `409`.
- Tests: `./mvnw test` (Windows: `mvnw.cmd test`). They need the `finconsult_test` PostgreSQL database (`TEST_DB_URL`, `DB_USERNAME`, `DB_PASSWORD`), and only one service's tests may run at a time because they share it.
- 314 tests (162 unit, 152 integration), including the race tests `AppointmentRaceTest` and `SlotCreationRaceTest`.

See the [root README](../README.MD) for the full architecture, Kafka design, CI/CD and AWS deployment.
