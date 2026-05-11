package com.ticketing.system.Core.Domain.exceptions;

// Thrown when a referenced Message id doesn't exist within its Conversation.
public class MessageNotFoundException extends DomainException {

    public MessageNotFoundException(Object messageId) {
        super("Message not found: " + messageId);
    }
}
