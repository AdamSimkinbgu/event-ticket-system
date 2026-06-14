package com.ticketing.system.Core.Application.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import com.ticketing.system.Core.Application.dto.OrganizationalTreeNodeDTO;
import com.ticketing.system.Core.Application.dto.OwnerAppointmentRequestDTO;
import com.ticketing.system.Core.Application.dto.PendingInvitationDTO;
import com.ticketing.system.Core.Application.dto.PermissionEditDTO;
import com.ticketing.system.Core.Application.dto.AppointmentResponseDTO;
import com.ticketing.system.Core.Application.dto.AppointmentRevokeDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Application.dtoMappers.OrderReceiptMapper;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Domain.company.CompanyStatus;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Core.Domain.exceptions.UserNotFoundException;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.users.AppointmentStatus;
import com.ticketing.system.Core.Domain.users.CompanyAppointment;
import com.ticketing.system.Core.Domain.users.CompanyRole;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.Core.Domain.users.User;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;

import com.ticketing.system.Core.Application.dto.CompanyRegistrationDTO;
import com.ticketing.system.Core.Application.dto.ManagerAppointmentRequestDTO;

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
            appointment = user.acceptInvitation(response.companyId());
            if (appointment.getRole() == CompanyRole.Owner) {
                company.addOwner(appointment.getInviterId(), userId);
            } else if (appointment.getRole() == CompanyRole.Manager) {
                company.addManager(userId);
            }
            log.info("Appointment accepted: userId={}, companyId={}", userId, response.companyId());
        } else {
            user.rejectInvitation(response.companyId());   // this will remove the pending appointment from the user's list, so no need to check role here.
            log.info("Appointment rejected: userId={}, companyId={}", userId, response.companyId());
        }
        userRepository.updateUser(user);
        companyRepository.updateCompany(company);
    }


    // UC-24 / II.4.7.3 — list all pending invitations for the signed-in user.
    public List<PendingInvitationDTO> listPendingInvitations(String token) {
        int userId = authenticate(token);
        User user = userRepository.getUserById(userId);

        return user.getAllCompanyAppointments().stream()
            .filter(a -> a.getStatus() == AppointmentStatus.PENDING)
            .map(a -> {
                ProductionCompany company = companyRepository.getCompanyById(a.getCompanyId());
                String companyName = company != null ? company.getName() : "Unknown company";
                String inviterName;
                try {
                    inviterName = userRepository.getUserById(a.getInviterId()).getUsername();
                } catch (Exception e) {
                    inviterName = "Unknown";
                }
                return new PendingInvitationDTO(
                    a.getCompanyId(),
                    companyName,
                    a.getRole().name(),
                    a.getPermissions().stream().toList(),
                    inviterName
                );
            })
            .toList();
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
        log.info("Manager revoked successfully");
    }


    // Resolves a username-or-email string to a userId — used by the invite flow.
    public int resolveUserId(String identifier) {
        if (identifier == null || identifier.isBlank())
            throw new IllegalArgumentException("Identifier must not be blank");

        Optional<User> byName = userRepository.findByUsername(identifier.trim());
        if (byName.isPresent()) return byName.get().getUserId();

        Optional<User> byEmail = userRepository.findByEmail(identifier.trim());
        if (byEmail.isPresent()) return byEmail.get().getUserId();

        throw new RuntimeException("No user found with username or email: " + identifier);
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











    public void setCompanyPolicies(
            String token,
            com.ticketing.system.Core.Application.dto.CompanyPolicyConfigDTO config) {
        throw new UnsupportedOperationException("UC-21: not implemented");
    }





    // UC-22 — Owner-side flat list of company sales.
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

        List<PurchaseHistoryDTO> salesHistory = this.orderReceiptRepository.findByEventIds(companyEventIds).stream()
                .map(receipt -> {
                    List<Ticket> companyTickets = ticketRepository.findByOrderReceiptId(receipt.getId()).stream()
                            .filter(ticket -> companyEventIdSet.contains(ticket.getEventId()))
                            .toList();

                    return new PurchaseHistoryDTO(
                            List.of(mapper.toPurchaseRecordDTO(receipt, companyTickets)) // use overloaded
                                                                                                                                   // mapper to pass
                                                                                                                                   // the filtered
                                                                                                                                   // list of tickets
                                                                                                                                   // for richer DTO
                                                                                                                                   // construction
                                                                                                                                   // without bloating
                                                                                                                                   // service logic
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
}
