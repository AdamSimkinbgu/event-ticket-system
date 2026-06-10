package com.ticketing.system.Presentation.views.admin;

import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkCol;
import com.ticketing.system.Presentation.components.kit.LkGrid;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.components.kit.LkSelect;
import com.ticketing.system.Presentation.layouts.PlatformAdminLayout;
import com.ticketing.system.Presentation.security.Capability;
import com.ticketing.system.Presentation.security.RequireCapability;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Route(value = "admin/announcements", layout = PlatformAdminLayout.class)
@PageTitle("Announcements · Admin")
@PermitAll
@RequireCapability(Capability.BROADCAST_ANNOUNCEMENT)
public class AdminAnnouncementsView extends LkPage {

    private final TextField subject  = new TextField("Subject");
    private final TextArea  body     = new TextArea("Body");
    private final LkSelect  audience = new LkSelect("All members",
        List.of("All members", "Owners only", "Managers only", "Specific role")).label("Audience");

    public AdminAnnouncementsView() {
        title("System announcements");
        subtitle("Broadcast a Conversation with type=ANNOUNCEMENT to members or a specific role.");

        add(buildComposer());
        add(Lk.h2("Past announcements"));
        add(buildHistory());
    }

    private Component buildComposer() {
        LkCard card = new LkCard("New announcement").pad(20);

        subject.setPlaceholder("Short, clear headline");
        subject.setWidthFull();
        body.setPlaceholder("Body of the announcement…");
        body.setHeight("160px");
        body.setWidthFull();

        LkCol col = new LkCol().gap(14);
        col.add(subject, body, audience);
        card.add(col);

        LkRow actions = new LkRow().gap(8).justify("flex-end");
        actions.add(
            new LkBtn("Save draft").variant(LkBtn.Variant.tertiary)
                .onClick(e -> Toasts.success("Draft saved.")),
            new LkBtn("Send announcement").variant(LkBtn.Variant.primary)
                .icon(new LkIcon("arrowRight", 15))
                .onClick(e -> {
                    if (subject.isEmpty() || body.isEmpty()) {
                        Toasts.failure("Subject and body are required.");
                        return;
                    }
                    Toasts.success("Sent to " + audience.getValue() + ".");
                    subject.clear();
                    body.clear();
                })
        );
        card.add(actions);
        return card;
    }

    private Component buildHistory() {
        LkCard card = new LkCard().pad(0);
        LkGrid grid = new LkGrid()
            .col("Sent",        "sent")
            .col("Subject",     "subj")
            .col("Audience",    "aud")
            .col("Recipients",  "rec", LkGrid.Align.RIGHT)
            .col("Sender",      "send");

        history(grid, "26 Jun 2026 · 10:14", "Maintenance window — Sat 06:00 UTC", "All members",   "82,481", "platform.admin");
        history(grid, "21 Jun 2026 · 18:02", "New refund policy now in effect",     "All members",   "82,103", "platform.admin");
        history(grid, "14 Jun 2026 · 09:30", "Managers: dashboard redesign rollout","Managers only","2,408",  "bar.miyara");

        grid.build();
        card.add(grid);
        return card;
    }

    private void history(LkGrid grid, String sent, String subject, String audience, String recipients, String sender) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sent", sent);
        Span s = new Span();
        s.getElement().setProperty("innerHTML", "<b>" + subject + "</b>");
        row.put("subj", s);
        row.put("aud",  audience);
        row.put("rec",  recipients);
        row.put("send", Lk.mono(sender));
        grid.row(row);
    }
}
