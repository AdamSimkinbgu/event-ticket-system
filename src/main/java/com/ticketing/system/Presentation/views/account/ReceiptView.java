package com.ticketing.system.Presentation.views.account;

import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.buyer.BzRefundDialog;
import com.ticketing.system.Presentation.components.buyer.BzTicketDialog;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBadge;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkCol;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkIconBtn;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Full-page receipt view for a past order. Reached by clicking "View"
 * on the {@code MyAccountView} orders grid. Looks up the receipt by
 * route parameter; each row's per-ticket action buttons open the same
 * {@code BzTicketDialog} and {@code BzRefundDialog} the account screen
 * uses.
 */
@Route(value = "receipt/:receiptId", layout = MainLayout.class)
@PageTitle("Receipt · TicketHub")
@PermitAll
public class ReceiptView extends LkPage implements BeforeEnterObserver {

    private record OrderLine(String label, int priceCents) { }

    private record ReceiptData(
        String id, String transactionId, String date, String status, boolean refunded,
        int subtotalCents, int feeCents, int totalCents, String paymentMethod,
        List<OrderLine> lines, List<BzTicketDialog.TicketInfo> tickets
    ) { }

    private static final Map<String, ReceiptData> RECEIPTS = new LinkedHashMap<>();
    static {
        RECEIPTS.put("TKT-20847", new ReceiptData(
            "TKT-20847", "4c1f-9a2d", "26 Jun 2026 · 14:08", "Paid", false,
            48000, 2400, 50400, "Visa •••• 4242",
            List.of(
                new OrderLine("Coldplay · MOTS · Lower L · Row C · Seat 14", 16000),
                new OrderLine("Coldplay · MOTS · Lower L · Row C · Seat 15", 16000),
                new OrderLine("Hapoel TLV · 2 × General Admission",          16000)),
            List.of(
                new BzTicketDialog.TicketInfo("Coldplay · Music of the Spheres", "Concert",
                    "Thu 26 Jun 2026 · 20:00", "Park HaYarkon, Tel Aviv",
                    "Lower L", "Row C · Seat 14", "$160.00", "7B2K-4Q", "TKT-20847"),
                new BzTicketDialog.TicketInfo("Coldplay · Music of the Spheres", "Concert",
                    "Thu 26 Jun 2026 · 20:00", "Park HaYarkon, Tel Aviv",
                    "Lower L", "Row C · Seat 15", "$160.00", "7B2K-4R", "TKT-20847"),
                new BzTicketDialog.TicketInfo("Hapoel TLV vs Maccabi Haifa", "Sport",
                    "Sat 28 Jun 2026 · 21:00", "Bloomfield Stadium, Tel Aviv",
                    "General Admission", "—", "$80.00", "9F1A-2M", "TKT-20847"),
                new BzTicketDialog.TicketInfo("Hapoel TLV vs Maccabi Haifa", "Sport",
                    "Sat 28 Jun 2026 · 21:00", "Bloomfield Stadium, Tel Aviv",
                    "General Admission", "—", "$80.00", "9F1A-2N", "TKT-20847"))));

        RECEIPTS.put("TKT-20713", new ReceiptData(
            "TKT-20713", "9b3d-1f4c", "20 Jun 2026 · 11:22", "Paid", false,
            16000, 0, 16000, "Mastercard •••• 8721",
            List.of(new OrderLine("Hapoel TLV vs Maccabi Haifa · 2 × GA", 16000)),
            List.of(
                new BzTicketDialog.TicketInfo("Hapoel TLV vs Maccabi Haifa", "Sport",
                    "Sat 28 Jun 2026 · 21:00", "Bloomfield Stadium, Tel Aviv",
                    "General Admission", "—", "$80.00", "9F1A-2M", "TKT-20713"),
                new BzTicketDialog.TicketInfo("Hapoel TLV vs Maccabi Haifa", "Sport",
                    "Sat 28 Jun 2026 · 21:00", "Bloomfield Stadium, Tel Aviv",
                    "General Admission", "—", "$80.00", "9F1A-2N", "TKT-20713"))));

        RECEIPTS.put("TKT-20566", new ReceiptData(
            "TKT-20566", "2a5e-0c91", "12 May 2026 · 19:47", "Refunded", true,
            12000, 0, 12000, "Visa •••• 4242",
            List.of(new OrderLine("Othello at Habima · Stalls · Seat 18", 12000)),
            List.of(
                new BzTicketDialog.TicketInfo("Othello at Habima", "Theatre",
                    "Mon 30 May 2026 · 20:30", "Habima National Theatre, Tel Aviv",
                    "Stalls", "Row F · Seat 18", "$120.00", "3M4Q-7P", "TKT-20566"))));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String id = event.getRouteParameters().get("receiptId").orElse("TKT-20847");
        ReceiptData receipt = RECEIPTS.getOrDefault(id, RECEIPTS.get("TKT-20847"));

        title("Receipt #" + receipt.id);
        subtitle(receipt.date + "  ·  Transaction " + receipt.transactionId);
        actions(buildHeaderActions());

        add(buildStatusCard(receipt));
        add(Lk.h2("Items"));
        add(buildItemsCard(receipt));
        add(Lk.h2("Payment"));
        add(buildPaymentCard(receipt));
        add(Lk.h2("Tickets"));
        add(buildTicketsCard(receipt));
    }

    // ---- header actions ----

    private Component buildHeaderActions() {
        LkRow actions = new LkRow().gap(8);
        actions.add(
            new LkBtn("Download PDF").variant(LkBtn.Variant.secondary)
                .icon(new LkIcon("copy", 15))
                .onClick(e -> Toasts.success("Receipt PDF queued for download (mock).")),
            new LkBtn("Email receipt").variant(LkBtn.Variant.tertiary)
                .onClick(e -> Toasts.success("Receipt resent to alex.morgan@email.com."))
        );
        return actions;
    }

    // ---- status / summary header ----

    private Component buildStatusCard(ReceiptData receipt) {
        LkCard card = new LkCard().pad(18);
        Div row = new Div();
        row.getStyle()
            .set("display", "grid")
            .set("grid-template-columns", "repeat(auto-fit, minmax(140px, 1fr))")
            .set("gap", "18px");

        LkBadge statusBadge = new LkBadge(receipt.status,
            receipt.refunded ? LkBadge.Tone.muted : LkBadge.Tone.success).small();

        Span totalSpan = new Span();
        totalSpan.getElement().setProperty("innerHTML",
            "<b style='font-size:1.1rem;color:#0f172a'>" + formatPrice(receipt.totalCents) + "</b>");

        row.add(
            statBlock("Status", statusBadge),
            statBlock("Date",   new Span(receipt.date)),
            statBlock("Items",  new Span(receipt.lines.size() + " line" + (receipt.lines.size() == 1 ? "" : "s"))),
            statBlock("Total",  totalSpan)
        );
        card.add(row);
        return card;
    }

    private Div statBlock(String label, Component value) {
        Div block = new Div();
        Span l = new Span(label.toUpperCase());
        l.getStyle()
            .set("font-size", "0.66rem").set("font-weight", "800").set("letter-spacing", "0.1em")
            .set("color", "var(--muted)").set("display", "block").set("margin-bottom", "4px");
        block.add(l, value);
        return block;
    }

    // ---- items ----

    private Component buildItemsCard(ReceiptData receipt) {
        LkCard card = new LkCard().pad(0);
        Div lines = new Div();
        lines.addClassName("bz-order-lines");
        for (OrderLine line : receipt.lines) {
            Div row = new Div();
            row.addClassName("bz-order-line");
            row.add(new Span(line.label));
            Span price = new Span();
            price.getElement().setProperty("innerHTML", "<b>" + formatPrice(line.priceCents) + "</b>");
            row.add(price);
            lines.add(row);
        }
        card.add(lines);
        return card;
    }

    // ---- payment ----

    private Component buildPaymentCard(ReceiptData receipt) {
        LkCard card = new LkCard().pad(18);
        LkCol col = new LkCol().gap(8);
        col.add(paymentRow("Subtotal",       formatPrice(receipt.subtotalCents), false));
        col.add(paymentRow("Service fee",    formatPrice(receipt.feeCents),      false));
        col.add(Lk.divider());
        col.add(paymentRow(receipt.refunded ? "Total refunded" : "Total paid",
                           formatPrice(receipt.totalCents), true));
        Span method = Lk.muted("Paid with " + receipt.paymentMethod);
        method.getStyle().set("font-size", "12.5px").set("display", "block").set("margin-top", "4px");
        col.add(method);
        card.add(col);
        return card;
    }

    private Component paymentRow(String label, String value, boolean bold) {
        LkRow row = new LkRow().justify("space-between");
        if (bold) {
            Span l = new Span(); l.getElement().setProperty("innerHTML",
                "<b style='font-size:1rem'>" + label + "</b>");
            Span v = new Span(); v.getElement().setProperty("innerHTML",
                "<b style='font-size:1rem;color:#0f172a'>" + value + "</b>");
            row.add(l, v);
        } else {
            row.add(Lk.muted(label), new Span(value));
        }
        return row;
    }

    // ---- tickets list ----

    private Component buildTicketsCard(ReceiptData receipt) {
        LkCard card = new LkCard().pad(0);
        for (BzTicketDialog.TicketInfo ticket : receipt.tickets) {
            card.add(buildTicketRow(ticket, receipt.refunded));
        }
        return card;
    }

    private Div buildTicketRow(BzTicketDialog.TicketInfo t, boolean refunded) {
        Div row = new Div();
        row.getStyle()
            .set("display", "flex").set("align-items", "center").set("gap", "16px")
            .set("padding", "14px 18px").set("border-bottom", "1px solid #f1f5f9");

        Div swatch = new Div();
        String[] grad = gradientFor(t.category());
        swatch.getStyle()
            .set("width", "44px").set("height", "44px")
            .set("border-radius", "8px").set("flex", "none")
            .set("background", "linear-gradient(135deg, " + grad[0] + ", " + grad[1] + ")");

        Div info = new Div();
        info.getStyle().set("flex", "1 1 auto").set("min-width", "0");
        Span title = new Span();
        title.getElement().setProperty("innerHTML", "<b>" + escape(t.event()) + "</b>");
        title.getStyle().set("display", "block").set("color", "#0f172a");
        Span meta = Lk.muted(t.date() + "  ·  " + t.zone() + "  ·  " + t.seat());
        meta.getStyle().set("font-size", "13px").set("display", "block").set("margin-top", "2px");
        info.add(title, meta);

        Span code = new Span(t.barcode());
        code.addClassName("lk-mono");
        code.getStyle()
            .set("font-size", "0.78rem").set("color", "var(--muted)").set("letter-spacing", "0.06em");

        LkIconBtn viewBtn = new LkIconBtn(new LkIcon("eye", 15), "View ticket");
        viewBtn.addClickListener(e -> BzTicketDialog.show(t));

        LkRow actions = new LkRow().gap(4).noWrap();
        actions.add(viewBtn);
        if (!refunded) {
            LkIconBtn refundBtn = new LkIconBtn(new LkIcon("card", 15), "Request refund");
            refundBtn.addClickListener(e -> BzRefundDialog.show(t));
            actions.add(refundBtn);
        }

        row.add(swatch, info, code, actions);
        return row;
    }

    private static String[] gradientFor(String cat) {
        return switch (cat == null ? "" : cat.toLowerCase()) {
            case "concert"    -> new String[]{"#8b5cf6", "#ec4899"};
            case "sport"      -> new String[]{"#0ea5e9", "#10b981"};
            case "theatre"    -> new String[]{"#dc2626", "#f59e0b"};
            case "conference" -> new String[]{"#0d9488", "#1d4ed8"};
            default           -> new String[]{"#475569", "#1a5490"};
        };
    }

    private static String formatPrice(int cents) {
        return "$" + (cents / 100) + "." + String.format("%02d", cents % 100);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
