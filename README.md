# Xperience

A REST API for a marketplace where people **sell experiences** and others **book them**.
A seller lists something like "Drive a Lamborghini for 30 minutes" and opens time slots in a calendar;
buyers add slots to a cart, check out and get confirmed reservations.

> A portfolio project built with Spring Boot to practice API design, JPA, transactions and security.

---

## Features

- **Accounts and login:** register as a buyer or a seller, log in and get a signed **JWT**. Passwords are
  hashed with BCrypt and never returned.
- **Three roles:**
  - **User** (buyer): browse, add slots to a cart, check out, see and cancel their own bookings.
  - **Seller:** everything a user can, plus create experiences and manage their own experiences and time slots.
  - **Admin:** everything, plus list all users and remove experiences or accounts for moderation.
- **Experiences:** a seller sets a price, a maximum group size and a duration.
- **Time slots:** bookable sessions; the end time is calculated from the experience's duration.
- **Calendar:** an experience's slots for any date range, sorted and paged. Browsing needs no account.
- **Cart and checkout:** checkout books every cart line in one all-or-nothing transaction.
- **Reservations:** see your bookings and cancel them; the places go back to the slot.
- **Business rules enforced by the API:**
  - only an experience's own seller can change it or its time slots; admins can delete but not edit
  - only the guest who made a booking can cancel it
  - slots of the same experience can't overlap, and can't hold more people than the experience allows
  - a booked slot can't be moved, and its capacity can't drop below the people already booked
  - checkout re-checks every slot (still open, not started, enough places) and books all or nothing
  - two guests can't take the last place at the same moment (optimistic locking)
  - cancelling a slot cancels its bookings and removes it from guests' carts
  - users and experiences with upcoming bookings by other guests can't be deleted
  - when a user deletes their account, the bookings they made are kept for the record
- **Consistent errors** in the standard `ProblemDetail` format (RFC 9457).
- **113 automated tests:** unit, integration, security and transaction-rollback tests.
- **Docker Compose** setup with PostgreSQL.

---

## Tech Stack

| Area        | Technology                                                      |
|-------------|-----------------------------------------------------------------|
| Language    | Java 21                                                         |
| Framework   | Spring Boot 4.1 (Web MVC, Data JPA, Validation)                 |
| Security    | Spring Security, OAuth2 Resource Server (JWT, HS256), BCrypt    |
| Persistence | Hibernate, H2 (local runs and tests), PostgreSQL (Docker)       |
| Testing     | JUnit 5, Mockito, MockMvc, Spring Security Test, AssertJ        |
| Build / Run | Maven wrapper, Docker, Docker Compose                           |

---

## Domain Model

```
User 1 ──── * Product          a seller offers many experiences
Product 1 ── * TimeSlot        an experience has many bookable sessions
TimeSlot 1 ─ * Reservation     a session can be booked by several guests
User 1 ──── * Reservation      a guest makes many reservations
User 1 ──── * CartItem         slots a guest plans to book
TimeSlot 1 ─ * CartItem
```

| Entity        | Main fields                                                                                                 |
|---------------|-------------------------------------------------------------------------------------------------------------|
| `User`        | `firstName`, `lastName`, `age`, `email` (unique), `password` (BCrypt hash), `role` (`USER`/`SELLER`/`ADMIN`) |
| `Product`     | `name`, `description`, `price`, `maxPeople`, `durationMinutes`, `createdAt`, `owner`                        |
| `TimeSlot`    | `startTime`, `endTime`, `maxPeopleInSlot`, `bookedPeople`, `status` (`OPEN`/`FULL`/`CANCELLED`), `version`  |
| `Reservation` | `numberPeople`, `pricePaid`, `status` (`PENDING`/`CONFIRMED`/`CANCELLED`), `timeSlot`, `guest`              |
| `CartItem`    | `numberPeople`, `addedAt`, `user`, `timeSlot`                                                               |

---

## API

All endpoints accept and return JSON. Lists are paged with `?page=0&size=20&sort=id`.

**Access:** 🌐 public · 🔒 any logged-in user · 🏷️ seller (or admin) · 🛡️ admin only.
Logged-in requests send `Authorization: Bearer <token>`. `/me/...` always means the logged-in user.

### Authentication

| Method | Endpoint          | Access | Description                                           | Success |
|--------|-------------------|--------|-------------------------------------------------------|---------|
| POST   | `/auth/register`  | 🌐     | Create an account (`accountType`: `USER` or `SELLER`) | 201     |
| POST   | `/auth/login`     | 🌐     | Log in, get a JWT valid for 60 minutes                | 200     |

### Users

| Method | Endpoint            | Access | Description                                    | Success |
|--------|---------------------|--------|------------------------------------------------|---------|
| PUT    | `/updateUser`       | 🔒     | Update your own account                        | 200     |
| GET    | `/get/{id}`         | 🔒     | Get one user                                   | 200     |
| GET    | `/get`              | 🛡️     | List all users (paged)                         | 200     |
| DELETE | `/deleteUser/{id}`  | 🔒     | Delete your own account (admins: any account)  | 204     |

### Experiences

| Method | Endpoint                | Access | Description                                          | Success |
|--------|-------------------------|--------|------------------------------------------------------|---------|
| GET    | `/products`             | 🌐     | List experiences (paged)                             | 200     |
| GET    | `/getProduct/{id}`      | 🌐     | Get one experience                                   | 200     |
| POST   | `/createProduct`        | 🏷️     | Create an experience (you become its owner)          | 201     |
| PUT    | `/updateProduct/{id}`   | 🏷️     | Update your experience                               | 200     |
| DELETE | `/deleteProduct/{id}`   | 🏷️     | Delete your experience (admins: any experience)      | 204     |

### Time slots

| Method | Endpoint                               | Access | Description                              | Success |
|--------|----------------------------------------|--------|------------------------------------------|---------|
| GET    | `/listTimeSlots?productId=&from=&to=`  | 🌐     | Calendar for a date range (paged)        | 200     |
| GET    | `/getTimeSlot/{id}`                    | 🌐     | Get one slot                             | 200     |
| POST   | `/createTimeSlot/{productId}`          | 🏷️     | Open a slot on your experience           | 201     |
| PUT    | `/updateTimeSlot/{id}`                 | 🏷️     | Change start time or capacity            | 200     |
| PATCH  | `/cancelTimeSlot/{id}`                 | 🏷️     | Cancel a slot and its bookings           | 200     |
| DELETE | `/deleteTimeSlot/{id}`                 | 🏷️     | Delete a slot that has no bookings       | 204     |

### Cart

| Method | Endpoint                  | Access | Description                                  | Success |
|--------|---------------------------|--------|----------------------------------------------|---------|
| POST   | `/me/cart`                | 🔒     | Add a slot (or change its number of people)  | 201     |
| GET    | `/me/cart`                | 🔒     | View the cart with prices and total          | 200     |
| DELETE | `/me/cart/{timeSlotId}`   | 🔒     | Remove a slot from the cart                  | 204     |
| DELETE | `/me/cart`                | 🔒     | Empty the cart                               | 204     |

### Reservations

| Method | Endpoint                    | Access | Description                                  | Success |
|--------|-----------------------------|--------|----------------------------------------------|---------|
| POST   | `/me/checkout`              | 🔒     | Book everything in the cart                  | 201     |
| GET    | `/me/reservations`          | 🔒     | Your bookings, newest first (paged)          | 200     |
| GET    | `/getReservation/{id}`      | 🔒     | One booking (its guest or the seller)        | 200     |
| PATCH  | `/cancelReservation/{id}`   | 🔒     | Cancel your booking, freeing the places      | 200     |

### Example: from signup to booking

```bash
# a seller signs up, logs in, creates an experience and opens a slot
curl -X POST localhost:8080/auth/register -H "Content-Type: application/json" \
  -d '{"firstName":"Anna","lastName":"Smith","email":"anna@example.com","password":"secret123","age":30,"accountType":"SELLER"}'

SELLER=$(curl -s -X POST localhost:8080/auth/login -H "Content-Type: application/json" \
  -d '{"email":"anna@example.com","password":"secret123"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

curl -X POST localhost:8080/createProduct -H "Authorization: Bearer $SELLER" -H "Content-Type: application/json" \
  -d '{"name":"Drive a Lamborghini","price":200,"maxPeople":4,"durationMinutes":30}'

curl -X POST localhost:8080/createTimeSlot/1 -H "Authorization: Bearer $SELLER" -H "Content-Type: application/json" \
  -d '{"startTime":"2030-06-01T10:00:00","maxPeopleInSlot":2}'      # ends at 10:30 automatically

# a buyer signs up, logs in, browses the calendar and books
curl -X POST localhost:8080/auth/register -H "Content-Type: application/json" \
  -d '{"firstName":"Ivan","lastName":"Petrov","email":"ivan@example.com","password":"secret123","age":25}'

BUYER=$(curl -s -X POST localhost:8080/auth/login -H "Content-Type: application/json" \
  -d '{"email":"ivan@example.com","password":"secret123"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

curl "localhost:8080/listTimeSlots?productId=1&from=2030-06-01T00:00&to=2030-06-01T23:59"

curl -X POST localhost:8080/me/cart -H "Authorization: Bearer $BUYER" -H "Content-Type: application/json" \
  -d '{"timeSlotId":1,"numberPeople":2}'
curl -X POST localhost:8080/me/checkout -H "Authorization: Bearer $BUYER"
```

### Errors

| Status | When                                                                                    |
|--------|-----------------------------------------------------------------------------------------|
| 400    | Invalid input; an `errors` map lists each invalid field                                 |
| 401    | Missing, invalid or expired token; or wrong email/password at login                     |
| 403    | Logged in, but not allowed: wrong role, or it belongs to someone else                   |
| 404    | User, experience, time slot, cart line or reservation not found                         |
| 409    | Conflict: duplicate email, overlapping slot, not enough places, active bookings, booked at the same moment |
| 500    | Unexpected error (details are logged, never returned)                                   |

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

### Configuration

Secrets live in a `.env` file in the project folder. It's ignored by git, so they're never committed.

```bash
cp .env.example .env
```

| Variable            | Purpose                                                                 |
|---------------------|-------------------------------------------------------------------------|
| `JWT_SECRET`        | Signs login tokens; at least 32 characters (`openssl rand -base64 48`)  |
| `POSTGRES_PASSWORD` | Database password (Docker only)                                         |
| `ADMIN_EMAIL`       | The account with this email becomes admin; leave empty for none         |

### Run locally (H2)

Requires JDK 21+. Uses an in-memory database that is recreated on every start.

```bash
./mvnw spring-boot:run
```

The API runs on `http://localhost:8080`; the H2 console is at `http://localhost:8080/h2-console`.

### Run with Docker (PostgreSQL)

Requires Docker Desktop (or another Docker engine).

```bash
docker compose up --build
```

The API runs on `http://localhost:8080`, PostgreSQL on `localhost:5432`.
Stop with `docker compose down` (add `-v` to also delete the database data).

### Run the tests

```bash
./mvnw test
```

No setup needed: the tests use a test-only JWT secret from `src/test/resources`.

---

## Design Decisions

- **DTOs everywhere.** Controllers never receive or return entities. Each response record has a `from(entity)`
  mapper, so internal data like password hashes can't leak and lazy relations can't break JSON serialization.
- **Validation in two layers.** Bean Validation on request DTOs gives clear 400 errors; database constraints
  (unique, foreign keys, checks) guarantee integrity even if application code has a bug.
- **Stateless JWT authentication.** Spring Security's resource server checks each token's HS256 signature and
  expiry; there are no server sessions. The user's ID and role come from the token, never from the URL, and the
  signing secret comes from the environment.
- **Roles plus ownership.** URL rules check the role (with the hierarchy `ADMIN > SELLER > USER`), and the
  service layer checks ownership, so a seller can only manage their own experiences. Admin accounts can't be
  created through the API; they come from the `ADMIN_EMAIL` setting.
- **No account enumeration.** Wrong passwords and unknown emails get the same login error.
- **All-or-nothing checkout.** Checkout runs in one transaction. A test that runs outside a test transaction
  proves that a failing cart line really rolls back the places taken for earlier lines.
- **Optimistic locking.** `TimeSlot` has a `@Version` field, so two guests booking the last place at the same
  moment can't both succeed; the second gets a 409.
- **Overlap detection in the database.** A JPQL query checks `start < newEnd and end > newStart`; updates exclude
  the slot itself.
- **No N+1 queries.** The cart and booking lists load slots and experiences with `join fetch`; a test counts the
  SQL statements to prove it.
- **Lazy loading with `open-in-view` disabled**, so no hidden database queries run outside the service layer.
- **Soft cancel instead of delete.** Cancelled slots and reservations stay in the database as history.

---

## Project Structure

```
src/main/java/com/xperience/
├── XperienceApplication.java
├── config/        # security rules, roles, JWT encoder/decoder, admin setup
├── controller/    # REST endpoints
├── dto/           # request/response records with validation and mappers
├── entity/        # JPA entities and enums
├── exception/     # custom exceptions and the global exception handler
├── repository/    # Spring Data JPA repositories and JPQL queries
└── service/       # business logic, transactions and ownership checks

src/test/java/com/xperience/
├── controller/    # MockMvc integration tests through the full stack, incl. security
├── repository/    # query tests (e.g. proving there's no N+1)
└── service/       # service tests (Mockito unit tests and integration tests)
```