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
import com.ticketing.system.Presentation.session.MockCart;
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

import java.time.Duration;
import java.time.Instant;

@Route(value = "checkout", layout = MainLayout.class)
@PageTitle("Checkout · TicketHub")
@AnonymousAllowed
public class CheckoutView extends LkPage {

    private static final int SERVICE_FEE_CENTS = 2400;

    private final TextField cardholder = new TextField("Cardholder name");
    private final TextField cardNumber = new TextField("Card number");
    private final TextField expiry     = new TextField("Expiry");
    private final TextField cvc        = new TextField("CVC");
    private final TextField coupon     = new TextField();

    private final int subtotalCents = MockCart.subtotalCents();
    private final int totalCents    = MockCart.getItems().isEmpty()
                                      ? 0 : subtotalCents + SERVICE_FEE_CENTS;

    public CheckoutView() {
        title("Checkout");
        ensureCheckoutDeadline();
        add(buildCountdownBanner());
        add(buildSplit());
    }

    /**
     * Seed a single 10-min global checkout window on the first entry to
     * Checkout for this cart. Subsequent visits (after leaving and
     * returning without paying) keep the existing deadline so the timer
     * doesn't reset — that's the contract V2-CHECK-01 promises.
     */
    private void ensureCheckoutDeadline() {
        if (MockCart.getItems().isEmpty()) return;
        if (MockCart.getCheckoutDeadline() == null) {
            MockCart.setCheckoutDeadline(Instant.now().plus(Duration.ofMinutes(10)));
        }
    }

    private Component buildCountdownBanner() {
        LkBanner banner = new LkBanner();
        banner.tone(LkBanner.Tone.warn);
        banner.setIcon(new LkIcon("clock", 17));

        Span timer = new Span("--:--");
        timer.addClassName("lk-mono");
        timer.getStyle().set("font-size", "18px").set("font-weight", "700");

        Instant deadline = MockCart.getCheckoutDeadline();
        double endMs = deadline != null
            ? (double) deadline.toEpochMilli()
            : (double) (System.currentTimeMillis() + 10 * 60 * 1000);

        timer.getElement().executeJs(
            "const t = this;" +
            "const end = $0;" +
            "function pad(n){return String(n).padStart(2,'0');}" +
            "function tick(){" +
            "  const s = Math.max(0, Math.floor((end - Date.now())/1000));" +
            "  t.textContent = pad(Math.floor(s/60)) + ':' + pad(s%60);" +
            "  if (s > 0) setTimeout(tick, 1000);" +
            "}" +
            "tick();",
            endMs);

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
        if (MockCart.getItems().isEmpty()) {
            Div empty = new Div();
            empty.getStyle()
                .set("padding", "20px").set("text-align", "center")
                .set("color", "var(--muted)").set("font-size", "13.5px");
            empty.setText("No items in cart. Add tickets from Browse first.");
            lines.add(empty);
        } else {
            for (MockCart.Item it : MockCart.getItems()) {
                lines.add(orderLine(it.title() + " · " + it.seat(), it.priceCents()));
            }
        }
        order.add(lines);

        Div foot = new Div();
        foot.addClassName("bz-order-foot");

        coupon.setPlaceholder("Coupon code");
        coupon.getStyle().set("flex", "1 1 auto");
        Button apply = new Button("Apply", e -> {
            if (coupon.isEmpty()) Toasts.warn("Enter a coupon code first.");
            else Toasts.success("Coupon '" + coupon.getValue() + "' applied (mock — no discount in placeholder).");
        });
        apply.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        LkRow couponRow = new LkRow().gap(8);
        couponRow.add(coupon, apply);
        foot.add(couponRow);

        LkCol totals = new LkCol().gap(6);
        totals.getStyle().set("margin-top", "12px");
        totals.add(line("Subtotal",    formatPrice(subtotalCents),       false));
        totals.add(line("Service fee", formatPrice(SERVICE_FEE_CENTS),   false));
        totals.add(line("Total",       formatPrice(totalCents),          true));
        foot.add(totals);

        order.add(foot);
        col.add(order);
        return col;
    }

    private Component orderLine(String label, int priceCents) {
        Div line = new Div();
        line.addClassName("bz-order-line");
        Span l = new Span(label);
        Span p = new Span();
        p.getElement().setProperty("innerHTML", "<b>" + formatPrice(priceCents) + "</b>");
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

        Button pay = new Button("Pay " + formatPrice(totalCents), e -> attemptPay());
        pay.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        pay.setWidthFull();
        pay.addClickShortcut(Key.ENTER);
        pay.setEnabled(totalCents > 0);
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
        if (totalCents == 0) {
            Toasts.failure("Your cart is empty.");
            return;
        }
        if (cardholder.isEmpty() || cardNumber.isEmpty() || expiry.isEmpty() || cvc.isEmpty()) {
            Toasts.failure("Please fill in every payment field.");
            return;
        }
        Toasts.success("Payment successful — " + formatPrice(totalCents) + " charged.");
        MockCart.clear();
        UI.getCurrent().navigate("order/TKT-20847");
    }

    private static String formatPrice(int cents) {
        return "$" + (cents / 100) + "." + String.format("%02d", cents % 100);
    }
}
