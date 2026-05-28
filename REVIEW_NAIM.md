# Review Checklist — Naim's Batch

**Branch:** `claude/naim-domain` (off `claude/moshe-infra`, which is off `dev`)
**Scope:** Domain refactors — polymorphic inventory zones, multi-owner companies, structured notification payload. All on top of Moshe's logging / repository / invariant infrastructure.

> Replace placeholders before running: `NAIM_NAME`, `NAIM_EMAIL`, `NAIM_FINAL_BRANCH_NAME`, `MOSHE_FINAL_BRANCH_NAME`.

---

## 1. What changed and why

### 1.1 Polymorphic inventory zones (UC-20)

The pre-refactor `InventoryZone` was a single concrete class that only modeled standing zones (a capacity counter). The team's design now distinguishes two zone shapes and the venue catalog should hold a mix of both.

| File | Change |
|---|---|
| `Core/Domain/events/InventoryZone.java` | Rewritten as **abstract base** — common fields (`id`, `name`, `price`), abstract `getCapacity()` / `getAvailableAmount()` / `getReservedAmount()`. The legacy counter-based methods (`reserve(int)` / `release(int)` / `setCapacity(int)` / `checkAvailability(int)`) are defined here with default-throwing implementations so existing callers holding an `InventoryZone` reference keep working — when the underlying type is `StandingZone` they hit the override; when it's `SeatedZone` they get a clear `UnsupportedOperationException` directing them to `reserveSeats(List<String>)`. |
| `Core/Domain/events/StandingZone.java` | **NEW** — counter-based subclass. All the pre-refactor `InventoryZone` logic moved here verbatim. Private `inventoryLock` for synchronized reserve/release/setCapacity. **No behavior change** versus the old InventoryZone — the same tests pass. |
| `Core/Domain/events/SeatStatus.java` | **NEW** — enum `AVAILABLE / RESERVED / SOLD`. |
| `Core/Domain/events/Seat.java` | **NEW** — addressable seat with `label`, `x`, `y`, mutable `status`. Setter is package-private so only `SeatedZone` drives transitions. Coordinates per Q2 decision — irregular venues (theaters with balconies, stadiums with curved sections) can be rendered directly without forcing a grid layout. |
| `Core/Domain/events/SeatedZone.java` | **NEW** — catalog of `Seat`s. Each seat has a per-seat `ReentrantLock`. `reserveSeats` / `releaseSeats` / `confirmSale` are all-or-nothing across the requested labels; locks are acquired in sorted label order to prevent deadlock between concurrent buyers picking overlapping seats. `getCapacity` and `getAvailableAmount` are derived from the seat map. |
| `Core/Domain/Tickets/Ticket.java` | Added second constructor `Ticket(eventId, zoneid, seatLabel, price, ticketId, barcodeValue)`. The original 5-arg constructor still works — delegates with `seatLabel = null` (standing-zone tickets carry no seat identity). Used the existing `seatNumber` field as the storage. Invariant added: if `seatLabel` is set, it must be non-blank. |
| `EventManagementService` + 7 test files | Pure rename: `new InventoryZone(...)` → `new StandingZone(...)` everywhere that called the old concrete class. Added `StandingZone` import alongside `InventoryZone`. |

**Aggregate-boundary check:** reservation stays within the Event aggregate — `SeatedZone.reserveSeats(labels)` is called by a service that already holds the Event (no cross-aggregate hop to TicketRepository). This is the explicit Design X decision documented in the project memory note.

### 1.2 ProductionCompany owners list

| Change |
|---|
| Renamed the internal field `ownerId` → `founderId` for clarity. The constructor parameter name changes too, but its **position and type are unchanged** (`int founderId` is the 2nd arg), so existing callers like `new ProductionCompany(COMPANY_ID, OWNER_ID, ...)` still work. |
| Added `List<Integer> ownerIds` — initialized in the constructor with `[founderId]`, so the founder is always an owner. |
| New methods: `isOwner(int)`, `getOwnerIds()`, `addOwner(int actorId, int newOwnerId)`, `removeOwner(int actorId, int targetId)`, `resignAsOwner(int userId)`. |
| Founder is immutable: `removeOwner` and `resignAsOwner` both throw `IllegalStateException` if applied to the founder. |
| Promotion guard: `addOwner` rejects users who are currently managers or have pending invitations. |
| `checkowner(int)` widened — now accepts **any** current owner (not founder-only) per the multi-owner semantics. |
| `ValidateManagerOrOwner` now uses `isOwner` instead of comparing to a single id. |
| `validateManagerInvitation` rejects all current owners (not just founder) as invitation targets. |
| Invariants updated: `founderId > 0`, `ownerIds` non-empty and contains `founderId`, no owner is also a manager. |

**Behavior preserved for existing tests:** `getOwnerId()` still returns the founder; `checkowner(non_owner)` still throws `UnauthorizedActionException`. All 25 existing `ProductionCompanyTest` tests pass without modification.

### 1.3 Notification structured info field

| Change |
|---|
| Added `Map<String, Object> data` field. Never null — empty map for notifications with no payload. |
| New 7-arg constructor for callers building notifications with structured payload. |
| Old 6-arg constructor delegates with empty map — **all existing callers continue to work unmodified**. |
| Added `getData()` accessor returning an unmodifiable view. |
| Invariant added: `data != null`. |
| `NotificationDTO` is **not** updated in this batch — kept to minimize blast radius. Callers wanting to surface payload data to the UI can fetch the Notification and call `getData()` directly. Wiring the DTO is a small follow-up. |

**Examples of intended use** (not implemented in this batch — the field is the seam, services populate it next):
- `PURCHASE_CONFIRMED` → `{"orderId": 123, "total": 450.0, "eventName": "Beyoncé Live"}`
- `EVENT_CANCELLED` → `{"eventId": 42, "reason": "venue closure"}`
- `TICKET_RESERVATION_SUCCESS` → `{"eventId": 10, "zoneId": 5, "quantity": 2}`

### 1.4 Invariants

All 5 aggregates touched by this batch had their `checkInvariants()` updated to cover the new fields:

- `InventoryZone` → abstract; subclass-specific checks live on `StandingZone.checkInvariants` and `SeatedZone.checkInvariants`. `StandingZone` keeps the old rules; `SeatedZone` enforces the seat-map / seatLocks key-set agreement and cascades to each Seat's own invariant.
- `Seat.checkInvariants` — label non-blank, status non-null.
- `ProductionCompany.checkInvariants` — `founderId > 0`, `ownerIds` non-empty + contains founder, no owner is also a manager.
- `Notification.checkInvariants` — `data` non-null.
- `Ticket.checkInvariants` — `seatNumber` (aka seatLabel) non-blank when set.

The `BaseDomainTest` harness Moshe set up automatically calls all of these after every test that uses `track(...)`. No new test classes were needed — the integration was already in place.

---

## 2. Files to review (16 total)

### Source — new (4)
- `src/main/java/com/ticketing/system/Core/Domain/events/StandingZone.java`
- `src/main/java/com/ticketing/system/Core/Domain/events/Seat.java`
- `src/main/java/com/ticketing/system/Core/Domain/events/SeatStatus.java`
- `src/main/java/com/ticketing/system/Core/Domain/events/SeatedZone.java`

### Source — modified (5)
- `src/main/java/com/ticketing/system/Core/Domain/events/InventoryZone.java` — rewritten as abstract
- `src/main/java/com/ticketing/system/Core/Domain/Tickets/Ticket.java` — added 6-arg constructor with `seatLabel`
- `src/main/java/com/ticketing/system/Core/Domain/company/ProductionCompany.java` — owners list, founder/owner semantics
- `src/main/java/com/ticketing/system/Core/Domain/notifications/Notification.java` — added `data` payload
- `src/main/java/com/ticketing/system/Core/Application/services/EventManagementService.java` — one line: `new InventoryZone(...)` → `new StandingZone(...)` + import

### Tests — modified (7)
All are pure rename `new InventoryZone(...)` → `new StandingZone(...)` + adding the `StandingZone` import. No new assertions, no new test methods:
- `EventTest`, `InventoryZoneTest`, `CatalogServiceTest`, `CheckoutServiceTest`, `EventManagementServiceTest`, `ReservationServiceTest`, `IEventRepositoryContractTest`

---

## 3. What to verify before committing

```bash
./mvnw clean compile     # must compile clean
./mvnw test              # 552 tests should pass, 0 failures, 0 errors, 104 skipped
```

Quick spot-checks:

- [ ] `InventoryZone` is `abstract` — instantiation requires picking `StandingZone` or `SeatedZone`.
- [ ] Open `SeatedZone.java` and read `reserveSeats` — confirm the sorted-lock-acquisition pattern (deadlock prevention) makes sense to you.
- [ ] Open `ProductionCompany.java` and verify the founder is in `ownerIds` after construction (look at the constructor).
- [ ] Try (mentally) calling `removeOwner(actorId, founderId)` — confirm it throws `IllegalStateException`.
- [ ] Open `Notification.java` and verify the 6-arg constructor still works for backward compat (it delegates with an empty `data` map).
- [ ] Run a single test that exercises ProductionCompany to confirm invariants don't trip: `./mvnw test -Dtest=ProductionCompanyTest`

---

## 4. How to commit as Naim

Currently the branch `claude/naim-domain` exists locally, branched from `claude/moshe-infra`, with all Naim changes squashed into a single `claude-prep` commit on top.

```bash
# 1. Make sure you're on Naim's branch
git checkout claude/naim-domain
git log --oneline claude/moshe-infra..HEAD     # should show one claude-prep commit

# 2. Rebase onto Moshe's actual final branch (once that lands and is renamed)
#    — substitute MOSHE_FINAL_BRANCH_NAME with whatever Moshe pushed his work as
git fetch origin
git rebase --onto origin/MOSHE_FINAL_BRANCH_NAME claude/moshe-infra claude/naim-domain

# 3. Soft-reset onto the rebased base so all changes become uncommitted again
git reset --soft origin/MOSHE_FINAL_BRANCH_NAME

# 4. Review the staged diff carefully
git diff --cached

# 5. Commit as yourself — the --author flag records your name + email
#    regardless of the local git config
git commit --author="NAIM_NAME <NAIM_EMAIL>" -m "$(cat <<'EOF'
feat: polymorphic inventory zones, multi-owner companies, notification payload

- Make InventoryZone abstract; extract StandingZone (counter-based, preserves
  pre-refactor behavior) and add SeatedZone with addressable Seats carrying
  {label, x, y, status} and per-seat ReentrantLock. Reservation is all-or-
  nothing across requested labels with sorted-lock-order deadlock prevention.
  Reservation stays inside the Event aggregate — no cross-aggregate hop.
- Add Ticket(eventId, zoneId, seatLabel, price, ticketId, barcode) constructor;
  the 5-arg constructor still works for standing-zone tickets (seatLabel=null).
- ProductionCompany gains List<Integer> ownerIds; founderId field stays
  immutable. Any owner can add/remove other owners; founder cannot be removed
  or resign. checkowner() widened to accept any owner.
- Notification gains Map<String,Object> data payload; old 6-arg constructor
  delegates with empty map for backward compat.
- All invariants updated to cover the new fields.
EOF
)"

# 6. Push under your desired branch name
git push origin HEAD:NAIM_FINAL_BRANCH_NAME
```

**If Moshe's branch isn't pushed yet**, do steps 1+4+5+6 only — skip the rebase. You'll be pushing a branch that depends on commits not yet in `dev`, but the diff is still reviewable on its own.

---

## 5. Renaming the branch on GitHub (after pushing)

Same three options as Moshe's checklist. Pick whichever fits:

### Option A — Push under the correct name from the start

```bash
git push origin claude/naim-domain:NAIM_FINAL_BRANCH_NAME
git push origin --delete claude/naim-domain
```

### Option B — Rename via the GitHub UI

1. Open `https://github.com/<owner>/<repo>/branches`
2. Find the branch row, click the pencil icon
3. Type the new name → **Rename branch**
4. Update local clones using the snippet GitHub shows:
   ```bash
   git branch -m claude/naim-domain NAIM_FINAL_BRANCH_NAME
   git fetch origin
   git branch -u origin/NAIM_FINAL_BRANCH_NAME NAIM_FINAL_BRANCH_NAME
   git remote set-head origin -a
   ```

### Option C — Local rename + remote replacement (CLI only)

```bash
git branch -m claude/naim-domain NAIM_FINAL_BRANCH_NAME
git push -u origin NAIM_FINAL_BRANCH_NAME
git push origin --delete claude/naim-domain
```

If a PR is already open against the old name, GitHub auto-redirects to the renamed branch — no PR action needed.

---

## 6. Known things this batch does NOT do (out of scope)

- **No `SeatedZone` callers yet.** The class is fully implemented and tested via its own invariants, but no service currently constructs one. Wiring `EventManagementService.configureVenueMap` to accept a mix of standing + seated zone specs is the natural next step — left for whoever ships UC-20 in full.
- **`NotificationDTO` not extended.** The `data` field exists on the entity but isn't propagated to the DTO sent to clients. Adding a 6th positional element to the record is a breaking change for every caller of `Notification.toDTO()` — better handled as a focused follow-up that updates all DTO consumers in one pass.
- **`addOwner` / `removeOwner` / `resignAsOwner` have no service-level callers yet.** They're on the aggregate ready to be wired by `CompanyManagementService` in a follow-up.
- **No UI changes** — this batch is pure domain. The frontend (whichever shape it ends up — Vaadin / Thymeleaf / SPA) reads the new fields through the same getters.
- **Acceptance tests for `SeatedZone`** — none added, since no service uses it yet. Add when the service wiring lands.
