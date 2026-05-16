package com.ticketing.system.Core.Application.dtoMappers;

import java.util.List;

import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;

public class OrderReceiptMapper {
    
    public PurchaseHistoryDTO.PurchaseRecordDTO OrderReceiptToPurchaseRecordDTO(OrderReceipt sale, ITicketRepository ticketRepository, IEventRepository eventRepository) {
        List<Ticket> tickets = ticketRepository.findByOrderReceiptId(sale.getId());
        double totalAmount = tickets.stream().mapToDouble(Ticket::getPrice).sum();
        String eventName = eventRepository.findById(sale.geteventId()).getName();
        List<PurchaseHistoryDTO.TicketRecordDTO> ticketRecords = tickets.stream()
                .map(Ticket::toTicketRecordDTO)
                .toList();
        return new PurchaseHistoryDTO.PurchaseRecordDTO(
                sale.getId(),
                sale.geteventId(),
                eventName,
                sale.getPurchaseTime(),
                totalAmount,
                ticketRecords);
    }
    
}
