package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Core.Application.dto.EventDetailDTO;
import com.ticketing.system.Core.Application.services.EventManagementService;
import com.ticketing.system.Core.Domain.events.EventStatus;
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
import com.ticketing.system.Presentation.layouts.WorkspaceLayout;
import com.ticketing.system.Presentation.security.Capability;
import com.ticketing.system.Presentation.security.MockAuth;
import com.ticketing.system.Presentation.security.RequireCapability;
import com.ticketing.system.Presentation.session.MockSession;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Route(value = "owner/events", layout = WorkspaceLayout.class)
@PageTitle("My events · TicketHub")
@PermitAll
@RequireCapability(Capability.VIEW_COMPANY_EVENTS)
public class CompanyEventListView extends LkPage {
    private final EventManagementService eventService;

    private record EventRow(String name, String date, String venue, String sold,
                            LkStatusDot.Tone tone, String status, String id) { }

    private static final List<EventRow> EVENTS = List.of(
        new EventRow("Coldplay · MOTS",            "26 Jun", "Park HaYarkon",         "38,420 / 45,000", LkStatusDot.Tone.ok,    "Live",          "coldplay"),
        new EventRow("Coldplay · MOTS (2nd night)", "27 Jun", "Park HaYarkon",         "12,090 / 45,000", LkStatusDot.Tone.ok,    "Live",          "coldplay-2"),
        new EventRow("Mashina · 35-Year Tour",     "5 Jul",  "TLV Convention Center", "4,330 / 6,000",   LkStatusDot.Tone.warn,  "Selling fast",  "mashina"),
        new EventRow("Eden Hason · Live",          "20 Jul", "Caesarea Amphitheatre", "0 / 3,800",       LkStatusDot.Tone.muted, "Draft",         "eden-hason"),
        new EventRow("NYE Festival 2026",          "31 Dec", "Rishon Park",           "—",               LkStatusDot.Tone.muted, "Draft",         "nye-2026")
    );


    public CompanyEventListView(EventManagementService eventService) {
        this.eventService = eventService;
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
        Span hint = Lk.muted("Row actions → Edit metadata · Venue map · Policies · Sales · Cancel.");
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
            .col("Status",       "status")
            .col("Actions",      "act", LkGrid.Align.RIGHT);

        String token = MockAuth.token();
        String companyIdStr = MockSession.currentCompanyId();

        if (token != null && companyIdStr != null) {
            try {
                List<EventDetailDTO> events =
                    eventService.listEventsForCompany(token, Integer.parseInt(companyIdStr));
                for (EventDetailDTO ev : events) {
                    addEventRow(grid, ev);
                }
            } catch (Exception ex) {
                card.add(Lk.muted("Could not load events: " + ex.getMessage()));
                return card;
            }
        } else {
            // fallback: keep one row so the page isn't empty while MockAuth is not set
            card.add(Lk.muted("Sign in to view events."));
            return card;
        }

        grid.build();
        card.add(grid);
        return card;
    }

    private void addEventRow(LkGrid grid, EventDetailDTO ev) {
        
        Map<String, Object> row = new LinkedHashMap<>();
        Span name = new Span();
        name.getElement().setProperty("innerHTML", "<b>" + escape(ev.name()) + "</b>");
        row.put("name", name);

        String date = ev.showDates() != null && !ev.showDates().isEmpty()
            ? ev.showDates().get(0).toString() : "—";
        row.put("date", date);

        String venue = ev.location() != null ? ev.location().toString() : "—";
        row.put("venue", venue);

        LkStatusDot.Tone tone = ev.status() == EventStatus.ON_SALE ? LkStatusDot.Tone.ok
            : ev.status() == EventStatus.DRAFT ? LkStatusDot.Tone.muted
            : LkStatusDot.Tone.warn;
        row.put("status", new LkStatusDot(tone, ev.status().name()));

        LkRow actions = new LkRow().gap(4).noWrap();
        actions.add(
            iconBtn("edit",    "Edit metadata", () -> UI.getCurrent().navigate("owner/events/" + ev.eventId())),
            iconBtn("map",     "Venue map",     () -> UI.getCurrent().navigate("owner/venue/" + ev.eventId())),
            iconBtn("policy",  "Policies",      () -> UI.getCurrent().navigate(PurchasePolicyEditorView.class)),
            iconBtn("warning", "Cancel event",  () -> Toasts.warn("Cancel-event dialog — V2-CADMIN-EVT-CANCEL."))
        );
        row.put("act", actions);
        grid.row(row);
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
