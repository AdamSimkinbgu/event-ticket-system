package com.ticketing.system.Core.Application.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import com.ticketing.system.Core.Application.dto.OrganizationalTreeNodeDTO;
import com.ticketing.system.Core.Application.dto.OwnerAppointmentRequestDTO;
import com.ticketing.system.Core.Application.dto.PermissionEditDTO;
import com.ticketing.system.Core.Application.dto.AppointmentResponseDTO;
import com.ticketing.system.Core.Application.dto.CompanyPolicyConfigDTO;
import com.ticketing.system.Core.Application.dto.AppointmentRevokeDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Application.dtoMappers.OrderReceiptMapper;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Domain.company.CompanyStatus;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.exceptions.CompanyNotFoundException;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Core.Domain.exceptions.UserNotFoundException;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.users.CompanyAppointment;
import com.ticketing.system.Core.Domain.users.CompanyRole;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.Core.Domain.users.User;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Application.interfaces.INotificationService;

import com.ticketing.system.Core.Application.dto.CompanyRegistrationDTO;
import com.ticketing.system.Core.Application.dto.ManagerAppointmentRequestDTO;

import java.util.regex.Pattern;
import com.ticketing.system.Core.Application.dto.PurchasePolicyDTO;
import com.ticketing.system.Core.Domain.policies.purchase.PurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.NoPurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.AgePurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.AndPurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.OrPurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.MinTicketsPurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.MaxTicketsPurchasePolicy;

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
    private final INotificationService notificationService;

    public CompanyManagementService(IProductionCompanyRepository companyRepository, IUserRepository userRepository,
            IOrderReceiptRepository orderReceiptRepository, ISessionManager sessionManager,
            ITicketRepository ticketRepository, IEventRepository eventRepository,
            INotificationService notificationService) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.orderReceiptRepository = orderReceiptRepository;
        this.sessionManager = sessionManager;
        this.ticketRepository = ticketRepository;
        this.eventRepository = eventRepository;
        this.notificationService = notificationService;
    }





    // UC-23 — Owner appoints another Member as co-Owner (PENDING).
    public void appointOwner(String token, OwnerAppointmentRequestDTO request) {
        if (request.companyId() <= 0 || request.targetUserId() <= 0) {
            log.warn("Invalid appointment request: companyId and targetUserId must be positive integers");
            throw new IllegalArgumentException("companyId and targetUserId must be positive integers");
        }
        int appointerId = authenticate(token);
        
        User appointer = null;
        User targetUser = null;
        try {
            appointer = userRepository.getUserById(appointerId);
            targetUser = userRepository.getUserById(request.targetUserId());
        } catch (UserNotFoundException e) {
            log.warn("User appointer/appointee not found during appointment: {}", e.getMessage());
            throw new RuntimeException("User not found");
        }
        

        appointer.requireOwnerInCompany(request.companyId()); // check if appointer has permissions
        targetUser.receiveOwnerAppointment(request.companyId(), appointerId); // target user receives pending owner
                                                                              // appointment, here we'll do logic checks.

        userRepository.updateUser(targetUser); // update target user with new appointment
        
        // Notify target user
        try {
            ProductionCompany company = companyRepository.getCompanyById(request.companyId());
            notificationService.notifyOwnerAppointmentPending(request.targetUserId(), request.companyId(), company.getName());
        } catch (Exception e) {
            log.warn("Owner appointment created but notification failed for userId={}", request.targetUserId());
        }

        log.info("Owner appointment created successfully: appointerId={}, targetUserId={}, companyId={}",
                appointerId, request.targetUserId(), request.companyId());
    }





    // UC-24 — Owner appoints a Manager with explicit granular permissions.
    public void appointManager(String token, ManagerAppointmentRequestDTO request) {
        if (request.companyId() <= 0 || request.targetUserId() <= 0) {
            log.warn("Invalid manager appointment request: companyId and targetUserId must be positive integers");
            throw new IllegalArgumentException("companyId and targetUserId must be positive integers");
        }
        int ownerId = authenticate(token);
        User owner = userRepository.getUserById(ownerId);
        User targetUser = userRepository.getUserById(request.targetUserId());
        owner.requireOwnerInCompany(request.companyId());

        targetUser.receiveManagerAppointment(request.companyId(), ownerId, request.permissions());
        userRepository.updateUser(targetUser);

        // Notify target user
        try {
            ProductionCompany company = companyRepository.getCompanyById(request.companyId());
            notificationService.notifyRoleChanged(request.targetUserId(), request.companyId(), company.getName(), "MANAGER");
        } catch (Exception e) {
            log.warn("Manager appointment created but notification failed for userId={}", request.targetUserId());
        }

        log.info("Manager appointment created successfully: ownerId={}, targetUserId={}, companyId={}, permissions={}",
                ownerId, request.targetUserId(), request.companyId(), request.permissions());
    }





    // UC-23 / UC-24 — target accepts or rejects a pending owner/manager appointment.
    public void respondToAppointment(String token, AppointmentResponseDTO response) {
        if (response.companyId() <= 0) {
            log.warn("Invalid appointment response: companyId must be a positive integer");
            throw new IllegalArgumentException("companyId must be a positive integer");
        }

        int userId = authenticate(token);
        User user = userRepository.getUserById(userId);
        ProductionCompany company = companyRepository.getCompanyById(response.companyId());

        CompanyAppointment appointment;

        if (response.accept()) {
            appointment = user.acceptInvitation(response.companyId());  // transitions the pending appointment to accepted state
            if (appointment.getRole() == CompanyRole.Owner) {
                company.addOwner(appointment.getInviterId(), userId);
            } else if (appointment.getRole() == CompanyRole.Manager) {
                company.addManager(userId);
            }
            log.info("Appointment accepted: userId={}, companyId={}", userId, response.companyId());

            // Notify of role change to final role
            try {
                notificationService.notifyRoleChanged(userId, response.companyId(), company.getName(), appointment.getRole().name());
            } catch (Exception e) {
                log.warn("Appointment accepted but notification failed for userId={}", userId);
            }
        } else {
            user.rejectInvitation(response.companyId()); // transitions the pending appointment to rejected state, status-based lookups will no longer return it.
            log.info("Appointment rejected: userId={}, companyId={}", userId, response.companyId());
        }
        userRepository.updateUser(user);
        companyRepository.updateCompany(company);
    }







    // UC-24 — edit a Manager's permission set (only by the original appointer).
    public void editManagerPermissions(String token, PermissionEditDTO edit) {
        int ownerId = authenticate(token);
        
        User manager = null;
        try {
            manager = userRepository.getUserById(edit.targetUserId());
        } catch (UserNotFoundException e) {
            log.warn("Manager not found during permission edit: {}", e.getMessage());
            throw new RuntimeException("Manager not found");
        }
        
        if (edit.newPermissions() == null || edit.newPermissions().isEmpty()) {
            log.warn("Invalid permission edit: newPermissions list cannot be null or empty");
            throw new IllegalArgumentException("Manager role must have at least one permission");
        }

        manager.ModifyManagerPermissions(edit.companyId(), ownerId, edit.newPermissions());  // checks done in here.

        userRepository.updateUser(manager);

        // Notify manager of permission change
        try {
            ProductionCompany company = companyRepository.getCompanyById(edit.companyId());
            notificationService.notifyRoleChanged(edit.targetUserId(), edit.companyId(), company.getName(), "MANAGER (permissions updated)");
        } catch (Exception e) {
            log.warn("Manager permissions updated but notification failed for userId={}", edit.targetUserId());
        }

        log.info("Manager permissions updated successfully for user {} in company {}", edit.targetUserId(),
                edit.companyId());
    }





    public void RevokeAppointment(String token, AppointmentRevokeDTO revokeRequest) {
        int ownerId = authenticate(token);
        ProductionCompany company = companyRepository.getCompanyById(revokeRequest.companyId());
        User targetUser = userRepository.getUserById(revokeRequest.targetUserId());

        if (company.getFounderId() == revokeRequest.targetUserId()) {
            log.warn("Cannot revoke appointment: target user {} is the founder of company {}",
                    revokeRequest.targetUserId(), company.getCompanyId());
            throw new RuntimeException("Cannot revoke appointment of the founder");
        }

        targetUser.revokeAppointment(revokeRequest.companyId(), ownerId);  // checks done in here.
        company.RevokeAppointment(revokeRequest.targetUserId());

        userRepository.updateUser(targetUser);
        companyRepository.updateCompany(company);

        // Notify user of revocation
        try {
            notificationService.notifyManagerRevoked(revokeRequest.targetUserId(), revokeRequest.companyId(), company.getName());
        } catch (Exception e) {
            log.warn("Role revoked but notification failed for userId={}", revokeRequest.targetUserId());
        }

        log.info("Manager revoked successfully");
    }




    
    // ---------------------------------------------------------------------------
    // DTO-typed methods added in skeleton round (parallel to the existing
    // token-arg / List<Permission>-arg methods above; team to consolidate later).
    // ---------------------------------------------------------------------------

    // UC-18 — register a new Production Company; appoints Founder/Owner in same
    // transaction.
    public ProductionCompanyDTO registerCompany(String token, CompanyRegistrationDTO request) {
        int userId = authenticate(token);
        User user = userRepository.getUserById(userId);

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
                    null);

            // IProductionCompanyRepository.save returns void; the new instance IS the saved
            // one.
            companyRepository.save(newProductionCompany);
            user.addFounderAppointment(companyId);
            userRepository.updateUser(user);
            log.info("Successfully registered new company: '{}' by userId: {}", newProductionCompany.getName(), userId);

            return new ProductionCompanyDTO(
                    newProductionCompany.getCompanyId(),
                    newProductionCompany.getName(),
                    newProductionCompany.getDescription(),
                    newProductionCompany.getStatus().name(), // DTO field is String
                    newProductionCompany.getFounderId() // DTO field is founderId
            );

        } catch (Exception e) {
            log.error("Error occurred while saving company '{}': {}", request.getName(), e.getMessage());
            throw new RuntimeException("Failed to register company due to a server error", e);
        }
    }











    public void setCompanyPolicies( String token, CompanyPolicyConfigDTO config) {
    if (config == null) {
        throw new IllegalArgumentException("Company policy config cannot be null");
    }
    int userId = authenticate(token);
    ProductionCompany company = companyRepository.getCompanyById(config.companyId());
    if (company == null) {
        throw new CompanyNotFoundException(config.companyId());
    }
    company.checkowner(userId);
    PurchasePolicy policy = buildPurchasePolicyFromDTO(config.defaultPurchasePolicy());
    company.setPurchasePolicy(policy);
    companyRepository.save(company);
    log.info("Purchase policy updated for company {} by user {}", config.companyId(), userId);
}

    // UC-22 — Owner-side flat list of company sales.
    // this function returns a list of PurchaseHistoryDTO, each containing a single PurchaseRecordDTO, which represents a single OrderReceipt that has 
    // at least one ticket for an event of this company. The PurchaseRecordDTO contains a list of TicketRecordDTOs, but only those that are 
    // for events of this company (the rest are filtered out). This way we return the full receipt details for each relevant purchase, but only 
    // include the tickets that are relevant to this company's sales history.
    public List<PurchaseHistoryDTO> viewSalesHistory(String token, int companyId) {
        log.info("Attempting to view sales history for company {}", companyId);

        int requesterId = authenticate(token);
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
        if (!currUser.hasPermissionInCompany(companyId, Permission.VIEW_SALES)) {
            log.warn("User {} does not have permission to view sales history for company {}", requesterId, companyId);
            throw new RuntimeException("Insufficient permissions");
        }

        List<Integer> companyEventIds = eventRepository.findIdsByCompany(companyId);

        if (companyEventIds == null || companyEventIds.isEmpty()) {
            return List.of();
        }

        Set<Integer> companyEventIdSet = Set.copyOf(companyEventIds);
        OrderReceiptMapper mapper = new OrderReceiptMapper();

        // The repository method finds all receipts that have at least one ticket for the company's events.
        List<PurchaseHistoryDTO> salesHistory = this.orderReceiptRepository.findByEventIds(companyEventIds).stream()
                .map(receipt -> {
                    List<Ticket> companyTickets = ticketRepository.findByOrderReceiptId(receipt.getId()).stream()
                            .filter(ticket -> companyEventIdSet.contains(ticket.getEventId()))
                            .toList();

                    return new PurchaseHistoryDTO(List.of(mapper.toPurchaseRecordDTO(
                            receipt, companyTickets, eventRepository, companyRepository, userRepository)));
                    // use overloaded mapper to pass the filtered list of tickets for richer DTO construction without bloating service logic
                }).toList();

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

        int requesterId = authenticate(token);
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
            log.warn("User {} does not have permission to view organizational tree for company {}, he's not an owner", requesterId,
                    companyId);
            throw new RuntimeException("Insufficient permissions");
        }

        log.info("Successfully retrieved organizational tree for company {}", companyId);
        // using the helper method to build the tree starting from the founder (root of the tree)
        return buildOrganizationalTree(companyId, company.getFounderId());
    }



    // *HELPER METHOD* — BFS build of the organizational tree for UC-25
    // (viewOrganizationalTree).
    private OrganizationalTreeNodeDTO buildOrganizationalTree(int companyId, int founderId) {
        ProductionCompany company = companyRepository.getCompanyById(companyId);

        // gather all members (owners and managers) of the company in a single list for
        // easy processing
        List<Integer> members = new ArrayList<>();
        members.addAll(company.getManagers());
        members.addAll(company.getOwnersIds());

        Map<Integer, OrganizationalTreeNodeDTO> userIdToNodeMap = new HashMap<>();

        // First pass: create a node for each member (including founder) without setting
        // children yet.
        for (Integer memberId : members) {
            User memberUser = userRepository.getUserById(memberId);
            CompanyAppointment appt = memberUser.getActiveCompanyAppointment(companyId);

            OrganizationalTreeNodeDTO node = new OrganizationalTreeNodeDTO(
                    memberId,
                    memberUser.getUsername(),
                    appt.getRole().name(),
                    memberId == founderId,
                    appt.getPermissions().stream().toList(),
                    new ArrayList<>());

            userIdToNodeMap.put(memberId, node);
        }

        // Second pass: set the appointedByThisUser list for each node based on
        // inviterId.
        for (Integer memberId : members) {
            if (memberId == founderId)
                continue; // skip founder, they have no appointer
            User memberUser = userRepository.getUserById(memberId);
            CompanyAppointment appt = memberUser.getActiveCompanyAppointment(companyId);
            OrganizationalTreeNodeDTO node = userIdToNodeMap.get(memberId);
            OrganizationalTreeNodeDTO inviterNode = userIdToNodeMap.get(appt.getInviterId());
            inviterNode.appointedByThisUser().add(node);
        }

        // return the founder's node, which is the root of the organizational tree
        return userIdToNodeMap.get(founderId);
    }









    private int authenticate(String token) {
        if (!sessionManager.validateToken(token)) {
            throw new InvalidTokenException("Invalid token");
        }
        return sessionManager.extractUserId(token);
    }
private PurchasePolicy buildPurchasePolicyFromDTO(PurchasePolicyDTO dto) {
    if (dto == null) return new NoPurchasePolicy();
    if (dto.type() == null || dto.type().isBlank())
        throw new IllegalArgumentException("Purchase policy type is required");
    switch (dto.type().trim().toUpperCase()) {
        case "AGE":
            if (dto.minimumAge() == null) throw new IllegalArgumentException("minimumAge is required");
            return new AgePurchasePolicy(dto.minimumAge());
        case "MIN_TICKETS":
            if (dto.minimumTickets() == null) throw new IllegalArgumentException("minimumTickets is required");
            return new MinTicketsPurchasePolicy(dto.minimumTickets());
        case "MAX_TICKETS":
            if (dto.maximumTickets() == null) throw new IllegalArgumentException("maximumTickets is required");
            return new MaxTicketsPurchasePolicy(dto.maximumTickets());
        case "AND":
            if (dto.children() == null || dto.children().size() < 2)
                throw new IllegalArgumentException("AND policy must have at least two children");
            PurchasePolicy andResult = buildPurchasePolicyFromDTO(dto.children().get(0));
            for (int i = 1; i < dto.children().size(); i++)
                andResult = new AndPurchasePolicy(andResult, buildPurchasePolicyFromDTO(dto.children().get(i)));
            return andResult;
        case "OR":
            if (dto.children() == null || dto.children().size() < 2)
                throw new IllegalArgumentException("OR policy must have at least two children");
            PurchasePolicy orResult = buildPurchasePolicyFromDTO(dto.children().get(0));
            for (int i = 1; i < dto.children().size(); i++)
                orResult = new OrPurchasePolicy(orResult, buildPurchasePolicyFromDTO(dto.children().get(i)));
            return orResult;
        case "NONE":
            return new NoPurchasePolicy();
        default:
            throw new IllegalArgumentException("Unknown purchase policy type: " + dto.type());
    }
}
public PurchasePolicyDTO getCompanyPurchasePolicy(String token, int companyId) {
    int userId = authenticate(token);
    ProductionCompany company = companyRepository.getCompanyById(companyId);
    if (company == null) throw new RuntimeException("Company not found");
    company.checkowner(userId);
    return policyToDTO(company.getPurchasePolicy());
}


private PurchasePolicyDTO policyToDTO(PurchasePolicy policy) {
    if (policy == null || policy instanceof NoPurchasePolicy)
        return new PurchasePolicyDTO("NONE", null, null, null, null);
    if (policy instanceof AgePurchasePolicy a)
        return new PurchasePolicyDTO("AGE", a.getMinimumAge(), null, null, null);
    if (policy instanceof MinTicketsPurchasePolicy m)
        return new PurchasePolicyDTO("MIN_TICKETS", null, m.getMinimumTickets(), null, null);
    if (policy instanceof MaxTicketsPurchasePolicy m)
        return new PurchasePolicyDTO("MAX_TICKETS", null, null, m.getMaximumTickets(), null);
    if (policy instanceof AndPurchasePolicy a)
        return new PurchasePolicyDTO("AND", null, null, null,
            List.of(policyToDTO(a.getLeftPolicy()), policyToDTO(a.getRightPolicy())));
    if (policy instanceof OrPurchasePolicy o)
        return new PurchasePolicyDTO("OR", null, null, null,
            List.of(policyToDTO(o.getLeftPolicy()), policyToDTO(o.getRightPolicy())));
    return new PurchasePolicyDTO("NONE", null, null, null, null);
}
}
