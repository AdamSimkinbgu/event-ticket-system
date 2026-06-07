package com.ticketing.system.Presentation.views.messaging;

import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "support/new", layout = MainLayout.class)
@PageTitle("Submit complaint · Event Ticket Platform")
@PermitAll
public class SubmitComplaintView extends PlaceholderView {
    public SubmitComplaintView() {
        super(
            "Submit a complaint",
            "V2-MSG-02",
            "Bentzion Hadad",
            "Member-initiated complaint flow (II.3.3). Creates Conversation with type=COMPLAINT, counterparty=ADMIN_GROUP via MessagingService.submitComplaint. Admin handles via V2-VIEW-NW-03."
        );
        add(wireCard("New complaint",
            wireForm("Subject (TextField, required)", "Body (TextArea, required)")
        ));
        add(wireActions("Submit complaint", "Cancel"));
        add(wireBox("Success: Toasts.success(\"Complaint submitted, an admin will respond shortly\") · Failure: structured error toast (req 3.5)"));
    }
}
