package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkFilterChip;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.components.messaging.MdConvRow;
import com.ticketing.system.Presentation.components.messaging.MdReplyBar;
import com.ticketing.system.Presentation.components.messaging.MdThread;
import com.ticketing.system.Presentation.layouts.AdminLayout;
import com.ticketing.system.Presentation.security.RequiresOwnerCompany;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.ArrayList;
import java.util.List;

@Route(value = "owner/inquiries", layout = AdminLayout.class)
@PageTitle("Member inquiries · TicketHub")
@PermitAll
public class CompanyInquiryInboxView extends LkPage implements RequiresOwnerCompany {

    private record Inquiry(String iconName, String subject, String who, String time,
                           String unread, String status, List<MdThread.Message> thread) { }

    private final List<Inquiry> inquiries = new ArrayList<>(List.of(
        new Inquiry("comment", "Wheelchair access", "Maya Goldberg · Coldplay", "2h", null, "Open", new ArrayList<>(List.of(
            new MdThread.Message("Maya Goldberg",            "Tue 10:14", false, "Is there wheelchair access for Lower L at the Coldplay show?"),
            new MdThread.Message("You · Live Nation Israel", "Tue 11:02", true,  "Hi Maya! Yes — Lower L has step-free access via the east entrance, and we hold accessible seats in rows A–B. Want me to reserve a pair?"),
            new MdThread.Message("Maya Goldberg",            "Tue 11:20", false, "That would be perfect, thank you!")
        ))),
        new Inquiry("comment", "Re-entry policy",   "Tom Azoulay · Coldplay",   "5h", "2",  "Open", new ArrayList<>(List.of(
            new MdThread.Message("Tom Azoulay", "Sun 18:42", false, "Hi — if I leave during the support act can I come back in?"),
            new MdThread.Message("Tom Azoulay", "Sun 18:43", false, "Just to grab something from the car.")
        ))),
        new Inquiry("comment", "Parking near venue", "Noa Levi · Hapoel TLV",   "1d", null, "Open", new ArrayList<>(List.of(
            new MdThread.Message("Noa Levi", "Mon 21:00", false, "Is there parking on-site or do I need to find street parking?")
        ))),
        new Inquiry("comment", "Group booking (12)", "Eitan Bar · Mashina",     "2d", null, "Open", new ArrayList<>(List.of(
            new MdThread.Message("Eitan Bar", "Sat 09:30", false, "We're a group of 12 — any discount for buying in bulk?")
        )))
    ));

    private int selected = 0;
    private final List<MdConvRow> convRows = new ArrayList<>();
    private LkCard detailCard;

    public CompanyInquiryInboxView() {
        title("Member inquiries");
        subtitle("Questions from members about your events and company.");
        add(buildFilters());
        add(buildSplit());
    }

    private Component buildFilters() {
        LkRow row = new LkRow().gap(8);
        row.add(
            new LkFilterChip("Status", List.of("Open", "Responded", "Resolved", "All"), true, List.of("Open")),
            new LkFilterChip("Event",  List.of("Coldplay · MOTS", "Hapoel TLV", "Mashina"), true, List.of())
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
        LkCard card = new LkCard("Inquiries").pad(8);
        convRows.clear();
        for (int i = 0; i < inquiries.size(); i++) {
            Inquiry inq = inquiries.get(i);
            int idx = i;
            MdConvRow row = new MdConvRow(inq.iconName, inq.subject, inq.who, inq.time, inq.unread);
            if (i == selected) row.active();
            row.onSelect(() -> selectInquiry(idx));
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

    private void selectInquiry(int idx) {
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
        Inquiry inq = inquiries.get(selected);
        detailCard.title(inq.subject);
        detailCard.subtitle(inq.who + " · " + inq.status);

        detailCard.add(new MdThread(inq.thread));

        Div actionRow = new Div();
        actionRow.addClassName("md-detail-actions");
        actionRow.add(
            new LkBtn("Mark resolved").variant(LkBtn.Variant.tertiary)
                .onClick(e -> Toasts.success("Marked as resolved."))
        );
        detailCard.add(actionRow);

        MdReplyBar reply = new MdReplyBar();
        reply.onSend(text -> {
            inq.thread.add(new MdThread.Message("You · Live Nation Israel", "now", true, text));
            renderDetail();
        });
        detailCard.add(reply);
    }
}
