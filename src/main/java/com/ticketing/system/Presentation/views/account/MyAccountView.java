package com.ticketing.system.Presentation.views.account;

import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO.PurchaseRecordDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO.TicketRecordDTO;
import com.ticketing.system.Core.Domain.Tickets.TicketStatus;
import com.ticketing.system.Presentation.components.buyer.BzTicketDialog;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkGrid;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkIconBtn;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkStatusDot;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.presenters.account.MyAccountPresenter;
import com.ticketing.system.Presentation.session.AuthSession;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Route(value = "my-account", layout = MainLayout.class)
@PageTitle("My account · TicketHub")
@PermitAll
public class MyAccountView extends LkPage {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("EEE d MMM yyyy · HH:mm");

    private final PurchaseHistoryDTO history;

    public MyAccountView(MyAccountPresenter presenter) {
        this.history = presenter.loadHistory();

        add(buildHero());
        add(Lk.h2("My orders"));
        add(buildOrdersCard());
        add(Lk.h2("My tickets"));
        add(buildTicketsCard());
    }

    private Component buildHero() {
        String name = AuthSession.displayName();
        if (name == null || name.isBlank()) name = "there";
        String first = name.split("\\s+")[0];

        Div hero = new Div();
        hero.addClassName("acct-hero");

        Span avatar = new Span(initials(name));
        avatar.addClassName("acct-hero-av");

        Div meta = new Div();
        H2 welcome = new H2("Welcome back, " + first);
        welcome.getStyle().set("margin", "0").set("color", "#fff");
        Span sub = new Span("Track your orders, view tickets, and request refunds.");
        sub.getStyle().set("color", "rgba(255,255,255,0.9)").set("font-size", "14.5px");
        meta.add(welcome, sub);

        int orderCount = history.records().size();
        long ticketCount = history.records().stream().mapToLong(r -> r.tickets().size()).sum();

        Div stats = new Div();
        stats.addClassName("acct-hero-stats");
        stats.add(statBlock(String.valueOf(orderCount), orderCount == 1 ? "order" : "orders"),
                statBlock(String.valueOf(ticketCount), ticketCount == 1 ? "ticket" : "tickets"));

        hero.add(avatar, meta, stats);
        return hero;
    }

    private Div statBlock(String value, String label) {
        Div d = new Div();
        Span v = new Span();
        v.getElement().setProperty("innerHTML", "<b>" + value + "</b>");
        Span l = new Span(label);
        d.add(v, l);
        return d;
    }

    private Component buildOrdersCard() {
        LkCard card = new LkCard().pad(0);
        if (history.records().isEmpty()) {
            card.add(Lk.muted("No orders yet — your purchases will appear here."));
            return card;
        }
        LkGrid grid = new LkGrid()
            .col("Order",   "id")
            .col("Event",   "evt")
            .col("Date",    "date")
            .col("Total",   "total", LkGrid.Align.RIGHT)
            .col("Status",  "status")
            .col("Receipt", "receipt", LkGrid.Align.RIGHT);

        history.records().stream()
            .sorted(Comparator.comparing(PurchaseRecordDTO::purchasedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .forEach(r -> orderRow(grid, r));

        grid.build();
        card.add(grid);
        return card;
    }

    private void orderRow(LkGrid grid, PurchaseRecordDTO r) {
        Map<String, Object> row = new LinkedHashMap<>();
        Span idSpan = new Span();
        idSpan.addClassName("lk-mono");
        idSpan.getElement().setProperty("innerHTML", "<b>#" + r.orderReceiptId() + "</b>");
        row.put("id", idSpan);
        row.put("evt", orderEventLabel(r));
        row.put("date", r.purchasedAt() == null ? "—" : r.purchasedAt().format(DATE_FMT));
        row.put("total", money(r.totalPaid()));
        boolean refunded = r.refunded();
        row.put("status", new LkStatusDot(refunded ? LkStatusDot.Tone.muted : LkStatusDot.Tone.ok,
                refunded ? "Refunded" : "Paid"));

        NativeButton view = new NativeButton("View");
        view.addClassName("bz-link");
        view.getStyle().set("background", "none").set("border", "none").set("padding", "0").set("cursor", "pointer");
        view.addClickListener(e -> UI.getCurrent().navigate("receipt/" + r.orderReceiptId()));
        row.put("receipt", view);
        grid.row(row);
    }

    private Component buildTicketsCard() {
        LkCard card = new LkCard().pad(0);
        List<TicketRecordDTO> tickets = history.records().stream()
            .flatMap(r -> r.tickets().stream())
            .toList();
        if (tickets.isEmpty()) {
            card.add(Lk.muted("No tickets yet."));
            return card;
        }
        LkGrid grid = new LkGrid()
            .col("Event",  "evt")
            .col("Zone",   "zone")
            .col("Seat",   "seat")
            .col("Price",  "price", LkGrid.Align.RIGHT)
            .col("Status", "status")
            .col("",       "act", LkGrid.Align.RIGHT);
        tickets.forEach(t -> ticketRow(grid, t));
        grid.build();
        card.add(grid);
        return card;
    }

    private void ticketRow(LkGrid grid, TicketRecordDTO t) {
        Map<String, Object> row = new LinkedHashMap<>();
        Span ev = new Span();
        ev.getElement().setProperty("innerHTML", "<b>" + escape(orElse(t.eventName(), "Event #" + t.eventId())) + "</b>");
        row.put("evt", ev);
        row.put("zone", orElse(t.zoneName(), "Zone #" + t.zoneId()));
        row.put("seat", orElse(t.seatNumber(), "—"));
        row.put("price", money(t.pricePaid()));
        TicketStatus st = t.currentStatus();
        row.put("status", new LkStatusDot(statusTone(st), prettyStatus(st)));

        LkIconBtn viewBtn = new LkIconBtn(new LkIcon("eye", 15), "View ticket + barcode");
        viewBtn.addClickListener(e -> BzTicketDialog.show(toTicketInfo(t)));
        row.put("act", viewBtn);
        grid.row(row);
    }

    private static BzTicketDialog.TicketInfo toTicketInfo(TicketRecordDTO t) {
        return new BzTicketDialog.TicketInfo(
            orElse(t.eventName(), "Event #" + t.eventId()),
            orElse(t.category(), "—"),
            t.eventStartsAt() == null ? "—" : t.eventStartsAt().format(DATETIME_FMT),
            orElse(t.venue(), "—"),
            orElse(t.zoneName(), "Zone #" + t.zoneId()),
            orElse(t.seatNumber(), "—"),
            money(t.pricePaid()),
            orElse(t.barcode(), "Not yet issued"),
            "#" + t.orderReceiptId());
    }

    // ---- helpers ----

    private String orderEventLabel(PurchaseRecordDTO r) {
        List<String> names = r.tickets().stream()
            .map(t -> orElse(t.eventName(), "Event #" + t.eventId()))
            .distinct().toList();
        if (names.isEmpty()) return "—";
        if (names.size() == 1) return names.get(0);
        return names.size() + " events";
    }

    private static String money(double amount) {
        return String.format("$%,.2f", amount);
    }

    private static String orElse(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static LkStatusDot.Tone statusTone(TicketStatus st) {
        if (st == null) return LkStatusDot.Tone.ok;
        return switch (st) {
            case PAID, ISSUED -> LkStatusDot.Tone.ok;
            case USED -> LkStatusDot.Tone.warn;
            case REFUNDED, VOIDED -> LkStatusDot.Tone.muted;
            default -> LkStatusDot.Tone.ok;
        };
    }

    private static String prettyStatus(TicketStatus st) {
        if (st == null) return "—";
        String s = st.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String initials(String name) {
        if (name == null || name.isBlank()) return "??";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            String p = parts[0];
            return p.substring(0, Math.min(2, p.length())).toUpperCase();
        }
        return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
