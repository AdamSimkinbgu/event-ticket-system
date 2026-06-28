package com.ticketing.system.Core.Domain.messaging;

import java.util.List;
import java.util.Optional;

import com.ticketing.system.Core.Domain.shared.IRepository;

/**
 * Aggregate-root entry point for the Conversation aggregate (per the course's
 * {@code IXxxRepository} convention).
 *
 * <p>Owns the centralized messaging subsystem — it replaces the per-User
 * MessageInbox, the per-Company Inbox, and the standalone Complaint repo.
 */
public interface IConversationRepository extends IRepository<Conversation, String> {

    /**
     * @param conversation the conversation to persist
     */
    void save(Conversation conversation);

    /**
     * @param conversationId the conversation id
     * @return the conversation if present
     */
    Optional<Conversation> findById(String conversationId);

    /**
     * "My conversations" view — used by the company support inbox.
     *
     * @param participantId   the participant's id
     * @param participantType whether the participant is a member, company, etc.
     * @return conversations the participant takes part in
     */
    List<Conversation> findByParticipant(int participantId, ParticipantType participantType);

    /**
     * Member Support Inbox: INQUIRY / COMPLAINT the member initiated, plus
     * DIRECT admin outreach where the member is the counterparty. Excludes
     * inquiries TO a company the member owns (those are company-counterparty
     * threads and belong in the company "Customer Inquiries" view).
     *
     * @param memberId the member's user id
     * @return the member's support inbox conversations
     */
    List<Conversation> findMemberInbox(int memberId);

    /**
     * Admin-side typed queries (II.6.3.1 — find all complaints).
     *
     * @param type the conversation type to filter by
     * @return conversations of the given type
     */
    List<Conversation> findByType(ConversationType type);

    /**
     * Admin outreach history (II.6.3.2) — DIRECT conversations the admin
     * initiated.
     *
     * @param type          the conversation type to filter by
     * @param initiatorType the type of the conversation initiator
     * @return matching conversations
     */
    List<Conversation> findByTypeAndInitiatorType(ConversationType type, ParticipantType initiatorType);

    /**
     * Company-side support inbox (II.4.4) — conversations where the company is
     * the counterparty.
     *
     * @param companyId the company's id
     * @return conversations where the company is the counterparty
     */
    List<Conversation> findByCompanyAsCounterparty(int companyId);

    /**
     * Member inbox unread badge.
     *
     * @param memberId the member's user id
     * @return the count of the member's unread conversations
     */
    int countUnreadForMember(int memberId);

    /**
     * Admin filter for complaint queues — by status and date.
     *
     * @param type   the conversation type to filter by
     * @param status the conversation status to filter by
     * @return matching conversations
     */
    List<Conversation> findByTypeAndStatus(ConversationType type, ConversationStatus status);
}
