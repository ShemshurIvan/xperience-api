# Xperience

A marketplace web app where people **sell experiences** and others **book them**.
For example, one user lists "Drive a Lamborghini for 30 minutes," and another user
finds it, picks a date, and reserves it.

> This is a pet project for learning. It isn't meant for production.

---

## Core Idea

| Role     | What they can do                                                        |
|----------|-------------------------------------------------------------------------|
| **Host** | Create, edit, and remove experiences they offer (title, description, price, location, available dates, capacity) |
| **Guest**| Browse and search experiences, view details, and make reservations      |
| Any user | Register, log in, and manage their profile and reservations             |

A user can be a host and a guest at the same time.

---

## Tech Stack

- **Java 21**
- **Spring Boot 4.1** (Web MVC, Data JPA, Validation)
- **H2**: in-memory database for development, with a web console
- **Maven**: builds the project (a wrapper is included)

---

## Domain Model (planned)

```
User 1 ──── * Product (Experience)      a host offers many experiences
User 1 ──── * Reservation               a guest makes many reservations
Product 1 ── * Reservation              an experience has many bookings
```

### `User`
- `id`, `name`, `email`, `password` (hashed), `createdAt`

### `Product` (an Experience)
- `id`, `title`, `description`, `price`, `location`, `durationMinutes`,
  `capacity`, `host` (→ User), `createdAt`

### `Reservation`
- `id`, `product` (→ Product), `guest` (→ User), `date`, `participants`,
  `status` (`PENDING`, `CONFIRMED`, `CANCELLED`), `totalPrice`, `createdAt`

---

## Planned REST API

### Users
| Method | Endpoint           | Description         |
|--------|--------------------|---------------------|
| POST   | `/api/users`       | Register a new user |
| GET    | `/api/users/{id}`  | Get a user profile  |
| PUT    | `/api/users/{id}`  | Update a profile    |

### Experiences
| Method | Endpoint                 | Description                          |
|--------|--------------------------|--------------------------------------|
| GET    | `/api/products`          | List and search experiences          |
| GET    | `/api/products/{id}`     | Get one experience's details         |
| POST   | `/api/products`          | Create an experience (host)          |
| PUT    | `/api/products/{id}`     | Update an experience (owner only)    |
| DELETE | `/api/products/{id}`     | Delete an experience (owner only)    |

### Reservations
| Method | Endpoint                          | Description                     |
|--------|-----------------------------------|---------------------------------|
| POST   | `/api/reservations`               | Book an experience              |
| GET    | `/api/reservations/{id}`          | Get reservation details         |
| GET    | `/api/users/{id}/reservations`    | List a user's reservations      |
| PATCH  | `/api/reservations/{id}/cancel`   | Cancel a reservation            |

---

## Project Structure

```
src/main/java/com/xperience/
├── XperienceApplication.java         # entry point
├── entity/                           # JPA entities (User, Product, Reservation)
├── repository/                       # Spring Data repositories       (planned)
├── service/                          # business logic                 (planned)
├── controller/                       # REST controllers               (planned)
├── dto/                              # request/response objects       (planned)
└── exception/                        # custom errors + global handler (planned)
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
Once you configure it in `application.properties`, the H2 console is available at
`http://localhost:8080/h2-console`.

---

## Roadmap

- [ ] Implement the `User`, `Product`, and `Reservation` entities and their relationships
- [ ] Add repositories, services, and REST controllers
- [ ] Add input validation and global error handling
- [ ] Prevent overbooking with capacity and date availability checks
- [ ] Add authentication (Spring Security + JWT)
- [ ] Add search and filters (price, location, date)
- [ ] Add reviews and ratings for experiences
- [ ] Add image uploads for experiences
- [ ] Build a frontend (e.g. React or Thymeleaf)
- [ ] Add mock payment integration (e.g. Stripe test mode)
- [ ] Switch to PostgreSQL and add Docker support

---

## License

This is a personal pet project, free to use for learning.
