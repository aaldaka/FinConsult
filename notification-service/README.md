# notification-service

In-app notifications for FinConsult: stores them, lists them for the logged-in user, marks them as read,
and pushes live updates over Server-Sent Events.

## Table ownership

* This service **owns only the `notifications` table**.
* It **reads no other service's tables**. It never looks at `users`, `appointments` or anything else.
* It **uses user ids from the JWT** (the `userId` and `role` claims issued by the main app). It never looks a user up.
* All services share one PostgreSQL database with the existing schema, and the main app owns that schema.
  This service must never create or change tables: run it with `spring.jpa.hibernate.ddl-auto=validate`.
  It then only checks that the `notifications` table matches its entity, and refuses to start if it does not.

## Configuration

Set through environment variables, nothing is stored in the repository: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
and `JWT_SECRET` (the same secret as the main app).

## Tests

The tests run on the main app's test database, `finconsult_test`, which the main app's own test suite builds.
Run the main app's tests first. The tests look up real users in that database, because the `notifications` table has
foreign keys to `users` and `appointments`, and delete only the notifications they created. Keep `TEST_DB_URL` pointing
at `finconsult_test`, never at the demo database.
