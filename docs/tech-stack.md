# Tech Stack & Project Structure

## Java Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Java 21 (LTS) — records for DTOs, sealed classes for domain constraints |
| **Framework** | Spring Boot 3.x — DI, REST APIs, application configuration |
| **Database ORM** | Spring Data JPA (Hibernate) → PostgreSQL |
| **Caching/Locking** | Spring Data Redis — 10-minute cart lock and queueing |
| **Unit/Integration Tests** | JUnit 5 + Mockito |
| **Acceptance Tests** | Cucumber for Java (Gherkin format) |
| **Build Tool** | Maven (or Gradle) |

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
│   │       └── db/migration/             # Flyway/Liquibase SQL schema files (optional)
│   │
│   └── test/
│       ├── java/
│       │   └── com/ticketing/system/
│       │       ├── unit/                 # JUnit 5 + Mockito fast tests
│       │       │   ├── domain/
│       │       │   └── application/
│       │       │
│       │       ├── integration/          # SpringBootTest (loads context, checks DB)
│       │       │
│       │       └── acceptance/           # Version 0 Acceptance Test Implementations
│       │           ├── RunCucumberTest.java     # Cucumber runner class
│       │           └── stepdefinitions/         # Java code that executes the Gherkin steps
│       │
│       └── resources/
│           ├── application-test.yml      # Test configurations (e.g., in-memory DB)
│           └── features/                 # Customer Acceptance Tests (Version 0 focus)
│               ├── reserve_ticket.feature
│               ├── define_discount_policy.feature
│               └── register_company.feature
```

---

## Version 0 Work Plan

### 1. UML & Interfaces
Design the classes for:
- `src/main/java/.../Core/Domain/` — pure business rules
- `src/main/java/.../Core/Application/interfaces/` — port definitions

> No database or controllers needed yet.

### 2. Acceptance Tests (Gherkin)
Write plain-English scenarios in `src/test/resources/features/`.

**Example — `reserve_ticket.feature`:**
```gherkin
Feature: Reserving a Ticket

  Scenario: Successfully reserving an available seat
    Given a logged-in Member "Alice"
    And an Event "Rock Concert" with available seats in "Zone A"
    When Alice adds a "Zone A" ticket to her cart
    Then the ticket should be locked for 10 minutes
```

### 3. Step Definitions
Map Gherkin steps to empty Java methods in `src/test/java/.../acceptance/stepdefinitions/` — proving the tests can be wired to real code in Version 1.
