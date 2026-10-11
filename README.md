# Xperience

A marketplace web app where people **sell experiences** and others **book them**.
For example, a host lists "Drive a Lamborghini for 30 minutes", opens time slots in a
calendar, and a guest picks a slot and books it for their group.

> This is a pet project for learning. It isn't meant for production.

---

## Core Idea

| Role      | What they can do                                                              |
|-----------|-------------------------------------------------------------------------------|
| **Host**  | Create experiences, set a price and group size, and open time slots           |
| **Guest** | Browse experiences, add time slots to a cart, and book them                   |
| Any user  | Register and manage their profile                                             |

A user can be a host and a guest at the same time.

---

## Tech Stack

- **Java 21**
- **Spring Boot 4.1** (Web MVC, Data JPA, Validation)
- **Spring Security Crypto**: BCrypt password hashing
- **H2**: in-memory database for development, with a web console
- **JUnit 5, Mockito, MockMvc**: tests
- **Maven**: build tool (a wrapper is included)

---

## Domain Model

```
User 1 ──── * Product          a host offers many experiences
Product 1 ── * TimeSlot        an experience has many bookable slots
TimeSlot 1 ─ * Reservation     a slot can be booked by several guests
User 1 ──── * Reservation      a guest makes many reservations
User 1 ──── * CartItem         a guest's cart holds slots they plan to book
TimeSlot 1 ─ * CartItem
```

### `User` (`users`)
`id`, `firstName`, `lastName`, `age`, `email` (unique), `password` (BCrypt hash), `products`

### `Product` (`products`): an experience
`id`, `name`, `description` (up to 2000 characters), `price`, `maxPeople`, `createdAt`,
`owner` (→ User), `timeSlots`

### `TimeSlot` (`time_slots`)
`id`, `startTime`, `endTime`, `maxPeopleInSlot`, `bookedPeople`, `status`
(`OPEN`, `FULL`, `CANCELLED`), `version`, `product` (→ Product)

- A product can't have two slots with the same start time (unique `product_id` + `start_time`).
- `@Version` (optimistic locking) prevents two guests from booking the last place at the same time.

### `Reservation` (`reservations`)
`id`, `numberPeople`, `pricePaid`, `status` (`PENDING`, `CONFIRMED`, `CANCELLED`),
`timeSlot` (→ TimeSlot), `guest` (→ User)

### `CartItem` (`cart_items`)
`id`, `numberPeople`, `addedAt`, `user` (→ User), `timeSlot` (→ TimeSlot)

- The same slot can be in a user's cart only once (unique `user_id` + `time_slot_id`).

---

## REST API

### Available now

| Method | Endpoint       | Description                                   | Success | Errors   |
|--------|----------------|-----------------------------------------------|---------|----------|
| POST   | `/createUser`  | Register a new user                           | 201     | 400, 409 |
| PUT    | `/changeUser`  | Update a user's profile (found by email)      | 200     | 400, 404 |

Example request:

```bash
curl -i -X POST localhost:8080/createUser \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Ivan","lastName":"Petrov","email":"ivan@example.com","password":"secret123","age":25}'
```

The response never contains the password.

### Planned

| Method | Endpoint                               | Description                          |
|--------|----------------------------------------|--------------------------------------|
| GET    | `/api/users/{id}`                      | Get a user profile                   |
| GET    | `/api/users`                           | List users (paged)                   |
| POST   | `/api/users/{id}/products`             | Create an experience                 |
| GET    | `/api/products`, `/api/products/{id}`  | Browse experiences                   |
| POST   | `/api/products/{id}/slots`             | Open a time slot                     |
| GET    | `/api/products/{id}/slots?from=&to=`   | Calendar of slots for a date range   |
| POST   | `/api/users/{id}/cart`                 | Add a slot to the cart               |
| GET    | `/api/users/{id}/cart`                 | View the cart with prices and total  |
| POST   | `/api/users/{id}/cart/checkout`        | Book everything in the cart          |
| PATCH  | `/api/reservations/{id}/cancel`        | Cancel a reservation                 |

### Error responses

Errors use the standard `ProblemDetail` format (RFC 9457):

| Status | When                                                   |
|--------|--------------------------------------------------------|
| 400    | Validation failed; an `errors` map lists each bad field |
| 404    | User not found                                         |
| 409    | Email already registered                               |
| 500    | Unexpected error (details are logged, not returned)    |

```json
{
  "status": 400,
  "detail": "Validation Failed",
  "errors": {
    "email": "must be a well-formed email address",
    "age": "must be greater than or equal to 18"
  }
}
```

---

## Project Structure

```
src/main/java/com/xperience/
├── XperienceApplication.java   # entry point
├── config/                     # beans such as the BCrypt PasswordEncoder
├── controller/                 # REST controllers
├── dto/                        # request/response records (user, product, timeslot, reservation, cart)
├── entity/                     # JPA entities and enums
├── exception/                  # custom exceptions and the global exception handler
├── repository/                 # Spring Data JPA repositories
└── service/                    # business logic

src/test/java/com/xperience/
├── controller/                 # MockMvc tests through the full stack (H2)
└── service/                    # unit tests with Mockito
```

---

## Getting Started

### Prerequisites
- JDK 21+

### Run
```bash
./mvnw spring-boot:run
```
The app starts on `http://localhost:8080`.

### Run tests
```bash
./mvnw test
```

### H2 Console
Open `http://localhost:8080/h2-console`. Use the JDBC URL printed in the startup log.

---

## Roadmap

- [x] Entities and relationships: users, experiences, time slots, reservations, cart
- [x] DTOs with validation
- [x] BCrypt password hashing
- [x] Global error handling with `ProblemDetail`
- [x] User registration and update, with tests
- [ ] Move user endpoints to `/api/users` and add read endpoints
- [ ] Experience, time slot, cart and reservation services and controllers
- [ ] Booking with capacity checks (no overbooking)
- [ ] Authentication (Spring Security + JWT)
- [ ] Search and filters (price, date)
- [ ] Reviews and ratings
- [ ] Image uploads for experiences
- [ ] Frontend (e.g. React)
- [ ] Mock payments (e.g. Stripe test mode)
- [ ] PostgreSQL, Flyway migrations and Docker

---

## License

This is a personal pet project, free to use for learning.