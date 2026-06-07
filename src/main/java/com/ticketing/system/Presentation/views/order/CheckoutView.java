package com.ticketing.system.Presentation.views.order;

import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "checkout", layout = MainLayout.class)
@PageTitle("Checkout · Event Ticket Platform")
@AnonymousAllowed
public class CheckoutView extends PlaceholderView {
    public CheckoutView() {
        super(
            "Checkout",
            "V2-CHECK-01",
            "Mohamad Lahwani",
            "Pay screen. Single global countdown banner for the whole order. Calls CheckoutService.checkout. Policy violations (V2-POL-05) shown per req 3.5."
        );
        add(wireCard("⚠ Global countdown banner — ONE timer for the whole order",
            wireBox("⏱ 09:42 — replaces all per-line cart timers on entry"),
            wireBox("On expiry: all reservations released, back to /cart with toast")
        ));
        add(wireSplit(1, 1,
            wireCard("Your order",
                wireBox("Line 1: event · seat · price"),
                wireBox("Line 2: event · seat · price"),
                wireBox("Subtotal / Discount / Total"),
                wireBox("Coupon code field + Apply button")
            ),
            wireCard("Payment",
                wireForm("Cardholder name", "Card number", "Expiry", "CVC")
            )
        ));
        add(wireActions("Pay $_____"));
    }
}
