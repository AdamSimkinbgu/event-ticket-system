package com.ticketing.system.Core.Application.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.ticketing.system.Core.Application.dto.AnnounceResultDTO;
import com.ticketing.system.Core.Application.dto.AnnouncementRequestDTO;
import com.ticketing.system.Core.Application.dto.ComplaintFilterDTO;
import com.ticketing.system.Core.Application.dto.ConversationDTO;
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

// Centralized messaging subsystem service.
// Replaces per-User MessageInbox, per-Company Inbox, and the standalone Complaint flow.
// Covers requirements II.3.3 (complaint), II.3.10 (contact company), II.4.4 (company support
// inbox), II.6.3.1 (admin complaint queue), II.6.3.2 (admin announcements).
//
// All write paths fire the messaging→notification bridge AFTER releasing the conversation
// lock: members are notified of admin/producer messages; company owners of new inquiries;
// admins of new complaints.
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

    // Which side of a conversation the authenticated caller is acting as.
    private record CallerSide(int id, ParticipantType type) {}

    // ---------------------------------------------------------------------------
    // Write operations
    // ---------------------------------------------------------------------------

    // II.3.10 — Member starts an INQUIRY conversation with a Company (or any DIRECT thread).
    public ConversationDTO startConversation(String token, StartConversationRequestDTO request) {
        int memberId = authenticate(token);
        int companyId = request.counterpartyId();
        ProductionCompany company = companyRepository.getCompanyById(companyId); // throws if missing

        ConversationType type = parseType(request.type(), ConversationType.INQUIRY);
        Conversation conversation = Conversation.start(
                type,
                memberId, ParticipantType.MEMBER,
                companyId, ParticipantType.COMPANY,
                request.subject(), request.firstMessageBody());
        conversationRepository.save(conversation);

        // Bridge — a member opened an inquiry; notify the company's owners.
        notifyCompanyOwners(company, conversation, senderLabel(ParticipantType.MEMBER), request.firstMessageBody());
        log.info("Conversation {} ({}) started by member {} to company {}",
                conversation.getConversationId(), type, memberId, companyId);
        return new ConversationMapper().toDTO(conversation, memberId);
    }



    // Append a reply to an existing conversation. Sender must be a participant.
    public void sendMessage(String token, SendMessageRequestDTO request) {
        Caller caller = authenticateCaller(token);
        String conversationId = request.conversationId();

        Conversation conversation;
        CallerSide sender;
        conversationRepository.lockForUpdate(conversationId);
        try {
            conversation = loadConversation(conversationId);
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



    // II.3.3 — Member submits a COMPLAINT (counterparty = ADMIN_GROUP).
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
        return new ConversationMapper().toDTO(conversation, memberId);
    }



    // II.6.3.1 — admin responds to a complaint and may transition status.
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
            conversation.addMessage(adminId, ParticipantType.ADMIN, request.body());
            applyNewStatus(conversation, request.newStatus());
            conversationRepository.save(conversation);
        } finally {
            conversationRepository.unlock(conversationId);
        }

        // Bridge — notify the member who filed the complaint.
        safeNotify(conversation.getInitiatorId(), conversationId,
                senderLabel(ParticipantType.ADMIN), conversation.getSubject(), request.body());
        log.info("Admin {} responded to complaint {} (newStatus={})", adminId, conversationId, request.newStatus());
    }

    // II.6.3.2 — admin outreach. Fans out to one ANNOUNCEMENT conversation per recipient
    // (a real two-party thread each), so read-tracking and the notification bridge stay uniform.
    public AnnounceResultDTO announce(String token, AnnouncementRequestDTO request) {
        int adminId = requireSystemAdmin(token);
        String audience = request.audienceType() == null ? "" : request.audienceType().trim().toUpperCase();

        List<Conversation> created = new ArrayList<>();
        switch (audience) {
            case "ALL_MEMBERS", "BROADCAST_MEMBERS" -> {
                for (User member : userRepository.findAll()) {
                    created.add(announceToMember(adminId, member.getUserId(), request));
                }
            }
            case "MEMBER_LIST" -> {
                for (Integer memberId : safeList(request.audienceMemberIds())) {
                    if (memberId != null) {
                        created.add(announceToMember(adminId, memberId, request));
                    }
                }
            }
            case "PRODUCERS" -> {
                List<Integer> companyIds = safeList(request.audienceCompanyIds());
                List<ProductionCompany> targets = companyIds.isEmpty()
                        ? companyRepository.findActive()
                        : companyIds.stream().filter(Objects::nonNull).map(this::tryGetCompany)
                                .filter(Objects::nonNull).toList();
                for (ProductionCompany company : targets) {
                    created.add(announceToCompany(adminId, company, request));
                }
            }
            default -> throw new IllegalArgumentException(
                    "Unknown announcement audienceType: " + request.audienceType());
        }

        if (created.isEmpty()) {
            throw new BusinessRuleViolationException("Announcement matched no recipients");
        }
        log.info("Admin {} announced to {} recipient(s) (audience={})", adminId, created.size(), audience);
        return new AnnounceResultDTO(created.size(), created.get(0).getConversationId());
    }



    // UI action — mark a single message as read by the viewer.
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



    // Terminal action — close a conversation (no further messages allowed).
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

    // Member-facing inbox view.
    public List<ConversationDTO> viewMyConversations(String token) {
        int memberId = authenticate(token);
        ConversationMapper mapper = new ConversationMapper();
        return conversationRepository.findByParticipant(memberId, ParticipantType.MEMBER).stream()
                .sorted(Comparator.comparing(Conversation::getLastMessageAt).reversed())
                .map(c -> mapper.toDTO(c, memberId))
                .toList();
    }

    // Open a single thread. Caller must be a participant.
    public ConversationDTO viewConversation(String token, String conversationId) {
        Caller caller = authenticateCaller(token);
        Conversation conversation = loadConversation(conversationId);
        CallerSide side = resolveCallerSide(caller, conversation); // throws if not a participant
        return new ConversationMapper().toDTO(conversation, side.id());
    }

    // II.4.4 — company support inbox: all conversations where this company is counterparty.
    public List<ConversationDTO> viewCompanyInbox(String token, int companyId) {
        int callerId = authenticate(token);
        if (!callerActsForCompany(callerId, companyId)) {
            throw new UnauthorizedActionException(
                    "view company inbox", callerId);
        }
        ConversationMapper mapper = new ConversationMapper();
        return conversationRepository.findByCompanyAsCounterparty(companyId).stream()
                .sorted(Comparator.comparing(Conversation::getLastMessageAt).reversed())
                .map(c -> mapper.toDTO(c, companyId))
                .toList();
    }

    // II.6.3.1 — admin queue of complaints with filters.
    public List<ConversationDTO> viewAllComplaints(String token, ComplaintFilterDTO filters) {
        int adminId = requireSystemAdmin(token);
        ConversationStatus status = parseStatusOrNull(filters == null ? null : filters.status());
        List<Conversation> complaints = status != null
                ? conversationRepository.findByTypeAndStatus(ConversationType.COMPLAINT, status)
                : conversationRepository.findByType(ConversationType.COMPLAINT);

        ConversationMapper mapper = new ConversationMapper();
        return complaints.stream()
                .filter(c -> matchesComplaintFilter(c, filters))
                .sorted(Comparator.comparing(Conversation::getLastMessageAt).reversed())
                .map(c -> mapper.toDTO(c, adminId))
                .toList();
    }

    // II.6.3.2 — admin "sent history": every ANNOUNCEMENT conversation across the platform.
    // The fan-out creates one conversation per recipient; callers group them into broadcasts.
    public List<ConversationDTO> viewSentAnnouncements(String token) {
        int adminId = requireSystemAdmin(token);
        ConversationMapper mapper = new ConversationMapper();
        return conversationRepository.findByType(ConversationType.ANNOUNCEMENT).stream()
                .sorted(Comparator.comparing(Conversation::getLastMessageAt).reversed())
                .map(c -> mapper.toDTO(c, adminId))
                .toList();
    }

    // ---------------------------------------------------------------------------
    // Authentication / authorization helpers
    // ---------------------------------------------------------------------------

    private int authenticate(String token) {
        if (!sessionManager.validateToken(token)) {
            throw new UnauthorizedActionException("Invalid or expired token");
        }
        return sessionManager.extractUserId(token);
    }

    // The authenticated caller: their id plus whether their token carries the ADMIN role claim.
    // The claim — not adminRepository membership — is the authoritative admin signal (see isAdmin).
    private Caller authenticateCaller(String token) {
        return new Caller(authenticate(token), sessionManager.isAdminToken(token));
    }

    private record Caller(int id, boolean adminToken) {}

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

    private boolean isAdmin(int id) {
        return adminRepository.findById(id) != null;
    }

    // Resolves which side of the conversation the caller represents — the single source of
    // truth for authorization, sender-stamping, and read-tracking.
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

    private boolean involvesAdmin(Conversation c, int callerId) {
        return c.getInitiatorType() == ParticipantType.ADMIN_GROUP
                || c.getCounterpartyType() == ParticipantType.ADMIN_GROUP
                || (c.getInitiatorType() == ParticipantType.ADMIN && c.getInitiatorId() == callerId)
                || (c.getCounterpartyType() == ParticipantType.ADMIN && c.getCounterpartyId() == callerId);
    }

    private boolean matchesMember(Conversation c, int callerId) {
        return (c.getInitiatorType() == ParticipantType.MEMBER && c.getInitiatorId() == callerId)
                || (c.getCounterpartyType() == ParticipantType.MEMBER && c.getCounterpartyId() == callerId);
    }

    private Integer companyParticipantId(Conversation c) {
        if (c.getInitiatorType() == ParticipantType.COMPANY) {
            return c.getInitiatorId();
        }
        if (c.getCounterpartyType() == ParticipantType.COMPANY) {
            return c.getCounterpartyId();
        }
        return null;
    }

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
            default -> { /* BROADCAST_MEMBERS / SYSTEM — no single recipient to notify */ }
        }
    }

    private boolean isInitiatorSide(Conversation c, CallerSide side) {
        if (c.getInitiatorType() == side.type() && c.getInitiatorId() == side.id()) {
            return true;
        }
        // An ADMIN acting for the ADMIN_GROUP counts as the initiator when the group initiated.
        return side.type() == ParticipantType.ADMIN && c.getInitiatorType() == ParticipantType.ADMIN_GROUP;
    }

    private void notifyCompanyOwners(ProductionCompany company, Conversation conversation,
            String senderLabel, String body) {
        for (Integer ownerId : company.getOwnerIds()) {
            if (ownerId != null) {
                safeNotify(ownerId, conversation.getConversationId(), senderLabel,
                        conversation.getSubject(), body);
            }
        }
    }

    private void notifyAllAdmins(Conversation conversation, String senderLabel, String body) {
        for (Admin admin : adminRepository.findAll()) {
            safeNotify(admin.getId(), conversation.getConversationId(), senderLabel,
                    conversation.getSubject(), body);
        }
    }

    // Never let a notification failure undo a persisted message — log and continue.
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
    // Announce fan-out helpers
    // ---------------------------------------------------------------------------

    private Conversation announceToMember(int adminId, int memberId, AnnouncementRequestDTO request) {
        Conversation conversation = Conversation.start(
                ConversationType.ANNOUNCEMENT,
                adminId, ParticipantType.ADMIN,
                memberId, ParticipantType.MEMBER,
                request.subject(), request.body());
        conversationRepository.save(conversation);
        safeNotify(memberId, conversation.getConversationId(), senderLabel(ParticipantType.ADMIN),
                request.subject(), request.body());
        return conversation;
    }

    private Conversation announceToCompany(int adminId, ProductionCompany company, AnnouncementRequestDTO request) {
        Conversation conversation = Conversation.start(
                ConversationType.ANNOUNCEMENT,
                adminId, ParticipantType.ADMIN,
                company.getCompanyId(), ParticipantType.COMPANY,
                request.subject(), request.body());
        conversationRepository.save(conversation);
        notifyCompanyOwners(company, conversation, senderLabel(ParticipantType.ADMIN), request.body());
        return conversation;
    }

    // ---------------------------------------------------------------------------
    // Misc helpers
    // ---------------------------------------------------------------------------

    private Conversation loadConversation(String conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
    }

    // Best-effort company lookup — returns null instead of throwing so a missing company
    // never aborts a notification fan-out (the repository throws when the id is unknown).
    private ProductionCompany tryGetCompany(int companyId) {
        try {
            return companyRepository.getCompanyById(companyId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void applyNewStatus(Conversation conversation, String newStatus) {
        if (newStatus == null || newStatus.isBlank()) {
            return;
        }
        ConversationStatus target;
        try {
            target = ConversationStatus.valueOf(newStatus.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown conversation status: " + newStatus);
        }
        switch (target) {
            case RESOLVED -> conversation.transitionToResolved();
            case CLOSED -> conversation.transitionToClosed();
            // RESPONDED is already set by addMessage; OPEN needs no explicit move.
            case RESPONDED, OPEN -> { /* no-op */ }
        }
    }

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

    private ConversationType parseType(String raw, ConversationType fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return ConversationType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

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

    private String senderLabel(ParticipantType type) {
        return switch (type) {
            case MEMBER -> "a member";
            case COMPANY -> "a production company";
            case ADMIN, ADMIN_GROUP -> "a system admin";
            case SYSTEM -> "the system";
            case BROADCAST_MEMBERS -> "the platform";
        };
    }

    private String snippet(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= SNIPPET_MAX ? body : body.substring(0, SNIPPET_MAX) + "…";
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }





}
