package com.ticketing.system.Presentation.views.admin;

import com.ticketing.system.Core.Application.dto.ConversationDTO;
import com.ticketing.system.Core.Application.dto.MessageDTO;
import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.LkBanner;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkFilterChip;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.components.messaging.MdConvRow;
import com.ticketing.system.Presentation.components.messaging.MdReplyBar;
import com.ticketing.system.Presentation.components.messaging.MdThread;
import com.ticketing.system.Presentation.layouts.PlatformAdminLayout;
import com.ticketing.system.Presentation.presenters.messaging.AdminComplaintQueuePresenter;
import com.ticketing.system.Presentation.security.Capability;
import com.ticketing.system.Presentation.security.RequireCapability;
import com.ticketing.system.Presentation.session.AuthSession;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * System-admin complaint queue (#269): lists the platform-wide member complaints, lets an admin
 * read each thread, reply, and mark a complaint resolved — all backed by
 * {@link AdminComplaintQueuePresenter} → {@code MessagingService}.
 *
 * <p>Both reply and "Mark resolved" go through {@code respondToComplaint}: a reply auto-flips the
 * status to Responded; resolve sends a short closing note with status Resolved (the domain forbids
 * blank message bodies, so resolve can't be message-less).
 *
 * <p>Third wired master/detail messaging inbox after {@code SupportInboxView} (#277) and
 * {@code CompanyInquiryInboxView} (#268).
 */
@Route(value = "admin/complaints", layout = PlatformAdminLayout.class)
@PageTitle("Complaint queue · Admin")
@PermitAll
@RequireCapability(Capability.MANAGE_COMPLAINTS)
public class AdminComplaintQueueView extends LkPage {

    private static final String ALL = "All";

    private final AdminComplaintQueuePresenter presenter;

    /** Holds the filter row + master/detail split, or an empty/error banner, so it can be
     *  rebuilt in place without disturbing the page header. */
    private final Div content = new Div();

    private List<ConversationDTO> complaints = List.of();
    private final List<MdConvRow> convRows = new ArrayList<>();
    private String selectedId;
    private String statusFilter = ALL;   // server-side filter, re-queried on change
    private LkCard detailCard;

    public AdminComplaintQueueView(AdminComplaintQueuePresenter presenter) {
        this.presenter = presenter;

        title("Complaint queue");
        subtitle("Member complaints opened via Conversation type=COMPLAINT.");
        add(content);
        reload();
    }

    // -- Loading / rendering --------------------------------------------------

    private void reload() {
        switch (presenter.load(AuthSession.token(), statusFilter)) {
            case AdminComplaintQueuePresenter.Outcome.Success ok -> renderQueue(ok.complaints());
            case AdminComplaintQueuePresenter.Outcome.NotAuthenticated ignored -> showBanner(
                "Your session has expired — please sign in again.");
            case AdminComplaintQueuePresenter.Outcome.Failure fail -> showBanner(
                fail.error().message());
        }
    }

    private void renderQueue(List<ConversationDTO> list) {
        this.complaints = list;
        content.removeAll();
        content.add(buildFilters());

        if (list.isEmpty()) {
            content.add(banner(ALL.equals(statusFilter)
                ? "No complaints in the queue yet."
                : "No complaints match this filter."));
            return;
        }

        // Keep the prior selection if it's still present, else select the first (newest).
        int selIdx = indexOf(selectedId);
        if (selIdx < 0) selIdx = 0;
        selectedId = list.get(selIdx).conversationId();

        Div split = new Div();
        split.addClassName("md-split");
        split.add(buildConversationList(), buildDetailCard());
        content.add(split);
    }

    private Component buildFilters() {
        LkRow row = new LkRow().gap(8);
        List<String> applied = ALL.equals(statusFilter) ? List.of() : List.of(statusFilter);
        LkFilterChip status = new LkFilterChip("Status",
            List.of("Open", "Responded", "Resolved", "Closed"), true, applied);
        status.onApply(() -> {
            Set<String> selected = status.getSelected();
            statusFilter = selected.isEmpty() ? ALL : selected.iterator().next();
            reload();   // server-side re-query
        });
        row.add(status);
        return row;
    }

    private Component buildConversationList() {
        LkCard card = new LkCard("Complaints").pad(8);
        convRows.clear();
        for (ConversationDTO c : complaints) {
            MdConvRow row = new MdConvRow("warning", c.subject(), "Member",
                relativeTime(c.lastMessageAt()), unreadBadge(c.unreadCountForViewer()));
            if (c.conversationId().equals(selectedId)) row.active();
            row.onSelect(() -> selectById(c.conversationId()));
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

    /** Selects a thread by id in response to a row click (toggles rows in place). */
    private void selectById(String conversationId) {
        int idx = indexOf(conversationId);
        if (idx < 0 || conversationId.equals(selectedId)) return;
        selectedId = conversationId;
        for (int i = 0; i < convRows.size(); i++) {
            convRows.get(i).active(i == idx);
        }
        renderDetail();
    }

    private void renderDetail() {
        if (detailCard == null) return;
        detailCard.removeAll();
        ConversationDTO conv = byId(selectedId);
        if (conv == null) return;

        detailCard.title(conv.subject());
        detailCard.subtitle("Member · " + humanizeStatus(conv.status()));
        detailCard.add(new MdThread(toThreadMessages(conv.messages())));

        // Terminal complaints (RESOLVED / CLOSED) accept no further messages and can't be re-resolved.
        if (!isTerminal(conv.status())) {
            Div actionRow = new Div();
            actionRow.addClassName("md-detail-actions");
            actionRow.add(new LkBtn("Mark resolved").variant(LkBtn.Variant.tertiary)
                .onClick(e -> handleResolve()));
            detailCard.add(actionRow);

            MdReplyBar reply = new MdReplyBar();
            reply.onSend(this::handleReply);
            detailCard.add(reply);
        }
    }

    private void handleReply(String text) {
        switch (presenter.reply(AuthSession.token(), selectedId, text)) {
            case AdminComplaintQueuePresenter.ActionOutcome.Success ignored ->
                reload(); // refresh thread, ordering, status, and unread badges
            case AdminComplaintQueuePresenter.ActionOutcome.NotAuthenticated ignored ->
                Toasts.failure("Your session has expired — please sign in again.");
            case AdminComplaintQueuePresenter.ActionOutcome.Failure fail ->
                Toasts.failure(fail.error());
        }
    }

    private void handleResolve() {
        switch (presenter.resolve(AuthSession.token(), selectedId)) {
            case AdminComplaintQueuePresenter.ActionOutcome.Success ignored -> {
                Toasts.success("Complaint marked resolved.");
                reload();
            }
            case AdminComplaintQueuePresenter.ActionOutcome.NotAuthenticated ignored ->
                Toasts.failure("Your session has expired — please sign in again.");
            case AdminComplaintQueuePresenter.ActionOutcome.Failure fail ->
                Toasts.failure(fail.error());
        }
    }

    private void showBanner(String message) {
        this.complaints = List.of();
        content.removeAll();
        content.add(banner(message));
    }

    private Component banner(String message) {
        return new LkBanner(LkBanner.Tone.info, new LkIcon("info", 18), message);
    }

    // -- Lookups --------------------------------------------------------------

    private int indexOf(String conversationId) {
        if (conversationId == null) return -1;
        for (int i = 0; i < complaints.size(); i++) {
            if (complaints.get(i).conversationId().equals(conversationId)) return i;
        }
        return -1;
    }

    private ConversationDTO byId(String conversationId) {
        int idx = indexOf(conversationId);
        return idx < 0 ? null : complaints.get(idx);
    }

    // -- Display mapping ------------------------------------------------------

    /** From the admin's vantage point, ADMIN messages are "You"; the other side is the member. */
    private static List<MdThread.Message> toThreadMessages(List<MessageDTO> messages) {
        List<MdThread.Message> out = new ArrayList<>();
        for (MessageDTO m : messages) {
            boolean me = "ADMIN".equals(m.senderType());
            String from = me ? "You" : "Member";
            out.add(new MdThread.Message(from, relativeTime(m.sentAt()), me, m.body()));
        }
        return out;
    }

    private static String unreadBadge(int unreadCount) {
        return unreadCount > 0 ? String.valueOf(unreadCount) : null;
    }

    private static boolean isTerminal(String status) {
        return "RESOLVED".equals(status) || "CLOSED".equals(status);
    }

    /** "OPEN" → "Open", "RESPONDED" → "Responded", etc. */
    private static String humanizeStatus(String status) {
        if (status == null || status.isEmpty()) return "";
        return status.charAt(0) + status.substring(1).toLowerCase();
    }

    /** Compact relative label from a timestamp: now / Nm / Nh / Nd / Nw. */
    private static String relativeTime(LocalDateTime when) {
        if (when == null) return "";
        Duration d = Duration.between(when, LocalDateTime.now());
        long mins = d.toMinutes();
        if (mins < 1) return "now";
        if (mins < 60) return mins + "m";
        long hours = d.toHours();
        if (hours < 24) return hours + "h";
        long days = d.toDays();
        if (days < 7) return days + "d";
        return (days / 7) + "w";
    }
}
