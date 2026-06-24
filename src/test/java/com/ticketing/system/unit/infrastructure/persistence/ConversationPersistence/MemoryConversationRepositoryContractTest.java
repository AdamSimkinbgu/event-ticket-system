package com.ticketing.system.unit.infrastructure.persistence.ConversationPersistence;

import com.ticketing.system.Core.Domain.messaging.IConversationRepository;
import com.ticketing.system.Infrastructure.persistence.ConversationPersistence.MemoryConversationRepository;

class MemoryConversationRepositoryContractTest extends IConversationRepositoryContractTest {

    @Override
    protected IConversationRepository newRepository() {
        return new MemoryConversationRepository();
    }
}
