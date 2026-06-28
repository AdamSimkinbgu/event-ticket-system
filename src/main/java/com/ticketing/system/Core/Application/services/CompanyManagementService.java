package com.ticketing.system.Core.Application.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import com.ticketing.system.Core.Application.dto.UserCompanyDTO;
import com.ticketing.system.Core.Application.dto.AppointmentInfoDTO;
import com.ticketing.system.Core.Application.dto.InvitationDTO;
import com.ticketing.system.Core.Application.dto.MyCompanyDTO;
import com.ticketing.system.Core.Application.dto.OrganizationalTreeNodeDTO;
import com.ticketing.system.Core.Application.dto.OwnerAppointmentRequestDTO;
import com.ticketing.system.Core.Application.dto.PermissionEditDTO;
import com.ticketing.system.Core.Application.dto.AppointmentResponseDTO;
import com.ticketing.system.Core.Application.dto.CompanyPolicyConfigDTO;
import com.ticketing.system.Core.Application.dto.AppointmentRevokeDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Application.dtoMappers.AppointmentInfoMapper;
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
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.users.AppointmentStatus;
import com.ticketing.system.Core.Domain.users.CompanyAppointment;
import com.ticketing.system.Core.Domain.users.CompanyRole;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.Core.Domain.users.User;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Application.interfaces.INotificationService;

import com.ticketing.system.Core.Application.dto.CompanyRegistrationDTO;
import com.ticketing.system.Core.Application.dto.ManagerAppointmentRequestDTO;

import com.ticketing.system.Core.Application.dto.PurchasePolicyDTO;
import com.ticketing.system.Core.Domain.policies.purchase.PurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.NoPurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.AgePurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.AndPurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.OrPurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.MinTicketsPurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.MaxTicketsPurchasePolicy;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner/manager-side service for the ProductionCompany aggregate: registration,
 * the appointment lifecycle (appoint, respond, edit permissions, revoke), roster
 * and invitation queries, purchase-policy configuration, and company sales /
 * organizational-tree reads.
 *
 * <p>Covers UC-18 (register), UC-22 (sales history), UC-23 (appoint co-owner),
 * UC-24 (appoint/edit manager), UC-25 (organizational tree). Authorization is a
 * domain concern: most operations resolve the caller via {@code authenticate}
 * and then defer the ownership/permission check to the {@code User} /
 * {@code ProductionCompany} aggregates. Notification failures never abort a
 * persisted change — they are logged and execution continues.
 */
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

    /**
     * UC-23 — an Owner appoints another member as a co-Owner, creating a PENDING
     * appointment the target must accept. The target is notified.
     *
     * @param token   the appointing owner's token
     * @param request the target company id and target user id
     * @throws IllegalArgumentException    if companyId or targetUserId is not positive
     * @throws InvalidTokenException       if the token is invalid
     * @throws UnauthorizedActionException if the caller is not an owner of the company
     */
    @Transactional
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
                                                                              // appointment, here we'll do logic
                                                                              // checks.

        userRepository.updateUser(targetUser); // update target user with new appointment

        // Notify target user
        try {
            ProductionCompany company = companyRepository.getCompanyById(request.companyId());
            notificationService.notifyOwnerAppointmentPending(request.targetUserId(), request.companyId(),
                    company.getName());
        } catch (Exception e) {
            log.warn("Owner appointment created but notification failed for userId={}", request.targetUserId(), e);
        }

        log.info("Owner appointment created successfully: appointerId={}, targetUserId={}, companyId={}",
                appointerId, request.targetUserId(), request.companyId());
    }

    /**
     * UC-24 — an Owner appoints a Manager with an explicit granular permission
     * set, creating a PENDING appointment the target must accept. The target is
     * notified.
     *
     * @param token   the appointing owner's token
     * @param request the target company id, target user id and granted permissions
     * @throws IllegalArgumentException    if companyId or targetUserId is not positive
     * @throws InvalidTokenException       if the token is invalid
     * @throws UnauthorizedActionException if the caller is not an owner of the company
     */
    @Transactional
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
            notificationService.notifyRoleChanged(request.targetUserId(), request.companyId(), company.getName(),
                    "MANAGER");
        } catch (Exception e) {
            log.warn("Manager appointment created but notification failed for userId={}", request.targetUserId(), e);
        }

        log.info("Manager appointment created successfully: ownerId={}, targetUserId={}, companyId={}, permissions={}",
                ownerId, request.targetUserId(), request.companyId(), request.permissions());
    }

    /**
     * UC-23 / UC-24 — the target accepts or rejects a pending owner/manager
     * appointment. On accept, the appointment transitions to ACTIVE and the
     * company roster is updated (owner or manager); the user is notified of the
     * new role. On reject, the appointment is marked rejected.
     *
     * @param token    the responding user's token
     * @param response the company id and the accept/reject decision
     * @throws IllegalArgumentException if companyId is not positive
     * @throws InvalidTokenException    if the token is invalid
     */
    @Transactional
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
            appointment = user.acceptInvitation(response.companyId()); // transitions the pending appointment to
                                                                       // accepted state
            if (appointment.getRole() == CompanyRole.Owner) {
                company.addOwner(appointment.getInviterId(), userId);
            } else if (appointment.getRole() == CompanyRole.Manager) {
                company.addManager(userId);
            }
            log.info("Appointment accepted: userId={}, companyId={}", userId, response.companyId());

            // Notify of role change to final role
            try {
                notificationService.notifyRoleChanged(userId, response.companyId(), company.getName(),
                        appointment.getRole().name());
            } catch (Exception e) {
                log.warn("Appointment accepted but notification failed for userId={}", userId, e);
            }
        } else {
            user.rejectInvitation(response.companyId()); // transitions the pending appointment to rejected state,
                                                         // status-based lookups will no longer return it.
            log.info("Appointment rejected: userId={}, companyId={}", userId, response.companyId());
        }
        userRepository.updateUser(user);
        companyRepository.updateCompany(company);
    }

    /**
     * UC-24 — edits a Manager's permission set (only by the original appointer).
     * A manager must retain at least one permission. The manager is notified.
     *
     * @param token the appointing owner's token
     * @param edit  the target user, company and new permission set
     * @throws InvalidTokenException       if the token is invalid
     * @throws IllegalArgumentException    if the new permission set is null or empty
     * @throws UnauthorizedActionException if the caller is not the original appointer
     */
    @Transactional
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

        manager.ModifyManagerPermissions(edit.companyId(), ownerId, edit.newPermissions()); // checks done in here.

        userRepository.updateUser(manager);

        // Notify manager of permission change
        try {
            ProductionCompany company = companyRepository.getCompanyById(edit.companyId());
            notificationService.notifyRoleChanged(edit.targetUserId(), edit.companyId(), company.getName(),
                    "MANAGER (permissions updated)");
        } catch (Exception e) {
            log.warn("Manager permissions updated but notification failed for userId={}", edit.targetUserId(), e);
        }

        log.info("Manager permissions updated successfully for user {} in company {}", edit.targetUserId(),
                edit.companyId());
    }

    /**
     * UC-24 — revokes an active appointment, removing the user from the company
     * roster. The founder's appointment cannot be revoked. The user is notified.
     *
     * @param token         the revoking owner's token
     * @param revokeRequest the target company id and target user id
     * @throws InvalidTokenException       if the token is invalid
     * @throws UnauthorizedActionException if the caller is not authorized to revoke
     * @throws RuntimeException            if the target user is the company founder
     */
    @Transactional
    public void RevokeAppointment(String token, AppointmentRevokeDTO revokeRequest) {
        int ownerId = authenticate(token);
        ProductionCompany company = companyRepository.getCompanyById(revokeRequest.companyId());
        User targetUser = userRepository.getUserById(revokeRequest.targetUserId());

        if (company.getFounderId() == revokeRequest.targetUserId()) {
            log.warn("Cannot revoke appointment: target user {} is the founder of company {}",
                    revokeRequest.targetUserId(), company.getCompanyId());
            throw new RuntimeException("Cannot revoke appointment of the founder");
        }

        targetUser.revokeAppointment(revokeRequest.companyId(), ownerId); // checks done in here.
        company.RevokeAppointment(revokeRequest.targetUserId());

        userRepository.updateUser(targetUser);
        companyRepository.updateCompany(company);

        // Notify user of revocation
        try {
            notificationService.notifyManagerRevoked(revokeRequest.targetUserId(), revokeRequest.companyId(),
                    company.getName());
        } catch (Exception e) {
            log.warn("Role revoked but notification failed for userId={}", revokeRequest.targetUserId(), e);
        }

        log.info("Manager revoked successfully");
    }

    /**
     * Resolves a username-or-email string to a userId — used by the invite flow.
     *
     * @param identifier a username or email (trimmed before lookup)
     * @return the matching user's id
     * @throws IllegalArgumentException if the identifier is null or blank
     * @throws UserNotFoundException    if no user matches by username or email
     */
    @Transactional(readOnly = true)
    public int resolveUserId(String identifier) {
        if (identifier == null || identifier.isBlank())
            throw new IllegalArgumentException("Identifier must not be blank");

        Optional<User> byName = userRepository.findByUsername(identifier.trim());
        if (byName.isPresent())
            return byName.get().getUserId();

        Optional<User> byEmail = userRepository.findByEmail(identifier.trim());
        if (byEmail.isPresent())
            return byEmail.get().getUserId();

        throw new UserNotFoundException(identifier);
    }

    // ---------------------------------------------------------------------------
    // Read-side roster queries (#264 — wire ManagerListView).
    // ---------------------------------------------------------------------------

    /**
     * II.4.7.1 — active managers of a company (owner-only view).
     *
     * @param token     the requesting owner's token
     * @param companyId the company whose managers to list
     * @return the company's active manager appointments
     * @throws InvalidTokenException       if the token is invalid
     * @throws CompanyNotFoundException    if the company does not exist
     * @throws UserNotFoundException       if the requester does not exist
     * @throws UnauthorizedActionException if the requester is not an owner
     */
    @Transactional(readOnly = true)
    public List<AppointmentInfoDTO> listManagers(String token, int companyId) {
        int requesterId = authenticate(token);
        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            throw new CompanyNotFoundException(companyId);
        }
        User requester = userRepository.getUserById(requesterId);
        if (requester == null) {
            throw new UserNotFoundException(requesterId);
        }
        requester.requireOwnerInCompany(companyId);

        AppointmentInfoMapper mapper = new AppointmentInfoMapper();
        List<AppointmentInfoDTO> managers = new ArrayList<>();
        for (Integer managerId : company.getManagers()) {
            User manager = userRepository.getUserById(managerId);
            CompanyAppointment appt = manager.getActiveCompanyAppointment(companyId);
            if (appt != null) {
                managers.add(mapper.toDTO(appt, manager.getUsername(), company.getName()));
            }
        }
        log.info("Listed {} active managers for company {}", managers.size(), companyId);
        return managers;
    }

    /**
     * II.4.7.1 — pending invitations (manager + owner offers) awaiting acceptance
     * (owner-only view).
     *
     * @param token     the requesting owner's token
     * @param companyId the company whose pending invitations to list
     * @return the company's pending appointments
     * @throws InvalidTokenException       if the token is invalid
     * @throws CompanyNotFoundException    if the company does not exist
     * @throws UserNotFoundException       if the requester does not exist
     * @throws UnauthorizedActionException if the requester is not an owner
     */
    @Transactional(readOnly = true)
    public List<AppointmentInfoDTO> listPendingInvitations(String token, int companyId) {
        int requesterId = authenticate(token);
        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            throw new CompanyNotFoundException(companyId);
        }
        User requester = userRepository.getUserById(requesterId);
        if (requester == null) {
            throw new UserNotFoundException(requesterId);
        }
        requester.requireOwnerInCompany(companyId);

        AppointmentInfoMapper mapper = new AppointmentInfoMapper();
        List<AppointmentInfoDTO> pending = new ArrayList<>();
        for (User invitee : userRepository.findUsersWithPendingAppointmentForCompany(companyId)) {
            CompanyAppointment appt = invitee.getPendingCompanyAppointment(companyId);
            if (appt != null) {
                pending.add(mapper.toDTO(appt, invitee.getUsername(), company.getName()));
            }
        }
        log.info("Listed {} pending invitations for company {}", pending.size(), companyId);
        return pending;
    }

    /**
     * Companies where the authenticated user holds an ACTIVE Owner appointment.
     * Bridges token → companyId for the owner workspace until a real
     * current-company selector lands (V2-CADMIN-05).
     *
     * @param token the authenticated user's token
     * @return the companies the user owns
     * @throws InvalidTokenException if the token is invalid
     * @throws UserNotFoundException if the user does not exist
     */
    @Transactional(readOnly = true)
    public List<ProductionCompanyDTO> findOwnedCompanies(String token) {
        int userId = authenticate(token);
        User user = userRepository.getUserById(userId);
        if (user == null) {
            throw new UserNotFoundException(userId);
        }

        List<ProductionCompanyDTO> owned = new ArrayList<>();
        for (CompanyAppointment appt : user.getAllCompanyAppointments()) {
            if (appt.getRole() == CompanyRole.Owner && appt.getStatus() == AppointmentStatus.ACTIVE) {
                ProductionCompany company = companyRepository.getCompanyById(appt.getCompanyId());
                if (company != null) {
                    owned.add(new ProductionCompanyDTO(
                            company.getCompanyId(),
                            company.getName(),
                            company.getDescription(),
                            company.getStatus().name(),
                            company.getFounderId()));
                }
            }
        }
        log.info("User {} owns {} active company appointment(s)", userId, owned.size());
        return owned;
    }

    /**
     * Every company the authenticated member belongs to via an ACTIVE appointment
     * (Owner OR Manager), with the viewer's display role resolved
     * ("Founder"/"Co-owner"/"Manager"). Feeds the owner-workspace company selector
     * and name/role subtitle (V2-WIRE-OWNER-DASH); unlike
     * {@link #findOwnedCompanies} it keeps managers, since {@code /owner} is
     * reachable by them too.
     *
     * @param token the authenticated user's token
     * @return the user's company memberships with display role
     * @throws InvalidTokenException if the token is invalid
     * @throws UserNotFoundException if the user does not exist
     */
    @Transactional(readOnly = true)
    public List<MyCompanyDTO> findMyCompanies(String token) {
        int userId = authenticate(token);
        User user = userRepository.getUserById(userId);
        if (user == null) {
            throw new UserNotFoundException(userId);
        }

        List<MyCompanyDTO> companies = new ArrayList<>();
        for (CompanyAppointment appt : user.getAllCompanyAppointments()) {
            if (appt.getStatus() != AppointmentStatus.ACTIVE) {
                continue;
            }
            ProductionCompany company = companyRepository.getCompanyById(appt.getCompanyId());
            if (company == null) {
                continue;
            }
            String role;
            if (appt.getRole() == CompanyRole.Owner) {
                role = company.getFounderId() == userId ? "Founder" : "Co-owner";
            } else {
                role = "Manager";
            }
            companies.add(new MyCompanyDTO(company.getCompanyId(), company.getName(), role));
        }
        log.info("User {} belongs to {} active company appointment(s)", userId, companies.size());
        return companies;
    }

    /**
     * II.4.7.3 / II.4.8.2 — the signed-in member's own invitation records (every
     * status), keyed on the inviter. The presenter splits PENDING (the pending
     * list) from the resolved ACTIVE/REJECTED/REVOKED rows (history). Inviter and
     * company names are resolved per row, falling back to placeholders when the
     * entity no longer exists.
     *
     * @param token the authenticated user's token
     * @return the user's invitation records across all statuses
     * @throws InvalidTokenException if the token is invalid
     * @throws UserNotFoundException if the user does not exist
     */
    @Transactional(readOnly = true)
    public List<InvitationDTO> listMyInvitations(String token) {
        int userId = authenticate(token);
        User user = userRepository.getUserById(userId);
        if (user == null) {
            throw new UserNotFoundException(userId);
        }

        List<InvitationDTO> invitations = new ArrayList<>();
        for (CompanyAppointment appt : user.getAllCompanyAppointments()) {
            ProductionCompany company = companyRepository.getCompanyById(appt.getCompanyId());
            String companyName = company != null ? company.getName() : "(unknown company)";
            // getUserById throws (never returns null) when the inviter no longer
            // exists, so fall back to a placeholder rather than failing the whole list.
            String fromUsername;
            try {
                fromUsername = userRepository.getUserById(appt.getInviterId()).getUsername();
            } catch (UserNotFoundException e) {
                fromUsername = "(unknown)";
            }

            invitations.add(new InvitationDTO(
                    String.valueOf(appt.getAppointmentId()),
                    appt.getCompanyId(),
                    companyName,
                    appt.getRole().name(),
                    fromUsername,
                    appt.getPermissions().stream().map(Permission::name).toList(),
                    appt.getStatus().name(),
                    appt.getCreatedAt()));
        }
        log.info("Listed {} invitation record(s) for user {}", invitations.size(), userId);
        return invitations;
    }

    // ---------------------------------------------------------------------------
    // DTO-typed methods added in skeleton round (parallel to the existing
    // token-arg / List<Permission>-arg methods above; team to consolidate later).
    // ---------------------------------------------------------------------------

    /**
     * UC-18 — registers a new Production Company and appoints the caller as
     * Founder/Owner in the same transaction. The company name must be unique.
     *
     * @param token   the founding user's token
     * @param request the company name and description
     * @return the newly registered company
     * @throws InvalidTokenException    if the token is invalid
     * @throws IllegalArgumentException if the name or description is blank
     * @throws IllegalStateException    if a company with the same name already exists
     * @throws RuntimeException         if persistence fails
     */
    @Transactional
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

    /**
     * Sets the company's default purchase policy (B5), building the policy tree
     * from the supplied DTO. Owner-only.
     *
     * @param token  the owner's token
     * @param config the company id and the purchase-policy configuration
     * @throws IllegalArgumentException    if the config is null or the policy DTO is malformed
     * @throws InvalidTokenException       if the token is invalid
     * @throws CompanyNotFoundException    if the company does not exist
     * @throws UnauthorizedActionException if the caller is not an owner
     */
    @Transactional
    public void setCompanyPolicies(String token, CompanyPolicyConfigDTO config) {
        if (config == null) {
            throw new IllegalArgumentException("Company policy config cannot be null");
        }
        int userId = authenticate(token);
        ProductionCompany company = companyRepository.getCompanyById(config.companyId());
        if (company == null) {
            throw new CompanyNotFoundException(config.companyId());
        }
        User user = userRepository.getUserById(userId);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        user.requirePermissionInCompany(config.companyId(), Permission.EDIT_POLICIES);
        PurchasePolicy policy = buildPurchasePolicyFromDTO(config.defaultPurchasePolicy());
        company.setPurchasePolicy(policy);
        companyRepository.save(company);
        log.info("Purchase policy updated for company {} by user {}", config.companyId(), userId);
    }

    /**
     * UC-22 — Owner-side flat list of company sales. Returns one
     * {@link PurchaseHistoryDTO} per relevant {@code OrderReceipt} (those with at
     * least one ticket for an event of this company), with each receipt trimmed to
     * only the tickets belonging to this company's events. Requires the
     * {@code VIEW_SALES} permission.
     *
     * @param token     the requester's token
     * @param companyId the company whose sales to view
     * @return the company's sales history, one entry per relevant receipt
     * @throws InvalidTokenException if the token is invalid
     * @throws RuntimeException      if the company or user is missing, or the
     *                              requester lacks {@code VIEW_SALES}
     */
    @Transactional(readOnly = true)
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

        // The repository method finds all receipts that have at least one ticket for
        // the company's events.
        List<PurchaseHistoryDTO> salesHistory = this.orderReceiptRepository.findByEventIds(companyEventIds).stream()
                .map(receipt -> {
                    List<Ticket> companyTickets = ticketRepository.findByOrderReceiptId(receipt.getId()).stream()
                            .filter(ticket -> companyEventIdSet.contains(ticket.getEventId()))
                            .toList();

                    return new PurchaseHistoryDTO(List.of(mapper.toPurchaseRecordDTO(
                            receipt, companyTickets, eventRepository, companyRepository, userRepository)));
                    // use overloaded mapper to pass the filtered list of tickets for richer DTO
                    // construction without bloating service logic
                }).toList();

        // This is the correct responsibility split.
        // The repository finds receipts related to company events.
        // The service filters the tickets to only this company’s events.
        // The mapper maps the receipt and the selected tickets into DTOs.

        log.info("Successfully retrieved sales history for company {}", companyId);
        return salesHistory;
    }

    /**
     * UC-25 — recursive organizational tree rooted at the founder (Owners only per
     * II.4.15).
     *
     * @param token     the requesting owner's token
     * @param companyId the company whose tree to build
     * @return the root (founder) node of the organizational tree
     * @throws InvalidTokenException if the token is invalid
     * @throws RuntimeException      if the company or user is missing, or the
     *                              requester is not an owner
     */
    @Transactional(readOnly = true)
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
            log.warn("User {} does not have permission to view organizational tree for company {}, he's not an owner",
                    requesterId,
                    companyId);
            throw new RuntimeException("Insufficient permissions");
        }

        log.info("Successfully retrieved organizational tree for company {}", companyId);
        // using the helper method to build the tree starting from the founder (root of
        // the tree)
        return buildOrganizationalTree(companyId, company.getFounderId());
    }

    /**
     * Admin-only — lists every company in the system regardless of ownership,
     * ordered by company id so the presenter's default selection is deterministic.
     *
     * @param token the admin's token
     * @return all companies
     * @throws InvalidTokenException if the token is invalid or not an admin token
     */
    public List<ProductionCompanyDTO> adminListAllCompanies(String token) {
        authenticate(token);
        if (!sessionManager.isAdminToken(token)) {
            throw new InvalidTokenException("Admin privileges required");
        }
        // Sort by companyId so the default selection (the presenter falls back to the first
        // entry) is deterministic — repository iteration order is not guaranteed.
        return companyRepository.findAll().stream()
                .sorted(Comparator.comparingInt(ProductionCompany::getCompanyId))
                .map(c -> new ProductionCompanyDTO(
                        c.getCompanyId(), c.getName(), c.getDescription(),
                        c.getStatus().name(), c.getFounderId()))
                .toList();
    }

    /**
     * Admin-only — builds the org tree for any company, bypassing the ownership
     * check that {@link #viewOrganizationalTree} enforces.
     *
     * @param token     the admin's token
     * @param companyId the company whose tree to build
     * @return the root (founder) node of the organizational tree
     * @throws InvalidTokenException    if the token is invalid or not an admin token
     * @throws CompanyNotFoundException if the company does not exist
     */
    public OrganizationalTreeNodeDTO adminViewOrgTree(String token, int companyId) {
        authenticate(token);
        if (!sessionManager.isAdminToken(token)) {
            throw new InvalidTokenException("Admin privileges required");
        }
        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            log.warn("Company {} not found", companyId);
            throw new CompanyNotFoundException(companyId);
        }
        log.info("Admin viewing organizational tree for company {}", companyId);
        return buildOrganizationalTree(companyId, company.getFounderId());
    }

    /**
     * Builds the organizational tree for UC-25 in two passes: first a node per
     * member (owners + managers), then linking each node under its appointer.
     *
     * @param companyId the company whose tree to build
     * @param founderId the founder's user id (the tree root)
     * @return the founder's node, the root of the organizational tree
     */
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

    /**
     * Lists every company the given user belongs to via an ACTIVE appointment,
     * skipping memberships whose company can no longer be loaded.
     *
     * @param userId the user whose memberships to list
     * @return the user's active company memberships
     * @throws UserNotFoundException if the user does not exist
     */
    @Transactional(readOnly = true)
    public List<UserCompanyDTO> listForUser(int userId) {
        User user = userRepository.getUserById(userId);
        if (user == null) {
            throw new UserNotFoundException(userId);
        }
        List<UserCompanyDTO> memberships = new ArrayList<>();
        for (CompanyAppointment appointment : user.getAllCompanyAppointments()) {
            if (appointment.getStatus() != AppointmentStatus.ACTIVE) {
                continue;
            }
            ProductionCompany company;
            try {
                company = companyRepository.getCompanyById(appointment.getCompanyId());
            } catch (RuntimeException e) {
                log.warn("Skipping membership for missing companyId={}", appointment.getCompanyId());
                continue;
            }
            memberships.add(toMembershipDto(userId, appointment, company));
        }
        return memberships;
    }

    /**
     * @param userId    the user to check
     * @param companyId the company in question
     * @return {@code true} if the user holds an ACTIVE Owner appointment at the company
     * @throws UserNotFoundException if the user does not exist
     */
    @Transactional(readOnly = true)
    public boolean isOwnerOf(int userId, int companyId) {
        User user = userRepository.getUserById(userId);
        if (user == null) {
            throw new UserNotFoundException(userId);
        }
        CompanyAppointment appointment = user.getActiveCompanyAppointment(companyId);
        return appointment != null && appointment.getRole() == CompanyRole.Owner;
    }

    /**
     * Maps an active appointment to a {@link UserCompanyDTO}, resolving the
     * viewer's display role, member/manager counts and active-event count.
     *
     * @param userId      the viewing user's id
     * @param appointment the user's appointment at the company
     * @param company     the company
     * @return the membership DTO
     */
    private UserCompanyDTO toMembershipDto(int userId, CompanyAppointment appointment, ProductionCompany company) {
        List<Permission> managerPermissions = appointment.getRole() == CompanyRole.Manager
                ? List.copyOf(appointment.getPermissions())
                : List.of();
        return new UserCompanyDTO(
                company.getCompanyId(),
                company.getName(),
                company.getDescription(),
                "",
                displayRole(userId, appointment, company),
                company.getStatus().name(),
                company.getOwnersIds().size() + company.getManagers().size(),
                countActiveEvents(company.getCompanyId()),
                managerPermissions);
    }

    /**
     * @param userId      the viewing user's id
     * @param appointment the user's appointment
     * @param company     the company
     * @return the viewer's display role: "Founder", "Co-owner" or "Manager"
     */
    private static String displayRole(int userId, CompanyAppointment appointment, ProductionCompany company) {
        if (company.getFounderId() == userId)
            return "Founder";
        if (appointment.getRole() == CompanyRole.Owner)
            return "Co-owner";
        if (appointment.getRole() == CompanyRole.Manager)
            return "Manager";
        return appointment.getRole().name();
    }

    /**
     * @param companyId the company id
     * @return the number of the company's events that are ON_SALE, SCHEDULED or
     *         SOLD_OUT
     */
    private int countActiveEvents(int companyId) {
        int count = 0;
        for (Event event : eventRepository.findByCompanyId(companyId)) {
            EventStatus status = event.getStatus();
            if (status == EventStatus.ON_SALE || status == EventStatus.SCHEDULED || status == EventStatus.SOLD_OUT) {
                count++;
            }
        }
        return count;
    }

    /**
     * @param token the token to validate
     * @return the authenticated user's id
     * @throws InvalidTokenException if the token is invalid
     */
    private int authenticate(String token) {
        if (!sessionManager.validateToken(token)) {
            throw new InvalidTokenException("Invalid token");
        }
        return sessionManager.extractUserId(token);
    }

    /**
     * Recursively builds a {@link PurchasePolicy} tree from its DTO
     * representation. AGE/MIN_TICKETS/MAX_TICKETS are leaves; AND/OR fold two or
     * more children left-to-right; NONE (or a null DTO) yields a
     * {@link NoPurchasePolicy}.
     *
     * @param dto the policy DTO (may be null)
     * @return the constructed policy tree
     * @throws IllegalArgumentException if the type is missing/unknown or a required
     *                                  field is absent
     */
    private PurchasePolicy buildPurchasePolicyFromDTO(PurchasePolicyDTO dto) {
        if (dto == null)
            return new NoPurchasePolicy();
        if (dto.type() == null || dto.type().isBlank())
            throw new IllegalArgumentException("Purchase policy type is required");
        switch (dto.type().trim().toUpperCase()) {
            case "AGE":
                if (dto.minimumAge() == null)
                    throw new IllegalArgumentException("minimumAge is required");
                return new AgePurchasePolicy(dto.minimumAge());
            case "MIN_TICKETS":
                if (dto.minimumTickets() == null)
                    throw new IllegalArgumentException("minimumTickets is required");
                return new MinTicketsPurchasePolicy(dto.minimumTickets());
            case "MAX_TICKETS":
                if (dto.maximumTickets() == null)
                    throw new IllegalArgumentException("maximumTickets is required");
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

    /**
     * Returns the company's current default purchase policy as a DTO tree.
     * Owner-only.
     *
     * @param token     the owner's token
     * @param companyId the company whose policy to read
     * @return the company's purchase policy as a DTO
     * @throws InvalidTokenException       if the token is invalid
     * @throws RuntimeException            if the company does not exist
     * @throws UnauthorizedActionException if the caller is not an owner
     */
    @Transactional(readOnly = true)
    public PurchasePolicyDTO getCompanyPurchasePolicy(String token, int companyId) {
        int userId = authenticate(token);
        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null)
            throw new RuntimeException("Company not found");
        User user = userRepository.getUserById(userId);
        if (user == null)
            throw new RuntimeException("User not found");
        user.requirePermissionInCompany(companyId, Permission.EDIT_POLICIES);
        return policyToDTO(company.getPurchasePolicy());
    }

    /**
     * Recursively maps a {@link PurchasePolicy} tree to its DTO representation
     * (the inverse of {@link #buildPurchasePolicyFromDTO}).
     *
     * @param policy the policy tree (a null or {@link NoPurchasePolicy} maps to "NONE")
     * @return the policy as a DTO tree
     */
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
