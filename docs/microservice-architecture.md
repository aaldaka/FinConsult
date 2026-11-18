# Microservice Architecture

FinConsult started as one Spring Boot application (the monolith, kept at the git tag `monolith-final`). It is now four services behind an nginx gateway. Each service owns one part of the business and can be built, tested and deployed on its own.

## The services

| Service | Port | Owns (tables) | Responsibilities |
|---|---|---|---|
| user-service | 8082 | `users`, `profiles`, `advisors`, `verification_tokens`, `password_reset_tokens` | Registration, login, JWT issuing, email verification, password reset, profiles, advisors, admin user management, strikes and blacklisting |
| booking-service | 8083 | `service_types`, `availability_slots`, `appointments` | Slots, booking, confirming, cancelling, no-show handling, completing appointments |
| document-service | 8084 | `kyc_documents`, `advisor_applications`, `application_certificates` | Document upload and checks (VirusTotal lookup), KYC review, advisor applications |
| notification-service | 8081 | `notifications`, `audit_logs`, `processed_events` | Notifications and live stream (SSE), audit log storage, email sending. Consumes all Kafka events |
| API gateway (nginx) | 8080 | none | One entry point that routes by path and blocks `/internal/` |

## Why it is split this way

- Each service owns one area of data and never reads another service's tables or imports its classes.
- Boundaries follow the business: people (users and advisors), scheduling, verification documents, and notifications as a side effect of the others. They change for different reasons.
- Advisors live with users because they are people accounts. Booking refers to an advisor by id and asks user-service for details.
- Notification went first because it had the fewest dependencies and the least risk.

## Shared database

All services use one PostgreSQL database. Each table has exactly one owning service, and other services get data through REST or Kafka. `docker/db/init/01-schema.sql` creates the tables and Hibernate only validates them. Tests run against a separate `finconsult_test` database. One database per service is future work and would need a migration tool such as Flyway.

## How services talk to each other

- REST when a service needs an answer right now (for example, booking asks user-service whether an advisor is active). Internal endpoints need the internal API key.
- Kafka when something has happened and others react: `notification-events`, `audit-events` and `email-events`. Consumers are idempotent, failed messages are retried 3 times and then go to a `.DLT` topic; a message that is not valid JSON or breaks the contract goes there straight away.

## Running and delivering

Docker Compose runs the services with PostgreSQL, Kafka, the gateway and the frontend. Jenkins tests, builds versioned images, pushes them to ECR and deploys to EC2 with RDS. See the root README for details.

## How the migration went

The services were moved out one at a time, with extra JUnit tests at each step: notification, user, booking, then document. Kafka events came next, and the monolith was removed once the services were deployed.
