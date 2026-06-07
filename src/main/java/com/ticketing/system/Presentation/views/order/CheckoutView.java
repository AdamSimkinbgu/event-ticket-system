package com.ticketing.system.Presentation.views.order;

import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBanner;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkCol;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "checkout", layout = MainLayout.class)
@PageTitle("Checkout · TicketHub")
@AnonymousAllowed
public class CheckoutView extends LkPage {

    private final TextField cardholder = new TextField("Cardholder name");
    private final TextField cardNumber = new TextField("Card number");
    private final TextField expiry     = new TextField("Expiry");
    private final TextField cvc        = new TextField("CVC");
    private final TextField coupon     = new TextField();

    public CheckoutView() {
        title("Checkout");
        add(buildCountdownBanner());
        add(buildSplit());
    }

    private Component buildCountdownBanner() {
        LkBanner banner = new LkBanner();
        banner.tone(LkBanner.Tone.warn);
        banner.setIcon(new LkIcon("clock", 17));

        Span timer = new Span("09:42");
        timer.addClassName("lk-mono");
        timer.getStyle().set("font-size", "18px").set("font-weight", "700");
        Span body = new Span();
        body.add(timer);
        Span msg = new Span(" — one timer for the whole order. Replaces the per-line cart timers.");
        msg.getStyle().set("font-size", "13.5px");
        body.add(msg);
        banner.setBody(body);

        Span action = Lk.muted("auto-releases on expiry");
        action.getStyle().set("font-size", "12.5px");
        banner.setAction(action);
        return banner;
    }

    private Component buildSplit() {
        Div split = new Div();
        split.addClassName("bz-checkout-split");
        split.add(buildOrderColumn(), buildPaymentCard());
        return split;
    }

    // -------- order summary --------

    private Component buildOrderColumn() {
        LkCol col = new LkCol().gap(14);
        LkCard order = new LkCard("Your order").pad(0);

        Div lines = new Div();
        lines.addClassName("bz-order-lines");
        lines.add(orderLine("Coldplay · Lower L · Row C · Seat 14", "$160"));
        lines.add(orderLine("Coldplay · Lower L · Row C · Seat 15", "$160"));
        lines.add(orderLine("Hapoel TLV · 2 × GA",                  "$160"));
        order.add(lines);

        Div foot = new Div();
        foot.addClassName("bz-order-foot");

        coupon.setPlaceholder("Coupon code");
        coupon.getStyle().set("flex", "1 1 auto");
        Button apply = new Button("Apply", e -> {
            if (coupon.isEmpty()) {
                Toasts.warn("Enter a coupon code first.");
            } else {
                Toasts.success("Coupon '" + coupon.getValue() + "' applied (mock — no discount in placeholder).");
            }
        });
        apply.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        LkRow couponRow = new LkRow().gap(8);
        couponRow.add(coupon, apply);
        foot.add(couponRow);

        LkCol totals = new LkCol().gap(6);
        totals.getStyle().set("margin-top", "12px");
        totals.add(line("Subtotal",    "$480.00", false));
        totals.add(line("Service fee", "$24.00",  false));
        totals.add(line("Total",       "$504.00", true));
        foot.add(totals);

        order.add(foot);
        col.add(order);
        return col;
    }

    private Component orderLine(String label, String price) {
        Div line = new Div();
        line.addClassName("bz-order-line");
        Span l = new Span(label);
        Span p = new Span();
        p.getElement().setProperty("innerHTML", "<b>" + price + "</b>");
        line.add(l, p);
        return line;
    }

    private Component line(String label, String value, boolean bold) {
        LkRow r = new LkRow().justify("space-between");
        if (bold) {
            Span l = new Span(); l.getElement().setProperty("innerHTML", "<b style='font-size:16px'>" + label + "</b>");
            Span v = new Span(); v.getElement().setProperty("innerHTML", "<b style='font-size:16px'>" + value + "</b>");
            r.add(l, v);
        } else {
            r.add(Lk.muted(label), new Span(value));
        }
        return r;
    }

    // -------- payment card --------

    private Component buildPaymentCard() {
        LkCard card = new LkCard("Payment").pad(20);
        LkCol col = new LkCol().gap(14);

        cardholder.setPlaceholder("Name on card");
        cardholder.setRequired(true);
        cardholder.setWidthFull();

        cardNumber.setPlaceholder("1234 5678 9012 3456");
        cardNumber.setSuffixComponent(new LkIcon("card", 16));
        cardNumber.setRequired(true);
        cardNumber.setWidthFull();

        expiry.setPlaceholder("MM / YY");
        expiry.setRequired(true);
        cvc.setPlaceholder("123");
        cvc.setRequired(true);

        LkRow expiryCvc = new LkRow().gap(12);
        expiry.getStyle().set("flex", "1 1 0");
        cvc.getStyle().set("flex", "1 1 0");
        expiryCvc.add(expiry, cvc);

        col.add(cardholder, cardNumber, expiryCvc);
        col.add(Lk.divider());

        Button pay = new Button("Pay $504.00", e -> attemptPay());
        pay.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        pay.setWidthFull();
        pay.addClickShortcut(Key.ENTER);
        col.add(pay);

        Span hint = new Span();
        hint.getStyle().set("font-size", "12px").set("color", "var(--muted)").set("text-align", "center")
            .set("display", "block");
        hint.add(new LkIcon("lock", 13));
        hint.add(new Span(" Payment + ticket issuance are atomic — if either fails, you are not charged."));
        col.add(hint);

        card.add(col);
        return card;
    }

    private void attemptPay() {
        if (cardholder.isEmpty() || cardNumber.isEmpty() || expiry.isEmpty() || cvc.isEmpty()) {
            Toasts.failure("Please fill in every payment field.");
            return;
        }
        Toasts.success("Payment successful — $504.00 charged.");
        UI.getCurrent().navigate("order/TKT-20847");
    }
}
