package com.ticketing.system.Presentation.components.kit;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;

/**
 * Reusable confirmation dialog primitive (V2-DLG-01). Wraps Vaadin
 * {@link Dialog} with a title, a message, and Cancel / Confirm actions —
 * the footer styling mirrors the rest of the kit (Cancel is tertiary, the
 * confirm button is primary, or red when {@link Severity#danger} is set for
 * destructive actions like revoking a manager).
 *
 * <p>Builder-style: configure with {@link #severity}, {@link #confirmText},
 * {@link #cancelText}, {@link #onConfirm}, then call {@link #open()}. The
 * dialog is built in the constructor (no UI context needed) and only
 * {@link #open()} requires a live UI — so it can be constructed in tests.
 *
 * <pre>{@code
 * new LkConfirm("Revoke manager", "Remove Carol from the team?")
 *     .severity(LkConfirm.Severity.danger)
 *     .confirmText("Revoke")
 *     .onConfirm(() -> presenter.revoke(...))
 *     .open();
 * }</pre>
 */
public class LkConfirm {

    public enum Severity { normal, danger }

    private final Dialog dialog = new Dialog();
    private final Button cancel = new Button("Cancel");
    private final Button confirm = new Button("Confirm");
    private Runnable onConfirm;

    public LkConfirm(String title, String message) {
        dialog.setHeaderTitle(title);
        dialog.setWidth("420px");
        dialog.setMaxWidth("92vw");

        Span body = new Span(message);
        body.getStyle().set("color", "var(--muted)").set("line-height", "1.5");
        dialog.add(body);

        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        cancel.addClickListener(e -> dialog.close());

        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirm.addClickListener(e -> {
            dialog.close();
            if (onConfirm != null) onConfirm.run();
        });

        dialog.getFooter().add(cancel, confirm);
    }

    /** {@link Severity#danger} turns the confirm button red for destructive actions. */
    public LkConfirm severity(Severity severity) {
        if (severity == Severity.danger) {
            confirm.removeThemeVariants(ButtonVariant.LUMO_PRIMARY);
            confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        }
        return this;
    }

    public LkConfirm confirmText(String text) {
        confirm.setText(text);
        return this;
    }

    public LkConfirm cancelText(String text) {
        cancel.setText(text);
        return this;
    }

    /** Runs when the user clicks the confirm button (the dialog closes first). */
    public LkConfirm onConfirm(Runnable handler) {
        this.onConfirm = handler;
        return this;
    }

    public void open() {
        dialog.open();
    }
}
