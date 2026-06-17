package com.ticketing.system.Presentation.session;

import com.vaadin.flow.server.VaadinSession;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Session-scoped placeholder cart for the V2 buyer flow.
 *
 * <p>Items are pushed in from {@code SeatPickerView}'s "Add to cart" /
 * "Hold tickets" actions and read by {@code CartView} and
 * {@code CheckoutView}. Real implementation is V2-RES-03 (Bentzion) —
 * {@code ReservationService.viewMyCart()} — which will replace this
 * VaadinSession-backed store with a real reservation aggregate.
 */
public final class MockCart {

    public record Item(
        String title,
        String seat,
        int    priceCents,
        Instant heldUntil,
        String  gradient
    ) { }

    private static final String KEY               = "mockCart.items";
    private static final String CHECKOUT_DEADLINE = "mockCart.checkoutDeadline";

    private MockCart() { }

    @SuppressWarnings("unchecked")
    public static List<Item> getItems() {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return new ArrayList<>();
        List<Item> items = (List<Item>) s.getAttribute(KEY);
        if (items == null) {
            items = new ArrayList<>();
            s.setAttribute(KEY, items);
        }
        return items;
    }

    public static void add(Item item) { getItems().add(item); }

    public static void remove(Item item) {
        getItems().remove(item);
        // No items left? The previous global checkout window is moot.
        if (getItems().isEmpty()) clearCheckoutDeadline();
    }

    public static void clear() {
        getItems().clear();
        clearCheckoutDeadline();
    }

    public static int size() { return getItems().size(); }

    // ---- single global checkout deadline ----

    /** {@code null} if the user hasn't started checkout yet on this session. */
    public static Instant getCheckoutDeadline() {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return null;
        return (Instant) s.getAttribute(CHECKOUT_DEADLINE);
    }

    public static void setCheckoutDeadline(Instant deadline) {
        VaadinSession s = VaadinSession.getCurrent();
        if (s != null) s.setAttribute(CHECKOUT_DEADLINE, deadline);
    }

    public static void clearCheckoutDeadline() {
        VaadinSession s = VaadinSession.getCurrent();
        if (s != null) s.setAttribute(CHECKOUT_DEADLINE, null);
    }

    /** Subtotal across all items, in cents. */
    public static int subtotalCents() {
        return getItems().stream().mapToInt(Item::priceCents).sum();
    }
}
