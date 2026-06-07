package com.ticketing.system.Presentation.views.account;

import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "my-profile", layout = MainLayout.class)
@PageTitle("My profile · Event Ticket Platform")
@PermitAll
public class MyProfileView extends PlaceholderView {
    public MyProfileView() {
        super(
            "My profile",
            "V2-MEM-02",
            "Moshe Klimer",
            "Read-only profile display. II.3.4 (edit profile) is V1 Tier A exempt — this view is voluntary scope, display only. If a team drops tier this view becomes an edit form."
        );
        add(wireCard("Profile (read-only — II.3.4 edit is V1 Tier A exempt)",
            wireForm("Username", "Email", "Registration date")
        ));
        add(wireBox("Logout: top-right Avatar menu in MainLayout (V2-F-02)"));
    }
}
