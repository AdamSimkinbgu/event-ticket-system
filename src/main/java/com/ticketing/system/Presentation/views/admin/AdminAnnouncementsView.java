package com.ticketing.system.Presentation.views.admin;

import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBanner;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkCol;
import com.ticketing.system.Presentation.components.kit.LkGrid;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.components.kit.LkSelect;
import com.ticketing.system.Presentation.layouts.PlatformAdminLayout;
import com.ticketing.system.Presentation.presenters.messaging.AdminAnnouncementsPresenter;
import com.ticketing.system.Presentation.presenters.messaging.AdminAnnouncementsPresenter.SentAnnouncement;
import com.ticketing.system.Presentation.security.Capability;
import com.ticketing.system.Presentation.security.RequireCapability;
import com.ticketing.system.Presentation.session.AuthSession;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Route(value = "admin/announcements", layout = PlatformAdminLayout.class)
@PageTitle("Announcements · Admin")
@PermitAll
@RequireCapability(Capability.BROADCAST_ANNOUNCEMENT)
public class AdminAnnouncementsView extends LkPage {

    private static final String AUDIENCE_MEMBERS = "All members";
    private static final String AUDIENCE_PRODUCERS = "All producers";
    private static final DateTimeFormatter SENT_FMT = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm");

    private final AdminAnnouncementsPresenter presenter;

    private final TextField subject  = new TextField("Subject");
    private final TextArea  body     = new TextArea("Body");
    private final LkSelect  audience = new LkSelect(AUDIENCE_MEMBERS,
        List.of(AUDIENCE_MEMBERS, AUDIENCE_PRODUCERS)).label("Audience");

    /** History grid lives in its own slot so it can reload in place after a send. */
    private final Div historySlot = new Div();

    public AdminAnnouncementsView(AdminAnnouncementsPresenter presenter) {
        this.presenter = presenter;

        title("System announcements");
        subtitle("Broadcast a Conversation with type=ANNOUNCEMENT to all members or all producers.");

        add(buildComposer());
        add(Lk.h2("Past announcements"));
        add(historySlot);
        reloadHistory();
    }

    // -- Composer -------------------------------------------------------------

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
                .onClick(e -> Toasts.warn("Draft saving isn't available yet.")),
            new LkBtn("Send announcement").variant(LkBtn.Variant.primary)
                .icon(new LkIcon("arrowRight", 15))
                .onClick(e -> send())
        );
        card.add(actions);
        return card;
    }

    private void send() {
        if (subject.isEmpty() || body.isEmpty()) {
            Toasts.failure("Subject and body are required.");
            return;
        }
        switch (presenter.send(AuthSession.token(), subject.getValue(), body.getValue(), audienceType())) {
            case AdminAnnouncementsPresenter.ActionOutcome.Success ok -> {
                Toasts.success("Sent to " + String.format("%,d", ok.recipientCount()) + " recipient(s).");
                subject.clear();
                body.clear();
                reloadHistory();
            }
            case AdminAnnouncementsPresenter.ActionOutcome.NotAuthenticated ignored ->
                Toasts.failure("Your session has expired — please sign in again.");
            case AdminAnnouncementsPresenter.ActionOutcome.Failure fail ->
                Toasts.failure("Could not send the announcement: " + fail.reason());
        }
    }

    /** Maps the audience dropdown label to the MessagingService audience type. */
    private String audienceType() {
        return AUDIENCE_PRODUCERS.equals(audience.getValue()) ? "PRODUCERS" : "ALL_MEMBERS";
    }

    // -- History --------------------------------------------------------------

    private void reloadHistory() {
        historySlot.removeAll();
        switch (presenter.load(AuthSession.token())) {
            case AdminAnnouncementsPresenter.Outcome.Success ok -> historySlot.add(buildHistoryCard(ok.announcements()));
            case AdminAnnouncementsPresenter.Outcome.NotAuthenticated ignored -> historySlot.add(
                banner("Your session has expired — please sign in again."));
            case AdminAnnouncementsPresenter.Outcome.Failure fail -> historySlot.add(
                banner("Could not load announcement history: " + fail.reason()));
        }
    }

    private Component buildHistoryCard(List<SentAnnouncement> announcements) {
        LkCard card = new LkCard().pad(0);
        if (announcements.isEmpty()) {
            card.add(Lk.muted("No announcements sent yet."));
            return card;
        }

        LkGrid grid = new LkGrid()
            .col("Sent",       "sent")
            .col("Subject",    "subj")
            .col("Audience",   "aud")
            .col("Recipients", "rec", LkGrid.Align.RIGHT)
            .col("Sender",     "send");

        for (SentAnnouncement a : announcements) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sent", a.sentAt() == null ? "—" : a.sentAt().format(SENT_FMT));
            // Plain-text Span (Vaadin escapes it) styled bold — never set innerHTML with user content.
            Span subj = new Span(a.subject() == null ? "" : a.subject());
            subj.getStyle().set("font-weight", "600");
            row.put("subj", subj);
            row.put("aud", a.audienceLabel());
            row.put("rec", String.format("%,d", a.recipientCount()));
            row.put("send", Lk.mono(a.senderLabel()));
            grid.row(row);
        }

        grid.build();
        card.add(grid);
        return card;
    }

    private Component banner(String message) {
        return new LkBanner(LkBanner.Tone.info, new LkIcon("info", 18), message);
    }
}
