package com.ticketing.system.Presentation.views.admin;

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
import com.ticketing.system.Presentation.security.Capability;
import com.ticketing.system.Presentation.security.RequireCapability;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Route(value = "admin/global-history", layout = PlatformAdminLayout.class)
@PageTitle("Global purchase history · Admin")
@PermitAll
@RequireCapability(Capability.VIEW_GLOBAL_HISTORY)
public class GlobalHistoryView extends LkPage {

    public GlobalHistoryView() {
        title("Global purchase history");
        subtitle("Every order across the platform — filter by date, company, event, or status.");
        actions(new LkBtn("Export CSV").variant(LkBtn.Variant.secondary)
            .icon(new LkIcon("chart", 15))
            .onClick(e -> Toasts.success("Global history exported (mock).")));

        add(buildFilters());
        add(buildStats());
        add(buildGridCard());
    }

    private Component buildFilters() {
        LkRow row = new LkRow().gap(8);
        row.add(
            new LkFilterChip("Date range", List.of("Last 7 days", "Last 30 days", "This quarter", "This year", "All time"),
                true, List.of("Last 30 days")),
            new LkFilterChip("Company", List.of("Live Nation Israel", "Coca-Cola Arena", "Shuni Productions"), true, List.of()),
            new LkFilterChip("Event",   List.of("Coldplay · MOTS", "Hapoel TLV", "Mashina"), true, List.of()),
            new LkFilterChip("Status",  List.of("Paid", "Refunded", "Disputed"), true, List.of("Paid"))
        );
        return row;
    }

    private Component buildStats() {
        Div stats = new Div();
        stats.addClassName("ow-stats");
        stats.add(
            new LkStat("Total revenue · 30d", "$5.82M").delta("▲ 12%", LkStat.Tone.up),
            new LkStat("Orders",              "44,108").delta("▲ 9%",  LkStat.Tone.up),
            new LkStat("Top event",           "Coldplay").delta("$1.6M", LkStat.Tone.up),
            new LkStat("Top company",         "Live Nation").delta("$2.1M", LkStat.Tone.up)
        );
        return stats;
    }

    private Component buildGridCard() {
        LkCard card = new LkCard().pad(0);
        LkGrid grid = new LkGrid()
            .col("Date",    "date")
            .col("Buyer",   "buyer")
            .col("Event",   "evt")
            .col("Company", "co")
            .col("Total",   "total",  LkGrid.Align.RIGHT)
            .col("Status",  "status", LkGrid.Align.RIGHT);

        order(grid, "26 Jun 2026", "noa.levi",     "Coldplay · MOTS",   "Live Nation Israel", "$640.00",   "Paid");
        order(grid, "26 Jun 2026", "alex.morgan",  "Coldplay · MOTS",   "Live Nation Israel", "$320.00",   "Paid");
        order(grid, "25 Jun 2026", "tom.azoulay",  "Hapoel TLV",        "Coca-Cola Arena",    "$160.00",   "Paid");
        order(grid, "24 Jun 2026", "eitan.bar",    "Mashina · 35-Year", "Shuni Productions",  "$1,080.00", "Refunded");
        order(grid, "23 Jun 2026", "maya.gold",    "Coldplay · MOTS",   "Live Nation Israel", "$160.00",   "Paid");
        order(grid, "22 Jun 2026", "lior.adler",   "Eden Hason",        "Shuni Productions",  "$240.00",   "Disputed");

        grid.build();
        card.add(grid);
        return card;
    }

    private void order(LkGrid grid, String date, String buyer, String event, String company, String total, String status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("date", date);
        row.put("buyer", Lk.mono(buyer));
        Span ev = new Span();
        ev.getElement().setProperty("innerHTML", "<b>" + event + "</b>");
        row.put("evt", ev);
        row.put("co", company);
        Span t = new Span();
        t.getElement().setProperty("innerHTML", "<b>" + total + "</b>");
        row.put("total", t);
        row.put("status", statusBadge(status));
        grid.row(row);
    }

    private LkBadge statusBadge(String status) {
        LkBadge.Tone tone = switch (status) {
            case "Paid"     -> LkBadge.Tone.success;
            case "Refunded" -> LkBadge.Tone.warn;
            case "Disputed" -> LkBadge.Tone.error;
            default          -> LkBadge.Tone.muted;
        };
        return new LkBadge(status, tone).small();
    }
}
