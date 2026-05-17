#### *Below is a section for each of the team members.

# LLM Usage Disclosure - Adam Simkin

**Project:** Event Ticket System — BGU Software Engineering Workshop, 2026
**Author:** Adam Simkin
**LLM(s) used:** Anthropic Claude (Sonnet/Opus via the Claude Code CLI)
**Disclosure scope:** all substantive interactions through 2026-05-15.

This document follows the structure required by §6 of the *LLM Usage Policy*. It lists every feature or component where an LLM materially affected the project, what was asked of the LLM, what the LLM produced, what I (the author) did with it, where my understanding was thin going in, and how I verified my understanding by the end.

I have continued to make every final design decision myself; the LLM was used as a sounding board for alternatives, a generator for boilerplate and scaffolding, and a tutor for unfamiliar Spring/Java APIs. Where the LLM produced non-trivial logic, I read it line by line, modified it where I disagreed, and re-wrote portions that I needed to internalize.

---

## Feature / Component: Repository review and architecture audit

- **Purpose of LLM use:** ramp-up. Get oriented in an existing codebase with unfamiliar package conventions and produce a per-issue review so I could plan my work for the semester.
- **Summary of prompt(s):** "Check my assigned issues (open and closed) and review each one. Tell me what's missing and what's already covered."
- **Output received (short description):** a series of markdown notes under `docs/my-review/` summarizing each issue, plus comments comparing issue scope against the SRS sections.
- **Files / components affected:** `docs/my-review/*` (gitignored, local-only review notes). No production code.
- **Modifications made:** I read the notes, rejected items that misread my team's intent, and used them as a personal checklist.
- **Initial gaps in understanding:** which issues mapped onto which SRS sections (II.X.Y), and how my team had partitioned the work.
- **Final understanding (brief explanation in your own words):** [TODO — write 1–2 sentences in your own words about how the issue tracker maps to SRS sections and what your slice of the project covers.]

---

## Feature / Component: Spring Boot framework orientation

- **Purpose of LLM use:** learning. I had used Spring at the framework-level before but had not wired `@Service` / `@Component` / `@Repository` / `@Value` / `@Scheduled` in a clean-architecture project of this size.
- **Summary of prompt(s):** asked the LLM to explain how Spring DI bean resolution works in this codebase, what each stereotype annotation does, and why we use ports/adapters (`ISessionManager` → `JwtSessionManager`).
- **Output received:** conceptual explanations + small annotated examples showing how a service receives its dependencies through constructor injection.
- **Files / components affected:** no code changed as a direct result; informed the way I read the project.
- **Modifications made:** N/A (conceptual).
- **Initial gaps in understanding:** difference between `@Component` / `@Service` / `@Repository`, how `@Value` reads `application.yml`, why we register `Clock` as a bean rather than calling `Instant.now()` directly.
- **Final understanding:** [TODO — write 2–3 sentences explaining bean wiring + why we inject a `Clock`.]
- **Allowed under policy §3.1** (conceptual understanding) + **§3.2** (no implementation copied).

---

## Feature / Component: Authentication rework — unified Session aggregate (UC-11 / UC-12 / UC-14)

> This is the largest single area where the LLM contributed code. Treating it with extra detail per §6.1.

- **Purpose of LLM use:** I had identified that our auth slice didn't cleanly accommodate Guest visitors (per SRS II.1.1–II.1.3) and that token validation against a denylist was fragile. I used the LLM as a sounding board for alternative designs, then to help implement the option I selected, then as a debugger when CI failed.
- **Summary of prompt(s):**
  - Discussion: "Do we support Guest users? Compare token-vs-sessionId approaches. Are there problems you notice with my approach?"
  - Design: presented the LLM with three options (A: separate Guest/Member sessions, B: token-only, C: unified Session aggregate with nullable userId). I asked for trade-offs.
  - Implementation: after I committed to Option C, I asked the LLM to scaffold the `Session` entity, repository port, and the JWT changes. I then had it iterate on `AuthenticationService` to express the Guest→Member promotion in place (D9a / D10a — both my decisions).
  - Debugging: when CI compile errors appeared after a merge from `dev`, I shared the log and asked the LLM what was wrong.
- **Output received:** new files `Session.java`, `ISessionRepository.java`, `MemorySessionRepository.java`; substantially rewritten `JwtSessionManager.java` (added `sid` claim + session-existence check on every parse) and `AuthenticationService.java` (split into `startGuestSession`, `register`, `login`, `endGuestSession`, `logout`); D7 addition `holderUserId` on `Ticket`; dual-identity refactor of `ActiveOrder` (userId OR sessionId).
- **Files / components affected:**
  - `Core/Domain/users/Session.java` (new)
  - `Core/Domain/users/ISessionRepository.java` (new)
  - `Infrastructure/persistence/MemorySessionRepository.java` (new)
  - `Core/Application/interfaces/ISessionManager.java` (extended)
  - `Infrastructure/security/JwtSessionManager.java` (substantially rewritten)
  - `Core/Application/services/AuthenticationService.java` (substantially rewritten)
  - `Core/Domain/ActiveOrder/ActiveOrder.java` (dual-identity refactor)
  - `Core/Domain/Tickets/Ticket.java` (D7 `holderUserId` field)
- **Modifications made:**
  - I rejected the LLM's initial suggestion to keep a JWT denylist alongside the Session table — the Session-existence check inside `parseClaims` is sufficient and simpler. The denylist code was removed.
  - I picked **Option C (unified Session)** myself over A/B; the LLM had argued for A on simplicity, I argued for C on the basis that cart persistence (D9a) became cleaner.
  - I picked **D8 = L1** for logout semantics, **D9a** (Member cart persists across logout), and **D10a** (session stays Guest after register — login is the explicit promotion). The LLM laid out the alternatives; I made the calls and noted them in `docs/auth-rework-plan.md` (local-only).
  - I debugged a botched 3-way merge from `dev` myself after the LLM's first auto-merge attempt mis-resolved `searchByCompany` and the `ActiveOrder.forGuest` factory call.
  - I am responsible for the resulting code — I have read every line in `Session.java`, `JwtSessionManager.java`, and `AuthenticationService.java`. I rewrote portions of `handleCartOnPromotion` to clarify the precedence order between a guest cart and a prior member cart.
- **Initial gaps in understanding:** how JWT revocation typically works (denylist vs server-side session record); how Spring's `@Value` reads timeouts from `application.yml`; how to inject `Clock` for testable time.
- **Final understanding:** [TODO — In your own words, write a paragraph that answers: (a) why we store a Session row at all if the JWT is signed; (b) what the `sid` claim does; (c) when a Guest session promotes to a Member session and what happens to its sessionId; (d) what D9a means and which code enforces it. If you can write this paragraph cold, you'll pass §7 oral validation on auth.]
- **Policy framing:** the design decisions (D8/D9a/D10a, Option C) are mine — policy §4.2 (design assistance, allowed with justification). The implementation work is LLM-assisted code generation across multiple files — this is more than the §4.1 "≤30 lines snippet" threshold and falls under restricted-use that requires this disclosure entry. **I can explain and live-modify every part of this code.** Mentors should feel free to ask me to add a constraint to the Guest→Member promotion or change the JWT claim layout.

---

## Feature / Component: UC-2 — Session and ActiveOrder Sweeper

- **Purpose of LLM use:** generate the `@Scheduled` sweeper that revokes expired sessions and carts. I had decided the contract; I was unfamiliar with Spring's `@EnableScheduling` + `@Profile` pattern.
- **Summary of prompt(s):** "Implement UC-2: a scheduled component that sweeps expired Sessions and expired ActiveOrders, respecting D9a (Member cart preserved on session expiry). Make the sweep method public so a test can drive it with a fixed Clock."
- **Output received:** `SessionAndOrderSweeper.java`, `SchedulingConfig.java` (with `@Profile("!test")` so tests don't kick off a real scheduler), and `SessionAndOrderSweeperTest.java`.
- **Files / components affected:**
  - `Infrastructure/scheduling/SessionAndOrderSweeper.java`
  - `Infrastructure/scheduling/SchedulingConfig.java`
  - `src/test/java/com/ticketing/system/unit/infrastructure/scheduling/SessionAndOrderSweeperTest.java`
- **Modifications made:** I changed the helper methods (`sweepExpiredSessions`, `sweepExpiredOrders`) from package-private to public so the test (in a different package) can drive them directly. I also reworded the Javadoc on `cleanUpAttachedCart` to make the D9a-aware behavior explicit, since the policy distinction (Guest cart dies, Member cart survives) is non-obvious.
- **Initial gaps in understanding:** the difference between `@Scheduled(fixedDelay)` and `fixedRate`; how to keep tests deterministic when a scheduler exists.
- **Final understanding:** [TODO — explain in your own words: why `@Profile("!test")` is on `SchedulingConfig`, and why the sweeper releases tickets by *grouping line items by (eventId, zoneId)* rather than one ticket at a time. Both are subtleties you'll want to defend.]
- **Policy framing:** this is **§5.5 territory** (failure-handling / expiry / concurrency). I disclose it explicitly. **Mitigation:** I have re-traced the logic on paper, I can explain the sweep contract end-to-end, and I can extend the sweeper (e.g., add a third pass for lottery codes) in a live session.

---

## Feature / Component: Spring Boot wireability — repository stubs

- **Purpose of LLM use:** to enable Spring DI for nine services, I needed in-memory implementations of five repository ports that were not yet implemented (Admin, ProductionCompany, Ticket, OrderReceipt, Conversation). These are scaffolding, not business logic.
- **Summary of prompt(s):** "Generate `Memory*Repository` stubs for these five ports so `@Service` can wire. Use `ConcurrentHashMap`. Throw `UnsupportedOperationException` for queries that aren't yet needed."
- **Output received:** five `Memory*Repository.java` files in `Infrastructure/persistence/`.
- **Files / components affected:**
  - `MemoryAdminRepository.java`
  - `MemoryProductionCompanyRepository.java`
  - `MemoryTicketRepository.java`
  - `MemoryOrderReceiptRepository.java`
  - `MemoryConversationRepository.java`
- **Modifications made:** I verified each stub against the matching `IXxxRepository` port to make sure method signatures matched, and I removed methods I didn't need. None of these contain business logic — they're CRUD over a `ConcurrentHashMap` + an `AtomicInteger` for `nextId()`.
- **Initial gaps in understanding:** how `@Repository` differs from `@Component` (mostly a marker for translating data-access exceptions in JPA — irrelevant for in-memory, but conventional).
- **Final understanding:** [TODO — one sentence: "These are temporary V1 stubs; V2 swaps them for JPA implementations behind the same port."]
- **Policy framing:** boilerplate CRUD over a `ConcurrentHashMap` is well below the §5 threshold. Each file is ≤80 lines and contains no logic the course is grading.

---

## Feature / Component: Code review and bug fixes for teammate's UC-18 PR

- **Purpose of LLM use:** my teammate's UC-18 PR (`CompanyManagementService.registerCompany`) had compile errors. We worked through it together with the LLM.
- **Summary of prompt(s):** I shared the failing CI log and the relevant files; asked the LLM to identify root causes.
- **Output received:** identification of five distinct bugs: record-style `.name()` on a non-record DTO, static calls on `IProductionCompanyRepository`, bare `ACTIVE` without import, `Company` type doesn't exist, void `save()` being treated as a return value. Suggested fixes.
- **Files / components affected:** `CompanyManagementService.java`, `CompanyManagementServiceTest.java`.
- **Modifications made:** I applied each fix manually, reviewed the DTO change that the team had agreed (no phone/email in registration DTO), and reverted the LLM's first attempt to add fields that the team had decided against.
- **Initial gaps in understanding:** I was looking at a teammate's code I hadn't written; the LLM helped me triangulate quickly.
- **Final understanding:** [TODO — one sentence: "I can explain each of the five fixes; they're standard Java/Spring issues."]
- **Policy framing:** §4.1 (code generation in small snippets) + §4.2 (review of someone else's code). My teammate is responsible for their own understanding of UC-18.

---

## Feature / Component: Design artifacts — architecture diagram and use-case book

- **Purpose of LLM use:** keep the design documents in sync with the implementation. Audit `architecture.puml`, redraw the drawio class diagram to reflect the Session split, generate a LaTeX use-case reference book from the GitHub issue bodies.
- **Summary of prompt(s):** describe the current state of the diagram; produce a clean redrawn version with the same conventions; write a generator script that fetches all UC-N issues from `gh` and produces a typeset book.
- **Output received:**
  - Audit notes on `architecture.puml` (no code changes — flagged drift).
  - A new `SadnaDesign_SessionSplit.drawio.xml` (replacement diagram).
  - `docs/latex/use-case-book.tex` (auto-generated, plus its generator script at `/tmp/build_uc_book.py`).
  - Supporting documentation: `docs/requirements.md` (distilled from SRS), `docs/issue-gap-report.md` (issues vs. SRS cross-reference).
- **Files / components affected:** documentation only — no production code.
- **Modifications made:** I reviewed the drawio output, planned to tidy the layout in drawio itself. I'll re-read the use-case book end-to-end to confirm each entry matches what we built.
- **Initial gaps in understanding:** drawio's `mxGraphModel` XML schema; LaTeX `tcolorbox` / `tabularx` setup.
- **Final understanding:** [TODO — one sentence: "These are documentation deliverables; the source of truth is the code + issue tracker."]
- **Policy framing:** §3.1 (learning) + §3.2 (documentation assistance) — unrestricted.

---

## Feature / Component: Targeted code fixes during development

- **Purpose of LLM use:** small, targeted Java fixes during the rework (rename helper visibility, fix a Mockito `when(save).thenThrow()` on a void method, adjust DTO constructor signatures).
- **Summary of prompt(s):** "This test won't compile, here's the error." / "Why does Mockito reject this?"
- **Output received:** specific fixes for specific compile errors.
- **Files / components affected:** `SessionAndOrderSweeperTest.java`, `CompanyManagementServiceTest.java`, and a handful of services touched during merge.
- **Modifications made:** applied each fix, re-ran tests.
- **Initial gaps in understanding:** the `doThrow().when().method()` pattern for void methods in Mockito (vs. the `when(...).thenThrow()` syntax for non-void).
- **Final understanding:** [TODO — one sentence: "For void methods, Mockito requires `doThrow(...).when(mock).voidMethod(...)`."]
- **Policy framing:** §4.1 (≤30 line snippets, mechanical fixes).

---

## Overall Usage Summary

- **Approximate percentage of code influenced by LLMs:** ~25–30% of code attributable to my contributions. **Concentrated** in the auth slice and the UC-2 sweeper, where the percentage is higher (≈70%). Across the wider project (work by teammates), the figure is much lower since I only worked on a slice of the codebase.
- **Main areas where LLMs were used:**
  - Authentication rework (Session aggregate, JWT + sid, Guest→Member promotion, D9a cart handling).
  - UC-2 session/cart sweeper (`@Scheduled` component + tests).
  - Infrastructure stub repositories enabling Spring DI for the rest of the team's services.
  - Spring framework orientation (DI, `@Scheduled`, `@Value`, `Clock` injection).
  - Documentation deliverables (architecture diagram update, requirements / gap analysis, use-case reference book, authentication guide).
  - Targeted code review and debugging (teammate's UC-18 PR, CI merge conflicts).
- **Main areas implemented without LLM assistance:**
  - All design decisions (Option C unified Session, D8 logout semantics, D9a cart persistence, D10a register-stays-Guest, the dual-identity ActiveOrder approach).
  - All git operations (branching, merging, conflict resolution, PR management).
  - All CI debugging beyond the surface error — root-cause analysis was mine.
  - The interactions with my team on scope (e.g., agreeing to drop phone/email from the registration DTO).
  - The PR review on my teammate's UC-18 work — I drove which fixes to accept and rewrote tests that I disagreed with.

---

## Notes for reviewers (§7 readiness)

I've focused on understanding the §5.5-adjacent code (concurrency, failure handling, permissions). Specifically:

- **Auth slice:** I can explain the Guest→Member promotion flow end-to-end, the role of the `sid` claim, the D9a cart logic, and the rationale for keeping JWT denylisting *out* of the design.
- **UC-2 sweeper:** I can defend the per-pass design (sessions then carts), the D9a-aware cleanup branch, and the inventory release grouping.
- **Permissions / role enforcement (II.4.X):** my contribution here was small (UC-18 fixes only). The core appointment-tree code is largely my teammates' work.

I welcome live modification requests on the auth slice and the sweeper. For purchase-policy logic (II.4.3.X discount composition), our team has not yet implemented the discount-types granularity (see `docs/issue-gap-report.md`); that work is open.

---







# LLM Usage Disclosure - Naim Elijah

## Feature / Component: UC-7 - Browse and Search Event Catalogs

- Purpose of LLM use:
  To help structure and review the implementation of catalog search functionality, including global search, company-scoped search, search filters, DTO mapping, and service/repository responsibility separation. The LLM was used as an implementation assistant and reviewer, not as the source of the final design.

- Summary of prompt(s):
  I asked the LLM how to implement UC-7 in the existing clean-architecture structure of the project. I described that the feature should allow searching events using filters such as event name, artist name, category, keywords, date range, price range, event rating, company rating, and location. I also asked how to separate global search from company-scoped search, where global search can include company rating and company-scoped search should only return events belonging to the requested company.

- Output received (short description):
  The LLM suggested a service-level flow for `searchGlobal` and `searchByCompany`, repository-level filtering logic for event fields, and DTO mapping from domain `Event` objects to `EventSummaryDTO`. It also helped identify edge cases such as null filters, unknown categories, inactive/missing companies, and case-insensitive string matching.

- Files / components affected:
  - `Core/Application/services/CatalogService.java`
  - `Core/Application/dto/CatalogSearchFiltersDTO.java`
  - `Core/Application/dto/EventSummaryDTO.java`
  - `Core/Application/dtoMappers/EventMapper.java`
  - `Infrastructure/persistence/MemoryEventRepository.java`
  - `Core/Domain/events/Event.java`
  - `Core/Domain/events/VenueMap.java`
  - `Core/Domain/events/InventoryZone.java`
  - `src/test/java/com/ticketing/system/unit/application/CatalogServiceTest.java`
  - `src/test/java/com/ticketing/system/unit/infrastructure/persistence/EventPersistence/IEventRepositoryContractTest.java`

- Modifications made:
  I adapted the suggested logic into the existing project structure. I kept the repository responsible for filtering event-internal fields such as event name, artist name, category, keywords, date, price, event rating, and location. I kept company-related logic in the service layer, especially filtering by company id and company rating because those require access to the production company repository. I also reviewed the LLM suggestions manually and adjusted the logic to match the project’s DTOs, repository interfaces, enum names, and existing domain methods.

- Initial gaps in understanding (if any):
  I was not fully sure where each filter should belong: inside `CatalogService`, inside `IEventRepository.search`, or inside the domain model. I also needed clarification about the difference between global catalog search and company-scoped search.

- Final understanding (brief explanation in your own words):
  UC-7 is a read-side use case. The repository can filter events according to properties that belong directly to the event, while the service layer is responsible for orchestration, authentication/credential validation, company-related checks, and DTO mapping. Global search may consider company-level rating, while company-scoped search must restrict results to one company and should not behave like an unrestricted global search.

## Overall Usage Summary
- Approximate percentage of code influenced by LLMs:
  Approximately 30-40% of my UC-7 implementation was influenced by LLM assistance, mainly in structuring the service/repository flow and thinking through filter edge cases. The final code integration, project-specific adaptation, and correctness review were done by me.

- Main areas where LLMs were used:
  Search-flow structure, filter edge cases, DTO mapping guidance, and test-case ideas.

- Main areas implemented without LLM assistance:
  Final decisions about how to fit the code into the project architecture, adapting names/signatures to the existing codebase, deciding which repository/service methods to call, and verifying the behavior against the project requirements.


## Feature / Component: UC-8 - View Event Venue Map and Inventory Status

- Purpose of LLM use:
  To help implement and review the flow for viewing an event venue map and inventory/zone status, including validation of the provided credential, event existence checks, production company status checks, null venue-map handling, and conversion from domain objects to DTOs.

- Summary of prompt(s):
  I asked the LLM how to implement a use case where a guest or logged-in member can view an event’s venue map and inventory status. I described that the function receives a credential and an event id, validates the credential, loads the event, verifies that the event exists and belongs to an active company, checks that the venue map is not null, and returns a DTO representation of the venue map.

- Output received (short description):
  The LLM suggested a service-level validation sequence and a mapper-based DTO conversion approach. It also helped identify important failure cases: invalid credential, missing event, inactive production company, and missing venue map.

- Files / components affected:
  - `Core/Application/services/CatalogService.java`
  - `Core/Application/dto/VenueMapDTO.java`
  - `Core/Application/dto/InventoryZoneDTO.java`
  - `Core/Application/dto/LocationDTO.java`
  - `Core/Application/dtoMappers/VenueMapMapper.java`
  - `Core/Domain/events/VenueMap.java`
  - `Core/Domain/events/InventoryZone.java`
  - `Core/Domain/events/Location.java`
  - `Core/Domain/events/Event.java`
  - `Core/Domain/company/ProductionCompany.java`
  - `Core/Domain/company/CompanyStatus.java`
  - `Core/Application/interfaces/ISessionManager.java`
  - `src/test/java/com/ticketing/system/unit/application/CatalogServiceTest.java`

- Modifications made:
  I adapted the LLM’s suggested flow to the project’s actual interfaces and exceptions. The function validates the credential, loads the event by id, checks whether the event exists, checks that the event’s production company is active, verifies that the event has a venue map, and then maps the venue map into a DTO. I also reviewed and adjusted the mapping logic so that the DTO preserves the venue zones and location information in a way that matches the project’s domain model.

- Initial gaps in understanding (if any):
  I initially needed clarification about whether this use case should require a member JWT only or whether it should also accept guest sessions. I also needed to understand how the domain `VenueMap` and `InventoryZone` objects should be exposed safely as application DTOs.

- Final understanding (brief explanation in your own words):
  UC-8 is a read-only catalog use case. A user should not need to own the event or company to view the venue map, but the request still needs a valid session/credential. The service is responsible for validating access and checking event/company state, while the mapper is responsible only for converting the domain venue map into a DTO.

## Overall Usage Summary
- Approximate percentage of code influenced by LLMs:
  Approximately 25-35% of this feature was influenced by LLM assistance, mostly around the validation sequence, DTO mapping structure, and test-case coverage.

- Main areas where LLMs were used:
  Validation-order review, DTO mapping guidance, exception-case identification, and unit test ideas.

- Main areas implemented without LLM assistance:
  Final integration into `CatalogService`, adapting the code to the existing repositories and DTOs, deciding the exact exception behavior, and verifying the behavior manually.


## Feature / Component: UC-22 - View Company Sales History

- Purpose of LLM use:
  To help implement and review the company sales-history query flow, including authentication, authorization, retrieving receipts by company id, mapping order receipts to purchase-history DTOs, and ensuring that only authorized company users can view the data.

- Summary of prompt(s):
  I asked the LLM how to implement a company-side sales history use case. I described that a user provides a token and company id, the service validates the token, extracts the requesting user id, verifies that the company exists, verifies that the user exists, checks whether the user is an owner or has the `VIEW_SALES` permission, then retrieves order receipts for the company and maps them to purchase-history DTOs.

- Output received (short description):
  The LLM suggested a service-level implementation outline, including token validation, permission checking, repository calls, and mapping receipts/tickets/events into `PurchaseHistoryDTO`. It also suggested relevant test cases for owner access, manager-with-permission access, unauthorized access, missing company, invalid token, and multiple sales records.

- Files / components affected:
  - `Core/Application/services/CompanyManagementService.java`
  - `Core/Application/dto/PurchaseHistoryDTO.java`
  - `Core/Application/dtoMappers/OrderReceiptMapper.java`
  - `Core/Domain/orders/IOrderReceiptRepository.java`
  - `Core/Domain/orders/OrderReceipt.java`
  - `Core/Domain/Tickets/ITicketRepository.java`
  - `Core/Domain/Tickets/Ticket.java`
  - `Core/Domain/events/IEventRepository.java`
  - `Core/Domain/events/Event.java`
  - `Core/Domain/users/User.java`
  - `Core/Domain/users/Permission.java`
  - `src/test/java/com/ticketing/system/unit/application/CompanyManagementServiceTest.java`

- Modifications made:
  I adapted the suggested flow into `CompanyManagementService.viewSalesHistory`. I added token validation, extracted the requester id from the session manager, loaded the company and user, checked ownership or `VIEW_SALES` permission, retrieved receipts using `findByCompanyId`, and mapped each receipt to a purchase-history DTO using `OrderReceiptMapper`. I reviewed the permission logic myself because role and permission enforcement is a core part of the project and must not be blindly generated.

- Initial gaps in understanding (if any):
  I needed clarification about whether only owners can view sales history or whether managers with a specific permission can also view it. I also needed to understand how to reuse the existing purchase-history DTO shape for company sales history.

- Final understanding (brief explanation in your own words):
  UC-22 is a company-management use case. The caller must be authenticated, and the service must authorize the caller based on their relationship to the company. Owners can view the company sales history, and managers can view it only if they have the correct permission. The receipt mapper creates a read-only snapshot of purchases by combining receipt, ticket, and event information.

## Overall Usage Summary
- Approximate percentage of code influenced by LLMs:
  Approximately 30-40% of this feature was influenced by LLM assistance, mainly in organizing the service flow and identifying test cases. The permission decision and final authorization behavior were reviewed and owned by me.

- Main areas where LLMs were used:
  Service-flow structure, mapping approach, exception/test-case ideas, and review of authorization edge cases.

- Main areas implemented without LLM assistance:
  Final role/permission decision, integration with the existing `User` and `ProductionCompany` domain methods, final repository calls, and manual understanding of the permission logic.


## Feature / Component: UC-25 - View Organizational Tree

- Purpose of LLM use:
  To generate a helper function that uses BFS as I instructed it, based on the system rules and the existing appointment logic. I gave the LLM the desired algorithm and the logic of the system, so it would build the helper function according to my design rather than inventing its own role-management logic.

- Summary of prompt(s):
  I asked the LLM to keep `viewOrganizationalTree` the same and only change the helper function `buildOrganizationalTree`. I explained that the helper receives the founder’s id, which is the `ownerId` field in the company, and the company id. It should build the tree starting from the root `OrganizationalTreeNodeDTO` with the owner id. Then it should continue with BFS to the users that the current user appointed as managers, build their `OrganizationalTreeNodeDTO`s, add them to the current node’s `appointedByThisUser` list, add them to the BFS queue, and continue with normal BFS logic. At the end, when the BFS queue is empty, the function returns the root `OrganizationalTreeNodeDTO`.

- Output received (short description):
  The LLM produced a suggested BFS-based helper implementation that builds a nested organizational tree from the founder/owner node downward through appointed managers. The output used a queue, created DTO nodes for each appointed user, connected each child node to the parent’s `appointedByThisUser` list, and returned the root after the BFS finished.

- Files / components affected:
  - `Core/Application/services/CompanyManagementService.java`
  - `Core/Application/dto/OrganizationalTreeNodeDTO.java`
  - `Core/Domain/company/ProductionCompany.java`
  - `Core/Domain/users/User.java`
  - `Core/Domain/users/CompanyAppointment.java`
  - `Core/Domain/users/IUserRepository.java`
  - `Core/Domain/company/IProductionCompanyRepository.java`
  - `src/test/java/com/ticketing/system/unit/application/CompanyManagementServiceTest.java`

- Modifications made:
  I reviewed the generated BFS helper and adapted it to the project’s actual DTO constructors, repository methods, and domain model. I kept the public `viewOrganizationalTree` function responsible for authentication and authorization, and kept the helper responsible only for building the tree structure. I verified the BFS logic manually: start from the founder/root, visit each node, find the users appointed by that node, create child DTOs, attach them to the parent node, enqueue them, and continue until the queue is empty.

- Initial gaps in understanding (if any):
  I needed help translating the organizational appointment model into a tree-building algorithm without accidentally using recursion incorrectly or mixing authorization logic into the tree-building helper. I also wanted to avoid changing the public method unnecessarily.

- Final understanding (brief explanation in your own words):
  UC-25 returns a hierarchical view of the company management structure. The founder/owner is the root. Each manager appears under the user who appointed them. BFS is useful here because it builds the tree level by level and avoids deep recursive calls. The algorithm itself does not decide permissions; it only builds the DTO tree from the already-valid company appointment relationships. Authorization must remain in the service-level entry function.

## Overall Usage Summary
- Approximate percentage of code influenced by LLMs:
  Approximately 40-50% of the helper-function implementation was influenced by the LLM, but the algorithm, system rules, and intended BFS behavior were provided by me in the prompt. The public use-case behavior and authorization responsibility remained my design decision.

- Main areas where LLMs were used:
  Translating my BFS description into Java helper-code structure, creating DTO nodes, queue-based traversal, and suggesting test cases for nested appointment structures.

- Main areas implemented without LLM assistance:
  The decision to use BFS, the definition of the organizational tree semantics, the rule that the founder/owner is the root, the role/permission meaning of appointments, and the final review of authorization boundaries.


## Feature / Component: UC-31 - View Global Purchase History

- Purpose of LLM use:
  To help implement and review the system-admin global purchase-history query, including filter passing, receipt mapping, DTO reuse, and the distinction between authentication and admin authorization.

- Summary of prompt(s):
  I asked the LLM how to implement UC-31 in `SystemAdminService`. I described that the function should receive global-history filters, query the order receipt repository for matching receipts, map each receipt into a purchase-history record, and return a `PurchaseHistoryDTO`. I also asked whether this system-admin service should validate a token using `validateToken`, and the LLM explained that `validateToken` proves only that the caller is a logged-in member, while a separate admin authorization check is needed to prove that the caller is a system admin.

- Output received (short description):
  The LLM suggested a mapping-based implementation for `viewGlobalHistory`, reuse of `OrderReceiptMapper`, and a security review explaining that admin-only use cases should validate the token, extract the user id, and then check whether that user is a system admin. It also helped identify that bootstrap methods such as default-admin creation should not necessarily require an existing admin token.

- Files / components affected:
  - `Core/Application/services/SystemAdminService.java`
  - `Core/Application/dto/GlobalHistoryFiltersDTO.java`
  - `Core/Application/dto/PurchaseHistoryDTO.java`
  - `Core/Application/dtoMappers/OrderReceiptMapper.java`
  - `Core/Domain/orders/IOrderReceiptRepository.java`
  - `Core/Domain/orders/OrderReceipt.java`
  - `Core/Domain/Tickets/ITicketRepository.java`
  - `Core/Domain/Tickets/Ticket.java`
  - `Core/Domain/events/IEventRepository.java`
  - `Core/Domain/events/Event.java`
  - `Core/Domain/Admin/IAdminRepository.java`
  - `Core/Application/interfaces/ISessionManager.java`
  - `src/test/java/com/ticketing/system/unit/application/SystemAdminServiceTest.java`

- Modifications made:
  I implemented/reviewed the mapping flow that calls `orderReceiptRepository.findGlobal(filters)`, maps each receipt using `OrderReceiptMapper`, and returns the records inside `PurchaseHistoryDTO`. I also reviewed the authorization issue separately: because `validateToken` only proves that a user is logged in, UC-31 should require a helper such as `requireSystemAdmin(token)` that validates the token, extracts the user id, and checks the admin repository. I understood that this admin authorization check is my responsibility and should not be blindly copied from the LLM.

- Initial gaps in understanding (if any):
  I initially confused authentication with authorization. I was not fully sure whether calling `validateToken(token)` inside `SystemAdminService` was enough. I also needed to understand whether system bootstrap functions should use the same admin-token logic as regular admin-only functions.

- Final understanding (brief explanation in your own words):
  UC-31 is an admin-only read use case. The service should retrieve global purchase history according to filters and map receipts to DTO records, but access must be protected by both authentication and authorization. `validateToken` confirms that the credential belongs to a logged-in member; it does not prove system-admin privileges. Therefore, global-history access should also check the admin repository or another admin-role source before returning data.

## Overall Usage Summary
- Approximate percentage of code influenced by LLMs:
  Approximately 30-40% of the UC-31 implementation/review was influenced by LLM assistance, mostly around DTO mapping, test-case structure, and identifying the missing distinction between token validation and system-admin authorization.

- Main areas where LLMs were used:
  Mapping receipts to purchase-history records, filter-flow review, test-case ideas, and security review of admin-only access.

- Main areas implemented without LLM assistance:
  Final understanding of the admin authorization requirement, integration with the existing repositories and DTOs, and the decision about which system-admin methods should or should not require a token.


## Feature / Component: Cross-cutting Authentication and Authorization Review

- Purpose of LLM use:
  To review how authentication should be applied consistently across service-layer functions, especially where functions receive tokens or credentials directly and where the project does not yet use Spring Security annotations or middleware-based authentication.

- Summary of prompt(s):
  I asked the LLM whether functions inside `SystemAdminService` and other application services should call `validateToken(String token, ...)`. I also provided the project’s authentication guide and asked how to apply it correctly to member-only, guest-or-member, and admin-only functions.

- Output received (short description):
  The LLM explained the difference between `validateCredential` and `validateToken`, and the difference between authentication and authorization. It suggested using `validateCredential` for guest-or-member use cases, `validateToken` plus `extractUserId` for member-only use cases, and an additional role/permission/admin check for protected management/admin use cases.

- Files / components affected:
  - `Core/Application/interfaces/ISessionManager.java`
  - `Infrastructure/security/JwtSessionManager.java`
  - `Core/Application/services/CatalogService.java`
  - `Core/Application/services/CompanyManagementService.java`
  - `Core/Application/services/SystemAdminService.java`
  - `Core/Domain/users/Session.java`
  - `Core/Domain/users/ISessionRepository.java`
  - `Core/Domain/Admin/IAdminRepository.java`
  - `Core/Domain/users/Permission.java`

- Modifications made:
  I used the review to clarify where each authentication method belongs. For member-only functions, I should validate the JWT token and extract the user id from the token rather than trusting a client-provided user id. For guest-or-member functions, I should use credential validation so that both guest sessions and member JWTs can work. For admin-only or permission-protected functions, I should add an authorization check after token validation.

- Initial gaps in understanding (if any):
  I was not fully sure whether token validation should happen in every service function, in middleware, or only in some functions. I also needed clarification about whether token validation alone proves that a caller has a specific role.

- Final understanding (brief explanation in your own words):
  Authentication answers “who is this caller?” while authorization answers “is this caller allowed to perform this action?” In this project, because Spring Security is not used as the main enforcement mechanism in V1, services that expose protected use cases must validate credentials/tokens themselves. A valid token is necessary for member-only actions, but not sufficient for owner/manager/admin actions; those require additional role or permission checks.

## Overall Usage Summary
- Approximate percentage of code influenced by LLMs:
  Approximately 30-40% of my authentication/authorization-related service changes and reviews were influenced by LLM explanations and suggestions. The final decisions about where to enforce permissions and how to adapt the logic to the project remain mine.

- Main areas where LLMs were used:
  Understanding credential validation, token validation, session-based identity, member-only versus guest-or-member flows, and admin/permission checks.

- Main areas implemented without LLM assistance:
  Final integration into the project services, deciding which use cases are public/guest/member/admin, and reviewing each authorization rule according to the project requirements.


## Overall Usage Summary

- Approximate percentage of code influenced by LLMs:
  Approximately 30-40% of my contributed code for the listed use cases was influenced by LLM assistance. The influence was mainly in implementation structure, DTO mapping, validation-order review, edge-case discovery, and test-case ideas. The final architecture integration, requirement interpretation, and responsibility for correctness are mine.

- Main areas where LLMs were used:
  - Catalog search flow for UC-7.
  - Venue-map validation and DTO mapping for UC-8.
  - Company sales-history mapping and access-flow review for UC-22.
  - BFS helper implementation for UC-25, based on an algorithm and system rules I provided.
  - Global purchase-history mapping and admin-authentication review for UC-31.
  - Cross-cutting clarification of `validateToken`, `validateCredential`, `extractUserId`, and role/permission checks.

- Main areas implemented without LLM assistance:
  - Final project-specific design decisions.
  - Final interpretation of the use-case rules.
  - Final authorization/permission responsibility.
  - Integration with the existing repositories, DTOs, and domain objects.
  - Manual code review, debugging, and verification.
  - Understanding and ability to explain the code during oral validation.







# LLM Usage Disclosure - Moshe Klein



# LLM Usage Disclosure - Bentziyon Hadad



# LLM Usage Disclosure - Mohamad



# LLM Usage Disclosure - Abed





*This file is maintained as a living document. Future LLM-assisted work will be appended in the same format below.*










