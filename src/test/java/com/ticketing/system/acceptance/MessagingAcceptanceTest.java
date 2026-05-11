package com.ticketing.system.acceptance;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MessagingAcceptanceTest {

    // II.3.10 — Member contacts Company
    @Test @Disabled("II.3.10 main: Member starts inquiry to Company")
    void GivenMember_WhenStartInquiryToCompany_ThenConversationOpen() {}
    @Test @Disabled("II.3.10 alt: Company replies — status RESPONDED")
    void GivenInquiry_WhenCompanyReplies_ThenResponded() {}

    // II.3.3 — Submit Complaint
    @Test @Disabled("II.3.3 main: Member submits complaint")
    void GivenMember_WhenSubmitComplaint_ThenComplaintConversationCreated() {}
    @Test @Disabled("II.3.3 negative: empty body rejected")
    void GivenEmptyBody_WhenSubmitComplaint_ThenRejected() {}

    // II.6.3.1 — Admin manages complaints
    @Test @Disabled("II.6.3.1 main: admin views all complaints")
    void GivenAdmin_WhenViewAllComplaints_ThenAllListed() {}
    @Test @Disabled("II.6.3.1 main: admin responds + resolves")
    void GivenComplaint_WhenAdminResolves_ThenResolvedTerminal() {}
    @Test @Disabled("II.6.3.1 negative: non-admin cannot view complaint queue")
    void GivenNonAdmin_WhenViewAllComplaints_ThenRejected() {}

    // II.4.4 — Company support inbox
    @Test @Disabled("II.4.4 main: Owner views company support inbox")
    void GivenOwner_WhenViewCompanyInbox_ThenCompanyConversations() {}

    // II.6.3.2 — Admin announcements
    @Test @Disabled("II.6.3.2 main: admin broadcasts to all members")
    void GivenAdmin_WhenAnnounce_ThenAllMembersReceive() {}

    // Cross-flow
    @Test @Disabled("messaging: closing conversation locks further messages")
    void GivenClosedConversation_WhenSendMessage_ThenRejected() {}
    @Test @Disabled("messaging: viewMyConversations returns participant-scoped only")
    void GivenMember_WhenViewMyConversations_ThenScopedCorrectly() {}
}
