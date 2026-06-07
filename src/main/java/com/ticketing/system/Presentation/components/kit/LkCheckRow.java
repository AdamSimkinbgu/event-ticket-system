package com.ticketing.system.Presentation.components.kit;

import com.vaadin.flow.component.html.Span;

/**
 * Checkbox + label row — ports {@code .lk-checkrow}. Visual only;
 * not a real form input.
 */
public class LkCheckRow extends Span {

    private final Span box = new Span();
    private boolean on;

    public LkCheckRow(String label, boolean checked) {
        addClassName("lk-checkrow");
        box.addClassName("lk-checkbox");
        Span lbl = new Span(label);
        add(box, lbl);
        setChecked(checked);
    }

    public LkCheckRow setChecked(boolean checked) {
        this.on = checked;
        box.removeAll();
        if (checked) {
            box.addClassName("on");
            box.add(new LkIcon("check", 11, 2.6));
        } else {
            box.removeClassName("on");
        }
        return this;
    }

    public boolean isChecked() { return on; }
}
