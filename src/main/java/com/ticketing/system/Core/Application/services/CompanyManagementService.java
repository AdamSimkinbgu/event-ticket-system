package com.ticketing.system.Core.Application.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

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
import com.ticketing.system.Core.Domain.users.CompanyAppointment;
import com.ticketing.system.Core.Domain.users.CompanyRole;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.ManagementInvitation;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.Core.Domain.users.User;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Application.dto.CompanyRegistrationDTO;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CompanyManagementService {
    private final IProductionCompanyRepository companyRepository;
    private final IUserRepository userRepository;
    private final IOrderReceiptRepository orderReceiptRepository;
    private final ISessionManager sessionManager;
    private final ITicketRepository ticketRepository;
    private final IEventRepository eventRepository;
    private static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
    private static final String PHONE_PATTERN = "^\\+?[0-9\\-\\s]{9,15}$";


    public CompanyManagementService(IProductionCompanyRepository companyRepository, IUserRepository userRepository, IOrderReceiptRepository orderReceiptRepository, ISessionManager sessionManager, ITicketRepository ticketRepository, IEventRepository eventRepository) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.orderReceiptRepository = orderReceiptRepository;
        this.sessionManager = sessionManager;
        this.ticketRepository = ticketRepository;
        this.eventRepository = eventRepository;
    }

    public void inviteManager(String token, int companyId, int targetId, List<Permission> permissions) {
        if (!sessionManager.validateToken(token)) {
            log.warn("Invalid token provided for inviting manager");
            throw new RuntimeException("Invalid token");
        }
        int ownerId = sessionManager.extractUserId(token);
        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            log.warn("Company {} not found", companyId);
            throw new RuntimeException("Company not found");
        }
        company.checkowner(ownerId);
        User targetUser = userRepository.getUserById(targetId);
        if (targetUser == null) {
            log.warn("Target user {} not found", targetId);
            throw new RuntimeException("Target user not found");
        }

        company.validateManagerInvitation(companyId, targetId, ownerId, permissions);
        
        targetUser.InvitetoCompanyAppointment(companyId, ownerId, permissions);

        companyRepository.updateCompany(company);
        userRepository.updateUser(targetUser);

        log.info("user invited succesfully");

    }

    public void acceptManagerInvitation(String token, int companyId) {
        if (!sessionManager.validateToken(token)) {
                log.warn("Invalid token provided for accepting manager invitation");
            throw new RuntimeException("Invalid token");
        }
        int targetId = sessionManager.extractUserId(token);
        User targetUser = userRepository.getUserById(targetId);
        if (targetUser == null) {
            log.warn("Target user {} not found", targetId);
            throw new RuntimeException("Target user not found");
        }

        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            log.warn("Company {} not found", companyId);
            throw new RuntimeException("Company not found");
        }

        targetUser.acceptInvitation(companyId);
        company.acceptManagerInvitation(targetId);
        userRepository.updateUser(targetUser);
        companyRepository.updateCompany(company);
        log.info("Manager invitation accepted successfully");
    }

    public void rejectManagerInvitation(String token, int companyId) {
        if (!sessionManager.validateToken(token)) {
            log.warn("Invalid token provided for rejecting manager invitation");
            throw new RuntimeException("Invalid token");
        }
        int targetId = sessionManager.extractUserId(token);
        User targetUser = userRepository.getUserById(targetId);
        if (targetUser == null) {
            log.warn("Target user {} not found", targetId);
            throw new RuntimeException("Target user not found");
        }

        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            log.warn("Company {} not found", companyId);
            throw new RuntimeException("Company not found");
        }

        targetUser.rejectInvitation(companyId);
        company.rejectManagerInvitation(targetId);
        userRepository.updateUser(targetUser);
        companyRepository.updateCompany(company);
        log.info("Manager invitation rejected successfully");
    }

    public void RevokeManager(String token, int companyId, int targetId) {
        if (!sessionManager.validateToken(token)) {
            log.warn("Invalid token provided for revoking manager");
            throw new RuntimeException("Invalid token");
        }
        int ownerId = sessionManager.extractUserId(token);
        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            log.warn("Company {} not found", companyId);
            throw new RuntimeException("Company not found");
        }
        company.checkowner(ownerId);

        User targetUser = userRepository.getUserById(targetId);
        if (targetUser == null) {
            log.warn("Target user {} not found", targetId);
            throw new RuntimeException("Target user not found");
        }

        company.RevokeManager(targetId);
        targetUser.removeCompanyAppointment(companyId);

        
        userRepository.updateUser(targetUser);
        companyRepository.updateCompany(company);
        log.info("Manager revoked successfully");

    }

    public void ModifyManagerPermissions(String token, int companyId, int targetId, List<Permission> newPermissions) {
        if (!sessionManager.validateToken(token)) {
            log.warn("Invalid token provided for modifying manager permissions");
            throw new RuntimeException("Invalid token");
        }
        int ownerId = sessionManager.extractUserId(token);
        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            log.warn("Company {} not found", companyId);
            throw new RuntimeException("Company not found");
        }
        company.checkowner(ownerId);
        User targetUser = userRepository.getUserById(targetId);
        if (targetUser == null) {
            log.warn("Target user {} not found", targetId);
            throw new RuntimeException("Target user not found");
        }

        company.ModifyManagerPermissions(companyId, targetId, newPermissions);
        targetUser.ModifyManagerPermissions(companyId, targetId, newPermissions);

         userRepository.updateUser(targetUser);
        companyRepository.updateCompany(company);

        log.info("Manager permissions modified successfully");
    }

    // ---------------------------------------------------------------------------
    // DTO-typed methods added in skeleton round (parallel to the existing
    // token-arg / List<Permission>-arg methods above; team to consolidate later).
    // ---------------------------------------------------------------------------

    // UC-18 — register a new Production Company; appoints Founder/Owner in same transaction.
    public ProductionCompanyDTO registerCompany(String token, CompanyRegistrationDTO request) {
        if (!sessionManager.validateToken(token)) {
            log.warn("Invalid token provided for registering a company");
            throw new RuntimeException("Invalid token");
        }
        int userId = sessionManager.extractUserId(token);

        // CompanyRegistrationDTO is a class with get* accessors, not a record.
        if (request.getName() == null || request.getName().trim().isEmpty() ||
            request.getDescription() == null || request.getDescription().trim().isEmpty()) {

            log.warn("Company registration failed: Missing required fields by user {}", userId);
            throw new IllegalArgumentException("All company fields (name, description) must be provided");
        }

        // Call on the injected instance, not the interface.
        if (companyRepository.existsByName(request.getName().trim())) {
            log.warn("Company registration failed: Company name '{}' already exists", request.getName());
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
                null
            );

            // IProductionCompanyRepository.save returns void; the new instance IS the saved one.
            companyRepository.save(newProductionCompany);
            log.info("Successfully registered new company: '{}' by userId: {}", newProductionCompany.getName(), userId);

            return new ProductionCompanyDTO(
                newProductionCompany.getCompanyId(),
                newProductionCompany.getName(),
                newProductionCompany.getDescription(),
                newProductionCompany.getStatus().name(),   // DTO field is String
                newProductionCompany.getFounderId()        // DTO field is founderId
            );

        } catch (Exception e) {
            log.error("Error occurred while saving company '{}': {}", request.getName(), e.getMessage());
            throw new RuntimeException("Failed to register company due to a server error", e);
        }
    }

    // UC-23 — Owner appoints another Member as co-Owner (PENDING).
    public void appointOwner(
            String token,
            com.ticketing.system.Core.Application.dto.OwnerAppointmentRequestDTO request) {
        throw new UnsupportedOperationException("UC-23: not implemented");
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
        log.info("Attempting to view sales history for company {}", companyId);

        if (!sessionManager.validateToken(token)) {
            log.warn("Invalid token provided for viewing sales history");
            throw new InvalidTokenException("Invalid token");
        }

        int requesterId = sessionManager.extractUserId(token);
        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            log.warn("Company {} not found", companyId);
            throw new RuntimeException("Company not found");
        }

        User currUser = userRepository.getUserById(requesterId);
        if (currUser == null) {
            log.warn("User {} not found", requesterId);
            throw new RuntimeException("User not found");
        }
        if (!currUser.isOwnerInCompany(companyId) && !currUser.hasPermissionInCompany(companyId, Permission.VIEW_SALES)) {
            log.warn("User {} does not have permission to view sales history for company {}", requesterId, companyId);
            throw new RuntimeException("Insufficient permissions");
        }

        List<Integer> companyEventIds = eventRepository.findIdsByCompany(companyId);

        if (companyEventIds == null || companyEventIds.isEmpty()) {
            return List.of();
        }

        Set<Integer> companyEventIdSet = Set.copyOf(companyEventIds);
        OrderReceiptMapper mapper = new OrderReceiptMapper();

        List<PurchaseHistoryDTO> salesHistory = this.orderReceiptRepository.findByEventIds(companyEventIds).stream()
                .map(receipt -> {
                    List<Ticket> companyTickets = ticketRepository.findByOrderReceiptId(receipt.getId()).stream()
                            .filter(ticket -> companyEventIdSet.contains(ticket.getEventId()))
                            .toList();

                    return new PurchaseHistoryDTO(
                            List.of(mapper.OrderReceiptToPurchaseRecordDTO(receipt, companyTickets))  // use overloaded mapper to pass the filtered list of tickets for richer DTO construction without bloating service logic
                    );
                })
                .toList();
        
        // This is the correct responsibility split.
        // The repository finds receipts related to company events.
        // The service filters the tickets to only this company’s events.
        // The mapper maps the receipt and the selected tickets into DTOs.


        log.info("Successfully retrieved sales history for company {}", companyId);
        return salesHistory;
    }

    






    // UC-25 — recursive organizational tree (Owners only per II.4.15).
    public OrganizationalTreeNodeDTO viewOrganizationalTree(String token, int companyId) {
        log.info("Attempting to view organizational tree for company {}", companyId);

        if (!sessionManager.validateToken(token)) {
            log.warn("Invalid token provided for viewing organizational tree");
            throw new InvalidTokenException("Invalid token");
        }

        int requesterId = sessionManager.extractUserId(token);
        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            log.warn("Company {} not found", companyId);
            throw new RuntimeException("Company not found");
        }

        User currUser = userRepository.getUserById(requesterId);
        if (currUser == null) {
            log.warn("User {} not found", requesterId);
            throw new RuntimeException("User not found");
        }
        if (!currUser.isOwnerInCompany(companyId)) {
            log.warn("User {} does not have permission to view organizational tree for company {}", requesterId,
                    companyId);
            throw new RuntimeException("Insufficient permissions");
        }

        log.info("Successfully retrieved organizational tree for company {}", companyId);
        // using the helper method to build the tree starting from the founder (root of the tree)
        return buildOrganizationalTree(companyId, company.getFounderId());
    }

    

    // *HELPER METHOD* — BFS build of the organizational tree for UC-25 (viewOrganizationalTree).
    private OrganizationalTreeNodeDTO buildOrganizationalTree(int companyId, int founderId) {

        // Build appointer -> direct appointees map from all managers' CompanyAppointments.
        Map<Integer, List<Integer>> appointerToAppointees = new HashMap<>();
        for (Integer managerId : companyRepository.getCompanyById(companyId).getManagers().keySet()) {
            User manager = userRepository.getUserById(managerId);
            // get this manager's appointment in the current company to find out who appointed them (their inviterId)
            CompanyAppointment appointment = manager.getAppointmentForCompany(companyId);
            if (appointment != null) {
                appointerToAppointees
                    .computeIfAbsent(appointment.getInviterId(), k -> new ArrayList<>()).add(managerId);
            }
        }
        // now we have a map of appointerId -> List of their direct appointees' userIds, which we can use to build the tree.
        
        // Build root node (the founder/owner).
        User founderUser = userRepository.getUserById(founderId);
        List<OrganizationalTreeNodeDTO> rootChildren = new ArrayList<>();
        OrganizationalTreeNodeDTO root = new OrganizationalTreeNodeDTO(
                founderId,
                founderUser.getUsername(),
                CompanyRole.Owner.name(),
                true,
                List.of(),
                rootChildren
        );

        // BFS — for each node, find who it appointed and attach them as children.
        Queue<OrganizationalTreeNodeDTO> queue = new LinkedList<>();
        queue.add(root);

        // BFS while traversal of the organizational tree, building DTO nodes on the fly and attaching to parents.
        while (!queue.isEmpty()) {

            OrganizationalTreeNodeDTO current = queue.poll();
            // get direct appointees of the current node's userId (if any) from the pre-built map; default to empty list if none.
            List<Integer> appointees = appointerToAppointees.getOrDefault(current.userId(), List.of());
            
            // For each direct appointee, create a DTO node and attach to current, then enqueue for further processing.
            for (int appointeeId : appointees) {
                User appointeeUser = userRepository.getUserById(appointeeId);
                CompanyAppointment appt = appointeeUser.getAppointmentForCompany(companyId);
                List<String> permissions = appt != null
                        ? appt.getPermissions().stream().map(Enum::name).toList()
                        : List.of();
                
                List<OrganizationalTreeNodeDTO> childChildren = new ArrayList<>();
                OrganizationalTreeNodeDTO childNode = new OrganizationalTreeNodeDTO(
                        appointeeId,
                        appointeeUser.getUsername(),
                        CompanyRole.Manager.name(),
                        false,
                        permissions,
                        childChildren
                );
                // Attach the child node to the current node's list of appointees and enqueue it for further processing.
                current.appointedByThisUser().add(childNode);
                queue.add(childNode);
            }
        }

        return root;
    }

}
