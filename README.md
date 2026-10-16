# RideSync

![CI](https://github.com/Arya089/RideSync/actions/workflows/ci.yml/badge.svg)

Backend for a carpooling and ride-sharing platform. Drivers post rides, passengers search by location, book seats and pay, and the driver's position can be streamed live during the trip. The interesting engineering problems are solved and tested: **geospatial search**, **race-free seat booking** under concurrent load, and **signed payment confirmation** with a webhook backup.

Built solo with Java and Spring Boot.

## Highlights

- **Geospatial ride search** with PostGIS: find rides whose start and end are within a chosen radius (in real meters) of where the passenger wants to go, backed by GiST indexes.
- **No overselling, proven by test:** 10 simultaneous requests for a ride with 3 seats produce exactly 3 successful bookings and 7 rejections, zero seats left, and no leftover locks.
- **Two-layer concurrency protection:** a Redis distributed lock plus an atomic conditional SQL update, so correctness holds even if the lock fails.
- **Secure payments:** HMAC-SHA256 signature verification (constant-time compare), idempotent confirmation, and a server-to-server webhook as the source of truth.
- **Self-healing bookings:** unpaid bookings expire automatically and release their seats; a late payment on an expired booking is flagged `REFUND_REQUIRED` instead of being silently lost.
- **Live driver tracking over WebSocket:** the driver streams GPS points, confirmed passengers receive them, and the last position is cached in Redis for late joiners.
- **Stateless JWT authentication** with role-based access (`DRIVER`, `PASSENGER`).
- **Containerised and CI-checked:** one command runs the whole stack in Docker, and GitHub Actions runs the tests and builds the image on every push.

## Tech stack

| Area | Technology |
|---|---|
| Language / framework | Java 17, Spring Boot 4.1 (Spring Framework 7, Hibernate 7) |
| Database | PostgreSQL 16 with PostGIS 3.4 (Hibernate Spatial) |
| Cache / locking | Redis 7 |
| Real time | Spring WebSocket |
| Auth | Spring Security, JWT (jjwt), BCrypt |
| Payments | Razorpay (orders API; mock mode for local development) |
| Messaging | Apache Kafka (provisioned in Docker Compose, see roadmap) |
| Infra / CI | Docker, Docker Compose, GitHub Actions |

## Architecture

Feature-first package layout, so everything for one feature lives together:

```
com.ridesync
|-- auth/       JWT service, auth filter, register and login
|-- user/       user entity and profile
|-- ride/       ride entity, PostGIS search, start/complete lifecycle
|-- booking/    bookings, Redis seat lock, expiry job
|-- payment/    payment orders, signature verification, webhook
|-- tracking/   WebSocket live location, handshake authentication
`-- common/     security config, exception handling, scheduling
```

Request flow for protected endpoints: `JwtAuthFilter` reads the `Bearer` token, validates it, and places the user in the security context; method-level `@PreAuthorize` then enforces the role. No server-side sessions are stored.

## Key design decisions

### 1. Geospatial search

Rides store origin and destination as PostGIS `geometry(Point, 4326)` columns. Search casts to `geography` and uses `ST_DWithin`, so the radius is measured in meters on the Earth's surface rather than in degrees. GiST indexes are built on the same `CAST(... AS geography)` expression the query uses, which is what allows PostGIS to use them.

### 2. Preventing double booking

The classic bug: two requests both read "1 seat left", and both succeed. RideSync prevents it in two layers:

1. **Redis lock per ride** (`booking:lock:{rideId}`), acquired with `SET NX EX` (atomic, with a TTL so a crashed server cannot hold it forever). The lock is released with a small Lua script that deletes the key only if it still holds the caller's token, so a slow request can never free a lock that expired and was re-acquired by someone else.
2. **Atomic conditional update** in the database: `UPDATE rides SET available_seats = available_seats - :n WHERE id = :id AND available_seats >= :n`. The database itself refuses to go below zero.

Ordering matters: **lock, then transaction, then commit, then unlock.** Releasing the lock before the commit would let the next request read stale seat counts, so the booking service uses a `TransactionTemplate` instead of `@Transactional`.

### 3. Payment flow

```
Passenger books  ->  booking PENDING (seats reserved)
        |
POST /api/payments/initiate  ->  gateway order created (reused if one already exists)
        |
Passenger pays via gateway checkout
        |
   +----+--------------------------+
   |                               |
POST /api/payments/verify       POST /api/payments/webhook
(client callback)               (server-to-server, source of truth)
   |                               |
   +--------------+----------------+
                  |
   HMAC signature verified -> booking PENDING -> CONFIRMED
```

- **Signature verification:** HMAC-SHA256 over `order_id|payment_id` for the client callback, and over the raw request body for the webhook, both compared in constant time.
- **Idempotent:** the callback and the webhook can both arrive, repeatedly, in any order. Confirming twice is safe.
- **No race with expiry:** booking status changes use an atomic compare-and-set (`PENDING -> CONFIRMED` or `PENDING -> CANCELLED`), so exactly one side wins.
- **No database connection held during the gateway HTTP call:** payment initiation is split into short transactions around the external call.

### 4. Booking expiry

A scheduled job cancels bookings that stay `PENDING` past a configurable window (default 15 minutes) and returns their seats. If payment arrives after expiry, the payment is marked `REFUND_REQUIRED` rather than discarded.

### 5. Live tracking

The driver starts a ride (`POST /api/rides/{id}/start`) and then sends `{"lat":..,"lng":..}` messages over `ws://host/ws/location/{rideId}`. Passengers with a **confirmed** booking on that ride connect to the same endpoint and receive each point.

- **Handshake authentication:** browsers cannot set headers on a WebSocket, so the JWT is passed as `?token=` and checked during the upgrade, followed by a ride-level check (the ride's driver may send, confirmed passengers may watch, everyone else is rejected).
- **Late joiners:** the last position is cached in Redis (`driver:location:{rideId}`, 60-second TTL), so a passenger who connects mid-ride sees the driver immediately. `GET /api/rides/{id}/location` returns the same value over REST.
- **Validation:** only the driver can send, only while the ride is `STARTED`, and coordinates must be in range.
- **Completing a ride** (`POST /api/rides/{id}/complete`) marks the ride and its confirmed bookings `COMPLETED`.

Limitation: connected sessions are tracked in memory, which is correct for one server instance. Running several instances would need Redis pub/sub to fan messages out between them.

## API overview

| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/api/auth/register` | public | Register as `DRIVER` or `PASSENGER` |
| POST | `/api/auth/login` | public | Log in, receive a JWT |
| GET | `/api/users/profile` | authenticated | Current user's profile |
| POST | `/api/rides/post` | driver | Post a ride |
| GET | `/api/rides/search` | authenticated | Radius search by origin, destination, date, seats |
| GET | `/api/rides/my-rides` | driver | Rides posted by the driver |
| GET | `/api/rides/{id}` | authenticated | Ride details |
| POST | `/api/rides/{id}/start` | driver (owner) | Start the ride |
| POST | `/api/rides/{id}/complete` | driver (owner) | Complete the ride and its confirmed bookings |
| GET | `/api/rides/{id}/location` | driver or confirmed passenger | Latest cached driver position |
| POST | `/api/rides/{id}/book` | passenger | Book seats (race-safe) |
| GET | `/api/rides/my-bookings` | passenger | Passenger's bookings |
| POST | `/api/bookings/{id}/cancel` | passenger | Cancel a booking, seats are returned |
| POST | `/api/payments/initiate` | passenger | Create a payment order for a booking |
| POST | `/api/payments/verify` | passenger | Verify the client-side payment signature |
| GET | `/api/payments/history` | passenger | Payment history |
| POST | `/api/payments/webhook` | public (HMAC) | Gateway webhook |
| WS | `/ws/location/{rideId}?token=` | driver or confirmed passenger | Live location stream |

## Running locally

**Prerequisites:** JDK 17 (newer versions up to 25 work with Spring Boot 4.1), Maven, Docker Desktop.

```bash
# 1. Start PostgreSQL/PostGIS, Redis and Kafka
docker compose up -d

# 2. Run the application
mvn spring-boot:run
```

The API starts on `http://localhost:8080`. PostgreSQL is exposed on host port **55432** (chosen to avoid clashing with a local PostgreSQL install on 5432).

### Run everything in Docker

```bash
docker compose --profile full up -d --build
```

This builds the application image and starts it on port 8080 alongside PostgreSQL, Redis and Kafka. Check `http://localhost:8080/actuator/health`. Stop it with `docker compose --profile full down` (data is kept in Docker volumes).

### Tests

```bash
mvn test
```

GitHub Actions runs the tests and builds the Docker image on every push.

### Configuration

All secrets come from environment variables, with safe local defaults:

| Variable | Default | Purpose |
|---|---|---|
| `JWT_SECRET` | dev placeholder | Signing key for JWTs (use a long random value in production) |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | local Docker values | Database connection |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis connection |
| `PAYMENT_MOCK` | `true` | `true` simulates the gateway locally; `false` calls Razorpay |
| `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` | mock values | Razorpay test or live keys |
| `RAZORPAY_WEBHOOK_SECRET` | mock value | Webhook signature secret |
| `BOOKING_EXPIRY_MINUTES` | `15` | Time before an unpaid booking expires |

### Try it

```bash
# Register a driver
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"driver@test.com","password":"Password123","fullName":"Ravi Driver","phone":"9999999999","role":"DRIVER"}'

# Search for rides near Bangalore -> Mysore on a date (use the token from login)
curl "http://localhost:8080/api/rides/search?originLat=12.9767&originLng=77.5713&destLat=12.3052&destLng=76.6553&date=2026-10-20&seats=1&radiusKm=10" \
  -H "Authorization: Bearer <token>"
```

## Verified behavior

Tested end to end against a running instance:

- **Concurrency:** 10 simultaneous booking requests for 3 seats resulted in exactly 3 x `201` and 7 x `409`, 0 seats remaining (never negative), 3 bookings in the database, and no lock keys left in Redis.
- **Payments:** a wrong signature is rejected (`400`); a correct signature confirms the booking; repeating verification is idempotent; paying a confirmed booking is rejected (`409`).
- **Webhook:** an invalid signature returns `401`; a valid signature confirms the booking with no client callback; replaying the same webhook is harmless.
- **Expiry:** stale unpaid bookings are cancelled by the scheduler and their seats are released.
- **Ride lifecycle:** only the owning driver can start or complete a ride; completing it marks the confirmed bookings `COMPLETED`.
- **Tracking access:** WebSocket connections without a token, or from a user with no confirmed booking, are refused.
- **Access control:** drivers cannot book, passengers cannot post rides (`403`), and protected endpoints without a token return `401`.
- **Container run:** the full stack starts from one command and the health endpoint reports `UP`.

## Known limitations and roadmap

- Payment runs in **mock mode** by default; real Razorpay checkout needs a frontend widget.
- Refunds are flagged (`REFUND_REQUIRED`) but not yet issued automatically, and cancelling a paid booking does not trigger a refund yet.
- Kafka is provisioned in Docker Compose but not wired into the application yet; planned for ride and booking events.
- Live-tracking sessions are held in memory (single instance); scaling out would use Redis pub/sub.
- Planned: in-app chat, a React frontend, a hosted deployment, and broader integration tests.
- JWT only (no refresh tokens yet).

## Author

**Arya Mishra**, [github.com/Arya089](https://github.com/Arya089)
