package com.ticketing.system.unit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Application.dto.AnnouncementRequestDTO;
import com.ticketing.system.Core.Application.dto.ComplaintFilterDTO;
import com.ticketing.system.Core.Application.dto.ConversationDTO;
import com.ticketing.system.Core.Application.dto.RespondToComplaintRequestDTO;
import com.ticketing.system.Core.Application.dto.SendMessageRequestDTO;
import com.ticketing.system.Core.Application.dto.StartConversationRequestDTO;
import com.ticketing.system.Core.Application.dto.SubmitComplaintRequestDTO;
import com.ticketing.system.Core.Application.interfaces.INotificationService;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.services.MessagingService;
import com.ticketing.system.Core.Domain.Admin.Admin;
import com.ticketing.system.Core.Domain.Admin.IAdminRepository;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.exceptions.ConversationClosedException;
import com.ticketing.system.Core.Domain.exceptions.InvalidParticipantException;
import com.ticketing.system.Core.Domain.exceptions.UnauthorizedActionException;
import com.ticketing.system.Core.Domain.messaging.ConversationStatus;
import com.ticketing.system.Core.Domain.messaging.ConversationType;
import com.ticketing.system.Core.Domain.messaging.IConversationRepository;
import com.ticketing.system.Core.Domain.messaging.ParticipantType;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.User;
import com.ticketing.system.Infrastructure.persistence.MemoryConversationRepository;

class MessagingServiceTest {

    private static final String MEMBER_TOKEN = "member-tok";
    private static final int MEMBER_ID = 10;
    private static final String MEMBER2_TOKEN = "member2-tok";
    private static final int MEMBER2_ID = 11;
    private static final String OWNER_TOKEN = "owner-tok";
    private static final int OWNER_ID = 20;
    private static final String OUTSIDER_TOKEN = "outsider-tok";
    private static final int OUTSIDER_ID = 99;
    private static final String ADMIN_TOKEN = "admin-tok";
    private static final int ADMIN_ID = 1;
    private static final int COMPANY_ID = 5;

    private IConversationRepository conversationRepository;
    private ISessionManager sessionManager;
    private IAdminRepository adminRepository;
    private IUserRepository userRepository;
    private IProductionCompanyRepository companyRepository;
    private INotificationService notificationService;
    private MessagingService service;

    @BeforeEach
    void setUp() {
        conversationRepository = new MemoryConversationRepository(); // real in-memory repo
        sessionManager = mock(ISessionManager.class);
        adminRepository = mock(IAdminRepository.class);
        userRepository = mock(IUserRepository.class);
        companyRepository = mock(IProductionCompanyRepository.class);
        notificationService = mock(INotificationService.class);
        service = new MessagingService(conversationRepository, sessionManager, adminRepository,
                userRepository, companyRepository, notificationService);

        stubToken(MEMBER_TOKEN, MEMBER_ID);
        stubToken(MEMBER2_TOKEN, MEMBER2_ID);
        stubToken(OWNER_TOKEN, OWNER_ID);
        stubToken(OUTSIDER_TOKEN, OUTSIDER_ID);
        stubToken(ADMIN_TOKEN, ADMIN_ID);

        when(adminRepository.findById(ADMIN_ID)).thenReturn(admin());
        when(adminRepository.findAll()).thenReturn(List.of(admin()));

        // Build mocks into locals first — passing a mock that is itself being stubbed directly
        // into thenReturn(...) trips Mockito's "unfinished stubbing" guard.
        ProductionCompany company = companyMock(COMPANY_ID, OWNER_ID);
        when(companyRepository.getCompanyById(COMPANY_ID)).thenReturn(company);

        User owner = ownerUser(OWNER_ID, COMPANY_ID);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(owner);

        User outsider = plainUser(OUTSIDER_ID);
        when(userRepository.getUserById(OUTSIDER_ID)).thenReturn(outsider);

        User member = plainUser(MEMBER_ID);
        when(userRepository.getUserById(MEMBER_ID)).thenReturn(member);
    }

    // --- startConversation (II.3.10) ---

    @Test
    void startConversation_createsInquiry_andNotifiesCompanyOwners() {
        ConversationDTO dto = service.startConversation(MEMBER_TOKEN, inquiryReq());

        assertEquals("INQUIRY", dto.type());
        assertEquals(MEMBER_ID, dto.initiatorId());
        assertEquals(COMPANY_ID, dto.counterpartyId());
        assertEquals(1, dto.messages().size());
        assertTrue(conversationRepository.findById(dto.conversationId()).isPresent());
        verify(notificationService).notifyNewMessage(eq(OWNER_ID), eq(dto.conversationId()),
                anyString(), eq("Parking?"), anyString());
    }

    // --- sendMessage ---

    @Test
    void sendMessage_byCompanyOwner_appends_transitionsResponded_andNotifiesMember() {
        ConversationDTO created = service.startConversation(MEMBER_TOKEN, inquiryReq());

        service.sendMessage(OWNER_TOKEN,
                new SendMessageRequestDTO(created.conversationId(), 0, null, "Yes, paid lot."));

        var conv = conversationRepository.findById(created.conversationId()).orElseThrow();
        assertEquals(2, conv.getMessages().size());
        assertEquals(ConversationStatus.RESPONDED, conv.getStatus());
        // The notification bridge: the member is notified of the producer's reply.
        verify(notificationService).notifyNewMessage(eq(MEMBER_ID), eq(created.conversationId()),
                anyString(), anyString(), anyString());
    }

    @Test
    void sendMessage_byNonParticipant_rejected() {
        ConversationDTO created = service.startConversation(MEMBER_TOKEN, inquiryReq());
        assertThrows(InvalidParticipantException.class,
                () -> service.sendMessage(OUTSIDER_TOKEN,
                        new SendMessageRequestDTO(created.conversationId(), 0, null, "hi")));
    }

    // --- submitComplaint (II.3.3) ---

    @Test
    void submitComplaint_createsComplaintToAdminGroup_andNotifiesAdmins() {
        ConversationDTO dto = service.submitComplaint(MEMBER_TOKEN,
                new SubmitComplaintRequestDTO(MEMBER_ID, "Refund", "Where is it?", null));

        assertEquals("COMPLAINT", dto.type());
        assertEquals("ADMIN_GROUP", dto.counterpartyType());
        verify(notificationService).notifyNewMessage(eq(ADMIN_ID), eq(dto.conversationId()),
                anyString(), eq("Refund"), anyString());
    }

    // --- respondToComplaint (II.6.3.1) ---

    @Test
    void respondToComplaint_byAdmin_transitionsResponded_andNotifiesMember() {
        ConversationDTO complaint = service.submitComplaint(MEMBER_TOKEN,
                new SubmitComplaintRequestDTO(MEMBER_ID, "Refund", "Where is it?", null));

        service.respondToComplaint(ADMIN_TOKEN, new RespondToComplaintRequestDTO(
                complaint.conversationId(), ADMIN_ID, "Looking into it", "RESPONDED"));

        var conv = conversationRepository.findById(complaint.conversationId()).orElseThrow();
        assertEquals(ConversationStatus.RESPONDED, conv.getStatus());
        assertEquals(2, conv.getMessages().size());
        verify(notificationService).notifyNewMessage(eq(MEMBER_ID), eq(complaint.conversationId()),
                anyString(), anyString(), anyString());
    }

    @Test
    void respondToComplaint_withResolved_locksFurtherReplies() {
        ConversationDTO complaint = service.submitComplaint(MEMBER_TOKEN,
                new SubmitComplaintRequestDTO(MEMBER_ID, "Refund", "Where is it?", null));

        service.respondToComplaint(ADMIN_TOKEN, new RespondToComplaintRequestDTO(
                complaint.conversationId(), ADMIN_ID, "Resolved now", "RESOLVED"));

        var conv = conversationRepository.findById(complaint.conversationId()).orElseThrow();
        assertEquals(ConversationStatus.RESOLVED, conv.getStatus());
        assertThrows(ConversationClosedException.class,
                () -> service.sendMessage(MEMBER_TOKEN,
                        new SendMessageRequestDTO(complaint.conversationId(), 0, null, "still here")));
    }

    @Test
    void respondToComplaint_byNonAdmin_rejected() {
        assertThrows(UnauthorizedActionException.class,
                () -> service.respondToComplaint(MEMBER_TOKEN,
                        new RespondToComplaintRequestDTO("any-id", MEMBER_ID, "hi", "RESPONDED")));
    }

    // --- announce (II.6.3.2) ---

    @Test
    void announce_allMembers_fansOut_andNotifiesEach() {
        User u10 = plainUser(MEMBER_ID);
        User u11 = plainUser(MEMBER2_ID);
        when(userRepository.findAll()).thenReturn(List.of(u10, u11));

        service.announce(ADMIN_TOKEN, new AnnouncementRequestDTO(
                ADMIN_ID, "Notice", "Body", "ALL_MEMBERS", List.of(), List.of()));

        assertEquals(2, conversationRepository.findByType(ConversationType.ANNOUNCEMENT).size());
        verify(notificationService).notifyNewMessage(eq(MEMBER_ID), anyString(), anyString(), eq("Notice"), anyString());
        verify(notificationService).notifyNewMessage(eq(MEMBER2_ID), anyString(), anyString(), eq("Notice"), anyString());
    }

    @Test
    void announce_memberList_targetsOnlyListed() {
        service.announce(ADMIN_TOKEN, new AnnouncementRequestDTO(
                ADMIN_ID, "Notice", "Body", "MEMBER_LIST", List.of(MEMBER2_ID), List.of()));

        assertEquals(1, conversationRepository.findByType(ConversationType.ANNOUNCEMENT).size());
        verify(notificationService).notifyNewMessage(eq(MEMBER2_ID), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void announce_producers_targetsCompanyAndNotifiesOwners() {
        service.announce(ADMIN_TOKEN, new AnnouncementRequestDTO(
                ADMIN_ID, "Notice", "Body", "PRODUCERS", List.of(), List.of(COMPANY_ID)));

        var convs = conversationRepository.findByType(ConversationType.ANNOUNCEMENT);
        assertEquals(1, convs.size());
        assertEquals(ParticipantType.COMPANY, convs.get(0).getCounterpartyType());
        verify(notificationService).notifyNewMessage(eq(OWNER_ID), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void announce_byNonAdmin_rejected() {
        assertThrows(UnauthorizedActionException.class,
                () -> service.announce(MEMBER_TOKEN, new AnnouncementRequestDTO(
                        MEMBER_ID, "x", "y", "ALL_MEMBERS", List.of(), List.of())));
    }

    // --- views ---

    @Test
    void viewMyConversations_returnsOnlyParticipantScoped() {
        service.startConversation(MEMBER_TOKEN, inquiryReq());                 // member 10 <-> company 5
        service.announce(ADMIN_TOKEN, new AnnouncementRequestDTO(
                ADMIN_ID, "x", "y", "MEMBER_LIST", List.of(MEMBER2_ID), List.of())); // admin -> member 11

        List<ConversationDTO> mine = service.viewMyConversations(MEMBER_TOKEN);
        assertEquals(1, mine.size());
        assertEquals("INQUIRY", mine.get(0).type());
    }

    @Test
    void viewConversation_byParticipant_returnsThread_andOutsiderRejected() {
        ConversationDTO created = service.startConversation(MEMBER_TOKEN, inquiryReq());

        ConversationDTO viewed = service.viewConversation(MEMBER_TOKEN, created.conversationId());
        assertEquals(created.conversationId(), viewed.conversationId());

        assertThrows(InvalidParticipantException.class,
                () -> service.viewConversation(OUTSIDER_TOKEN, created.conversationId()));
    }

    @Test
    void viewCompanyInbox_returnsCounterpartyConversations_andRejectsNonOwner() {
        service.startConversation(MEMBER_TOKEN, inquiryReq());

        assertEquals(1, service.viewCompanyInbox(OWNER_TOKEN, COMPANY_ID).size());
        assertThrows(UnauthorizedActionException.class,
                () -> service.viewCompanyInbox(OUTSIDER_TOKEN, COMPANY_ID));
    }

    @Test
    void viewAllComplaints_adminOnly_andFiltersByMember() {
        service.submitComplaint(MEMBER_TOKEN, new SubmitComplaintRequestDTO(MEMBER_ID, "A", "a", null));
        service.submitComplaint(MEMBER2_TOKEN, new SubmitComplaintRequestDTO(MEMBER2_ID, "B", "b", null));

        assertThrows(UnauthorizedActionException.class,
                () -> service.viewAllComplaints(MEMBER_TOKEN, null));

        assertEquals(2, service.viewAllComplaints(ADMIN_TOKEN, null).size());

        List<ConversationDTO> filtered = service.viewAllComplaints(ADMIN_TOKEN,
                new ComplaintFilterDTO(null, MEMBER_ID, null, null));
        assertEquals(1, filtered.size());
        assertEquals(MEMBER_ID, filtered.get(0).initiatorId());
    }

    // --- markMessageAsRead / closeConversation ---

    @Test
    void markMessageAsRead_flipsReadFlag() {
        ConversationDTO created = service.startConversation(MEMBER_TOKEN, inquiryReq());
        String msgId = created.messages().get(0).messageId();

        service.markMessageAsRead(OWNER_TOKEN, created.conversationId(), msgId);

        var conv = conversationRepository.findById(created.conversationId()).orElseThrow();
        assertTrue(conv.getMessages().get(0).isRead());
    }

    @Test
    void closeConversation_blocksFurtherMessages() {
        ConversationDTO created = service.startConversation(MEMBER_TOKEN, inquiryReq());

        service.closeConversation(MEMBER_TOKEN, created.conversationId());

        assertThrows(ConversationClosedException.class,
                () -> service.sendMessage(OWNER_TOKEN,
                        new SendMessageRequestDTO(created.conversationId(), 0, null, "hi")));
    }

    // --- helpers ---

    private void stubToken(String token, int id) {
        when(sessionManager.validateToken(token)).thenReturn(true);
        when(sessionManager.extractUserId(token)).thenReturn(id);
    }

    private StartConversationRequestDTO inquiryReq() {
        return new StartConversationRequestDTO(MEMBER_ID, "MEMBER", COMPANY_ID, "COMPANY",
                "INQUIRY", "Parking?", "Is there parking near the venue?");
    }

    private Admin admin() {
        return new Admin(ADMIN_ID, "admin", "hash", true);
    }

    private ProductionCompany companyMock(int companyId, int... ownerIds) {
        ProductionCompany c = mock(ProductionCompany.class);
        when(c.getCompanyId()).thenReturn(companyId);
        List<Integer> owners = new ArrayList<>();
        for (int o : ownerIds) {
            owners.add(o);
        }
        when(c.getOwnerIds()).thenReturn(owners);
        return c;
    }

    private User ownerUser(int userId, int companyId) {
        User u = mock(User.class);
        when(u.getUserId()).thenReturn(userId);
        when(u.isOwnerInCompany(companyId)).thenReturn(true);
        return u;
    }

    private User plainUser(int userId) {
        User u = mock(User.class);
        when(u.getUserId()).thenReturn(userId);
        return u;
    }
}
