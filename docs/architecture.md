# Architecture & Tech Stack Documentation

## Dependencies

### Spring Boot Starters

| Dependency | Why |
|---|---|
| `spring-boot-starter-web` | REST API controllers — exposes HTTP endpoints via `@RestController` |
| `spring-boot-starter-data-jpa` | ORM layer — maps Java classes to database tables via Hibernate |
| `spring-boot-starter-data-redis` | Cart locking (10-minute reservation timer) and virtual queue management |
| `spring-boot-starter-validation` | Input validation on DTOs at system boundaries |

### Database

| Dependency | Why |
|---|---|
| `postgresql` | Main production database driver |
| `flyway-core` | Manages DB schema via versioned SQL migration files |
| `flyway-database-postgresql` | Flyway's PostgreSQL-specific dialect support |
| `h2` | In-memory database for tests — no real Postgres required in CI |

### Utilities

| Dependency | Why |
|---|---|
| `lombok` | Eliminates boilerplate (getters, constructors, builders) via annotations |

### Testing

| Dependency | Why |
|---|---|
| `spring-boot-starter-test` | Bundles JUnit 5 + Mockito for unit and integration tests |
| `h2` | In-memory database for integration tests — fast, no external DB required |
| `postgresql` | Also used in acceptance tests against a real PostgreSQL instance |

---

## Directory Structure

```
event-ticket-system/
├── pom.xml                               # Maven dependencies and build config
├── README.md
├── .gitignore
├── docs/                                 # Architecture docs, requirements, planning
│
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/ticketing/system/     # Base package
    │   │       │
    │   │       ├── Core/                 # Inner rings — no Spring dependencies
    │   │       │   ├── Domain/           # Pure business rules
    │   │       │   │   ├── users/        # Member, Owner, Admin, Guest domain models
    │   │       │   │   ├── events/       # Event, VenueMap, Zone, Seat
    │   │       │   │   ├── policies/     # PurchasePolicy, DiscountPolicy
    │   │       │   │   └── exceptions/   # Domain exceptions (e.g. SeatAlreadyTakenException)
    │   │       │   │
    │   │       │   └── Application/      # Use case orchestration
    │   │       │       ├── dto/          # Data Transfer Objects (Java Records)
    │   │       │       ├── interfaces/   # IPaymentGateway, ITicketIssuancer, etc.
    │   │       │       └── services/     # EventManagementService, CheckoutService, etc.
    │   │       │
    │   │       ├── Infrastructure/       # Spring-dependent implementations
    │   │       │   ├── persistence/      # JPA Entities + Spring Data Repositories
    │   │       │   ├── external/         # PaymentGatewayAdapter, TicketIssuancerAdapter
    │   │       │   └── security/         # PasswordHasherImpl, JwtSessionManager
    │   │       │
    │   │       └── Presentation/         # External entry points
    │   │           ├── controllers/      # Spring @RestController classes
    │   │           └── middleware/       # GlobalExceptionHandler, SecurityFilters
    │   │
    │   └── resources/
    │       ├── application.yml           # Production: DB, Redis, Spring config
    │       └── db/migration/             # Flyway SQL schema migration files
    │           └── V1__init_schema.sql   # Initial schema
    │
    └── test/
        ├── java/
        │   └── com/ticketing/system/
        │       ├── unit/                 # JUnit 5 + Mockito — fast, isolated tests
        │       │   ├── domain/
        │       │   └── application/
        │       │
        │       ├── integration/          # SpringBootTest — loads context, hits H2 in-memory DB
        │       │
        │       └── acceptance/           # Full-stack JUnit tests against real PostgreSQL
        │
        └── resources/
            ├── application-test.yml      # Integration test overrides: H2 in-memory DB
            └── application-acceptance.yml # Acceptance test overrides: real PostgreSQL
```

---

## Architecture Layers

The project follows **Clean Architecture** — dependencies only point inward.

```
Presentation  →  Application  →  Domain
Infrastructure  →  Application  →  Domain
```

### Core / Domain
Pure Java business rules. No Spring, no JPA, no external dependencies. This is where domain models, policies, and domain exceptions live. This layer can be tested without starting Spring.

### Core / Application
Orchestrates use cases. Defines service interfaces (`IPaymentGateway`, `ITicketIssuancer`) that the Infrastructure layer implements. Uses Java Records for DTOs to keep data transfer objects immutable and concise.

### Infrastructure
Spring-dependent implementations. JPA entities and Spring Data repositories live here, as do adapters for external services (payment gateways, ticket issuance). This layer implements the interfaces defined in Application.

### Presentation
The HTTP boundary. REST controllers accept requests and delegate to Application services. Middleware handles cross-cutting concerns like exception formatting and security filtering.

---

## Configuration Files

### `application.yml` (production)
- PostgreSQL datasource connection (host, port, DB name via environment variables)
- JPA/Hibernate dialect and DDL settings
- Redis connection for cart locking
- Flyway migration settings

### `application-test.yml` (integration tests)
- Overrides datasource to H2 in-memory DB
- Disables Redis (or uses an embedded stub)
- Flyway runs migrations against H2

### `application-acceptance.yml` (acceptance tests)
- Points to a real PostgreSQL instance
- Used by full-stack acceptance tests that must verify real DB behavior
- Requires a running PostgreSQL instance (local or Docker)

### `db/migration/V1__init_schema.sql`
The first Flyway migration file. Establishes the initial database schema. Subsequent schema changes are added as new versioned files (`V2__...`, `V3__...`) and never modify existing ones.
