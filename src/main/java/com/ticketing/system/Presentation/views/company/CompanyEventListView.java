package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkFilterChip;
import com.ticketing.system.Presentation.components.kit.LkGrid;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkIconBtn;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.components.kit.LkStatusDot;
import com.ticketing.system.Presentation.layouts.AdminLayout;
import com.ticketing.system.Presentation.security.RequiresOwnerCompany;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Route(value = "owner/events", layout = AdminLayout.class)
@PageTitle("My events · TicketHub")
@PermitAll
public class CompanyEventListView extends LkPage implements RequiresOwnerCompany {

    private record EventRow(String name, String date, String venue, String sold,
                            LkStatusDot.Tone tone, String status, String id) { }

    private static final List<EventRow> EVENTS = List.of(
        new EventRow("Coldplay · MOTS",            "26 Jun", "Park HaYarkon",         "38,420 / 45,000", LkStatusDot.Tone.ok,    "Live",          "coldplay"),
        new EventRow("Coldplay · MOTS (2nd night)", "27 Jun", "Park HaYarkon",         "12,090 / 45,000", LkStatusDot.Tone.ok,    "Live",          "coldplay-2"),
        new EventRow("Mashina · 35-Year Tour",     "5 Jul",  "TLV Convention Center", "4,330 / 6,000",   LkStatusDot.Tone.warn,  "Selling fast",  "mashina"),
        new EventRow("Eden Hason · Live",          "20 Jul", "Caesarea Amphitheatre", "0 / 3,800",       LkStatusDot.Tone.muted, "Draft",         "eden-hason"),
        new EventRow("NYE Festival 2026",          "31 Dec", "Rishon Park",           "—",               LkStatusDot.Tone.muted, "Draft",         "nye-2026")
    );

    public CompanyEventListView() {
        title("My events");
        subtitle("All events under the selected company.");
        actions(
            new LkBtn("Bulk export").variant(LkBtn.Variant.secondary)
                .onClick(e -> Toasts.success("Event list exported to CSV (mock).")),
            new LkBtn("New event").variant(LkBtn.Variant.primary)
                .icon(new LkIcon("plus", 15))
                .onClick(e -> UI.getCurrent().navigate("owner/events/new"))
        );

        add(buildFilters());
        add(buildGridCard());
        Span hint = Lk.muted("Row actions → Edit metadata · Venue map · Policies · Sales · Cancel (opens refund-all dialog).");
        hint.getStyle().set("font-size", "12.5px");
        add(hint);
    }

    private Component buildFilters() {
        LkRow row = new LkRow().gap(8);
        row.add(
            new LkFilterChip("Status", List.of("Live", "Selling fast", "Draft", "Sold out", "Cancelled")),
            new LkFilterChip("Date range", List.of("Any time", "This week", "This month", "Next 3 months", "Past events"), true, List.of()),
            new LkFilterChip("Venue", List.of(
                "Park HaYarkon", "Bloomfield Stadium", "Teddy Stadium", "Habima Theatre",
                "Caesarea Amphitheatre", "TLV Convention Center"))
        );
        return row;
    }

    private Component buildGridCard() {
        LkCard card = new LkCard().pad(0);
        LkGrid grid = new LkGrid()
            .col("Event",        "name")
            .col("Date",         "date")
            .col("Venue",        "venue")
            .col("Tickets sold", "sold")
            .col("Status",       "status")
            .col("Actions",      "act", LkGrid.Align.RIGHT);

        for (EventRow ev : EVENTS) {
            Map<String, Object> row = new LinkedHashMap<>();
            Span name = new Span();
            name.getElement().setProperty("innerHTML", "<b>" + escape(ev.name) + "</b>");
            row.put("name", name);
            row.put("date", ev.date);
            row.put("venue", ev.venue);
            row.put("sold", ev.sold);
            row.put("status", new LkStatusDot(ev.tone, ev.status));

            LkRow actions = new LkRow().gap(4).noWrap();
            actions.add(
                iconBtn("edit",    "Edit metadata", () -> UI.getCurrent().navigate("owner/events/" + ev.id)),
                iconBtn("map",     "Venue map",     () -> UI.getCurrent().navigate("owner/venue/" + ev.id)),
                iconBtn("policy",  "Policies",      () -> UI.getCurrent().navigate(PurchasePolicyEditorView.class)),
                iconBtn("chart",   "Sales",         () -> Toasts.warn("Per-event sales filter — V2-VIEW-02.")),
                iconBtn("warning", "Cancel event",  () -> Toasts.warn("Cancel-event dialog (V2-CADMIN-EVT-CANCEL) refunds all holders."))
            );
            row.put("act", actions);
            grid.row(row);
        }
        grid.build();
        card.add(grid);
        return card;
    }

    private static LkIconBtn iconBtn(String iconName, String tooltip, Runnable r) {
        LkIconBtn b = new LkIconBtn(new LkIcon(iconName, 15), tooltip);
        b.addClickListener(e -> r.run());
        return b;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
