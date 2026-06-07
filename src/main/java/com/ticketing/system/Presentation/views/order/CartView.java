package com.ticketing.system.Presentation.views.order;

import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "cart", layout = MainLayout.class)
@PageTitle("Cart · Event Ticket Platform")
@AnonymousAllowed
public class CartView extends PlaceholderView {
    public CartView() {
        super(
            "Your cart",
            "V2-RES-03",
            "Bentzion Hadad",
            "Active reservations with per-line expiry countdown (10-min TTL). Proceeding to checkout refreshes timers into one global counter (see V2-CHECK-01)."
        );
        add(wireSplit(3, 1,
            wireColumn(
                wireCard("Cart line items",
                    wireBox("Event name · seat label · price"),
                    wireBox("Event name · seat label · price"),
                    wireBox("Event name · 2× General Admission · price"),
                    wireBox("Per-line: ⏱ countdown chip · \"Remove\" link")
                )
            ),
            wireCard("Summary",
                wireBox("Subtotal"),
                wireBox("Fee"),
                wireBox("Total ($_____)"),
                wireActions("Proceed to checkout")
            )
        ));
    }
}
