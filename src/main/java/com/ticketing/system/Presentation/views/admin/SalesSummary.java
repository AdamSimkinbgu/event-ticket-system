package com.ticketing.system.Presentation.views.admin;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO.PurchaseRecordDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO.TicketRecordDTO;

/**
 * Refund-aware headline figures for the Company Sales History page, derived from the order
 * records the grid shows. Refunded orders are excluded from every figure (they remain listed and
 * tagged in the grid). Pure (no Vaadin) so it is unit-testable.
 */
public record SalesSummary(double revenue, int ticketsSold, double avgOrderValue, String topEvent) {

    public static final String NO_TOP_EVENT = "—";

    public static SalesSummary of(List<PurchaseRecordDTO> records) {
        List<PurchaseRecordDTO> paid = records.stream().filter(r -> !r.refunded()).toList();

        double revenue = paid.stream().mapToDouble(PurchaseRecordDTO::totalPaid).sum();
        int ticketsSold = paid.stream().mapToInt(r -> r.tickets().size()).sum();
        double avgOrderValue = paid.isEmpty() ? 0.0 : revenue / paid.size();

        String topEvent = paid.stream()
                .flatMap(r -> r.tickets().stream())
                .filter(t -> t.eventName() != null)
                .collect(Collectors.groupingBy(
                        TicketRecordDTO::eventName,
                        Collectors.summingDouble(TicketRecordDTO::pricePaid)))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(NO_TOP_EVENT);

        return new SalesSummary(revenue, ticketsSold, avgOrderValue, topEvent);
    }
}
