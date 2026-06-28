package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when a sender attempts to post to a Conversation they're not a
 * participant of, or when a participant of an unexpected type is supplied.
 */
public class InvalidParticipantException extends DomainException {

    /**
     * @param participantId  the offending participant
     * @param conversationId the conversation they are not part of
     */
    public InvalidParticipantException(Object participantId, Object conversationId) {
        super("Participant " + participantId + " is not part of conversation " + conversationId);
    }

    /**
     * @param message custom detail message
     */
    public InvalidParticipantException(String message) {
        super(message);
    }
}
