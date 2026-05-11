# GitHub Issues Plan

## Labels

| Label | Color | Purpose |
|---|---|---|
| `initialize` | #0075ca | Project bootstrapping (already created) |
| `domain:auth` | #0052cc | Authentication, login, registration |
| `domain:events` | #0e8a16 | Browsing, searching, venue maps |
| `domain:ticketing` | #e4820d | Reservation, checkout, payment, refunds, lottery |
| `domain:company` | #5319e7 | Company management, roles, policies |
| `domain:admin` | #b60205 | System admin actions |
| `domain:notifications` | #fbca04 | Real-time & offline notifications |
| `non-functional` | #aaaaaa | Performance, security, scalability, observability |
| `acceptance-test` | #f9d0c4 | Gherkin test tracking per use case |

---

## Issues

### initialize (2 issues)

| # | Title | Traceability |
|---|---|---|
| - | UC-1: Initialize Ticketing Platform | I.1.1, I.1.2, I.1.3, I.1.4 |
| - | UC-32: Open Trading Market | I.2.1, I.2.2, I.2.3 |

---

### domain:auth (6 issues)

| # | Title | Traceability |
|---|---|---|
| - | UC-11: Register Account | II.1.3 |
| - | UC-12: Login | II.1.4 |
| - | UC-13: Restore Active Order on Login | II.3.0.2 |
| - | UC-14: Logout | II.3.0.1, II.3.1 |
| - | UC-15: Manage Profile | II.3.4, II.3.8 |
| - | UC-16: View Personal Purchase History | II.3.5.1, II.3.5.2, II.3.7 |

---

### domain:events (3 issues)

| # | Title | Traceability |
|---|---|---|
| - | UC-3: Browse Events as Guest | II.1.1 |
| - | UC-7: Browse and Search Event Catalogs | II.2.1, II.2.3.1, II.2.3.2 |
| - | UC-8: View Event Venue Map and Inventory Status | II.2.2.1, II.2.2.2 |

---

### domain:ticketing (9 issues)

| # | Title | Traceability |
|---|---|---|
| - | UC-5: Create Active Order (First Ticket Selection) | II.1.2 |
| - | UC-9: Select Tickets & Lock Inventory | II.1.2, II.2.4, II.2.5, II.2.7 |
| - | UC-2: Expire Temporary User Access | II.1.3, II.2.6.2, II.3.0.3 |
| - | UC-10: Complete Checkout (All-or-Nothing) | I.3.1, I.4.1, II.2.8.1, II.2.8.2 |
| - | UC-4: Auto-Refund Processing | I.3.3 |
| - | UC-33: Charge Payment via External Gateway | I.3.1 |
| - | UC-34: Issue Tickets via External Service | I.4.1 |
| - | UC-17: Enroll in Purchase Lottery | II.3.9.1 |
| - | UC-6: Grant Lottery Winner Access (Timed Code) | II.3.9.2 |

---

### domain:company (7 issues)

| # | Title | Traceability |
|---|---|---|
| - | UC-18: Register Production Company | II.3.2.1, II.3.2.2 |
| - | UC-19: Manage Event Catalog | II.4.1.1, II.4.1.2 |
| - | UC-20: Configure Venue Map & Inventory | II.4.2.1, II.4.2.2, II.4.2.3 |
| - | UC-21: Configure Purchase & Discount Policies | II.4.3.1, II.4.3.2 |
| - | UC-22: View Company Sales History | II.4.5.1, II.4.5.2 |
| - | UC-23: Appoint Co-Owner | II.4.8.1, II.4.8.2, II.4.8.3 |
| - | UC-24: Appoint / Edit / Revoke Event Manager | II.4.7.1, II.4.7.2, II.4.7.3, II.4.11, II.4.12 |
| - | UC-25: View Organizational Tree | II.4.15 |

---

### domain:admin (1 issue)

| # | Title | Traceability |
|---|---|---|
| - | UC-31: View Global Purchase History | II.6.4 |

---

### domain:notifications (3 issues)

| # | Title | Traceability |
|---|---|---|
| - | UC-35: Send Real-Time Notification | I.5.1, I.5.2, I.5.3 |
| - | UC-36: Store Offline Notification | I.6.1 |
| - | UC-37: Deliver Delayed Notifications on Login | I.6.2 |

---

### non-functional (5 issues)

| # | Title | Traceability |
|---|---|---|
| - | Consistency & Race Condition Prevention | SLR.1.1, SLR.1.2 |
| - | Data Security & Privacy (Password Hashing, User Data) | SLR.2 |
| - | Performance, Response Times & Usability Under Load | SLR.3.1, SLR.3.2, SLR.3.3 |
| - | Scalability, Traffic Spike Resilience & High Availability | SLR.4.1, SLR.4.2, SLR.4.3 |
| - | Fault Tolerance, Crash Recovery, State Persistence & Observability | SLR.5, SLR.6, SLR.7, SLR.8.1, SLR.8.2 |

---

## Summary

| Label | Count |
|---|---|
| `initialize` | 2 |
| `domain:auth` | 6 |
| `domain:events` | 3 |
| `domain:ticketing` | 9 |
| `domain:company` | 7 |
| `domain:admin` | 1 |
| `domain:notifications` | 3 |
| `non-functional` | 5 |
| **Total** | **36** |
