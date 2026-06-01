package com.ticketing.system.Core.Application.services;

import java.util.List;

import com.ticketing.system.Core.Application.dto.GlobalHistoryFiltersDTO;
import com.ticketing.system.Core.Application.dto.MarketControlRequestDTO;
import com.ticketing.system.Core.Application.dto.MarketStateDTO;
import com.ticketing.system.Core.Application.dto.PageDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Application.dtoMappers.OrderReceiptMapper;
import com.ticketing.system.Core.Application.interfaces.IPaymentGateway;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.interfaces.ITicketIssuer;
import com.ticketing.system.Core.Domain.Admin.IAdminRepository;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
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
    public List<PurchaseHistoryDTO> viewGlobalHistory(String token, GlobalHistoryFiltersDTO filters) {
        requireSystemAdmin(token);

        log.info("Viewing global purchase history with filters: {}", filters);
        OrderReceiptMapper mapper = new OrderReceiptMapper();

        List<PurchaseHistoryDTO.PurchaseRecordDTO> records = orderReceiptRepository.findGlobal(filters)
                .stream()
                .map(receipt -> mapper.OrderReceiptToPurchaseRecordDTO(receipt, ticketRepository))
                .toList();

        log.info("Found {} records for global purchase history with filters: {}", records.size(), filters);
        return List.of(new PurchaseHistoryDTO(records));
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
