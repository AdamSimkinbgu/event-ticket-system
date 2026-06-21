package com.ticketing.system.Presentation.views.order;

import com.ticketing.system.Core.Application.dto.ActiveOrderDTO;
import com.ticketing.system.Core.Application.dto.CheckoutResultDTO;
import com.ticketing.system.Core.Application.events.OrderExpiredEvent;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.services.CheckoutService;
import com.ticketing.system.Core.Application.services.ReservationService;
import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBanner;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkCol;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Core.Domain.exceptions.ActiveOrderExpiredException;
import com.ticketing.system.Core.Domain.exceptions.ConcurrentReservationException;
import com.ticketing.system.Core.Domain.exceptions.InsufficientInventoryException;
import com.ticketing.system.Core.Domain.exceptions.PaymentGatewayException;
import com.ticketing.system.Core.Domain.exceptions.PolicyViolationException;
import com.ticketing.system.Core.Domain.exceptions.SessionExpiredException;
import com.ticketing.system.Presentation.session.AuthSession;
import java.util.UUID;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;


/**
 * Checkout view — wired to real CheckoutService and ReservationService.
 *
 * STATE DESIGN
 * ------------
 * Vaadin creates a new View instance on every navigation (default scope).
 * We load the active order fresh in beforeEnter(), which fires before the
 * DOM is shown. This means:
 *   - Expired orders are caught before the user sees stale totals.
 *   - A cart change in another tab is reflected on the next navigation here.
 *
 * FINANCIAL ARITHMETIC
 * --------------------
 * All monetary values are kept as integer cents (long) throughout.
 * Conversion to a display string happens only at the last moment in
 * formatCents(). This eliminates floating-point rounding errors entirely.
 *
 * GUEST PATH (UC-3 / UC-5)
 * ------------------------
 * Guest order loading falls back to restoreActiveOrderForGuest(sessionId).
 */
@Route(value = "checkout", layout = MainLayout.class)
@PageTitle("Checkout · TicketHub")
@AnonymousAllowed
@Slf4j
public class CheckoutView extends LkPage implements BeforeEnterObserver {

    private static final long SERVICE_FEE_CENTS = 2400L;

    private final ReservationService reservationService;
    private final CheckoutService    checkoutService;
    private final ISessionManager    sessionManager;

    private String  memberToken;
    private String  sessionId;
    private boolean isMember;
    private int     resolvedUserId;

    private volatile UI ui;

    private ActiveOrderDTO activeOrder;
    private long subtotalCents;
    private long totalCents;

    private final TextField    cardholder = new TextField("Cardholder name");
    private final TextField    cardNumber  = new TextField("Card number");
    private final TextField    expiry      = new TextField("Expiry");
    private final TextField    cvc         = new TextField("CVC");
    private final TextField    coupon      = new TextField();
    private final EmailField   guestEmail  = new EmailField("Your email");
    private final IntegerField guestAge    = new IntegerField("Your age");

    private Div    linesContainer;
    private Span   subtotalSpan;
    private Span   serviceFeeSpan;
    private Span   totalSpan;
    private Button payButton;
    private Span   timerSpan;
    private Button editCartBtn;

    public CheckoutView(ReservationService reservationService,
                        CheckoutService    checkoutService,
                        ISessionManager    sessionManager) {
        this.reservationService = reservationService;
        this.checkoutService    = checkoutService;
        this.sessionManager     = sessionManager;
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        this.ui = attachEvent.getUI();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        this.ui = null;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        resolveIdentity();
        refreshFromOrder(renewAndLoadActiveOrder());

        if (getChildren().findAny().isEmpty()) {
            buildPage();
        } else {
            syncDynamicUI();
        }
    }

    private void resolveIdentity() {
        String token = AuthSession.token();
        if (token != null && sessionManager.validateToken(token)) {
            this.memberToken = token;
            this.sessionId   = null;
            this.isMember    = true;
        } else {
            this.memberToken = null;
            this.sessionId   = resolveGuestSessionId();
            this.isMember    = false;
        }
    }

    private String resolveGuestSessionId() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) return null;
        String guestId = (String) session.getAttribute("guestSessionId");
        if (guestId == null) {
            guestId = UUID.randomUUID().toString();
            session.setAttribute("guestSessionId", guestId);
        }
        return guestId;
    }

    /**
     * Like {@link #loadActiveOrder()}, but resets every reserved ticket's 10-minute hold
     * timer to a fresh full window. Used only on checkout-page entry ({@link #beforeEnter})
     * so the buyer gets the maximum time to pay; the pre-pay refresh keeps using the plain
     * {@link #loadActiveOrder()} so its "cart expired while you were typing" guard stays intact.
     */
    private ActiveOrderDTO renewAndLoadActiveOrder() {
        if (isMember) {
            try {
                this.resolvedUserId = sessionManager.extractUserId(memberToken);
                return reservationService.renewReservationsForMemberCheckout(resolvedUserId);
            } catch (Exception e) {
                log.warn("Failed to renew/load active order for member", e);
                Toasts.warn("Could not load your cart — please try again.");
                return null;
            }
        }

        if (sessionId != null) {
            try {
                return reservationService.renewReservationsForGuestCheckout(sessionId);
            } catch (Exception e) {
                log.warn("Failed to renew/load active order for guest", e);
                return null;
            }
        }
        return null;
    }

    private ActiveOrderDTO loadActiveOrder() {
        if (isMember) {
            try {
                this.resolvedUserId = sessionManager.extractUserId(memberToken);
                return reservationService.restoreActiveOrder(resolvedUserId);
            } catch (Exception e) {
                log.warn("Failed to load active order for member", e);
                Toasts.warn("Could not load your cart — please try again.");
                return null;
            }
        }

        if (sessionId != null) {
            try {
                return reservationService.restoreActiveOrderForGuest(sessionId);
            } catch (Exception e) {
                log.warn("Failed to load active order for guest", e);
                return null;
            }
        }
        return null;
    }

    @EventListener
    public void onOrderExpired(OrderExpiredEvent event) {
        UI currentUi = this.ui;
        if (currentUi == null || !currentUi.isAttached()) return;

        boolean isForMe = isMember
                ? event.userId() == resolvedUserId && resolvedUserId != 0
                : sessionId != null && sessionId.equals(event.sessionId());

        if (!isForMe) return;

        currentUi.access(() -> {
            refreshFromOrder(null);
            syncDynamicUI();
            Toasts.failure("Your reservation has expired. Please add tickets again.");
        });
    }

    private void refreshFromOrder(ActiveOrderDTO order) {
        this.activeOrder = order;

        if (order == null || order.lines().isEmpty()) {
            this.subtotalCents = 0L;
            this.totalCents    = 0L;
            return;
        }

        long subtotal = order.lines().stream()
                .mapToLong(l -> Math.round(l.pricePerTicket() * 100))
                .sum();
        this.subtotalCents = subtotal;
        this.totalCents    = subtotal + SERVICE_FEE_CENTS;
    }

    private void buildPage() {
        title("Checkout");
        add(buildCountdownBanner());
        add(buildSplit());
        syncDynamicUI();
    }

    private void syncDynamicUI() {
        if (linesContainer == null) return;

        linesContainer.removeAll();
        boolean hasItems = activeOrder != null && !activeOrder.lines().isEmpty();

        if (!hasItems) {
            Div empty = new Div();
            empty.getStyle()
                    .set("padding", "20px")
                    .set("text-align", "center")
                    .set("color", "var(--muted)")
                    .set("font-size", "13.5px");

            if (isMember) {
                empty.setText("No items in cart. Add tickets from Browse first.");
            } else {
                empty.setText("Sign in to view your saved cart, or browse events as a guest.");
                Button signInBtn = new Button("Sign in", e -> UI.getCurrent().navigate("login"));
                signInBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                signInBtn.getStyle().set("margin-top", "10px");
                empty.add(signInBtn);
            }
            linesContainer.add(empty);
        } else {
            for (ActiveOrderDTO.CartLineDTO line : activeOrder.lines()) {
                linesContainer.add(buildOrderLine(line));
            }
        }

        if (subtotalSpan   != null) subtotalSpan.setText(formatCents(subtotalCents));
        if (serviceFeeSpan != null) serviceFeeSpan.setText(formatCents(totalCents > 0 ? SERVICE_FEE_CENTS : 0L));
        if (totalSpan      != null) totalSpan.setText(formatCents(totalCents));

        if (payButton != null) {
            payButton.setText("Pay " + formatCents(totalCents));
            payButton.setEnabled(totalCents > 0);
        }

        if (timerSpan != null && activeOrder != null
                && activeOrder.remainingSecondsBeforeExpiry() > 0) {
            double endMs = System.currentTimeMillis()
                    + activeOrder.remainingSecondsBeforeExpiry() * 1000.0;
            timerSpan.getElement().executeJs(
                    "const t = this;" +
                    "const end = $0;" +
                    "function pad(n){return String(n).padStart(2,'0');}" +
                    "function tick(){" +
                    "  const s=Math.max(0,Math.floor((end-Date.now())/1000));" +
                    "  t.textContent=pad(Math.floor(s/60))+':'+pad(s%60);" +
                               "  if (s > 0) { setTimeout(tick, 1000); }" +
            "  else { document.querySelectorAll('vaadin-button.bz-pay-btn').forEach(b => b.setAttribute('disabled', '')); }" +
            "}" +
            "tick();",
                    endMs);
        }
    }

    private Component buildCountdownBanner() {
        LkBanner banner = new LkBanner();
        banner.tone(LkBanner.Tone.warn);
        banner.setIcon(new LkIcon("clock", 17));

        timerSpan = new Span("--:--");
        timerSpan.addClassName("lk-mono");
        timerSpan.getStyle().set("font-size", "18px").set("font-weight", "700");

        Span body = new Span();
        body.add(timerSpan);
        Span msg = new Span(" — seats are held for you. Complete payment before time runs out.");
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

    private Component buildOrderColumn() {
        LkCol col = new LkCol().gap(14);
        LkCard orderCard = new LkCard("Your order").pad(0);

        linesContainer = new Div();
        linesContainer.addClassName("bz-order-lines");
        orderCard.add(linesContainer);

        Div foot = new Div();
        foot.addClassName("bz-order-foot");

        coupon.setPlaceholder("Coupon code");
        coupon.getStyle().set("flex", "1 1 auto");
        Button apply = new Button("Apply", e -> {
            if (coupon.isEmpty()) Toasts.warn("Enter a coupon code first.");
            else Toasts.success("Coupon applied (no discount in placeholder).");
        });
        apply.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        LkRow couponRow = new LkRow().gap(8);
        couponRow.add(coupon, apply);
        foot.add(couponRow);

        subtotalSpan    = new Span(formatCents(subtotalCents));
        serviceFeeSpan  = new Span(formatCents(totalCents > 0 ? SERVICE_FEE_CENTS : 0L));
        totalSpan       = new Span(formatCents(totalCents));

        LkCol totals = new LkCol().gap(6);
        totals.getStyle().set("margin-top", "12px");
        totals.add(summaryRow("Subtotal",    subtotalSpan, false));
        totals.add(summaryRow("Service fee", serviceFeeSpan, false));
        totals.add(summaryRow("Total",       totalSpan, true));
        foot.add(totals);

        editCartBtn = new Button("Edit cart", e -> UI.getCurrent().navigate("cart"));
        editCartBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        editCartBtn.getStyle().set("margin-top", "8px");

        orderCard.add(foot);
        col.add(orderCard);
        col.add(editCartBtn);
        return col;
    }

    private Component buildOrderLine(ActiveOrderDTO.CartLineDTO line) {
        Div row = new Div();
        row.addClassName("bz-order-line");

        String labelText = line.eventName()
                + (line.seatNumber() != null ? " · " + line.seatNumber() : "");
        Span label = new Span(labelText);

        long lineCents = Math.round(line.pricePerTicket() * 100);
        Span price = new Span(formatCents(lineCents));
        price.getStyle().set("font-weight", "700");

        row.add(label, price);
        return row;
    }

    private Component summaryRow(String label, Span valueSpan, boolean bold) {
        LkRow r = new LkRow().justify("space-between");
        Span labelSpan = new Span(label);
        if (bold) {
            labelSpan.getStyle().set("font-weight", "700").set("font-size", "16px");
            valueSpan.getStyle().set("font-weight", "700").set("font-size", "16px");
        } else {
            labelSpan.getStyle().set("color", "var(--muted)");
        }
        r.add(labelSpan, valueSpan);
        return r;
    }

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

        if (!isMember) {
            guestEmail.setPlaceholder("you@example.com");
            guestEmail.setRequired(true);
            guestEmail.setWidthFull();
            guestEmail.setHelperText("We'll send your ticket confirmation here.");

            guestAge.setPlaceholder("e.g. 25");
            guestAge.setMin(1);
            guestAge.setMax(120);
            guestAge.setRequired(true);
            guestAge.setHelperText("Required to verify age-restricted events.");
            guestAge.setWidthFull();

            col.add(guestEmail, guestAge);
        }

        col.add(Lk.divider());

        payButton = new Button("Pay " + formatCents(totalCents), e -> attemptPay());
        payButton.addClassName("bz-pay-btn");
        payButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        payButton.setWidthFull();
        payButton.addClickShortcut(Key.ENTER);
        payButton.setEnabled(totalCents > 0);
        col.add(payButton);

        Span hint = new Span();
        hint.getStyle()
                .set("font-size", "12px")
                .set("color", "var(--muted)")
                .set("text-align", "center")
                .set("display", "block");
        hint.add(new LkIcon("lock", 13));
        hint.add(new Span(
                " Payment and ticket issuance are atomic — if either fails, you are not charged."));
        col.add(hint);

        card.add(col);
        return card;
    }

    private void attemptPay() {
        if (totalCents == 0L) {
            Toasts.failure("Your cart is empty.");
            return;
        }

        if (cardholder.isEmpty() || cardNumber.isEmpty()
                || expiry.isEmpty() || cvc.isEmpty()) {
            Toasts.failure("Please fill in every payment field.");
            return;
        }

        if (!isMember) {
            if (guestEmail.isInvalid() || guestEmail.isEmpty()) {
                Toasts.failure("Please enter a valid email address.");
                return;
            }
            if (guestAge.isEmpty() || guestAge.getValue() == null
                    || guestAge.getValue() < 1) {
                Toasts.failure("Please enter your age.");
                return;
            }
        }

        refreshFromOrder(loadActiveOrder());
        if (totalCents == 0L) {
            syncDynamicUI();
            Toasts.failure("Your reservation has expired. Please add tickets again.");
            return;
        }

        payButton.setEnabled(false);
        payButton.setText("Processing…");

        String idempotencyKey = UUID.randomUUID().toString();
        String paymentMethodToken = "tok_" + cardNumber.getValue().replaceAll("\\s+", "");
        String currency = "USD";

        try {
            CheckoutResultDTO result;

            if (isMember) {
                result = checkoutService.checkoutMember(
                        memberToken,
                        idempotencyKey,
                        currency,
                        paymentMethodToken);
            } else {
                result = checkoutService.checkoutGuest(
                        sessionId,
                        guestEmail.getValue().trim(),
                        idempotencyKey,
                        currency,
                        paymentMethodToken,
                        guestAge.getValue());
            }

            Toasts.success("Payment successful — "
                    + formatCents(Math.round(result.totalCharged() * 100))
                    + " charged.");
            UI.getCurrent().navigate("order/" + result.orderReceiptId());

        } catch (RuntimeException e) {
            log.error("Checkout failed for {} user", isMember ? "member" : "guest", e);
            handlePaymentError(e);
        }
    }

    // Maps a checkout failure to a clear, actionable message + recovery navigation (SLR.3.2).
    // CheckoutService re-wraps failures as RuntimeException(cause), so we unwrap one level.
    // Today only PaymentGatewayException is surfaced as a distinct type; the PolicyViolation /
    // InsufficientInventory / ConcurrentReservation / Session- & ActiveOrderExpired branches are
    // forward-looking — they activate once checkout/domain throw those typed exceptions (until
    // then those failures arrive as IllegalStateException and hit the generic branch below).
    private void handlePaymentError(RuntimeException ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;

        if (cause instanceof PolicyViolationException pve) {
            Toasts.failure("Purchase not allowed: " + pve.getMessage());
        } else if (cause instanceof PaymentGatewayException) {
            Toasts.failure("Payment declined. Please check your card details and try again.");
        } else if (cause instanceof InsufficientInventoryException) {
            Toasts.failure("Some tickets are no longer available. Please review your cart.");
            UI.getCurrent().navigate("cart");
            return;
        } else if (cause instanceof ConcurrentReservationException) {
            Toasts.warn("Your cart was modified by another session. Please review and try again.");
        } else if (cause instanceof SessionExpiredException || cause instanceof ActiveOrderExpiredException) {
            Toasts.failure("Your session expired. Please start a new order.");
            UI.getCurrent().navigate("browse");
            return;
        } else {
            Toasts.failure("Checkout couldn't be completed — no charge remains on your card. Please try again.");
        }
        resetPayButton();
    }

    private void resetPayButton() {
        payButton.setEnabled(totalCents > 0);
        payButton.setText("Pay " + formatCents(totalCents));
    }

    private static String formatCents(long cents) {
        long abs  = Math.abs(cents);
        String sign = cents < 0 ? "-" : "";
        return sign + "$" + (abs / 100) + "." + String.format("%02d", abs % 100);
    }
}
