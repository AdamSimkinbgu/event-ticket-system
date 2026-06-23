package com.ticketing.system.Presentation.views.account;

import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO.PurchaseRecordDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO.TicketRecordDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO.TransactionRecordDTO;
import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.buyer.BzTicketDialog;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBadge;
import com.ticketing.system.Presentation.components.kit.LkBanner;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkCol;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkIconBtn;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.presenters.account.ReceiptPresenter;
import com.ticketing.system.Presentation.session.SessionIdentity;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Full-page receipt for one member-owned order (#276). Reached by clicking "View" on the
 * {@code MyAccountView} orders grid ({@code receipt/{orderReceiptId}}). Loads the real receipt
 * through {@link ReceiptPresenter}; the view never calls the service directly nor uses
 * {@code try/catch} — it switches on the presenter's sealed {@code Outcome}. A receipt that isn't
 * the signed-in member's renders a 403 banner.
 */
@Route(value = "receipt/:receiptId", layout = MainLayout.class)
@PageTitle("Receipt · TicketHub")
@PermitAll
public class ReceiptView extends LkPage implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm");
    private static final DateTimeFormatter EVENT_FMT = DateTimeFormatter.ofPattern("EEE d MMM yyyy · HH:mm");

    private final ReceiptPresenter presenter;
    private final SessionIdentity sessionIdentity;

    private final Div bodyHolder = new Div();
    private int receiptId;

    public ReceiptView(ReceiptPresenter presenter, SessionIdentity sessionIdentity) {
        this.presenter = presenter;
        this.sessionIdentity = sessionIdentity;
        add(bodyHolder);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        this.receiptId = event.getRouteParameters().get("receiptId")
                .map(s -> { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; } })
                .orElse(0);
        loadAndBuild();
    }

    private void loadAndBuild() {
        bodyHolder.removeAll();
        title("Receipt");
        subtitle(null);
        actions();

        if (receiptId <= 0) {
            bodyHolder.add(infoBanner("We couldn't find that receipt."));
            return;
        }

        switch (presenter.load(sessionIdentity.memberToken(), receiptId)) {
            case ReceiptPresenter.Outcome.Success s   -> renderReceipt(s.receipt());
            case ReceiptPresenter.Outcome.NotFound nf -> bodyHolder.add(infoBanner(nf.message()));
            case ReceiptPresenter.Outcome.Forbidden f -> bodyHolder.add(infoBanner(f.message()));
            case ReceiptPresenter.Outcome.Failure f   -> bodyHolder.add(infoBanner(f.message()));
        }
    }

    private void renderReceipt(PurchaseRecordDTO receipt) {
        title("Receipt #" + receipt.orderReceiptId());
        subtitle(headerSubtitle(receipt));
        actions(buildHeaderActions());

        bodyHolder.add(buildStatusCard(receipt));
        bodyHolder.add(Lk.h2("Items"));
        bodyHolder.add(buildItemsCard(receipt));
        bodyHolder.add(Lk.h2("Payment"));
        bodyHolder.add(buildPaymentCard(receipt));
        bodyHolder.add(Lk.h2("Tickets"));
        bodyHolder.add(buildTicketsCard(receipt));
    }

    // ---- header ----

    private String headerSubtitle(PurchaseRecordDTO receipt) {
        String when = receipt.purchasedAt() == null ? "—" : receipt.purchasedAt().format(DATE_FMT);
        TransactionRecordDTO charge = paymentCharge(receipt);
        return charge == null ? when : when + "  ·  Transaction " + charge.externalTransactionId();
    }

    private Component buildHeaderActions() {
        LkRow actions = new LkRow().gap(8);
        actions.add(
            new LkBtn("Print").variant(LkBtn.Variant.secondary)
                .icon(new LkIcon("copy", 15))
                .onClick(e -> UI.getCurrent().getPage().executeJs("window.print()")),
            new LkBtn("Email receipt").variant(LkBtn.Variant.tertiary)
                .onClick(e -> Toasts.success("Receipt resent to your account email (mock)."))
        );
        return actions;
    }

    // ---- status / summary header ----

    private Component buildStatusCard(PurchaseRecordDTO receipt) {
        LkCard card = new LkCard().pad(18);
        Div row = new Div();
        row.getStyle()
            .set("display", "grid")
            .set("grid-template-columns", "repeat(auto-fit, minmax(140px, 1fr))")
            .set("gap", "18px");

        LkBadge statusBadge = new LkBadge(receipt.refunded() ? "Refunded" : "Paid",
            receipt.refunded() ? LkBadge.Tone.muted : LkBadge.Tone.success).small();

        Span totalSpan = new Span();
        totalSpan.getElement().setProperty("innerHTML",
            "<b style='font-size:1.1rem;color:#0f172a'>" + money(receipt.totalPaid()) + "</b>");

        int ticketCount = receipt.tickets().size();

        row.add(
            statBlock("Status", statusBadge),
            statBlock("Date",   new Span(receipt.purchasedAt() == null ? "—" : receipt.purchasedAt().format(DATE_FMT))),
            statBlock("Items",  new Span(ticketCount + " ticket" + (ticketCount == 1 ? "" : "s"))),
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

    // ---- items (one line per ticket) ----

    private Component buildItemsCard(PurchaseRecordDTO receipt) {
        LkCard card = new LkCard().pad(0);
        if (receipt.tickets().isEmpty()) {
            card.pad(18).add(Lk.muted("No line items on this order."));
            return card;
        }
        Div lines = new Div();
        lines.addClassName("bz-order-lines");
        for (TicketRecordDTO t : receipt.tickets()) {
            Div row = new Div();
            row.addClassName("bz-order-line");
            row.add(new Span(itemLabel(t)));
            Span price = new Span();
            price.getElement().setProperty("innerHTML", "<b>" + money(t.pricePaid()) + "</b>");
            row.add(price);
            lines.add(row);
        }
        card.add(lines);
        return card;
    }

    private static String itemLabel(TicketRecordDTO t) {
        String evt = orElse(t.eventName(), "Event #" + t.eventId());
        String zone = orElse(t.zoneName(), "Zone #" + t.zoneId());
        String seat = orElse(t.seatNumber(), null);
        return seat == null ? evt + " · " + zone : evt + " · " + zone + " · " + seat;
    }

    // ---- payment ----

    private Component buildPaymentCard(PurchaseRecordDTO receipt) {
        LkCard card = new LkCard().pad(18);
        LkCol col = new LkCol().gap(8);

        double subtotal = receipt.tickets().stream().mapToDouble(TicketRecordDTO::pricePaid).sum();
        double fee = Math.max(0, receipt.totalPaid() - subtotal);

        col.add(paymentRow("Subtotal", money(subtotal), false));
        if (fee > 0.0001) {
            col.add(paymentRow("Service fee", money(fee), false));
        }
        col.add(Lk.divider());
        col.add(paymentRow(receipt.refunded() ? "Total refunded" : "Total paid",
                           money(receipt.totalPaid()), true));

        TransactionRecordDTO charge = paymentCharge(receipt);
        if (charge != null) {
            Span method = Lk.muted("Paid with " + charge.providerName()
                + (charge.currency() == null || charge.currency().isBlank() ? "" : " (" + charge.currency() + ")"));
            method.getStyle().set("font-size", "12.5px").set("display", "block").set("margin-top", "4px");
            col.add(method);
        }
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

    private Component buildTicketsCard(PurchaseRecordDTO receipt) {
        LkCard card = new LkCard().pad(0);
        if (receipt.tickets().isEmpty()) {
            card.pad(18).add(Lk.muted("No tickets on this order."));
            return card;
        }
        for (TicketRecordDTO t : receipt.tickets()) {
            card.add(buildTicketRow(t));
        }
        return card;
    }

    private Div buildTicketRow(TicketRecordDTO t) {
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
        title.getElement().setProperty("innerHTML", "<b>" + escape(orElse(t.eventName(), "Event #" + t.eventId())) + "</b>");
        title.getStyle().set("display", "block").set("color", "#0f172a");
        Span meta = Lk.muted(ticketMeta(t));
        meta.getStyle().set("font-size", "13px").set("display", "block").set("margin-top", "2px");
        info.add(title, meta);

        Span code = new Span(orElse(t.barcode(), "Not yet issued"));
        code.addClassName("lk-mono");
        code.getStyle()
            .set("font-size", "0.78rem").set("color", "var(--muted)").set("letter-spacing", "0.06em");

        LkIconBtn viewBtn = new LkIconBtn(new LkIcon("eye", 15), "View ticket");
        viewBtn.addClickListener(e -> BzTicketDialog.show(toTicketInfo(t)));

        LkRow actions = new LkRow().gap(4).noWrap();
        actions.add(viewBtn);

        row.add(swatch, info, code, actions);
        return row;
    }

    private static String ticketMeta(TicketRecordDTO t) {
        String when = t.eventStartsAt() == null ? "—" : t.eventStartsAt().format(EVENT_FMT);
        return when + "  ·  " + orElse(t.zoneName(), "Zone #" + t.zoneId()) + "  ·  " + orElse(t.seatNumber(), "—");
    }

    private static BzTicketDialog.TicketInfo toTicketInfo(TicketRecordDTO t) {
        return new BzTicketDialog.TicketInfo(
            orElse(t.eventName(), "Event #" + t.eventId()),
            orElse(t.category(), "—"),
            t.eventStartsAt() == null ? "—" : t.eventStartsAt().format(EVENT_FMT),
            orElse(t.venue(), "—"),
            orElse(t.zoneName(), "Zone #" + t.zoneId()),
            orElse(t.seatNumber(), "—"),
            money(t.pricePaid()),
            orElse(t.barcode(), "Not yet issued"),
            "#" + t.orderReceiptId());
    }

    // ---- helpers ----

    private static TransactionRecordDTO paymentCharge(PurchaseRecordDTO receipt) {
        return receipt.transactions().stream()
            .filter(tx -> "PAYMENT_CHARGE".equals(tx.type()))
            .findFirst()
            .orElse(null);
    }

    private Component infoBanner(String message) {
        LkBanner banner = new LkBanner();
        banner.tone(LkBanner.Tone.info);
        banner.setIcon(new LkIcon("info", 18));
        banner.setBody(new Span(message));
        return banner;
    }

    private static String[] gradientFor(String cat) {
        return switch (cat == null ? "" : cat.toLowerCase()) {
            case "concert", "music" -> new String[]{"#8b5cf6", "#ec4899"};
            case "sport", "sports"  -> new String[]{"#0ea5e9", "#10b981"};
            case "theatre", "theater" -> new String[]{"#dc2626", "#f59e0b"};
            case "conference"       -> new String[]{"#0d9488", "#1d4ed8"};
            default                 -> new String[]{"#475569", "#1a5490"};
        };
    }

    private static String money(double amount) {
        return String.format("$%,.2f", amount);
    }

    private static String orElse(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
