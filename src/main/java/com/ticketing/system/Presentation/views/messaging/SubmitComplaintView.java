package com.ticketing.system.Presentation.views.messaging;

import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.LkBanner;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkCol;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.components.kit.LkSelect;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.account.SupportInboxView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.List;

@Route(value = "support/new", layout = MainLayout.class)
@PageTitle("Submit complaint · TicketHub")
@PermitAll
public class SubmitComplaintView extends LkPage {

    private final LkSelect about    = new LkSelect("An order or ticket",
        List.of("An order or ticket", "An event or organizer", "Payment or refund", "The TicketHub app", "Something else"));
    private final TextField subject = new TextField("Subject");
    private final TextArea  details = new TextArea("Details");

    public SubmitComplaintView() {
        title("Submit a complaint");
        subtitle("Tell us what went wrong — an admin will respond shortly.");
        add(buildForm());
    }

    private Component buildForm() {
        Div narrow = new Div();
        narrow.addClassName("form-narrow");

        LkCard card = new LkCard("New complaint").pad(20);

        about.label("About");
        subject.setPlaceholder("Brief summary of the issue");
        subject.setRequired(true);
        subject.setWidthFull();
        details.setPlaceholder("Describe what happened, including any order or event references…");
        details.setMinHeight("160px");
        details.setRequired(true);
        details.setWidthFull();

        LkCol col = new LkCol().gap(14);
        col.add(about, subject, details);
        col.add(new LkBanner(LkBanner.Tone.info, new LkIcon("info", 17),
            "Complaints go to the TicketHub admin team and are tracked in your Support inbox."));
        card.add(col);

        LkRow actions = new LkRow().gap(8).justify("flex-end");
        actions.getStyle().set("margin-top", "16px");
        actions.add(
            new LkBtn("Cancel").variant(LkBtn.Variant.tertiary)
                .onClick(e -> UI.getCurrent().navigate(SupportInboxView.class)),
            new LkBtn("Submit complaint").variant(LkBtn.Variant.primary)
                .onClick(e -> submit())
        );
        card.add(actions);

        narrow.add(card);
        return narrow;
    }

    private void submit() {
        if (subject.isEmpty() || details.isEmpty()) {
            Toasts.failure("Please fill in subject and details.");
            return;
        }
        Toasts.success("Complaint submitted — we'll get back to you in your Support inbox.");
        UI.getCurrent().navigate(SupportInboxView.class);
    }
}
