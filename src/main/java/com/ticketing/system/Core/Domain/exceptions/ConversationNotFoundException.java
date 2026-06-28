package com.ticketing.system.Core.Domain.exceptions;

/**
 * Specific subclass of {@link EntityNotFoundException} for Conversation lookups
 * (messaging subsystem).
 */
public class ConversationNotFoundException extends EntityNotFoundException {

    /**
     * @param conversationId the id that was looked up
     */
    public ConversationNotFoundException(Object conversationId) {
        super("Conversation", conversationId);
    }
}
