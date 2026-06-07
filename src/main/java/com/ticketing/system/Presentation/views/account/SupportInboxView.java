package com.ticketing.system.Presentation.views.account;

import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "support", layout = MainLayout.class)
@PageTitle("Support · Event Ticket Platform")
@PermitAll
public class SupportInboxView extends PlaceholderView {
    public SupportInboxView() {
        super(
            "Support inbox",
            "V2-MSG-01",
            "Bentzion Hadad",
            "Member's view into Conversation threads with companies + admin (II.3.10, II.4.4). Backed by MessagingService.viewMyConversations. Master/detail layout (Pattern A)."
        );
        add(wireSplit(1, 2,
            wireCard("Conversations",
                wireGrid("Subject", "Counterparty", "Unread", "Last reply"),
                wireActions("New inquiry to company", "Submit complaint")
            ),
            wireCard("Reply pane",
                wireBox("Thread — admin / company replies + member messages"),
                wireForm("Your reply (TextArea)"),
                wireActions("Send")
            )
        ));
    }
}
