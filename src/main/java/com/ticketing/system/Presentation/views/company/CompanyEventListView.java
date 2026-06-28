package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Core.Application.dto.EventDetailDTO;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkFilterChip;
import com.ticketing.system.Presentation.components.kit.LkGrid;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkIconBtn;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.components.kit.LkStatusDot;
import com.ticketing.system.Presentation.layouts.WorkspaceLayout;
import com.ticketing.system.Presentation.presenters.company.CompanyEventListPresenter;
import com.ticketing.system.Presentation.security.Capability;
import com.ticketing.system.Presentation.security.RequireCapability;
import com.ticketing.system.Presentation.session.AuthSession;
import com.ticketing.system.Presentation.views.admin.CompanySalesView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owner event-list page (route {@code /owner/events}). Lists the company's events and
 * drives lifecycle actions via
 * {@link com.ticketing.system.Presentation.presenters.company.CompanyEventListPresenter}.
 */
@Route(value = "owner/events", layout = WorkspaceLayout.class)
@PageTitle("My Events · TicketHub")
@PermitAll
@RequireCapability(Capability.VIEW_COMPANY_EVENTS)
public class CompanyEventListView extends LkPage {

    private final CompanyEventListPresenter presenter;
    private final LkCard eventsCard = new LkCard().pad(0);


        public CompanyEventListView(CompanyEventListPresenter presenter) {
        this.presenter = presenter;
        title("My Events");
        subtitle("All events under the selected company.");
        actions(
            new LkBtn("Bulk Export").variant(LkBtn.Variant.secondary)
                .onClick(e -> Toasts.success("Event list exported to CSV (mock).")),
            new LkBtn("New Event").variant(LkBtn.Variant.primary)
                .icon(new LkIcon("plus", 15))
                .onClick(e -> UI.getCurrent().navigate("owner/events/new"))
        );
        add(buildFilters());
        add(eventsCard);
        Span hint = Lk.muted("Row actions → Edit metadata · Venue map · Policies · Sales · Status · Cancel.");
        hint.getStyle().set("font-size", "12.5px");
        add(hint);
        reload();
    }

    private void reload() {
        eventsCard.removeAll();
        LkGrid grid = new LkGrid()
            .col("Event",   "name")
            .col("Date",    "date")
            .col("Venue",   "venue")
            .col("Status",  "status")
            .col("Actions", "act", LkGrid.Align.RIGHT);
        switch (presenter.load(AuthSession.token())) {
            case CompanyEventListPresenter.Outcome.Success ok -> {
                for (EventDetailDTO ev : ok.events()) {
                    addEventRow(grid, ev);
                }
                grid.build();
                eventsCard.add(grid);
            }
            case CompanyEventListPresenter.Outcome.NoCompany ignored ->
                eventsCard.add(Lk.muted("You don't own a company yet."));
            case CompanyEventListPresenter.Outcome.NotAuthenticated ignored ->
                eventsCard.add(Lk.muted("Your session has expired — please sign in again."));
            case CompanyEventListPresenter.Outcome.Failure fail ->
                eventsCard.add(Lk.muted("Could not load events: " + fail.reason()));
        }
    }


    private Component buildFilters() {
        LkRow row = new LkRow().gap(8);
        row.add(
            new LkFilterChip("Status", List.of("Live", "Selling fast", "Draft", "Sold out", "Cancelled")),
            new LkFilterChip("Date range", List.of("Any time", "This week", "This month", "Next 3 months", "Past events"), true, List.of()),
            new LkFilterChip("Venue", List.of(
                "Park HaYarkon", "Bloomfield Stadium", "Teddy Stadium", "Habima Theatre",
                "Caesarea Amphitheatre", "TLV Convention Center"))
        );
        return row;
    }

    private void addEventRow(LkGrid grid, EventDetailDTO ev) {
        Map<String, Object> row = new LinkedHashMap<>();
        Span name = new Span();
        name.getElement().setProperty("innerHTML", "<b>" + escape(ev.name()) + "</b>");
        row.put("name", name);

        String date = ev.showDates() != null && !ev.showDates().isEmpty()
            ? ev.showDates().get(0).getStartTime().toString() : "—";
        row.put("date", date);

        String venue = ev.location() != null ? ev.location().toString() : "—";
        row.put("venue", venue);

        LkStatusDot.Tone tone = ev.status() == EventStatus.ON_SALE ? LkStatusDot.Tone.ok
            : ev.status() == EventStatus.DRAFT ? LkStatusDot.Tone.muted
            : LkStatusDot.Tone.warn;
        row.put("status", new LkStatusDot(tone, ev.status().name()));

        LkRow actions = new LkRow().gap(4).noWrap();
        actions.add(
            iconBtn("edit",    "Edit metadata", () -> UI.getCurrent().navigate("owner/events/" + ev.eventId())),
            iconBtn("map",     "Venue map",     () -> UI.getCurrent().navigate("owner/venue/" + ev.eventId())),
            iconBtn("policy",  "Policies",      () -> UI.getCurrent().navigate(PurchasePolicyEditorView.class)),
            iconBtn("chart",   "Sales",         () -> UI.getCurrent().navigate(CompanySalesView.class))
        );
        if (ev.status() == EventStatus.CANCELED) {
            // A canceled event has no further transitions and can't be canceled again —
            // the only action is to permanently remove it.
            actions.add(iconBtn("trash", "Remove event", () -> openDeleteDialog(ev)));
        } else {
            actions.add(iconBtn("gear",    "Change status", () -> openStatusDialog(ev)));
            actions.add(iconBtn("warning", "Cancel event",  () -> openCancelDialog(ev)));
        }
        row.put("act", actions);
        grid.row(row);
    }

    private void openCancelDialog(EventDetailDTO ev) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Cancel \"" + ev.name() + "\"?");
        dialog.setText("This will refund all ticket holders and permanently cancel the event. This cannot be undone.");
        dialog.setCancelable(true);
        dialog.setCancelText("Keep event");
        dialog.setConfirmText("Cancel event");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(ignored -> {
            int eventId = Integer.parseInt(ev.eventId());
            switch (presenter.cancelEvent(AuthSession.token(), eventId)) {
                case CompanyEventListPresenter.ActionOutcome.Success ignored2 -> {
                    Toasts.success("Event canceled and refunds issued.");
                    reload();
                }
                case CompanyEventListPresenter.ActionOutcome.NotAuthenticated ignored2 ->
                    Toasts.warn("Session expired — please sign in again.");
                case CompanyEventListPresenter.ActionOutcome.Failure fail ->
                    Toasts.failure("Cancel failed: " + fail.reason());
            }
        });
        dialog.open();
    }

    private void openDeleteDialog(EventDetailDTO ev) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Remove \"" + ev.name() + "\"?");
        dialog.setText("The event record will be permanently deleted. This cannot be undone.");
        dialog.setCancelable(true);
        dialog.setCancelText("Keep");
        dialog.setConfirmText("Remove");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(ignored -> {
            switch (presenter.deleteEvent(AuthSession.token(), Integer.parseInt(ev.eventId()))) {
                case CompanyEventListPresenter.ActionOutcome.Success ignored2 -> {
                    Toasts.success("Event removed.");
                    reload();
                }
                case CompanyEventListPresenter.ActionOutcome.NotAuthenticated ignored2 ->
                    Toasts.warn("Session expired — please sign in again.");
                case CompanyEventListPresenter.ActionOutcome.Failure fail ->
                    Toasts.failure(fail.reason() == null ? "Remove failed." : fail.reason());
            }
        });
        dialog.open();
    }

    private void openStatusDialog(EventDetailDTO ev) {
        List<EventStatus> allowed = allowedTransitions(ev.status());
        if (allowed.isEmpty()) {
            Toasts.warn("No status transitions available for a " + ev.status().name() + " event.");
            return;
        }

        Select<EventStatus> select = new Select<>();
        select.setItems(allowed);
        select.setItemLabelGenerator(s -> switch (s) {
            case SCHEDULED -> "Mark as Scheduled";
            case ON_SALE -> "Publish (On Sale)";
            default -> s.name();
        });
        select.setPlaceholder("Select new status…");

        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Change status — " + ev.name());
        dialog.add(select);
        dialog.setCancelable(true);
        dialog.setCancelText("Cancel");
        dialog.setConfirmText("Apply");
        dialog.addConfirmListener(ignored -> {
            EventStatus target = select.getValue();
            if (target == null) {
                Toasts.warn("Please select a status.");
                return;
            }
            switch (presenter.changeEventStatus(AuthSession.token(), Integer.parseInt(ev.eventId()), target)) {
                case CompanyEventListPresenter.ActionOutcome.Success ignored2 -> {
                    Toasts.success("Event status changed to " + target.name() + ".");
                    reload();
                }
                case CompanyEventListPresenter.ActionOutcome.NotAuthenticated ignored2 ->
                    Toasts.warn("Session expired — please sign in again.");
                case CompanyEventListPresenter.ActionOutcome.Failure fail ->
                    Toasts.failure("Status change failed: " + fail.reason());
            }
        });
        dialog.open();
    }

    private static List<EventStatus> allowedTransitions(EventStatus current) {
        return switch (current) {
            case DRAFT -> List.of(EventStatus.SCHEDULED);
            case SCHEDULED -> List.of(EventStatus.ON_SALE);
            default -> List.of();
        };
    }

    private static LkIconBtn iconBtn(String iconName, String tooltip, Runnable r) {
        LkIconBtn b = new LkIconBtn(new LkIcon(iconName, 15), tooltip);
        b.addClickListener(e -> r.run());
        return b;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
