package com.ticketing.system.Presentation.components.buyer;

import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBanner;
import com.ticketing.system.Presentation.components.kit.LkCol;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkSelect;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextArea;

import java.util.List;

/**
 * Refund-request modal — ticket summary, reason picker, optional notes,
 * policy banner, and Cancel / Request-refund actions. Submission is
 * mocked (toast only) until V2-RES-04 wires
 * {@code ReservationService.requestRefund}.
 */
public final class BzRefundDialog {

    private BzRefundDialog() { }

    public static void show(BzTicketDialog.TicketInfo ticket) {
        Dialog d = new Dialog();
        d.setHeaderTitle("Request a refund");
        d.setWidth("460px");
        d.setMaxWidth("92vw");

        LkCol col = new LkCol().gap(14);
        col.add(buildTicketSummary(ticket));

        LkSelect reason = new LkSelect("Event cancelled", List.of(
            "Event cancelled",
            "I can no longer attend",
            "Duplicate purchase",
            "Wrong ticket",
            "Other"
        )).label("Reason for refund").required();
        col.add(reason);

        TextArea notes = new TextArea("Additional details (optional)");
        notes.setPlaceholder("Anything else we should know…");
        notes.setMinHeight("80px");
        notes.setWidthFull();
        col.add(notes);

        col.add(new LkBanner(LkBanner.Tone.info, new LkIcon("info", 17),
            "Refunds are processed in 3–5 business days. The event organizer is notified."));

        d.add(col);

        Button cancel = new Button("Cancel", e -> d.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button submit = new Button("Request refund", e -> {
            Toasts.success("Refund requested for " + ticket.zone() + " · " + ticket.seat()
                + " — we'll email when it's processed.");
            d.close();
        });
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        d.getFooter().add(cancel, submit);
        d.open();
    }

    private static Div buildTicketSummary(BzTicketDialog.TicketInfo t) {
        Div summary = new Div();
        summary.getStyle()
            .set("background", "#f8fafc")
            .set("border", "1px solid var(--border)")
            .set("border-radius", "10px")
            .set("padding", "14px 16px");

        Span title = new Span();
        title.getElement().setProperty("innerHTML", "<b>" + escape(t.event()) + "</b>");
        title.getStyle().set("display", "block").set("color", "#0f172a");

        Span line = Lk.muted(t.zone() + " · " + t.seat() + " · " + t.price()
            + (t.date() == null ? "" : " · " + t.date()));
        line.getStyle().set("font-size", "13px").set("display", "block").set("margin-top", "4px");

        summary.add(title, line);
        return summary;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
