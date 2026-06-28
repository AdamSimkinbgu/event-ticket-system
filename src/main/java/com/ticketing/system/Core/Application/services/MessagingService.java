package com.ticketing.system.Core.Application.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.system.Core.Application.dto.ComplaintFilterDTO;
import com.ticketing.system.Core.Application.dto.ConversationDTO;
import com.ticketing.system.Core.Application.dto.OutreachRequestDTO;
import com.ticketing.system.Core.Application.dto.OutreachResultDTO;
import com.ticketing.system.Core.Application.dto.RespondToComplaintRequestDTO;
import com.ticketing.system.Core.Application.dto.SendMessageRequestDTO;
import com.ticketing.system.Core.Application.dto.StartConversationRequestDTO;
import com.ticketing.system.Core.Application.dto.SubmitComplaintRequestDTO;
import com.ticketing.system.Core.Application.dtoMappers.ConversationMapper;
import com.ticketing.system.Core.Application.interfaces.INotificationService;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Domain.Admin.Admin;
import com.ticketing.system.Core.Domain.Admin.IAdminRepository;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.exceptions.BusinessRuleViolationException;
import com.ticketing.system.Core.Domain.exceptions.ConversationNotFoundException;
import com.ticketing.system.Core.Domain.exceptions.InvalidParticipantException;
import com.ticketing.system.Core.Domain.exceptions.UnauthorizedActionException;
import com.ticketing.system.Core.Domain.exceptions.UserNotFoundException;
import com.ticketing.system.Core.Domain.messaging.Conversation;
import com.ticketing.system.Core.Domain.messaging.ConversationStatus;
import com.ticketing.system.Core.Domain.messaging.ConversationType;
import com.ticketing.system.Core.Domain.messaging.IConversationRepository;
import com.ticketing.system.Core.Domain.messaging.ParticipantType;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.Core.Domain.users.User;

import lombok.extern.slf4j.Slf4j;

/**
 * Centralized messaging subsystem service. Replaces the per-User MessageInbox,
 * the per-Company Inbox, and the standalone Complaint flow. Covers II.3.3
 * (complaint), II.3.10 (contact company), II.4.4 (company support inbox),
 * II.6.3.1 (admin complaint queue) and II.6.3.2 (admin announcements).
 *
 * <p>All write paths fire the messaging→notification bridge <em>after</em>
 * releasing the conversation lock: members are notified of admin/producer
 * messages, company owners of new inquiries, and admins of new complaints. A
 * notification failure never undoes a persisted message (see
 * {@code safeNotify}).
 */
@Service
@Slf4j
public class MessagingService {

    // Sentinel counterparty id for group descriptors (ADMIN_GROUP has no single entity id).
    private static final int GROUP_SENTINEL = 0;
    private static final int SNIPPET_MAX = 120;

    private final IConversationRepository conversationRepository;
    private final ISessionManager sessionManager;
    private final IAdminRepository adminRepository;
    private final IUserRepository userRepository;
    private final IProductionCompanyRepository companyRepository;
    private final INotificationService notificationService;

    public MessagingService(
            IConversationRepository conversationRepository,
            ISessionManager sessionManager,
            IAdminRepository adminRepository,
            IUserRepository userRepository,
            IProductionCompanyRepository companyRepository,
            INotificationService notificationService
    ) {
        this.conversationRepository = conversationRepository;
        this.sessionManager = sessionManager;
        this.adminRepository = adminRepository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.notificationService = notificationService;
    }

    /** Which side of a conversation the authenticated caller is acting as. */
    private record CallerSide(int id, ParticipantType type) {}

    // ---------------------------------------------------------------------------
    // Write operations
    // ---------------------------------------------------------------------------

    /**
     * II.3.10 — a member starts an INQUIRY conversation (two-way chat) with a
     * company, then the company's owners are notified.
     *
     * @param token   the authenticated member's token
     * @param request the target company id, subject and first message body
     * @return the created conversation, from the member's viewpoint
     * @throws UnauthorizedActionException if the token is invalid or expired
     * @throws com.ticketing.system.Core.Domain.exceptions.CompanyNotFoundException
     *         if the target company does not exist
     */
    @Transactional
    public ConversationDTO startConversation(String token, StartConversationRequestDTO request) {
        int memberId = authenticate(token);
        int companyId = request.counterpartyId();
        ProductionCompany company = companyRepository.getCompanyById(companyId); // throws if missing

        Conversation conversation = Conversation.start(
                ConversationType.INQUIRY,
                memberId, ParticipantType.MEMBER,
                companyId, ParticipantType.COMPANY,
                request.subject(), request.firstMessageBody());
        conversationRepository.save(conversation);

        // Bridge — a member opened an inquiry; notify the company's owners.
        notifyCompanyOwners(company, conversation, senderLabel(ParticipantType.MEMBER), request.firstMessageBody());
        log.info("Inquiry {} started by member {} to company {}",
                conversation.getConversationId(), memberId, companyId);
        return toDTO(conversation, memberId);
    }



    /**
     * Appends a reply to an existing conversation, then notifies the other party.
     * Complaints are one-shot and are rejected here — the admin's single reply
     * goes through {@link #respondToComplaint}.
     *
     * @param token   the sender's token
     * @param request the target conversation id and message body
     * @throws UnauthorizedActionException   if the token is invalid or expired
     * @throws ConversationNotFoundException if the conversation does not exist
     * @throws BusinessRuleViolationException if the conversation is a complaint
     * @throws InvalidParticipantException   if the sender is not a participant
     */
    @Transactional
    public void sendMessage(String token, SendMessageRequestDTO request) {
        Caller caller = authenticateCaller(token);
        String conversationId = request.conversationId();

        Conversation conversation;
        CallerSide sender;
        conversationRepository.lockForUpdate(conversationId);
        try {
            conversation = loadConversation(conversationId);
            // Complaints are one-shot: the admin's single reply goes through respondToComplaint,
            // never this chat path. (The domain enforces this too — belt and suspenders.)
            if (conversation.getType() == ConversationType.COMPLAINT) {
                throw new BusinessRuleViolationException(
                        "Complaints are not a chat — they accept a single admin response only");
            }
            sender = resolveCallerSide(caller, conversation);
            conversation.addMessage(sender.id(), sender.type(), request.body());
            conversationRepository.save(conversation);
        } finally {
            conversationRepository.unlock(conversationId);
        }

        // Bridge — notify the other side, outside the lock.
        notifyOtherParty(conversation, sender, request.body());
        log.info("Message appended to conversation {} by {} {}", conversationId, sender.type(), sender.id());
    }



    /**
     * II.3.3 — a member submits a COMPLAINT to the admin group. An optional
     * related-entity reference is appended to the body; all admins are then
     * notified.
     *
     * @param token   the authenticated member's token
     * @param request the subject, body and optional related-entity reference
     * @return the created complaint, from the member's viewpoint
     * @throws UnauthorizedActionException if the token is invalid or expired
     */
    @Transactional
    public ConversationDTO submitComplaint(String token, SubmitComplaintRequestDTO request) {
        int memberId = authenticate(token);
        String body = request.body();
        if (request.relatedEntityRef() != null && !request.relatedEntityRef().isBlank()) {
            body = body + "\n\n[Related: " + request.relatedEntityRef() + "]";
        }
        Conversation conversation = Conversation.start(
                ConversationType.COMPLAINT,
                memberId, ParticipantType.MEMBER,
                GROUP_SENTINEL, ParticipantType.ADMIN_GROUP,
                request.subject(), body);
        conversationRepository.save(conversation);

        // Bridge — notify all admins a complaint was filed.
        notifyAllAdmins(conversation, senderLabel(ParticipantType.MEMBER), body);
        log.info("Complaint {} submitted by member {}", conversation.getConversationId(), memberId);
        return toDTO(conversation, memberId);
    }



    /**
     * II.6.3.1 — an admin sends the single, terminal response to a complaint,
     * which resolves it (one-shot). The lock serializes concurrent admins and the
     * domain rejects a second admin reply; the filing member is then notified.
     *
     * @param token   the admin's token
     * @param request the target complaint id and response body
     * @throws UnauthorizedActionException    if the token is not a valid admin token
     * @throws ConversationNotFoundException  if the conversation does not exist
     * @throws BusinessRuleViolationException if the conversation is not a complaint
     */
    @Transactional
    public void respondToComplaint(String token, RespondToComplaintRequestDTO request) {
        int adminId = requireSystemAdmin(token);
        String conversationId = request.conversationId();

        Conversation conversation;
        conversationRepository.lockForUpdate(conversationId);
        try {
            conversation = loadConversation(conversationId);
            if (conversation.getType() != ConversationType.COMPLAINT) {
                throw new BusinessRuleViolationException(
                        "Conversation " + conversationId + " is not a complaint");
            }
            // The admin's reply is the complaint's terminal response: append it, then resolve.
            // The lock serializes concurrent admins; the domain rejects a second admin reply.
            conversation.addMessage(adminId, ParticipantType.ADMIN, request.body());
            conversation.transitionToResolved();
            conversationRepository.save(conversation);
        } finally {
            conversationRepository.unlock(conversationId);
        }

        // Bridge — notify the member who filed the complaint.
        safeNotify(conversation.getInitiatorId(), conversationId,
                senderLabel(ParticipantType.ADMIN), conversation.getSubject(), request.body());
        log.info("Admin {} resolved complaint {}", adminId, conversationId);
    }

    /**
     * II.6.3.2 — admin proactive messaging. Resolves the recipient set (explicit
     * members, and/or all members, and/or all producers' owner accounts), then
     * opens one two-way DIRECT conversation per recipient so each lands in that
     * member's Support Inbox as a chat. Each recipient is notified.
     *
     * @param token   the admin's token
     * @param request the recipient selection (all-members / all-producers /
     *                explicit ids), subject and body
     * @return the number of conversations created and the first conversation's id
     * @throws UnauthorizedActionException   if the token is not a valid admin token
     * @throws BusinessRuleViolationException if the selection matches no recipients
     */
    @Transactional
    public OutreachResultDTO sendOutreach(String token, OutreachRequestDTO request) {
        int adminId = requireSystemAdmin(token);

        Set<Integer> recipientIds = new LinkedHashSet<>();
        if (request.allMembers()) {
            for (User member : userRepository.findAll()) {
                recipientIds.add(member.getUserId());
            }
        }
        if (request.allProducers()) {
            for (ProductionCompany company : companyRepository.findActive()) {
                for (Integer ownerId : company.getOwnerIds()) {
                    if (ownerId != null) recipientIds.add(ownerId);
                }
            }
        }
        if (!request.allMembers() && !request.allProducers()) {
            for (Integer memberId : safeList(request.recipientMemberIds())) {
                if (memberId != null) recipientIds.add(memberId);
            }
        }

        if (recipientIds.isEmpty()) {
            throw new BusinessRuleViolationException("Outreach matched no recipients");
        }

        List<Conversation> created = new ArrayList<>();
        for (Integer memberId : recipientIds) {
            Conversation conversation = Conversation.start(
                    ConversationType.DIRECT,
                    adminId, ParticipantType.ADMIN,
                    memberId, ParticipantType.MEMBER,
                    request.subject(), request.body());
            conversationRepository.save(conversation);
            safeNotify(memberId, conversation.getConversationId(),
                    senderLabel(ParticipantType.ADMIN), request.subject(), request.body());
            created.add(conversation);
        }

        log.info("Admin {} sent outreach to {} recipient(s)", adminId, created.size());
        return new OutreachResultDTO(created.size(), created.get(0).getConversationId());
    }



    /**
     * UI action — marks a single message as read by the viewer.
     *
     * @param token          the viewer's token
     * @param conversationId the conversation containing the message
     * @param messageId      the message to mark read
     * @throws UnauthorizedActionException   if the token is invalid or expired
     * @throws ConversationNotFoundException if the conversation does not exist
     * @throws InvalidParticipantException   if the caller is not a participant
     */
    @Transactional
    public void markMessageAsRead(String token, String conversationId, String messageId) {
        Caller caller = authenticateCaller(token);
        conversationRepository.lockForUpdate(conversationId);
        try {
            Conversation conversation = loadConversation(conversationId);
            CallerSide reader = resolveCallerSide(caller, conversation);
            conversation.markMessageRead(messageId, reader.id());
            conversationRepository.save(conversation);
        } finally {
            conversationRepository.unlock(conversationId);
        }
    }



    /**
     * Terminal action — closes a conversation (no further messages allowed). The
     * caller must be a participant.
     *
     * @param token          the caller's token
     * @param conversationId the conversation to close
     * @throws UnauthorizedActionException   if the token is invalid or expired
     * @throws ConversationNotFoundException if the conversation does not exist
     * @throws InvalidParticipantException   if the caller is not a participant
     */
    @Transactional
    public void closeConversation(String token, String conversationId) {
        Caller caller = authenticateCaller(token);
        conversationRepository.lockForUpdate(conversationId);
        try {
            Conversation conversation = loadConversation(conversationId);
            resolveCallerSide(caller, conversation); // authorize: caller must be a participant
            conversation.transitionToClosed();
            conversationRepository.save(conversation);
        } finally {
            conversationRepository.unlock(conversationId);
        }
    }

    // ---------------------------------------------------------------------------
    // Read operations
    // ---------------------------------------------------------------------------

    /**
     * Member-facing Support Inbox: inquiries and complaints the member opened,
     * plus admin outreach, newest activity first.
     *
     * @param token the authenticated member's token
     * @return the member's conversations, from their viewpoint
     * @throws UnauthorizedActionException if the token is invalid or expired
     */
    @Transactional(readOnly = true)
    public List<ConversationDTO> viewMyConversations(String token) {
        int memberId = authenticate(token);
        return conversationRepository.findMemberInbox(memberId).stream()
                .sorted(Comparator.comparing(Conversation::getLastMessageAt).reversed())
                .map(c -> toDTO(c, memberId))
                .toList();
    }

    /**
     * Opens a single thread. The caller must be a participant.
     *
     * @param token          the caller's token
     * @param conversationId the conversation to open
     * @return the conversation from the caller's viewpoint
     * @throws UnauthorizedActionException   if the token is invalid or expired
     * @throws ConversationNotFoundException if the conversation does not exist
     * @throws InvalidParticipantException   if the caller is not a participant
     */
    @Transactional(readOnly = true)
    public ConversationDTO viewConversation(String token, String conversationId) {
        Caller caller = authenticateCaller(token);
        Conversation conversation = loadConversation(conversationId);
        CallerSide side = resolveCallerSide(caller, conversation); // throws if not a participant
        return toDTO(conversation, side.id());
    }

    /**
     * II.4.4 — company support inbox: all conversations where this company is the
     * counterparty, newest activity first. The caller must own or hold
     * {@code RESPOND_TO_INQUIRIES} on the company.
     *
     * @param token     the caller's token
     * @param companyId the company whose inbox to view
     * @return the company's conversations, from the company's viewpoint
     * @throws UnauthorizedActionException if the token is invalid or the caller
     *                                     does not act for the company
     */
    @Transactional(readOnly = true)
    public List<ConversationDTO> viewCompanyInbox(String token, int companyId) {
        int callerId = authenticate(token);
        if (!callerActsForCompany(callerId, companyId)) {
            throw new UnauthorizedActionException(
                    "view company inbox", callerId);
        }
        return conversationRepository.findByCompanyAsCounterparty(companyId).stream()
                .sorted(Comparator.comparing(Conversation::getLastMessageAt).reversed())
                .map(c -> toDTO(c, companyId))
                .toList();
    }

    /**
     * II.6.3.1 — admin queue of complaints, optionally filtered by status, member
     * and date range, newest activity first.
     *
     * @param token   the admin's token
     * @param filters the optional status/member/date filters (may be null)
     * @return matching complaints, from the admin's viewpoint
     * @throws UnauthorizedActionException if the token is not a valid admin token
     */
    @Transactional(readOnly = true)
    public List<ConversationDTO> viewAllComplaints(String token, ComplaintFilterDTO filters) {
        int adminId = requireSystemAdmin(token);
        ConversationStatus status = parseStatusOrNull(filters == null ? null : filters.status());
        List<Conversation> complaints = status != null
                ? conversationRepository.findByTypeAndStatus(ConversationType.COMPLAINT, status)
                : conversationRepository.findByType(ConversationType.COMPLAINT);

        return complaints.stream()
                .filter(c -> matchesComplaintFilter(c, filters))
                .sorted(Comparator.comparing(Conversation::getLastMessageAt).reversed())
                .map(c -> toDTO(c, adminId))
                .toList();
    }

    /**
     * II.6.3.2 — admin "sent history": every DIRECT conversation an admin
     * initiated, newest activity first. The fan-out creates one conversation per
     * recipient; callers group them into broadcasts.
     *
     * @param token the admin's token
     * @return the admin-initiated outreach conversations
     * @throws UnauthorizedActionException if the token is not a valid admin token
     */
    @Transactional(readOnly = true)
    public List<ConversationDTO> viewSentOutreach(String token) {
        int adminId = requireSystemAdmin(token);
        return conversationRepository.findByTypeAndInitiatorType(ConversationType.DIRECT, ParticipantType.ADMIN).stream()
                .sorted(Comparator.comparing(Conversation::getLastMessageAt).reversed())
                .map(c -> toDTO(c, adminId))
                .toList();
    }

    /**
     * Admin Inbox — the calling admin's own DIRECT conversations (outreach they
     * started), newest first, so they can chat and close each one. Scoped to this
     * admin (an exact id/type match), so it never includes another admin's
     * outreach or complaints.
     *
     * @param token the admin's token
     * @return this admin's own DIRECT conversations
     * @throws UnauthorizedActionException if the token is not a valid admin token
     */
    @Transactional(readOnly = true)
    public List<ConversationDTO> viewAdminInbox(String token) {
        int adminId = requireSystemAdmin(token);
        return conversationRepository.findByParticipant(adminId, ParticipantType.ADMIN).stream()
                .filter(c -> c.getType() == ConversationType.DIRECT)
                .sorted(Comparator.comparing(Conversation::getLastMessageAt).reversed())
                .map(c -> toDTO(c, adminId))
                .toList();
    }

    // ---------------------------------------------------------------------------
    // Authentication / authorization helpers
    // ---------------------------------------------------------------------------

    /**
     * @param token the token to validate
     * @return the authenticated user's id
     * @throws UnauthorizedActionException if the token is invalid or expired
     */
    private int authenticate(String token) {
        if (!sessionManager.validateToken(token)) {
            throw new UnauthorizedActionException("Invalid or expired token");
        }
        return sessionManager.extractUserId(token);
    }

    /**
     * Resolves the authenticated caller: their id plus whether their token carries
     * the ADMIN role claim. The claim — not {@code adminRepository} membership — is
     * the authoritative admin signal (see {@link #isAdmin}).
     *
     * @param token the token to validate
     * @return the caller's id and admin-token flag
     * @throws UnauthorizedActionException if the token is invalid or expired
     */
    private Caller authenticateCaller(String token) {
        return new Caller(authenticate(token), sessionManager.isAdminToken(token));
    }

    private record Caller(int id, boolean adminToken) {}

    /**
     * Requires the ADMIN role claim, not just {@code adminRepository} membership:
     * member and admin id pools overlap, so a member token whose id collides with
     * an admin's would otherwise pass this gate (mirrors
     * {@code SystemAdminService.requireSystemAdmin}).
     *
     * @param token the token to validate
     * @return the admin's user id
     * @throws UnauthorizedActionException if the token is not a valid admin token
     */
    private int requireSystemAdmin(String token) {
        int callerId = authenticate(token);
        // Require the ADMIN role claim, not just adminRepository membership: member and admin id
        // pools overlap, so a member token whose id collides with an admin's would otherwise pass
        // this gate (mirrors SystemAdminService.requireSystemAdmin).
        if (!sessionManager.isAdminToken(token) || !isAdmin(callerId)) {
            throw new UnauthorizedActionException("messaging admin operation", callerId);
        }
        return callerId;
    }

    /**
     * @param id the user id
     * @return {@code true} if an admin with that id exists
     */
    private boolean isAdmin(int id) {
        return adminRepository.findById(id) != null;
    }

    /**
     * Resolves which side of the conversation the caller represents — the single
     * source of truth for authorization, sender-stamping and read-tracking. Tries
     * admin, then member, then company-agent in order.
     *
     * @param caller       the authenticated caller
     * @param conversation the conversation being acted on
     * @return the caller's side (id + participant type)
     * @throws InvalidParticipantException if the caller is not a participant
     */
    private CallerSide resolveCallerSide(Caller caller, Conversation conversation) {
        int callerId = caller.id();
        // 1. Admin acting within an admin-involved thread (complaint queue or admin-authored).
        //    Requires an ADMIN-role token, not just adminRepository membership: the member/admin id
        //    pools overlap, so a colliding member token must not be treated as ADMIN here.
        if (caller.adminToken() && isAdmin(callerId) && involvesAdmin(conversation, callerId)) {
            return new CallerSide(callerId, ParticipantType.ADMIN);
        }
        // 2. Member acting as themselves.
        if (matchesMember(conversation, callerId)) {
            return new CallerSide(callerId, ParticipantType.MEMBER);
        }
        // 3. Company side — caller is an Owner of / holds RESPOND_TO_INQUIRIES on the company.
        Integer companyId = companyParticipantId(conversation);
        if (companyId != null && callerActsForCompany(callerId, companyId)) {
            return new CallerSide(companyId, ParticipantType.COMPANY);
        }
        throw new InvalidParticipantException(
                "Caller " + callerId + " is not a participant in conversation " + conversation.getConversationId());
    }

    /**
     * @param c        the conversation
     * @param callerId the candidate admin's id
     * @return {@code true} if the conversation involves the admin group or this
     *         specific admin as a party
     */
    private boolean involvesAdmin(Conversation c, int callerId) {
        return c.getInitiatorType() == ParticipantType.ADMIN_GROUP
                || c.getCounterpartyType() == ParticipantType.ADMIN_GROUP
                || (c.getInitiatorType() == ParticipantType.ADMIN && c.getInitiatorId() == callerId)
                || (c.getCounterpartyType() == ParticipantType.ADMIN && c.getCounterpartyId() == callerId);
    }

    /**
     * @param c        the conversation
     * @param callerId the candidate member's id
     * @return {@code true} if the caller is the member party of the conversation
     */
    private boolean matchesMember(Conversation c, int callerId) {
        return (c.getInitiatorType() == ParticipantType.MEMBER && c.getInitiatorId() == callerId)
                || (c.getCounterpartyType() == ParticipantType.MEMBER && c.getCounterpartyId() == callerId);
    }

    /**
     * @param c the conversation
     * @return the company party's id, or {@code null} if neither party is a company
     */
    private Integer companyParticipantId(Conversation c) {
        if (c.getInitiatorType() == ParticipantType.COMPANY) {
            return c.getInitiatorId();
        }
        if (c.getCounterpartyType() == ParticipantType.COMPANY) {
            return c.getCounterpartyId();
        }
        return null;
    }

    /**
     * @param callerId  the caller's user id
     * @param companyId the company in question
     * @return {@code true} if the caller is an owner of, or holds
     *         {@code RESPOND_TO_INQUIRIES} on, the company
     */
    private boolean callerActsForCompany(int callerId, int companyId) {
        try {
            User user = userRepository.getUserById(callerId);
            return user.isOwnerInCompany(companyId)
                    || user.hasPermissionInCompany(companyId, Permission.RESPOND_TO_INQUIRIES);
        } catch (UserNotFoundException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------------------
    // Notification bridge helpers
    // ---------------------------------------------------------------------------

    /**
     * Notifies whichever party did not send the message, dispatching by the other
     * party's type (member, company owners, or all admins).
     *
     * @param conversation the conversation that was just appended to
     * @param sender       the side that sent the message
     * @param body         the message body (snippeted into the notification)
     */
    private void notifyOtherParty(Conversation conversation, CallerSide sender, String body) {
        boolean senderIsInitiator = isInitiatorSide(conversation, sender);
        ParticipantType otherType = senderIsInitiator
                ? conversation.getCounterpartyType() : conversation.getInitiatorType();
        int otherId = senderIsInitiator
                ? conversation.getCounterpartyId() : conversation.getInitiatorId();
        String label = senderLabel(sender.type());

        switch (otherType) {
            case MEMBER -> safeNotify(otherId, conversation.getConversationId(), label,
                    conversation.getSubject(), body);
            case COMPANY -> {
                ProductionCompany company = tryGetCompany(otherId);
                if (company != null) {
                    notifyCompanyOwners(company, conversation, label, body);
                }
            }
            case ADMIN, ADMIN_GROUP -> notifyAllAdmins(conversation, label, body);
            default -> { /* SYSTEM — no single recipient to notify */ }
        }
    }

    /**
     * @param c    the conversation
     * @param side the side in question
     * @return {@code true} if {@code side} is the conversation's initiator (an
     *         ADMIN acting for the ADMIN_GROUP counts as the initiator when the
     *         group initiated)
     */
    private boolean isInitiatorSide(Conversation c, CallerSide side) {
        if (c.getInitiatorType() == side.type() && c.getInitiatorId() == side.id()) {
            return true;
        }
        // An ADMIN acting for the ADMIN_GROUP counts as the initiator when the group initiated.
        return side.type() == ParticipantType.ADMIN && c.getInitiatorType() == ParticipantType.ADMIN_GROUP;
    }

    /**
     * Notifies every owner of a company about new conversation activity.
     *
     * @param company      the company whose owners to notify
     * @param conversation the conversation the activity belongs to
     * @param senderLabel  a human-readable label for the sender
     * @param body         the message body (snippeted into the notification)
     */
    private void notifyCompanyOwners(ProductionCompany company, Conversation conversation,
            String senderLabel, String body) {
        for (Integer ownerId : company.getOwnerIds()) {
            if (ownerId != null) {
                safeNotify(ownerId, conversation.getConversationId(), senderLabel,
                        conversation.getSubject(), body);
            }
        }
    }

    /**
     * Notifies every system admin about new conversation activity.
     *
     * @param conversation the conversation the activity belongs to
     * @param senderLabel  a human-readable label for the sender
     * @param body         the message body (snippeted into the notification)
     */
    private void notifyAllAdmins(Conversation conversation, String senderLabel, String body) {
        for (Admin admin : adminRepository.findAll()) {
            safeNotify(admin.getId(), conversation.getConversationId(), senderLabel,
                    conversation.getSubject(), body);
        }
    }

    /**
     * Sends one notification, swallowing failures so a notification error never
     * undoes a persisted message (it is logged and execution continues).
     *
     * @param recipientUserId the recipient's user id
     * @param conversationId  the conversation the notification refers to
     * @param senderLabel     a human-readable label for the sender
     * @param subject         the conversation subject
     * @param body            the message body (snippeted into the notification)
     */
    private void safeNotify(int recipientUserId, String conversationId, String senderLabel,
            String subject, String body) {
        try {
            notificationService.notifyNewMessage(recipientUserId, conversationId, senderLabel,
                    subject, snippet(body));
        } catch (RuntimeException e) {
            log.warn("Message persisted but notification failed for recipient {} (conversation {})",
                    recipientUserId, conversationId, e);
        }
    }

    // ---------------------------------------------------------------------------
    // DTO mapping
    // ---------------------------------------------------------------------------

    /**
     * Maps a conversation to its DTO for the given viewer, resolving both parties'
     * display names.
     *
     * @param c        the conversation
     * @param viewerId the id of the party viewing it (drives read/unread framing)
     * @return the conversation DTO
     */
    private ConversationDTO toDTO(Conversation c, int viewerId) {
        return new ConversationMapper().toDTO(c, viewerId,
                resolveDisplayName(c.getInitiatorId(), c.getInitiatorType()),
                resolveDisplayName(c.getCounterpartyId(), c.getCounterpartyType()));
    }

    /**
     * Resolves a human-readable label for a conversation party: member username,
     * company name, or a fixed label for the admin side / system. Falls back to
     * "&lt;Kind&gt; #id" if the entity can't be loaded.
     *
     * @param id   the party's id
     * @param type the party's type
     * @return a display name for the party
     */
    private String resolveDisplayName(int id, ParticipantType type) {
        return switch (type) {
            case MEMBER -> {
                try { yield userRepository.getUserById(id).getUsername(); }
                catch (RuntimeException e) { yield "Member #" + id; }
            }
            case COMPANY -> {
                try { yield companyRepository.getCompanyById(id).getName(); }
                catch (RuntimeException e) { yield "Company #" + id; }
            }
            case ADMIN, ADMIN_GROUP -> "TicketHub Support";
            case SYSTEM -> "TicketHub";
        };
    }

    // ---------------------------------------------------------------------------
    // Misc helpers
    // ---------------------------------------------------------------------------

    /**
     * @param conversationId the conversation id
     * @return the conversation
     * @throws ConversationNotFoundException if no conversation with that id exists
     */
    private Conversation loadConversation(String conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
    }

    /**
     * Best-effort company lookup — returns {@code null} instead of throwing so a
     * missing company never aborts a notification fan-out (the repository throws
     * when the id is unknown).
     *
     * @param companyId the company id
     * @return the company, or {@code null} if it can't be loaded
     */
    private ProductionCompany tryGetCompany(int companyId) {
        try {
            return companyRepository.getCompanyById(companyId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * @param c       the complaint conversation to test
     * @param filters the optional member/date filters (null matches everything)
     * @return {@code true} if the complaint satisfies the supplied filters
     */
    private boolean matchesComplaintFilter(Conversation c, ComplaintFilterDTO filters) {
        if (filters == null) {
            return true;
        }
        if (filters.memberId() != null && c.getInitiatorId() != filters.memberId()) {
            return false;
        }
        if (filters.fromDate() != null && c.getCreatedAt().toLocalDate().isBefore(filters.fromDate())) {
            return false;
        }
        if (filters.toDate() != null && c.getCreatedAt().toLocalDate().isAfter(filters.toDate())) {
            return false;
        }
        return true;
    }

    /**
     * @param raw the raw status string (may be null/blank/unknown)
     * @return the parsed {@link ConversationStatus}, or {@code null} if absent or
     *         unrecognized (meaning "no status filter")
     */
    private ConversationStatus parseStatusOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ConversationStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * @param type the sender's participant type
     * @return a human-readable label for the sender used in notifications
     */
    private String senderLabel(ParticipantType type) {
        return switch (type) {
            case MEMBER -> "a member";
            case COMPANY -> "a production company";
            case ADMIN, ADMIN_GROUP -> "a system admin";
            case SYSTEM -> "the system";
        };
    }

    /**
     * @param body the message body (may be null)
     * @return the body truncated to {@code SNIPPET_MAX} characters (with an
     *         ellipsis) for use in a notification preview
     */
    private String snippet(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= SNIPPET_MAX ? body : body.substring(0, SNIPPET_MAX) + "…";
    }

    /**
     * @param list a possibly-null list
     * @param <T>  the element type
     * @return the list, or an empty list if it was {@code null}
     */
    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }





}
