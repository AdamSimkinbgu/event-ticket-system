# Review Checklist — Moshe's Batch

**Branch:** `claude/moshe-infra` (off `dev`)
**Scope:** Cross-cutting infrastructure — logging, generic repository contract, and the runtime invariant-verification harness integrated into every domain test file.

> Replace placeholders before running: `MOSHE_NAME`, `MOSHE_EMAIL`, `MOSHE_FINAL_BRANCH_NAME`.

---

## 1. What changed and why

### 1.1 Logging overhaul

| File | Change |
|---|---|
| `pom.xml` | Added `spring-boot-starter-aop` |
| `Infrastructure/logging/LoggingAspect.java` | **NEW** — `@Around` advice on every public method in `Core.Application.services.*`. Logs entry (DEBUG with arg count, no values), exit (DEBUG with elapsed ms), and exception (WARN with type/message + ms). |
| `resources/logback-spring.xml` | Header comment updated to reference @Slf4j + AOP. Added per-profile logger config so `Core.Domain.*` warnings/errors funnel to the same file appender. |
| 13 `*Service` + 2 infra classes | Replaced `private static final Logger log = LoggerFactory.getLogger(X.class)` boilerplate with Lombok `@Slf4j` annotation. Renamed `logger` / `eventLogger` / `errorLogger` call sites to `log`. **No behavior change** — `log.info(...)` etc. work identically. |
| `CheckoutService.java` | Same conversion, plus added missing `@Service` annotation (it was a bean without the stereotype — now properly registered). |

**Why:** Lecture-2 rule "loggers should be injected, not constructed in every class." `@Slf4j` is Lombok's compile-time injection — generates the same static `log` field but at one annotation instead of a boilerplate line per class. The aspect satisfies "log each service function at the start and end" without manually editing every method body.

### 1.2 Generic repository contract

| File | Change |
|---|---|
| `Core/Domain/shared/IRepository.java` | **NEW** — generic `IRepository<T, ID>` defining `lockForUpdate(ID)` and `unlock(ID)`. Documents the JPA-ready synchronization seam. |
| `Infrastructure/persistence/RepositoryLocks.java` | **NEW** — per-key `ReentrantLock` map. Helper used by every Memory repo to satisfy `lockForUpdate`/`unlock`. |
| 10 `I*Repository.java` interfaces | Each now `extends IRepository<X, IdType>` with the appropriate aggregate and ID type. **Existing methods unchanged.** |
| 10 `Memory*Repository.java` impls | Each adds one `RepositoryLocks<IdType> locks` field and two trivial `@Override` methods. **Existing methods unchanged.** |

**Why:** The course design rule "no cross-aggregate relations" is fine, but pre-purchase contention on the same aggregate (event reservation, owner changes, etc.) was unsafe — `ConcurrentHashMap` provides per-call atomicity, not per-transaction atomicity. The `lockForUpdate` → mutate → `unlock` pattern serializes writes to the same id. JPA implementations will later translate `lockForUpdate` to `SELECT ... FOR UPDATE`; memory impls use `ReentrantLock`.

Note: `IActiveOrderRepository` uses `String` as the lock-id type because ActiveOrder has dual identity (userId or sessionId); callers must namespace (e.g. `"user:" + userId`). Documented in the interface Javadoc.

### 1.3 Invariant-verification harness — integrated into every domain test

| File | Change |
|---|---|
| `Core/Domain/shared/InvariantChecked.java` | **NEW** — single-method interface (`void checkInvariants()`). |
| `support/BaseDomainTest.java` (test) | **NEW** — abstract base. Tests call `track(aggregate)` to register; the `@AfterEach` hook calls `checkInvariants()` on every tracked aggregate. Failures from multiple aggregates are reported together via suppressed exceptions. |
| 12 aggregate classes | Each now `implements InvariantChecked` and exposes a `checkInvariants()` method that throws `IllegalStateException` with a descriptive message on violation. The 12: `Admin`, `User`, `Session`, `OrderReceipt`, `ActiveOrder`, `Conversation`, `ReceiptLine`, `Event`, `InventoryZone`, `Notification`, `ProductionCompany`, `Ticket`. |
| 11 domain test classes | Each now `extends BaseDomainTest` and wraps aggregate constructions with `track(...)`. After every existing test, the harness automatically verifies invariants. |

**Why:** Per Adam's feedback — the value of invariant verification is catching the case where existing operations leave an aggregate in a structurally broken state, not testing the `checkInvariants` method in isolation. Integrating into existing tests means every existing test scenario also verifies the structural rules.

**One real bug it surfaced during this work:** `UserTest.setUp` used an empty string `""` for email. The User invariant requires non-blank email. Fixed the test data to `"target@example.com"`.

**One existing design choice it respected:** `Session` intentionally does NOT validate userId positivity (per `SessionTest#memberSession_acceptsZeroAndNegativeUserIds` — "Domain doesn't validate userId positivity — that's the User aggregate's job"). My initial draft of `Session.checkInvariants` was tightened too far; corrected.

---

## 2. Files to review (57 total)

### Source — new (5)
- `src/main/java/com/ticketing/system/Core/Domain/shared/IRepository.java`
- `src/main/java/com/ticketing/system/Core/Domain/shared/InvariantChecked.java`
- `src/main/java/com/ticketing/system/Infrastructure/logging/LoggingAspect.java`
- `src/main/java/com/ticketing/system/Infrastructure/persistence/RepositoryLocks.java`
- `src/test/java/com/ticketing/system/support/BaseDomainTest.java`

### Source — modified (43)

Logging (15 files): `pom.xml`, `logback-spring.xml`, `AuthenticationService`, `CatalogService`, `CheckoutService`, `CompanyManagementService`, `EventManagementService`, `MemberAccountService`, `MessagingService`, `NotificationDispatchService`, `ReservationService`, `SystemAdminService`, `JwtSessionManager`, `SessionAndOrderSweeper`.

Repository interfaces (10 files): `IUserRepository`, `ISessionRepository`, `INotificationRepository`, `IAdminRepository`, `IConversationRepository`, `IProductionCompanyRepository`, `IEventRepository`, `ITicketRepository`, `IOrderReceiptRepository`, `IActiveOrderRepository`.

Memory repository impls (10 files): same names, `Memory*Repository`.

Aggregates with invariants (12 files): `Admin`, `User`, `Session`, `OrderReceipt`, `ActiveOrder`, `Conversation`, `ReceiptLine`, `Event`, `InventoryZone`, `Notification`, `ProductionCompany`, `Ticket`.

### Tests — modified (11)
- All 11 files in `src/test/java/com/ticketing/system/unit/domain/` now `extends BaseDomainTest`.

---

## 3. What to verify before committing

Run each, expect green:

```bash
./mvnw clean compile         # must compile clean
./mvnw test                  # 552 tests should pass, 0 failures, 0 errors
```

Quick spot-checks:

- [ ] Open one service (e.g. `AuthenticationService.java`) and confirm `@Slf4j` is present, `LoggerFactory.getLogger(...)` is gone, all `log.info(...)` calls still work.
- [ ] Open one memory repo (e.g. `MemoryEventRepository.java`) and confirm `lockForUpdate` + `unlock` exist and delegate to `RepositoryLocks`.
- [ ] Open one aggregate (e.g. `User.java`) and read the `checkInvariants` method — confirm the rules match your understanding of what makes a valid `User`.
- [ ] Open `BaseDomainTest.java` and read the `@AfterEach` block — confirm the failure-reporting logic is what you want.
- [ ] `tail -f logs/event-ticket-system.log` while running tests; you should see entry/exit lines for service methods at DEBUG level (visible under the `test` Spring profile).

---

## 4. How to commit as Moshe

Currently the branch `claude/moshe-infra` exists locally with all changes uncommitted (or in a temporary `claude-prep` commit — see step 1).

```bash
# 1. Make sure you're on the right branch
git checkout claude/moshe-infra
git status         # should show files modified per the lists above

# 2. Stage everything (review the diff first!)
git diff           # read through the changes
git add -A

# 3. Commit as yourself — the --author flag ensures your name + email
#    is recorded as the commit author regardless of git config
git commit --author="MOSHE_NAME <MOSHE_EMAIL>" -m "$(cat <<'EOF'
infra: introduce AOP logging, generic IRepository contract, and invariant harness

- Add spring-boot-starter-aop and LoggingAspect for cross-cutting entry/exit
  tracing on every Core.Application.services public method (no PII in logs).
- Convert 13 services + 2 infra classes from LoggerFactory.getLogger to
  Lombok @Slf4j; rename eventLogger/errorLogger/logger call sites to log.
- Extend logback-spring.xml so domain warnings/errors funnel to the same file.
- Introduce IRepository<T,ID> with lockForUpdate/unlock seam for JPA-ready
  pessimistic locking; all 10 IXxxRepository interfaces extend it; all 10
  MemoryXxxRepository impls wire RepositoryLocks<ID>.
- Introduce InvariantChecked + BaseDomainTest harness. All 12 aggregate roots
  implement checkInvariants(); all 11 domain test classes extend BaseDomainTest
  and track() the aggregates they construct, so existing test scenarios
  automatically verify structural rules.
EOF
)"

# 4. Push and create the PR-style branch name you actually want on GitHub
git push origin claude/moshe-infra:MOSHE_FINAL_BRANCH_NAME
```

**Why `--author`:** Without it, `git config user.name/email` (which is currently `Adam Simkin`) gets recorded as the author. The `--author` flag is the clean way to attribute a commit to a different person.

---

## 5. Renaming the branch on GitHub (after pushing)

If you pushed as `MOSHE_FINAL_BRANCH_NAME` already, you're done — skip this section.

If you pushed under a temporary name (like `claude/moshe-infra`) and want to rename it on GitHub:

### Option A — Push under the correct name, delete the temp

```bash
# Push your work under the desired name
git push origin claude/moshe-infra:MOSHE_FINAL_BRANCH_NAME

# Delete the temporary remote branch
git push origin --delete claude/moshe-infra
```

### Option B — Rename via the GitHub UI

1. Open `https://github.com/<owner>/<repo>/branches`
2. Find the branch row, click the pencil icon on the right
3. Type the new name → click **Rename branch**
4. GitHub will show you a snippet to update local clones:
   ```bash
   git branch -m claude/moshe-infra MOSHE_FINAL_BRANCH_NAME
   git fetch origin
   git branch -u origin/MOSHE_FINAL_BRANCH_NAME MOSHE_FINAL_BRANCH_NAME
   git remote set-head origin -a
   ```

### Option C — Local rename + remote replacement (CLI only)

```bash
# Local rename
git branch -m claude/moshe-infra MOSHE_FINAL_BRANCH_NAME

# Push new name and set upstream
git push -u origin MOSHE_FINAL_BRANCH_NAME

# Delete the old remote name
git push origin --delete claude/moshe-infra

# If a PR is already open against the old name, GitHub auto-redirects
# to the renamed branch — no PR action needed.
```

---

## 6. Known things this batch does NOT do (out of scope)

- The merge-conflict markers inside Javadoc comments of `AuthenticationService.java` (lines 140/149/222/230/312/327) are pre-existing — not my mess to clean up here. Flag separately if you want them removed.
- The `Conversation`, `Notification`, `Ticket` (full lifecycle), and `Admin` test classes are mostly `@Disabled` skeletons. Their `extends BaseDomainTest` is in place so when they get filled in, invariant checks come for free.
- `ActiveOrder` uses `String` as the `IRepository` lock-id type. Callers that want to lock an active order by userId or sessionId need to namespace the key (`"user:" + userId` / `"sess:" + sessionId`). No callers do this yet — it's just a seam.
- No real callers use `lockForUpdate`/`unlock` yet. The infrastructure is in place; wiring it into the hot reservation/cancellation paths is a follow-up.
