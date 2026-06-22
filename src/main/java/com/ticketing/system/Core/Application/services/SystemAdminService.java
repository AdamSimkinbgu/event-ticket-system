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
import com.ticketing.system.Core.Domain.exceptions.MissingDefaultAdminException;
import com.ticketing.system.Core.Domain.exceptions.UnauthorizedActionException;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;

import lombok.extern.slf4j.Slf4j;
// Owns platform-bootstrap, market-lifecycle, and global admin queries.
// UC-1 (Initialize), UC-31 (Global History), UC-32 (Open/Close Market).
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    // UC-1 — I.1.1 invariants + I.1.2 payment-gateway check + I.1.3 issuer check + I.1.4 default-admin.
    // Brings the platform to a healthy, market-openable state. Idempotent: a second call on an
    // already-initialized platform is a graceful no-op.
    public synchronized void initializePlatform() {
        log.info("Platform initialization requested.");

        // Re-initializing an already-initialized platform is handled gracefully.
        if (status != PlatformStatus.UNINITIALIZED) {
            log.info("Platform already initialized (status={}); ignoring re-initialization.", status);
            return;
        }

        // I.1.2 — at least one payment service must be reachable.
        if (paymentGateways.stream().noneMatch(this::isReachable)) {
            throw new ExternalServiceUnavailableException("no reachable payment service");
        }

        // I.1.3 — at least one ticket-issuance service must be reachable.
        if (ticketIssuers.stream().noneMatch(this::isReachable)) {
            throw new ExternalServiceUnavailableException("no reachable ticket issuance service");
        }

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

    // UC-1 / I.1.4 — auto-create the default admin if none exists.
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

    // UC-32 — open the trading market after re-verifying gateways/issuers/admin.
    public MarketStateDTO openMarket(MarketControlRequestDTO request) {
        throw new UnsupportedOperationException("UC-32: not implemented");
    }

    // (No closeMarket UC defined; defensive method for ops/incident response.)
    public MarketStateDTO closeMarket(MarketControlRequestDTO request) {
        throw new UnsupportedOperationException("not implemented (no UC defined; admin/ops use)");
    }

    // Health snapshot for admin dashboards.
    public MarketStateDTO viewMarketState() {
        throw new UnsupportedOperationException("not implemented");
    }




    // *HELPER* — UC-1 step 4 / I.1.1: re-assert the platform post-conditions as a single gate.
    // Defensive against an external service dropping between the initial check and this point.
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

    // *HELPER METHODS* — UC-1 I.1.2/I.1.3 reachability probe (maps to the WSEP `handshake`).
    // A thrown verification (e.g. a real HTTP adapter timing out) is treated as "unreachable"
    // so a flaky provider can never crash bootstrap — it just doesn't count toward the quorum.
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




    // UC-31 — global purchase history with filters (admin-only RBAC enforced inside).
    // this function filters by buyer, production company, or specific event, and by date range. All filters are optional and can be combined.
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




    // *HELPER METHODS* for viewGlobalHistory() that normalize and validate the filters, and enforce admin RBAC.
    // this method enforces that the event filter is consistent with the company filter (if provided), and that the date range is valid.
    // It also logs the filters being applied for audit purposes.
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


    // *HELPER METHOD* to convert the list of event IDs in the filters to a set for efficient lookup later. Returns null if no event filter is applied.
    private Set<Integer> selectedEventIdsOrNull(GlobalHistoryFiltersDTO filters) {
        if (filters.eventIds() == null) {
            return null;
        }
        // Convert to set for efficient lookup later.
        return filters.eventIds().stream().collect(Collectors.toSet());
    }





    // *HELPER METHOD* to enforce that the requester is a system admin. Throws if not.
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
