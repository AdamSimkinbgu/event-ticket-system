package com.ticketing.system.Core.Domain.messaging;

import java.time.LocalDateTime;
import java.util.List;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;

// Aggregate root for the centralized messaging subsystem.
// Replaces the per-User MessageInbox, per-Company Inbox, and the standalone Complaint aggregate.
//
// Two-party model: an initiator and a counterparty. Either side can be a specific entity
// (MEMBER/COMPANY/ADMIN) or a broadcast descriptor (ADMIN_GROUP / BROADCAST_MEMBERS / SYSTEM).
//
// Cross-aggregate references by ID per course rules — never holds direct refs to User / ProductionCompany / Admin.
public class Conversation implements InvariantChecked {

    private final String conversationId;
    private final ConversationType type;
    private ConversationStatus status;

    // Initiator (the party that started the thread).
    private final int initiatorId;
    private final ParticipantType initiatorType;

    // Counterparty (the recipient — may be a specific entity or a group descriptor).
    private final int counterpartyId;        // 0 / sentinel when counterpartyType is a group
    private final ParticipantType counterpartyType;

    private final String subject;
    private final LocalDateTime createdAt;
    private LocalDateTime lastMessageAt;
    private final List<Message> messages;

    public Conversation(String conversationId, ConversationType type,
                        int initiatorId, ParticipantType initiatorType,
                        int counterpartyId, ParticipantType counterpartyType,
                        String subject, LocalDateTime createdAt,
                        List<Message> messages) {
        this.conversationId = conversationId;
        this.type = type;
        this.status = ConversationStatus.OPEN;
        this.initiatorId = initiatorId;
        this.initiatorType = initiatorType;
        this.counterpartyId = counterpartyId;
        this.counterpartyType = counterpartyType;
        this.subject = subject;
        this.createdAt = createdAt;
        this.lastMessageAt = createdAt;
        this.messages = messages;
        checkInvariants();
    }

    public String getConversationId() { return conversationId; }
    public ConversationType getType() { return type; }
    public ConversationStatus getStatus() { return status; }
    public int getInitiatorId() { return initiatorId; }
    public ParticipantType getInitiatorType() { return initiatorType; }
    public int getCounterpartyId() { return counterpartyId; }
    public ParticipantType getCounterpartyType() { return counterpartyType; }
    public String getSubject() { return subject; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public List<Message> getMessages() { return List.copyOf(messages); }

    // Append a new message. Updates lastMessageAt and may transition status (OPEN→RESPONDED, etc).
    // Throws ConversationClosedException if status is CLOSED or RESOLVED.
    // Throws InvalidParticipantException if sender is not initiator or counterparty.
    public void addMessage(int senderId, ParticipantType senderType, String body) {
        throw new UnsupportedOperationException("messaging: not implemented");
    }

    // Mark a single message read by the given participant (the non-sender).
    public void markMessageRead(String messageId, int readerId) {
        throw new UnsupportedOperationException("messaging: not implemented");
    }

    // Status transitions.
    public void transitionToResponded() {
        throw new UnsupportedOperationException("messaging: not implemented");
    }

    public void transitionToResolved() {
        throw new UnsupportedOperationException("messaging: not implemented");
    }

    public void transitionToClosed() {
        throw new UnsupportedOperationException("messaging: not implemented");
    }

    // State checks.
    public boolean isClosed() {
        return status == ConversationStatus.CLOSED || status == ConversationStatus.RESOLVED;
    }

    public boolean involvesParticipant(int participantId, ParticipantType participantType) {
        throw new UnsupportedOperationException("messaging: not implemented");
    }

    public int unreadCountFor(int readerId) {
        throw new UnsupportedOperationException("messaging: not implemented");
    }

    @Override
    public void checkInvariants() {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalStateException("Conversation invariant violated: conversationId must be non-blank");
        }
        if (type == null) {
            throw new IllegalStateException("Conversation invariant violated: type must not be null");
        }
        if (status == null) {
            throw new IllegalStateException("Conversation invariant violated: status must not be null");
        }
        if (initiatorType == null) {
            throw new IllegalStateException("Conversation invariant violated: initiatorType must not be null");
        }
        if (counterpartyType == null) {
            throw new IllegalStateException("Conversation invariant violated: counterpartyType must not be null");
        }
        if (createdAt == null) {
            throw new IllegalStateException("Conversation invariant violated: createdAt must not be null");
        }
        if (lastMessageAt == null) {
            throw new IllegalStateException("Conversation invariant violated: lastMessageAt must not be null");
        }
        if (lastMessageAt.isBefore(createdAt)) {
            throw new IllegalStateException("Conversation invariant violated: lastMessageAt must be >= createdAt");
        }
        if (messages == null) {
            throw new IllegalStateException("Conversation invariant violated: messages list must not be null");
        }
    }
}
