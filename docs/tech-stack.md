# Tech Stack & Project Structure

## Java Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Java 21 (LTS) — records for DTOs, sealed classes for domain constraints |
| **Framework** | Spring Boot 3.x — DI, REST APIs, application configuration |
| **Database ORM** | Spring Data JPA (Hibernate) → PostgreSQL, schema auto-managed via `ddl-auto` |
| **Cart Locking** | DB timestamp (`reserved_until` column) — no extra service required |
| **Unit Tests** | JUnit 5 + Mockito — domain layer and services, no DB |
| **Integration Tests** | JUnit 5 + SpringBootTest — H2 in-memory DB |
| **Acceptance Tests** | JUnit 5 + SpringBootTest — real PostgreSQL instance |
| **Build Tool** | Maven |

---

## Directory Structure

```
ticket-management-system/
├── pom.xml                               # Maven dependencies (or build.gradle)
├── README.md
├── .gitignore
├── docs/                                 # Version 0 models, requirement PDFs, draw.io files
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/ticketing/system/     # Base package
│   │   │       │
│   │   │       ├── Core/                 # The inner rings of your architecture
│   │   │       │   ├── Domain/           # Pure Java business rules (No Spring dependencies here!)
│   │   │       │   │   ├── users/        # Member, Owner, Admin, Guest
│   │   │       │   │   ├── events/       # Event, VenueMap, Zone, Seat
│   │   │       │   │   ├── policies/     # PurchasePolicy, DiscountPolicy
│   │   │       │   │   └── exceptions/   # E.g., SeatAlreadyTakenException
│   │   │       │   │
│   │   │       │   └── Application/      # Orchestrates use cases
│   │   │       │       ├── dto/          # Data Transfer Objects (using Java Records)
│   │   │       │       ├── interfaces/   # IPaymentGateway, ITicketIssuancer, etc.
│   │   │       │       └── services/     # EventManagementService, CheckoutService, etc.
│   │   │       │
│   │   │       ├── Infrastructure/       # Spring-dependent implementations
│   │   │       │   ├── persistence/      # JPA Entities, Spring Data Repositories
│   │   │       │   ├── external/         # PaymentGatewayAdapter, TicketIssuancerAdapter
│   │   │       │   └── security/         # PasswordHasherImpl, JwtSessionManager
│   │   │       │
│   │   │       └── Presentation/         # External entry points
│   │   │           ├── controllers/      # Spring @RestController classes
│   │   │           └── middleware/       # GlobalExceptionHandler, SecurityFilters
│   │   │
│   │   └── resources/
│   │       ├── application.yml           # DB connections, Spring configs
│   │
│   └── test/
│       ├── java/
│       │   └── com/ticketing/system/
│       │       ├── unit/                 # JUnit 5 + Mockito fast tests
│       │       │   ├── domain/
│       │       │   └── application/
│       │       │
│       │       ├── integration/          # SpringBootTest against H2 in-memory DB
│       │       │
│       │       └── acceptance/           # Full-stack JUnit tests against real PostgreSQL
│       │
│       └── resources/
│           ├── application-test.yml      # Integration test config: H2 in-memory DB
│           └── application-acceptance.yml # Acceptance test config: real PostgreSQL
```

---

## Version 0 Work Plan

### 1. UML & Interfaces
Design the classes for:
- `src/main/java/.../Core/Domain/` — pure business rules
- `src/main/java/.../Core/Application/interfaces/` — port definitions

> No database or controllers needed yet.

### 2. Tests
Three layers of testing, all JUnit 5:
- `src/test/java/.../unit/domain/` — domain model and aggregate tests (Mockito, no Spring)
- `src/test/java/.../unit/application/` — service tests (Mockito, no Spring)
- `src/test/java/.../integration/` — SpringBootTest against H2 in-memory DB
- `src/test/java/.../acceptance/` — full-stack SpringBootTest against real PostgreSQL
