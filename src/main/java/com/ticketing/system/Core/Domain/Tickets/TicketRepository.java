package com.ticketing.system.Core.Domain.Tickets;




public interface TicketRepository {
    Ticket findById(int ticktid);

    boolean save(Ticket ticket);
}
  