package com.ticketing.system.Core.Application.services;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.ticketing.system.Core.Application.dto.GlobalHistoryFiltersDTO;
import com.ticketing.system.Core.Application.dto.MarketControlRequestDTO;
import com.ticketing.system.Core.Application.dto.MarketStateDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Application.dtoMappers.OrderReceiptMapper;
import com.ticketing.system.Core.Application.interfaces.IPasswordHasher;
import com.ticketing.system.Core.Application.interfaces.IPaymentGateway;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.interfaces.ITicketIssuer;
import com.ticketing.system.Core.Domain.Admin.Admin;
import com.ticketing.system.Core.Domain.Admin.IAdminRepository;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.exceptions.ExternalServiceUnavailableException;
import com.ticketing.system.Core.Domain.exceptions.InitializationIntegrityException;
import com.ticketing.system.Core.Domain.exceptions.InvalidStateTransitionException;
import com.ticketing.system.Core.Domain.exceptions.MarketNotOpenException;
import com.ticketing.system.Core.Domain.exceptions.MissingDefaultAdminException;
import com.ticketing.system.Core.Domain.exceptions.UnauthorizedActionException;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns platform bootstrap, market lifecycle, and global admin queries.
 * UC-1 (Initialize), UC-31 (Global History), UC-32 (Open/Close Market).
 *
 * <p>Holds the platform lifecycle state machine
 * (UNINITIALIZED → READY → OPEN ↔ CLOSED) in memory for V1; sales are gated on
 * the OPEN state via {@link #isMarketOpen()}. Admin-only operations enforce RBAC
 * internally through {@code requireSystemAdmin}.
 */
@Service
@Slf4j
public class SystemAdminService {

    private final ISessionManager sessionManager;
    private final IAdminRepository adminRepository;
    private final IOrderReceiptRepository orderReceiptRepository;
    private final ITicketRepository ticketRepository;
    private final IEventRepository eventRepository;
    private final IProductionCompanyRepository companyRepository;
    private final IUserRepository userRepository;
    private final List<IPaymentGateway> paymentGateways;
    private final List<ITicketIssuer> ticketIssuers;
    private final IPasswordHasher passwordHasher;
    private final SystemIntegrityVerifier integrityVerifier;

    // UC-1 / I.1.4 — default System Admin credentials. Bound from platform.admin.* in
    // application.yml (env-overridable); never hardcoded here.
    private final String defaultAdminUsername;
    private final String defaultAdminPassword;
    private static final int DEFAULT_ADMIN_ID = 1;

    // Platform lifecycle state (in-memory for V1). openMarket()/closeMarket() (UC-32, #307)
    // build on top of this status.
    private enum PlatformStatus { UNINITIALIZED, READY, OPEN, CLOSED }
    private volatile PlatformStatus status = PlatformStatus.UNINITIALIZED;
    private volatile LocalDateTime lastInitializedAt;
    private volatile LocalDateTime lastOpenedAt;

    public SystemAdminService(
            ISessionManager sessionManager,
            IAdminRepository adminRepository,
            IOrderReceiptRepository orderReceiptRepository,
            ITicketRepository ticketRepository,
            IEventRepository eventRepository,
            IProductionCompanyRepository companyRepository,
            IUserRepository userRepository,
            List<IPaymentGateway> paymentGateways,
            List<ITicketIssuer> ticketIssuers,
            IPasswordHasher passwordHasher,
            SystemIntegrityVerifier integrityVerifier,
            @Value("${platform.admin.username}") String defaultAdminUsername,
            @Value("${platform.admin.password}") String defaultAdminPassword
    ) {
        this.sessionManager = sessionManager;
        this.adminRepository = adminRepository;
        this.orderReceiptRepository = orderReceiptRepository;
        this.ticketRepository = ticketRepository;
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.paymentGateways = paymentGateways;
        this.ticketIssuers = ticketIssuers;
        this.passwordHasher = passwordHasher;
        this.integrityVerifier = integrityVerifier;
        this.defaultAdminUsername = defaultAdminUsername;
        this.defaultAdminPassword = defaultAdminPassword;
    }

    /**
     * UC-1 — runs the I.1.1 invariants, the I.1.2 payment-gateway check, the
     * I.1.3 issuer check and the I.1.4 default-admin guarantee, bringing the
     * platform to a healthy, market-openable READY state.
     *
     * <p>Idempotent: a second call on an already-initialized platform is a
     * graceful no-op.
     *
     * @throws ExternalServiceUnavailableException  if no payment or issuance service is reachable
     * @throws MissingDefaultAdminException         if the default admin cannot be created/persisted
     * @throws InitializationIntegrityException     if a post-condition gate fails before going live
     */
    public synchronized void initializePlatform() {
        log.info("Platform initialization requested.");

        // Re-initializing an already-initialized platform is handled gracefully.
        if (status != PlatformStatus.UNINITIALIZED) {
            log.info("Platform already initialized (status={}); ignoring re-initialization.", status);
            return;
        }

        // I.1.2 / I.1.3 — at least one payment service and one ticket-issuance service reachable.
        requireExternalServicesReachable();

        // I.1.4 — guarantee at least one System Admin (auto-create a default if none).
        createDefaultAdminIfMissing();

        // Step 4 / I.1.1 — re-assert the platform post-conditions, then the system-wide
        // structural correctness constraints, as explicit gates before going live.
        verifyInitializationInvariants();
        integrityVerifier.verify();

        this.lastInitializedAt = LocalDateTime.now();
        this.status = PlatformStatus.READY;
        log.info("Platform initialized — status READY.");
    }

    /**
     * UC-1 / I.1.4 — auto-creates the default System Admin if none exists, then
     * confirms the write took (a silent persistence failure must fail init). The
     * credentials are bound from {@code platform.admin.*} and should be rotated
     * after first login.
     *
     * @throws MissingDefaultAdminException if the default admin cannot be created
     *                                      or was not persisted
     */
    public void createDefaultAdminIfMissing() {
        if (adminRepository.existsAny()) {
            log.info("A System Admin already exists; no default needed.");
            return;
        }

        log.warn("No System Admin found — creating default admin '{}'. Override its password via "
                + "PLATFORM_ADMIN_PASSWORD and rotate after first login.", defaultAdminUsername);
        try {
            Admin defaultAdmin = new Admin(
                    DEFAULT_ADMIN_ID,
                    defaultAdminUsername,
                    passwordHasher.hash(defaultAdminPassword),
                    true);
            defaultAdmin.checkInvariants();
            adminRepository.save(defaultAdmin);
        } catch (RuntimeException e) {
            throw new MissingDefaultAdminException(e.getMessage());
        }

        // Confirm the write actually took — a silent persistence failure must fail init.
        if (!adminRepository.existsAny()) {
            throw new MissingDefaultAdminException("default admin was not persisted");
        }
    }

    /**
     * UC-32 (#9, I.2.1) — opens the trading market so transactions can begin.
     * Admin-only. Re-verifies both external services (I.2.2) and the structural
     * invariants before flipping to OPEN. Opens from READY or re-opens from
     * CLOSED, never from UNINITIALIZED; idempotent on an already-open market.
     * Sales are gated on this state (see {@link #isMarketOpen()}).
     *
     * @param request the admin control request carrying the auth token
     * @return the resulting market state snapshot
     * @throws UnauthorizedActionException         if the token is not a valid admin token
     * @throws MarketNotOpenException              if the platform has not been initialized
     * @throws ExternalServiceUnavailableException if a required external service is down
     * @throws InitializationIntegrityException    if no admin is present at open time
     */
    public synchronized MarketStateDTO openMarket(MarketControlRequestDTO request) {
        requireSystemAdmin(tokenOf(request));

        if (status == PlatformStatus.OPEN) {
            log.info("Market already open; ignoring open request.");
            return buildMarketState();
        }
        if (status == PlatformStatus.UNINITIALIZED) {
            throw new MarketNotOpenException("platform not initialized");
        }

        // I.2.2 — re-verify both external services at open time; don't flip state if either is down.
        requireExternalServicesReachable();

        // I.2.1 — re-assert structural invariants (>=1 admin + system-wide integrity) before going live.
        if (!adminRepository.existsAny()) {
            throw new InitializationIntegrityException("no System Admin present");
        }
        integrityVerifier.verify();

        this.status = PlatformStatus.OPEN;
        this.lastOpenedAt = LocalDateTime.now();
        log.info("Market opened.");
        return buildMarketState();
    }

    /**
     * UC-32 — closes the trading market (admin/ops incident control; no dedicated
     * UC). Admin-only. Idempotent on an already-closed market; rejects a close
     * when the market was never opened.
     *
     * @param request the admin control request carrying the auth token and an
     *                optional reason
     * @return the resulting market state snapshot
     * @throws UnauthorizedActionException     if the token is not a valid admin token
     * @throws InvalidStateTransitionException if the market is not currently open
     */
    public synchronized MarketStateDTO closeMarket(MarketControlRequestDTO request) {
        requireSystemAdmin(tokenOf(request));

        if (status == PlatformStatus.CLOSED) {
            log.info("Market already closed; ignoring close request.");
            return buildMarketState();
        }
        if (status != PlatformStatus.OPEN) {
            throw new InvalidStateTransitionException("market is not open; cannot close");
        }

        this.status = PlatformStatus.CLOSED;
        log.info("Market closed (reason: {}).", reasonOrDefault(request));
        return buildMarketState();
    }

    /**
     * Read-only market/health snapshot for admin dashboards. Takes no token — the
     * admin route is already access-gated and this exposes no sensitive data.
     *
     * @return the current market state snapshot
     */
    public MarketStateDTO viewMarketState() {
        return buildMarketState();
    }

    /**
     * Read boundary for the private status enum: the sales gate
     * (ReservationService / CheckoutService) blocks transactions while the market
     * is not OPEN. (I.2.1 / UC-32)
     *
     * @return {@code true} if the market is currently OPEN
     */
    public boolean isMarketOpen() {
        return status == PlatformStatus.OPEN;
    }

    /**
     * Builds the market snapshot, probing external-service health so it lives in
     * one place.
     *
     * @return a snapshot of status, timestamps, and service/admin health
     */
    private MarketStateDTO buildMarketState() {
        boolean paymentHealthy = paymentGateways.stream().anyMatch(this::isReachable);
        boolean issuerHealthy = ticketIssuers.stream().anyMatch(this::isReachable);
        boolean adminPresent = adminRepository.existsAny();
        return new MarketStateDTO(
                status.name(),
                lastInitializedAt,
                lastOpenedAt,
                paymentHealthy,
                issuerHealthy,
                adminPresent);
    }

    /**
     * Null-safe accessor for the optional control request's token.
     *
     * @param request the control request (may be null)
     * @return the token, or {@code null} if the request is null
     */
    private static String tokenOf(MarketControlRequestDTO request) {
        return request == null ? null : request.token();
    }

    /**
     * Null-safe accessor for the optional control request's reason.
     *
     * @param request the control request (may be null)
     * @return the reason, or "unspecified" if absent/blank
     */
    private static String reasonOrDefault(MarketControlRequestDTO request) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            return "unspecified";
        }
        return request.reason();
    }




    /**
     * I.1.2 / I.1.3 &amp; I.2.2 external-service quorum: at least one payment
     * gateway and one ticket issuer must be reachable. Shared by initialize and
     * openMarket so the "service down" failure is identical in both paths.
     *
     * @throws ExternalServiceUnavailableException if no payment gateway or no
     *                                             ticket issuer is reachable
     */
    private void requireExternalServicesReachable() {
        if (paymentGateways.stream().noneMatch(this::isReachable)) {
            throw new ExternalServiceUnavailableException("no reachable payment service");
        }
        if (ticketIssuers.stream().noneMatch(this::isReachable)) {
            throw new ExternalServiceUnavailableException("no reachable ticket issuance service");
        }
    }

    /**
     * UC-1 step 4 / I.1.1 — re-asserts the platform post-conditions as a single
     * gate, defending against an external service dropping between the initial
     * check and this point.
     *
     * @throws InitializationIntegrityException if a payment/issuer service is
     *                                          unreachable or no admin is present
     */
    private void verifyInitializationInvariants() {
        if (paymentGateways.stream().noneMatch(this::isReachable)) {
            throw new InitializationIntegrityException("no reachable payment service");
        }
        if (ticketIssuers.stream().noneMatch(this::isReachable)) {
            throw new InitializationIntegrityException("no reachable ticket issuance service");
        }
        if (!adminRepository.existsAny()) {
            throw new InitializationIntegrityException("no System Admin present");
        }
    }

    /**
     * UC-1 I.1.2 reachability probe for a payment gateway (maps to the WSEP
     * {@code handshake}). A thrown verification (e.g. a real HTTP adapter timing
     * out) is treated as "unreachable" so a flaky provider can never crash
     * bootstrap — it just doesn't count toward the quorum.
     *
     * @param gateway the payment gateway to probe
     * @return {@code true} if the gateway is reachable and authenticated
     */
    private boolean isReachable(IPaymentGateway gateway) {
        try {
            boolean ok = gateway.verifyConnection();
            if (!ok) {
                log.warn("Payment gateway '{}' reported unreachable.", gateway.getId());
            }
            return ok;
        } catch (RuntimeException e) {
            log.warn("Payment gateway '{}' verification failed: {}", gateway.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * UC-1 I.1.3 reachability probe for a ticket issuer (maps to the WSEP
     * {@code handshake}). A thrown verification is treated as "unreachable" so a
     * flaky provider can never crash bootstrap.
     *
     * @param issuer the ticket issuer to probe
     * @return {@code true} if the issuer is reachable and authenticated
     */
    private boolean isReachable(ITicketIssuer issuer) {
        try {
            boolean ok = issuer.verifyConnection();
            if (!ok) {
                log.warn("Ticket issuer '{}' reported unreachable.", issuer.getId());
            }
            return ok;
        } catch (RuntimeException e) {
            log.warn("Ticket issuer '{}' verification failed: {}", issuer.getId(), e.getMessage());
            return false;
        }
    }




    /**
     * UC-31 — global purchase history with filters (admin-only; RBAC enforced
     * inside). Filters by buyer, production company, or specific event, and by
     * date range; all filters are optional and can be combined. When a company
     * filter is supplied it is translated into a consistent event-id filter and
     * each receipt is trimmed to the matching tickets.
     *
     * @param token   the requester's auth token (must be a System Admin)
     * @param filters the optional buyer/company/event/date filters
     * @return a singleton list holding the assembled purchase history
     * @throws UnauthorizedActionException if the token is not a valid admin token
     * @throws IllegalArgumentException    if {@code fromDate} is after {@code toDate}
     */
    @Transactional(readOnly = true)
    public List<PurchaseHistoryDTO> viewGlobalHistory(String token, GlobalHistoryFiltersDTO filters) {
        log.info("Admin request to view global purchase history with filters: {}", filters);
        requireSystemAdmin(token);

        // If companyId is provided, we ensure that the eventIds filter (if provided) is a subset of the events for that company, in the normalizeGlobalHistoryFilters() method.
        GlobalHistoryFiltersDTO effectiveFilters = normalizeGlobalHistoryFilters(filters);
        Set<Integer> selectedEventIds = selectedEventIdsOrNull(effectiveFilters);

        OrderReceiptMapper mapper = new OrderReceiptMapper();

        List<PurchaseHistoryDTO.PurchaseRecordDTO> records = orderReceiptRepository.findGlobal(effectiveFilters)
                .stream()
                .map(receipt -> {
                    if (selectedEventIds == null) {
                        return mapper.toPurchaseRecordDTO(
                                receipt, ticketRepository, eventRepository, companyRepository, userRepository);
                    }

                    return mapper.toFilteredPurchaseRecordDTO(
                            receipt, selectedEventIds, ticketRepository, eventRepository, companyRepository, userRepository);
                })
                .filter(record -> !record.tickets().isEmpty())
                .toList();

        log.info("Found {} records for admin global purchase history with filters: {}", records.size(), effectiveFilters);
        return List.of(new PurchaseHistoryDTO(records));
    }




    /**
     * Helper for {@link #viewGlobalHistory} that normalizes and validates the
     * filters. Validates the date range, and — when a company filter is given —
     * translates it into an event-id filter intersected with the company's events
     * (out-of-company event ids are silently dropped, not rejected).
     *
     * @param filters the raw filters (may be null)
     * @return an equivalent, company-consistent filter set
     * @throws IllegalArgumentException if {@code fromDate} is after {@code toDate}
     */
    private GlobalHistoryFiltersDTO normalizeGlobalHistoryFilters(GlobalHistoryFiltersDTO filters) {
        // If no filters are provided, return a default filter that matches all receipts.
        GlobalHistoryFiltersDTO f = filters == null
                ? new GlobalHistoryFiltersDTO(null, null, null, null, null)
                : filters;
        // Validate that fromDate is not after toDate if both are provided.
        if (f.fromDate() != null && f.toDate() != null && f.fromDate().isAfter(f.toDate())) {
            throw new IllegalArgumentException("fromDate must be before or equal to toDate");
        }

        // If companyId is provided but eventIds is not, we need to fetch the event IDs for that company and use them as the effective filter.
        if (f.companyId() == null) {
            return new GlobalHistoryFiltersDTO(
                    f.buyerUserId(),
                    null,
                    f.eventIds(),
                    f.fromDate(),
                    f.toDate());
        }

        // If companyId is provided, we need to ensure that the eventIds filter (if provided) is a subset of the events for that company.
        // If eventIds is null, we will use all events for that company.
        List<Integer> companyEventIds = eventRepository.findIdsByCompany(f.companyId());

        List<Integer> effectiveEventIds;

        if (f.eventIds() == null) {
            // No event filter provided, use all events for the company.
            effectiveEventIds = companyEventIds;
        } else {
            // Event filter provided: intersect the requested event IDs with the company's events.
            // (No exception is thrown; out-of-company eventIds are silently dropped.)
            Set<Integer> requestedSet = new HashSet<>(f.eventIds());
            // Retain only the event IDs that are both in the requested set and in the company's events.(Intersection of the two sets)
            effectiveEventIds = companyEventIds.stream()
                    .filter(requestedSet::contains)
                    .toList();
        }
        // now the company filter is effectively translated into an event filter that is guaranteed to be consistent with the company constraint, and we can proceed with the query.
        return new GlobalHistoryFiltersDTO(
                f.buyerUserId(),
                null,
                effectiveEventIds,
                f.fromDate(),
                f.toDate());
    }


    /**
     * Converts the filter's event-id list to a set for efficient lookup during
     * record mapping.
     *
     * @param filters the effective filters
     * @return a set of selected event ids, or {@code null} if no event filter is
     *         applied (meaning "all events")
     */
    private Set<Integer> selectedEventIdsOrNull(GlobalHistoryFiltersDTO filters) {
        if (filters.eventIds() == null) {
            return null;
        }
        // Convert to set for efficient lookup later.
        return filters.eventIds().stream().collect(Collectors.toSet());
    }





    /**
     * Enforces that the requester is a System Admin. The token must be valid,
     * carry the ADMIN role (without this, a member whose id happens to equal an
     * admin's id would pass the repository lookup), and resolve to an existing
     * admin.
     *
     * @param token the auth token to check
     * @throws UnauthorizedActionException if the token is invalid, not an admin
     *                                     token, or does not resolve to an admin
     */
    private void requireSystemAdmin(String token) {
        if (!sessionManager.validateToken(token)) {
            log.warn("Unauthorized access attempt with id: {}", sessionManager.extractUserId(token));
            throw new UnauthorizedActionException("Invalid or non-admin token.");
        }

        // The token must carry the ADMIN role. Without this, a member whose id happens to equal an
        // admin's id (both pools start at 1) would pass the adminRepository lookup below.
        if (!sessionManager.isAdminToken(token)) {
            log.warn("Unauthorized access attempt (non-admin token) with id: {}", sessionManager.extractUserId(token));
            throw new UnauthorizedActionException("Invalid or non-admin token.");
        }

        int userId = sessionManager.extractUserId(token);
        if (adminRepository.findById(userId) == null) {
            log.warn("Unauthorized access attempt with id: {}", userId);
            throw new UnauthorizedActionException("Invalid or non-admin token.");
        }
    }

}
