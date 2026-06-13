package com.ticketing.system.Presentation.views.order;

import com.ticketing.system.Core.Application.dto.ActiveOrderDTO;
import com.ticketing.system.Core.Application.dto.CheckoutResultDTO;
import com.ticketing.system.Core.Application.services.CheckoutService;
import com.ticketing.system.Core.Application.services.ReservationService;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
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
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.ticketing.system.Core.Application.events.OrderExpiredEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

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
 * Guest order loading is not yet implemented in ReservationService.
 * Until UC-3 lands, guests see an empty cart and are prompted to sign in.
 * TODO: replace the guest stub in loadActiveOrder() once
 *       ReservationService.restoreActiveOrderForGuest(sessionId) exists.
 */
@Route(value = "checkout", layout = MainLayout.class)
@PageTitle("Checkout · TicketHub")
@AnonymousAllowed
@Slf4j
public class CheckoutView extends LkPage implements BeforeEnterObserver {

    // ── service fee in cents (integer — no floating-point) ─────────────────
    private static final long SERVICE_FEE_CENTS = 2400L;

    // ── injected services ──────────────────────────────────────────────────
    private final ReservationService reservationService;
    private final CheckoutService    checkoutService;
    private final ISessionManager    sessionManager;

    // ── identity — resolved in beforeEnter ────────────────────────────────
    private String  memberToken;     // null → guest
    private String  sessionId;       // null → member
    private boolean isMember;
    private int     resolvedUserId;  // 0 for guest

    // ── push — captured in onAttach for cross-thread UI updates ───────────
    private volatile UI ui;

    // ── order state — refreshed in beforeEnter ─────────────────────────────
    private ActiveOrderDTO activeOrder;   // null = empty / not found
    private long subtotalCents;
    private long totalCents;

    // ── payment form fields ────────────────────────────────────────────────
    private final TextField    cardholder = new TextField("Cardholder name");
    private final TextField    cardNumber  = new TextField("Card number");
    private final TextField    expiry      = new TextField("Expiry");
    private final TextField    cvc         = new TextField("CVC");
    private final TextField    coupon      = new TextField();
    // Guest-only fields — added to the DOM conditionally in buildPaymentCard()
    private final EmailField   guestEmail  = new EmailField("Your email");
    private final IntegerField guestAge    = new IntegerField("Your age");

    // ── live UI references — updated by syncDynamicUI() ───────────────────
    private Div    linesContainer;
    private Span   subtotalSpan;
    private Span   serviceFeeSpan;
    private Span   totalSpan;
    private Button payButton;
    private Span   timerSpan;
    private Button editCartBtn;

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor — inject only, never call services here
    // ─────────────────────────────────────────────────────────────────────────

    public CheckoutView(ReservationService reservationService,
                        CheckoutService    checkoutService,
                        ISessionManager    sessionManager) {
        this.reservationService = reservationService;
        this.checkoutService    = checkoutService;
        this.sessionManager     = sessionManager;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Attach / Detach — capture the UI reference for server-push
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // BeforeEnterObserver — load fresh data before the DOM is shown
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        resolveIdentity();
        refreshFromOrder(loadActiveOrder());

        // Build the page on first entry only; on subsequent navigations just
        // sync the dynamic parts (Vaadin may reuse the same instance).
        if (getChildren().findAny().isEmpty()) {
            buildPage();
        } else {
            syncDynamicUI();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Identity resolution
    // ─────────────────────────────────────────────────────────────────────────

    private void resolveIdentity() {
        String token = resolveTokenFromSecurityContext();
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

    /**
     * Reads the JWT stored in Spring Security's context (placed there by the
     * JWT filter on every request). Returns null for anonymous users.
     */
    private String resolveTokenFromSecurityContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;

        Object credentials = auth.getCredentials();
        if (credentials instanceof String s && !s.isBlank()) return s;

        Object principal = auth.getPrincipal();
        if (principal instanceof String s && !s.isBlank()
                && !s.equals("anonymousUser")) return s;

        return null;
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

    // ─────────────────────────────────────────────────────────────────────────
    // Data loading — no MockCart, no stubs
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // Real-time push — fired by SessionAndOrderSweeper via Spring events
    // ─────────────────────────────────────────────────────────────────────────

    @EventListener
    public void onOrderExpired(OrderExpiredEvent event) {
        UI currentUi = this.ui;
        if (currentUi == null || !currentUi.isAttached()) return;

        boolean isForMe = isMember
                ? event.getUserId() == resolvedUserId && resolvedUserId != 0
                : sessionId != null && sessionId.equals(event.getSessionId());

        if (!isForMe) return;

        currentUi.access(() -> {
            refreshFromOrder(null);
            syncDynamicUI();
            Toasts.failure("Your reservation has expired. Please add tickets again.");
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Totals — stored as cents (long) to avoid floating-point errors
    // ─────────────────────────────────────────────────────────────────────────

    private void refreshFromOrder(ActiveOrderDTO order) {
        this.activeOrder = order;

        if (order == null || order.lines().isEmpty()) {
            this.subtotalCents = 0L;
            this.totalCents    = 0L;
            return;
        }

        // Sum each line in cents. Math.round absorbs the tiny floating-point
        // imprecision in the DTO's double pricePerTicket field.
        long subtotal = order.lines().stream()
                .mapToLong(l -> Math.round(l.pricePerTicket() * 100))
                .sum();
        this.subtotalCents = subtotal;
        this.totalCents    = subtotal + SERVICE_FEE_CENTS;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Page construction (called once)
    // ─────────────────────────────────────────────────────────────────────────

    private void buildPage() {
        title("Checkout");
        add(buildCountdownBanner());
        add(buildSplit());
        syncDynamicUI();
    }

    /**
     * Updates only the dynamic parts of the DOM (lines, totals, timer,
     * pay button). Safe to call multiple times.
     */
    private void syncDynamicUI() {
        if (linesContainer == null) return;

        // Re-render line items
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

        // Update totals
        if (subtotalSpan   != null) subtotalSpan.setText(formatCents(subtotalCents));
        if (serviceFeeSpan != null) serviceFeeSpan.setText(formatCents(totalCents > 0 ? SERVICE_FEE_CENTS : 0L));
        if (totalSpan      != null) totalSpan.setText(formatCents(totalCents));

        // Update pay button
        if (payButton != null) {
            payButton.setText("Pay " + formatCents(totalCents));
            payButton.setEnabled(totalCents > 0);
        }

        // Restart the countdown timer
        if (timerSpan != null && activeOrder != null
                && activeOrder.remainingSecondsBeforeExpiry() > 0) {
            double endMs = System.currentTimeMillis()
                    + activeOrder.remainingSecondsBeforeExpiry() * 1000.0;
            timerSpan.getElement().executeJs(
                    "const t = this;" +
                    "const end = $0;" +
                    "function pad(n){return String(n).padStart(2,'0');}" +
                    "function onExpired(){" +
                    "  t.textContent='00:00';" +
                    "}" +
                    "function tick(){" +
                    "  const s=Math.max(0,Math.floor((end-Date.now())/1000));" +
                    "  t.textContent=pad(Math.floor(s/60))+':'+pad(s%60);" +
                    "  if(s>0) setTimeout(tick,1000); else onExpired();" +
                    "}" +
                    "tick();",
                    endMs);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI builders
    // ─────────────────────────────────────────────────────────────────────────

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

    // ── order summary column ───────────────────────────────────────────────

    private Component buildOrderColumn() {
        LkCol col = new LkCol().gap(14);
        LkCard orderCard = new LkCard("Your order").pad(0);

        linesContainer = new Div();
        linesContainer.addClassName("bz-order-lines");
        orderCard.add(linesContainer);

        Div foot = new Div();
        foot.addClassName("bz-order-foot");

        // Coupon row
        coupon.setPlaceholder("Coupon code");
        coupon.getStyle().set("flex", "1 1 auto");
        Button apply = new Button("Apply", e -> {
            if (coupon.isEmpty()) {
                Toasts.warn("Enter a coupon code first.");
            } else {
                Toasts.success("Coupon applied (no discount in placeholder).");
            }
        });
        apply.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        LkRow couponRow = new LkRow().gap(8);
        couponRow.add(coupon, apply);
        foot.add(couponRow);

        // Totals — Span references kept for syncDynamicUI()
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

    /**
     * One line-item row. Uses setText() for all content — no innerHTML —
     * to prevent any risk of XSS regardless of what eventName contains.
     */
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

    /**
     * A two-column summary row (label | value).
     * Uses setText() for all strings — no innerHTML.
     */
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

    // ── payment card ───────────────────────────────────────────────────────

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

        // Guest-only fields
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

    // ─────────────────────────────────────────────────────────────────────────
    // Payment submission
    // ─────────────────────────────────────────────────────────────────────────

    private void attemptPay() {
        // Guard: empty or expired cart
        if (totalCents == 0L) {
            Toasts.failure("Your cart is empty.");
            return;
        }

        // Form validation
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

        // Re-load the order to catch server-side expiry before submitting.
        // The JS timer is best-effort (clock skew, suspended laptop, etc.).
        refreshFromOrder(loadActiveOrder());
        if (totalCents == 0L) {
            syncDynamicUI();
            Toasts.failure("Your reservation has expired. Please add tickets again.");
            return;
        }

        // Fresh idempotency key per submit click
        String idempotencyKey = UUID.randomUUID().toString();

        // TODO (UC-10): replace with the token returned by the payment-gateway
        // JS SDK (e.g. Stripe.js createPaymentMethod) so the raw card number
        // never travels through our server.
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
                // TODO (UC-3): guest checkout path — requires real sessionId
                // and a working guest order in the repository.
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
            // Log the full exception server-side; show only a safe generic
            // message to the user — never expose internal details.
            log.error("Checkout failed for {} user", isMember ? "member" : "guest", e);
            Toasts.failure("Payment could not be completed. Please try again or contact support.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Formatting — cents only, no floating-point arithmetic
    // ─────────────────────────────────────────────────────────────────────────

    /** Converts an integer cent value to a display string, e.g. "$12.34". */
    private static String formatCents(long cents) {
        long abs  = Math.abs(cents);
        String sign = cents < 0 ? "-" : "";
        return sign + "$" + (abs / 100) + "." + String.format("%02d", abs % 100);
    }
}