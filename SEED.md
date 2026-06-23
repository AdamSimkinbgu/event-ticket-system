# Demo data seeding

Boot-time demo data lives under `src/main/java/com/ticketing/system/Infrastructure/dev/seed/`. The seeder is `@Profile("dev")` only — it never runs in `test` or production.

Run with the dev profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## What gets seeded

| Aggregate | Count | Notes |
|---|---|---|
| Users | 12 | `dev.member` + `dev.admin` (via `DevUserSeeder`) plus 10 named (see below) |
| Companies | 4 | Live Nation Israel · Coca-Cola Arena · Habima Theatre · Shuni Productions |
| Appointments | 5 | 4 founders + 1 co-owner (Moshe at Shuni) + 3 managers with varied permission sets |
| Events | 13 | All `ON_SALE`, future dates 4–165 days out from the boot moment |
| Active reservations | 4 | One in each named buyer's cart on first load |
| Past orders + tickets | 8 | Real reserve + checkout flow (stub payment gateway) |
| Conversations | 3 | 1 buyer→company inquiry, 2 open complaints |
| Announcements | 1 | Admin → all members |
| Notifications | 30 | 10 PENDING (flushed on next login), 20 DELIVERED history |

## Seeded user credentials

All seeded users share the password **`password123`**.

### Personas (created by `DevUserSeeder`)

| Username | Role | Used by |
|---|---|---|
| `dev.member` | regular member | DevPanel "Member" persona toggle |
| `dev.admin` | platform admin | DevPanel "Admin" persona toggle |

### Founders

| Username | Owns | Notes |
|---|---|---|
| `naim.founder` | Live Nation Israel, Shuni Productions | Founds two companies |
| `moshe.founder` | Coca-Cola Arena, Shuni Productions (co-owner) | Accepted co-owner appointment |
| `bentzion.founder` | Habima Theatre | |

### Managers (with assigned permissions)

| Username | Company | Permissions |
|---|---|---|
| `faour.manager` | Live Nation Israel | `VIEW_SALES`, `RESPOND_TO_INQUIRIES` |
| `mohamad.manager` | Coca-Cola Arena | `MANAGE_INVENTORY`, `EDIT_POLICIES` |
| `ben.manager` | Habima Theatre | All five (super manager) |

### Buyers

| Username | Notes |
|---|---|
| `avi.avocado` | 2 past orders, 1 active reservation |
| `dana.dabkeh` | 2 past orders, 1 active reservation |
| `ido.idealist` | 2 past orders, 1 active reservation |
| `maya.manyana` | 2 past orders, 1 active reservation |

## Reset behaviour — `seed.mode`

Three modes, selected via the `seed.mode` Spring property:

| Mode | What it does |
|---|---|
| `idempotent` (default) | Skip if the sentinel user `naim.founder` already exists. Seeds otherwise. |
| `wipe` | Clear every `Memory*Repository`, re-seed dev personas, then seed the demo graph. |
| `off` | Do nothing. |

Trigger a wipe-and-reseed from the command line:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev \
    -Dspring-boot.run.arguments=--seed.mode=wipe
```

## Date relativity

`DemoClock` captures `clock.instant()` once at run start. Every event/reservation/notification timestamp is computed as an offset (`anchor.plusDays(N)`, `anchor.minusHours(N)`, …). Boot on a different day → all dates shift accordingly. No hard-coded calendar dates.

## Known limitations

- `InMemoryNotificationService.send` currently throws `UnsupportedOperationException`. Every reservation flow routes through it, so reservations effectively fail mid-pipeline today. The seeder's smoke tests are `@Disabled` pending this fix.
- The domain has no `DRAFT → SCHEDULED` transition for events, so the seeder reflection-sets `Event.status` directly to `ON_SALE`. Once that transition lands, swap `forceStatusOnSale` for the proper call.
- Past order timestamps reflect "today" — `OrderReceipt.purchaseTime` is set to `LocalDateTime.now()` inside the constructor and isn't backdated. Notification history timestamps are real past values (constructor accepts `createdAt`).
- Queue and Lottery subsystems are not seeded (Tier-C exempt per the team scope decision).
- `MyAccountView`'s orders/tickets tables remain hardcoded — wiring those to seeded data belongs to a separate `[V2-WIRE-ACCT]` ticket.
