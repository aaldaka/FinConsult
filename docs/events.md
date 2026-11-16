# Events

The services tell each other about things that already happened through Kafka. Today these calls are logging stubs
(`NotificationPublisher`, `CheckEventPublisher`, `AuditRecorder`); this file is the contract the real producers and
consumers will follow.

## Topics

| Topic | Producers | Consumer | Key |
|---|---|---|---|
| `notification-events` | booking-service, document-service, user-service | notification-service | the recipient's `userId` (as a string) |
| `audit-events` | booking-service, document-service, user-service | notification-service (the audit log) | `"<entityType>:<entityId>"`, for example `"Appointment:42"` |
| `notification-events.DLT` | the notification consumer, after its retries | nobody automatically, read by hand | same key as the original |
| `audit-events.DLT` | the audit consumer, after its retries | nobody automatically, read by hand | same key as the original |
| `email-events` | user-service | notification-service | the recipient's email address |
| `email-events.DLT` | the email consumer, after its retries | nobody automatically, read by hand | same key as the original |

All six topics have 3 partitions and replication factor 1 (one local broker). The key decides the partition, so all
events for one user (or one audited record) arrive in the order they were sent.

The email topics keep their messages for a short time only, because they carry one-time links: `email-events` keeps
them for 1 hour (`retention.ms=3600000`) and `email-events.DLT` for 24 hours (`retention.ms=86400000`), enough to
look at a failure and replay it by hand. The other topics use the broker default.

## Envelope

Every message on every topic is a JSON object with the same five fields:

| Field | Type | Meaning |
|---|---|---|
| `eventId` | UUID string | unique per event, made by the producer. Consumers use it to ignore duplicates |
| `type` | string | what kind of event this is, see the topics below |
| `occurredAt` | ISO-8601 timestamp with offset, e.g. `2026-11-15T10:30:00Z` | when the change happened, not when it was sent |
| `source` | string | the service that sent it: `booking-service`, `document-service`, `user-service` |
| `payload` | object | the fields for this `type` |

```json
{
  "eventId": "0b6f3c2e-6f0a-4d1e-9a3b-2f5c8e1d7a90",
  "type": "NOTIFICATION",
  "occurredAt": "2026-11-15T10:30:00Z",
  "source": "booking-service",
  "payload": { }
}
```

## notification-events

### `NOTIFICATION`

A notification the user can see in the notifications list. The consumer stores it and pushes it to the user's open
stream.

| Field | Type | Notes |
|---|---|---|
| `userId` | number | who gets it |
| `notificationType` | string | one of the values below |
| `message` | string | the text the user sees, at most 255 characters (the `notifications.message` column) |
| `relatedAppointmentId` | number or `null` | booking sends the appointment id; document-service sends `null` |

`notificationType` values, the same as the `notifications.type` check constraint:

| Value | Sent by |
|---|---|
| `APPOINTMENT_BOOKED`, `APPOINTMENT_CONFIRMED`, `APPOINTMENT_CANCELLED`, `APPOINTMENT_NO_SHOW` | booking-service |
| `KYC_REVIEWED`, `ADVISOR_APPLICATION_REVIEWED`, `DOCUMENT_FLAGGED` | document-service |
| `APPOINTMENT_REMINDER` | allowed by the table, nothing sends it yet |

This maps one to one onto the current stubs: booking's
`NotificationPublisher.publish(recipientUserId, type, message, appointmentId)` and document-service's
`NotificationPublisher.publish(recipientUserId, type, message)`.

```json
{
  "eventId": "0b6f3c2e-6f0a-4d1e-9a3b-2f5c8e1d7a90",
  "type": "NOTIFICATION",
  "occurredAt": "2026-11-15T10:30:00Z",
  "source": "booking-service",
  "payload": {
    "userId": 14,
    "notificationType": "APPOINTMENT_CONFIRMED",
    "message": "Reem Al-Mannai confirmed your General consultation appointment on 16 Nov 2026, 10:00.",
    "relatedAppointmentId": 42
  }
}
```

### `STREAM_PUSH`

A short signal for the user's open browser only. It is not stored and does not show in the notifications list; the
frontend uses it to refresh a page.

| Field | Type | Notes |
|---|---|---|
| `userId` | number | whose stream gets it |
| `eventName` | string | `KYC_CHECKED` or `CERTIFICATES_CHECKED` |
| `data` | object, optional | extra fields the current pushes carry: `documentIds` (array of numbers) for `KYC_CHECKED`, `applicationId` (number) for `CERTIFICATES_CHECKED`. The frontend only reads `eventName` today |

This maps onto document-service's `CheckEventPublisher.push(userId, event)`, where `event` today is
`{"type": "KYC_CHECKED", "documentIds": [...]}` or `{"type": "CERTIFICATES_CHECKED", "applicationId": ...}`. The
consumer pushes `{"type": eventName, ...data}` to the stream, which is the shape the frontend already handles.

```json
{
  "eventId": "7c1d9e44-2a5b-4f60-8d3e-91b0c6a4e215",
  "type": "STREAM_PUSH",
  "occurredAt": "2026-11-15T10:31:12Z",
  "source": "document-service",
  "payload": {
    "userId": 14,
    "eventName": "KYC_CHECKED",
    "data": { "documentIds": [31, 32] }
  }
}
```

## email-events

### `EMAIL`

An email to send. The consumer fills in the template and sends it; nothing is stored apart from the `eventId`.

| Field | Type | Notes |
|---|---|---|
| `template` | string | which email, see below |
| `to` | string | the recipient's email address |
| `data` | object | exactly the values the body needs, see below |

| `template` | Sent by | Subject | `data` |
|---|---|---|---|
| `EMAIL_VERIFICATION` | user-service, on registration | `Verify your FinConsult email` | `link`: the finished link, `<app.frontend-url>/verify-email?token=<token>`. The link expires in 24 hours |
| `PASSWORD_RESET` | user-service, on a reset request for a known address | `Reset your FinConsult password` | `link`: the finished link, `<app.frontend-url>/reset-password?token=<token>`; `expiresInMinutes`: number, how long the link is valid (5 today) |

The producer builds the whole link, so the consumer does not need the frontend address. An unknown `template` is
logged and skipped like an unknown `type`.

**The tokens and links are secrets.** They must never be logged, by the producer or the consumer, and never be put
in an exception message: log the `eventId` and the `template` only. A message on the DLT still contains the link,
which is one reason the DLT keeps it for 24 hours only.

```json
{
  "eventId": "5e2b7a91-3c4d-4f8e-b6a0-d17c92e4f358",
  "type": "EMAIL",
  "occurredAt": "2026-11-15T10:32:05Z",
  "source": "user-service",
  "payload": {
    "template": "PASSWORD_RESET",
    "to": "client@example.com",
    "data": { "link": "http://localhost:5173/reset-password?token=...", "expiresInMinutes": 5 }
  }
}
```

## audit-events

### `AUDIT`

One entry for the audit log. notification-service writes it to the `audit_logs` table and serves it on
`GET /admin/logs`. The fields follow the monolith's table (`action`, `entity_type`, `entity_id`, `actor_id`,
`details`); the table's `timestamp` is the envelope's `occurredAt` (converted to the server's time zone, the column has
none), so a late or replayed event still shows at the time the change happened. `details` longer than 800 characters
is cut, like the monolith did. `actorId` must be an existing user or `null`: the column has a foreign key to `users`,
so an unknown actor fails the insert and the event ends up on `audit-events.DLT` after the retries. `details` can
contain names, so the consumer logs only the `eventId` and the `action`.

| Field | Type | Notes |
|---|---|---|
| `action` | string | the `AuditAction` value, see below |
| `entityType` | string | what was changed: `Appointment`, `User`, `Advisor`, `Profile`, `KycDocument`, `AdvisorApplication`, `ApplicationCertificate` |
| `entityId` | number | the id of that record |
| `actorId` | number or `null` | the user who did it; `null` for something the system did on its own |
| `details` | string | readable text, at most 800 characters (the `audit_logs.details` column) |

`action` values sent today:

| Sent by | Values |
|---|---|
| booking-service | `APPOINTMENT_BOOKED`, `APPOINTMENT_CONFIRMED`, `APPOINTMENT_CANCELLED`, `APPOINTMENT_NO_SHOW`, `NO_SHOW_EXCUSED`, `CLIENT_BLACKLISTED` |
| document-service | `KYC_SUBMITTED`, `KYC_RESUBMITTED`, `KYC_APPROVED`, `KYC_REJECTED`, `ADVISOR_APPLICATION_SUBMITTED`, `ADVISOR_APPLICATION_APPROVED`, `ADVISOR_APPLICATION_REJECTED`, `CERTIFICATE_REPLACED` |
| user-service | `PROFILE_UPDATED`, `PROFILE_PICTURE_UPDATED` (entityType `Profile`), `USER_DEACTIVATED` (`User`), `ADVISOR_CREATED`, `ADVISOR_DEACTIVATED` (`Advisor`) |

The current stubs: user-service's `AuditRecorder.record(actorUserId, action, entityType, entityId, details)` matches
these fields exactly. Booking's and document-service's `AuditRecorder.record(action, actorUserId, entityId, detail)`
have no `entityType`, so when they become real producers they must add it (booking: `Appointment`, or `User` for
`CLIENT_BLACKLISTED`; document-service: `KycDocument`, `AdvisorApplication` or `ApplicationCertificate`).

```json
{
  "eventId": "c94a7d10-55e3-4b8f-a0d2-3e6f1b8c9d47",
  "type": "AUDIT",
  "occurredAt": "2026-11-15T10:30:00Z",
  "source": "document-service",
  "payload": {
    "action": "KYC_APPROVED",
    "entityType": "KycDocument",
    "entityId": 31,
    "actorId": 1,
    "details": "Approved PASSPORT for Maryam Al-Hammadi"
  }
}
```

## Delivery rules

1. **Published after the commit.** A producer sends an event only once the database transaction that made the change
   has committed, never from inside it, the same rule the REST calls follow today. A rolled back change sends nothing.
2. **At least once.** The producer waits for the broker to acknowledge (`acks=all`, idempotent producer) and the
   consumer commits its offset only after it has handled the message, so a message is never lost but can arrive more
   than once.
3. **Idempotent consumers, keyed by `eventId`.** A consumer remembers the `eventId`s it has handled (for example a
   unique column or a processed-events table written in the same transaction as the effect) and skips a repeat, so
   a duplicate never creates a second notification or audit row.
4. **3 retries, then the DLT.** If handling fails, the consumer retries the message 3 times with a short back-off. If
   it still fails it sends the unchanged message, with the same key, to `<topic>.DLT` and moves on, so one bad message
   never blocks its partition. Messages in a DLT are looked at and replayed by hand.
5. **Ordering** holds per key only: events for the same user (notifications) or the same record (audit) are handled
   in order; there is no ordering across keys.
6. **Unknown fields are ignored** and an unknown `type` (or email `template`) is logged and skipped (not sent to the DLT), so producers can
   add fields without breaking consumers.
