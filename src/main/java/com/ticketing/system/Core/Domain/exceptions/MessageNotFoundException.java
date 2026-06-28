package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when a referenced Message id doesn't exist within its Conversation.
 */
public class MessageNotFoundException extends DomainException {

    /**
     * @param messageId the id that was looked up
     */
    public MessageNotFoundException(Object messageId) {
        super("Message not found: " + messageId);
    }
}
