package com.ticketing.system.Presentation.components.kit;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;

import java.util.List;

/**
 * Notification dropdown panel — header + scrollable list + footer link.
 * Ports the React {@code NotifPanel}. Static factory methods
 * {@link #buyer()} and {@link #admin()} produce the two reference
 * variants populated with the mock data from {@code lumo-kit.jsx}.
 */
public class LkNotifPanel extends Div {

    public record Item(String iconName, String title, String body, String time, boolean unread) { }

    public LkNotifPanel(String headerTitle, List<Item> items, String footerLabel) {
        addClassName("lk-notif");

        Div head = new Div();
        head.addClassName("lk-notif-h");
        Span title = new Span();
        title.getElement().setProperty("innerHTML", "<b>" + escape(headerTitle) + "</b>");
        NativeButton markAll = new NativeButton("Mark all read");
        markAll.addClassName("lk-link-btn");
        head.add(title, markAll);
        add(head);

        Div list = new Div();
        list.addClassName("lk-notif-list");
        for (Item it : items) {
            NativeButton row = new NativeButton();
            row.addClassName("lk-notif-item");
            if (it.unread) row.addClassName("unread");

            Span icoSlot = new Span();
            icoSlot.addClassName("lk-notif-ic");
            icoSlot.add(new LkIcon(it.iconName, 17));

            Span body = new Span();
            body.addClassName("lk-notif-body");
            Span t = new Span(it.title); t.addClassName("lk-notif-t");
            Span d = new Span(it.body);  d.addClassName("lk-notif-d");
            Span tm = new Span(it.time); tm.addClassName("lk-notif-time");
            body.add(t, d, tm);

            row.add(icoSlot, body);
            if (it.unread) {
                Span dot = new Span();
                dot.addClassName("lk-notif-dot");
                row.add(dot);
            }
            list.add(row);
        }
        add(list);

        NativeButton foot = new NativeButton(footerLabel);
        foot.addClassName("lk-notif-foot");
        add(foot);
    }

    public static LkNotifPanel buyer() {
        return new LkNotifPanel("Notifications", List.of(
            new Item("ticket", "Your Coldplay seats are confirmed", "Receipt #TKT-20847 · $504.00 paid", "2m", true),
            new Item("clock",  "Cart expiring soon",                "Hapoel TLV · 2 tickets release in 2 min", "5m", true),
            new Item("star",   "Price drop on a watched event",     "Mashina · 35-Year Tour — now from $150", "1h", true),
            new Item("card",   "Refund processed",                  "Othello at Habima · $120.00 returned", "Yesterday", false),
            new Item("bell",   "New from Live Nation Israel",       "Eden Hason · Live — on sale now", "2d", false)
        ), "View all notifications");
    }

    public static LkNotifPanel admin() {
        return new LkNotifPanel("Admin alerts", List.of(
            new Item("comment",  "5 open complaints",          "Oldest is 2 days old — review the queue",        "2d", true),
            new Item("chart",    "Daily sales report ready",   "Yesterday: $48,250 across 12 companies",         "6h", true),
            new Item("building", "New company registration",   "BlueWave Productions awaiting review",           "8h", true),
            new Item("policy",   "Policy flagged for review",  "Coldplay · MOTS age-gate needs a check",         "1d", true),
            new Item("warning",  "Refund spike detected",      "Othello at Habima — 14 refunds in 1h",           "1d", true)
        ), "View admin activity feed");
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
