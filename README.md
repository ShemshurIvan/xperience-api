# Xperience

A REST API for a marketplace where people **sell experiences** and others **book them**.
A host lists something like "Drive a Lamborghini for 30 minutes", opens time slots in a
calendar, and guests add slots to a cart, check out and get confirmed reservations.

> A portfolio project built to practice Spring Boot, JPA and API design.

---

## Features

- **Login with JWT:** register, log in and get a signed token; every protected request sends it as
  `Authorization: Bearer <token>`. Passwords are hashed with BCrypt and never returned.
- **Users:** update, view and delete your own account.
- **Experiences (products):** a host creates experiences with a price, maximum group size and duration.
- **Time slots:** the host opens bookable sessions; the end time is calculated from the experience's duration.
- **Calendar:** list an experience's slots for any date range, sorted and paged. Browsing is public.
- **Cart and checkout:** add slots to a cart, then book everything in one all-or-nothing transaction.
- **Reservations:** see your bookings, cancel them, and the places go back to the slot.
- **Ownership rules:** only the owner can change or delete an experience and its slots; only the guest
  can cancel a booking. Everyone else gets 403.
- **Business rules enforced by the API:**
  - slots of the same experience can't overlap
  - a slot can't hold more people than the experience allows
  - a booked slot can't be moved, and its capacity can't drop below the people already booked
  - cancelling a slot cancels its bookings and removes it from guests' carts
  - users and experiences with upcoming bookings by other guests can't be deleted
  - when a user is deleted, the bookings they made are kept for the record
  - checkout re-checks every slot (still open, not started, enough places) and books all or nothing
- **Consistent errors** in the standard `ProblemDetail` format (RFC 9457).
- **113 automated tests** (unit and integration), including security and rollback tests.
- **Docker Compose** setup with PostgreSQL.

---

## Tech Stack

| Area        | Technology                                              |
|-------------|---------------------------------------------------------|
| Language    | Java 21                                                 |
| Framework   | Spring Boot 4.1 (Web MVC, Data JPA, Validation)         |
| Persistence | Hibernate, H2 (development/tests), PostgreSQL (Docker)  |
| Security    | Spring Security, OAuth2 Resource Server (JWT, HS256), BCrypt |
| Testing     | JUnit 5, Mockito, MockMvc, AssertJ                      |
| Build / Run | Maven wrapper, Docker, Docker Compose                   |

---

## Domain Model

```
User 1 ──── * Product          a host offers many experiences
Product 1 ── * TimeSlot        an experience has many bookable sessions
TimeSlot 1 ─ * Reservation     a session can be booked by several guests
User 1 ──── * Reservation      a guest makes many reservations
User 1 ──── * CartItem         slots a guest plans to book
TimeSlot 1 ─ * CartItem
```

| Entity        | Main fields                                                                                           |
|---------------|-------------------------------------------------------------------------------------------------------|
| `User`        | `firstName`, `lastName`, `age`, `email` (unique), `password` (BCrypt hash)                            |
| `Product`     | `name`, `description`, `price`, `maxPeople`, `durationMinutes`, `createdAt`, `owner`                  |
| `TimeSlot`    | `startTime`, `endTime`, `maxPeopleInSlot`, `bookedPeople`, `status` (`OPEN`/`FULL`/`CANCELLED`), `version` |
| `Reservation` | `numberPeople`, `pricePaid`, `status` (`PENDING`/`CONFIRMED`/`CANCELLED`), `timeSlot`, `guest`        |
| `CartItem`    | `numberPeople`, `addedAt`, `user`, `timeSlot`                                                         |

---

## API

All endpoints accept and return JSON. Lists are paged with `?page=0&size=20&sort=id`.
🔒 = requires `Authorization: Bearer <token>` from `/auth/login`. `/me/...` always means the logged-in user.

### Authentication

| Method | Endpoint          | Description                              | Success |
|--------|-------------------|------------------------------------------|---------|
| POST   | `/auth/register`  | Create an account                        | 201     |
| POST   | `/auth/login`     | Log in, get a JWT (valid for 60 minutes) | 200     |

### Users

| Method | Endpoint             | Description                        | Success |
|--------|----------------------|------------------------------------|---------|
| PUT    | `/updateUser` 🔒      | Update your own account            | 200     |
| GET    | `/get/{id}` 🔒        | Get one user                       | 200     |
| GET    | `/get` 🔒             | List users (paged)                 | 200     |
| DELETE | `/deleteUser/{id}` 🔒 | Delete your own account            | 204     |

### Experiences

| Method | Endpoint                   | Description                        | Success |
|--------|----------------------------|------------------------------------|---------|
| POST   | `/createProduct` 🔒         | Create an experience (you're the owner) | 201 |
| PUT    | `/updateProduct/{id}` 🔒    | Update your experience             | 200     |
| GET    | `/getProduct/{id}`         | Get one experience                 | 200     |
| GET    | `/products`                | List experiences (paged)           | 200     |
| DELETE | `/deleteProduct/{id}` 🔒    | Delete your experience             | 204     |

### Time slots

| Method | Endpoint                                  | Description                          | Success |
|--------|-------------------------------------------|--------------------------------------|---------|
| POST   | `/createTimeSlot/{productId}` 🔒           | Open a slot on your experience       | 201     |
| GET    | `/listTimeSlots?productId=&from=&to=`     | Calendar for a date range (paged)    | 200     |
| GET    | `/getTimeSlot/{id}`                       | Get one slot                         | 200     |
| PUT    | `/updateTimeSlot/{id}` 🔒                  | Change start time or capacity        | 200     |
| PATCH  | `/cancelTimeSlot/{id}` 🔒                  | Cancel a slot and its bookings       | 200     |
| DELETE | `/deleteTimeSlot/{id}` 🔒                  | Delete a slot without bookings       | 204     |

### Cart

| Method | Endpoint                        | Description                                  | Success |
|--------|---------------------------------|----------------------------------------------|---------|
| POST   | `/me/cart` 🔒                    | Add a slot (or change its number of people)  | 201     |
| GET    | `/me/cart` 🔒                    | View the cart with prices and total          | 200     |
| DELETE | `/me/cart/{timeSlotId}` 🔒       | Remove a slot from the cart                  | 204     |
| DELETE | `/me/cart` 🔒                    | Empty the cart                               | 204     |

### Reservations

| Method | Endpoint                       | Description                                 | Success |
|--------|--------------------------------|---------------------------------------------|---------|
| POST   | `/me/checkout` 🔒               | Book everything in the cart                 | 201     |
| GET    | `/me/reservations` 🔒           | Your bookings, newest first (paged)         | 200     |
| GET    | `/getReservation/{id}` 🔒       | One booking (its guest or the host)         | 200     |
| PATCH  | `/cancelReservation/{id}` 🔒    | Cancel your booking, freeing the places     | 200     |

### Example

```bash
# register and log in
curl -X POST localhost:8080/auth/register -H "Content-Type: application/json" \
  -d '{"firstName":"Ivan","lastName":"Petrov","email":"ivan@example.com","password":"secret123","age":25}'

TOKEN=$(curl -s -X POST localhost:8080/auth/login -H "Content-Type: application/json" \
  -d '{"email":"ivan@example.com","password":"secret123"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

# create an experience and open a slot (the end time is calculated: 10:00 + 30 min)
curl -X POST localhost:8080/createProduct -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Drive a Lamborghini","price":200,"maxPeople":4,"durationMinutes":30}'
curl -X POST localhost:8080/createTimeSlot/1 -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"startTime":"2030-06-01T10:00:00","maxPeopleInSlot":2}'

# book it: add to cart, then check out
curl -X POST localhost:8080/me/cart -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"timeSlotId":1,"numberPeople":2}'
curl -X POST localhost:8080/me/checkout -H "Authorization: Bearer $TOKEN"
```

### Errors

| Status | When                                                                 |
|--------|----------------------------------------------------------------------|
| 400    | Invalid input; an `errors` map lists each invalid field              |
| 401    | Missing, invalid or expired token; or wrong email/password at login  |
| 403    | Logged in, but it belongs to someone else                            |
| 404    | User, experience, time slot, cart line or reservation not found      |
| 409    | Conflict: duplicate email, overlapping slot, not enough places, active bookings, booked at the same moment |
| 500    | Unexpected error (details are logged, never returned)                |

```json
{
  "status": 400,
  "title": "Bad Request",
  "detail": "Validation Failed",
  "errors": {
    "startTime": "must be a future date",
    "maxPeopleInSlot": "must be greater than or equal to 1"
  }
}
```

---

## Getting Started

### Run with Docker (PostgreSQL)

Requires Docker Desktop (or another Docker engine).

```bash
cp .env.example .env          # then set your own POSTGRES_PASSWORD and JWT_SECRET
docker compose up --build
```

The API runs on `http://localhost:8080`, PostgreSQL on `localhost:5432`.
Stop with `docker compose down` (add `-v` to delete the database data).

### Run locally (H2)

Requires JDK 21+. The app reads `JWT_SECRET` from the `.env` file (create it as above;
`openssl rand -base64 48` gives a good secret).

```bash
./mvnw spring-boot:run
```

Uses an in-memory H2 database that is recreated on every start.
The H2 console is at `http://localhost:8080/h2-console`.

### Run the tests

```bash
./mvnw test
```

Tests need no setup: they use a test-only JWT secret from `src/test/resources`.

---

## Design Decisions

- **DTOs everywhere.** Controllers never receive or return entities. Each response record has a `from(entity)`
  mapper, so internal data (like password hashes) can't leak, and lazy relations can't break JSON serialization.
- **Validation in two layers.** Bean Validation on request DTOs gives clear 400 errors; database constraints
  (unique, foreign keys, checks) guarantee integrity even if application code has a bug.
- **Optimistic locking.** `TimeSlot` has a `@Version` field, so two guests booking the last place at the same
  moment can't both succeed.
- **Overlap detection in the database.** A JPQL query checks `start < newEnd and end > newStart`; updates exclude
  the slot itself.
- **Transactions in the service layer.** Multi-step operations (such as deleting a user) run in one transaction,
  so they either complete fully or not at all. Reads use `readOnly = true`.
- **Lazy loading with `open-in-view` disabled**, so no hidden database queries run outside the service layer.
- **Soft cancel instead of delete.** Cancelled slots and reservations stay in the database as history.
- **Stateless JWT authentication.** Spring Security's resource server checks each token's HS256 signature and
  expiry; there are no server sessions. The user's ID comes from the token, never from the URL, and the
  secret is read from the environment, never committed.
- **Ownership checks in the service layer** return 403, and login failures use one generic message so
  nobody can find out which emails are registered.
- **All-or-nothing checkout,** proven by a test that runs without a test transaction so the real rollback
  can be checked.

---

## Project Structure

```
src/main/java/com/xperience/
├── XperienceApplication.java
├── config/        # security rules, JWT encoder/decoder, PasswordEncoder
├── controller/    # REST endpoints
├── dto/           # request/response records with validation and mappers
├── entity/        # JPA entities and enums
├── exception/     # custom exceptions and the global exception handler
├── repository/    # Spring Data JPA repositories and JPQL queries
└── service/       # business logic and transactions

src/test/java/com/xperience/
├── controller/    # MockMvc integration tests through the full stack, incl. security
├── repository/    # query tests (e.g. proving there's no N+1)
└── service/       # service tests (Mockito unit tests and integration tests)
```

---

## Roadmap

- [x] Users, experiences and time slots with full CRUD
- [x] Calendar with date-range queries and paging
- [x] Validation, consistent error handling and tests
- [x] Docker Compose with PostgreSQL
- [x] Cart and checkout: book slots with capacity checks
- [x] Reservation cancellation
- [x] Authentication with Spring Security and JWT, with ownership checks
- [ ] REST-style URLs (`/api/users`, `/api/products/{id}/slots`)
- [ ] Search experiences by name, price and date
- [ ] Flyway database migrations
- [ ] OpenAPI / Swagger documentation
- [ ] CI with GitHub Actions