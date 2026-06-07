package com.ticketing.system.Presentation.views.account;

import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "my-account", layout = MainLayout.class)
@PageTitle("My account · Event Ticket Platform")
@PermitAll
public class MyAccountView extends PlaceholderView {
    public MyAccountView() {
        super(
            "My account",
            "V2-MEM-01",
            "Moshe Klimer",
            "Member purchase history. Calls MemberAccountService.viewMyHistory. Two grids — past orders (II.3.7) and held tickets (II.3.8) with refund-status badges (II.3.9)."
        );
        add(wireHero("Welcome back, {member name}", "Track your orders, view tickets, request refunds"));
        add(wireSectionTitle("My orders"));
        add(wireGrid("Date", "Event", "Total", "Status", "Receipt"));
        add(wireSectionTitle("My tickets"));
        add(wireGrid("Event", "Date", "Zone", "Seat", "Status", "Actions"));
        add(wireBox("Per ticket: \"View barcode\" → dialog with barcode text · \"Refund\" while II.3.9 allows"));
    }
}
