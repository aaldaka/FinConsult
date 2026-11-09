# Microservice Architecture

FinConsult started as one Spring Boot application (the monolith). It is being split into small services that each own one part of the business and can run, be tested and be deployed on their own.

## The services

| Service | Owns (tables) | Responsibilities | Status |
|---|---|---|---|
| User Service | `users`, `profiles`, `advisors`, `verification_tokens`, `password_reset_tokens` | Registration, login, JWT issuing, email verification, password reset, profiles, advisor accounts, admin user management, strikes and blacklisting | In progress |
| Booking Service | `service_types`, `availability_slots`, `appointments` | Slots, booking, confirming, cancelling, no-show handling | Planned |
| KYC and Documents Service | `kyc_documents`, `advisor_applications`, `application_certificates` | Document upload and virus scanning, KYC review, advisor applications | Planned |
| Notification Service (port 8081) | `notifications` | Notification storage, read/unread, live stream (SSE) | Built |
| API Gateway (optional) | none | One entry point that routes requests to the services | Optional |

## Why it is split this way

- **Each service owns one area of data.** A service only reads and writes its own tables. If it needs something from another area, it asks that service, and it never imports another service's classes or reads its tables.
- **Boundaries follow the business.** Users and advisors are "people", appointments and slots are "scheduling", documents and applications are "verification", and notifications are a side effect of the others. These parts change for different reasons and at different times.
- **Related data stays together.** Advisors live with users because they are people accounts. The Booking Service refers to an advisor by id only.
- **Notifications went first.** It has the fewest dependencies and the least risk, so it was the best place to learn the pattern.

## Shared database

The course requires one database for all services, and each service connects to the same one. We keep the services independent anyway:

- Each service owns its tables by convention and never touches another service's tables.
- A service uses `ddl-auto=validate` for tables it does not create, and `update` only for tables it owns.
- Tests run against a separate `finconsult_test` database, never the demo data.
- Splitting into one database per service is future work and would need a migration tool such as Flyway (Optional as per the feedback).

## How services talk to each other

- **REST** when a service needs an answer right now (for example, checking that an advisor exists and is active).
- **Events (Kafka, planned)** when something has happened and other services react (for example, an appointment is booked and the advisor is notified). Events use the topics `booking-events`, `kyc-events` and `user-events`. Consumers are idempotent, and failed messages go to a dead-letter topic after 3 retries.

## Running and delivering

- Each service has its own Maven project, port and tests.
- Docker and Docker Compose run the services together with Kafka.
- A Jenkins pipeline builds, tests and builds images for each service.
- Deployment targets AWS (EC2 with EB and RDS, potentially introducing S3 with privacy options configured).

## Order of the migration

1. Notification Service (done).
2. User Service.
3. Booking Service.
4. KYC and Documents Service.
5. Kafka events, then removing the old code from the monolith and pointing the frontend at the services.

The monolith keeps running until each piece is switched over, so the application works at every step.