package com.ticketing.system.unit.application;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class MessagingServiceTest {

    @Test @Disabled("II.3.10: startConversation creates INQUIRY with first message")
    void givenMember_whenStartConversation_thenInquiryCreated() {}

    @Test @Disabled("messaging: sendMessage by participant succeeds")
    void givenParticipant_whenSendMessage_thenAppended() {}

    @Test @Disabled("messaging: sendMessage by non-participant rejected")
    void givenOutsider_whenSendMessage_thenRejected() {}

    @Test @Disabled("II.3.3: submitComplaint creates COMPLAINT with ADMIN_GROUP counterparty")
    void givenMember_whenSubmitComplaint_thenComplaintCreated() {}

    @Test @Disabled("II.6.3.1: respondToComplaint by admin transitions to RESPONDED")
    void givenComplaint_whenAdminResponds_thenStatusResponded() {}

    @Test @Disabled("II.6.3.1: respondToComplaint with newStatus=RESOLVED locks further replies")
    void givenComplaint_whenAdminResolves_thenStatusResolved() {}

    @Test @Disabled("II.6.3.2: announce broadcasts to BROADCAST_MEMBERS audience")
    void givenAdmin_whenAnnounce_thenBroadcastConversationCreated() {}

    @Test @Disabled("messaging: viewMyConversations returns participant-scoped list only")
    void givenMember_whenViewMyConversations_thenOwnOnly() {}

    @Test @Disabled("II.4.4: viewCompanyInbox returns conversations where company is counterparty")
    void givenCompany_whenViewInbox_thenCounterpartyConversations() {}

    @Test @Disabled("II.6.3.1: viewAllComplaints admin-only RBAC")
    void givenNonAdmin_whenViewAllComplaints_thenRejected() {}

    @Test @Disabled("messaging: markMessageAsRead flips read flag")
    void givenUnreadMessage_whenMarkAsRead_thenRead() {}

    @Test @Disabled("messaging: closeConversation makes further sendMessage fail")
    void givenOpenConversation_whenClose_thenFurtherSendsRejected() {}
}
