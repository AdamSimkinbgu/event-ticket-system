package com.ticketing.system.Presentation.views.admin;

import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO.PurchaseRecordDTO;
import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBadge;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkFilterChip;
import com.ticketing.system.Presentation.components.kit.LkGrid;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.components.kit.LkStat;
import com.ticketing.system.Presentation.layouts.PlatformAdminLayout;
import com.ticketing.system.Presentation.presenters.admin.GlobalHistoryPresenter;
import com.ticketing.system.Presentation.security.Capability;
import com.ticketing.system.Presentation.security.RequireCapability;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Route(value = "admin/global-history", layout = PlatformAdminLayout.class)
@PageTitle("Global purchase history · Admin")
@PermitAll
@RequireCapability(Capability.VIEW_GLOBAL_HISTORY)
public class GlobalHistoryView extends LkPage {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final List<PurchaseRecordDTO> records;
    private final Map<Integer, String> eventNames;

    public GlobalHistoryView(GlobalHistoryPresenter presenter) {
        this.records = presenter.loadAllRecords();
        this.eventNames = presenter.eventNames();

        title("Global purchase history");
        subtitle("Every order across the platform — filter by date, company, event, or status.");
        actions(new LkBtn("Export CSV").variant(LkBtn.Variant.secondary)
            .icon(new LkIcon("chart", 15))
            .onClick(e -> Toasts.success("Global history exported (mock).")));

        add(buildFilters());
        add(buildStats());
        add(buildGridCard());
    }

    // Filter chips are presentational for now — wiring them to a
    // GlobalHistoryFiltersDTO + re-query is a follow-up.
    private Component buildFilters() {
        LkRow row = new LkRow().gap(8);
        row.add(
            new LkFilterChip("Date range",
                List.of("Last 7 days", "Last 30 days", "This quarter", "This year", "All time"),
                true, List.of("All time")),
            new LkFilterChip("Status", List.of("Paid", "Refunded"), true, List.of())
        );
        return row;
    }

    private Component buildStats() {
        double revenue = records.stream().filter(r -> !r.refunded())
                .mapToDouble(PurchaseRecordDTO::totalPaid).sum();
        long orders = records.size();
        long refunds = records.stream().filter(PurchaseRecordDTO::refunded).count();
        long tickets = records.stream().mapToLong(r -> r.tickets().size()).sum();

        Div stats = new Div();
        stats.addClassName("ow-stats");
        stats.add(
            new LkStat("Total revenue", money(revenue)),
            new LkStat("Orders",        String.valueOf(orders)),
            new LkStat("Refunds",       String.valueOf(refunds)),
            new LkStat("Tickets sold",  String.valueOf(tickets))
        );
        return stats;
    }

    private Component buildGridCard() {
        LkCard card = new LkCard().pad(0);
        if (records.isEmpty()) {
            card.add(Lk.muted("No orders across the platform yet."));
            return card;
        }
        LkGrid grid = new LkGrid()
            .col("Date",   "date")
            .col("Buyer",  "buyer")
            .col("Event",  "evt")
            .col("Total",  "total",  LkGrid.Align.RIGHT)
            .col("Status", "status", LkGrid.Align.RIGHT);

        records.stream()
            .sorted(Comparator.comparing(PurchaseRecordDTO::purchasedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .forEach(r -> recordRow(grid, r));

        grid.build();
        card.add(grid);
        return card;
    }

    private void recordRow(LkGrid grid, PurchaseRecordDTO r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("date", r.purchasedAt() == null ? "—" : r.purchasedAt().format(DATE_FMT));
        row.put("buyer", Lk.mono(buyerLabel(r)));
        Span ev = new Span();
        ev.getElement().setProperty("innerHTML", "<b>" + escape(orderEventLabel(r)) + "</b>");
        row.put("evt", ev);
        Span t = new Span();
        t.getElement().setProperty("innerHTML", "<b>" + money(r.totalPaid()) + "</b>");
        row.put("total", t);
        row.put("status", statusBadge(r.refunded() ? "Refunded" : "Paid"));
        grid.row(row);
    }

    private static String buyerLabel(PurchaseRecordDTO r) {
        if (r.guestEmail() != null && !r.guestEmail().isBlank()) return r.guestEmail();
        return r.buyerUserId() == null ? "—" : "User #" + r.buyerUserId();
    }

    private String eventLabel(int eventId) {
        return eventNames.getOrDefault(eventId, "Event #" + eventId);
    }

    private String orderEventLabel(PurchaseRecordDTO r) {
        List<Integer> ids = r.tickets().stream().map(t -> t.eventId()).distinct().toList();
        if (ids.isEmpty()) return "—";
        if (ids.size() == 1) return eventLabel(ids.get(0));
        return ids.size() + " events";
    }

    private static String money(double amount) {
        return String.format("$%,.2f", amount);
    }

    private LkBadge statusBadge(String status) {
        LkBadge.Tone tone = switch (status) {
            case "Paid"     -> LkBadge.Tone.success;
            case "Refunded" -> LkBadge.Tone.warn;
            default          -> LkBadge.Tone.muted;
        };
        return new LkBadge(status, tone).small();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
