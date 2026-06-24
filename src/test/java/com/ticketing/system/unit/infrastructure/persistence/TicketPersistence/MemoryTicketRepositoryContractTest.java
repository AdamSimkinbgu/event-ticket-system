package com.ticketing.system.unit.infrastructure.persistence.TicketPersistence;

import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Infrastructure.persistence.MemoryTicketRepository;

class MemoryTicketRepositoryContractTest extends ITicketRepositoryContractTest {

    @Override
    protected ITicketRepository newRepository() {
        return new MemoryTicketRepository();
    }
}
