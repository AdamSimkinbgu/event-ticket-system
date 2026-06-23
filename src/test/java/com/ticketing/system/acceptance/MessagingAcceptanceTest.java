package com.ticketing.system.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.ticketing.system.Core.Application.dto.AnnouncementRequestDTO;
import com.ticketing.system.Core.Application.dto.AuthTokenDTO;
import com.ticketing.system.Core.Application.dto.CompanyRegistrationDTO;
import com.ticketing.system.Core.Application.dto.ConversationDTO;
import com.ticketing.system.Core.Application.dto.LoginRequestDTO;
import com.ticketing.system.Core.Application.dto.RegisterRequestDTO;
import com.ticketing.system.Core.Application.dto.RespondToComplaintRequestDTO;
import com.ticketing.system.Core.Application.dto.SendMessageRequestDTO;
import com.ticketing.system.Core.Application.dto.StartConversationRequestDTO;
import com.ticketing.system.Core.Application.dto.SubmitComplaintRequestDTO;
import com.ticketing.system.Core.Application.interfaces.IPasswordHasher;
import com.ticketing.system.Core.Application.services.AuthenticationService;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Application.services.MessagingService;
import com.ticketing.system.Core.Domain.Admin.Admin;
import com.ticketing.system.Core.Domain.Admin.IAdminRepository;
import com.ticketing.system.Core.Domain.exceptions.ConversationClosedException;
import com.ticketing.system.Core.Domain.exceptions.UnauthorizedActionException;

// End-to-end acceptance tests for the messaging subsystem, exercised through the real
// Spring "test" context (AuthenticationService → CompanyManagementService → MessagingService).
//
// Requirement coverage:
//   II.3.10  — member contacts a company; company replies
//   II.3.3   — member submits a complaint (+ empty-body rejection)
//   II.6.3.1 — admin complaint queue (view all, respond+resolve, non-admin rejected)
//   II.4.4   — company support inbox
//   II.6.3.2 — admin announcement broadcast to all members
//   cross    — closed-thread guard, participant-scoped inbox
//
// The "test" context is shared across methods, so every persona/company uses a unique name and
// assertions are written to tolerate co-resident data (membership by id, not absolute totals).
@SpringBootTest
@ActiveProfiles("test")
class MessagingAcceptanceTest {

    @Autowired private AuthenticationService authService;
    @Autowired private CompanyManagementService companyService;
    @Autowired private MessagingService messagingService;
    @Autowired private IAdminRepository adminRepository;
    @Autowired private IPasswordHasher passwordHasher;

    // II.3.10 — Member contacts Company
    @Test
    void GivenMember_WhenStartInquiryToCompany_ThenConversationOpen() {
        AuthTokenDTO member = registerMember("inq.member.a");
        int companyId = registerCompany("inq.owner.a", "InquiryCoA");

        ConversationDTO dto = messagingService.startConversation(member.token(),
                inquiry(member, companyId, "Parking?", "Is there parking near the venue?"));

        assertEquals("INQUIRY", dto.type());
        assertEquals("OPEN", dto.status());
        assertEquals(member.userId(), dto.initiatorId());
        assertEquals(companyId, dto.counterpartyId());
        assertEquals("COMPANY", dto.counterpartyType());
        assertEquals(1, dto.messages().size());

        // The thread is durably retrievable by its initiator.
        assertEquals(dto.conversationId(),
                messagingService.viewConversation(member.token(), dto.conversationId()).conversationId());
    }

    @Test
    void GivenInquiry_WhenCompanyReplies_ThenResponded() {
        AuthTokenDTO member = registerMember("inq.member.b");
        AuthTokenDTO owner = registerMember("inq.owner.b");
        int companyId = registerCompanyAs(owner, "InquiryCoB");

        ConversationDTO created = messagingService.startConversation(member.token(),
                inquiry(member, companyId, "Parking?", "Any parking?"));

        messagingService.sendMessage(owner.token(),
                new SendMessageRequestDTO(created.conversationId(), 0, null, "Yes — a paid lot next door."));

        ConversationDTO reloaded = messagingService.viewConversation(owner.token(), created.conversationId());
        assertEquals("RESPONDED", reloaded.status());
        assertEquals(2, reloaded.messages().size());
    }

    // II.3.3 — Submit Complaint
    @Test
    void GivenMember_WhenSubmitComplaint_ThenComplaintConversationCreated() {
        AuthTokenDTO member = registerMember("cmp.member.a");

        ConversationDTO dto = messagingService.submitComplaint(member.token(),
                new SubmitComplaintRequestDTO(member.userId(), "Integrity", "An event was misrepresented.", null));

        assertEquals("COMPLAINT", dto.type());
        assertEquals("OPEN", dto.status());
        assertEquals(member.userId(), dto.initiatorId());
        assertEquals("ADMIN_GROUP", dto.counterpartyType());
        assertEquals(1, dto.messages().size());
    }

    @Test
    void GivenEmptyBody_WhenSubmitComplaint_ThenRejected() {
        AuthTokenDTO member = registerMember("cmp.member.empty");

        // A blank body fails the Message construction invariant in the domain.
        assertThrows(IllegalStateException.class,
                () -> messagingService.submitComplaint(member.token(),
                        new SubmitComplaintRequestDTO(member.userId(), "No body", "   ", null)));
    }

    // II.6.3.1 — Admin manages complaints
    @Test
    void GivenAdmin_WhenViewAllComplaints_ThenAllListed() {
        AuthTokenDTO admin = registerAdmin("queue.admin.a");
        AuthTokenDTO member = registerMember("queue.member.a");

        ConversationDTO complaint = messagingService.submitComplaint(member.token(),
                new SubmitComplaintRequestDTO(member.userId(), "Listed?", "Please review.", null));

        List<ConversationDTO> queue = messagingService.viewAllComplaints(admin.token(), null);
        assertTrue(containsConversation(queue, complaint.conversationId()),
                "admin complaint queue must include the freshly filed complaint");
        assertTrue(queue.stream().allMatch(c -> "COMPLAINT".equals(c.type())),
                "the complaint queue must contain only complaints");
    }

    @Test
    void GivenComplaint_WhenAdminResolves_ThenResolvedTerminal() {
        AuthTokenDTO admin = registerAdmin("resolve.admin.a");
        AuthTokenDTO member = registerMember("resolve.member.a");

        ConversationDTO complaint = messagingService.submitComplaint(member.token(),
                new SubmitComplaintRequestDTO(member.userId(), "Refund", "Where is my refund?", null));

        messagingService.respondToComplaint(admin.token(), new RespondToComplaintRequestDTO(
                complaint.conversationId(), admin.userId(), "Resolved — refund issued.", "RESOLVED"));

        ConversationDTO reloaded = messagingService.viewConversation(admin.token(), complaint.conversationId());
        assertEquals("RESOLVED", reloaded.status());
        assertEquals(2, reloaded.messages().size());

        // RESOLVED is terminal — no further replies accepted.
        assertThrows(ConversationClosedException.class,
                () -> messagingService.sendMessage(member.token(),
                        new SendMessageRequestDTO(complaint.conversationId(), 0, null, "still here?")));
    }

    @Test
    void GivenNonAdmin_WhenViewAllComplaints_ThenRejected() {
        AuthTokenDTO member = registerMember("queue.nonadmin.a");
        assertThrows(UnauthorizedActionException.class,
                () -> messagingService.viewAllComplaints(member.token(), null));
    }

    // II.4.4 — Company support inbox
    @Test
    void GivenOwner_WhenViewCompanyInbox_ThenCompanyConversations() {
        AuthTokenDTO member = registerMember("inbox.member.a");
        AuthTokenDTO owner = registerMember("inbox.owner.a");
        int companyId = registerCompanyAs(owner, "InboxCoA");

        ConversationDTO inquiry = messagingService.startConversation(member.token(),
                inquiry(member, companyId, "Seating", "Are seats numbered?"));

        List<ConversationDTO> inbox = messagingService.viewCompanyInbox(owner.token(), companyId);
        // The company id is unique to this test, so the inbox holds exactly this one thread.
        assertEquals(1, inbox.size());
        assertEquals(inquiry.conversationId(), inbox.get(0).conversationId());
        assertEquals(companyId, inbox.get(0).counterpartyId());

        // A non-owner cannot read the company support inbox.
        AuthTokenDTO outsider = registerMember("inbox.outsider.a");
        assertThrows(UnauthorizedActionException.class,
                () -> messagingService.viewCompanyInbox(outsider.token(), companyId));
    }

    // II.6.3.2 — Admin announcements
    @Test
    void GivenAdmin_WhenAnnounce_ThenAllMembersReceive() {
        AuthTokenDTO admin = registerAdmin("ann.admin.a");
        AuthTokenDTO m1 = registerMember("ann.member.1");
        AuthTokenDTO m2 = registerMember("ann.member.2");
        String subject = "System maintenance window";

        messagingService.announce(admin.token(), new AnnouncementRequestDTO(
                admin.userId(), subject, "We will be down 02:00–03:00.", "ALL_MEMBERS", List.of(), List.of()));

        // Both members must find the broadcast in their own inboxes.
        assertTrue(hasAnnouncement(m1, subject), "member 1 must receive the broadcast");
        assertTrue(hasAnnouncement(m2, subject), "member 2 must receive the broadcast");
    }

    // Cross-flow
    @Test
    void GivenClosedConversation_WhenSendMessage_ThenRejected() {
        AuthTokenDTO member = registerMember("close.member.a");
        AuthTokenDTO owner = registerMember("close.owner.a");
        int companyId = registerCompanyAs(owner, "CloseCoA");

        ConversationDTO created = messagingService.startConversation(member.token(),
                inquiry(member, companyId, "Hi", "Question."));

        messagingService.closeConversation(member.token(), created.conversationId());

        assertThrows(ConversationClosedException.class,
                () -> messagingService.sendMessage(owner.token(),
                        new SendMessageRequestDTO(created.conversationId(), 0, null, "too late")));
    }

    @Test
    void GivenMember_WhenViewMyConversations_ThenScopedCorrectly() {
        AuthTokenDTO mine = registerMember("scope.member.mine");
        AuthTokenDTO other = registerMember("scope.member.other");
        int companyId = registerCompany("scope.owner.a", "ScopeCoA");

        ConversationDTO myThread = messagingService.startConversation(mine.token(),
                inquiry(mine, companyId, "Mine", "My question."));
        ConversationDTO otherThread = messagingService.startConversation(other.token(),
                inquiry(other, companyId, "Theirs", "Their question."));

        List<ConversationDTO> mineList = messagingService.viewMyConversations(mine.token());
        assertTrue(containsConversation(mineList, myThread.conversationId()),
                "my inbox must contain my own thread");
        assertFalse(containsConversation(mineList, otherThread.conversationId()),
                "my inbox must not leak another member's thread");
        assertTrue(mineList.stream().allMatch(c -> c.initiatorId() == mine.userId()),
                "every thread in my inbox is one I initiated");
    }

    // --- helpers ---

    private AuthTokenDTO registerMember(String name) {
        String sid = authService.startGuestSession().sessionId();
        authService.register(new RegisterRequestDTO(name, name + "@test.com", "Password1", sid, 20));
        return authService.login(new LoginRequestDTO(name, "Password1", sid)).authToken();
    }

    // Registers a member, promotes them to a real system Admin (hashed password), then signs in via
    // the admin pool so the returned token carries the ADMIN role claim the messaging gate requires
    // (mirrors the dev-seed promotion). A plain member token would now be rejected — the very
    // shared-id-space gap this exercises.
    private AuthTokenDTO registerAdmin(String name) {
        AuthTokenDTO member = registerMember(name);
        adminRepository.save(new Admin(member.userId(), name, passwordHasher.hash("Password1"), false));
        return authService.signInAsAdmin(name, "Password1");
    }

    private int registerCompany(String ownerName, String companyName) {
        return registerCompanyAs(registerMember(ownerName), companyName);
    }

    private int registerCompanyAs(AuthTokenDTO owner, String companyName) {
        return companyService.registerCompany(owner.token(),
                new CompanyRegistrationDTO(companyName, "desc")).companyId();
    }

    private StartConversationRequestDTO inquiry(AuthTokenDTO member, int companyId, String subject, String body) {
        return new StartConversationRequestDTO(member.userId(), "MEMBER", companyId, "COMPANY",
                "INQUIRY", subject, body);
    }

    private boolean hasAnnouncement(AuthTokenDTO member, String subject) {
        return messagingService.viewMyConversations(member.token()).stream()
                .anyMatch(c -> "ANNOUNCEMENT".equals(c.type()) && subject.equals(c.subject()));
    }

    private static boolean containsConversation(List<ConversationDTO> list, String conversationId) {
        return list.stream().anyMatch(c -> conversationId.equals(c.conversationId()));
    }
}
