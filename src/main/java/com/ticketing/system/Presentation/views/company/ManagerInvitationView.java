package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkCheckRow;
import com.ticketing.system.Presentation.components.kit.LkCol;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.layouts.WorkspaceLayout;
import com.ticketing.system.Presentation.security.Capability;
import com.ticketing.system.Presentation.security.RequireCapability;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.List;

@Route(value = "owner/managers/invite", layout = WorkspaceLayout.class)
@PageTitle("Invite manager · TicketHub")
@PermitAll
@RequireCapability(Capability.APPOINT_MANAGER)
public class ManagerInvitationView extends LkPage {

    private static final List<String> PERMS = List.of(
        "Manage events",
        "View sales history",
        "Edit purchase policies",
        "Respond to inquiries",
        "Manage venue maps"
    );

    private final TextField invitee = new TextField("Username or email");

    public ManagerInvitationView() {
        title("Invite a manager");
        subtitle("Grant a member scoped access to manage this company.");
        add(buildForm());
    }

    private Component buildForm() {
        Div narrow = new Div();
        narrow.addClassName("form-narrow");

        LkCard inviteeCard = new LkCard("Invitee").pad(20);
        invitee.setPlaceholder("Who do you want to invite?");
        invitee.setRequired(true);
        invitee.setWidthFull();
        inviteeCard.add(invitee);

        LkCard permsCard = new LkCard("Permissions").pad(20);
        LkCol col = new LkCol().gap(4);
        for (int i = 0; i < PERMS.size(); i++) {
            // First two permissions checked by default to match the React reference.
            col.add(new LkCheckRow(PERMS.get(i), i < 2));
        }
        permsCard.add(col);

        LkRow actions = new LkRow().gap(8).justify("flex-end");
        actions.add(
            new LkBtn("Cancel").variant(LkBtn.Variant.tertiary)
                .onClick(e -> UI.getCurrent().navigate(ManagerListView.class)),
            new LkBtn("Send invitation").variant(LkBtn.Variant.primary)
                .onClick(e -> {
                    if (invitee.isEmpty()) {
                        Toasts.failure("Enter a username or email to invite.");
                        return;
                    }
                    Toasts.success("Invitation sent to " + invitee.getValue() + ".");
                    UI.getCurrent().navigate(ManagerListView.class);
                })
        );

        narrow.add(inviteeCard, permsCard, actions);
        return narrow;
    }
}