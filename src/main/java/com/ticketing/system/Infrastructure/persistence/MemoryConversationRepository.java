package com.ticketing.system.Infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.ticketing.system.Core.Domain.messaging.Conversation;
import com.ticketing.system.Core.Domain.messaging.ConversationStatus;
import com.ticketing.system.Core.Domain.messaging.ConversationType;
import com.ticketing.system.Core.Domain.messaging.IConversationRepository;
import com.ticketing.system.Core.Domain.messaging.ParticipantType;

/**
 * In-memory {@link IConversationRepository} for V1. Lets Spring wire
 * MessagingService.
 *
 * <p>{@code countUnreadForMember} requires per-message read tracking that
 * the {@code Conversation} aggregate doesn't yet expose at this granularity
 * — returning 0 is a safe placeholder that matches the "no unread" UX state
 * until messaging UCs (II.3.10 / II.6.3.x) are implemented for real.
 */
@Repository
public class MemoryConversationRepository implements IConversationRepository {

    private final Map<String, Conversation> conversationsById = new ConcurrentHashMap<>();
    private final RepositoryLocks<String> locks = new RepositoryLocks<>();

    @Override
    public void lockForUpdate(String id) { locks.lock(id); }

    @Override
    public void unlock(String id) { locks.unlock(id); }


    @Override
    public void save(Conversation conversation) {
        conversationsById.put(conversation.getConversationId(), conversation);
    }

    @Override
    public Optional<Conversation> findById(String conversationId) {
        if (conversationId == null) return Optional.empty();
        return Optional.ofNullable(conversationsById.get(conversationId));
    }

    @Override
    public List<Conversation> findByParticipant(int participantId, ParticipantType participantType) {
        List<Conversation> result = new ArrayList<>();
        for (Conversation c : conversationsById.values()) {
            boolean asInitiator = c.getInitiatorId() == participantId
                    && c.getInitiatorType() == participantType;
            boolean asCounterparty = c.getCounterpartyId() == participantId
                    && c.getCounterpartyType() == participantType;
            if (asInitiator || asCounterparty) result.add(c);
        }
        return result;
    }

    @Override
    public List<Conversation> findByType(ConversationType type) {
        List<Conversation> result = new ArrayList<>();
        for (Conversation c : conversationsById.values()) {
            if (c.getType() == type) result.add(c);
        }
        return result;
    }

    @Override
    public List<Conversation> findByCompanyAsCounterparty(int companyId) {
        List<Conversation> result = new ArrayList<>();
        for (Conversation c : conversationsById.values()) {
            if (c.getCounterpartyType() == ParticipantType.COMPANY
                    && c.getCounterpartyId() == companyId) {
                result.add(c);
            }
        }
        return result;
    }

    @Override
    public int countUnreadForMember(int memberId) {
        // Per-message unread tracking not exposed by Conversation yet. Return
        // 0 as a placeholder so the inbox badge renders cleanly until the
        // messaging UCs are implemented.
        return 0;
    }

    @Override
    public List<Conversation> findByTypeAndStatus(ConversationType type, ConversationStatus status) {
        List<Conversation> result = new ArrayList<>();
        for (Conversation c : conversationsById.values()) {
            if (c.getType() == type && c.getStatus() == status) result.add(c);
        }
        return result;
    }
}
