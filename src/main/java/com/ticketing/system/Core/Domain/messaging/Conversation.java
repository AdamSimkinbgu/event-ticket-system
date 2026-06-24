package com.ticketing.system.Core.Domain.messaging;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ticketing.system.Core.Domain.exceptions.BusinessRuleViolationException;
import com.ticketing.system.Core.Domain.exceptions.ConversationClosedException;
import com.ticketing.system.Core.Domain.exceptions.InvalidParticipantException;
import com.ticketing.system.Core.Domain.exceptions.InvalidStateTransitionException;
import com.ticketing.system.Core.Domain.exceptions.MessageNotFoundException;
import com.ticketing.system.Core.Domain.shared.InvariantChecked;

// Aggregate root for the centralized messaging subsystem.
// Replaces the per-User MessageInbox, per-Company Inbox, and the standalone Complaint aggregate.
//
// Two-party model: an initiator and a counterparty. Either side can be a specific entity
// (MEMBER/COMPANY/ADMIN) or a group descriptor (ADMIN_GROUP / SYSTEM).
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

    // Factory — opens a new conversation with its first message already attached.
    // Generates the conversationId, the first messageId, and the timestamps internally
    // so id/time creation stays in the domain (callers don't synthesize them).
    public static Conversation start(ConversationType type,
                                     int initiatorId, ParticipantType initiatorType,
                                     int counterpartyId, ParticipantType counterpartyType,
                                     String subject, String firstMessageBody) {
        LocalDateTime now = LocalDateTime.now();
        Message first = new Message(UUID.randomUUID().toString(),
                initiatorId, initiatorType, firstMessageBody, now);
        List<Message> messages = new ArrayList<>();
        messages.add(first);
        return new Conversation(UUID.randomUUID().toString(), type,
                initiatorId, initiatorType, counterpartyId, counterpartyType,
                subject, now, messages);
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
        if (isClosed()) {
            throw new ConversationClosedException(conversationId);
        }
        // One-shot complaint: a COMPLAINT carries the member's single opening message and accepts
        // exactly one admin reply (which resolves it). The member can never reply, and a second
        // admin reply is rejected — enforced here so no service path can bypass it.
        if (type == ConversationType.COMPLAINT) {
            if (senderType != ParticipantType.ADMIN) {
                throw new BusinessRuleViolationException(
                        "Complaints accept only a single admin response; the member cannot reply");
            }
            boolean alreadyAnswered = messages.stream()
                    .anyMatch(m -> m.getSenderType() == ParticipantType.ADMIN);
            if (alreadyAnswered) {
                throw new BusinessRuleViolationException(
                        "Complaint " + conversationId + " has already received its admin response");
            }
        }
        if (!involvesParticipant(senderId, senderType)) {
            throw new InvalidParticipantException(senderId, conversationId);
        }
        LocalDateTime now = LocalDateTime.now();
        messages.add(new Message(UUID.randomUUID().toString(), senderId, senderType, body, now));
        this.lastMessageAt = now;
        // Auto status flip between the two sides: the initiator re-opens; the other side responds.
        if (isInitiatorSide(senderId, senderType)) {
            if (status == ConversationStatus.RESPONDED) {
                status = ConversationStatus.OPEN;
            }
        } else if (status == ConversationStatus.OPEN) {
            status = ConversationStatus.RESPONDED;
        }
        checkInvariants();
    }

    // Mark a single message read by the given participant (the non-sender).
    public void markMessageRead(String messageId, int readerId) {
        for (Message m : messages) {
            if (m.getMessageId().equals(messageId)) {
                if (m.getSenderId() != readerId) {
                    m.markRead();
                }
                return;
            }
        }
        throw new MessageNotFoundException(messageId);
    }

    // Status transitions.
    public void transitionToResponded() {
        if (isClosed()) {
            throw new InvalidStateTransitionException("Conversation", status.name(),
                    ConversationStatus.RESPONDED.name());
        }
        this.status = ConversationStatus.RESPONDED;
        checkInvariants();
    }

    public void transitionToResolved() {
        if (status == ConversationStatus.RESOLVED) {
            return; // idempotent
        }
        if (status == ConversationStatus.CLOSED) {
            throw new InvalidStateTransitionException("Conversation", status.name(),
                    ConversationStatus.RESOLVED.name());
        }
        this.status = ConversationStatus.RESOLVED;
        checkInvariants();
    }

    public void transitionToClosed() {
        if (status == ConversationStatus.CLOSED) {
            return; // idempotent
        }
        this.status = ConversationStatus.CLOSED;
        checkInvariants();
    }

    // State checks.
    public boolean isClosed() {
        return status == ConversationStatus.CLOSED || status == ConversationStatus.RESOLVED;
    }

    public boolean involvesParticipant(int participantId, ParticipantType participantType) {
        if (participantType == null) {
            return false;
        }
        boolean matchesInitiator = participantId == initiatorId && participantType == initiatorType;
        boolean matchesCounterparty = participantId == counterpartyId && participantType == counterpartyType;
        if (matchesInitiator || matchesCounterparty) {
            return true;
        }
        // A specific ADMIN acts on behalf of the ADMIN_GROUP (the complaint queue).
        if (participantType == ParticipantType.ADMIN) {
            return initiatorType == ParticipantType.ADMIN_GROUP
                    || counterpartyType == ParticipantType.ADMIN_GROUP;
        }
        return false;
    }

    // Unread = messages addressed to (i.e. not sent by) the reader and not yet read.
    public int unreadCountFor(int readerId) {
        int count = 0;
        for (Message m : messages) {
            if (m.getSenderId() != readerId && !m.isRead()) {
                count++;
            }
        }
        return count;
    }

    // Whether the given sender represents the initiator side of this thread
    // (a specific ADMIN counts as the initiator when the initiator is the ADMIN_GROUP).
    private boolean isInitiatorSide(int senderId, ParticipantType senderType) {
        if (senderId == initiatorId && senderType == initiatorType) {
            return true;
        }
        return senderType == ParticipantType.ADMIN && initiatorType == ParticipantType.ADMIN_GROUP;
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
