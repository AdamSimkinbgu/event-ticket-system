package com.ticketing.system.Core.Application.services;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.system.Core.Application.dto.OrganizationalTreeNodeDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Application.dtoMappers.OrderReceiptMapper;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Domain.company.CompanyStatus;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;
import com.ticketing.system.Core.Domain.users.CompanyRole;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.ManagementInvitation;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.Core.Domain.users.User;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Application.dto.CompanyRegistrationDTO;
import com.ticketing.system.Core.Application.dto.OwnerAppointmentRequestDTO;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class CompanyManagementService {
    private final IProductionCompanyRepository companyRepository;
    private final IUserRepository userRepository;
    private final IOrderReceiptRepository orderReceiptRepository;
    private final ISessionManager sessionManager;
    private final ITicketRepository ticketRepository;
    private final IEventRepository eventRepository;
    private final Logger logger = LoggerFactory.getLogger(CompanyManagementService.class);
    private static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
    private static final String PHONE_PATTERN = "^\\+?[0-9\\-\\s]{9,15}$";

    public CompanyManagementService(IProductionCompanyRepository companyRepository, IUserRepository userRepository,
            IOrderReceiptRepository orderReceiptRepository, ISessionManager sessionManager,
            ITicketRepository ticketRepository, IEventRepository eventRepository) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.orderReceiptRepository = orderReceiptRepository;
        this.sessionManager = sessionManager;
        this.ticketRepository = ticketRepository;
        this.eventRepository = eventRepository;
    }

    public void inviteManager(String token, int companyId, int targetId, List<Permission> permissions) {
        if (!sessionManager.validateToken(token)) {
            logger.warn("Invalid token provided for inviting manager");
            throw new RuntimeException("Invalid token");
        }
        int ownerId = sessionManager.extractUserId(token);
        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            logger.warn("Company {} not found", companyId);
            throw new RuntimeException("Company not found");
        }
        company.checkowner(ownerId);
        User targetUser = userRepository.getUserById(targetId);
        if (targetUser == null) {
            logger.warn("Target user {} not found", targetId);
            throw new RuntimeException("Target user not found");
        }

        company.validateManagerInvitation(companyId, targetId, ownerId, permissions);

        targetUser.InvitetoCompanyAppointment(companyId, ownerId, permissions);

        companyRepository.updateCompany(company);
        userRepository.updateUser(targetUser);

        logger.info("user invited succesfully");

    }

    public void acceptManagerInvitation(String token, int companyId) {
        if (!sessionManager.validateToken(token)) {
            logger.warn("Invalid token provided for accepting manager invitation");
            throw new RuntimeException("Invalid token");
        }
        int targetId = sessionManager.extractUserId(token);
        User targetUser = userRepository.getUserById(targetId);
        if (targetUser == null) {
            logger.warn("Target user {} not found", targetId);
            throw new RuntimeException("Target user not found");
        }

        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            logger.warn("Company {} not found", companyId);
            throw new RuntimeException("Company not found");
        }

        ManagementInvitation invitation = targetUser.acceptInvitation(companyId);
        company.acceptManagerInvitation(targetId);
        userRepository.updateUser(targetUser);
        companyRepository.updateCompany(company);
        logger.info("Manager invitation accepted successfully");
    }

    public void rejectManagerInvitation(String token, int companyId) {
        if (!sessionManager.validateToken(token)) {
            logger.warn("Invalid token provided for rejecting manager invitation");
            throw new RuntimeException("Invalid token");
        }
        int targetId = sessionManager.extractUserId(token);
        User targetUser = userRepository.getUserById(targetId);
        if (targetUser == null) {
            logger.warn("Target user {} not found", targetId);
            throw new RuntimeException("Target user not found");
        }

        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            logger.warn("Company {} not found", companyId);
            throw new RuntimeException("Company not found");
        }

        targetUser.rejectInvitation(companyId);
        company.rejectManagerInvitation(targetId);
        userRepository.updateUser(targetUser);
        companyRepository.updateCompany(company);
        logger.info("Manager invitation rejected successfully");
    }

    public void RevokeManager(String token, int companyId, int targetId) {
        if (!sessionManager.validateToken(token)) {
            logger.warn("Invalid token provided for revoking manager");
            throw new RuntimeException("Invalid token");
        }
        int ownerId = sessionManager.extractUserId(token);
        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            logger.warn("Company {} not found", companyId);
            throw new RuntimeException("Company not found");
        }
        company.checkowner(ownerId);

        User targetUser = userRepository.getUserById(targetId);
        if (targetUser == null) {
            logger.warn("Target user {} not found", targetId);
            throw new RuntimeException("Target user not found");
        }

        company.RevokeManager(targetId);
        targetUser.removeCompanyAppointment(companyId);

        userRepository.updateUser(targetUser);
        companyRepository.updateCompany(company);
        logger.info("Manager revoked successfully");

    }

    public void ModifyManagerPermissions(String token, int companyId, int targetId, List<Permission> newPermissions) {
        if (!sessionManager.validateToken(token)) {
            logger.warn("Invalid token provided for modifying manager permissions");
            throw new RuntimeException("Invalid token");
        }
        int ownerId = sessionManager.extractUserId(token);
        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            logger.warn("Company {} not found", companyId);
            throw new RuntimeException("Company not found");
        }
        company.checkowner(ownerId);
        User targetUser = userRepository.getUserById(targetId);
        if (targetUser == null) {
            logger.warn("Target user {} not found", targetId);
            throw new RuntimeException("Target user not found");
        }

        company.ModifyManagerPermissions(companyId, targetId, newPermissions);
        targetUser.ModifyManagerPermissions(companyId, targetId, newPermissions);

        userRepository.updateUser(targetUser);
        companyRepository.updateCompany(company);

        logger.info("Manager permissions modified successfully");
    }

    // ---------------------------------------------------------------------------
    // DTO-typed methods added in skeleton round (parallel to the existing
    // token-arg / List<Permission>-arg methods above; team to consolidate later).
    // ---------------------------------------------------------------------------

    // UC-18 — register a new Production Company; appoints Founder/Owner in same
    // transaction.
    public ProductionCompanyDTO registerCompany(String token, CompanyRegistrationDTO request) {
        if (!sessionManager.validateToken(token)) {
            logger.warn("Invalid token provided for registering a company");
            throw new RuntimeException("Invalid token");
        }
        int userId = sessionManager.extractUserId(token);

        // CompanyRegistrationDTO is a class with get* accessors, not a record.
        if (request.getName() == null || request.getName().trim().isEmpty() ||
                request.getDescription() == null || request.getDescription().trim().isEmpty()) {

            logger.warn("Company registration failed: Missing required fields by user {}", userId);
            throw new IllegalArgumentException("All company fields (name, description) must be provided");
        }

        // Call on the injected instance, not the interface.
        if (companyRepository.existsByName(request.getName().trim())) {
            logger.warn("Company registration failed: Company name '{}' already exists", request.getName());
            throw new IllegalStateException("A company with this name already exists");
        }

        try {
            int companyId = companyRepository.nextId();
            ProductionCompany newProductionCompany = new ProductionCompany(
                    companyId,
                    userId,
                    request.getName().trim(),
                    CompanyStatus.ACTIVE,
                    request.getDescription().trim(),
                    null);

            // IProductionCompanyRepository.save returns void; the new instance IS the saved
            // one.
            companyRepository.save(newProductionCompany);
            logger.info("Successfully registered new company: '{}' by userId: {}", newProductionCompany.getName(),
                    userId);

            return new ProductionCompanyDTO(
                    newProductionCompany.getCompanyId(),
                    newProductionCompany.getName(),
                    newProductionCompany.getDescription(),
                    newProductionCompany.getStatus().name(), // DTO field is String
                    newProductionCompany.getFounderId() // DTO field is founderId
            );

        } catch (Exception e) {
            logger.error("Error occurred while saving company '{}': {}", request.getName(), e.getMessage());
            throw new RuntimeException("Failed to register company due to a server error", e);
        }
    }

    // UC-23 — Owner appoints another Member as co-Owner (PENDING).
    public void RequestUserToBecomeOwner(String token, OwnerAppointmentRequestDTO request) {
        if (!sessionManager.validateToken(token)) {
            logger.warn("Invalid token provided for appointing another Member as co-Owner");
            throw new RuntimeException("Invalid token");
        }

        ProductionCompany company = companyRepository.getCompanyById(request.getCompanyId());
        if (company == null) {
            logger.warn("Company {} not found", request.getCompanyId());
            throw new RuntimeException("Company not found");
        }

        if (company.getOwnerId() != sessionManager.extractUserId(token)) {
            logger.warn("User {} is not an owner of company {}", sessionManager.extractUserId(token),
                    request.getCompanyId());
            throw new RuntimeException("User is not an owner of company");
        }

        User targetUser = userRepository.getUserById(request.getTargetUserId());
        if (targetUser == null) {
            logger.warn("User {} not found", request.getTargetUserId());
            throw new RuntimeException("User not found");
        }

        if (company.canAppoint(sessionManager.extractUserId(token), request.getTargetUserId())) {
            userRepository.sendOwnerInvitation(request.getTargetUserId(), request.getCompanyId());
            logger.info("Owner appointment request sended to user successfully");
        } else {
            logger.warn("Owner appointment request failed. the user " + request.getTargetUserId()
                    + " is already an owner or user " + sessionManager.extractUserId(token) + " is not an owner");
            throw new RuntimeException("Cannot appoint owner");
        }

    }

    // UC-23 / UC-24 — target accepts or rejects a pending appointment.
    public void respondToAppointment(
            String token,
            com.ticketing.system.Core.Application.dto.AppointmentResponseDTO response) {
        throw new UnsupportedOperationException("UC-23 / UC-24: not implemented");
    }

    // UC-24 — Owner appoints a Manager with explicit granular permissions.
    public void appointManager(
            String token,
            com.ticketing.system.Core.Application.dto.ManagerAppointmentRequestDTO request) {
        throw new UnsupportedOperationException("UC-24: not implemented");
    }

    // UC-24 — edit a Manager's permission set (only by the original appointer).
    public void editManagerPermissions(
            String token,
            com.ticketing.system.Core.Application.dto.PermissionEditDTO edit) {
        throw new UnsupportedOperationException("UC-24: not implemented");
    }

    // UC-21 — set / replace company-wide default policies.
    public void setCompanyPolicies(
            String token,
            com.ticketing.system.Core.Application.dto.CompanyPolicyConfigDTO config) {
        throw new UnsupportedOperationException("UC-21: not implemented");
    }

    // UC-22 — Owner-side flat list of company sales.
    public List<PurchaseHistoryDTO> viewSalesHistory(String token, int companyId) {
        this.logger.info("Attempting to view sales history for company {}", companyId);

        if (!sessionManager.validateToken(token)) {
            logger.warn("Invalid token provided for viewing sales history");
            throw new InvalidTokenException("Invalid token");
        }

        int requesterId = sessionManager.extractUserId(token);
        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            logger.warn("Company {} not found", companyId);
            throw new RuntimeException("Company not found");
        }

        User currUser = userRepository.getUserById(requesterId);
        if (currUser == null) {
            logger.warn("User {} not found", requesterId);
            throw new RuntimeException("User not found");
        }
        if (!currUser.isOwnerInCompany(companyId)
                && !currUser.hasPermissionInCompany(companyId, Permission.VIEW_SALES)) {
            logger.warn("User {} does not have permission to view sales history for company {}", requesterId,
                    companyId);
            throw new RuntimeException("Insufficient permissions");
        }

        List<PurchaseHistoryDTO> salesHistory = this.orderReceiptRepository.findByCompanyId(companyId).stream()
                .map(sale -> new PurchaseHistoryDTO(
                        List.of(new OrderReceiptMapper().OrderReceiptToPurchaseRecordDTO(sale, ticketRepository,
                                eventRepository))))
                .toList();

        logger.info("Successfully retrieved sales history for company {}", companyId);
        return salesHistory;
    }

    // UC-25 — recursive organizational tree (Owners only per II.4.15).
    public OrganizationalTreeNodeDTO viewOrganizationalTree(String token, int companyId) {
        throw new UnsupportedOperationException("UC-25: not implemented");
    }

}
