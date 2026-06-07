package com.ticketing.system.Presentation.views.order;

import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "order/:receiptId", layout = MainLayout.class)
@PageTitle("Order confirmed · Event Ticket Platform")
@AnonymousAllowed
public class OrderConfirmationView extends PlaceholderView {
    public OrderConfirmationView() {
        super(
            "Order confirmed",
            "V2-CHECK-02",
            "Mohamad Lahwani",
            "Post-checkout success screen showing barcodes (text, no PDF), seat labels, total paid, transaction id. Failure path: structured error toast per req 3.5."
        );
        add(wireHero("✓ Thanks — your tickets are confirmed", "Receipt #_____ · Transaction id _____ · _____ paid"));
        add(wireCard("Your tickets",
            wireGrid("Event", "Date", "Zone", "Seat", "Price", "Barcode")
        ));
        add(wireActions("View my tickets", "Browse more events"));
    }
}
