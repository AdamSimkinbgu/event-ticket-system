package com.ticketing.system.Core.Application.services;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.ticketing.system.Core.Application.dto.GlobalHistoryFiltersDTO;
import com.ticketing.system.Core.Application.dto.MarketControlRequestDTO;
import com.ticketing.system.Core.Application.dto.MarketStateDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Application.dtoMappers.OrderReceiptMapper;
import com.ticketing.system.Core.Application.interfaces.IPaymentGateway;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.interfaces.ITicketIssuer;
import com.ticketing.system.Core.Domain.Admin.IAdminRepository;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.exceptions.UnauthorizedActionException;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;

import lombok.extern.slf4j.Slf4j;
// Owns platform-bootstrap, market-lifecycle, and global admin queries.
// UC-1 (Initialize), UC-31 (Global History), UC-32 (Open/Close Market).
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SystemAdminService {

    private final ISessionManager sessionManager;
    private final IAdminRepository adminRepository;
    private final IOrderReceiptRepository orderReceiptRepository;
    private final ITicketRepository ticketRepository;
    private final IEventRepository eventRepository;
    private final List<IPaymentGateway> paymentGateways;
    private final List<ITicketIssuer> ticketIssuers;

    public SystemAdminService(
            ISessionManager sessionManager,
            IAdminRepository adminRepository,
            IOrderReceiptRepository orderReceiptRepository,
            ITicketRepository ticketRepository,
            IEventRepository eventRepository,
            List<IPaymentGateway> paymentGateways,
            List<ITicketIssuer> ticketIssuers
    ) {
        this.sessionManager = sessionManager;
        this.adminRepository = adminRepository;
        this.orderReceiptRepository = orderReceiptRepository;
        this.ticketRepository = ticketRepository;
        this.eventRepository = eventRepository;
        this.paymentGateways = paymentGateways;
        this.ticketIssuers = ticketIssuers;
    }

    // UC-1 — invariants + I.1.2 gateway check + I.1.3 issuer check + I.1.4 default-admin.
    public void initializePlatform() {
        throw new UnsupportedOperationException("UC-1: not implemented");
    }

    // UC-1 / I.1.4 — auto-create the default admin if none exists.
    public void createDefaultAdminIfMissing() {
        throw new UnsupportedOperationException("UC-1 / I.1.4: not implemented");
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
                        return mapper.toPurchaseRecordDTO(receipt, ticketRepository);
                    }

                    return mapper.toFilteredPurchaseRecordDTO(receipt, selectedEventIds, ticketRepository);
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
            // Event filter provided, ensure it is a subset of the company's events. If not, throw an exception.
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

        int userId = sessionManager.extractUserId(token);
        if (adminRepository.findById(userId) == null) {
            log.warn("Unauthorized access attempt with id: {}", userId);
            throw new UnauthorizedActionException("Invalid or non-admin token.");
        }
    }

}
