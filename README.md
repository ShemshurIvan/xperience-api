# Xperience

A REST API for a marketplace where people **sell experiences** and others **book them**.
A host lists something like "Drive a Lamborghini for 30 minutes", opens time slots in a
calendar, and guests pick a slot and book it for their group.

> A portfolio project built to practice Spring Boot, JPA and API design.

---

## Features

- **Users:** register, update, view and delete accounts. Passwords are hashed with BCrypt and never returned.
- **Experiences (products):** a host creates experiences with a price, maximum group size and duration.
- **Time slots:** the host opens bookable sessions; the end time is calculated from the experience's duration.
- **Calendar:** list an experience's slots for any date range, sorted and paged.
- **Business rules enforced by the API:**
  - slots of the same experience can't overlap
  - a slot can't hold more people than the experience allows
  - a booked slot can't be moved, and its capacity can't drop below the people already booked
  - cancelling a slot cancels its bookings and removes it from guests' carts
  - users and experiences with upcoming bookings by other guests can't be deleted
  - when a user is deleted, the bookings they made are kept for the record
- **Consistent errors** in the standard `ProblemDetail` format (RFC 9457).
- **63 automated tests** (unit and integration).
- **Docker Compose** setup with PostgreSQL.

---

## Tech Stack

| Area        | Technology                                              |
|-------------|---------------------------------------------------------|
| Language    | Java 21                                                 |
| Framework   | Spring Boot 4.1 (Web MVC, Data JPA, Validation)         |
| Persistence | Hibernate, H2 (development/tests), PostgreSQL (Docker)  |
| Security    | Spring Security Crypto (BCrypt)                         |
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

### Users

| Method | Endpoint             | Description                         | Success |
|--------|----------------------|-------------------------------------|---------|
| POST   | `/createUser`        | Register a user                     | 201     |
| PUT    | `/updateUser`        | Update a user (found by email)      | 200     |
| GET    | `/get/{id}`          | Get one user                        | 200     |
| GET    | `/get`               | List users (paged)                  | 200     |
| DELETE | `/deleteUser/{id}`   | Delete a user                       | 204     |

### Experiences

| Method | Endpoint                      | Description                    | Success |
|--------|-------------------------------|--------------------------------|---------|
| POST   | `/createProduct/{ownerId}`    | Create an experience           | 201     |
| PUT    | `/updateProduct/{id}`         | Update an experience           | 200     |
| GET    | `/getProduct/{id}`            | Get one experience             | 200     |
| GET    | `/products`                   | List experiences (paged)       | 200     |
| DELETE | `/deleteProduct/{id}`         | Delete an experience           | 204     |

### Time slots

| Method | Endpoint                                           | Description                         | Success |
|--------|----------------------------------------------------|-------------------------------------|---------|
| POST   | `/createTimeSlot/{productId}`                      | Open a slot                         | 201     |
| GET    | `/listTimeSlots?productId=&from=&to=`              | Calendar for a date range (paged)   | 200     |
| GET    | `/getTimeSlot/{id}`                                | Get one slot                        | 200     |
| PUT    | `/updateTimeSlot/{id}`                             | Change start time or capacity       | 200     |
| PATCH  | `/cancelTimeSlot/{id}`                             | Cancel a slot and its bookings      | 200     |
| DELETE | `/deleteTimeSlot/{id}`                             | Delete a slot without bookings      | 204     |

### Example

```bash
# register a host
curl -X POST localhost:8080/createUser -H "Content-Type: application/json" \
  -d '{"firstName":"Ivan","lastName":"Petrov","email":"ivan@example.com","password":"secret123","age":25}'

# create an experience for user 1
curl -X POST localhost:8080/createProduct/1 -H "Content-Type: application/json" \
  -d '{"name":"Drive a Lamborghini","price":200,"maxPeople":4,"durationMinutes":30}'

# open a slot (the end time is calculated: 10:00 + 30 min)
curl -X POST localhost:8080/createTimeSlot/1 -H "Content-Type: application/json" \
  -d '{"startTime":"2030-06-01T10:00:00","maxPeopleInSlot":2}'

# calendar for that day
curl "localhost:8080/listTimeSlots?productId=1&from=2030-06-01T00:00&to=2030-06-01T23:59"
```

### Errors

| Status | When                                                                 |
|--------|----------------------------------------------------------------------|
| 400    | Invalid input; an `errors` map lists each invalid field              |
| 404    | User, experience or time slot not found                              |
| 409    | Conflict with existing data: duplicate email, overlapping slot, active bookings |
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
cp .env.example .env          # then set your own POSTGRES_PASSWORD
docker compose up --build
```

The API runs on `http://localhost:8080`, PostgreSQL on `localhost:5432`.
Stop with `docker compose down` (add `-v` to delete the database data).

### Run locally (H2, no setup)

Requires JDK 21+.

```bash
./mvnw spring-boot:run
```

Uses an in-memory H2 database that is recreated on every start.
The H2 console is at `http://localhost:8080/h2-console`.

### Run the tests

```bash
./mvnw test
```

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

---

## Project Structure

```
src/main/java/com/xperience/
├── XperienceApplication.java
├── config/        # PasswordEncoder bean
├── controller/    # REST endpoints
├── dto/           # request/response records with validation and mappers
├── entity/        # JPA entities and enums
├── exception/     # custom exceptions and the global exception handler
├── repository/    # Spring Data JPA repositories and JPQL queries
└── service/       # business logic and transactions

src/test/java/com/xperience/
├── controller/    # MockMvc integration tests through the full stack
└── service/       # service tests (Mockito unit tests and integration tests)
```

---

## Roadmap

- [x] Users, experiences and time slots with full CRUD
- [x] Calendar with date-range queries and paging
- [x] Validation, consistent error handling and tests
- [x] Docker Compose with PostgreSQL
- [ ] Cart and checkout: book slots with capacity checks
- [ ] Reservation cancellation
- [ ] Authentication with Spring Security and JWT
- [ ] REST-style URLs (`/api/users`, `/api/products/{id}/slots`)
- [ ] Search experiences by name, price and date
- [ ] Flyway database migrations
- [ ] OpenAPI / Swagger documentation
- [ ] CI with GitHub Actions