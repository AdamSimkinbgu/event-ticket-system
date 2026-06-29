# Use cases — V3 updates

This document records the V3 (Persistence & Robustness) changes to the use cases most affected by
the move to a persisted database and the cart-lifecycle work: **UC-1 Initialize Platform**,
**UC-32 Open Trading Market**, and **UC-13 Restore Active Order**.

> The canonical, graded use-case book is maintained externally (LaTeX/Overleaf). This Markdown is
> the in-repo record of the V3 deltas so the code and the docs stay in sync; mirror any change here
> into the LaTeX book before submitting.

---

## UC-1 — Initialize Platform

**Primary actor:** System (boot) / System Admin
**Goal:** Bring the platform to a healthy, market-openable state on startup, validated against the
**persisted** database.

**Preconditions**
- The configured datasource is reachable (`jpa` profile → PostgreSQL on deploy, H2 in dev).
- Init parameters are available from the config file (`platform.admin.*`, `wsep.base-url`, …).

**Main flow**
1. On boot, `PlatformInitializationRunner` (`@Order(0)`) invokes
   `SystemAdminService.initializePlatform()`.
2. **Persistence load (V3).** Under the `jpa` profile the repositories are JPA-backed, so the
   platform initializes against state already persisted in the database (admins, companies, events,
   orders, …) rather than an empty in-memory store.
3. **I.1.2 / I.1.3 — external-service quorum.** At least one payment gateway and one ticket issuer
   must answer a WSEP `handshake` (`requireExternalServicesReachable()`).
4. **I.1.4 — default admin.** `createDefaultAdminIfMissing()` creates the default System Admin from
   `platform.admin.username` / `platform.admin.password` if none exists, and confirms the write
   persisted.
5. **I.1.1 — integrity re-validation (V3).** `verifyInitializationInvariants()` re-asserts the
   post-conditions and `SystemIntegrityVerifier.verify()` runs the structural correctness scans
   against the **loaded DB state** (e.g. every active company has an owner; every producer resolves
   to a registered user). See #372 for extending these scans across the full constraint set at boot.
6. The platform transitions **UNINITIALIZED → READY**; the market remains **CLOSED** until UC-32.

**Alternate / exception flows**
- No reachable payment **or** issuance service → `ExternalServiceUnavailableException`; platform
  stays UNINITIALIZED.
- Default admin cannot be created/persisted → `MissingDefaultAdminException`.
- A post-condition or invariant fails at the gate (incl. a violation in loaded DB state) →
  `InitializationIntegrityException`; the platform does not reach READY and the market cannot open.
- Re-invocation on an already-initialized platform is a graceful no-op (idempotent).

**Postconditions**
- On success: status = READY, a System Admin exists, external services verified, invariants hold.
- `isMarketOpen()` is still `false` — opening the market is the separate UC-32.

---

## UC-32 — Open Trading Market

**Primary actor:** System Admin
**Goal:** Open the market so reservations and purchases are accepted.

**Preconditions**
- The platform is initialized (status = READY) or was previously OPEN then CLOSED.
- The caller presents a valid admin token.

**Main flow**
1. Admin calls `SystemAdminService.openMarket(request)`; `requireSystemAdmin()` enforces the admin
   role.
2. **I.2.2** — re-verify the external-service quorum (`requireExternalServicesReachable()`).
3. **I.2.1** — re-assert structural invariants: at least one admin present and
   `SystemIntegrityVerifier.verify()` passes against current DB state.
4. Status transitions **READY → OPEN** (or **CLOSED → OPEN**); `lastOpenedAt` is recorded.
5. Sales paths (`ReservationService`, `CheckoutService`) now pass the `isMarketOpen()` gate.

**Alternate / exception flows**
- Caller is not a valid admin → `UnauthorizedActionException`.
- Platform not initialized → `MarketNotOpenException("platform not initialized")`.
- External service down at open time → `ExternalServiceUnavailableException`.
- No admin present / invariant violated → `InitializationIntegrityException`.
- Already OPEN → idempotent no-op returning the current state.

**Postconditions**
- On success: status = OPEN; transactions are accepted.
- `closeMarket()` returns the market to CLOSED (idempotent; rejects if it was never opened).

---

## UC-13 — Restore Active Order

**Primary actor:** Member
**Goal:** Restore a member's reserved Active Order (cart) on re-authentication, before the
reservation timer expires (II.3.0.2).

**Preconditions**
- The member had an Active Order with at least one reserved line.
- Re-authentication happens within the reservation window (`constants.ticket-reservation-duration`).

**Main flow**
1. The cart persists by `userId` across logout — the Session row is deleted but the Active Order
   survives (II.3.0.1 / D9a). In the domain, logout orphans the cart from its session
   (`ActiveOrder.clearSession()`) while keeping `userId`.
2. On login, `AuthenticationService.handleCartOnPromotion(session, user)` runs:
   - If a **guest cart** exists for the just-used guest session, it is promoted to the member
     (`attachToUser`).
   - Otherwise, if a **persisted member cart** exists from a previous session, it is re-attached to
     the new session (`attachToSession`).
3. `ReservationService.restoreActiveOrder(userId)` rehydrates the cart, enriches lines with event
   names, and returns the remaining seconds so the UI can resume the countdown. The result is
   carried back in the `LoginDTO`.

**Alternate / exception flows**
- **Expired cart.** If the reservation window elapsed while the member was logged out, the
  scheduled `SessionAndOrderSweeper` has already released the tickets to inventory and deleted the
  cart (II.3.0.3); restore finds nothing and the member sees an empty cart (no stale hold).
- **Both carts present** (a fresh guest cart and a prior member cart): the guest cart wins, because
  the repository collapses identity by `userId` on save.

**Postconditions**
- On success: the member's reserved items and remaining hold time are restored to the session.
- Inventory is never double-held: a restored cart re-attaches the *same* reservation; an expired one
  was already returned to stock.

---

## Related V3 work

- #366–#368 — seeding/reset safety, config-file-driven init params + boot validity check, and the
  initial-state replay file (documented in the README).
- #369–#371 — cart persist-on-exit, restore-on-re-auth (this UC-13), and offline expiration sweep.
- #372 — re-validate invariants on startup against loaded DB state (extends UC-1 step 5).
