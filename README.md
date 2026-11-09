# Appointment Booking System

Multi-tenant booking backend for any business that sells time: clinics and hospitals (OPD consultations), service
centres (repair visits), salons, labs, tutors, meeting rooms. Each business sets its own providers, services,
weekly hours, leave, hold time and cancellation rules; customers search free slots and book.

Java 21, Spring Boot 3, Spring Security (JWT), Spring Data JPA, MySQL, Flyway, Kafka, Docker, Kubernetes.

## Design

One deployable split into modules that only call each other's services, not each other's tables:

```
tenant        businesses, users, login (JWT carries tenant + role)
catalog       providers (doctor / technician / room) and service types (duration + buffer + price)
schedule      weekly hours, time off, slot search
booking       hold -> confirm -> cancel / reschedule / attendance, hold expiry, reminders
waitlist      "tell me if a slot opens on this day"
notification  Kafka consumer that turns events into customer messages
```

I kept it a modular monolith on purpose: booking, schedule and waitlist change together and share one
transaction (cancel = free the time + offer it to the waitlist). Splitting them into services would turn that into
a distributed transaction for no gain. Anything that can happen later (messages, reminders, other systems)
leaves through Kafka, so that part can move out into its own service without touching the booking code.

## The parts worth reading

**No double booking, even with many app instances.** Every booking inserts one row per 5-minute unit it covers
(appointment + buffer) into `booked_unit`, whose primary key is `(provider_id, unit_start)`. Two people racing for
the same or overlapping time both try to insert the same unit; the database lets one through and the other
transaction rolls back with `SLOT_TAKEN`. `BookingFlowTest` sends 30 customers at one slot at the same instant:
exactly one wins. No locks held while the user decides, no "check then insert" gap.

**Holds.** Picking a slot creates a HELD booking that expires after the business's hold time (default 10 min).
A scheduled job releases expired holds. It selects with `FOR UPDATE SKIP LOCKED`, so running 3 pods does not
release the same hold three times.

**Time zones.** Hours are stored as local times, bookings as UTC instants, and slot search works in the
business's zone, so "09:00" means 09:00 in Hyderabad or in Dubai.

**Rules per business.** Customers can't cancel or move a booking inside the cut-off (e.g. 2 hours); staff can.
Reschedule is one transaction: if the new time is taken, the old booking is untouched.

**Waitlist.** When a booking is cancelled or moved, the first person waiting for that provider, service and day is
notified (`WaitlistSlotOpened`). The slot is not reserved for them; they book through the normal flow.

**Reliable events.** Confirm, cancel, reschedule, reminders and waitlist offers are written to an outbox table in
the same transaction as the change and published to `booking.events` afterwards. The notification consumer keys
its log on the event id, so a redelivered event never sends a second SMS.

**Tenant isolation.** The tenant id comes only from the signed token. Every query is scoped by it, and an id
from another business returns 404, not 403.

## API (all under /api)

| | |
|---|---|
| `POST /tenants` | a business signs up (creates its admin) |
| `POST /tenants/{id}/customers`, `POST /auth/login` | customer sign-up, login for everyone |
| `POST /services`, `POST /providers`, `PUT /providers/{id}/working-hours`, `POST /providers/{id}/time-off` | admin / staff setup |
| `GET /slots?providerId&serviceId&date` | free start times |
| `POST /bookings`, `POST /bookings/{id}/confirm` | hold, then confirm |
| `POST /bookings/{id}/cancel`, `/reschedule`, `/attendance` | changes |
| `GET /bookings?providerId&date`, `GET /bookings/mine` | front-desk day sheet, customer history |
| `POST /waitlist`, `GET /notifications` | waitlist, my messages |

Swagger UI: `/swagger-ui.html`.

## Run

```bash
docker compose up --build
```

Tests need no Docker (H2 in MySQL mode, embedded Kafka); time is controlled with a test clock, so hold expiry and
reminders are tested without sleeping:

```bash
mvn verify
```

`k8s/deployment.yaml` is what I deploy on EKS: 3 replicas, HPA, PodDisruptionBudget, readiness/liveness on the
actuator probes, ALB ingress, RDS MySQL and MSK for Kafka.

## Not done yet

- Payments at confirmation (a provider callback would move HELD to CONFIRMED).
- Recurring appointments (weekly physiotherapy, monthly service).
- SMS/WhatsApp provider; `MessageSender` only logs.
- A provider calendar sync (Google/Outlook).
