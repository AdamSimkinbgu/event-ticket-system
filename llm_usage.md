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
# For each LLM interaction affecting the project:

## Feature / Component: UC-7 - 
- Purpose of LLM use:
- Summary of prompt(s):
- Output received (short description):
- Files / components affected:
- Modifications made:
- Initial gaps in understanding (if any):
- Final understanding (brief explanation in your own words):
6.2 General Summary Section
## Overall Usage Summary
- Approximate percentage of code influenced by LLMs:
- Main areas where LLMs were used:
- Main areas implemented without LLM assistance:

## Feature / Component: UC-8 - 
- Purpose of LLM use:
- Summary of prompt(s):
- Output received (short description):
- Files / components affected:
- Modifications made:
- Initial gaps in understanding (if any):
- Final understanding (brief explanation in your own words):
6.2 General Summary Section
## Overall Usage Summary
- Approximate percentage of code influenced by LLMs:
- Main areas where LLMs were used:
- Main areas implemented without LLM assistance:

## Feature / Component: UC-22 - 
- Purpose of LLM use:
- Summary of prompt(s):
- Output received (short description):
- Files / components affected:
- Modifications made:
- Initial gaps in understanding (if any):
- Final understanding (brief explanation in your own words):
6.2 General Summary Section
## Overall Usage Summary
- Approximate percentage of code influenced by LLMs:
- Main areas where LLMs were used:
- Main areas implemented without LLM assistance:

## Feature / Component: UC-25 - 
- Purpose of LLM use:
- Summary of prompt(s):
- Output received (short description):
- Files / components affected:
- Modifications made:
- Initial gaps in understanding (if any):
- Final understanding (brief explanation in your own words):
6.2 General Summary Section
## Overall Usage Summary
- Approximate percentage of code influenced by LLMs:
- Main areas where LLMs were used:
- Main areas implemented without LLM assistance:

## Feature / Component: UC-31 - 
- Purpose of LLM use:
- Summary of prompt(s):
- Output received (short description):
- Files / components affected:
- Modifications made:
- Initial gaps in understanding (if any):
- Final understanding (brief explanation in your own words):
6.2 General Summary Section
## Overall Usage Summary
- Approximate percentage of code influenced by LLMs:
- Main areas where LLMs were used:
- Main areas implemented without LLM assistance:


## Feature / Component: .............................................................
- Purpose of LLM use:
- Summary of prompt(s):
- Output received (short description):
- Files / components affected:
- Modifications made:
- Initial gaps in understanding (if any):
- Final understanding (brief explanation in your own words):
6.2 General Summary Section
## Overall Usage Summary
- Approximate percentage of code influenced by LLMs:
- Main areas where LLMs were used:
- Main areas implemented without LLM assistance:



# LLM Usage Disclosure - Moshe Klein

# LLM Usage Disclosure - Bentziyon Hadad

# LLM Usage Disclosure - Mohamad

# LLM Usage Disclosure - Abed



*This file is maintained as a living document. Future LLM-assisted work will be appended in the same format below.*










