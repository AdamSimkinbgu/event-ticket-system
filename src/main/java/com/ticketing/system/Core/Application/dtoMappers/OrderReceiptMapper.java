package com.ticketing.system.Core.Application.dtoMappers;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.Tickets.TicketStatus;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;
import com.ticketing.system.Core.Domain.orders.ReceiptLine;
import com.ticketing.system.Core.Domain.orders.TransactionRecord;

public class OrderReceiptMapper {

    /**
     * Maps a full OrderReceipt to a PurchaseRecordDTO.
     *
     * The receipt is the immutable purchase snapshot. Tickets are loaded only to
     * enrich the DTO with current live ticket status.
     */
    public PurchaseHistoryDTO.PurchaseRecordDTO toPurchaseRecordDTO(OrderReceipt receipt,
            ITicketRepository ticketRepository) {
        List<Ticket> tickets = ticketRepository.findByOrderReceiptId(receipt.getId());
        return map(receipt, safeList(tickets), false, true);
    }

    /**
     * Maps only the selected tickets of a receipt. // overloading of the above
     * function
     *
     * Use this for company sales history after the service already filtered the
     * tickets that belong to the company. Transaction records are intentionally
     * omitted here so a company does not see payment data for tickets from another
     * company that happened to be bought in the same order.
     */
    public PurchaseHistoryDTO.PurchaseRecordDTO toPurchaseRecordDTO(OrderReceipt receipt,
            List<Ticket> selectedTickets) { // overloading of the above function
        return map(receipt, safeList(selectedTickets), true, false);
    }

    // Backward-compatible wrappers for current code that uses the old method name.
    @Deprecated
    public PurchaseHistoryDTO.PurchaseRecordDTO OrderReceiptToPurchaseRecordDTO(OrderReceipt receipt,
            ITicketRepository ticketRepository) {
        return toPurchaseRecordDTO(receipt, ticketRepository);
    }

    @Deprecated
    public PurchaseHistoryDTO.PurchaseRecordDTO OrderReceiptToPurchaseRecordDTO(OrderReceipt receipt,
            List<Ticket> tickets) {
        return toPurchaseRecordDTO(receipt, tickets);
    }

    // this function does the actual mapping work from *OrderReceipt to
    // PurchaseRecordDTO* , with options to include only selected tickets and to
    // include/exclude transactions.
    private PurchaseHistoryDTO.PurchaseRecordDTO map(OrderReceipt receipt, List<Ticket> tickets,
            boolean selectedTicketsOnly, boolean includeTransactions) {
        // Create a map of ticketId to Ticket for quick lookup when mapping receipt
        // lines to ticket DTOs.
        Map<Integer, Ticket> ticketsById = tickets.stream()
                .collect(Collectors.toMap(
                        Ticket::getId,
                        Function.identity(),
                        (first, second) -> first));

        // If selectedTicketsOnly is true, filter the receipt lines to include only
        // those that correspond to the selected tickets. Otherwise, include all lines.
        List<ReceiptLine> linesToMap = receipt.getReceiptLines();
        if (selectedTicketsOnly) {
            Set<Integer> selectedTicketIds = ticketsById.keySet();
            linesToMap = linesToMap.stream()
                    .filter(line -> selectedTicketIds.contains(line.getTicketId()))
                    .toList();
        }

        // Calculate totalPaid based on whether we're including all tickets or only
        // selected tickets. If only selected, sum the priceAtReservation of the
        // included lines. Otherwise, use the receipt's total amount.
        double totalPaid = selectedTicketsOnly
                ? linesToMap.stream().mapToDouble(ReceiptLine::getPriceAtReservation).sum()
                : receipt.getTotalAmount();

        // Map the receipt lines to ticket DTOs, using the ticketsById map to enrich
        // with current ticket status. If a ticket is missing (which shouldn't happen
        // but we handle it gracefully), use a fallback status based on whether the
        // receipt was refunded.
        List<PurchaseHistoryDTO.TicketRecordDTO> ticketRecords = linesToMap.stream()
                .map(line -> toTicketRecordDTO(receipt, line, ticketsById))
                .toList();

        // Optionally map the transaction records to transaction DTOs if
        // includeTransactions is true. Otherwise, use an empty list.
        List<PurchaseHistoryDTO.TransactionRecordDTO> transactionDtos = includeTransactions
                ? receipt.getTransactionRecords().stream().map(this::toTransactionRecordDTO).toList()
                : List.of();

        return new PurchaseHistoryDTO.PurchaseRecordDTO(
                receipt.getId(),
                receipt.getHolderUserId(),
                receipt.getGuestEmail(),
                receipt.getPurchaseTime(),
                totalPaid,
                receipt.wasRefunded(),
                transactionDtos,
                ticketRecords);
    }

    private PurchaseHistoryDTO.TicketRecordDTO toTicketRecordDTO(OrderReceipt receipt, ReceiptLine line,
            Map<Integer, Ticket> ticketsById) {
        Ticket ticket = ticketsById.get(line.getTicketId());
        TicketStatus currentStatus = ticket != null
                ? ticket.getStatus()
                : fallbackStatus(receipt);

        return new PurchaseHistoryDTO.TicketRecordDTO(
                line.getTicketId(),
                line.getZoneId(),
                line.getEventId(),
                receipt.getId(),
                line.getSeatNumber(),
                line.getPriceAtReservation(),
                currentStatus);
    }

    private PurchaseHistoryDTO.TransactionRecordDTO toTransactionRecordDTO(TransactionRecord record) {
        return new PurchaseHistoryDTO.TransactionRecordDTO(
                record.getType().name(),
                record.getProviderName(),
                record.getExternalTransactionId(),
                record.getAmount(),
                record.getCurrency(),
                record.getTimestamp());
    }

    private TicketStatus fallbackStatus(OrderReceipt receipt) {
        return receipt.wasRefunded() ? TicketStatus.REFUNDED : TicketStatus.PAID;
    }

    private List<Ticket> safeList(List<Ticket> tickets) {
        return tickets == null ? List.of() : tickets;
    }

}
