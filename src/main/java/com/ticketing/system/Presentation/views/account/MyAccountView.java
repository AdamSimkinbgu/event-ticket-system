package com.ticketing.system.Presentation.views.account;

import com.ticketing.system.Presentation.components.buyer.BzRefundDialog;
import com.ticketing.system.Presentation.components.buyer.BzTicketDialog;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkGrid;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkIconBtn;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.components.kit.LkStatusDot;
import com.ticketing.system.Presentation.layouts.MainLayout;
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

import java.util.LinkedHashMap;
import java.util.Map;

@Route(value = "my-account", layout = MainLayout.class)
@PageTitle("My account · TicketHub")
@PermitAll
public class MyAccountView extends LkPage {

    public MyAccountView() {
        add(buildHero());
        add(Lk.h2("My orders"));
        add(buildOrdersCard());
        add(Lk.h2("My tickets"));
        add(buildTicketsCard());
    }

    private Component buildHero() {
        String name = AuthSession.displayName();
        if (name == null || name.isBlank()) name = "Alex";
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

        Div stats = new Div();
        stats.addClassName("acct-hero-stats");
        stats.add(statBlock("12", "orders"), statBlock("3", "upcoming"));

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
        LkGrid grid = new LkGrid()
            .col("Order",   "id")
            .col("Event",   "evt")
            .col("Date",    "date")
            .col("Total",   "total", LkGrid.Align.RIGHT)
            .col("Status",  "status")
            .col("Receipt", "receipt", LkGrid.Align.RIGHT);

        order(grid, "TKT-20847", "Coldplay · Music of the Spheres", "26 Jun 2026", "$504.00", "Paid",     LkStatusDot.Tone.ok);
        order(grid, "TKT-20713", "Hapoel TLV vs Maccabi Haifa",     "28 Jun 2026", "$160.00", "Paid",     LkStatusDot.Tone.ok);
        order(grid, "TKT-20566", "Othello at Habima",               "30 May 2026", "$120.00", "Refunded", LkStatusDot.Tone.muted);
        grid.build();
        card.add(grid);
        return card;
    }

    private void order(LkGrid grid, String id, String event, String date, String total, String status, LkStatusDot.Tone tone) {
        Map<String, Object> row = new LinkedHashMap<>();
        Span idSpan = new Span();
        idSpan.addClassName("lk-mono");
        idSpan.getElement().setProperty("innerHTML", "<b>#" + id + "</b>");
        row.put("id", idSpan);
        row.put("evt", event);
        row.put("date", date);
        row.put("total", total);
        row.put("status", new LkStatusDot(tone, status));
        NativeButton view = new NativeButton("View");
        view.addClassName("bz-link");
        view.getStyle()
            .set("background", "none").set("border", "none").set("padding", "0").set("cursor", "pointer");
        view.addClickListener(e -> UI.getCurrent().navigate("receipt/" + id));
        row.put("receipt", view);
        grid.row(row);
    }

    private Component buildTicketsCard() {
        LkCard card = new LkCard().pad(0);
        LkGrid grid = new LkGrid()
            .col("Event",  "evt")
            .col("Date",   "date")
            .col("Zone",   "zone")
            .col("Seat",   "seat")
            .col("Status", "status")
            .col("",       "act", LkGrid.Align.RIGHT);

        ticket(grid, "Coldplay · MOTS", "26 Jun · 20:00", "Lower L", "C-14", "Valid",
            new BzTicketDialog.TicketInfo("Coldplay · Music of the Spheres", "Concert",
                "Thu 26 Jun 2026 · 20:00", "Park HaYarkon, Tel Aviv",
                "Lower L", "Row C · Seat 14", "$160.00", "7B2K-4Q", "TKT-20847"));
        ticket(grid, "Coldplay · MOTS", "26 Jun · 20:00", "Lower L", "C-15", "Valid",
            new BzTicketDialog.TicketInfo("Coldplay · Music of the Spheres", "Concert",
                "Thu 26 Jun 2026 · 20:00", "Park HaYarkon, Tel Aviv",
                "Lower L", "Row C · Seat 15", "$160.00", "7B2K-4R", "TKT-20847"));
        ticket(grid, "Hapoel TLV",      "28 Jun · 21:00", "GA",      "—",    "Valid",
            new BzTicketDialog.TicketInfo("Hapoel TLV vs Maccabi Haifa", "Sport",
                "Sat 28 Jun 2026 · 21:00", "Bloomfield Stadium, Tel Aviv",
                "General Admission", "—", "$80.00", "9F1A-2M", "TKT-20713"));
        grid.build();
        card.add(grid);
        return card;
    }

    private void ticket(LkGrid grid, String event, String date, String zone, String seat, String status,
                        BzTicketDialog.TicketInfo info) {
        Map<String, Object> row = new LinkedHashMap<>();
        Span ev = new Span();
        ev.getElement().setProperty("innerHTML", "<b>" + escape(event) + "</b>");
        row.put("evt", ev);
        row.put("date", date);
        row.put("zone", zone);
        row.put("seat", seat);
        row.put("status", new LkStatusDot(LkStatusDot.Tone.ok, status));

        LkIconBtn viewBtn = new LkIconBtn(new LkIcon("eye", 15), "View ticket + barcode");
        viewBtn.addClickListener(e -> BzTicketDialog.show(info));

        LkIconBtn refundBtn = new LkIconBtn(new LkIcon("card", 15), "Request refund");
        refundBtn.addClickListener(e -> BzRefundDialog.show(info));

        LkRow actions = new LkRow().gap(4).noWrap();
        actions.add(viewBtn, refundBtn);
        row.put("act", actions);
        grid.row(row);
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
