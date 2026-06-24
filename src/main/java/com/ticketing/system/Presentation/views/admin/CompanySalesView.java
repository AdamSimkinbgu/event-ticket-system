package com.ticketing.system.Presentation.views.admin;

import com.ticketing.system.Core.Application.dto.CompanyDashboardDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Presentation.components.Money;
import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkFilterChip;
import com.ticketing.system.Presentation.components.kit.LkGrid;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.components.kit.LkStat;
import com.ticketing.system.Presentation.layouts.WorkspaceLayout;
import com.ticketing.system.Presentation.presenters.company.CompanySalesPresenter;
import com.ticketing.system.Presentation.security.Capability;
import com.ticketing.system.Presentation.security.RequireCapability;
import com.ticketing.system.Presentation.session.AuthSession;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Route(value = "owner/sales", layout = WorkspaceLayout.class)
@PageTitle("Company sales · TicketHub")
@PermitAll
@RequireCapability(Capability.VIEW_COMPANY_SALES)
public class CompanySalesView extends LkPage {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy");

    public CompanySalesView(CompanySalesPresenter presenter) {
        title("Company sales history");
        actions(new LkBtn("Export CSV").variant(LkBtn.Variant.secondary)
            .icon(new LkIcon("chart", 15))
            .onClick(e -> Toasts.success("Export CSV — no streaming endpoint yet.")));

        switch (presenter.load(AuthSession.token())) {
            case CompanySalesPresenter.Outcome.Success ok -> {
                subtitle(ok.companyName() + "  ·  immutable order receipts.");
                add(buildFilters());
                add(buildStats(ok.stats(), ok.sales()));
                add(buildGridCard(ok.sales()));
            }
            case CompanySalesPresenter.Outcome.NotAuthenticated ignored ->
                add(Lk.muted("Your session has expired — please sign in again."));
            case CompanySalesPresenter.Outcome.NoCompany ignored ->
                add(Lk.muted("You don't own a company yet."));
            case CompanySalesPresenter.Outcome.Failure fail ->
                add(Lk.muted("Could not load sales: " + fail.reason()));
        }
    }

    private Component buildFilters() {
        LkRow row = new LkRow().gap(8);
        row.add(
            new LkFilterChip("Date range",
                List.of("Last 7 days", "Last 30 days", "This quarter", "This year", "All time"),
                true, List.of("Last 30 days")),
            new LkFilterChip("Event",
                List.of("All events"), false, List.of())
        );
        return row;
    }

    private Component buildStats(CompanyDashboardDTO stats,
                                  PurchaseHistoryDTO sales) {
        List<PurchaseHistoryDTO.PurchaseRecordDTO> records = sales.records();

        String revenue = Money.format(Money.toCents(stats.revenue30d()));
        String tickets = String.valueOf(stats.ticketsSold30d());

        double aov = records.isEmpty() ? 0
                : records.stream().mapToDouble(PurchaseHistoryDTO.PurchaseRecordDTO::totalPaid).sum()
                  / records.size();

        String topEvent = records.stream()
                .flatMap(r -> r.tickets().stream())
                .filter(t -> t.eventName() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        PurchaseHistoryDTO.TicketRecordDTO::eventName,
                        java.util.stream.Collectors.summingDouble(
                                PurchaseHistoryDTO.TicketRecordDTO::pricePaid)))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("—");

        Div statsDiv = new Div();
        statsDiv.addClassName("ow-stats");
        statsDiv.add(
            new LkStat("Total revenue · 30d", revenue),
            new LkStat("Tickets sold · 30d",  tickets),
            new LkStat("Avg order value",      Money.format(Money.toCents(aov))),
            new LkStat("Top event",            topEvent)
        );
        return statsDiv;
    }

    private Component buildGridCard(PurchaseHistoryDTO sales) {
        LkCard card = new LkCard().pad(0);
        LkGrid grid = new LkGrid()
            .col("Date",    "date")
            .col("Event",   "evt")
            .col("Buyer",   "buyer")
            .col("Tickets", "tickets", LkGrid.Align.RIGHT)
            .col("Total",   "total",   LkGrid.Align.RIGHT);

        if (sales.records().isEmpty()) {
            card.add(Lk.muted("No sales yet."));
            return card;
        }

        for (PurchaseHistoryDTO.PurchaseRecordDTO r : sales.records()) {
            Map<String, Object> row = new LinkedHashMap<>();

            String date = r.purchasedAt() != null
                    ? r.purchasedAt().format(DATE_FMT) : "—";
            row.put("date", date);

            String eventName = r.tickets().stream()
                    .map(PurchaseHistoryDTO.TicketRecordDTO::eventName)
                    .filter(n -> n != null)
                    .findFirst().orElse("—");
            Span ev = new Span();
            ev.getElement().setProperty("innerHTML", "<b>" + escape(eventName) + "</b>");
            row.put("evt", ev);

            String buyer = r.buyerName() != null ? r.buyerName()
                    : r.guestEmail() != null ? r.guestEmail() : "Guest";
            row.put("buyer", Lk.mono(buyer));

            row.put("tickets", String.valueOf(r.tickets().size()));

            Span total = new Span();
            total.getElement().setProperty("innerHTML",
                    "<b>" + Money.format(Money.toCents(r.totalPaid())) + "</b>");

            grid.row(row);
        }

        grid.build();
        card.add(grid);
        return card;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}