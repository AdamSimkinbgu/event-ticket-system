# V2 frontend ↔ backend wire-up plan

The master narrative that ties every `V2-WIRE-*` ticket together. **This document does not duplicate ticket bodies** — it tells the story of how the pieces compose, what order to do them in, and where the genuine gaps and hard blockers are.

If you're triaging which V2 ticket to pick up next, start here.

## The vision

V2 ships a Vaadin frontend whose every view talks to the real application services through MVP presenters that translate domain exceptions into typed UI outcomes. When V2 is "done":

- No view holds session state directly (no `Mock*` helpers; no inline fixture lists).
- No view directly calls a service — everything goes through a presenter.
- Every presenter returns a sealed `Outcome` so views render via `switch`, never `try/catch`.
- Capability resolution, current-company selection, cart contents, and notifications all read from real services.
- Real-time updates (cart timers, sold-out broadcasts, notification bell) ride a Vaadin `@Push` channel.

## The pattern (already in flight)

> **Convention reference:** [docs/coding-standards.md](docs/coding-standards.md) — presenter outcome shapes, `ErrorPayload` factory methods, exception → `ErrorCode` mapping table, and `Toasts` usage rules.

`#299` (V2-AUTH-01) shipped `LoginPresenter` / `RegisterPresenter` as Vaadin-free POJOs with sealed `Outcome` hierarchies. Every other write-side view inherits this shape:

```
View ──→ Presenter ──→ Service (throws typed domain exceptions)
   ↑           │
   └── switch on Outcome.{Success, TypedFailure...}
```

Read-side views differ only in payload — `Outcome.Success(DataDTO)` carries what the view renders; `Failure` variants cover `NotFound`, `Forbidden`, `ServiceUnavailable`. The common shape is codified by [`#255` V2-ERROR-01](https://github.com/AdamSimkinbgu/event-ticket-system/issues/255) — once it lands, every presenter uses the same `FailureReason` enum + `ErrorPayload` record.

## Phases

### Phase 0 — Convention + hard blockers (must precede everything else)

| What | Issue | Notes |
|---|---|---|
| Structured failure payload | [#255 V2-ERROR-01](https://github.com/AdamSimkinbgu/event-ticket-system/issues/255) | Defines `ErrorPayload` + `Toasts.failure(ErrorPayload)`. Every later wire-up references it. |
| Reservation presenter (reference impl for read-write views) | [#233 V2-RES-04](https://github.com/AdamSimkinbgu/event-ticket-system/issues/233) | Sets the template for the rest of the buyer-side presenters. |
| Vaadin `@Push` seam | [#212 V2-F-07](https://github.com/AdamSimkinbgu/event-ticket-system/issues/212) | Prerequisite for the bell, cart countdown, sold-out broadcasts. |
| Spring Security wiring | [#209 V2-F-03](https://github.com/AdamSimkinbgu/event-ticket-system/issues/209) | Threads token from `AuthSession` into the service-call boundary. |
| **NEW**: fix `InMemoryNotificationService.send` throwing `UnsupportedOperationException` | (unfiled) | Found during V2-DEMO-SEED. Every reservation flow throws today. Two-line stub fix. |
| **NEW**: domain `Event.transitionToScheduled()` (DRAFT → SCHEDULED) | (unfiled) | `transitionToOnSale()` requires SCHEDULED but nothing produces that state. |

### Phase 1 — Mock state rip (foundation for every owner-side wire-up)

Replaces session-scoped fakes with real-service-backed views. Listed in dependency order:

| Mock | Replacement | Issue |
|---|---|---|
| `MockCompanies` | `CompanyMembershipService` + `CurrentCompanies` session helper | [#256 V2-OWNER-WIRE-01](https://github.com/AdamSimkinbgu/event-ticket-system/issues/256) |
| `MockSession.currentCompany` | folded into `CurrentCompanies` | (#256 by extension) |
| `MockPermissions` | reads `CompanyAppointment.permissions` from the real aggregate | (#256 by extension) |
| `MockCart` | `ReservationService.viewMyActiveOrder` + cart-side helpers | Likely a new ticket; see "Gaps" below |
| `Capabilities.forCurrentUser` rewire | depends on all the above | (#256 by extension) |

### Phase 2 — Buyer-facing views

| View | Wire-up ticket | Notes |
|---|---|---|
| LandingView featured events | [#285 V2-LANDING-01](https://github.com/AdamSimkinbgu/event-ticket-system/issues/285) | |
| BrowseEventsView | [#271 V2-WIRE-BUYER-BROWSE](https://github.com/AdamSimkinbgu/event-ticket-system/issues/271) | Plus [#286 V2-URL-FILTERS](https://github.com/AdamSimkinbgu/event-ticket-system/issues/286) and [#281 V2-SEARCH-01](https://github.com/AdamSimkinbgu/event-ticket-system/issues/281) |
| EventDetailsView | [#272 V2-WIRE-BUYER-EVENT](https://github.com/AdamSimkinbgu/event-ticket-system/issues/272) | Blocked on `EventManagementService.getEventDetail` being implemented (currently `UnsupportedOperationException`) |
| SeatPickerView | [#273 V2-WIRE-BUYER-SEATS](https://github.com/AdamSimkinbgu/event-ticket-system/issues/273) | Plus components [#230 V2-RES-01](https://github.com/AdamSimkinbgu/event-ticket-system/issues/230) / [#231 V2-RES-02](https://github.com/AdamSimkinbgu/event-ticket-system/issues/231) |
| CartView | [#232 V2-RES-03](https://github.com/AdamSimkinbgu/event-ticket-system/issues/232) + ReservationPresenter (#233) | Server-side eviction is [#257 V2-CART-EVICT](https://github.com/AdamSimkinbgu/event-ticket-system/issues/257) |
| CheckoutView | [#221 V2-CHECK-01](https://github.com/AdamSimkinbgu/event-ticket-system/issues/221), [#282 V2-WIRE-CHECKOUT-PAY](https://github.com/AdamSimkinbgu/event-ticket-system/issues/282), [#283 V2-WIRE-CHECKOUT-COUPON](https://github.com/AdamSimkinbgu/event-ticket-system/issues/283), purchase policy eval [#220 V2-POL-05](https://github.com/AdamSimkinbgu/event-ticket-system/issues/220) | Plural tickets — biggest single view |

### Phase 3 — Member account

| View | Wire-up ticket | Notes |
|---|---|---|
| MyAccountView | [#274 V2-WIRE-MEMBER-ACCOUNT](https://github.com/AdamSimkinbgu/event-ticket-system/issues/274) | Replaces hardcoded order/ticket tables |
| ReceiptView | [#276 V2-WIRE-MEMBER-RECEIPT](https://github.com/AdamSimkinbgu/event-ticket-system/issues/276) | |
| MyInvitationsView | [#275 V2-WIRE-MEMBER-INVITES](https://github.com/AdamSimkinbgu/event-ticket-system/issues/275) | Accept / decline appointments |
| SupportInboxView | [#277 V2-WIRE-MEMBER-SUPPORT](https://github.com/AdamSimkinbgu/event-ticket-system/issues/277) | |
| MyCompaniesView | [#291 V2-WIRE-MEMBER-COMPANIES](https://github.com/AdamSimkinbgu/event-ticket-system/issues/291) | Depends on #256 |
| MyProfileView | (currently exempt — Tier-C per team scope decision) | |
| Refund flow on receipt / account | [#284 V2-WIRE-MEMBER-REFUND](https://github.com/AdamSimkinbgu/event-ticket-system/issues/284) | |

### Phase 4 — Owner workspace

All depend on Phase 1 (#256) landing.

| View | Wire-up ticket |
|---|---|
| OwnerDashboardView | [#292 V2-WIRE-OWNER-DASH](https://github.com/AdamSimkinbgu/event-ticket-system/issues/292) |
| CompanyEventListView | [#260 V2-WIRE-COMPANY-EVENTS](https://github.com/AdamSimkinbgu/event-ticket-system/issues/260) |
| EventManagementView | [#261 V2-WIRE-COMPANY-EVENT-MGMT](https://github.com/AdamSimkinbgu/event-ticket-system/issues/261) |
| VenueMapEditorView | [#262 V2-WIRE-COMPANY-VENUE](https://github.com/AdamSimkinbgu/event-ticket-system/issues/262) + [#229 V2-PEDIT-01](https://github.com/AdamSimkinbgu/event-ticket-system/issues/229) (visual editor) |
| CompanyRegistrationView | [#263 V2-WIRE-COMPANY-REGISTER](https://github.com/AdamSimkinbgu/event-ticket-system/issues/263) |
| ManagerListView | [#264 V2-WIRE-COMPANY-MANAGERS](https://github.com/AdamSimkinbgu/event-ticket-system/issues/264) + [#253 V2-CADMIN-03](https://github.com/AdamSimkinbgu/event-ticket-system/issues/253) (edit/revoke dialogs) |
| ManagerInvitationView | [#265 V2-WIRE-COMPANY-INVITE](https://github.com/AdamSimkinbgu/event-ticket-system/issues/265) |
| OwnerAppointmentView | [#266 V2-WIRE-COMPANY-OWNER](https://github.com/AdamSimkinbgu/event-ticket-system/issues/266) |
| CompanySalesView | [#278 V2-WIRE-OWNER-SALES](https://github.com/AdamSimkinbgu/event-ticket-system/issues/278) |
| CompanyInquiryInboxView | [#268 V2-WIRE-MSG-INBOX](https://github.com/AdamSimkinbgu/event-ticket-system/issues/268) |
| PurchasePolicyEditorView | [#229 V2-PEDIT-01](https://github.com/AdamSimkinbgu/event-ticket-system/issues/229) + [#254 V2-PEDIT-02](https://github.com/AdamSimkinbgu/event-ticket-system/issues/254) (buyer simulator) |

### Phase 5 — Admin

| View | Wire-up ticket |
|---|---|
| AdminLoginView | [#290 V2-WIRE-ADMIN-LOGIN](https://github.com/AdamSimkinbgu/event-ticket-system/issues/290) |
| AdminDashboardView | [#279 V2-WIRE-ADMIN-DASH](https://github.com/AdamSimkinbgu/event-ticket-system/issues/279) (blocked on `SystemAnalyticsService` — currently stubbed) |
| AdminComplaintQueueView | [#269 V2-WIRE-MSG-ADMINQ](https://github.com/AdamSimkinbgu/event-ticket-system/issues/269) |
| AdminAnnouncementsView | [#270 V2-WIRE-MSG-ANNOUNCE](https://github.com/AdamSimkinbgu/event-ticket-system/issues/270) |
| GlobalHistoryView | [#280 V2-WIRE-ADMIN-HISTORY](https://github.com/AdamSimkinbgu/event-ticket-system/issues/280) |
| OrganizationalTreeView | [#224 V2-VIEW-03](https://github.com/AdamSimkinbgu/event-ticket-system/issues/224) (data layer) + [#237 V2-VIEW-NW-02](https://github.com/AdamSimkinbgu/event-ticket-system/issues/237) (renderer) |

### Phase 6 — Messaging

| Flow | Issue |
|---|---|
| SubmitComplaintView | [#267 V2-WIRE-MSG-COMPLAINT](https://github.com/AdamSimkinbgu/event-ticket-system/issues/267) |
| CompanyInquiryInboxView | [#268 V2-WIRE-MSG-INBOX](https://github.com/AdamSimkinbgu/event-ticket-system/issues/268) (same as Phase 4 row) |
| AdminComplaintQueueView | [#269 V2-WIRE-MSG-ADMINQ](https://github.com/AdamSimkinbgu/event-ticket-system/issues/269) (same as Phase 5 row) |
| AdminAnnouncementsView | [#270 V2-WIRE-MSG-ANNOUNCE](https://github.com/AdamSimkinbgu/event-ticket-system/issues/270) (same as Phase 5 row) |

### Phase 7 — Notifications + real-time

| What | Issue |
|---|---|
| VaadinNotifier implements INotifier | [#225 V2-NOTIF-02](https://github.com/AdamSimkinbgu/event-ticket-system/issues/225) |
| Online detection helper | [#226 V2-NOTIF-03](https://github.com/AdamSimkinbgu/event-ticket-system/issues/226) |
| NotificationBellComponent | [#227 V2-NOTIF-04](https://github.com/AdamSimkinbgu/event-ticket-system/issues/227) |
| Offline storage + flush on login | [#70 I.6.1](https://github.com/AdamSimkinbgu/event-ticket-system/issues/70) + [#71 I.6.2](https://github.com/AdamSimkinbgu/event-ticket-system/issues/71) (requirements) |

### Phase 8 — Form validation polish + non-functional

| What | Issue |
|---|---|
| Live username uniqueness + password strength | [#258 V2-AUTH-FORM-VAL](https://github.com/AdamSimkinbgu/event-ticket-system/issues/258) |
| Reusable confirm dialog | [#252 V2-DLG-01](https://github.com/AdamSimkinbgu/event-ticket-system/issues/252) |
| Mobile responsive audit | [#288 V2-RESP-AUDIT](https://github.com/AdamSimkinbgu/event-ticket-system/issues/288) |
| A11y audit | [#287 V2-A11Y-AUDIT](https://github.com/AdamSimkinbgu/event-ticket-system/issues/287) |
| i18n audit | [#289 V2-I18N-AUDIT](https://github.com/AdamSimkinbgu/event-ticket-system/issues/289) |
| Logging hygiene | [#236 V2-LOG-02](https://github.com/AdamSimkinbgu/event-ticket-system/issues/236) |
| Javadoc — services + aggregates | [#234 V2-DOC-02](https://github.com/AdamSimkinbgu/event-ticket-system/issues/234), [#235 V2-DOC-03](https://github.com/AdamSimkinbgu/event-ticket-system/issues/235) |
| Coding standards extension | [#219 V2-DOC-04](https://github.com/AdamSimkinbgu/event-ticket-system/issues/219) |

## Hard blockers

These break parts of the wire-up no matter what view you start from:

1. **`InMemoryNotificationService.send` throws** — every `ReservationService.reserve` call routes through it via `notifyReservationSuccessIfMember` / `notifyReservationFailureIfMember`. **Not currently filed.** Two-line stub fix.
2. **No `Event` `DRAFT → SCHEDULED` transition** — `transitionToOnSale()` is unreachable through legitimate API. **Not currently filed.** The V2-DEMO-SEED branch works around it with reflection.
3. **`EventManagementService.editEventDetails` and `getEventDetail`** are `UnsupportedOperationException`. Blocks #261 and #272 respectively.
4. **`SystemAdminService.openMarket / closeMarket / viewMarketState / createDefaultAdminIfMissing`** all stubbed. Blocks #279.
5. **Service read methods that the wire-up tickets promise but don't exist yet** — e.g. `CompanyMembershipService.listForUser` (#256, #291), `MemberQueryService.usernameExists` (#258), various `*QueryService` types referenced in the wire-* bodies.

## Gaps (no ticket for the work)

| Gap | Suggested ticket |
|---|---|
| Fix `InMemoryNotificationService.send` no-op | New: `[BUG] InMemoryNotificationService.send blocks every reservation flow` |
| Add `Event.transitionToScheduled()` | New: `[DOMAIN] Event DRAFT → SCHEDULED transition missing` |
| Implement `EventManagementService.getEventDetail` + `editEventDetails` | New: `[UC-19] Implement remaining EventManagementService stubs` |
| Implement `SystemAdminService` stubs | New: `[UC-32] Implement SystemAdminService market + analytics methods` |
| `MockCart` → real `ActiveOrder` rip (no equivalent of #256 for cart) | New: `[V2-CART-WIRE] Replace MockCart with ReservationService.viewMyActiveOrder` |
| Build `CompanyMembershipService` (#256 references it but doesn't include its build) | Likely already inside #256 scope — clarify in the body |

## Overlaps and clarifications

- **`#256` V2-OWNER-WIRE-01 vs. `#291` V2-WIRE-MEMBER-COMPANIES** — both describe replacing `MockCompanies`. `#291` correctly says it depends on `#256`. Read each on its own and the relationship is clear; together they form `#256 = foundation` + `#291 = consumer`. Not a true overlap.
- **`#233` V2-RES-04 (ReservationPresenter)** is the template the rest of the buyer-side presenters should copy. Worth calling out explicitly in `coding-standards`.
- **`#221` V2-CHECK-01 / `#282` V2-WIRE-CHECKOUT-PAY / `#283` V2-WIRE-CHECKOUT-COUPON** all touch CheckoutView. They are distinct concerns (visual / payment / discount) — keep as three but make sure the bodies cite each other.
- **`#229` V2-PEDIT-01 / `#254` V2-PEDIT-02 / `#262` V2-WIRE-COMPANY-VENUE** all touch the policy / venue editor surface. PEDIT-01 is visual, PEDIT-02 is buyer simulator, V2-WIRE-COMPANY-VENUE is venue map wiring. Keep as three.

## Suggested order

The team's existing dependencies + the analysis above point to this order:

1. **Convention + blockers** — `#255` V2-ERROR-01, `#212` V2-F-07, `#209` V2-F-03, **plus** new tickets for the two hard blockers (notification stub, event transition).
2. **Foundation rip** — `#256` V2-OWNER-WIRE-01.
3. **Reference presenter** — `#233` V2-RES-04 (BentzionHadad owns).
4. **One buyer-facing slice** (`#271` browse → `#272` event → `#273` seats → cart → `#221` checkout) — proves the pattern end-to-end for buyers.
5. **One owner-facing slice** (`#291` MyCompaniesView → `#292` OwnerDashboard → `#260` events list) — proves the pattern for the owner shell.
6. **Parallel lanes** — once both reference slices are merged, every other `V2-WIRE-*` ticket can run in parallel against the same convention.
7. **Notifications + real-time** — `#225` / `#226` / `#227` once `#212` Push seam is in.
8. **Polish + audits** — `#258` validation, `#288` / `#287` / `#289` audits, docs.

## How to use this doc

- **Picking a ticket?** Find the view or concern in the phase tables, read the linked ticket body, check its `Depends on` list against the phases above.
- **Filing a new ticket?** Add a row in the relevant phase table and reference this doc from the issue body.
- **Reviewing a PR?** Confirm the change matches the phase it claims and doesn't reach into a downstream phase's territory.
