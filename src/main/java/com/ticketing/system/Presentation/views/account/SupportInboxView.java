package com.ticketing.system.Presentation.views.account;

import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.messaging.MdConvRow;
import com.ticketing.system.Presentation.components.messaging.MdReplyBar;
import com.ticketing.system.Presentation.components.messaging.MdThread;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.messaging.SubmitComplaintView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.ArrayList;
import java.util.List;

@Route(value = "support", layout = MainLayout.class)
@PageTitle("Support · TicketHub")
@PermitAll
public class SupportInboxView extends LkPage {

    private record Conversation(String iconName, String subject, String who, String time, String unread, String status, List<MdThread.Message> thread) { }

    private final List<Conversation> conversations = new ArrayList<>(List.of(
        new Conversation("ticket",   "Ticket transfer",       "Live Nation Israel", "2d", null, "Resolved", new ArrayList<>(List.of(
            new MdThread.Message("You",                  "Mon 14:02", true,  "Hi — can I transfer my Coldplay tickets to a friend?"),
            new MdThread.Message("Live Nation Israel",   "Mon 15:20", false, "Hi Alex! Yes — open the ticket in My Tickets and tap Transfer. The recipient needs a TicketHub account."),
            new MdThread.Message("You",                  "Mon 15:31", true,  "Perfect, thank you!")
        ))),
        new Conversation("card",     "Refund for Othello",    "TicketHub Support",  "5d", "1",  "Open",     new ArrayList<>(List.of(
            new MdThread.Message("You",               "Wed 09:11", true,  "The event was cancelled — when will my refund land?"),
            new MdThread.Message("TicketHub Support", "Wed 10:02", false, "Refund issued — it'll clear to your card in 3–5 business days.")
        ))),
        new Conversation("building", "Venue accessibility",   "Habima Theatre",     "1w", null, "Resolved", new ArrayList<>(List.of(
            new MdThread.Message("You",            "27 May 17:30", true,  "Is there step-free access to Row C?"),
            new MdThread.Message("Habima Theatre", "28 May 09:14", false, "Yes — east entrance leads directly to Row C with no stairs.")
        )))
    ));

    private int selected = 0;
    private final List<MdConvRow> convRows = new ArrayList<>();
    private LkCard detailCard;

    public SupportInboxView() {
        title("Support inbox");
        subtitle("Your conversations with organizers and the TicketHub team.");
        actions(
            new LkBtn("New inquiry").variant(LkBtn.Variant.secondary)
                .icon(new LkIcon("comment", 15))
                .onClick(e -> Toasts.warn("New-inquiry composer wires with V2-MSG-04.")),
            new LkBtn("Submit complaint").variant(LkBtn.Variant.primary)
                .icon(new LkIcon("warning", 15))
                .onClick(e -> UI.getCurrent().navigate(SubmitComplaintView.class))
        );
        add(buildSplit());
    }

    private Component buildSplit() {
        Div split = new Div();
        split.addClassName("md-split");
        split.add(buildConversationList(), buildDetailCard());
        return split;
    }

    private Component buildConversationList() {
        LkCard card = new LkCard("Conversations").pad(8);
        convRows.clear();
        for (int i = 0; i < conversations.size(); i++) {
            Conversation conv = conversations.get(i);
            int idx = i;
            MdConvRow row = new MdConvRow(conv.iconName, conv.subject, conv.who, conv.time, conv.unread);
            if (i == selected) row.active();
            row.onSelect(() -> selectConversation(idx));
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

    private void selectConversation(int idx) {
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
        Conversation conv = conversations.get(selected);
        detailCard.title(conv.subject);
        detailCard.subtitle(conv.who + " · " + conv.status);

        MdThread thread = new MdThread(conv.thread);
        detailCard.add(thread);

        MdReplyBar reply = new MdReplyBar();
        reply.onSend(text -> {
            conv.thread.add(new MdThread.Message("You", "now", true, text));
            renderDetail();
        });
        detailCard.add(reply);
    }
}
