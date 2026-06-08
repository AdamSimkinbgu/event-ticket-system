package com.ticketing.system.Presentation.views.admin;

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
import com.ticketing.system.Presentation.layouts.AdminLayout;
import com.ticketing.system.Presentation.security.RequiresOwnerCompany;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Route(value = "owner/sales", layout = AdminLayout.class)
@PageTitle("Company sales · TicketHub")
@PermitAll
public class CompanySalesView extends LkPage implements RequiresOwnerCompany {

    public CompanySalesView() {
        title("Company sales history");
        subtitle("Live Nation Israel  ·  immutable order receipts.");
        actions(new LkBtn("Export CSV").variant(LkBtn.Variant.secondary)
            .icon(new LkIcon("chart", 15))
            .onClick(e -> Toasts.success("Sales history exported to CSV (mock).")));

        add(buildFilters());
        add(buildStats());
        add(buildGridCard());
    }

    private Component buildFilters() {
        LkRow row = new LkRow().gap(8);
        row.add(
            new LkFilterChip("Date range", List.of("Last 7 days", "Last 30 days", "This quarter", "This year", "All time"),
                true, List.of("Last 30 days")),
            new LkFilterChip("Event", List.of("Coldplay · MOTS", "Hapoel TLV", "Mashina"), false, List.of())
        );
        return row;
    }

    private Component buildStats() {
        Div stats = new Div();
        stats.addClassName("ow-stats");
        stats.add(
            new LkStat("Total revenue · 30d", "$1.92M").delta("▲ 8%",  LkStat.Tone.up),
            new LkStat("Tickets sold",        "14,208").delta("▲ 12%", LkStat.Tone.up),
            new LkStat("Average order",       "$135"),
            new LkStat("Top event",           "Coldplay").delta("$1.1M", LkStat.Tone.up)
        );
        return stats;
    }

    private Component buildGridCard() {
        LkCard card = new LkCard().pad(0);
        LkGrid grid = new LkGrid()
            .col("Date",    "date")
            .col("Event",   "evt")
            .col("Buyer",   "buyer")
            .col("Tickets", "tickets", LkGrid.Align.RIGHT)
            .col("Total",   "total",   LkGrid.Align.RIGHT);

        sale(grid, "26 Jun 2026", "Coldplay · MOTS",    "alex.morgan", "2", "$320.00");
        sale(grid, "26 Jun 2026", "Coldplay · MOTS",    "noa.levi",    "4", "$640.00");
        sale(grid, "25 Jun 2026", "Hapoel TLV",         "tom.azoulay", "2", "$160.00");
        sale(grid, "24 Jun 2026", "Mashina · 35-Year",  "eitan.bar",   "6", "$1,080.00");
        sale(grid, "23 Jun 2026", "Coldplay · MOTS",    "maya.gold",   "1", "$160.00");

        grid.build();
        card.add(grid);
        return card;
    }

    private void sale(LkGrid grid, String date, String event, String buyer, String tickets, String total) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("date", date);
        Span ev = new Span();
        ev.getElement().setProperty("innerHTML", "<b>" + event + "</b>");
        row.put("evt", ev);
        row.put("buyer", Lk.mono(buyer));
        row.put("tickets", tickets);
        Span t = new Span();
        t.getElement().setProperty("innerHTML", "<b>" + total + "</b>");
        row.put("total", t);
        grid.row(row);
    }
}
