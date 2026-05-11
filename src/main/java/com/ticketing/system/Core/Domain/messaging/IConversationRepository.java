package com.ticketing.system.Core.Domain.messaging;

import java.util.List;
import java.util.Optional;

// Aggregate-root entry point for the Conversation aggregate (per course's
// IXxxRepository convention). Owns the centralized messaging subsystem —
// replaces per-User MessageInbox, per-Company Inbox, and the standalone Complaint repo.
public interface IConversationRepository {

    void save(Conversation conversation);

    Optional<Conversation> findById(String conversationId);

    // "My conversations" view — used by member inbox, company support inbox, admin queue.
    List<Conversation> findByParticipant(int participantId, ParticipantType participantType);

    // Admin-side typed queries (II.6.3.1 — find all complaints).
    List<Conversation> findByType(ConversationType type);

    // Company-side support inbox (II.4.4) — conversations where the company is counterparty.
    List<Conversation> findByCompanyAsCounterparty(int companyId);

    // Member inbox unread badge.
    int countUnreadForMember(int memberId);

    // Admin filter for complaint queues — by status and date.
    List<Conversation> findByTypeAndStatus(ConversationType type, ConversationStatus status);
}
