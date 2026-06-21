package com.ticketing.system.Core.Domain.messaging;

import java.time.LocalDateTime;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;

// Sub-entity of the Conversation aggregate.
// Identity is local to the conversation. Cross-aggregate refs by ID:
//   senderId — User / ProductionCompany / Admin (resolved by senderType)
public class Message implements InvariantChecked {

    private final String messageId;
    private final int senderId;
    private final ParticipantType senderType;
    private final String body;
    private final LocalDateTime sentAt;
    private boolean read;

    public Message(String messageId, int senderId, ParticipantType senderType,
                   String body, LocalDateTime sentAt) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.senderType = senderType;
        this.body = body;
        this.sentAt = sentAt;
        this.read = false;
        checkInvariants();
    }

    @Override
    public void checkInvariants() {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalStateException("Message invariant violated: messageId must be non-blank");
        }
        if (senderType == null) {
            throw new IllegalStateException("Message invariant violated: senderType must not be null");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Message invariant violated: body must be non-blank");
        }
        if (sentAt == null) {
            throw new IllegalStateException("Message invariant violated: sentAt must not be null");
        }
    }

    public String getMessageId() { return messageId; }

    public int getSenderId() { return senderId; }

    public ParticipantType getSenderType() { return senderType; }

    public String getBody() { return body; }

    public LocalDateTime getSentAt() { return sentAt; }

    public boolean isRead() { return read; }

    // UI action — flips false -> true. Idempotent.
    public void markRead() {
        throw new UnsupportedOperationException("messaging: not implemented");
    }
}
