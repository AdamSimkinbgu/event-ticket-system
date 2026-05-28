package com.ticketing.system.unit.domain;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.ticketing.system.support.BaseDomainTest;

// Unit tests for the centralized messaging Conversation aggregate.
// Extends BaseDomainTest so future (currently @Disabled) tests get automatic
// invariant verification via track(aggregate).
class ConversationTest extends BaseDomainTest {

    @Test @Disabled("messaging: addMessage appends + updates lastMessageAt")
    void givenOpenConversation_whenAddMessage_thenAppendedAndTimestamped() {}

    @Test @Disabled("messaging: addMessage by non-participant rejected")
    void givenOutsider_whenAddMessage_thenInvalidParticipantException() {}

    @Test @Disabled("messaging: addMessage on CLOSED rejected")
    void givenClosedConversation_whenAddMessage_thenConversationClosedException() {}

    @Test @Disabled("messaging: addMessage on RESOLVED rejected")
    void givenResolvedComplaint_whenAddMessage_thenConversationClosedException() {}

    @Test @Disabled("messaging: counterparty reply transitions OPEN -> RESPONDED")
    void givenOpenInquiry_whenCounterpartyReplies_thenStatusResponded() {}

    @Test @Disabled("messaging: markMessageRead is idempotent + only by non-sender")
    void givenUnreadMessage_whenSenderMarksRead_thenRejected() {}

    @Test @Disabled("messaging: unreadCountFor counts messages not authored by reader")
    void givenMixedMessages_whenUnreadCount_thenExcludesOwn() {}
}
