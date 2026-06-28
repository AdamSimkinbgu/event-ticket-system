package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when a message is sent to a Conversation whose status is RESOLVED or
 * CLOSED.
 */
public class ConversationClosedException extends DomainException {

    /**
     * @param conversationId the id of the closed conversation
     */
    public ConversationClosedException(Object conversationId) {
        super("Conversation " + conversationId + " is closed; no further messages allowed");
    }
}
