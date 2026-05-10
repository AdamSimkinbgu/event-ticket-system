package com.ticketing.system.Core.Domain.messaging;

import java.time.LocalDateTime;

// Sub-entity of the Conversation aggregate.
// Identity is local to the conversation. Cross-aggregate refs by ID:
//   senderId — User / ProductionCompany / Admin (resolved by senderType)
public class Message {

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
