package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Core.Application.dto.UserCompanyDTO;
import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.LkBanner;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkCol;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.layouts.WorkspaceLayout;
import com.ticketing.system.Presentation.presenters.company.MyCompaniesPresenter;
import com.ticketing.system.Presentation.security.Capability;
import com.ticketing.system.Presentation.security.RequireCapability;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "owner/owners/appoint", layout = WorkspaceLayout.class)
@PageTitle("Appoint co-owner · TicketHub")
@PermitAll
@RequireCapability(Capability.APPOINT_CO_OWNER)
public class OwnerAppointmentView extends LkPage {

    private final TextField invitee = new TextField("Invitee username or email");
    private final TextField scope   = new TextField("Scope");

    public OwnerAppointmentView(MyCompaniesPresenter membershipPresenter) {
        title("Appoint co-owner");
        subtitle("Co-owners have full company access, except removing the founder.");
        add(buildForm(membershipPresenter));
    }

    private Component buildForm(MyCompaniesPresenter membershipPresenter) {
        Div narrow = new Div();
        narrow.addClassName("form-narrow");

        LkCard card = new LkCard("Appoint co-owner").pad(20);

        invitee.setPlaceholder("Who do you want to appoint?");
        invitee.setRequired(true);
        invitee.setWidthFull();

        UserCompanyDTO current = membershipPresenter.currentCompany();
        String companyName = current == null
            ? "this company"
            : current.name() + " (this company)";
        scope.setValue(companyName);
        scope.setReadOnly(true);
        scope.setWidthFull();

        LkCol col = new LkCol().gap(14);
        col.add(invitee, scope);
        card.add(col);

        narrow.add(card);
        narrow.add(new LkBanner(LkBanner.Tone.warn, new LkIcon("warning", 17),
            "Cycle prevention is enforced — you cannot appoint someone who already appointed you, and the founder cannot be re-appointed."));

        LkRow actions = new LkRow().gap(8).justify("flex-end");
        actions.add(
            new LkBtn("Cancel").variant(LkBtn.Variant.tertiary)
                .onClick(e -> UI.getCurrent().navigate(MyCompaniesView.class)),
            new LkBtn("Send invitation").variant(LkBtn.Variant.primary)
                .onClick(e -> {
                    if (invitee.isEmpty()) {
                        Toasts.failure("Enter a username or email to appoint.");
                        return;
                    }
                    Toasts.success(invitee.getValue() + " invited as co-owner — they'll see it in their invitations.");
                    UI.getCurrent().navigate(MyCompaniesView.class);
                })
        );
        narrow.add(actions);
        return narrow;
    }
}
