package com.ticketing.system.Presentation.components.kit;

import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.html.Span;

/**
 * Multi-line input mock — like {@link LkField} but renders as a tall
 * textarea-shaped block. Ports the React {@code Area} component.
 */
public class LkArea extends NativeLabel {

    private final Span labelSpan = new Span();
    private final Span inputSpan = new Span();
    private final Span valueSpan = new Span();
    private boolean hasLabel;

    public LkArea() {
        addClassName("lk-field");
        labelSpan.addClassName("lk-label");
        inputSpan.addClassName("lk-input");
        inputSpan.addClassName("lk-area");
        valueSpan.addClassName("lk-input-val");
        inputSpan.add(valueSpan);
        add(inputSpan);
        rows(3);
    }

    public LkArea label(String t) {
        labelSpan.setText(t);
        if (!hasLabel) {
            getElement().insertChild(0, labelSpan.getElement());
            hasLabel = true;
        }
        return this;
    }

    public LkArea placeholder(String p) {
        if (valueSpan.getText() == null || valueSpan.getText().isEmpty()) {
            valueSpan.setText(p);
            valueSpan.getStyle().set("color", "var(--faint)");
        }
        return this;
    }

    public LkArea value(String v) {
        valueSpan.setText(v == null ? "" : v);
        valueSpan.getStyle().set("color", "var(--text)");
        return this;
    }

    public LkArea rows(int n) {
        inputSpan.getStyle().set("min-height", (n * 22) + "px");
        return this;
    }

    public LkArea width(String w) {
        getStyle().set("width", w);
        return this;
    }
}
