package com.ticketing.system.Presentation.views.admin;

import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkFilterChip;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.components.messaging.MdConvRow;
import com.ticketing.system.Presentation.components.messaging.MdReplyBar;
import com.ticketing.system.Presentation.components.messaging.MdThread;
import com.ticketing.system.Presentation.layouts.PlatformAdminLayout;
import com.ticketing.system.Presentation.security.RequiresAdminRole;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.ArrayList;
import java.util.List;

@Route(value = "admin/complaints", layout = PlatformAdminLayout.class)
@PageTitle("Complaint queue · Admin")
@PermitAll
public class AdminComplaintQueueView extends LkPage implements RequiresAdminRole {

    private record Complaint(String iconName, String subject, String who, String time,
                             String unread, String status, List<MdThread.Message> thread) { }

    private final List<Complaint> complaints = new ArrayList<>(List.of(
        new Complaint("warning", "Charged twice for Coldplay",      "alex.morgan · 26 Jun", "12m", "2", "Open", new ArrayList<>(List.of(
            new MdThread.Message("alex.morgan", "26 Jun 09:14", false, "Hi — my card was charged twice for order #84021. Please refund the duplicate."),
            new MdThread.Message("alex.morgan", "26 Jun 09:15", false, "Both charges show as PAID in my account.")
        ))),
        new Complaint("warning", "Refund not received yet",         "noa.levi · 25 Jun",    "2h",  null, "Responded", new ArrayList<>(List.of(
            new MdThread.Message("noa.levi",         "25 Jun 18:42", false, "It's been 7 days since my refund was approved and nothing has arrived."),
            new MdThread.Message("platform.admin",   "26 Jun 09:01", true,  "Hi Noa — banks take 5–10 business days. If you don't see it by 28 Jun, reply here and we'll escalate.")
        ))),
        new Complaint("warning", "Event cancelled, no notification","tom.azoulay · 24 Jun", "1d",  null, "Open", new ArrayList<>(List.of(
            new MdThread.Message("tom.azoulay", "24 Jun 14:00", false, "I showed up at the venue and it was cancelled. I never got an email.")
        )))
    ));

    private int selected = 0;
    private final List<MdConvRow> convRows = new ArrayList<>();
    private LkCard detailCard;

    public AdminComplaintQueueView() {
        title("Complaint queue");
        subtitle("Member complaints opened via Conversation type=COMPLAINT.");

        add(buildFilters());
        add(buildSplit());
    }

    private Component buildFilters() {
        LkRow row = new LkRow().gap(8);
        row.add(
            new LkFilterChip("Status", List.of("Open", "Responded", "Resolved", "All"), false, List.of("Open")),
            new LkFilterChip("Company", List.of("Live Nation Israel", "Coca-Cola Arena", "Shuni Productions"), true, List.of())
        );
        return row;
    }

    private Component buildSplit() {
        Div split = new Div();
        split.addClassName("md-split");
        split.add(buildList(), buildDetailCard());
        return split;
    }

    private Component buildList() {
        LkCard card = new LkCard("Complaints").pad(8);
        convRows.clear();
        for (int i = 0; i < complaints.size(); i++) {
            Complaint c = complaints.get(i);
            int idx = i;
            MdConvRow row = new MdConvRow(c.iconName, c.subject, c.who, c.time, c.unread);
            if (i == selected) row.active();
            row.onSelect(() -> selectComplaint(idx));
            convRows.add(row);
            card.add(row);
        }
        return card;
    }

    private Component buildDetailCard() {
        detailCard = new LkCard().pad(0);
        renderDetail();
        return detailCard;
    }

    private void selectComplaint(int idx) {
        if (idx == selected) return;
        selected = idx;
        for (int i = 0; i < convRows.size(); i++) {
            convRows.get(i).active(i == selected);
        }
        renderDetail();
    }

    private void renderDetail() {
        if (detailCard == null) return;
        detailCard.removeAll();
        Complaint c = complaints.get(selected);
        detailCard.title(c.subject);
        detailCard.subtitle(c.who + " · " + c.status);

        detailCard.add(new MdThread(c.thread));

        Div actionRow = new Div();
        actionRow.addClassName("md-detail-actions");
        actionRow.add(
            new LkBtn("Mark resolved").variant(LkBtn.Variant.tertiary)
                .onClick(e -> Toasts.success("Complaint marked resolved."))
        );
        detailCard.add(actionRow);

        MdReplyBar reply = new MdReplyBar();
        reply.onSend(text -> {
            c.thread.add(new MdThread.Message("platform.admin", "now", true, text));
            renderDetail();
        });
        detailCard.add(reply);
    }
}
